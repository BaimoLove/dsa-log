# 上线 Runbook（M8.5）

阿里云 2G 单机。**宿主只装 Docker**：nginx、Spring Boot、MySQL 全在容器里。
本文件是**可逐条复制执行**的清单，不是设计文档；设计取舍见 `PLAN.md` 第九章。

> 纪律：服务器上不跑 `mvn`、不跑 `npm`，也不装 nginx / mysql 客户端。
> 构建全在本机，运维命令一律走 `docker exec`。2G 机器编译 Spring Boot 极易被 OOM killer 干掉。

---

## 0. 开工前确认

```bash
docker -v                       # 必须有
docker compose version          # 需要 v2（命令是 docker compose，不是 docker-compose）
ss -lntp | grep -E ':80|:3306|:8080'   # 80 应空闲；3306 只能 127.0.0.1；8080 不该有
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream
```

| 检查项 | 期望 | 不满足怎么处置 |
| --- | --- | --- |
| 80 端口 | 空闲 | 有别的服务占着就先停掉（本方案不再用宿主 nginx） |
| 3306 | `127.0.0.1:3306` | 若是 `0.0.0.0:3306` → 重建 MySQL 容器只绑回环 |
| MySQL 容器名 | 记下来（常见是 `mysql8` 之类，不一定是 `mysql`） | 不叫 `mysql` 时在 `/opt/dsa/api/.env` 加 `MYSQL_CONTAINER=<容器名>`，备份脚本读它 |
| MySQL 内存 | 有 limit | `docker update --memory=512m --memory-swap=1g --restart=always mysql8   # 换成实际容器名` |
| MySQL 时区 | `+08:00` | my.cnf 加 `default-time-zone='+08:00'`，重启容器 |

MySQL 容器里再补三项（2G 关键）：

```
innodb_buffer_pool_size = 256M
performance_schema      = OFF
default-time-zone       = '+08:00'
```

```bash
docker restart mysql8          # 换成实际容器名
# Redis 目前无消费方，停掉省 40MB
docker stop redis
```

---

## 1. 建目录 + 口令

```bash
mkdir -p /opt/dsa/{api/dump,web/dist,nginx,backup}
chmod 755 /opt/dsa /opt/dsa/api /opt/dsa/nginx /opt/dsa/backup

cat > /opt/dsa/api/.env <<'EOF'
DB_USER=root
DB_PASSWORD=换成真实口令
DB_NAME=dsa_log
EOF
chmod 600 /opt/dsa/api/.env
```

> `compose.yaml` 里是 `${DB_PASSWORD:?...}`：没有这个文件时 `docker compose up` 直接报错退出。
> 这是刻意的——缺口令时宁可起不来，也不要拿空口令去连库。
> 备份脚本也从这份 `.env` 读口令，只维护一处。

---

## 2. 访问控制（不用密码，靠安全组）

这台机器的 80 端口只对自己的固定 IP 开放，**不做 basic auth**——网络层已经拦掉了，
再加一层密码只是多一次输入。所以本步骤不需要 `.htpasswd`，nginx 配置里也没有 `auth_basic`。

上线前请确认安全组（阿里云 → 实例 → 安全组 → 入方向）：

| 端口 | 授权对象 | 说明 |
| --- | --- | --- |
| 22 | **你的 IP/32** | SSH，最好也限制来源 |
| 80 | **你的 IP/32** | 唯一对外的业务端口 |
| 8080 / 3306 | **不开放** | 后端与 MySQL 只绑 127.0.0.1，开了也没用，但少一条被扫的入口 |

```bash
# 在服务器上确认 8080 只绑回环（这是一切的前提）
ss -lntp | grep -E ':(80|8080|3306)[[:space:]]'
```

**风险提示**：安全组是唯一防线，它的问题是「配置漂移」——某天为了临时调试加了
`0.0.0.0/0` 忘了删，站点就完全公开了。两个习惯可以兜住：
- 调试时改规则，用完立刻改回；每次改完在日历上留个提醒
- RUNBOOK 第 5 节的核对项每次都看一眼授权对象是不是 `/32`

> 如果哪天想在公网随便一台机器上临时访问，再回到 basic auth 也很快：
> 在 `dsa.conf` 的 `server {` 里加两行 `auth_basic` / `auth_basic_user_file`，
> 把 `.htpasswd` 挂回 compose，生成方式：`openssl passwd -apr1 '密码' | sed 's/^/dsa:/' > .htpasswd`。

---

## 3. 首次部署

本机执行：

```bash
chmod +x deploy/*.sh deploy/backup/dump.sh
deploy/deploy.sh root@IP
```

依次做：本机构建后端 + 前端 → 建远端目录 → 同步
`Dockerfile`/`compose.yaml`/`nginx/dsa.conf` → scp jar → rsync dist → 远端
`docker compose up -d --build` → 轮询健康检查。

首次会拉两个镜像：`eclipse-temurin:17-jre-alpine`（约 110MB）、`nginx:stable-alpine`（约 50MB）。
拉不动就在服务器配镜像加速后重启 docker（会重启所有容器，挑低峰做）。

改过 `dsa.conf` 之后：

```bash
ssh root@IP 'docker exec dsa-nginx nginx -t'                  # 先验语法
ssh root@IP 'cd /opt/dsa/api && docker compose restart nginx'
```

---

## 4. 备份 cron

