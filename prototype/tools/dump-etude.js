const fs=require('fs');const {JSDOM}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const d=new JSDOM(fs.readFileSync(PAGE,'utf8'),{runScripts:'dangerously',url:'https://x.test/',
  beforeParse(w){w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});}});
const w=d.window;
const sc=w.eval('SCORES').find(s=>s.id===(process.argv[2]||'s13'));
const p=sc.parts[+(process.argv[3]||0)];
const items=w.eval('toDisp')(p.ms,false,p.bars);
const rows=[];
for(let i=0;i<items.length;i+=2)rows.push(items.slice(i,i+2));
let inner='',y=0,W=0;
rows.forEach((it,i)=>{
  const r=w.eval('renderSystem')(it,0,{clef:p.clef,fifths:p.fifths,meter:p.meter,
    timesig:i===0,end:i===rows.length-1});
  const vb=r.svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/);
  W=Math.max(W,+vb[1]);
  inner+='<g transform="translate(0,'+y+')">'+r.svg.replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'')+'</g>';
  y+=+vb[2]+6;
});
fs.writeFileSync('etude.svg','<svg xmlns="http://www.w3.org/2000/svg" width="'+W+'" height="'+y+'" viewBox="0 0 '+W+' '+y+'"><rect width="100%" height="100%" fill="#FAF8F3"/>'+inner+'</svg>');
console.log('систем:',rows.length,'ширина',W);
