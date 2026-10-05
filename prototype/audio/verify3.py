"""Порівняння синтезу з реальними семплами на трьох висотах — тепер із
   моделлю «джерело × резонатор»."""
import re,subprocess
import numpy as np
import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt

import os
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# Семпли не лежать у репо: це чужі записи на 1.6 МБ. Качає fetch-samples.sh.
SMP = os.environ.get('MUSECLASS_SAMPLES', os.path.join(HERE, 'samples'))
SR=22050
NOTES={'C3':130.813,'C4':261.626,'C5':523.251}
html=open(os.path.join(ROOT, 'index.html'), encoding='utf-8').read()
GRID=[float(x) for x in re.search(r'const SPEC_F=\[([^\]]+)\]',html).group(1).split(',')]
V={}
for m in re.finditer(r"(\w+):\{a:([\d.-]+),b:([\d.-]+),F:\[([^\]]+)\]",html):
    V[m.group(1)]=dict(a=float(m.group(2)),b=float(m.group(3)),
                       F=[float(x) for x in m.group(4).split(',')])

def specAt(F,f):
    if f<=GRID[0]: return F[0]
    if f>=GRID[-1]: return F[-1]*(GRID[-1]/f)**3
    i=1
    while i<len(GRID) and GRID[i]<f: i+=1
    t=(np.log(f)-np.log(GRID[i-1]))/(np.log(GRID[i])-np.log(GRID[i-1]))
    return F[i-1]+(F[i]-F[i-1])*t

def mine(name,f0,nh=20):
    v=V[name]
    L=np.log2(f0/261.626)
    ex=min(3.2,max(.25,v['a']+v['b']*L))
    a=np.array([max(0.0,(k**-ex)*specAt(v['F'],f0*k)) for k in range(1,nh+1)])
    return a/a.max() if a.max()>0 else a

def load(p):
    raw=subprocess.run(['ffmpeg','-v','quiet','-i',p,'-ac','1','-ar',str(SR),
                        '-f','f32le','-'],capture_output=True).stdout
    return np.frombuffer(raw,dtype=np.float32).astype(float)

def real(inst,note,nh=20):
    x=load(os.path.join(SMP, f'{inst}_{note}.mp3')); f0=NOTES[note]
    pk=int(np.argmax(np.abs(x)))
    seg=x[pk+int(.05*SR):pk+int(.35*SR)]
    sp=np.abs(np.fft.rfft(seg*np.hanning(len(seg))))
    fr=np.fft.rfftfreq(len(seg),1/SR)
    a=[]
    for k in range(1,nh+1):
        f=f0*k
        if f>SR/2-300: a.append(0.0); continue
        b=sp[(fr>=f*.97)&(fr<=f*1.03)]
        a.append(float(b.max()) if len(b) else 0.0)
    a=np.array(a)
    return a/a.max() if a.max()>0 else a

order=['piano','guitar','bass','trumpet','sax','violin']
fig,axes=plt.subplots(len(order),3,figsize=(12,13))
worst=0; rows=[]
for r,nm in enumerate(order):
    errs=[]
    for c,note in enumerate(['C3','C4','C5']):
        ax=axes[r][c]
        try: rr=real(nm,note)
        except Exception: ax.axis('off'); continue
        mm=mine(nm,NOTES[note])
        ks=np.arange(1,21)
        ax.bar(ks-.2,rr,.4,color='#3C4BC0',label='семпл')
        ax.bar(ks+.2,mm,.4,color='#F0B429',label='синтез')
        d=float(np.abs(rr-mm).mean()); errs.append(d); worst=max(worst,d)
        ax.set_title(f'{nm} {note} · розбіжність {d:.2f}',fontsize=8)
        ax.set_ylim(0,1.1); ax.set_xticks(ks[::4])
        if r==0 and c==0: ax.legend(fontsize=7)
    rows.append((nm,np.mean(errs) if errs else float('nan')))
fig.suptitle('Джерело × резонатор проти справжніх семплів',fontsize=12)
fig.tight_layout(); fig.savefig(os.path.join(HERE,'sf_check.png'),dpi=100)
print('середня розбіжність по інструменту:')
for nm,e in rows: print(f'  {nm:9s} {e:.3f}')
print('найгірша окрема:',round(worst,3))
