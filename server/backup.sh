#!/usr/bin/env sh
# Дамп бази в backups/ (або в BACKUP_DIR). Для cron (crontab -e, щодня о 03:30;
# шлях підстав свій — у свіжій Raspberry Pi OS юзера pi за замовчуванням немає):
#   30 3 * * * cd $HOME/museclass-server && ./backup.sh >> backup.log 2>&1
# На малині з SD-картою дампи краще класти на інший носій, напр. флешку:
#   30 3 * * * cd $HOME/museclass-server && BACKUP_DIR=/mnt/usb/museclass ./backup.sh >> backup.log 2>&1
# Відновлення:
#   docker compose exec -T db pg_restore -U museclass -d museclass --clean --if-exists < backups/<файл>.dump
set -eu
cd "$(dirname "$0")"
dir="${BACKUP_DIR:-backups}"
mkdir -p "$dir"
f="$dir/museclass-$(date +%F-%H%M).dump"
# Спершу в .part: якщо дамп впаде (або зникне світло), недописаний файл
# не прикинеться бекапом і не витіснить живі з ротації
docker compose exec -T db pg_dump -U museclass -Fc museclass > "$f.part"
mv "$f.part" "$f"
echo "$(date -Is) ok $f"
# Лишаємо останні 14 дампів
ls -1t "$dir"/museclass-*.dump | tail -n +15 | xargs -r rm --
