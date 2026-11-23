#!/usr/bin/env bash
# Daily backup of the SaathChalo database and uploads, kept for 14 days in deploy/backups/.
# Run on the server from the deploy folder, e.g. from cron at 02:30:
#   30 2 * * * cd /opt/saathchalo/deploy && ./backup.sh >> backups/backup.log 2>&1
# Copy deploy/backups to another place (another server, or encrypted cloud storage) regularly: a backup that lives
# only on the same server is lost with it. Test a restore once a month (docs/DEPLOYMENT.md).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
day=$(date +%F)

# The database, in PostgreSQL's compressed custom format (restore with pg_restore).
docker compose exec -T db pg_dump -U saathchalo -d saathchalo -Fc > "backups/db-$day.dump"

# Uploaded pictures and (encrypted) documents.
docker compose run --rm --no-deps -v "$(pwd)/backups:/backup" --entrypoint tar app \
  -czf "/backup/uploads-$day.tar.gz" -C /data uploads

# Keep two weeks.
find backups -name 'db-*.dump' -mtime +14 -delete
find backups -name 'uploads-*.tar.gz' -mtime +14 -delete
echo "$(date -Is) backup done: db-$day.dump, uploads-$day.tar.gz"
