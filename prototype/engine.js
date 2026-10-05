/* Такти для показу: суцільні паузи від двох поспіль згортаються в одну
   багатотактову паузу. Без цього витягнута партія духовика — це сторінки
   порожніх тактів, які треба рахувати вручну. */
function toDisp(ms,compress,bars,v2){
  const out=[];let i=0,ev=0;
  const empty=m=>m.length&&m.every(n=>!n.p.length);
  const plain=i=>{const b=bars&&bars[i];
    return !b||(!b.rep&&!b.ending&&!b.text&&!b.dyn&&!b.mark&&!b.hair&&!b.meter&&b.fifths===undefined&&!b.jump);};
  while(i<ms.length){
    if(compress&&empty(ms[i])&&plain(i)){
      let j=i,evs=0;
      while(j<ms.length&&empty(ms[j])&&plain(j)){evs+=ms[j].length;j++;}
      if(j-i>=2){out.push({mm:j-i,n:i,ev,evs});ev+=evs;i=j;continue;}
    }
    out.push({m:ms[i],n:i,ev,evs:ms[i].length,b:(bars&&bars[i])||null,
              v2:(v2&&v2[i])||null});
    ev+=ms[i].length;i++;
  }
  return out;
}
const dispEvents=d=>d.reduce((a,it)=>a+it.evs,0);

function baseOf(d){
  /* з допуском: тріольна вісімка це 1/3, а 1/3*3/2 у float дає 0.4999… */
  const T=[[6,4,1],[4,4,0],[3,2,1],[2,2,0],[1.5,1,1],[1,1,0],
           [.75,.5,1],[.5,.5,0],[.375,.25,1],[.25,.25,0],[.125,.125,0]];
  for(const [v,b,dt] of T)if(Math.abs(d-v)<1e-6)return [b,dt];
  let best=T[T.length-1];
  for(const t of T)if(Math.abs(d-t[0])<Math.abs(d-best[0]))best=t;
  return [best[1],best[2]];
}
const FLAGS={0.5:1,0.25:2,0.125:3};
const notated=n=>n.tup?n.d*n.tup.n/n.tup.of:n.d;

function restGlyph(base,cx,top){
  const ink='#1B1B1E';
  if(base>=4)return '<rect x="'+(cx-6)+'" y="'+(top+10)+'" width="12" height="5" fill="'+ink+'"/>';
  if(base>=2)return '<rect x="'+(cx-6)+'" y="'+(top+15)+'" width="12" height="5" fill="'+ink+'"/>';
  if(base>=1)
    return '<path d="M'+(cx-1.5)+' '+(top+6)+' c3 3.4 4.6 5 5.4 6.6 c.8 1.6 .4 2.8 -1.4 4.6'
      +' c-2 2 -2.6 3.4 -1.8 5.2 c.6 1.4 2 2.8 3.2 3.8 c-2.6 -1.2 -5.2 -1.6 -6.6 -.6'
      +' c-1.6 1.2 -1.2 3.4 .6 5.4 c-3 -2 -4.8 -4.2 -4.6 -6.2 c.2 -2 2 -3 4.6 -2.6'
      +' c-2.4 -2.6 -3.4 -4.6 -2.8 -6.4 c.4 -1.4 1.8 -2.8 3.4 -4.2 l0 0 z" fill="'+ink+'"/>';
  const n=base<=.25?2:1;
  let s='<path d="M'+(cx+3.5)+' '+(top+9)+' L'+(cx-3)+' '+(top+12+(n>1?8:0))
    +'" stroke="'+ink+'" stroke-width="1.6" fill="none"/>';
  for(let i=0;i<n;i++){
    const y=top+11+i*8;
    s+='<circle cx="'+(cx+3.2)+'" cy="'+y+'" r="1.9" fill="'+ink+'"/>'
      +'<path d="M'+(cx+3.2)+' '+(y+1.4)+' c1.6 .6 3 1.4 4 2.6" stroke="'+ink
      +'" stroke-width="1.3" fill="none"/>';
  }
  return s;
}
function artGlyph(kind,x,y,up){
  const ink='#1B1B1E',d=up?1:-1;
  if(kind==='staccato')return '<circle cx="'+x+'" cy="'+y+'" r="1.7" fill="'+ink+'"/>';
  if(kind==='tenuto')return '<rect x="'+(x-4.5)+'" y="'+(y-.9)+'" width="9" height="1.8" fill="'+ink+'"/>';
  if(kind==='accent')return '<path d="M'+(x-5)+' '+(y-3)+' L'+(x+5)+' '+y+' L'+(x-5)+' '+(y+3)
    +'" stroke="'+ink+'" stroke-width="1.6" fill="none" stroke-linejoin="round"/>';
  if(kind==='marcato')return '<path d="M'+(x-4)+' '+(y+3.2*d)+' L'+x+' '+(y-3.2*d)+' L'+(x+4)+' '+(y+3.2*d)
    +'" stroke="'+ink+'" stroke-width="1.7" fill="none" stroke-linejoin="round"/>';
  if(kind==='fermata')return '<path d="M'+(x-7)+' '+y+' a7 7 0 0 '+(up?1:0)+' 14 0" stroke="'+ink
    +'" stroke-width="1.5" fill="none"/><circle cx="'+x+'" cy="'+(y-3.4*d)+'" r="1.6" fill="'+ink+'"/>';
  return '';
}
/* ширина такту — щоб у партитурі всі стани мали спільну сітку */
function measureWidth(it){
  if(it.mm)return 104;
  let w=20;
  const scan=arr=>arr.forEach(n=>{
    w+=26+16*Math.min(n.d,2)+(n.p.length>1?6:0)+(n.art&&n.art.length?4:0)
      +((n.gr&&n.gr.length)?n.gr.length*13:0);
  });
  scan(it.m);
  if(it.v2)w=Math.max(w,20+it.v2.reduce((a,n)=>a+26+16*Math.min(n.d,2),0));
  if(it.b&&it.b.rep)w+=14;
  if(it.b&&(it.b.meter||it.b.fifths!==undefined))w+=34;
  if(it.m.some(n=>n.lyric))w=Math.max(w,20+it.m.length*30);
  return Math.max(w,72);
}

