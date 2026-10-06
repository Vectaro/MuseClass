# MuseClass API для клієнта

Звірено з кодом `server/src` і з живим dev-сервером 2026-10-06 (Spring Boot
4.1.1). Якщо щось тут розходиться з поведінкою сервера — правий сервер, а цей
файл треба виправити.

## Загальне

| | |
|---|---|
| Базова адреса, dev з емулятора | `http://10.0.2.2:8081` |
| Базова адреса, dev з ПК | `http://localhost:8081` |
| Базова адреса, бойовий | Funnel-адреса ПК (`https://<машина>.<tailnet>.ts.net`) |
| Префікс | усі шляхи нижче — від `/api`, напр. `POST /api/auth/login` |
| Формат | JSON, UTF-8. Завантаження файлу — `multipart/form-data` |
| Id | UUID рядком: `"2d719391-fb02-4f31-91bb-8bd279eb52cd"` |
| Час | ISO-8601 в UTC з `Z`, дробова частина змінної довжини: `"2026-10-06T18:03:00.55Z"`, `"2026-10-06T18:03:00.427162Z"`. Парсити як `Instant` |
| `null` | поля з `null` приходять явно (`"composer": null`), не пропускаються |
| Невідомі поля в запиті | ігноруються |
| Health | `GET /actuator/health` (без `/api`, без токена) → `{"status":"UP",...}` |

### Токен (JWT)
- `register` і `login` повертають `token` (JWT, HS256) і `expiresAt`.
- Кожен інший запит під `/api` — із заголовком
  `Authorization: Bearer <token>`.
- Живе 30 днів від видачі. Refresh-токенів немає: після закінчення —
  знову `login`. Зберігати токен і `expiresAt`; парсити сам JWT клієнту не
  треба (`sub` — id користувача, але він і так приходить як `userId`).
- Вихід — просто забути токен (на сервері сесій немає).

### Помилки
Два формати, клієнт має вміти обидва:

**1. `application/problem+json`** — усі помилки бізнес-логіки й валідації:
```json
{"title":"Not Found","status":404,"detail":"Клас не знайдено.",
 "instance":"/api/classes/00000000-0000-0000-0000-000000000000"}
```
`detail` — готовий текст українською, показувати користувачу як є. `title` —
англійська назва статусу, не для показу. Помилка валідації — тільки одна
(перше поле, що знайшлось; порядок між полями не гарантований).

**2. Без `detail`** — помилки, до яких код сервісу не дійшов:
- **401 без тіла** (`Content-Length: 0`, лише `WWW-Authenticate: Bearer ...`):
  немає токена, він зіпсований, прострочений або підписаний іншим ключем.
  Клієнт: скинути токен і відправити на вхід.
- **400/404/405/415** у форматі Spring за замовчуванням
  `{"timestamp","status","error","path"}`: зламаний JSON, не-UUID у шляху
  (`/classes/123`), нечисловий `limit`, неіснуючий шлях, не той метод, не той
  `Content-Type`. Це баги клієнта; показувати загальне «Щось пішло не так».

| Код | Коли |
|---|---|
| 400 | некоректні дані; `detail` пояснює (або формат 2, див. вище) |
| 401 | `login`: невірна пошта або пароль (problem+json). Будь-де ще: токен (без тіла). Також `GET /me`, якщо акаунт видалено, — problem+json «Акаунт не знайдено. Увійди знову.» |
| 403 | ресурс видно, але змінювати не можна (учень редагує клас, чужа видана партитура) |
| 404 | не існує **або** немає доступу — сервер свідомо не розрізняє |
| 409 | пошта зайнята; вступ у власний клас |
| 413 | файл більший за 5 МБ |
| 422 | файл не є коректним MusicXML / MXL |
| 503 | не вдалося підібрати вільний код класу (майже неможливо; повторити) |

## Довідники

