"""
Запуск:  pip install "psycopg[binary]"
         PGDSN="host=127.0.0.1 port=5432 user=postgres dbname=postgres" python3 dev/sql_check.py
(з теки server/; потрібен Postgres 16 з правом create database; створює й
перезаписує базу mc_sqltest).

Проганяє SQL-рядки, витягнуті просто з Java-репозиторіїв, на справжньому Postgres
через prepared statements (psycopg3), і перевіряє правила доступу, ранжування,
пошук, видачу класу. Схема — ті самі міграції, що накочує Flyway.
"""
import os, re, sys, uuid, pathlib
import psycopg

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main"
DSN = os.environ.get("PGDSN", "host=127.0.0.1 port=5432 user=postgres dbname=postgres")

def java_sql(path):
    src = (ROOT / "java" / path).read_text(encoding="utf8")
    out = {}
    for m in re.finditer(r'static final String (\w+) = """\n(.*?)""";', src, re.S):
        body = m.group(2).replace("\\\\", "\\")   # екранування Java text block
        body = re.sub(r"(?<![:\w]):(\w+)", r"%(\1)s", body)  # :name → %(name)s
        out[m.group(1)] = body
    return out

SQL = {}
for f in ["ua/museclass/user/UserRepository.java", "ua/museclass/klass/ClassRepository.java",
          "ua/museclass/score/ScoreRepository.java"]:
    SQL.update(java_sql(f))
print(f"витягнуто SQL-запитів: {len(SQL)}")

fails = 0
def check(cond, msg):
    global fails
    print(("  ok   " if cond else "  FAIL ") + msg)
    if not cond: fails += 1

with psycopg.connect(DSN, autocommit=True) as admin:
    admin.execute("drop database if exists mc_sqltest")
    admin.execute("create database mc_sqltest template template0 encoding 'UTF8' lc_collate 'C.UTF-8' lc_ctype 'C.UTF-8'")

conn = psycopg.connect(DSN.replace("dbname=postgres", "dbname=mc_sqltest"), autocommit=True,
                       prepare_threshold=0)   # одразу серверні prepared statements, як у JDBC
# Усі міграції по черзі, як Flyway: V1__..., V2__... (сортування за номером версії)
for mig in sorted((ROOT / "resources/db/migration").glob("V*__*.sql"), key=lambda f: int(f.name[1:].split("__")[0])):
    conn.execute(mig.read_text(encoding="utf8"), prepare=False)

def q(_n, **p):
    return conn.execute(SQL[_n], p).fetchall()

def one(_n, **p):
    rows = q(_n, **p)
    return rows[0][0] if rows else None

def upd(_n, **p):
    return conn.execute(SQL[_n], p).rowcount

# --- користувачі
T = one("INSERT_USER", email="teacher@x.ua", hash="{bcrypt}x", name="Оксана Кравець")
S = one("INSERT_USER", email="student@x.ua", hash="{bcrypt}x", name="Влад")
X = one("INSERT_USER", email="other@x.ua", hash="{bcrypt}x", name="Чужий")
try:
    one("INSERT_USER", email="STUDENT@x.ua", hash="h", name="дубль")
    check(False, "дубль пошти в іншому регістрі відхилено")
except psycopg.errors.UniqueViolation:
    check(True, "дубль пошти в іншому регістрі відхилено")
check(q("FIND_BY_EMAIL", email="Student@X.ua")[0][0] == S, "пошук пошти без урахування регістру")
upd("CLEAR_INSTRUMENTS", id=S)
upd("ADD_INSTRUMENT", id=S, instrument="trumpet")
upd("ADD_INSTRUMENT", id=S, instrument="trumpet")
check([r[0] for r in q("INSTRUMENTS_OF", id=S)] == ["trumpet"], "інструменти без дублів")
try:
    upd("ADD_INSTRUMENT", id=S, instrument="theremin")
    check(False, "невідомий інструмент відхилено базою")
except psycopg.errors.CheckViolation:
    check(True, "невідомий інструмент відхилено базою")

# --- класи
C = one("INSERT_CLASS", code="PNO-3A", name="Фортепіано, 3 клас", teacher=T)
check(C is not None, "клас створено")
check(one("INSERT_CLASS", code="PNO-3A", name="дубль", teacher=X) is None, "колізія коду → порожньо, без винятку")
C2 = one("INSERT_CLASS", code="SOL-2B", name="Сольфеджіо", teacher=X)
try:
    one("INSERT_CLASS", code="PNO-33", name="битий", teacher=T)
    check(False, "код не того формату відхилено базою")
except psycopg.errors.CheckViolation:
    check(True, "код не того формату відхилено базою")

check(one("ROLE_IN_CLASS", id=C, me=T) == "teacher", "роль: викладач")
check(q("ROLE_IN_CLASS", id=C, me=S) == [(None,)], "роль: ще не учень → null")
check(q("ROLE_IN_CLASS", id=uuid.uuid4(), me=S) == [], "роль: класу немає → порожньо")
check(one("FIND_ID_BY_CODE", code="PNO-3A") == C, "клас за кодом")
upd("ADD_MEMBER", id=C, user=S)
upd("ADD_MEMBER", id=C, user=S)
check(one("ROLE_IN_CLASS", id=C, me=S) == "student", "роль: учень")

