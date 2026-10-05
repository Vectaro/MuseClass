const fs=require('fs');const {JSDOM}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const d=new JSDOM(fs.readFileSync(PAGE,'utf8'),{runScripts:'dangerously',url:'https://x.test/',
  beforeParse(w){w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});}});
const all=d.window.eval('SCORES');
let bad=0;
for(const sc of all){
  for(const p of sc.parts){
    if(!p.ms)continue;
    const acc=new Set();
    p.ms.forEach(m=>m.forEach(n=>n.p.forEach(x=>{if(x.a)acc.add(x.a);})));
    /* розмір може мінятись посеред партії — рахуємо такт за тактом */
    let cur=p.meter;
    const bad=[];
    p.ms.forEach((m,i)=>{
      if(p.bars&&p.bars[i]&&p.bars[i].meter)cur=p.bars[i].meter;
      const want=cur[0]*4/cur[1];
      const t=m.reduce((a,n)=>a+n.d,0);
      if(Math.abs(t-want)>1e-6)bad.push((i+1)+': '+t.toFixed(2)+' замість '+want);
    });
    const beats=[...new Set(p.ms.map(m=>m.reduce((a,n)=>a+n.d,0)))];
    const okBeats=bad.length===0;
    if(!okBeats)bad++;
    if(sc.id==='s12')
      console.log((okBeats?'  ok  ':'  FAIL')+'  '+p.name.padEnd(22)+
        'долі: '+beats.join(',')+'  | альтерації: '+(acc.size?[...acc].join(','):'немає'));
  }
}
console.log('\nтактів неправильної довжини у всьому каталозі:',bad);
