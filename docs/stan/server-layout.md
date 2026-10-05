# Структура `server/` — специфікація

Статус: **чекає «ок» від Влада.** Поки «ок» немає, за цим файлом нічого не
робити.

Вказівка Влада (2026-10-05, дослівно по суті): «Папка `server` коренева, тут
мають бути тільки файли, які спільні. Відрізняються тільки файли деплою, для
них створюємо окремі папки в `server/` — `pc` і `pi`. Попередні дії затерти,
щоб не засирати репо». «Затерти» = прибрати зайве звичайним комітом, історію
git не переписувати, без force-push.

## Кінцеве дерево

```
server/
  pom.xml               спільне
  src/                  спільне — єдина копія коду
  Dockerfile            спільне (з налаштуваннями під малину: MAVEN_OPTS 512m,
                        start-period 180 с — на ПК вони нешкідливі)
  .dockerignore         спільне
  .gitattributes        спільне
  .gitignore            спільне (.env, backups/ ловить на будь-якій глибині)
  dev/                  спільне: sql_check.py, RealFilesSmoke.java, README.md
  README.md             спільне: опис, API, правила доступу, розробка,
                        перевірки; деплой — лише посилання на pc/ і pi/
  pc/                   деплой робочого ПК (Windows 10/11)
    compose.yaml          build: ..   name: museclass-pc
                          пам'ять за замовчуванням 1g / 512MB
    .env.example
    backup.ps1
    README.md             запуск, живучість, бекап на Windows
  pi/                   деплой малини (і будь-якого Linux: NUC)
    compose.yaml          build: ..   name: museclass
                          пам'ять за замовчуванням 384m / 128MB
    .env.example
    backup.sh
    README.md
```

Крім цього в `server/` нічого немає. Зокрема, НЕМАЄ: `server/.env.example`,
`server/compose.yaml`, `server/backup.*`, `server/pc/src`,
`server/pc/pom.xml`, `server/pc/Dockerfile`, `server/pc/dev`,
`server/pc/.git*`, `server/pc/.dockerignore`.

Команди `docker compose` виконуються з `server/pc/` або `server/pi/`.
`mvn verify` — з `server/`.

## Хто що робить

**Чат про сервер (ПК):**
1. `server/pc/` → лише 4 деплой-файли з дерева вище. Повну копію коду
   (`src`, `pom.xml`, `Dockerfile`, `dev`, `.git*`, `.dockerignore`) звідти
   видалити.
2. `server/pc/compose.yaml`: `build: ..`.
3. `server/pc/README.md`: шлях `C:\MuseClass\server\pc` для compose і бекапу,
   `mvn verify` — з `C:\MuseClass\server`.
4. Видалити `server/.env.example` (у кожної машини свій).
5. Корінь `server/README.md` і `server/dev/README.md` — привести у
   відповідність дереву (шляхи запуску перевірок — з `server/`).
6. `docs/stan/server.md`, рядок у `CLAUDE.md` про ділянку сервера.
7. Перевірити: `docker compose config` у `pc/`, `dev/sql_check.py` з
   `server/`. Звірити дерево з цим файлом.

**Чат про малину:**
1. `server/pi/` уже відповідає дереву — структурно нічого не робити.
2. Звірити дерево з цим файлом і лише це: чи немає в `pi/` нічого понад
   4 файли, чи шляхи в README і `backup.sh` — `~/MuseClass/server/pi`, а
   `mvn verify` — з `server/`.
3. `docs/stan/hardware.md` — за потреби.

Спільні файли в корені `server/` надалі правити окремим комітом з приміткою в
повідомленні «спільне», бо це зачіпає обидві машини.

## Якщо щось не сходиться
Розбіжність із цим файлом не виправляти на власний розсуд — дописати внизу в
розділ «Розбіжності» й чекати Влада.

## Розбіжності
(поки немає)
