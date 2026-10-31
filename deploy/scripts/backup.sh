#!/usr/bin/env bash
# Copia de seguridad diaria de la base de datos del panel. Se programa con cron (ver README):
#   15 4 * * * /opt/stacks/scripts/backup.sh >> /var/log/panel-backup.log 2>&1
#
# Guarda un volcado comprimido por día en /var/backups/panel y borra los de más de 14 días.
set -euo pipefail

BACKUP_DIR=/var/backups/panel
KEEP_DAYS=14
FILE="$BACKUP_DIR/panel-$(date +%F).sql.gz"

mkdir -p "$BACKUP_DIR"
docker compose -f /opt/stacks/panel/compose.yaml exec -T db \
	pg_dump -U controlpanel --clean --if-exists controlpanel | gzip > "$FILE.tmp"
# Solo se da por buena si el volcado terminó bien
mv "$FILE.tmp" "$FILE"
find "$BACKUP_DIR" -name 'panel-*.sql.gz' -mtime +"$KEEP_DAYS" -delete

echo "$(date -Is) Copia guardada: $FILE ($(du -h "$FILE" | cut -f1))"