**Інструменти** (`instrument`, `instruments[]`): `piano`, `guitar`, `voice`,
`violin`, `trumpet`, `flute`, `bass_guitar`, `drums`, `saxophone`, `bandura`.

**kind** (жанр): `classical`, `folk`, `cover`, `technique`, `other`.
**rights**: `public_domain`, `folk`, `arrangement`, `original`, `unknown`.
**visibility**: `private`, `class`, `public`.
**role** у класі: `teacher`, `student`.

У запитах ці значення приймаються без урахування регістру й пробілів по краях;
у відповідях завжди в нижньому регістрі.

## Об'єкти

**Profile**
```json
{"id":"uuid","email":"student@dev.museclass","displayName":"Тарас Учень",
 "instruments":["guitar","trumpet"],"createdAt":"2026-10-06T18:03:00.427162Z"}
```
`instruments` — за абеткою, не в порядку, в якому їх передали.

**Class**
```json
{"id":"uuid","code":"DEV-6K","name":"Dev: оркестр","teacherId":"uuid",
 "teacherName":"Оксана Кравець","role":"teacher","students":1,
 "createdAt":"2026-10-06T18:03:00.550044Z"}
```
`code` — тільки викладачу; учню `null`. `students` — кількість учнів (без
викладача).

**Member**: `{"userId":"uuid","displayName":"Тарас Учень","joinedAt":"..."}`

**Summary** (картка партитури в усіх списках)
```json
{"id":"uuid","title":"Щедрик","composer":"М. Леонтович","arranger":"О. Кравець",
 "kind":"folk","visibility":"public","measures":8,"updatedAt":"...",
 "ownerId":"uuid","ownerName":"Оксана Кравець",
 "instruments":["piano","trumpet","voice"],"fits":true}
```
- `composer`, `arranger` можуть бути `null`.
- `instruments` — різні впізнані інструменти партій, за абеткою, без
  повторів; невпізнані партії (напр. тромбон) сюди не потрапляють. Може бути `[]`.
- `fits` — у партитурі є партія під один з інструментів поточного
  користувача.

**ScoreView** (`GET /scores/{id}`, а також відповідь на upload / PATCH / PUT file)
```json
{"score":{"id":"uuid","title":"Маленький марш для бенду","composer":"В. Сорока",
          "arranger":"сім партій","kind":"other","rights":"original","visibility":"class",
          "format":"musicxml","sizeBytes":27353,"measures":8,
          "sha256":"e2278bbf...","createdAt":"...","updatedAt":"...",
          "ownerId":"uuid","ownerName":"Оксана Кравець","saved":false},
 "parts":[{"position":0,"partId":"P1","name":"Труба 1 in B♭","instrument":"trumpet"},
          {"position":3,"partId":"P4","name":"Тромбон","instrument":null}],
 "canEdit":false}
```
- `format` — `musicxml` або `mxl`.
- `sha256` — те саме значення, що в `ETag` файлу (без лапок).
- `saved` — поточний користувач зберіг партитуру.
- `parts` — у порядку партитури (`position` з 0). `partId` — id партії з
  MusicXML. `instrument` — код або `null`, якщо не впізнано.
- `canEdit` — поточний користувач — автор.

**LibraryEntry**
```json
{"classId":"uuid","className":"Dev: оркестр","assignedAt":"...","score":{Summary}}
```
Одна партитура, видана у два класи, — два записи.

## Акаунт

| Метод і шлях | Тіло | Відповідь |
|---|---|---|
| `POST /auth/register` | `{email, password, displayName}` | **201** `{token, expiresAt, userId, displayName}` |
| `POST /auth/login` | `{email, password}` | 200 `{token, expiresAt, userId, displayName}` |
| `GET /me` | | 200 Profile |
| `PATCH /me` | `{displayName}` | 200 Profile |
| `PUT /me/instruments` | `{instruments: ["trumpet", ...]}` | 200 Profile |

