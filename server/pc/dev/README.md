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

Потрібен Postgres 16 з правом `create database`; запускати з теки `server/pc/`.
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
