# MuseClass — сервер

REST API для застосунку MuseClass: акаунти, класи з кодами, партитури MusicXML
з рівнями доступу, видача нот класу, публічний каталог з ранжуванням під інструмент.

Spring Boot 4.1 · Java 21 · PostgreSQL 16 · Flyway · JWT (HS256) · Docker Compose.
Назовні — через Tailscale Funnel, білий IP не потрібен.

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
cd C:\MuseClass\server
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
  -Argument '-NoProfile -ExecutionPolicy Bypass -File "C:\MuseClass\server\backup.ps1"'
Register-ScheduledTask -TaskName MuseClassBackup -Action $a -Trigger (New-ScheduledTaskTrigger -Daily -At 3:30)
```

Відновлення — у коментарі в самому `backup.ps1`. Дампи бажано час від часу
копіювати кудись, крім цього ж диска.

`.gitattributes` тримає `Dockerfile`, `.sh` і YAML з LF-кінцями рядків навіть
на Windows. Інакше git з `autocrlf` зламав би їх для Linux-контейнерів.

## Запуск на Linux (малина)

Налаштування розраховані на найслабше залізо, з яким сервер має працювати:
Raspberry Pi 4 на 2 ГБ з microSD. На сильнішій машині той самий `compose.yaml`
працює так само, лише в `.env` піднімається пам'ять (див. нижче).

| | Мінімум | Оптимально | Найкраще |
|---|---|---|---|
| Плата | Pi 4, 2 ГБ | Pi 5, 4 ГБ | Pi 5, 8 ГБ |
| Диск | microSD A2, 32+ ГБ | SSD (USB 3 або NVMe HAT) | NVMe HAT |
| Живлення | офіційне | офіційне 27 Вт + Active Cooler | те саме + UPS на малину й роутер |

### Система

Потрібна 64-бітна Raspberry Pi OS Lite: `uname -m` має показати `aarch64`.
На 32-бітній образ не збереться.

На 2 ГБ потрібен swap хоча б на 1 ГБ, інакше перша збірка може впертись у
пам'ять. Перевір `free -h`. Якщо swap менший, у системах на dphys-swapfile
виправ `CONF_SWAPSIZE=1024` у `/etc/dphys-swapfile` і виконай
`sudo systemctl restart dphys-swapfile`. У свіжіших образах swap на zram
налаштований одразу.

```sh
curl -fsSL https://get.docker.com | sh          # Docker Engine + compose
sudo usermod -aG docker $USER                   # перезайди після цього
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

### Запуск

```sh
git clone https://github.com/Vectaro/MuseClass ~/MuseClass && cd ~/MuseClass/server
cp .env.example .env && nano .env    # DB_PASSWORD, JWT_SECRET: openssl rand -base64 48
docker compose up -d --build         # перша збірка на Pi 4 — хвилин 10–15
docker compose ps                    # api має стати healthy (дай йому до 3 хв)
curl -s http://localhost:8080/actuator/health
sudo tailscale funnel --bg 8080
```

Для переїзду з іншої машини бери її `.env` цілком, з тим самим `JWT_SECRET`,
інакше видані токени стануть недійсні.

### Пам'ять під залізо

За замовчуванням heap API 384 МБ, `shared_buffers` Postgres 128 МБ. Разом із
системою це близько 1 ГБ, тож на 2 ГБ лишається запас. На сильнішій машині
підніми в `.env`:

- 4 ГБ: `API_HEAP=768m`, `PG_SHARED_BUFFERS=512MB`
- 8 ГБ і більше: `API_HEAP=1g`, `PG_SHARED_BUFFERS=1GB`

Потім `docker compose up -d`, перезбирати не треба.

Heap задається явним `-Xmx`, а не відсотком від пам'яті. На Raspberry Pi OS
ліміти пам'яті контейнерів можуть бути вимкнені в ядрі, і тоді JVM рахувала б
відсоток від усієї RAM разом із тим, що потрібне Postgres.

### Живучість

- Контейнери з `restart: unless-stopped` піднімаються самі після
  перезавантаження, Docker і tailscaled стартують як служби systemd, а `--bg`
  зберігає Funnel.
- Логи контейнерів обмежені до 30 МБ на сервіс. Postgres налаштований на рідші
  чекпоінти й стиснений WAL: менше записів на SD-карту без втрати надійності
  commit'ів.
- Раптове вимкнення світла Postgres переживає сам (WAL), а от microSD при
  цьому іноді псується цілком. Від цього рятує SSD або UPS, а бекап на інший
  носій потрібен у будь-якому разі.

### Бекап

