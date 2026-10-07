# Дамп бази MuseClass у backups\ (тримає останні 14).
#
#   .\backup.ps1                                   # бойова база (сервіс db проєкту museclass-pc)
#   .\backup.ps1 -Container museclass-dev-db -OutDir backups\dev    # dev-база
#
# Щоденний запуск — див. README, розділ «Оновлення і бекап». Кожен запуск
# дописує рядок у backup.log поруч зі скриптом, і вдалий, і з помилкою.
#
# Відновлення з дампу — див. README, «Відновлення з бекапу».
#
# Дамп пишеться у файл усередині контейнера і копіюється назовні:
# Windows PowerShell 5.1 псує бінарний вивід при перенаправленні через >.

param(
    # Контейнер Postgres. Порожньо — сервіс db проєкту museclass-pc з compose.yaml поруч.
    [string]$Container = '',
    [string]$OutDir = 'backups',
    [int]$Keep = 14
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Log([string]$line) {
    $entry = '{0} {1}' -f (Get-Date -Format s), $line
    Add-Content -Path (Join-Path $PSScriptRoot 'backup.log') -Value $entry -Encoding UTF8
    $entry
}

try {
    if (-not $Container) {
        $Container = docker compose ps -q db
        if ($LASTEXITCODE -ne 0 -or -not $Container) { throw 'не знайшов запущений контейнер db проєкту museclass-pc' }
    }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $file = Join-Path $OutDir ('museclass-{0}.dump' -f (Get-Date -Format 'yyyy-MM-dd-HHmmss'))

    docker exec $Container pg_dump -U museclass -Fc -f /tmp/museclass.dump museclass
    if ($LASTEXITCODE -ne 0) { throw "pg_dump завершився з кодом $LASTEXITCODE" }

    docker cp -q "${Container}:/tmp/museclass.dump" $file
    if ($LASTEXITCODE -ne 0) { throw "docker cp завершився з кодом $LASTEXITCODE" }

    docker exec $Container rm -f /tmp/museclass.dump | Out-Null

    $size = (Get-Item $file).Length
    if ($size -lt 1024) { throw "дамп підозріло малий: $size байт" }

    Log ('ok {0} ({1:N0} КБ)' -f $file, ($size / 1KB))

    Get-ChildItem -Path (Join-Path $OutDir 'museclass-*.dump') |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $Keep |
        Remove-Item
}
catch {
    Log "ПОМИЛКА: $($_.Exception.Message)"
    exit 1
}