```bash
cat > /etc/cron.d/dsa <<'EOF'
# 每天 03:30 全量备份（走 docker exec，宿主不需要 mysql 客户端）
30 3 * * * root /opt/dsa/backup/dump.sh >> /var/log/dsa-backup.log 2>&1
# 每天 04:00 探活：接口不通就重启容器（进程卡死时 restart 策略不会触发）
0 4 * * * root curl -fsS http://127.0.0.1/api/health >/dev/null || docker restart dsa-api
EOF
chmod 644 /etc/cron.d/dsa
```

容器名不用手工填：`dump.sh` 会扫 `docker ps` 里镜像名含 mysql 的容器（你的机器上是 `mysql8`）。
要指定就在 `/opt/dsa/api/.env` 加 `MYSQL_CONTAINER=mysql8`。

手动验一次，别等出事才发现脚本写错：

```bash
/opt/dsa/backup/dump.sh && ls -lh /opt/dsa/backup/
```

---

## 5. 上线核对（逐条过，别跳）

1. `cd /opt/dsa/api && docker compose ps` → `dsa-api` 与 `dsa-nginx` 都是 `running`
2. `docker inspect dsa-api --format '{{.HostConfig.RestartPolicy.Name}}'` → `unless-stopped`；`dsa-nginx` 同
3. `ss -lntp`：80 有监听（nginx 容器）；8080 必须是 `127.0.0.1`；3306 必须是 `127.0.0.1`
4. `curl http://127.0.0.1/api/health` → `code:SUCCESS` 且 `db:ok`
   （`redis` 是 `down`，**不参与判据**——Redis 已停用）
5. 公网打开站点直接能进（不弹密码框）；建一道题，刷新后还在
6. **`reboot` 后全链路自动恢复**（必须真做一次，这是 restart 策略唯一的验证方式）
   ```bash
   reboot
   # 等 60s
   curl http://IP/api/health
   ```
7. 演练恢复一次（先停 app，避免半途被写）：
   ```bash
   docker stop dsa-api
   set -a; . /opt/dsa/api/.env; set +a
   MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8}"      # 换成实际容器名
   gunzip -c /opt/dsa/backup/dsa_log-YYYYmmdd-HHMMSS.sql.gz \
     | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" mysql -u"$DB_USER" dsa_log
   docker start dsa-api
   ```

### 内存基线（2G）

```bash
free -m            # available 应 > 300MB
vmstat 1 5         # si/so 必须是 0，非 0 说明已经在换页
docker stats --no-stream
```

实测基线（阿里云 2G，`free -m` 的 total 是 **1735MB**，不是 2048）：
系统+Docker ~530MB、MySQL 稳态 400–450MB、dsa-api 500–650MB、dsa-nginx 15–20MB，
合计约 **1490MB**，余 245MB。

> MySQL 冷启动只有 35MB，别拿它做容量规划——InnoDB buffer pool 是惰性分配的，
> 数据攒起来后会爬到 400MB 以上。上限按 `--memory=512m` 设，稳态刚好兜住。

---

## 6. 日常发版与回滚

```bash
deploy/deploy.sh root@IP                  # 构建 + 上传 + 重建（编排与 nginx 配置一起同步）
deploy/rollback.sh root@IP                # 列出可回滚的 tag
deploy/rollback.sh root@IP 202610211530   # 切到指定 tag（秒级，不重建）
```

**回滚能生效的前提**：每次发版 tag 不同。`deploy.sh` 用 `date +%Y%m%d%H%M`，同一分钟连发两次会覆盖。

---

## 7. 出问题先看这三处

```bash
docker logs --tail 120 dsa-api       # 应用（prod 已关 SQL 日志，只剩业务日志）
docker logs --tail 80 dsa-nginx      # nginx 的 access/error 都在这里（写的是 /dev/stdout）
docker logs --tail 50 mysql8         # 换成实际容器名
```

| 症状 | 先查 | 常见原因 |
| --- | --- | --- |
| 502 Bad Gateway | `ss -lntp \| grep 8080`、`docker ps` | api 没起来；或 api 绑的不是回环 |
| 公网打不开 / 连接超时 | 云控制台看安全组入方向 | 你当前 IP 变了（换网络、手机热点），把 80 的授权对象改成新 IP/32 |
| 403 Forbidden | `docker exec dsa-nginx ls -l /usr/share/nginx/html` | dist 权限不对，容器内用户读不了 → 重跑 `deploy.sh` |
| 页面空白 / 404 | `docker exec dsa-nginx ls /usr/share/nginx/html/index.html` | dist 没同步上，或挂载路径写错 |
| 页面能开但接口 500 | `docker logs dsa-api` | 数据库连不上（`.env` 缺口令/口令错/MySQL 没起） |
| 时间差 8 小时 | `date`、`docker exec dsa-api date` | 宿主时区没设，或 compose 少了 `TZ` |
| 磁盘满 | `df -h`、`docker system df` | 日志没限额；或镜像堆积：`docker image prune -f` |
| 容器反复重启 | `docker inspect dsa-api --format '{{.State.OOMKilled}}'` | true 就是被 OOM，降 `-Xmx` 或查慢查询 |
| nginx 起不来 | `docker logs dsa-nginx` | `dsa.conf` 语法错误，或该文件被 Docker 建成了目录 |

回滚永远是第一选项：先回到上一个 tag 恢复可用，再从容查原因。
