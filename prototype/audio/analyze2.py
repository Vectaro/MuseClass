"""Чи тримається тембр на номерах гармонік, чи на частотах?
   Міряємо спектр того самого інструмента на різних висотах і дивимось,
   де пік: на сталому НОМЕРІ гармоніки чи на сталій ЧАСТОТІ."""
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
NH = 24


def load(p):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', p, '-ac', '1',
                          '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(float)


def harm(x, f0):
    env = np.abs(x)
    pk = int(np.argmax(env))
    seg = x[pk + int(.06 * SR): pk + int(.40 * SR)]
    if len(seg) < 2048:
        return None
    sp = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    fr = np.fft.rfftfreq(len(seg), 1 / SR)
    a = []
    for k in range(1, NH + 1):
        f = f0 * k
        if f > SR / 2 - 200:
            a.append(0.0); continue
        b = sp[(fr >= f * .97) & (fr <= f * 1.03)]
        a.append(float(b.max()) if len(b) else 0.0)
    a = np.array(a)
    return a / a.max() if a.max() > 0 else None


data = {}
for p in sorted(glob.glob(os.path.join(SMP, '*.mp3'))):
    inst, note = os.path.basename(p)[:-4].rsplit('_', 1)
    if note not in NOTES:
        continue
    x = load(p)
    if x.size < 2000:
        continue
    h = harm(x, NOTES[note])
    if h is None:
        continue
    data.setdefault(inst, {})[note] = h

print('Пік спектра: на якій гармоніці / на якій частоті\n')
for inst in sorted(data):
    row = []
    for n in ['C3', 'G3', 'C4', 'G4', 'C5', 'E5']:
        if n not in data[inst]:
            continue
        h = data[inst][n]
        k = int(np.argmax(h)) + 1
        row.append(f'{n}: h{k:<2d} ({NOTES[n]*k:6.0f} Гц)')
    print(f'{inst:9s} ' + '  '.join(row))

# Спектральна огинаюча по частоті: амплітуда як функція абсолютної частоти
GRID = np.geomspace(80, 11000, 46)
env_out = {}
for inst in sorted(data):
    acc = np.zeros(len(GRID))
    wsum = np.zeros(len(GRID))
    for n, h in data[inst].items():
        f0 = NOTES[n]
        fs = f0 * np.arange(1, len(h) + 1)
        ok = (fs > 60) & (fs < 11000) & (h > 0)
        if ok.sum() < 3:
            continue
        # кожну гармоніку розмазуємо гаусом по логарифму частоти
        for f, a in zip(fs[ok], h[ok]):
            w = np.exp(-((np.log(GRID) - np.log(f)) ** 2) / (2 * 0.16 ** 2))
            acc += w * a
            wsum += w
    e = np.where(wsum > 1e-6, acc / np.maximum(wsum, 1e-6), 0)
    if e.max() > 0:
        e = e / e.max()
    env_out[inst] = [round(float(v), 3) for v in e]

json.dump({'grid': [round(float(g)) for g in GRID], 'env': env_out},
          open(os.path.join(HERE, 'formants.json'), 'w'), indent=1)
print('\nСпектральні огинаючі (пік по частоті):')
for inst, e in env_out.items():
    i = int(np.argmax(e))
    print(f'  {inst:9s} пік ~{GRID[i]:5.0f} Гц')