/* ---------- один нотний стан ----------
   Повертає готовий шматок SVG без обгортки, щоб партитура могла скласти
   кілька станів в одну систему зі спільними тактовими рисками. */
function renderStaff(items,opts){
  opts=opts||{};
  const clef=opts.clef||'treble';
  let fifths=opts.fifths||0,meter=opts.meter||[4,4];
  const nAcc=clef==='perc'?0:Math.min(Math.abs(fifths),7);
  const top=opts.top||0,ink='#1B1B1E';
  const nameW=opts.nameW||0;
  const padL=opts.padL!==undefined?opts.padL:(52+nAcc*9+(opts.timesig?20:0));
  const widths=opts.widths||items.map(measureWidth);
  const total=opts.total||(padL+widths.reduce((a,b)=>a+b,0)+16);
  const pi=opts.part||0;
  let s='',deco='',over='',under='';

  for(let i=0;i<5;i++)
    s+='<line x1="'+(padL-8)+'" y1="'+(top+i*10)+'" x2="'+(total-6)+'" y2="'+(top+i*10)
      +'" stroke="'+ink+'" stroke-width="1" opacity=".85"/>';
  s+=clefPath(clef,nameW+22,top);
  let kx=nameW+50;
  const kt=()=>{
    const tbl=fifths>0?SHARP_DIA[clef]:FLAT_DIA[clef],g=fifths>0?'♯':'♭';
    let o='',n=clef==='perc'?0:Math.min(Math.abs(fifths),7);
    for(let i=0;i<n;i++){
      o+='<text x="'+kx+'" y="'+(top+yOf(tbl[i],clef)+5)+'" font-size="15" font-family="serif" fill="'+ink+'">'+g+'</text>';
      kx+=9;
    }
    return o;
  };
  s+=kt();
  if(opts.timesig){
    s+='<text x="'+(kx+5)+'" y="'+(top+19)+'" font-family="serif" font-size="19" font-weight="700" fill="'+ink+'">'+meter[0]+'</text>';
    s+='<text x="'+(kx+5)+'" y="'+(top+38)+'" font-family="serif" font-size="19" font-weight="700" fill="'+ink+'">'+meter[1]+'</text>';
  }

  /* ---- прохід 1: позиції ---- */
  const laid=[];
  let x=padL;
  items.forEach((it,mi)=>{
    const w=widths[mi],x0=x;
    it['_x'+pi]=x0;
    if(!it.mm){
      const acc={};
      let cx=x0+(it.b&&(it.b.rep==='start'||it.b.rep==='both')?26:16);
      if(it.b&&(it.b.meter||it.b.fifths!==undefined))cx+=30;
      let evi=it.ev;
      const place=(n,voice)=>{
        const [base,dot]=baseOf(notated(n));
        const rec={it,n,ev:evi,x:cx,base,dot,heads:[],acc:[],rest:!n.p.length,voice};
        if(n.p.length){
          const heads=n.p.map(pt=>({pt,d:dia(pt),y:top+yOf(dia(pt),clef)})).sort((a,b)=>b.d-a.d);
          const avg=heads.reduce((a,h)=>a+h.y,0)/heads.length;
          rec.up=voice===2?false:(voice===1?true:avg>top+20);
          rec.heads=heads;
          rec.ymin=Math.min.apply(null,heads.map(h=>h.y));
          rec.ymax=Math.max.apply(null,heads.map(h=>h.y));
          let ax=cx-16;
          heads.forEach(h=>{
            if(h.pt.u)return;
            const key=h.pt.s+':'+h.pt.o,cur=acc[key]!==undefined?acc[key]:(keyAlters(fifths)[h.pt.s]||0);
            if(h.pt.a!==cur||n.forceAcc){
              rec.acc.push({g:ACC_GLYPH[h.pt.a]||'',x:ax,y:h.y+5});
              acc[key]=h.pt.a;ax-=10;
            }
          });
        }else{rec.up=voice!==2;rec.ymin=top+14;rec.ymax=top+24;}
        return rec;
      };
      it.m.forEach(n=>{
        if(n.gr&&n.gr.length){                 /* форшлаги перед нотою */
          n.gr.forEach(g=>{
            const gd=dia(g.p[0]),gy=top+yOf(gd,clef);
            const gx=cx-10-(n.gr.length-n.gr.indexOf(g)-1)*11;
            s+='<ellipse cx="'+gx+'" cy="'+gy+'" rx="4.1" ry="3.1" transform="rotate(-20 '+gx+' '+gy+')" fill="'+ink+'"/>';
            s+='<line x1="'+(gx+3.9)+'" y1="'+gy+'" x2="'+(gx+3.9)+'" y2="'+(gy-22)+'" stroke="'+ink+'" stroke-width="1.1"/>';
            s+='<path d="M'+(gx+3.9)+' '+(gy-22)+' q6 3 5.5 10 q-2 -5.5 -5.5 -6" fill="'+ink+'"/>';
            s+='<line x1="'+(gx-3)+'" y1="'+(gy-9)+'" x2="'+(gx+8)+'" y2="'+(gy-15)+'" stroke="'+ink+'" stroke-width="1.1"/>';
          });
        }
        const rec=place(n,0);
        laid.push(rec);
        if(n.lyric)under+='<text x="'+cx+'" y="'+(top+66)+'" text-anchor="middle" font-size="11.5"'
          +' font-family="sans-serif" fill="'+ink+'">'+esc(n.lyric)+'</text>';
        cx+=26+16*Math.min(n.d,2)+(n.p.length>1?6:0)+(n.art&&n.art.length?4:0)
           +((n.gr&&n.gr.length)?n.gr.length*13:0);
        evi++;
      });
      if(it.v2){                                /* другий голос: штилі вниз */
        let cx2=x0+16,ev2=0;
        it.v2.forEach(n=>{
          const save=cx;cx=cx2;
          const rec=place(n,2);rec.ev=-1;rec.v2i=ev2++;
          laid.push(rec);cx=save;
          cx2+=26+16*Math.min(n.d,2);
        });
      }
    }
    x+=w;
  });

  /* ---- вʼязки ---- */
  const beams=[];
  {
    let run=[];
    const flush=()=>{if(run.length>1)beams.push(run.slice());run=[];};
    laid.forEach((r,i)=>{
      const prev=laid[i-1];
      if(prev&&(prev.it!==r.it||prev.voice!==r.voice))flush();
      if(r.rest||r.base>=1){flush();return;}
      if(r.n.tup&&r.n.tup.pos==='start')flush();
      if(prev&&!!prev.n.tup!==!!r.n.tup)flush();
      run.push(r);
      if(r.n.tup){if(r.n.tup.pos==='stop')flush();return;}
      if(run.length>=4)flush();
    });
    flush();
  }
  const beamed=new Set();
  beams.forEach(g=>{
    const up=g[0].voice===2?false:(g[0].voice===1?true:g.filter(r=>r.up).length*2>=g.length);
    const ends=g.map(r=>up?r.ymin:r.ymax);
    const by=up?Math.min.apply(null,ends)-30:Math.max.apply(null,ends)+30;
    g.forEach(r=>{r.up=up;r.beamY=by;beamed.add(r);});
  });

  /* ---- прохід 2: ноти ---- */
  laid.forEach(r=>{
    const n=r.n,cx=r.x;
    if(r.rest){
      s+=restGlyph(r.base,cx,top);
      if(r.dot)s+='<circle cx="'+(cx+9)+'" cy="'+(top+13)+'" r="1.7" fill="'+ink+'"/>';
    }else{
      r.acc.forEach(a=>{s+='<text x="'+a.x+'" y="'+a.y+'" font-size="15" font-family="serif" fill="'+ink+'">'+a.g+'</text>';});
      for(let ly=top-10;ly>=r.ymin-2;ly-=10)
        s+='<line x1="'+(cx-10)+'" y1="'+ly+'" x2="'+(cx+10)+'" y2="'+ly+'" stroke="'+ink+'" stroke-width="1"/>';
      for(let ly=top+50;ly<=r.ymax+2;ly+=10)
        s+='<line x1="'+(cx-10)+'" y1="'+ly+'" x2="'+(cx+10)+'" y2="'+ly+'" stroke="'+ink+'" stroke-width="1"/>';
      const filled=r.base<=1;let prevD=null,shifted=false;
      const tag=r.ev>=0?' class="nh" data-p="'+pi+'" data-e="'+r.ev+'"':' class="nh2"';
      r.heads.forEach(h=>{
        shifted=(prevD!==null&&Math.abs(prevD-h.d)===1)?!shifted:false;
        prevD=h.d;
        const ox=shifted?(r.up?11.8:-11.8):0,X=cx+ox;
        h.X=X;
        if(h.pt.u){
          s+='<path'+tag+' d="M'+(X-6)+' '+(h.y-4.6)+' l1.9-1.9 L'+X+' '+(h.y-1.6)
            +' l4.1-3 l1.9 1.9 L'+(X+1.8)+' '+h.y+' l4.1 4.6 l-1.9 1.9 L'+X+' '+(h.y+1.6)
            +' l-4.1 3 l-1.9-1.9 L'+(X-1.8)+' '+h.y+' Z" fill="'+(filled?ink:'none')
            +'" stroke="'+ink+'" stroke-width="'+(filled?0:1.6)+'"/>';
        }else{
          s+='<ellipse'+tag+' cx="'+X+'" cy="'+h.y+'" rx="6.3" ry="4.7"'
            +' transform="rotate(-20 '+X+' '+h.y+')" fill="'+(filled?ink:'none')
            +'" stroke="'+ink+'" stroke-width="'+(filled?0:1.9)+'"/>';
        }
        if(r.dot)s+='<circle cx="'+(X+11)+'" cy="'+(h.y-2)+'" r="1.7" fill="'+ink+'"/>';
      });
      if(r.base<4){
        const sx=r.up?cx+5.9:cx-5.9,sy=r.up?r.ymin-33:r.ymax+33;
        r.sx=sx;r.sy=sy;
        if(!beamed.has(r)){
          s+='<line x1="'+sx+'" y1="'+(r.up?r.ymax:r.ymin)+'" x2="'+sx+'" y2="'+sy
            +'" stroke="'+ink+'" stroke-width="1.5"/>';
          const nf=FLAGS[r.base]||0;
          for(let k=0;k<nf;k++){
            const fy=sy+(r.up?1:-1)*k*6;
            s+='<path d="M'+sx+' '+fy+' q9 5 8 15 q-3 -8 -8 -9" fill="'+ink+'"'
              +(r.up?'':' transform="scale(1,-1) translate(0,'+(-2*fy)+')"')+'/>';
          }
        }
      }
      if(n.art&&n.art.length){
        const below=r.up;
        let ay=below?r.ymax+11:r.ymin-11;
        n.art.forEach(k=>{
          if(k==='fermata'){over+=artGlyph('fermata',cx,top-8,true);return;}
          s+=artGlyph(k,cx,ay,!below);
          ay+=below?7:-7;
        });
      }
    }
  });

  /* ---- вʼязки ---- */
  beams.forEach(g=>{
    const up=g[0].up,by=g[0].beamY;
    g.forEach(r=>{
      const sx=up?r.x+5.9:r.x-5.9;
      s+='<line x1="'+sx+'" y1="'+(up?r.ymax:r.ymin)+'" x2="'+sx+'" y2="'+by
        +'" stroke="'+ink+'" stroke-width="1.5"/>';
      r.sx=sx;r.sy=by;
    });
    const x1=(up?g[0].x+5.9:g[0].x-5.9),x2=(up?g[g.length-1].x+5.9:g[g.length-1].x-5.9);
    const levels=Math.max.apply(null,g.map(r=>FLAGS[r.base]||1));
    for(let L=0;L<levels;L++){
      const y=by+(up?1:-1)*L*5.5;
      if(L===0){
        s+='<rect x="'+x1+'" y="'+(y-(up?0:4))+'" width="'+(x2-x1)+'" height="4" fill="'+ink+'"/>';
      }else{
        let seg=[];
        g.forEach((r,i)=>{
          const need=(FLAGS[r.base]||1)>L;
          if(need)seg.push(r);
          if((!need||i===g.length-1)&&seg.length){
            const a=(up?seg[0].x+5.9:seg[0].x-5.9);
            const b=(up?seg[seg.length-1].x+5.9:seg[seg.length-1].x-5.9);
            s+='<rect x="'+a+'" y="'+(y-(up?0:4))+'" width="'+Math.max(b-a,7)+'" height="4" fill="'+ink+'"/>';
            seg=[];
          }
        });
      }
    }
  });

  /* ---- ліги ---- */
  function curve(x1,y1,x2,y2,up,w){
    const mx=(x1+x2)/2,d=up?-1:1,h=Math.min(16,Math.max(7,(x2-x1)*.18));
    return '<path d="M'+x1+' '+y1+' Q'+mx+' '+((y1+y2)/2+d*h)+' '+x2+' '+y2
      +'" stroke="'+ink+'" stroke-width="'+(w||1.5)+'" fill="none" stroke-linecap="round"/>';
  }
  const openTie={},openSlur=[];
  laid.forEach(r=>{
    const n=r.n;
    if(r.rest)return;
    if(n.tie==='stop'||n.tie==='both'){
      r.heads.forEach(h=>{
        const k=h.pt.s+':'+h.pt.o,o=openTie[k];
        if(!o){
          const up=!r.up;
          deco+=curve(h.X-16,h.y+(up?-6:6),h.X-7,h.y+(up?-6:6),up,1.6);
          return;
        }
        if(o.r!==r){
          const up=!o.r.up;
          deco+=curve(o.h.X+7,o.h.y+(up?-6:6),h.X-7,h.y+(up?-6:6),up,1.6);
          delete openTie[k];
        }
      });
    }
    if(n.tie==='start'||n.tie==='both')r.heads.forEach(h=>{openTie[h.pt.s+':'+h.pt.o]={r,h};});
    if(n.slur==='start'||n.slur==='both')openSlur.push(r);
    if((n.slur==='stop'||n.slur==='both')&&openSlur.length){
      const a=openSlur.pop();
      if(a!==r){
        const up=!a.up;
        deco+=curve(a.x,(up?a.ymin-9:a.ymax+9),r.x,(up?r.ymin-9:r.ymax+9),up,1.5);
      }
    }
  });
  Object.keys(openTie).forEach(k=>{
    const o=openTie[k];if(!o)return;
    const up=!o.r.up;
    deco+=curve(o.h.X+7,o.h.y+(up?-6:6),o.h.X+16,o.h.y+(up?-6:6),up,1.6);
  });

  /* ---- тріолі ---- */
  {
    let run=[];
    const draw=()=>{
      if(run.length<2){run=[];return;}
      const up=run.filter(r=>r.up).length*2>=run.length;
      const lvl=r=>r.beamY!==undefined?r.beamY:(r.sy!==undefined?r.sy:(up?r.ymin:r.ymax));
      const y=up?Math.min.apply(null,run.map(lvl))-11:Math.max.apply(null,run.map(lvl))+13;
      const x1=run[0].x-6,x2=run[run.length-1].x+6,mid=(x1+x2)/2,tip=up?4:-4;
      deco+='<path d="M'+x1+' '+(y+tip)+' L'+x1+' '+y+' L'+(mid-8)+' '+y+'" stroke="'+ink+'" stroke-width="1.2" fill="none"/>';
      deco+='<path d="M'+(mid+8)+' '+y+' L'+x2+' '+y+' L'+x2+' '+(y+tip)+'" stroke="'+ink+'" stroke-width="1.2" fill="none"/>';
      deco+='<text x="'+mid+'" y="'+(y+3.5)+'" text-anchor="middle" font-size="11" font-style="italic"'
        +' font-family="serif" fill="'+ink+'">'+(run[0].n.tup.n||3)+'</text>';
      run=[];
    };
    laid.forEach(r=>{
      if(r.n.tup){
        if(r.n.tup.pos==='start'&&run.length)draw();
        run.push(r);
        if(r.n.tup.pos==='stop')draw();
      }else if(run.length)draw();
    });
    draw();
  }

  /* ---- такти, репізи, написи ---- */
  x=padL;
  let hairFrom=(opts.hairOpen?padL:null),hairKind=opts.hairOpen||'cresc';
  items.forEach((it,mi)=>{
    const w=widths[mi],x0=x,b=it.b;
    if(it.mm){
      const L=x0+16,R=x0+w-16;
      s+='<path class="nh mmr" data-p="'+pi+'" data-e="'+it.ev+'" data-e2="'+(it.ev+it.evs-1)+'"'
        +' d="M'+L+' '+(top+11)+' H'+R+' V'+(top+19)+' H'+L+' Z" fill="'+ink+'"/>';
      s+='<rect x="'+(L-3)+'" y="'+(top+6)+'" width="3" height="18" fill="'+ink+'"/>';
      s+='<rect x="'+R+'" y="'+(top+6)+'" width="3" height="18" fill="'+ink+'"/>';
      s+='<text x="'+((L+R)/2)+'" y="'+(top-7)+'" text-anchor="middle" font-size="16" font-weight="700"'
        +' font-family="serif" fill="'+ink+'">'+it.mm+'</text>';
    }
    if(b&&(b.rep==='start'||b.rep==='both')){
      s+='<rect x="'+(x0+1)+'" y="'+top+'" width="3.4" height="40" fill="'+ink+'"/>';
      s+='<rect x="'+(x0+6.5)+'" y="'+top+'" width="1.2" height="40" fill="'+ink+'"/>';
      s+='<circle cx="'+(x0+12)+'" cy="'+(top+15)+'" r="1.9" fill="'+ink+'"/>';
      s+='<circle cx="'+(x0+12)+'" cy="'+(top+25)+'" r="1.9" fill="'+ink+'"/>';
    }
    /* зміна розміру або тональності посеред партитури */
    if(b&&b.fifths!==undefined&&b.fifths!==fifths){
      fifths=b.fifths;kx=x0+4;s+=kt();
    }
    if(b&&b.meter){
      meter=b.meter;
      s+='<text x="'+(x0+4)+'" y="'+(top+19)+'" font-family="serif" font-size="17" font-weight="700" fill="'+ink+'">'+meter[0]+'</text>';
      s+='<text x="'+(x0+4)+'" y="'+(top+37)+'" font-family="serif" font-size="17" font-weight="700" fill="'+ink+'">'+meter[1]+'</text>';
    }
    if(opts.chrome!==false){
      if(b&&b.ending){
        const ey=top-20;
        over+='<path d="M'+(x0+2)+' '+(ey+9)+' L'+(x0+2)+' '+ey+' L'+(x0+w-4)+' '+ey
          +(b.endingStop===false?'':' L'+(x0+w-4)+' '+(ey+9))
          +'" stroke="'+ink+'" stroke-width="1.2" fill="none"/>';
        over+='<text x="'+(x0+7)+'" y="'+(ey+9)+'" font-size="10.5" font-weight="600"'
          +' font-family="sans-serif" fill="'+ink+'">'+b.ending.join(', ')+'.</text>';
      }
      if(b&&b.mark){
        over+='<rect x="'+(x0-2)+'" y="'+(top-48)+'" width="20" height="16" fill="none" stroke="'+ink+'" stroke-width="1.2"/>';
        over+='<text x="'+(x0+8)+'" y="'+(top-36)+'" text-anchor="middle" font-size="12" font-weight="700"'
          +' font-family="sans-serif" fill="'+ink+'">'+esc(b.mark)+'</text>';
      }
      if(b&&b.text)
        over+='<text x="'+(x0+(b.mark?26:2))+'" y="'+(top-30)+'" font-size="12.5" font-style="italic"'
          +' font-family="serif" fill="'+ink+'">'+esc(b.text)+'</text>';
      if(b&&b.jump)
        over+='<text x="'+(x0+w-6)+'" y="'+(top-30)+'" text-anchor="end" font-size="12.5" font-style="italic"'
          +' font-family="serif" fill="'+ink+'">'+esc(b.jump)+'</text>';
    }
    if(b&&b.dyn)
      under+='<text x="'+(x0+2)+'" y="'+(top+(opts.lyrics?82:66))+'" font-size="14" font-style="italic"'
        +' font-weight="700" font-family="serif" fill="'+ink+'">'+esc(b.dyn)+'</text>';
    /* вилки cresc / dim */
    if(b&&b.hair==='start'){hairFrom=x0+4;hairKind=b.hairKind||'cresc';}
    const hairEnd=(b&&b.hair==='stop')||(hairFrom!==null&&mi===items.length-1);
    if(hairEnd&&hairFrom!==null){
      const hy=top+(opts.lyrics?78:58),x2=x0+w-8,gr=hairKind==='dim';
      under+='<path d="M'+hairFrom+' '+(hy+(gr?0:4))+' L'+x2+' '+(hy+(gr?4:0))
        +' M'+hairFrom+' '+(hy+(gr?8:4))+' L'+x2+' '+(hy+(gr?4:8))
        +'" stroke="'+ink+'" stroke-width="1.1" fill="none"/>';
      hairFrom=null;
    }
    x+=w;
    const last=mi===items.length-1;
    if(b&&(b.rep==='end'||b.rep==='both')){
      s+='<circle cx="'+(x-12)+'" cy="'+(top+15)+'" r="1.9" fill="'+ink+'"/>';
      s+='<circle cx="'+(x-12)+'" cy="'+(top+25)+'" r="1.9" fill="'+ink+'"/>';
      s+='<rect x="'+(x-7.5)+'" y="'+top+'" width="1.2" height="40" fill="'+ink+'"/>';
      s+='<rect x="'+(x-4.4)+'" y="'+top+'" width="3.4" height="40" fill="'+ink+'"/>';
    }else{
      s+='<line x1="'+x+'" y1="'+top+'" x2="'+x+'" y2="'+(top+40)+'" stroke="'+ink
        +'" stroke-width="'+(last&&opts.end?3:1.1)+'"/>';
      if(last&&opts.end)
        s+='<line x1="'+(x-5)+'" y1="'+top+'" x2="'+(x-5)+'" y2="'+(top+40)+'" stroke="'+ink+'" stroke-width="1.1"/>';
    }
    if(opts.barNums!==false&&opts.chrome!==false&&!(b&&b.ending)
       &&!(it.m&&it.m.some(n=>(n.art||[]).indexOf('fermata')>=0)))
      s+='<text x="'+(x0+2)+'" y="'+(top-8)+'" font-size="9.5" fill="#8A8A92" font-family="sans-serif">'+(it.n+1)+'</text>';
  });
  return {body:s+deco,over,under,padL,widths,total};
}

