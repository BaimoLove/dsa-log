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

# 首次上线最容易踩的坑：compose 把 .htpasswd 挂进容器，而 Docker 遇到
# 「宿主机上不存在的文件」会建一个**同名目录**，nginx 随即报
# auth_basic_user_file 是个目录 —— 报错信息完全指不到根因。所以先挡一道。
if ! ssh "${TARGET}" "test -f /opt/dsa/nginx/.htpasswd"; then
    cat >&2 <<EOF
!!! 缺少 /opt/dsa/nginx/.htpasswd（basic auth 账号文件），nginx 会起不来。

先生成，二选一（宿主有 openssl 就不用拉镜像，细节见 RUNBOOK 第 2 节）：

  有 openssl：
    ssh ${TARGET} 'mkdir -p /opt/dsa/nginx'
    ssh ${TARGET} 'openssl passwd -apr1 "换成访问密码" | sed "s/^/dsa:/" > /opt/dsa/nginx/.htpasswd'

  没有 openssl：
    ssh ${TARGET} 'mkdir -p /opt/dsa/nginx'
    ssh ${TARGET} 'docker run --rm httpd:2.4-alpine htpasswd -nbB dsa "换成访问密码" > /opt/dsa/nginx/.htpasswd'

  补权限（两种都要）：
    ssh ${TARGET} 'chown root:101 /opt/dsa/nginx/.htpasswd && chmod 640 /opt/dsa/nginx/.htpasswd'

EOF
    exit 1
fi

# 编排与 nginx 配置跟着仓库走：改完配置直接发版，不会出现「服务器上还是旧配置」
scp -q "${ROOT}/deploy/Dockerfile" "${ROOT}/deploy/compose.yaml" "${TARGET}:/opt/dsa/api/"
scp -q "${ROOT}/deploy/nginx/dsa.conf" "${TARGET}:/opt/dsa/nginx/dsa.conf"

# 同样要防「文件被 Docker 建成目录」：万一远端 .htpasswd 是个目录，这里直接说清楚
if ssh "${TARGET}" "test -d /opt/dsa/nginx/.htpasswd"; then
    echo "!!! /opt/dsa/nginx/.htpasswd 是个目录（Docker 挂载不存在的文件时会这样建）。" >&2
    echo "    删除后按第 2 节重新生成：ssh ${TARGET} 'rm -rf /opt/dsa/nginx/.htpasswd'" >&2
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
