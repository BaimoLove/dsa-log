#!/usr/bin/env bash
# deploy/deploy.sh —— 本机一键发版（构建在本地，服务器只收产物）
#
# 用法：
#   deploy/deploy.sh root@1.2.3.4                 # 构建 + 上传 + 重建
#   deploy/deploy.sh root@1.2.3.4 --skip-build    # 只上传现有产物（网络慢时省事）
#
# 为什么不在服务器构建：2G 机器跑 mvn 极易 OOM，且会同时占住 MySQL 的内存。
set -euo pipefail

TARGET="${1:-}"
SKIP_BUILD="${2:-}"
if [ -z "${TARGET}" ]; then
    echo "用法：$0 user@host [--skip-build]" >&2
    exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${ROOT}/dsa-log-server/target/dsa-log-server-0.0.1-SNAPSHOT.jar"
DIST="${ROOT}/web/dist"
TAG="$(date +%Y%m%d%H%M)"

if [ "${SKIP_BUILD}" != "--skip-build" ]; then
    echo "==> 构建后端（本机，跳过测试：测试在 CI/提交前跑）"
    (cd "${ROOT}/dsa-log-server" && ./mvnw -q clean package -DskipTests)

    echo "==> 构建前端（production 模式，VITE_DATA_SOURCE=http）"
    (cd "${ROOT}/web" && npm run build)
fi

for f in "${JAR}" "${DIST}/index.html"; do
    if [ ! -f "${f}" ]; then
        echo "缺少产物：${f}" >&2
        exit 1
    fi
done

echo "==> 远端目录与配置"
ssh "${TARGET}" "mkdir -p /opt/dsa/api/dump /opt/dsa/web/dist /opt/dsa/nginx /opt/dsa/backup"

# 编排与 nginx 配置跟着仓库走：改完配置直接发版，不会出现「服务器上还是旧配置」
scp -q "${ROOT}/deploy/Dockerfile" "${ROOT}/deploy/compose.yaml" "${TARGET}:/opt/dsa/api/"
scp -q "${ROOT}/deploy/nginx/dsa.conf" "${TARGET}:/opt/dsa/nginx/dsa.conf"

# basic auth 账号文件不存在时 nginx 起不来，且 Docker 会把缺失的挂载点建成目录，
# 报错（auth_basic_user_file 是个目录）完全指不到根因。所以先挡一道。
if ! ssh "${TARGET}" "test -f /opt/dsa/nginx/.htpasswd"; then
    cat >&2 <<EOF
!!! 缺少 /opt/dsa/nginx/.htpasswd（basic auth 账号文件），nginx 会起不来。

在服务器上生成（宿主有 openssl 就行，不用装别的）：

  ssh ${TARGET} 'openssl passwd -apr1 "换成访问密码" | sed "s/^/dsa:/" > /opt/dsa/nginx/.htpasswd'
  ssh ${TARGET} 'chown root:101 /opt/dsa/nginx/.htpasswd && chmod 640 /opt/dsa/nginx/.htpasswd'

（101 是容器内 nginx worker 的 gid；宿主上若已被别的组占用，退成 chmod 644）

EOF
    exit 1
fi

# 另一种失败形态：Docker 已经把缺失的挂载点建成了同名目录
if ssh "${TARGET}" "test -d /opt/dsa/nginx/.htpasswd"; then
    echo "!!! /opt/dsa/nginx/.htpasswd 是个目录（Docker 挂载不存在的文件时会这样建）。" >&2
    echo "    先删掉再重新生成：ssh ${TARGET} 'rm -rf /opt/dsa/nginx/.htpasswd'" >&2
    exit 1
fi

echo "==> 上传后端 jar（tag ${TAG}）"
# compose 里 COPY app.jar，所以远端固定叫 app.jar；版本信息靠 image tag 保留
scp -q "${JAR}" "${TARGET}:/opt/dsa/api/app.jar"

echo "==> 同步前端静态资源"
# --delete 让被删掉的旧 chunk 一起清走，否则 dist 里会越堆越多。
# 权限跟着统一：nginx 容器里是非 root 用户，目录必须可读可进入
if command -v rsync >/dev/null 2>&1; then
    rsync -az --delete --chmod=D755,F644 "${DIST}/" "${TARGET}:/opt/dsa/web/dist/"
else
    # Windows 的 Git Bash 默认没有 rsync，这里退成 tar 管道：
    # 先清空远端 dist（只删内部，不删目录本身），再整包解过去。
    echo "    （本机无 rsync，改用 tar 管道同步）"
    ssh "${TARGET}" "mkdir -p /opt/dsa/web/dist && find /opt/dsa/web/dist -mindepth 1 -delete"
    tar -C "${DIST}" -czf - . \
        | ssh "${TARGET}" "tar -xzf - -C /opt/dsa/web/dist && chmod -R a+rX /opt/dsa/web/dist"
fi

echo "==> 确保数据库与表结构"
# schema.sql 是幂等的（CREATE DATABASE IF NOT EXISTS + 全部 CREATE TABLE IF NOT EXISTS），
# 所以每次发版都跑一遍：首次部署自动建库，之后等于一次廉价的结构校验。
# 注意它只「创建不存在的表」，不会给已有表补列——加列要单独写 ALTER（PLAN/schema.sql 注释里有）。
scp -q "${ROOT}/dsa-log-server/src/main/resources/db/schema.sql" "${TARGET}:/opt/dsa/api/schema.sql"
ssh "${TARGET}" 'set -a; . /opt/dsa/api/.env; set +a; \
    docker exec -i -e MYSQL_PWD="$DB_PASSWORD" "${MYSQL_CONTAINER:-mysql8}" \
    mysql -u"$DB_USER" < /opt/dsa/api/schema.sql' \
    || { echo "!!! 建库失败，检查 .env 里的口令与容器名" >&2; exit 1; }
echo "    表结构就绪"

echo "==> 重建容器"
ssh "${TARGET}" "cd /opt/dsa/api && IMAGE_TAG=${TAG} docker compose up -d --build"

echo "==> 等待健康检查（最多 90s）"
for i in $(seq 1 30); do
    sleep 3
    if ssh "${TARGET}" "curl -fsS -m 3 http://127.0.0.1:8080/api/health >/dev/null 2>&1"; then
        echo "==> 部署完成，镜像 tag：${TAG}"
        echo "    回滚：deploy/rollback.sh ${TARGET} <上一个 tag>   （docker images 里查）"
        exit 0
    fi
done

echo "!!! 健康检查超时，看日志：ssh ${TARGET} 'docker logs --tail 120 dsa-api'" >&2
exit 1
