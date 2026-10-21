#!/usr/bin/env bash
# /opt/dsa/backup/dump.sh —— 每日 mysqldump，保留 14 天
#
# 两个参数选择的原因：
#   --single-transaction  InnoDB 一致性快照，不锁表（加 --lock-tables 会阻塞写入）
#   --no-tablespaces      没有 PROCESS 权限时也能导，少一类失败原因
#
# 用法：cron 里 30 3 * * * /opt/dsa/backup/dump.sh >> /var/log/dsa-backup.log 2>&1
# 口令从 /opt/dsa/backup/.my.cnf 读，不要写成命令行参数（ps 可见）
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/dsa/backup}"
DB_NAME="${DB_NAME:-dsa_log}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.gz"

# 先探活再导：库连不上时 mysqldump 的报错会被 gzip 吞掉，留下一个 20 字节的坏包
if ! mysqladmin --defaults-file=/opt/dsa/backup/.my.cnf ping --silent >/dev/null 2>&1; then
    echo "[$(date '+%F %T')] FATAL 数据库 ping 不通，跳过本次备份"
    exit 1
fi

echo "[$(date '+%F %T')] 开始备份 ${DB_NAME} → ${OUT}"
mysqldump --defaults-file=/opt/dsa/backup/.my.cnf \
    --single-transaction --no-tablespaces --quick \
    --default-character-set=utf8mb4 \
    "${DB_NAME}" | gzip -6 > "${OUT}"

# 体积检查：一个正常库不可能小于 1KB，小于就是导失败了
SIZE=$(stat -c%s "${OUT}")
if [ "${SIZE}" -lt 1024 ]; then
    echo "[$(date '+%F %T')] FATAL 备份文件只有 ${SIZE} 字节，疑似失败，保留现场 ${OUT}"
    exit 1
fi

echo "[$(date '+%F %T')] 完成，${SIZE} 字节"

# 轮转：只删「本脚本产出的、超过保留期」的文件，绝不用通配符横扫目录
find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${DB_NAME}-*.sql.gz" -mtime "+${KEEP_DAYS}" -print -delete
