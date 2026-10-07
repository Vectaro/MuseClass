# Помічники для емулятора: source ui.sh
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
dump() { "$ADB" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/u.xml; }
# центр першого вузла з text/content-desc, що містить рядок
center() {
  printf '%s' "$1" > "$TEMP/uiq.txt"
  dump > "$TEMP/uid.xml"
  python -c "
import re,os,sys
s=open(os.environ['TEMP']+'/uid.xml','rb').read().decode('utf-8','replace'); q=open(os.environ['TEMP']+'/uiq.txt','rb').read().decode('utf-8')
nodes=[]
for m in re.finditer(r'<node [^>]*>', s):
    n=m.group(0)
    t=re.search(r'text=\"([^\"]*)\"',n).group(1); d=re.search(r'content-desc=\"([^\"]*)\"',n).group(1)
    b=list(map(int,re.findall(r'[0-9]+',re.search(r'bounds=\"([^\"]*)\"',n).group(1))))
    nodes.append((t,d,b))
ex=[x for x in nodes if q==x[0] or q==x[1]]
hit=ex[-1:] or [x for x in nodes if q in x[0] or q in x[1]][:1]
if hit:
    b=hit[0][2]
    print((b[0]+b[2])//2,(b[1]+b[3])//2)
"
}
tap() { local c; c=$(center "$1"); if [ -z "$c" ]; then echo "НЕ ЗНАЙДЕНО: $1" >&2; return 1; fi; "$ADB" shell input tap $c; sleep "${2:-1.2}"; }
type_() { "$ADB" shell input text "$(echo "$1" | sed 's/ /%s/g')"; }
shot() { "$ADB" exec-out screencap -p > "$1"; }
texts() { dump > "$TEMP/uid.xml"; PYTHONIOENCODING=utf-8 python -c "
import re,os
s=open(os.environ['TEMP']+'/uid.xml','rb').read().decode('utf-8','replace')
print('\n'.join(t for t in re.findall(r'text=\"([^\"]*)\"',s) if t))"; }