- `register`: `email` — валідна адреса до 254 символів, зберігається в нижньому
  регістрі; `password` — 8–128 символів; `displayName` — до 60 символів, не
  порожнє. Пошта зайнята → 409 «Акаунт з такою поштою вже є.»
- `login`: пошта без урахування регістру. Невірна пошта **або** пароль → 401
  «Невірна пошта або пароль.» (не розрізняє, що саме).
- `PUT /me/instruments` замінює список цілком; `[]` — очистити. До 10
  елементів, дублікати зливаються. Невідомий код → 400
  «Невідомий інструмент: <код>».

## Класи

| Метод і шлях | Тіло | Відповідь | Хто |
|---|---|---|---|
| `POST /classes` | `{name, codePrefix?}` | **201** Class | будь-хто, стає викладачем |
| `POST /classes/join` | `{code}` | 200 Class | будь-хто |
| `GET /classes` | | 200 `[Class]` | — |
| `GET /classes/{id}` | | 200 Class | учасник |
| `PATCH /classes/{id}` | `{name}` | 200 Class | викладач |
| `DELETE /classes/{id}` | | **204** | викладач |
| `POST /classes/{id}/code` | `{codePrefix?}` або без тіла | 200 Class з новим кодом | викладач |
| `GET /classes/{id}/members` | | 200 `[Member]` | учасник |
| `DELETE /classes/{id}/members/{userId}` | | **204** | викладач — будь-кого; учень — тільки свій `userId` (вийти) |

- `name` — до 80 символів, не порожнє.
- `codePrefix` — рівно три літери (латиниця або кириличні двійники, будь-який
  регістр), напр. `"pno"` → `PNO`. Інакше 400. Без нього — випадковий.
- Код класу: `XXX-0X`, напр. `PNO-3A`. Випадкова частина без `I`, `O`, `0`, `1`.
- `join` прощає регістр, пробіли, відсутній дефіс і кириличні А В С Е Н І К М О
  Р Т Х: `"dev 6k"`, `"РNО3А"` — валідні. Невалідний формат → 400 «Код класу
  має вигляд PNO-3A.»; немає такого → 404; свій клас → 409. Повторний вступ —
  200 без змін.
- Перевипуск коду (`POST .../code`): старий код одразу перестає працювати.
- `GET /classes` — і ті, що веду, і ті, де вчуся; за датою створення класу.
- `members` — тільки учні, за `displayName`.
- Чужий клас → 404 «Клас не знайдено.»; учень на дії викладача → 403
  «Це може тільки викладач класу.»
- Видалення класу знімає і всі видачі в ньому; самі партитури лишаються.

## Партитури

| Метод і шлях | Тіло | Відповідь |
|---|---|---|
| `POST /scores` | multipart, див. нижче | **201** ScoreView |
| `GET /scores/mine` | | 200 `[Summary]` — мої, будь-який доступ |
| `GET /scores/{id}` | | 200 ScoreView |
| `GET /scores/{id}/file` | | 200 файл / 304 |
| `PUT /scores/{id}/file` | multipart: `file` | 200 ScoreView (автор) |
| `PATCH /scores/{id}` | JSON, будь-яка підмножина полів | 200 ScoreView (автор) |
| `DELETE /scores/{id}` | | **204** (автор) |
| `GET /catalog?q=&kind=&limit=30&offset=0` | | 200 `[Summary]` |

**Завантаження** `POST /scores`, `multipart/form-data`:
- `file` — обов'язково, `.musicxml` (нестиснений) або `.mxl` (стиснений);
  формат сервер визначає за вмістом, не за розширенням. До 5 МБ.
- `title`, `composer`, `arranger` — необов'язкові; порожнє береться з файлу
  (`work-title` / `movement-title`, `creator`), назва в крайньому разі — з
  імені файлу. Обрізаються до 200 символів.
- `kind` (за замовч. `other`), `rights` (`unknown`), `visibility` (`private`).
- Помилки: немає `file` → 400 «Не передано файл партитури.»; порожній → 400
  «Файл порожній.»; > 5 МБ → 413; не MusicXML → 422 з поясненням; не
  multipart → 415 (формат 2).

