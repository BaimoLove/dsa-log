#!/usr/bin/env bash
# /opt/dsa/backup/dump.sh —— 每日全量备份，保留 14 天
#
# 走 docker exec 而不是宿主 mysqldump：这台机器上 MySQL 本来就是容器，
# 宿主不必再装一份 mysql 客户端（少一个要维护的东西，版本也天然一致）。
#
# 两个参数选择的原因：
#   --single-transaction  InnoDB 一致性快照，不锁表（加 --lock-tables 会阻塞写入）
#   --no-tablespaces      没有 PROCESS 权限时也能导，少一类失败原因
#
# 用法：cron 里 30 3 * * * /opt/dsa/backup/dump.sh >> /var/log/dsa-backup.log 2>&1
# 口令从 /opt/dsa/backup/.env 读，再用 MYSQL_PWD 环境变量传给容器，不出现在 ps 里
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/dsa/backup}"
DB_NAME="${DB_NAME:-dsa_log}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.gz"

# 口令只有一个真源：/opt/dsa/api/.env（compose 也从那里读）。
# /opt/dsa/backup/.env 存在时优先用它，方便单独换备份账号。
for envfile in "${BACKUP_DIR}/.env" "/opt/dsa/api/.env"; do
    if [ -f "${envfile}" ]; then
        set -a
        # shellcheck disable=SC1090
        . "${envfile}"
        set +a
        break
    fi
done
DB_USER="${DB_USER:-root}"
: "${DB_PASSWORD:?DB_PASSWORD 未设置，写在 /opt/dsa/api/.env 或 ${BACKUP_DIR}/.env 里}"

# 先探活再导：mysqldump 失败时 gzip 仍会产出一个小文件，
# 等到要恢复那天才发现是空包就晚了
if ! docker exec -e MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
        mysqladmin -u"${DB_USER}" ping --silent >/dev/null 2>&1; then
    echo "[$(date '+%F %T')] FATAL 数据库 ping 不通（容器 ${MYSQL_CONTAINER}），跳过本次备份"
    exit 1
fi

echo "[$(date '+%F %T')] 开始备份 ${DB_NAME} → ${OUT}"
docker exec -e MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
    mysqldump -u"${DB_USER}" \
    --single-transaction --no-tablespaces --quick \
    --default-character-set=utf8mb4 \
    "${DB_NAME}" | gzip -6 > "${OUT}"

# 体积检查：正常库不可能小于 1KB，小于就是导失败了
SIZE=$(stat -c%s "${OUT}" 2>/dev/null || echo 0)
if [ "${SIZE}" -lt 1024 ]; then
    echo "[$(date '+%F %T')] FATAL 备份文件只有 ${SIZE} 字节，疑似失败，保留现场 ${OUT}"
    exit 1
fi

echo "[$(date '+%F %T')] 完成，${SIZE} 字节"

# 轮转：只删「本脚本产出的、超过保留期」的文件，绝不用通配符横扫目录
find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${DB_NAME}-*.sql.gz" -mtime "+${KEEP_DAYS}" -print -delete
