# MuseClass — сервер

REST API для застосунку MuseClass: акаунти, класи з кодами, партитури MusicXML
з рівнями доступу, видача нот класу, публічний каталог з ранжуванням під інструмент.

Spring Boot 4.1 · Java 21 · PostgreSQL 16 · Flyway · JWT (HS256) · Docker Compose.
Назовні — через Tailscale Funnel, білий IP не потрібен.

## Структура

Тут лише спільне: `pom.xml`, `src/`, `Dockerfile`, `dev/`. Деплой — окремо на
машину, у кожній теці 4 файли (`compose.yaml` з `build: ..`, `.env.example`,
скрипт бекапу, README). Специфікація —
[`docs/stan/server-layout.md`](../docs/stan/server-layout.md).

| Тека | Машина | Веде |
|---|---|---|
| [`pc/`](pc/README.md) | робочий ПК, Windows 10/11 | чат про сервер |
| [`pi/`](pi/README.md) | малина і будь-який Linux (NUC) | чат про малину |

`docker compose` запускається з `pc/` або `pi/`, Maven — звідси, з `server/`.
Правка спільних файлів зачіпає обидві машини — окремим комітом з позначкою
«спільне».

## Розробка і тести

З цієї теки: `./mvnw verify` (Windows: `.\mvnw.cmd verify`). Maven ставити не
треба — wrapper сам завантажить Maven 3.9.16. Наскрізний `ApiFlowTest` піднімає свій Postgres через
Testcontainers, тому Docker має бути запущений. Бойову базу тест не чіпає.
Запуск dev-версії поруч із бойовою на тому ж ПК — у [`pc/`](pc/README.md),
на Linux — у [`pi/`](pi/README.md).

Емулятор Android бачить комп'ютер за адресою `10.0.2.2`, телефон у тій самій
Wi-Fi — за локальною IP комп'ютера, бойовий сервер — за адресою Funnel. Базову
адресу API в застосунку тримай у `BuildConfig` (через `buildConfigField` у
Gradle), щоб перемикання було одним рядком.

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
- **Збірка.** 2026-10-06 `mvnw verify` на робочому ПК (Windows, JDK 21,
  Docker Desktop) пройшов з першого разу: 26 тестів, зокрема `ApiFlowTest` на
  Testcontainers 2.0.5 з `postgres:16-alpine`.

Інструменти цих перевірок — у [`dev/`](dev/README.md).

Скрипти бекапу перевіряються в README своєї машини.

## Чого поки немає

Оновлення токена (refresh) і вихід з усіх пристроїв, скидання пароля поштою,
обмеження частоти спроб входу, видалення акаунта, лічильник відтворень,
PDF- і MIDI-вкладення, видача окремим учням (тільки всьому класу).
