#!/bin/bash
# Inner Cosmos — Database Backup Script
# Run daily via cron: 0 3 * * * /app/scripts/backup/backup.sh

set -e

BACKUP_DIR="/backup"
DATE=$(date +%Y%m%d_%H%M%S)
CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-inner-cosmos-db}"
POSTGRES_DB="${POSTGRES_DB:-inner_cosmos}"
POSTGRES_USER="${POSTGRES_USER:-inner_cosmos}"
RETENTION_DAYS=30

echo "Starting backup at $(date)"

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR"

# PostgreSQL logical backup. The password is injected only into the container process.
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}" \
    "$CONTAINER_NAME" pg_dump \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --format=custom \
    --no-owner \
    --no-privileges > "$BACKUP_DIR/innercosmos_${DATE}.dump"

# Verify backup was created and has content
if [ -f "$BACKUP_DIR/innercosmos_${DATE}.dump" ] && [ $(stat -f%z "$BACKUP_DIR/innercosmos_${DATE}.dump" 2>/dev/null || stat -c%s "$BACKUP_DIR/innercosmos_${DATE}.dump") -gt 1024 ]; then
    echo "Backup successful: $BACKUP_DIR/innercosmos_${DATE}.dump"
else
    echo "Backup FAILED: file missing or too small"
    exit 1
fi

# Clean up old backups (keep last 30 days)
find "$BACKUP_DIR" -name "innercosmos_*.dump" -mtime +$RETENTION_DAYS -delete
echo "Old backups cleaned (retention: $RETENTION_DAYS days)"

# Also backup H2 file-based DB (for dev environment)
if [ -f "./data/innercosmos.mv.db" ]; then
    cp "./data/innercosmos.mv.db" "$BACKUP_DIR/innercosmos_${DATE}.db"
    echo "H2 DB backup also created"
fi

echo "Backup completed at $(date)"
