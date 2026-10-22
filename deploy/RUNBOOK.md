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

## 2. nginx 账号（basic auth）

**必须在首次启动前做**。compose 会把 `.htpasswd` 挂进容器，而 Docker 遇到「宿主机上不存在的文件」
会建一个同名**目录**，nginx 随即报 `auth_basic_user_file` 是个目录——从报错完全看不出根因。

```bash
mkdir -p /opt/dsa/nginx
docker run --rm httpd:2.4-alpine htpasswd -nbB dsa '换成访问密码' > /opt/dsa/nginx/.htpasswd
# worker 进程在容器内是 nginx 用户（gid 101），属主必须是 root、组给 101 才能读
chown root:101 /opt/dsa/nginx/.htpasswd
chmod 640 /opt/dsa/nginx/.htpasswd
cat /opt/dsa/nginx/.htpasswd     # 应该是 dsa:$2y$05$... 一行
```

- `-B` 是 bcrypt，比 `apr1` 强；nginx stable 线支持 `$2y$`。
- 万一启动后密码总是不对（个别镜像的 crypt 实现对 bcrypt 支持不全），退回 `-nb`（apr1）。
- **如果日志里出现 `Permission denied` 读 .htpasswd**，说明宿主上 gid 101 是别的组：
  改成 `chmod 644` 即可（bcrypt 哈希泄露也难以爆破，单机私人站点可接受）。
- 换密码：重跑 `htpasswd` 那条覆盖文件，再 `docker restart dsa-nginx`。

> 不需要 `systemctl`、`/etc/nginx/`、`www-data` 这些宿主概念——nginx 在容器里。

---

## 3. 首次部署

本机执行：

```bash
chmod +x deploy/*.sh deploy/backup/dump.sh
deploy/deploy.sh root@IP
```

依次做：本机构建后端 + 前端 → 建远端目录 → 拦一次 `.htpasswd` 缺失 → 同步
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
0 4 * * * root curl -fsS -u dsa:密码 http://127.0.0.1/api/health >/dev/null || docker restart dsa-api
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
4. `curl -u dsa:密码 http://127.0.0.1/api/health` → `code:SUCCESS` 且 `db:ok`
   （`redis` 是 `down`，**不参与判据**——Redis 已停用）
5. 公网打开站点：先弹 basic auth，输完能进；建一道题，刷新后还在
6. **`reboot` 后全链路自动恢复**（必须真做一次，这是 restart 策略唯一的验证方式）
   ```bash
   reboot
   # 等 60s
   curl -u dsa:密码 http://IP/api/health
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
| 一直弹 auth 后又 401 | `docker exec dsa-nginx cat /etc/nginx/.htpasswd` | 文件被建成了目录（见第 2 节）；或哈希格式不支持 |
| 403 Forbidden | `docker exec dsa-nginx ls -l /usr/share/nginx/html` | dist 权限不对，容器内用户读不了 → 重跑 `deploy.sh` |
| 页面空白 / 404 | `docker exec dsa-nginx ls /usr/share/nginx/html/index.html` | dist 没同步上，或挂载路径写错 |
| 页面能开但接口 500 | `docker logs dsa-api` | 数据库连不上（`.env` 缺口令/口令错/MySQL 没起） |
| 时间差 8 小时 | `date`、`docker exec dsa-api date` | 宿主时区没设，或 compose 少了 `TZ` |
| 磁盘满 | `df -h`、`docker system df` | 日志没限额；或镜像堆积：`docker image prune -f` |
| 容器反复重启 | `docker inspect dsa-api --format '{{.State.OOMKilled}}'` | true 就是被 OOM，降 `-Xmx` 或查慢查询 |
| nginx 起不来 | `docker logs dsa-nginx` | 多半是 `.htpasswd` 或 `dsa.conf` 挂载成了目录 |

回滚永远是第一选项：先回到上一个 tag 恢复可用，再从容查原因。