**Публічність.** `visibility: public` дозволено тільки з `rights` =
`public_domain` або `folk`, інакше 400 «У публічний каталог — тільки public
domain і народні твори. …». Діє і для upload, і для PATCH.

**Файл** `GET /scores/{id}/file`:
- 200, тіло — оригінальні байти, як завантажені.
- `Content-Type`: `application/vnd.recordare.musicxml+xml` (musicxml) або
  `application/vnd.recordare.musicxml` (mxl).
- `Content-Disposition: attachment; filename*=UTF-8''<назва>.musicxml|.mxl`.
- `ETag: "<sha256>"`, `Cache-Control: no-cache, private`.
- Офлайн-копія: зберегти ETag; наступного разу послати `If-None-Match: "<sha256>"`
  → **304** без тіла, якщо файл не змінився.

**PATCH** `{title?, composer?, arranger?, kind?, rights?, visibility?}`:
- відсутнє або `null` поле — не чіпати;
- `""` у `composer` / `arranger` — стерти (стане `null`);
- `title` не може бути порожнім → 400;
- невідоме значення довідника → 400 «Невідомий жанр: jazz» (аналогічно
  «Невідомий тип прав», «Невідомий рівень доступу»).

**PUT file** — нова версія з редактора: метадані лишаються, партії, `measures`,
`sha256` перераховуються. Ті самі помилки файлу, що в upload.

**Не автор**: якщо партитуру видно — 403 «Змінювати партитуру може тільки
автор.»; якщо не видно — 404.

**Каталог** `GET /catalog`: тільки `public`. `q` шукає підрядок у назві,
композиторі й аранжувальнику без урахування регістру (кирилиця теж);
`%` і `_` — звичайні символи. `kind` — фільтр, невідомий → 400. `limit` 1–100
(більше — обріжеться до 100), `offset` ≥ 0. Порядок: спершу `fits: true`,
далі за назвою. Кінець сторінок — коли прийшло менше, ніж `limit`.

**Хто бачить партитуру**: автор; будь-хто, якщо `public`; викладач і учні
класу, якщо `class` і вона видана в цей клас. `private` бачить тільки автор.

## Видача класу і бібліотека

| Метод і шлях | Відповідь | Хто |
|---|---|---|
| `PUT /classes/{classId}/scores/{scoreId}` | **204** | викладач |
| `DELETE /classes/{classId}/scores/{scoreId}` | **204** | викладач |
| `GET /classes/{classId}/scores` | 200 `[LibraryEntry]` | учасник |
| `GET /me/library` | 200 `[LibraryEntry]` | — |
| `GET /me/saved` | 200 `[Summary]` | — |
| `PUT /me/saved/{scoreId}` | **204** | будь-хто, хто бачить партитуру |
| `DELETE /me/saved/{scoreId}` | **204** | — |

- Видати можна свою партитуру або будь-яку `public`. Своя `private` при видачі
  сама стає `class`. Чужа не-публічна → 404. Повторна видача — 204 без змін.
- Зняти невидане → 404 «Партитуру в цьому класі не знайдено.»
- `/me/library` — видане в усіх моїх класах (і де вчуся, і де веду), новіші
  видачі першими.
- `/me/saved` — новіші збереження першими. Якщо автор закрив доступ,
  партитура зникає зі списку (але повернеться, якщо доступ повернуть).
  `DELETE` неіснуючого — теж 204.

## Тестові дані на dev
`python server/dev/seed_dev.py` (див. `server/dev/README.md`) створює:
викладач `teacher@dev.museclass` / `teacher-dev-1`, учень
`student@dev.museclass` / `student-dev-1` (trumpet, guitar), клас «Dev:
оркестр» з кодом `DEV-..`, чотири видані партитури, серед них бенд на 7 партій.
