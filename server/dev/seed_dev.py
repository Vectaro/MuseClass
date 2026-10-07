"""Тестові дані для dev-сервера: викладач, учень, клас і чотири демо з прототипу.

    python server/dev/seed_dev.py                 # dev на http://localhost:8081
    python server/dev/seed_dev.py http://127.0.0.1:8082

Працює через API, як справжній клієнт: паролі хешує сервер, партитури розбирає
сервер. Повторний запуск нічого не дублює — бере вже створене.

На бойовий не діє: адреса має бути localhost/127.0.0.1 і не порт 8080, на якому
слухає museclass-pc. Funnel-адресу скрипт теж відкине.
"""
import json
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
SEED = pathlib.Path(__file__).parent / "seed"

TEACHER = {"email": "teacher@dev.museclass", "password": "teacher-dev-1", "displayName": "Оксана Кравець"}
STUDENT = {"email": "student@dev.museclass", "password": "student-dev-1", "displayName": "Тарас Учень"}
STUDENT_INSTRUMENTS = ["trumpet", "guitar"]
CLASS_NAME = "Dev: оркестр"
CLASS_PREFIX = "DEV"

# файл, kind, rights, visibility. Видача класу сама робить приватну класною.
SCORES = [
    ("band-march.musicxml", "other", "original", "private"),
    ("shchedryk.musicxml", "folk", "folk", "public"),
    ("oda.musicxml", "classical", "public_domain", "public"),
    ("etude.musicxml", "technique", "original", "private"),
]


def guard():
    u = urllib.parse.urlsplit(BASE)
    if u.hostname not in ("localhost", "127.0.0.1", "::1") or u.port in (None, 80, 443, 8080):
        sys.exit(f"Відмова: {BASE} схоже не на dev. Потрібен localhost з портом dev (8081).")


def call(method, path, token=None, body=None, files=None):
    headers = {"Accept": "application/json"}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if files is not None:
        boundary = uuid.uuid4().hex
        parts = []
        for name, (fname, content) in files.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{fname}"\r\n'
                         f"Content-Type: application/vnd.recordare.musicxml+xml\r\n\r\n".encode() + content + b"\r\n")
        for name, value in (body or {}).items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        data = b"".join(parts) + f"--{boundary}--\r\n".encode()
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + "/api" + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"detail": raw.decode(errors="replace")}


def ok(status, body, *expected):
    if status not in expected:
        sys.exit(f"Помилка {status}: {body.get('detail') if isinstance(body, dict) else body}")
    return body


def account(user):
    status, body = call("POST", "/auth/register", body=user)
    if status == 409:
        status, body = call("POST", "/auth/login", body={"email": user["email"], "password": user["password"]})
    return ok(status, body, 200, 201)["token"]


def main():
    guard()
    try:
        urllib.request.urlopen(BASE + "/actuator/health", timeout=5)
    except OSError as e:
        sys.exit(f"Dev-сервер на {BASE} не відповідає ({e}). Запусти його за server/pc/README.md, розділ «Розробка».")

    teacher = account(TEACHER)
    student = account(STUDENT)
    ok(*call("PUT", "/me/instruments", student, {"instruments": STUDENT_INSTRUMENTS}), 200)

    classes = ok(*call("GET", "/classes", teacher), 200)
    klass = next((c for c in classes if c["name"] == CLASS_NAME and c["role"] == "teacher"), None)
    if klass is None:
        klass = ok(*call("POST", "/classes", teacher, {"name": CLASS_NAME, "codePrefix": CLASS_PREFIX}), 201)
    status, body = call("POST", "/classes/join", student, {"code": klass["code"]})
    ok(status, body, 200)

    mine = {s["title"]: s for s in ok(*call("GET", "/scores/mine", teacher), 200)}
    assigned = []
    for fname, kind, rights, visibility in SCORES:
        content = (SEED / fname).read_bytes()
        title = title_of(content)
        if title in mine:
            score_id, state = mine[title]["id"], "вже була"
            ok(*call("PATCH", f"/scores/{score_id}", teacher, {"kind": kind, "rights": rights}), 200)
            # та сама версія файлу ще раз: сервер заново розпізнає інструменти партій
            # (після зміни довідника старі партії інакше лишились би як були)
            ok(*call("PUT", f"/scores/{score_id}/file", teacher, files={"file": (fname, content)}), 200)
        else:
            view = ok(*call("POST", "/scores", teacher, {"kind": kind, "rights": rights, "visibility": visibility},
                            files={"file": (fname, content)}), 201)
            score_id, state = view["score"]["id"], "нова"
        ok(*call("PUT", f"/classes/{klass['id']}/scores/{score_id}", teacher), 204)
        parts = ok(*call("GET", f"/scores/{score_id}", teacher), 200)["parts"]
        assigned.append((title, [pt["instrument"] or "?" for pt in parts], state))

    library = ok(*call("GET", "/me/library", student), 200)

    print(f"Dev-сервер: {BASE}")
    print(f"Викладач:   {TEACHER['email']} / {TEACHER['password']}")
    print(f"Учень:      {STUDENT['email']} / {STUDENT['password']}  (інструменти: {', '.join(STUDENT_INSTRUMENTS)})")
    print(f"Клас:       {klass['name']}, код {klass['code']}")
    for title, instruments, state in assigned:
        print(f"  видано:   {title} ({state}): {', '.join(instruments)}")
    print(f"Бібліотека учня: партитур — {len(library)}")


def title_of(content):
    s = content.decode("utf-8")
    a = s.find("<work-title>") + len("<work-title>")
    return s[a:s.find("</work-title>")].replace("&amp;", "&")


if __name__ == "__main__":
    main()
