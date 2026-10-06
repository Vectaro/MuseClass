# server/dev/

Перевірки, на яких стоять цифри в [`docs/stan/server.md`](../../docs/stan/server.md).
Писались, поки Maven Central був недоступний. Після першого `mvn verify`
основними стають тести в `src/test`, а ці лишаються як додаткова сітка.

## sql_check.py — 61 перевірка SQL на живому Postgres
Витягує всі SQL-рядки (`static final String X = """..."""`) прямо з
`*Repository.java`, накочує `V1__init.sql` на чисту базу `mc_sqltest` і ганяє
запити через серверні prepared statements, як це робить JDBC. Перевіряє доступи
(`score_readable`), видачу класу, ранжування каталогу, пошук кирилицею,
екранування `%`/`_`, check-обмеження бази.

```sh
pip install "psycopg[binary]"
PGDSN="host=127.0.0.1 port=5432 user=postgres password=... dbname=postgres" python3 dev/sql_check.py
```

Потрібен Postgres 16 з правом `create database`; запускати з теки `server/`.
Наприкінці друкує `УСЕ ГАРАЗД` або кількість провалів. Якщо додаєш запит у
репозиторій, додай і перевірку сюди. Інакше він ніде не буде прогнаний, поки
немає справжньої збірки.

## RealFilesSmoke.java — розбір MusicXML на реальних файлах
Ганяє `MusicXmlInspector` по всіх `.xml` / `.musicxml` / `.mxl` у теці й друкує,
скільки розібралось, які інструменти впізнались і що не впізналось. Плюс
перевірка XXE.

```sh
git clone --depth 1 --filter=blob:none --sparse https://github.com/opensheetmusicdisplay/opensheetmusicdisplay osmd
git -C osmd sparse-checkout set test/data
javac -encoding UTF-8 -d /tmp/rf src/main/java/ua/museclass/musicxml/*.java dev/RealFilesSmoke.java
java -cp /tmp/rf RealFilesSmoke osmd/test/data
```

Останній прогін (2026-10-05): `ok=377`, жодного `ERR`.

## seed_dev.py — тестові дані для dev-сервера
Одна команда з кореня репо, поки dev-сервер працює на 8081 (запуск — у
[`pc/README.md`](../pc/README.md), розділ «Розробка»):

```sh
python server/dev/seed_dev.py
```

Створює через API, як справжній клієнт:

| Що | Дані |
|---|---|
| викладач | `teacher@dev.museclass` / `teacher-dev-1` |
| учень | `student@dev.museclass` / `student-dev-1`, інструменти trumpet і guitar |
| клас | «Dev: оркестр», код з префіксом `DEV` (випадковий хвіст, скрипт друкує) |
| партитури | бенд на 7 партій і «Етюд» (класні), «Щедрик» і «Ода» (публічні) — усі видані класу |

Повторний запуск нічого не дублює. Залежностей немає, тільки стандартний
Python 3. На бойовий не діє: приймає лише `localhost`/`127.0.0.1` і відкидає
порт 8080 та Funnel-адреси. Інший порт dev — аргументом:
`python server/dev/seed_dev.py http://localhost:8082`. Почати з нуля —
перестворити `museclass-dev-db` і перезапустити dev-сервер (Flyway накотить
схему заново).

Партитури лежать у `seed/` і згенеровані з демо прототипу його ж
`toMusicXML()` — такі самі файли, як дає «Експорт» у прототипі. Перегенерувати
після зміни демо:

```sh
cd prototype && npm install && node ../server/dev/export-demos.js
```