/* обгортка для одного стану */
function renderSystem(items,startEvent,opts){
  opts=opts||{};
  const hasTop=items.some(it=>it.b&&(it.b.text||it.b.ending||it.b.mark||it.b.jump));
  const hasDyn=items.some(it=>it.b&&(it.b.dyn||it.b.hair));
  const lyrics=items.some(it=>it.m&&it.m.some(n=>n.lyric));
  const top=(items.some(it=>it.b&&it.b.mark)?88:(hasTop?72:46));
  const r=renderStaff(items,Object.assign({},opts,{top,lyrics}));
  const height=top+40+(lyrics?36:0)+(hasDyn?52:40);
  return {svg:'<svg viewBox="0 0 '+r.total+' '+height+'" preserveAspectRatio="xMidYMid meet" style="max-width:'
      +r.total+'px;margin:0 auto">'+r.body+r.over+r.under+'</svg>',width:r.total};
}

/* ---------- партитура: кілька станів в одній системі ----------
   Ширина кожного такту — спільна для всіх партій, інакше тактові риски
   не збігатимуться по вертикалі. */
function renderScore(partItems,parts,opts){
  opts=opts||{};
  const n=parts.length;
  const cols=partItems[0].length;
  const widths=[];
  for(let i=0;i<cols;i++){
    let w=0;
    partItems.forEach(items=>{w=Math.max(w,measureWidth(items[i]));});
    widths.push(w);
  }
  /* зліва — колонка під назви інструментів, щоб вони не лізли на ключі */
  const nameW=64;
  let padL=0;
  parts.forEach(p=>{
    const nAcc=p.clef==='perc'?0:Math.min(Math.abs(p.fifths||0),7);
    padL=Math.max(padL,nameW+52+nAcc*9+(opts.timesig?20:0));
  });
  const total=padL+widths.reduce((a,b)=>a+b,0)+16;
  const hasTop=partItems[0].some(it=>it.b&&(it.b.text||it.b.ending||it.b.mark||it.b.jump));
  const top0=(partItems[0].some(it=>it.b&&it.b.mark)?88:(hasTop?72:46));
  const GAPY=86;
  let body='',over='',under='';
  parts.forEach((p,i)=>{
    const top=top0+i*GAPY;
    const lyrics=partItems[i].some(it=>it.m&&it.m.some(nn=>nn.lyric));
    const r=renderStaff(partItems[i],{
      clef:p.clef,fifths:p.fifths,meter:p.meter,timesig:opts.timesig,
      end:opts.end,barNums:opts.barNums,widths,padL,total,top,part:i,
      chrome:i===0,lyrics,nameW});
    body+=r.body;over+=r.over;under+=r.under;
    /* назва партії у лівій колонці, по центру стану */
    over+='<text x="'+(nameW-8)+'" y="'+(top+24)+'" text-anchor="end" font-size="9.5"'
      +' font-family="sans-serif" fill="#8A8A92">'+esc(shortName(p.name))+'</text>';
  });
  /* дужка і спільна ліва риска */
  const yTop=top0,yBot=top0+(n-1)*GAPY+40;
  body='<path d="M'+(padL-14)+' '+yTop+' q-7 '+((yBot-yTop)/2)+' 0 '+(yBot-yTop)
    +'" stroke="#1B1B1E" stroke-width="2.2" fill="none"/>'
    +'<line x1="'+(padL-8)+'" y1="'+yTop+'" x2="'+(padL-8)+'" y2="'+yBot
    +'" stroke="#1B1B1E" stroke-width="1.4"/>'+body;
  const height=yBot+56;
  return {svg:'<svg viewBox="0 0 '+total+' '+height+'" preserveAspectRatio="xMidYMid meet" style="max-width:'
      +total+'px;margin:0 auto">'+body+over+under+'</svg>',width:total};
}
function shortName(nm){
  const t=String(nm).replace(/\s*\((ліва|права) рука\)/,'')
    .replace(/\s*in\s*[A-H][b♭#♯]?$/,'');
  return t.length>11?t.slice(0,10)+'.':t;
}
function layout(items){
  const dense=items.some(it=>it.m&&it.m.length>5);
  const per=dense?2:(typeof window!=='undefined'&&window.innerWidth<380?2:3);
  const out=[];
  for(let i=0;i<items.length;i+=per)out.push(items.slice(i,i+per));
  return out;
}
/* у партитурі на систему вміщається менше тактів */
function layoutScore(cols,parts){
  const per=parts.length>=6?2:3;
  const out=[];
  for(let i=0;i<cols;i+=per)out.push([i,Math.min(i+per,cols)]);
  return out;
}
