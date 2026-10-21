# 上线 Runbook（M8.5）

阿里云 2G 单机，nginx 做唯一入口，Spring Boot 与 MySQL 都在 Docker 里。
本文件是**可逐条复制执行**的清单，不是设计文档；设计取舍见 `PLAN.md` 第九章。

> 纪律：服务器上不跑 `mvn`、不跑 `npm`。构建全在本机，服务器只收产物。
> 2G 机器编译 Spring Boot 极易被 OOM killer 干掉，还会连累 MySQL。

---

## 0. 开工前必须确认的三件事

| 项 | 怎么确认 | 影响 |
| --- | --- | --- |
| 服务器已装 Docker + nginx | `docker -v`、`nginx -v` | 缺则先装 |
| MySQL 容器已跑且数据在 | `docker ps --format '{{.Names}}\t{{.Status}}'` | app 连它 |
| 3306 **没有**暴露到 0.0.0.0 | `ss -lntp \| grep 3306` | 暴露即公网裸奔 |

```bash
# 一次看全
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker inspect mysql --format '{{json .HostConfig.PortBindings}}'
docker stats --no-stream
docker exec mysql mysql -uroot -p -e "SELECT VERSION(), @@time_zone, @@character_set_server;"
```

处置（按需）：

```bash
# 端口仍是 0.0.0.0:3306 → 重建容器，只绑回环
#   注意：先 dump 数据再重建，重建会换容器内卷挂载
docker rm -f mysql   # 有数据卷才不会丢数据，确认 -v 挂载后再执行

# 内存限额 + 随 docker 一起启动
docker update --memory=512m --memory-swap=1g --restart=always mysql

# 时区 + 2G 关键三项（写进 my.cnf 后重启容器）
#   innodb_buffer_pool_size = 256M
#   performance_schema      = OFF
#   default-time-zone       = '+08:00'

# Redis 目前没有消费方，停掉省 40MB
docker stop redis
```

---

## 1. 建目录、放配置

```bash
mkdir -p /opt/dsa/{api,web/dist,backup} /opt/dsa/api/dump
chown -R www-data:www-data /opt/dsa/web
chmod 755 /opt/dsa/api /opt/dsa/backup
```

把本仓库 `deploy/` 下的三个文件传上去（之后不需要再动）：

```bash
# 在本机仓库根目录执行
scp deploy/Dockerfile deploy/compose.yaml root@IP:/opt/dsa/api/
scp deploy/nginx/dsa.conf           root@IP:/etc/nginx/conf.d/dsa.conf
scp deploy/backup/dump.sh           root@IP:/opt/dsa/backup/dump.sh
ssh root@IP 'chmod +x /opt/dsa/backup/dump.sh'
```

**数据库口令**（不给版本库）：

```bash
ssh root@IP 'cat > /opt/dsa/api/.env <<EOF
DB_USER=root
DB_PASSWORD=换成真实口令
DB_NAME=dsa_log
EOF
chmod 600 /opt/dsa/api/.env'
```

> `compose.yaml` 里写的是 `${DB_PASSWORD:?...}`，没有这个 `.env` 时 `docker compose up` 会直接报错退出——
> 这是刻意的：缺口令时宁可起不来，也不要拿空口令去连库。

**备份用的 my.cnf**（避免口令出现在 `ps` 里）：

```bash
ssh root@IP 'cat > /opt/dsa/backup/.my.cnf <<EOF
[client]
user=root
password=换成真实口令
EOF
chmod 600 /opt/dsa/backup/.my.cnf'
```

---

## 2. nginx：账号与站点

```bash
# 账号密码（apr1 是 nginx 认的格式；-c 会覆盖整个文件，追加时去掉 -c）
ssh root@IP 'printf "dsa:$(openssl passwd -apr1 "换成访问密码")\n" > /etc/nginx/.htpasswd && chmod 640 /etc/nginx/.htpasswd'

ssh root@IP 'nginx -t && systemctl enable --now nginx && systemctl reload nginx'
```

安全组只放行 22 与 80。**8080 与 3306 一律不开**（后端只绑 127.0.0.1，开了也连不上，但少一条被扫的入口）。

---

## 3. 首次部署

本机执行：

```bash
chmod +x deploy/*.sh
deploy/deploy.sh root@IP
```

它会：本机构建后端 + 前端 → scp jar → rsync dist → 远端 `docker compose up -d --build` → 轮询健康检查。

