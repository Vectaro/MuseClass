const fs=require('fs');const {JSDOM}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const d=new JSDOM(fs.readFileSync(PAGE,'utf8'),{runScripts:'dangerously',url:'https://x.test/',
  beforeParse(w){w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});}});
const w=d.window;
const sc=w.eval('SCORES').find(s=>s.id===(process.argv[2]||'s12'));
const play=sc.parts.filter(p=>p.ms&&p.ms.length);
const cols=Math.max(...play.map(p=>p.ms.length));
const items=play.map(pp=>{
  const ms=pp.ms.slice();
  while(ms.length<cols)ms.push([{p:[],d:pp.meter[0]*4/pp.meter[1]}]);
  return w.eval('toDisp')(ms,false,pp.bars,pp.v2);
});
const rows=w.eval('layoutScore')(cols,play);
let inner='',y=0,W=0;
rows.slice(0,+(process.argv[3]||2)).forEach((rg,i)=>{
  const slice=items.map(it=>it.slice(rg[0],rg[1]));
  const r=w.eval('renderScore')(slice,play,{timesig:i===0,end:false,barNums:true});
  const vb=r.svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/);
  W=Math.max(W,+vb[1]);
  inner+='<g transform="translate(0,'+y+')">'+r.svg.replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'')+'</g>';
  y+=+vb[2]+10;
});
fs.writeFileSync('score.svg','<svg xmlns="http://www.w3.org/2000/svg" width="'+W+'" height="'+y+'" viewBox="0 0 '+W+' '+y+'"><rect width="100%" height="100%" fill="#FAF8F3"/>'+inner+'</svg>');
console.log('систем:',rows.length,'ширина',W,'партій',play.length);
