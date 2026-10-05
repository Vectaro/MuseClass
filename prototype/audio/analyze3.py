"""Як швидко згасають низькі й високі частоти окремо.
   Для фортепіано й щипкових це головне: високі вмирають набагато швидше,
   і саме через це однакове згасання всіх гармонік звучить як клавесин."""
import subprocess, glob, os, json
import numpy as np

import os
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# Семпли не лежать у репо: це чужі записи на 1.6 МБ. Качає fetch-samples.sh.
SMP = os.environ.get('MUSECLASS_SAMPLES', os.path.join(HERE, 'samples'))
SR=22050
NOTES={'C3':130.813,'G3':195.998,'C4':261.626,'G4':391.995,'C5':523.251,'E5':659.255}

def load(p):
    raw=subprocess.run(['ffmpeg','-v','quiet','-i',p,'-ac','1','-ar',str(SR),
                        '-f','f32le','-'],capture_output=True).stdout
    return np.frombuffer(raw,dtype=np.float32).astype(float)

def band_decay(x,f0):
    """Час спаду до 1/e для смуги навколо основного тону і для верхів."""
    n=1024; hop=256
    if len(x)<n*4: return None
    frames=[]
    for i in range(0,len(x)-n,hop):
        sp=np.abs(np.fft.rfft(x[i:i+n]*np.hanning(n)))
        frames.append(sp)
    S=np.array(frames)
    fr=np.fft.rfftfreq(n,1/SR)
    lo=(fr>f0*0.7)&(fr<f0*2.5)
    hi=(fr>f0*4)&(fr<min(f0*14,10000))
    if hi.sum()<2: return None
    t=np.arange(len(S))*hop/SR
    def tau(mask):
        e=S[:,mask].sum(axis=1)
        if e.max()<=0: return None
        pk=int(np.argmax(e)); e=e[pk:]; tt=t[pk:]-t[pk]
        target=e[0]/np.e
        below=np.where(e<target)[0]
        return float(tt[below[0]]) if len(below) else float(tt[-1])
    a,b=tau(lo),tau(hi)
    return None if (a is None or b is None) else (a,b)

res={}
for p in sorted(glob.glob(os.path.join(SMP, '*.mp3'))):
    inst,note=os.path.basename(p)[:-4].rsplit('_',1)
    if note not in NOTES: continue
    x=load(p)
    if x.size<4000: continue
    r=band_decay(x,NOTES[note])
    if r: res.setdefault(inst,[]).append(r)

print('Час спаду до 1/e, секунд:\n')
out={}
for inst in sorted(res):
    arr=np.array(res[inst])
    lo,hi=float(np.median(arr[:,0])),float(np.median(arr[:,1]))
    out[inst]={'lo':round(lo,3),'hi':round(hi,3),'ratio':round(lo/max(hi,1e-3),2)}
    print(f'  {inst:9s} низи {lo:5.2f}   верхи {hi:5.2f}   верхи швидші у {lo/max(hi,1e-3):4.1f} раз')
json.dump(out,open(os.path.join(HERE, 'decays.json'), 'w'),indent=1)