rows = q("MY_CLASSES", me=S)
check(len(rows) == 1 and rows[0][1] is None and rows[0][5] == "student" and rows[0][6] == 1,
      "учень бачить свій клас без коду, учнів = 1")
rows = q("MY_CLASSES", me=T)
check(rows[0][1] == "PNO-3A" and rows[0][5] == "teacher", "викладач бачить код")
upd("ADD_MEMBER", id=C2, user=T)
check(len(q("MY_CLASSES", me=T)) == 2, "викладач водночас учень у чужому класі")
check([r[1] for r in q("MEMBERS", id=C)] == ["Влад"], "список учнів")
check(one("TEACHER_OF", id=C) == T, "викладач класу")
v = q("CLASS_VIEW", id=C, me=S)[0]
check(v[1] is None and v[4] == "Оксана Кравець" and v[5] == "student", "картка класу для учня без коду")
check(q("CLASS_VIEW", id=C, me=T)[0][1] == "PNO-3A", "картка класу для викладача з кодом")
check(upd("UPDATE_CODE", id=C, code="SOL-2B") == 0, "перевипуск на зайнятий код → 0 рядків")
check(upd("UPDATE_CODE", id=C, code="PNO-4C") == 1, "перевипуск на вільний код")

# --- партитури
def score(owner, title, vis, rights="unknown", kind="other", composer=None):
    return one("INSERT_SCORE", owner=owner, title=title, composer=composer, arranger=None, kind=kind,
               rights=rights, visibility=vis, format="musicxml", content=b"<score-partwise/>",
               sha="ab" * 32, size=17, measures=8)

def part(sc, pos, name, instr):
    upd("INSERT_PART", score=sc, position=pos, partId=f"P{pos+1}", name=name, instrument=instr, midi=None)

T_PRIV = score(T, "Гама до мажор", "private", kind="technique")
part(T_PRIV, 0, "Фортепіано", "piano")
T_PUB = score(T, "Щедрик", "public", rights="folk", kind="folk", composer="М. Леонтович")
part(T_PUB, 0, "Фортепіано", "piano"); part(T_PUB, 1, "Труба in B♭", "trumpet"); part(T_PUB, 2, "Вокал", "voice")
X_PUB = score(X, "Ода до радості", "public", rights="public_domain", kind="classical", composer="Л. ван Бетховен")
part(X_PUB, 0, "Фортепіано", "piano")
X_PRIV = score(X, "100% кавер_тест", "private", rights="arrangement", kind="cover")
X_CLASS = score(X, "Розспівка", "class", rights="original")
try:
    score(T, "Кавер", "public", rights="arrangement")
    check(False, "публічний кавер відхилено базою")
except psycopg.errors.CheckViolation:
    check(True, "публічний кавер відхилено базою")

R = lambda who, sc: one("IS_READABLE", me=who, id=sc)
mine = q("MY_SCORES", me=X)
check({r[0] for r in mine} == {X_PUB, X_PRIV, X_CLASS} and all(r[8] == X for r in mine), "мої ноти: всі свої, будь-який доступ")
check(R(T, T_PRIV) and not R(S, T_PRIV) and not R(X, T_PRIV), "приватна: тільки власник")
check(R(S, X_PUB) and R(T, X_PUB), "публічна: всі")
check(not R(T, X_CLASS) and not R(S, X_CLASS) and R(X, X_CLASS), "класна без видачі: тільки власник")
check(R(S, uuid.uuid4()) is False, "неіснуюча: false, не null")

# видача класу: приватна стає класною
upd("PROMOTE_PRIVATE", id=T_PRIV, me=T)
upd("ASSIGN", classId=C, scoreId=T_PRIV, me=T)
upd("ASSIGN", classId=C, scoreId=T_PRIV, me=T)
upd("ASSIGN", classId=C, scoreId=X_PUB, me=T)
check(q("OWNERSHIP", id=T_PRIV)[0][1] == "class", "після видачі приватна стала класною")
check(R(S, T_PRIV) and not R(X, T_PRIV), "класна: учень бачить, чужий ні")
upd("PROMOTE_PRIVATE", id=X_PUB, me=X)
check(q("OWNERSHIP", id=X_PUB)[0][1] == "public", "PROMOTE не чіпає публічну")

lib = q("CLASS_LIBRARY", classId=C, me=S)
check({r[3] for r in lib} == {T_PRIV, X_PUB}, "бібліотека класу для учня")
check(len(q("MY_LIBRARY", me=S)) == 2 and len(q("MY_LIBRARY", me=T)) == 2, "моя бібліотека: учень і викладач")
check(q("CLASS_LIBRARY", classId=C, me=X) == [] or all(R(X, r[3]) for r in q("CLASS_LIBRARY", classId=C, me=X)),
      "чужому бібліотека класу не віддає нечитабельного")

