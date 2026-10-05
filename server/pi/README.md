# Сервер на малині (і будь-якому Linux)

Деплой-файли для малини. Код сервера спільний і лежить рівнем вище
(`../src`, `../pom.xml`, `../Dockerfile`); `compose.yaml` тут збирає образ з
`..`. Усі команди нижче — з теки `server/pi/`. Опис API — у
[`../README.md`](../README.md).

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
git clone https://github.com/Vectaro/MuseClass ~/MuseClass && cd ~/MuseClass/server/pi
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

### Тести на малині

JDK на хості не потрібен: Maven у контейнері, сокет Docker прокидається, щоб
Testcontainers підняв Postgres поруч. Запускати з `server/`:

```sh
cd ~/MuseClass/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/src -w /src maven:3.9-eclipse-temurin-21 mvn -B verify
```

### Збірка на ПК замість малини

Якщо збірка на малині надто повільна, образ можна зібрати на ПК під arm64:

```sh
docker buildx build --platform linux/arm64 -t museclass-api:latest --load ..   # з server/pi/
docker save museclass-api:latest | gzip > api-arm64.tar.gz
# скопіювати на малину, там (ім'я образу museclass-api задає `name:` у compose.yaml):
gunzip -c api-arm64.tar.gz | docker load && docker compose up -d --no-build
```
