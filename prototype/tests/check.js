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
    const wrong=[];
    p.ms.forEach((m,i)=>{
      if(p.bars&&p.bars[i]&&p.bars[i].meter)cur=p.bars[i].meter;
      const want=cur[0]*4/cur[1];
      const t=m.reduce((a,n)=>a+n.d,0);
      if(Math.abs(t-want)>1e-6)wrong.push((i+1)+': '+t.toFixed(2)+' замість '+want);
    });
    const beats=[...new Set(p.ms.map(m=>m.reduce((a,n)=>a+n.d,0)))];
    const okBeats=wrong.length===0;
    if(!okBeats)bad++;
    if(sc.id==='s12')
      console.log((okBeats?'  ok  ':'  FAIL')+'  '+p.name.padEnd(22)+
        'долі: '+beats.join(',')+'  | альтерації: '+(acc.size?[...acc].join(','):'немає'));
  }
}
console.log('\nтактів неправильної довжини у всьому каталозі:',bad);

/* Імпорт: партія має отримати ПОЧАТКОВІ розмір і тональність, а зміни
   посеред — лежати в bars. Раніше бралися останні з файлу. */
let impBad=0;
for(const sc of all){
  if(sc.parts.some(p=>!p.ms))continue;          /* PDF-демо без нот */
  const back=d.window.fromMusicXML(d.window.toMusicXML(sc));
  back.parts.forEach((bp,i)=>{
    const p=sc.parts.filter(x=>x.ms)[i];
    if(!p)return;
    const m0=p.meter||[4,4],f0=p.fifths||0;
    if(bp.meter[0]!==m0[0]||bp.meter[1]!==m0[1]||bp.fifths!==f0){
      impBad++;
      console.log('  FAIL  імпорт '+sc.title+' / '+p.name+': '+bp.meter.join('/')+' '+bp.fifths
        +' замість '+m0.join('/')+' '+f0);
    }
  });
}
console.log('партій з неправильним початковим розміром/тональністю після імпорту:',impBad);
/* Панель відтворення: перемикачі реприз і темпових написів мають бути.
   Колись друга, застаріла копія функції їх тихо перекривала. */
const vs=d.window.eval("state.view='s13';state.mode='part';const _h=playerSettingsHTML(SCORES[0].parts[0]);state.view=null;_h");
const vsBad=['dorep','dotempo','metro','tosound','compress'].filter(k=>vs.indexOf('data-act="'+k+'"')<0);
console.log(vsBad.length?'  FAIL  у панелі відтворення бракує: '+vsBad.join(', '):'  ok    панель відтворення: усі перемикачі на місці');
if(bad||impBad||vsBad.length)process.exitCode=1;
