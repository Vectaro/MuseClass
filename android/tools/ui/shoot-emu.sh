# Прохід по застосунку в емуляторі з тими самими станами, що shoot-proto.js.
# bash shoot-emu.sh <light|dark> <outDir>
SP="$(dirname "$0")"
source "$SP/ui.sh"
THEME=$1; OUT=$2; mkdir -p "$OUT"
s() { sleep 0.4; shot "$OUT/$1-$THEME.png"; echo "  $1"; }
key() { "$ADB" shell input keyevent "$1"; sleep "${2:-0.8}"; }
up() { "$ADB" shell input swipe 540 1800 540 400 250; sleep 0.8; }
top() { for i in 1 2 3 4; do "$ADB" shell input swipe 540 500 540 1900 150; done; sleep 0.6; }
EMAIL="ui-$THEME-$(date +%s)@dev.museclass"

"$ADB" shell cmd uimode night $([ "$THEME" = dark ] && echo yes || echo no) >/dev/null
"$ADB" shell input keyevent 224; "$ADB" shell svc power stayon true
"$ADB" shell am force-stop ua.museclass.app; "$ADB" shell pm clear ua.museclass.app >/dev/null; sleep 2
"$ADB" shell pm grant ua.museclass.app android.permission.ACCESS_LOCAL_NETWORK
"$ADB" shell am start -W -n ua.museclass.app/.MainActivity >/dev/null; sleep 3
s 01-onb-auth
tap "Створити акаунт з поштою" 1.5; type_ "$EMAIL"; key 61; type_ "ui-dev-pass-1"; sleep 0.5
s 02-onb-email
tap "Створити" 3; key 111; s 03-onb-name
tap "Далі" 3; tap "Я вчусь у викладача" 1; s 04-onb-role
tap "Далі" 1.5; s 05-onb-code
tap "XXX-0X" 0.8; type_ "XYZ-9K"; key 111; tap "Приєднатися" 2; s 06-onb-code-err
key 4 1; tap "Я викладаю" 1; tap "Далі" 1.5; key 111; s 07-onb-class
key 4 1; tap "Я вчусь у викладача" 1; tap "Далі" 1.5; tap "Пропустити" 1.5
tap "Фортепіано" 0.8; s 08-onb-inst
tap "Готово" 4; s 10-home
up; up; s 11-home-scrolled
top; tap "Народна" 2.5; s 12-home-filter
tap "Усі" 2; tap "Назва, автор, інструмент" 0.8; type_ "zzz"; sleep 2; key 111; s 13-home-search-empty
tap "Бібліотека" 3; s 22-lib-noclass-top
up; up; s 22-lib-noclass
tap "XXX-0X" 0.8; type_ "DEV-6K"; key 111; tap "Приєднатися" 4; top; s 20-lib-student
up; up; s 21-lib-student-scrolled
tap "Профіль" 3; s 30-me
up; up; up; s 31-me-scrolled
tap "Ввести код" 1.5; key 111; s 32-dlg-join
tap "Скасувати" 1; tap "Створити клас" 1.5; key 111; s 33-dlg-mkclass
tap "Скасувати" 1; tap "Ввести код" 1.2; type_ "DEV-6K"; tap "Приєднатися" 2.2; s 34-toast
sleep 2.5; tap "Бібліотека" 3; tap "Щедрик" 4; s 41-viewer-part
tap "Партитура" 0.6; s 40-viewer-score
sleep 2.5; tap "Налаштування відтворення" 1.2; s 42-viewer-vset
tap "Закрити" 0.8; tap "Партії" 1.2; s 43-viewer-parts
tap "Закрити" 0.8; key 4 2; top; tap "Етюд: усе одразу" 4; s 45-viewer-single
key 4 2; tap "Профіль" 2.5; up; up; up; up; tap "Вийти" 3
tap "Увійти з поштою" 1.5; type_ "teacher@dev.museclass"; key 61; type_ "teacher-dev-1"; tap "Увійти" 4
tap "Бібліотека" 3; s 23-lib-teacher
echo "готово: $THEME ($EMAIL)"
