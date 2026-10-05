"""srcfilter.json → таблиця голосів для index.html.

Підгонка (fit.py) дає спектральну частину: показники спаду a, b і криву
резонатора F. Решта — рішення про поведінку інструмента (як швидко згасає,
чи має вібрато, скільки дихання), і вони живуть тут, а не в підгонці.

Друкує готовий шматок JS; build.py вставляє його в index.html.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SF = json.load(open(os.path.join(HERE, 'srcfilter.json'), encoding='utf-8'))

# kind   — decay (щипок, удар) чи sustain (смичок, духові)
# atk    — час атаки, с
# dec    — скільки звук тягнеться після зняття
# hr     — у скільки разів верхи згасають швидше за низи (заміряно analyze3.py)
# vib    — частота вібрато, Гц (0 — немає)
# nz     — шум дихання; у міді нуль, бо читався як перешкоди
# g      — вирівнювання гучності між інструментами
# th     — стук молоточка чи нігтя на атаці
FAM = {
 'piano':   dict(src='piano',   kind='decay',   atk=.003, dec=1.7, hr=3.6, vib=0,   nz=0,    g=1.00, th=.45),
 'guitar':  dict(src='guitar',  kind='decay',   atk=.004, dec=1.1, hr=1.5, vib=0,   nz=0,    g=1.05, th=.30),
 'bandura': dict(src='guitar',  kind='decay',   atk=.003, dec=.75, hr=2.2, vib=0,   nz=0,    g=1.00, th=.26),
 'bass':    dict(src='bass',    kind='decay',   atk=.005, dec=1.4, hr=2.1, vib=0,   nz=0,    g=1.30, th=.34),
 'violin':  dict(src='violin',  kind='sustain', atk=.070, dec=.20, hr=1,   vib=5.4, nz=.006, g=1.00, th=0),
 'trumpet': dict(src='trumpet', kind='sustain', atk=.028, dec=.12, hr=1,   vib=4.6, nz=0,    g=.92,  th=0),
 'trombone':dict(src='trombone',kind='sustain', atk=.045, dec=.16, hr=1,   vib=4.0, nz=0,    g=1.40, th=0),
 'sax':     dict(src='sax',     kind='sustain', atk=.030, dec=.14, hr=1,   vib=4.9, nz=.010, g=1.00, th=0),
 'flute':   dict(src='flute',   kind='sustain', atk=.055, dec=.14, hr=1,   vib=5.0, nz=.055, g=1.10, th=0),
 'voice':   dict(src='voice',   kind='sustain', atk=.085, dec=.22, hr=1,   vib=5.2, nz=.008, g=1.05, th=0),
}


def build():
    grid, V = SF['grid'], SF['v']
    lines = []
    for name, v in FAM.items():
        d = V[v['src']]
        F = '[' + ','.join(str(x) for x in d['F']) + ']'
        lines.append(
            "  %s:{a:%s,b:%s,F:%s,kind:'%s',atk:%s,dec:%s,hr:%s,vib:%s,nz:%s,g:%s,th:%s}"
            % (name, d['a'], d['b'], F, v['kind'], v['atk'], v['dec'],
               v['hr'], v['vib'], v['nz'], v['g'], v['th']))
    return ("/* Вузли резонатора, Гц. */\nconst SPEC_F=["
            + ','.join(str(g) for g in grid) + "];\nconst VOICES={\n"
            + ",\n".join(lines) + "\n};")


if __name__ == '__main__':
    sys.stdout.write(build() + '\n')
