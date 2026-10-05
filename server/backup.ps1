# Дамп бази MuseClass у backups\ (тримає останні 14).
#
# Щоденний запуск — див. README, розділ «Оновлення і бекап».
#
# Відновлення з дампу:
#   docker compose cp backups\<файл>.dump db:/tmp/restore.dump
#   docker compose exec -T db pg_restore -U museclass -d museclass --clean --if-exists /tmp/restore.dump
#
# Дамп пишеться у файл усередині контейнера і копіюється назовні:
# Windows PowerShell 5.1 псує бінарний вивід при перенаправленні через >.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force -Path backups | Out-Null

$file = Join-Path 'backups' ("museclass-{0}.dump" -f (Get-Date -Format 'yyyy-MM-dd-HHmm'))

docker compose exec -T db pg_dump -U museclass -Fc -f /tmp/museclass.dump museclass
if ($LASTEXITCODE -ne 0) { throw "pg_dump завершився з кодом $LASTEXITCODE" }

docker compose cp db:/tmp/museclass.dump $file
if ($LASTEXITCODE -ne 0) { throw "docker compose cp завершився з кодом $LASTEXITCODE" }

docker compose exec -T db rm -f /tmp/museclass.dump | Out-Null

"{0} ok {1}" -f (Get-Date -Format s), $file

Get-ChildItem -Path 'backups\museclass-*.dump' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip 14 |
    Remove-Item