d = q("DETAIL", id=T_PRIV, me=S)
check(len(d) == 1 and d[0][14] == "Оксана Кравець", "деталі класної для учня")
check(q("DETAIL", id=T_PRIV, me=X) == [], "деталі класної для чужого — порожньо")
check([r[2] for r in q("PARTS_OF", id=T_PUB)] == ["Фортепіано", "Труба in B♭", "Вокал"], "партії в порядку")
f = q("FILE_OF", id=T_PRIV, me=S)
check(f and bytes(f[0][2]) == b"<score-partwise/>", "файл віддається байт у байт")
check(q("FILE_OF", id=X_PRIV, me=S) == [], "чужий приватний файл — порожньо")

# --- каталог
def cat(me, pattern=None, kind=None, limit=30, offset=0):
    return q("CATALOG", me=me, pattern=pattern, kind=kind, limit=limit, offset=offset)

rows = cat(S)
check([r[1] for r in rows] == ["Щедрик", "Ода до радості"], "трубач: Щедрик з партією труби вище")
check(rows[0][10] == "piano,trumpet,voice" and rows[0][11] is True, "інструменти й fits у картці")
rows = cat(X)
check([r[1] for r in rows] == ["Ода до радості", "Щедрик"], "без інструментів — просто за назвою")
check([r[1] for r in cat(S, pattern="%щедрик%")] == ["Щедрик"], "пошук кирилицею без урахування регістру")
check([r[1] for r in cat(S, pattern="%бетховен%")] == ["Ода до радості"], "пошук за композитором")
check([r[1] for r in cat(S, kind="folk")] == ["Щедрик"], "фільтр за жанром")
check(len(cat(S, limit=1)) == 1 and cat(S, limit=1, offset=1)[0][1] == "Ода до радості", "пагінація")
check(all(r[5] == "public" for r in cat(S)), "у каталозі тільки публічне")
# екранування LIKE: '%' і '_' з запиту — буквальні
X_PUB2 = score(X, "Етюд 100% темп", "public", rights="public_domain")
esc = lambda s: s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
check([r[1] for r in cat(S, pattern="%" + esc("100%") + "%")] == ["Етюд 100% темп"], "'%' у запиті — буквальний")
check(cat(S, pattern="%" + esc("_") + "%") == [], "'_' у запиті — буквальний")

# --- збережене
upd("SAVE", me=S, id=T_PUB); upd("SAVE", me=S, id=T_PUB)
check([r[1] for r in q("SAVED", me=S)] == ["Щедрик"], "збережене")
upd("UPDATE_META", id=T_PUB, me=T, title="Щедрик", composer=None, arranger=None,
    kind="folk", rights="folk", visibility="private")
check(q("SAVED", me=S) == [], "автор приховав — зникло зі збережених")
check(upd("UPDATE_META", id=T_PUB, me=S, title="злам", composer=None, arranger=None,
          kind="folk", rights="folk", visibility="public") == 0, "чужу партитуру не оновити")
try:
    upd("UPDATE_META", id=T_PUB, me=T, title="Щедрик", composer=None, arranger=None,
        kind="folk", rights="arrangement", visibility="public")
    check(False, "оновлення до публічного кавера відхилено базою")
except psycopg.errors.CheckViolation:
    check(True, "оновлення до публічного кавера відхилено базою")

check(upd("REPLACE_FILE", id=T_PRIV, me=T, format="mxl", content=b"PK\x03\x04", sha="cd" * 32,
          size=4, measures=12) == 1, "нова версія файлу")
check(q("OWNERSHIP", id=T_PRIV)[0][1] == "class", "нова версія не чіпає доступ")
check(upd("UNASSIGN", classId=C, scoreId=T_PRIV) == 1 and not R(S, T_PRIV), "зняли з видачі — учень не бачить")
check(upd("REMOVE_MEMBER", id=C, user=S) == 1 and q("MY_LIBRARY", me=S) == [], "вийшов з класу — бібліотека порожня")
check(upd("DELETE_SCORE", id=X_PUB, me=T) == 0, "чужу партитуру не видалити")
check(upd("DELETE_SCORE", id=X_PUB, me=X) == 1, "свою видалити")
upd("DELETE_CLASS", id=C)
check(one("FIND_ID_BY_CODE", code="PNO-4C") is None, "клас видалено")
upd("DELETE_PARTS", score=T_PUB)
check(q("PARTS_OF", id=T_PUB) == [], "партії видаляються перед перерахунком")
upd("UPDATE_NAME", id=S, name="Влад Д.")
check(q("FIND_BY_ID", id=S)[0][3] == "Влад Д.", "перейменування")
upd("RENAME", id=C2, name="Сольфеджіо, група Б")
upd("UNSAVE", me=S, id=T_PUB)

used = {n for n in SQL}
print(f"\n{'УСЕ ГАРАЗД' if fails == 0 else f'ПРОВАЛІВ: {fails}'}; SQL у коді: {len(used)}")
sys.exit(1 if fails else 0)
