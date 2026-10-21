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

echo "==> 上传后端 jar（tag ${TAG}）"
# compose 里 COPY app.jar，所以远端固定叫 app.jar；版本信息靠 image tag 保留
scp -q "${JAR}" "${TARGET}:/opt/dsa/api/app.jar"

echo "==> 同步前端静态资源"
# --delete 让被删掉的旧 chunk 一起清走，否则 dist 里会越堆越多
rsync -az --delete "${DIST}/" "${TARGET}:/opt/dsa/web/dist/"

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
