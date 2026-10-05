"""Збирає index.html із частин.

index.html — це зібрана сторінка, а не джерело: три шматки в ній генеруються.
    engine.js            → нотний рушій
    audio/build-voices.py→ таблиця голосів (з audio/srcfilter.json)
    clef.js              → шлях скрипкового ключа

Навіщо окремий збирач. Тричі траплялось так: правиш engine.js, вставляєш його
в сторінку цілим блоком — і точкова правка, зроблена в сторінці раніше,
зникає без сліду. Тепер напрямок один: правиш частину, запускаєш build.py,
а `--check` (його ж ганяють тести) не дає зібраному файлу розійтися з
частинами непомітно.

    python3 tools/build.py            — зібрати
    python3 tools/build.py --check    — лише звірити, нічого не писати
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PAGE = os.path.join(ROOT, 'index.html')

ENGINE_FROM = '/* Такти для показу: суцільні паузи від двох поспіль згортаються в одну'
ENGINE_TO = '/* ============================ ПЛЕЄР ================================='
VOICES_FROM = '/* Вузли резонатора, Гц. */'
VOICES_TO = 'const INST_VOICE={'
# Прив'язка саме до скрипкового ключа: його шлях лежить у <g translate>.
# Без цього якоря регулярка чіпляє альтовий ключ, що стоїть вище у файлі.
CLEF_RE = re.compile(
    r"(translate\('\+\(x\+4\)\+','\+\(top\+30\)\+'\)\">'\s*\+'<path d=\")"
    r"([^\"]+)(\")")


def engine_block():
    return open(os.path.join(ROOT, 'engine.js'), encoding='utf-8').read().rstrip() + '\n'


def voices_block():
    import importlib.util
    p = os.path.join(ROOT, 'audio', 'build-voices.py')
    spec = importlib.util.spec_from_file_location('build_voices', p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m.build().rstrip() + '\n'


def clef_path():
    """clef.js рахує контур із кривих Безьє; беремо той самий N, що в сторінці."""
    js = ("const c=require(%s);"
          "const p=c.centerline(13);"
          "process.stdout.write(c.outline(p,10,0,0));"
          % json.dumps(os.path.join(ROOT, 'clef.js')))
    r = subprocess.run(['node', '-e', js], capture_output=True, text=True)
    if r.returncode:
        raise SystemExit('clef.js не відпрацював:\n' + r.stderr)
    return r.stdout.strip().splitlines()[-1]


def splice(src, frm, to, block, what):
    a = src.find(frm)
    b = src.find(to)
    if a < 0 or b < 0 or b <= a:
        raise SystemExit('не знайшов межі блоку «%s» в index.html' % what)
    return src[:a] + block + src[b:], src[a:b]


def main():
    check = '--check' in sys.argv
    src = open(PAGE, encoding='utf-8').read()
    diffs = []

    new, old = splice(src, ENGINE_FROM, ENGINE_TO, engine_block(), 'рушій')
    if old.rstrip() != engine_block().rstrip():
        diffs.append('engine.js')
    src = new

    new, old = splice(src, VOICES_FROM, VOICES_TO, voices_block(), 'голоси')
    if old.rstrip() != voices_block().rstrip():
        diffs.append('таблиця голосів')
    src = new

    want = clef_path()
    found = CLEF_RE.search(src)
    if not found:
        raise SystemExit('не знайшов шлях ключа в index.html')
    if found.group(2) != want:
        diffs.append('шлях ключа (clef.js)')
        src = src[:found.start(2)] + want + src[found.end(2):]

    if check:
        if diffs:
            print('index.html розійшовся з частинами: ' + ', '.join(diffs))
            print('полагодити: python3 tools/build.py')
            return 1
        print('index.html зібраний з поточних частин')
        return 0

    if diffs:
        open(PAGE, 'w', encoding='utf-8').write(src)
        print('перезібрано: ' + ', '.join(diffs))
    else:
        print('нічого не змінилось')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
