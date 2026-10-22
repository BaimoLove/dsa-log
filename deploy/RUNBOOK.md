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

`/opt/dsa/nginx/.htpasswd`（网站访问密码）在下一节生成——**必须在首次 `up` 之前建好**。

---

## 2. 访问控制（整站 basic auth）

**必须在首次启动前做**。compose 会把 `.htpasswd` 挂进容器，而 Docker 遇到
「宿主机上不存在的文件」会建一个同名**目录**，nginx 随即报 `auth_basic_user_file`
是个目录——从报错完全看不出根因。

```bash
mkdir -p /opt/dsa/nginx
openssl passwd -apr1 '换成访问密码' | sed 's/^/dsa:/' > /opt/dsa/nginx/.htpasswd
# worker 进程在容器内是 nginx 用户（gid 101），属主 root、组 101 才能读
chown root:101 /opt/dsa/nginx/.htpasswd
chmod 640 /opt/dsa/nginx/.htpasswd
cat /opt/dsa/nginx/.htpasswd      # 应该是一行 dsa:$apr1$...
```

- 用户名固定 `dsa`，密码自己定。以后浏览器打开站点会弹框要这一对。
- 用 `-apr1` 不用 bcrypt：alpine 版 nginx 对 `$2y$` 偶有不支持，表现为「密码明明对却一直 401」。
  单用户私人站点，apr1 足够。
- 若日志里出现 `Permission denied` 读 `.htpasswd`，说明宿主上 gid 101 已被别的组占用，
  改成 `chmod 644` 即可。
- 换密码：重跑第一条覆盖文件，再 `docker restart dsa-nginx`。

安全组（阿里云 → 实例 → 安全组 → 入方向）：

| 端口 | 授权对象 | 说明 |
| --- | --- | --- |
| 80 | `0.0.0.0/0` | 站点对公网开放，**靠 basic auth 守门** |
| 22 | **当前 IP/32** | SSH 必须限源，暴破后果远大于 HTTP |
| 22 | `100.104.0.0/16` | 阿里云 Workbench 通道，**别删**——家宽 IP 变了靠它进来改规则 |
| 8080 / 3306 | 不开放 | 只绑回环，开了也没用 |

> **为什么 80 要放开而不是限源**：家宽出口 IP 由运营商 DHCP 动态分配，实测一天内
> 就变过（`171.38.95.255` → `171.38.105.168`），一变站点立刻不可达，每次都要回控制台。
> 云服务器的公网 IP 是固定的，所以「80 放开 + 密码守门」反过来更稳：IP 随便变，
> 站点照常访问。22 不能这么干——SSH 暴破的后果严重得多，仍限源，配合 Workbench 兜底。

```bash
# 在服务器上确认 8080 / 3306 只绑回环（这是一切的前提）
ss -lntp | grep -E ':(80|8080|3306)[[:space:]]'
```

---

## 3. 首次部署

本机执行：

```bash
chmod +x deploy/*.sh deploy/backup/dump.sh
deploy/deploy.sh root@IP
```

依次做：本机构建后端 + 前端 → 建远端目录 → 同步
`Dockerfile`/`compose.yaml`/`nginx/dsa.conf` → **校验 `.htpasswd` 存在** → scp jar
→ 同步 dist → 远端 `docker compose up -d --build` → 轮询健康检查。

`.htpasswd` 缺失时脚本会直接退出并打出生成命令，不会让你踩「Docker 把文件建成目录」那个坑。

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
0 4 * * * root curl -fsS http://127.0.0.1:8080/api/health >/dev/null || docker restart dsa-api
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
4. `curl -u dsa:你的密码 http://127.0.0.1/api/health` → `code:SUCCESS` 且 `db:ok`
   （`redis` 是 `down`，**不参与判据**——Redis 已停用。
   不带 `-u` 会拿到 401，这是 basic auth 生效的正常表现，不是故障）
5. 公网打开站点弹出密码框，用 `dsa` / 你设的密码能进；建一道题，刷新后还在
6. **`reboot` 后全链路自动恢复**（必须真做一次，这是 restart 策略唯一的验证方式）
   ```bash
   reboot
   # 等 60s；带 -u 因为有 basic auth
   curl -u dsa:你的密码 http://IP/api/health
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
| 公网打不开 / 连接超时 | 云控制台看安全组入方向 | 80 那条被删了或不是 `0.0.0.0/0`；`docker ps` 看 dsa-nginx 是否 running |
| 一直弹密码框 / 输对了还是 401 | `docker exec dsa-nginx cat /etc/nginx/.htpasswd`、`docker logs dsa-nginx` | `.htpasswd` 被 Docker 建成目录（见第 2 节）；或哈希格式不被支持；或权限 640 但宿主 gid 101 冲突导致读不到 |
| 403 Forbidden | `docker exec dsa-nginx ls -l /usr/share/nginx/html` | dist 权限不对，容器内用户读不了 → 重跑 `deploy.sh` |
| 页面空白 / 404 | `docker exec dsa-nginx ls /usr/share/nginx/html/index.html` | dist 没同步上，或挂载路径写错 |
| 页面能开但接口 500 | `docker logs dsa-api` | 数据库连不上（`.env` 缺口令/口令错/MySQL 没起） |
| 时间差 8 小时 | `date`、`docker exec dsa-api date` | 宿主时区没设，或 compose 少了 `TZ` |
| 磁盘满 | `df -h`、`docker system df` | 日志没限额；或镜像堆积：`docker image prune -f` |
| 容器反复重启 | `docker inspect dsa-api --format '{{.State.OOMKilled}}'` | true 就是被 OOM，降 `-Xmx` 或查慢查询 |
| nginx 起不来 | `docker logs dsa-nginx` | `dsa.conf` / `.htpasswd` 语法错误或被 Docker 建成了目录；`.htpasswd` 不存在则直接退出 |

回滚永远是第一选项：先回到上一个 tag 恢复可用，再从容查原因。
