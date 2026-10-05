/* Генератор скрипкового ключа.
   Центральна лінія з кубічних Безьє + змінна товщина (як пером),
   на виході — замкнений контур, а не дріт сталої ширини.
   Координати: одиниця = міжлінійний проміжок (10px), y=0 — лінія соль. */
const fs=require('fs');

function bez(p0,p1,p2,p3,n){
  const out=[];
  for(let i=0;i<=n;i++){
    const t=i/n,u=1-t;
    out.push([
      u*u*u*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t*t*t*p3[0],
      u*u*u*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t*t*t*p3[1]
    ]);
  }
  return out;
}
/* центральна лінія: від хвоста внизу, вгору по стеблу, петля зверху,
   вниз і всередину — у завиток навколо лінії соль */
function centerline(N){
  N=N||26;
  const segs=[
    /* хвіст: нижче за завиток, гачок вліво */
    [[-0.74,2.26],[-0.84,2.92],[0.18,2.98],[0.38,2.10]],
    /* стебло вгору, крізь завиток */
    [[0.38,2.10],[0.48,0.66],[0.34,-1.22],[0.24,-2.30]],
    /* верхня петля */
    [[0.24,-2.30],[0.06,-3.40],[0.78,-4.30],[1.14,-3.62]],
    [[1.14,-3.62],[1.52,-2.90],[0.64,-2.00],[-0.16,-1.34]],
    /* ліва дуга вниз */
    [[-0.16,-1.34],[-1.16,-0.62],[-1.44,0.02],[-1.06,0.56]],
    /* завиток навколо лінії соль: низ, правий бік, усередину */
    [[-1.06,0.56],[-0.70,1.34],[0.66,1.30],[0.94,0.42]],
    [[0.94,0.42],[1.20,-0.44],[0.44,-0.94],[-0.08,-0.60]],
    [[-0.08,-0.60],[-0.58,-0.32],[-0.64,0.30],[-0.26,0.48]]
  ];
  let pts=[];
  segs.forEach((s,i)=>{
    const p=bez(s[0],s[1],s[2],s[3],N);
    pts=pts.concat(i?p.slice(1):p);
  });
  return pts;
}
/* товщина вздовж лінії: тонко на кінцях і на верхівці, товсто на дугах */
function widthAt(t){
  const k=[
    [0.00,0.024],[0.05,0.078],[0.12,0.112],[0.22,0.106],
    [0.31,0.078],[0.40,0.044],[0.47,0.056],[0.55,0.120],
    [0.64,0.170],[0.74,0.162],[0.84,0.104],[0.93,0.050],[1.00,0.022]
  ];
  for(let i=1;i<k.length;i++){
    if(t<=k[i][0]){
      const a=k[i-1],b=k[i],u=(t-a[0])/(b[0]-a[0]);
      return a[1]+(b[1]-a[1])*u;
    }
  }
  return k[k.length-1][1];
}
function outline(pts,scale,cx,cy){
  const n=pts.length,L=[],R=[];
  for(let i=0;i<n;i++){
    const p=pts[i],a=pts[Math.max(0,i-1)],b=pts[Math.min(n-1,i+1)];
    let dx=b[0]-a[0],dy=b[1]-a[1];
    const len=Math.hypot(dx,dy)||1;dx/=len;dy/=len;
    const w=widthAt(i/(n-1));
    L.push([p[0]-dy*w,p[1]+dx*w]);
    R.push([p[0]+dy*w,p[1]-dx*w]);
  }
  const all=L.concat(R.reverse());
  const f=([x,y])=>(cx+x*scale).toFixed(1)+' '+(cy+y*scale).toFixed(1);
  return 'M'+all.map(f).join(' L')+' Z';
}
function clefSVG(cx,cy,scale){
  const pts=centerline();
  /* очко завитка — маленький круг у центрі спіралі */
  const eye=pts[pts.length-1];
  return '<path d="'+outline(pts,scale,cx,cy)+'" fill="#1B1B1E"/>'
    +'<circle cx="'+(cx+eye[0]*scale).toFixed(2)+'" cy="'+(cy+eye[1]*scale).toFixed(2)
    +'" r="'+(0.13*scale).toFixed(2)+'" fill="#1B1B1E"/>';
}

/* --- прев'ю: ключ на справжньому стані --- */
const top=40,gap=10,gLine=top+30;
let s='<svg xmlns="http://www.w3.org/2000/svg" width="300" height="130" viewBox="0 0 300 130">'
  +'<rect width="100%" height="100%" fill="#FAF8F3"/>';
for(let i=0;i<5;i++)s+='<line x1="10" y1="'+(top+i*gap)+'" x2="290" y2="'+(top+i*gap)+'" stroke="#1B1B1E" stroke-width="1" opacity=".85"/>';
s+=clefSVG(34,gLine,gap);
s+='</svg>';
fs.writeFileSync('clef_try.svg',s);
console.log('ok');
module.exports={clefSVG,centerline,outline,widthAt};