首次 build 要拉 `eclipse-temurin:17-jre-alpine`（约 110MB）。拉不动就在服务器配镜像加速：

```bash
# /etc/docker/daemon.json 里加 registry-mirrors 后：
systemctl restart docker    # 会重启所有容器，挑低峰做
```

---

## 4. 备份 cron

```bash
ssh root@IP 'cat > /etc/cron.d/dsa <<EOF
# 每天 03:30 全量 dump（保留 14 天）
30 3 * * * root /opt/dsa/backup/dump.sh >> /var/log/dsa-backup.log 2>&1
# 每天 04:00 探活：接口不通就重启容器（容器内进程卡死时 restart 策略不会触发）
0 4 * * * root curl -fsS -u dsa:密码 http://127.0.0.1/api/health >/dev/null || docker restart dsa-api
EOF'
```

手动验一次，别等到出事才发现脚本写错：

```bash
ssh root@IP '/opt/dsa/backup/dump.sh && ls -lh /opt/dsa/backup/'
```

---

## 5. 上线核对（逐条过，别跳）

1. `systemctl is-enabled nginx` → `enabled`
2. `docker inspect dsa-api --format '{{.HostConfig.RestartPolicy.Name}}'` → `unless-stopped`；MySQL → `always`
3. `ss -lntp`：8080 必须是 `127.0.0.1:8080`，3306 必须是 `127.0.0.1`
4. `curl -u dsa:密码 http://127.0.0.1/api/health` → `code:SUCCESS` 且 `db:ok`
   （`redis` 字段是 `down`，**不参与判据**——Redis 已停用）
5. 公网打开站点：先弹 basic auth，输完能进；随便建一道题，刷新后还在
6. **`reboot` 后全链路自动恢复**（必须真做一次，这是 restart 策略唯一的验证方式）
   ```bash
   reboot
   # 等 60s 后
   curl -u dsa:密码 http://IP/api/health
   ```
7. 演练恢复一次备份（恢复前先停 app，避免半途被写）：
   ```bash
   docker stop dsa-api
   gunzip -c /opt/dsa/backup/dsa_log-YYYYmmdd-HHMMSS.sql.gz | mysql -uroot -p dsa_log
   docker start dsa-api
   ```

### 内存基线（2G 机器）

```bash
free -m            # available 应 > 300MB
vmstat 1 5         # si/so 必须是 0，非 0 说明已经在换页
docker stats --no-stream
```

经验值：MySQL ~300MB、dsa-api ~400MB（堆 448m 打满）、nginx ~25MB、系统 ~250MB。

---

## 6. 日常发版与回滚

```bash
# 发版（自动打时间戳 tag）
deploy/deploy.sh root@IP

# 看有哪些 tag 可回滚
deploy/rollback.sh root@IP

# 回滚到某个 tag（秒级，不重新构建）
deploy/rollback.sh root@IP 202610211530
```

**回滚能生效的前提**：每次发版 tag 不同。`deploy.sh` 用 `date +%Y%m%d%H%M`，同一分钟连发两次会覆盖——真要连发，手动改 `IMAGE_TAG`。

---

## 7. 出问题先看这三处

```bash
docker logs --tail 120 dsa-api          # 应用侧（SQL 日志已在 prod 关闭，只剩业务日志）
docker logs --tail 50 mysql
tail -50 /var/log/nginx/dsa-error.log
```

| 症状 | 先查 | 常见原因 |
| --- | --- | --- |
| 502 Bad Gateway | `ss -lntp \| grep 8080` | 容器没起来 / 后端绑的不是回环 |
| 页面能开但接口 500 | `docker logs dsa-api` | 数据库连不上（看是不是 `.env` 缺口令） |
| 时间差 8 小时 | `date`、`docker exec dsa-api date` | 宿主机时区没设，或 compose 少了 `TZ` |
| 导入报 500 | 后端日志里 `Column 'xxx' cannot be null` | 备份文件字段与当前表结构不匹配，先 `GET /api/export` 比对 |
| 磁盘满 | `df -h`、`docker system df` | 日志没限额，或镜像堆积：`docker image prune -f` |
| 容器反复重启 | `docker inspect dsa-api --format '{{.State.OOMKilled}}'` | true 就是被 OOM，降 `-Xmx` 或查慢查询 |

回滚永远是第一选项：先回到上一个 tag 恢复可用，再从容查原因。
