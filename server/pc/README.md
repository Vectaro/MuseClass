# MuseClass — деплой на робочий ПК (Windows 10/11)

Тільки деплой: `compose.yaml`, `.env.example`, `backup.ps1` і ця інструкція.
Код, API й перевірки — спільні, у [`server/`](../README.md); compose збирає
образ звідти (`build: ..`). Деплой малини — [`../pi/`](../pi/README.md). Веде
цю теку чат про сервер.

## Запуск на Windows 10/11 (робочий ПК)

Потрібно:

- Windows 10 22H2+ або Windows 11 23H2+, 64-біт; Home теж підходить.
- Ввімкнена віртуалізація в BIOS/UEFI.
- Docker Desktop з бекендом WSL 2.
- Tailscale для Windows.
- Git.

Усі команди нижче — у PowerShell.

```powershell
git clone https://github.com/Vectaro/MuseClass C:\MuseClass
cd C:\MuseClass\server\pc
Copy-Item .env.example .env
notepad .env    # DB_PASSWORD і JWT_SECRET — довгі випадкові рядки, 32+ символи
docker compose up -d --build
curl.exe -s http://localhost:8080/actuator/health    # {"status":"UP",...}
```

Криптостійкий випадковий рядок без openssl (працює і в Windows PowerShell 5.1):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($b); [Convert]::ToBase64String($b)
```

Назовні (PowerShell від адміністратора):

```powershell
tailscale funnel --bg 8080
tailscale funnel status    # публічна адреса https://<машина>.<tailnet>.ts.net
```

Якщо Funnel ще не дозволений, команда дасть посилання на адмінку. Там же
вмикаються MagicDNS і HTTPS-сертифікати. Сертифікат справжній, тож Android
прийме його без винятків у `network_security_config`.

Порт 8080 прив'язаний до `127.0.0.1`: з локальної мережі до API напряму не
достукатись, тільки через Funnel. Postgres назовні не відкритий взагалі.

### Щоб сервер не падав

- **Автостарт Docker.** У налаштуваннях Docker Desktop (General) увімкни старт
  при вході в систему. Контейнери з `restart: unless-stopped` піднімуться самі.
  Docker Desktop живе в сесії користувача, тож сервер працює, поки ти
  залогінений. Після перезавантаження, зокрема через оновлення Windows, треба
  увійти в систему.
- **Сон.** Сплячий ПК — мертвий сервер. Вимкни сон від мережі:
  `powercfg /change standby-timeout-ac 0` і `powercfg /change hibernate-timeout-ac 0`.
- **Tailscale** працює як служба Windows і стартує з системою сам;
  `--bg` зберігає Funnel між перезапусками.
- **Пам'ять.** WSL 2 може з'їсти багато RAM. Якщо ПК важко, обмеж її у
  `%UserProfile%\.wslconfig`:
  ```
  [wsl2]
  memory=4GB
  ```
  Потім виконай `wsl --shutdown` і перезапусти Docker Desktop.

### Оновлення і бекап

Оновлення: `git pull; docker compose up -d --build`. Міграції бази Flyway
накочує сам при старті.

Бекап: `.\backup.ps1` кладе дамп у `backups\` і тримає останні 14. Щоденний
запуск через Планувальник завдань:

```powershell
$a = New-ScheduledTaskAction -Execute powershell.exe `
  -Argument '-NoProfile -ExecutionPolicy Bypass -File "C:\MuseClass\server\pc\backup.ps1"'
Register-ScheduledTask -TaskName MuseClassBackup -Action $a -Trigger (New-ScheduledTaskTrigger -Daily -At 3:30)
```

Відновлення — у коментарі в самому `backup.ps1`. Дампи бажано час від часу
копіювати кудись, крім цього ж диска.

`.gitattributes` (спільний, у корені репо й `server/`) тримає `Dockerfile`, `.sh` і YAML з LF-кінцями рядків навіть
на Windows. Інакше git з `autocrlf` зламав би їх для Linux-контейнерів.

### Пам'ять

На ПК за замовчуванням heap API 1 ГБ і `shared_buffers` Postgres 512 МБ.
Разом із самим Postgres це близько 2 ГБ усередині WSL. WSL 2 за замовчуванням
бере до половини RAM ПК, тож на 8 ГБ запас є. Інші значення — у `.env`
(`API_HEAP`, `PG_SHARED_BUFFERS`), потім `docker compose up -d`, перезбирати не
треба.

### Ім'я compose-проєкту

`name: museclass-pc` у `compose.yaml`. Том з базою — `museclass-pc_pgdata`, образ —
`museclass-pc-api`. Окреме ім'я — щоб деплой малини, запущений на тому ж ПК
для перевірки, не поділив з цим базу.

## Розробка

На тому самому ПК, де крутиться бойовий сервер, dev-версію запускай на
іншому порту й з окремою базою, щоб не зачепити справжні дані:

```powershell
docker run -d --name museclass-dev-db -p 5433:5432 `
  -e POSTGRES_DB=museclass -e POSTGRES_USER=museclass -e POSTGRES_PASSWORD=museclass postgres:16-alpine

$env:DB_URL = "jdbc:postgresql://localhost:5433/museclass"
$env:JWT_SECRET = "dev-secret-dev-secret-dev-secret-123"
$env:SERVER_PORT = "8081"
cd C:\MuseClass\server
.\mvnw.cmd spring-boot:run   # або Run у IntelliJ з тими ж змінними
```

Емулятор Android бачить комп'ютер за адресою `10.0.2.2` (dev — `:8081`), телефон
у тій самій Wi-Fi — за локальною IP комп'ютера. Бойовий сервер — за адресою
Funnel. Базову адресу API в застосунку тримай у `BuildConfig` (через
`buildConfigField` у Gradle), щоб перемикання було одним рядком.

Тести: `.\mvnw.cmd verify` з `C:\MuseClass\server` (код спільний, у корені `server/`).
Наскрізний `ApiFlowTest` піднімає свій Postgres через
Testcontainers, тому Docker Desktop має бути запущений. Бойову базу тест не
чіпає.

## Не перевірено

`backup.ps1` і PowerShell-команди з цього README ще не запускались: там, де вони
писалися, PowerShell не було. Перевірити після першого запуску на ПК.
