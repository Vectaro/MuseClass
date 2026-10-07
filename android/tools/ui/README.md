# Порівняння UI з прототипом

Знімки в `docs/android/ui/` зроблено цими скриптами (Windows, Git Bash).

1. Прототип у вікні 400 px, обидві теми — `shoot-proto.js` через `puppeteer-core` і Edge:
   `npm install puppeteer-core` у будь-якій тимчасовій теці, далі
   `NODE_PATH=<та тека>/node_modules node android/tools/ui/shoot-proto.js <proto>`.
2. Застосунок в емуляторі (запущений AVD, dev-сервер на 8081, debug-збірка встановлена) —
   `bash android/tools/ui/shoot-emu.sh light <emu>` і те саме з `dark`. Кожен прохід реєструє новий
   dev-акаунт `ui-<тема>-<час>@dev.museclass`, вступає в «Dev: оркестр» і наприкінці входить як викладач.
   `ui.sh` — помічники: тап за текстом через `uiautomator`, знімок екрана.
3. Пари — `powershell -File android/tools/ui/pair.ps1 <proto> <emu> docs/android/ui` (JPEG, висота 1000).

Імена кадрів однакові з обох боків (`10-home-light.png` тощо); скрипти треба правити разом.
