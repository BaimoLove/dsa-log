#!/usr/bin/env bash
# deploy/rollback.sh —— 秒级回滚（改 tag 重启，不重新构建）
#
# 用法：
#   deploy/rollback.sh root@1.2.3.4              # 列出可回滚的 tag
#   deploy/rollback.sh root@1.2.3.4 202610211530 # 切到指定 tag
#
# 前提：每次发版用了不同的 IMAGE_TAG（deploy.sh 就是这么做的）。
# 如果发版时没打 tag（全是 latest），这个脚本无解——build 覆盖了 latest。
set -euo pipefail

TARGET="${1:-}"
TAG="${2:-}"
if [ -z "${TARGET}" ]; then
    echo "用法：$0 user@host [image-tag]" >&2
    exit 2
fi

if [ -z "${TAG}" ]; then
    echo "==> 本地可用的镜像 tag（新→旧）："
    ssh "${TARGET}" "docker images --format '{{.Tag}}\t{{.CreatedAt}}' dsa-api | sort -k2 -r"
    echo ""
    echo "再执行：$0 ${TARGET} <tag>"
    exit 0
fi

echo "==> 切回 dsa-api:${TAG}"
ssh "${TARGET}" "cd /opt/dsa/api && IMAGE_TAG=${TAG} docker compose up -d"

echo "==> 等待健康检查"
for i in $(seq 1 20); do
    sleep 3
    if ssh "${TARGET}" "curl -fsS -m 3 http://127.0.0.1:8080/api/health >/dev/null 2>&1"; then
        echo "==> 已回滚到 ${TAG}"
        exit 0
    fi
done

echo "!!! 回滚后健康检查仍失败，看日志：ssh ${TARGET} 'docker logs --tail 120 dsa-api'" >&2
exit 1