`./backup.sh` кладе дамп у `backups/` і тримає останні 14 штук.
Змінна `BACKUP_DIR` дозволяє писати на інший носій. Рядки для cron — у
коментарі скрипта. На малині з SD-картою класти бекапи на ту саму карту
майже марно: помре карта — помруть і вони.

### Збірка на ПК замість малини

Якщо збірка на малині надто повільна, образ можна зібрати на ПК під arm64:

```sh
docker buildx build --platform linux/arm64 -t museclass-api:latest --load .
docker save museclass-api:latest | gzip > api-arm64.tar.gz
# скопіювати на малину, там (ім'я образу museclass-api задає `name:` у compose.yaml):
gunzip -c api-arm64.tar.gz | docker load && docker compose up -d --no-build
```

## Розробка

На тому самому ПК, де крутиться бойовий сервер, dev-версію запускай на
іншому порту й з окремою базою, щоб не зачепити справжні дані:

```powershell
docker run -d --name museclass-dev-db -p 5433:5432 `
  -e POSTGRES_DB=museclass -e POSTGRES_USER=museclass -e POSTGRES_PASSWORD=museclass postgres:16-alpine

$env:DB_URL = "jdbc:postgresql://localhost:5433/museclass"
$env:JWT_SECRET = "dev-secret-dev-secret-dev-secret-123"
$env:SERVER_PORT = "8081"
mvn spring-boot:run       # або Run у IntelliJ з тими ж змінними
```

Емулятор Android бачить комп'ютер за адресою `10.0.2.2` (dev — `:8081`), телефон
у тій самій Wi-Fi — за локальною IP комп'ютера. Бойовий сервер — за адресою
Funnel. Базову адресу API в застосунку тримай у `BuildConfig` (через
`buildConfigField` у Gradle), щоб перемикання було одним рядком.

Тести: `mvn verify`. Наскрізний `ApiFlowTest` піднімає свій Postgres через
Testcontainers, тому Docker Desktop має бути запущений. Бойову базу тест не
чіпає.

## API

Усе під `/api`, JSON, UTF-8. Крім реєстрації та входу, кожен запит несе
`Authorization: Bearer <token>`. Токен живе 30 днів (`JWT_TTL`).

Помилки приходять у форматі `application/problem+json`, поле `detail` —
готовий текст українською, його можна показувати користувачу як є.

| Код | Коли |
|---|---|
| 400 | некоректні дані (поле `detail` пояснює що саме) |
| 401 | немає токена, він прострочений, або невірний пароль |
| 403 | бачиш ресурс, але змінювати не маєш права |
| 404 | не існує **або** не маєш доступу (свідомо не розрізняємо) |
| 409 | пошта зайнята, спроба вступити у власний клас |
| 413 | файл більший за 5 МБ |
| 422 | файл не є коректним MusicXML |

### Акаунт

| Метод | Шлях | Тіло / параметри | Відповідь |
|---|---|---|---|
| POST | `/auth/register` | `{email, password, displayName}` | 201 `{token, expiresAt, userId, displayName}` |
| POST | `/auth/login` | `{email, password}` | `{token, expiresAt, userId, displayName}` |
| GET | `/me` | | `{id, email, displayName, instruments[], createdAt}` |
| PATCH | `/me` | `{displayName}` | профіль |
| PUT | `/me/instruments` | `{instruments: ["trumpet", ...]}` | профіль |

Коди інструментів: `piano`, `guitar`, `voice`, `violin`, `trumpet`, `flute`,
`bass_guitar`, `drums`, `saxophone`, `bandura` — ті самі десять, що в прототипі.

### Класи

| Метод | Шлях | Тіло | Хто |
|---|---|---|---|
| POST | `/classes` | `{name, codePrefix?}` → 201 клас | будь-хто (стає викладачем) |
| POST | `/classes/join` | `{code}` → клас | будь-хто |
| GET | `/classes` | → мої класи (і ті, що веду, і ті, де вчуся) | |
| GET | `/classes/{id}` | → клас | учасник |
| PATCH | `/classes/{id}` | `{name}` | викладач |
| DELETE | `/classes/{id}` | → 204 | викладач |
| POST | `/classes/{id}/code` | `{codePrefix?}` → клас з новим кодом, старий перестає працювати | викладач |
| GET | `/classes/{id}/members` | → `[{userId, displayName, joinedAt}]` | учасник |
| DELETE | `/classes/{id}/members/{userId}` | → 204 | викладач; учень — тільки себе (вихід з класу) |

Клас: `{id, code, name, teacherId, teacherName, role, students, createdAt}`.
`role` — `teacher` або `student`. `code` приходить тільки викладачу, учню там `null`.

Код класу має формат `PNO-3A`. Префікс (три латинські літери) викладач може
задати сам, інакше він випадковий. Сервер прощає введення коду маленькими
літерами, без дефіса і з кириличними «двійниками» латиниці (Р, О, А з
української розкладки).

### Партитури

| Метод | Шлях | Що |
|---|---|---|
| POST | `/scores` | multipart: `file` + необов'язкові `title`, `composer`, `arranger`, `kind`, `rights`, `visibility` → 201 |
| GET | `/scores/mine` | мої партитури, будь-який доступ |
| GET | `/scores/{id}` | `{score, parts[], canEdit}` |
| GET | `/scores/{id}/file` | оригінальний `.musicxml` або `.mxl`; `ETag` + `If-None-Match` → 304 |
| PUT | `/scores/{id}/file` | multipart `file`: нова версія з редактора, партії перераховуються |
| PATCH | `/scores/{id}` | будь-яка підмножина `{title, composer, arranger, kind, rights, visibility}` |
| DELETE | `/scores/{id}` | 204, тільки автор |
| GET | `/catalog?q=&kind=&limit=30&offset=0` | публічний каталог |

Порожні поля при завантаженні беруться з файлу: назва — з `work-title` або
`movement-title`, автори — з `creator`. Партії та їхні інструменти сервер
визначає сам (за назвою партії, потім за MIDI-програмою).

- `visibility`: `private` (за замовчуванням), `class`, `public`
- `kind`: `classical`, `folk`, `cover`, `technique`, `other`
- `rights`: `public_domain`, `folk`, `arrangement`, `original`, `unknown`

**Авторські права.** `public` дозволено тільки з `rights` = `public_domain` або
`folk`, інакше 400. Це перевіряє і сервіс, і обмеження в самій базі.

**Каталог.** Партитури, де є партія під один з твоїх інструментів, ідуть першими
(`fits: true`), решта лишається в тих самих результатах. Пошук — за назвою,
композитором і аранжувальником, без урахування регістру.

Картка у списках: `{id, title, composer, arranger, kind, visibility, measures,
updatedAt, ownerId, ownerName, instruments[], fits}`.

### Видача класу і бібліотека

| Метод | Шлях | Що |
|---|---|---|
| PUT | `/classes/{classId}/scores/{scoreId}` | видати всім учням, 204 (викладач) |
| DELETE | `/classes/{classId}/scores/{scoreId}` | зняти з видачі, 204 (викладач) |
| GET | `/classes/{classId}/scores` | що видали в класі |
| GET | `/me/library` | що видали в усіх моїх класах |
| GET | `/me/saved` | збережені |
| PUT / DELETE | `/me/saved/{scoreId}` | зберегти / прибрати, 204 |

Бібліотека: `[{classId, className, assignedAt, score: <картка>}]`.

Видати можна свою партитуру або будь-яку публічну. Своя приватна при видачі
автоматично стає класною, інакше учні її не побачать.

## Правила доступу

Одне джерело правди — SQL-функція `score_readable(user, score)` у міграції:

- власник бачить свою партитуру завжди;
- `public` бачать усі;
- `class` бачать викладач і учні класів, яким її видали;
- `private` бачить тільки власник.

Змінювати й видаляти може тільки власник. Якщо автор зробить видану партитуру
приватною, клас її більше не бачить. Запис про видачу лишається, тож після
повернення доступу партитура з'явиться знову.

## Що перевірено і що ні

Перевірено:

- **SQL.** Схема та всі 40 запитів з репозиторіїв (рядки витягуються прямо з
  Java-коду) прогнані на справжньому PostgreSQL 16 через prepared statements.
  61 перевірка: доступи, видача, каталог з ранжуванням, кирилиця в пошуку,
  екранування `%` і `_`, обмеження бази.
- **Розбір MusicXML.** 25 модульних тестів плюс прогін по 377 реальних файлах
  з експортів MuseScore, Finale, Sibelius і Dorico: усі розбираються. Один з
  них мав `encoding='UTF-16'` у заголовку при UTF-8 вмісті — це тепер
  обробляється. Ще перевірено захист від XXE і zip-бомби.
- **Типи.** Код компілюється з заглушками API Spring.

Не перевірено: скрипти під Windows (`backup.ps1`, команди PowerShell у цьому
README) — PowerShell там, де це писалося, не було. І справжня збірка Maven та
запуск Spring. Maven Central був
недоступний там, де це писалося, тому перший `mvn verify` буде на твоїй
машині. Якщо щось не збереться, найімовірніше це назви стартерів Spring Boot 4
або модулів Testcontainers 2 у `pom.xml`.

## Чого поки немає

Оновлення токена (refresh) і вихід з усіх пристроїв, скидання пароля поштою,
обмеження частоти спроб входу, видалення акаунта, лічильник відтворень,
PDF- і MIDI-вкладення, видача окремим учням (тільки всьому класу).
