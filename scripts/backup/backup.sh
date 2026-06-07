#!/bin/bash
# Inner Cosmos — Database Backup Script
# Run daily via cron: 0 3 * * * /app/scripts/backup/backup.sh

set -e

BACKUP_DIR="/backup"
DATE=$(date +%Y%m%d_%H%M%S)
CONTAINER_NAME="inner-cosmos-db"
RETENTION_DAYS=30

echo "Starting backup at $(date)"

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR"

# MySQL dump with compression
docker exec "$CONTAINER_NAME" mysqldump \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    -u root \
    -p"${MYSQL_ROOT_PASSWORD}" \
    innercosmos | gzip > "$BACKUP_DIR/innercosmos_${DATE}.sql.gz"

# Verify backup was created and has content
if [ -f "$BACKUP_DIR/innercosmos_${DATE}.sql.gz" ] && [ $(stat -f%z "$BACKUP_DIR/innercosmos_${DATE}.sql.gz" 2>/dev/null || stat -c%s "$BACKUP_DIR/innercosmos_${DATE}.sql.gz") -gt 1024 ]; then
    echo "Backup successful: $BACKUP_DIR/innercosmos_${DATE}.sql.gz"
else
    echo "Backup FAILED: file missing or too small"
    exit 1
fi

# Clean up old backups (keep last 30 days)
find "$BACKUP_DIR" -name "innercosmos_*.sql.gz" -mtime +$RETENTION_DAYS -delete
echo "Old backups cleaned (retention: $RETENTION_DAYS days)"

# Also backup H2 file-based DB (for dev environment)
if [ -f "./data/innercosmos.mv.db" ]; then
    cp "./data/innercosmos.mv.db" "$BACKUP_DIR/innercosmos_${DATE}.db"
    echo "H2 DB backup also created"
fi

echo "Backup completed at $(date)"