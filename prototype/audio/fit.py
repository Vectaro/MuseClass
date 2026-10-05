"""Модель «джерело × резонатор».

Попередня спроба зберігала одну спектральну огинаючу по частоті. Вона тримає
форманту на місці, але втрачає спад по НОМЕРУ гармоніки: у фортепіано на C4
третя гармоніка вже тиха, а усереднена по діапазону крива дає їй забагато.
Звідси «фортепіано = труба + клавесин» і «гітара = клавесин».

Реальний спектр — добуток двох речей:
  amp(k, f0) ≈ S(k) · F(k·f0)
де S(k) = k^(-a) — спад джерела (щипок, удар, губи), однаковий на всі висоти,
а F(f) — резонатор корпусу/труби, прив'язаний до частоти.

Обидві частини підганяються одночасно методом найменших квадратів у логарифмі.
"""
import subprocess, glob, os, json
import numpy as np

import os
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# Семпли не лежать у репо: це чужі записи на 1.6 МБ. Качає fetch-samples.sh.
SMP = os.environ.get('MUSECLASS_SAMPLES', os.path.join(HERE, 'samples'))

SR = 22050
NOTES = {'C3': 130.813, 'G3': 195.998, 'C4': 261.626,
         'G4': 391.995, 'C5': 523.251, 'E5': 659.255}
NH = 20
GRID = np.geomspace(90, 10000, 34)          # вузли резонатора


def load(p):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', p, '-ac', '1',
                          '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(float)


def harmonics(x, f0):
    env = np.abs(x)
    pk = int(np.argmax(env))
    seg = x[pk + int(.05 * SR): pk + int(.35 * SR)]
    if len(seg) < 2048:
        return None
    sp = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    fr = np.fft.rfftfreq(len(seg), 1 / SR)
    out = []
    for k in range(1, NH + 1):
        f = f0 * k
        if f > SR / 2 - 300:
            out.append(np.nan); continue
        b = sp[(fr >= f * .97) & (fr <= f * 1.03)]
        out.append(float(b.max()) if len(b) else np.nan)
    a = np.array(out)
    m = np.nanmax(a)
    return a / m if m and m > 0 else None


def basis(f):
    """Лінійна інтерполяція по логарифму частоти → ваги вузлів сітки."""
    w = np.zeros(len(GRID))
    lf, lg = np.log(f), np.log(GRID)
    if lf <= lg[0]:
        w[0] = 1; return w
    if lf >= lg[-1]:
        w[-1] = 1; return w
    i = np.searchsorted(lg, lf)
    t = (lf - lg[i - 1]) / (lg[i] - lg[i - 1])
    w[i - 1] = 1 - t; w[i] = t
    return w


def fit(rows):
    """rows: список (f0, harmonics[]). Повертає (a, F) — показник спаду
       джерела і криву резонатора."""
    A, y = [], []
    for f0, h in rows:
        for k in range(1, len(h) + 1):
            v = h[k - 1]
            if not np.isfinite(v) or v < 1e-4:
                continue
            f = f0 * k
            if f < GRID[0] * .7 or f > GRID[-1] * 1.3:
                continue
            L = np.log2(f0 / 261.626)
            row = np.concatenate([[-np.log(k), -L * np.log(k)], basis(f)])
            A.append(row); y.append(np.log(v))
    if len(A) < 20:
        return None
    A = np.array(A); y = np.array(y)
    # згладжування другої похідної резонатора, щоб крива не стрибала
    lam = 2.2
    n = len(GRID)
    S = np.zeros((n - 2, A.shape[1]))
    for i in range(n - 2):
        S[i, 2 + i] = 1; S[i, 3 + i] = -2; S[i, 4 + i] = 1
    # стримувальні члени: без них a і F не розрізняються однозначно —
    # підгонка може дати від'ємний спад, скомпенсований кривим резонатором
    pa = np.zeros(A.shape[1]); pa[0] = 3.0          # a тягнемо до 1.0
    pb = np.zeros(A.shape[1]); pb[1] = 3.0          # b тягнемо до 0
    A2 = np.vstack([A, lam * S, pa, pb])
    y2 = np.concatenate([y, np.zeros(n - 2), [3.0 * 1.0], [0.0]])
    sol, *_ = np.linalg.lstsq(A2, y2, rcond=None)
    a = float(sol[0]); b = float(sol[1])
    F = np.exp(sol[2:])
    F = F / F.max()
    return a, b, F


data = {}
for p in sorted(glob.glob(os.path.join(SMP, '*.mp3'))):
    inst, note = os.path.basename(p)[:-4].rsplit('_', 1)
    if note not in NOTES:
        continue
    x = load(p)
    if x.size < 3000:
        continue
    h = harmonics(x, NOTES[note])
    if h is not None:
        data.setdefault(inst, []).append((NOTES[note], h))

out = {'grid': [round(float(g)) for g in GRID], 'v': {}}
print('інструмент   спад   регістр   похибка   пік резонатора')
for inst in sorted(data):
    r = fit(data[inst])
    if not r:
        print('  ', inst, 'замало даних'); continue
    a, b, F = r
    # похибка підгонки
    errs = []
    for f0, h in data[inst]:
        L = np.log2(f0 / 261.626)
        pred = np.array([(k ** -(a + b * L)) * float(basis(f0 * k) @ F)
                         for k in range(1, NH + 1)])
        m = np.nanmax(pred)
        if m > 0:
            pred = pred / m
        ok = np.isfinite(h)
        errs.append(np.abs(pred[ok] - h[ok]).mean())
    out['v'][inst] = {'a': round(a, 3), 'b': round(b, 3),
                      'F': [round(float(x), 4) for x in F]}
    print(f'  {inst:9s} a={a:5.2f} b={b:+5.2f}   {np.mean(errs):.3f}     {GRID[int(np.argmax(F))]:6.0f} Гц')

json.dump(out, open(os.path.join(HERE, 'srcfilter.json'), 'w'))
print('\nвузлів резонатора:', len(GRID))
