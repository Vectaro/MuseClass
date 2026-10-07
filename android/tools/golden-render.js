/* Еталони для Java-верстки нотного стану (:engraving).
   Бере демо-партитури, які вже лежать як еталони парсера
   (android/musicxml/src/test/resources/golden/demo-*.musicxml), розбирає їх
   fromMusicXML прототипу і верстає кожну партію справжнім renderStaff з
   prototype/engine.js — по 3 такти на систему, як layout() прототипу на
   широкому екрані. Пише в android/engraving/src/test/resources/golden/
   <demo>.json: для кожної партії й системи — канонічний список фігур тіла
   стану (без шару deco: ліги й тріолі ще не перенесені).

   Канонічний запис (той самий будує Shape.canon() у Java):
     L x1 y1 x2 y2 товщина | R x y w h | E cx cy rx ry залита | C cx cy r
     T x y розмір текст    | P x y — перша точка контуру.

   Запуск з кореня репо (потрібен jsdom: cd prototype && npm install):
     node android/tools/golden-render.js
   Перезапускати, коли міняється engine.js або демо. */
'use strict';
const fs=require('fs'),path=require('path');
const ROOT=path.resolve(__dirname,'..','..');
const PROTO=path.join(ROOT,'prototype');
const IN=path.join(ROOT,'android','musicxml','src','test','resources','golden');
const OUT=path.join(ROOT,'android','engraving','src','test','resources','golden');
const {JSDOM}=require(path.join(PROTO,'node_modules','jsdom'));

const dom=new JSDOM(fs.readFileSync(path.join(PROTO,'index.html'),'utf8'),{
  runScripts:'dangerously',url:'https://x.test/',
  beforeParse(w){
    w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});
    Object.assign(w,{TextDecoder,TextEncoder,DecompressionStream,Blob,Response});
  }});
const w=dom.window;

/* renderStaff з engine.js, але тіло стану окремо від deco (ліг і тріолей) */
const eng=fs.readFileSync(path.join(PROTO,'engine.js'),'utf8');
const a=eng.indexOf('function renderStaff('),b=eng.indexOf('/* обгортка для одного стану */');
let src=eng.slice(a,b);
const RET='return {body:s+deco,over,under,padL,widths,total};';
if(src.indexOf(RET)<0)throw new Error('renderStaff змінився: не знайдено '+RET);
src=src.replace(RET,'return {body:s,deco,over,under,padL,widths,total};')
  .replace('function renderStaff(','function goldenStaff(');
w.eval(src);
const goldenStaff=w.eval('goldenStaff'),toDisp=w.eval('toDisp'),fromMusicXML=w.eval('fromMusicXML');

const num=v=>Number(v).toFixed(2);
const attrs=s=>{const o={};s.replace(/([\w-]+)="([^"]*)"/g,(_,k,v)=>{o[k]=v;});return o;};
function canon(svg){
  const out=[],stack=[[0,0]];
  const re=/<(\/?)(\w+)([^>]*?)\/?>(?:([^<]*)<\/text>)?/g;
  let m;
  while((m=re.exec(svg))){
    const [,close,tag,rest,text]=m;
    if(tag==='g'){
      if(close){stack.pop();continue;}
      const t=/translate\(([-\d.]+),([-\d.]+)\)/.exec(rest)||[0,0,0];
      const [ox,oy]=stack[stack.length-1];
      stack.push([ox+Number(t[1]),oy+Number(t[2])]);
      continue;
    }
    if(close)continue;
    const [ox,oy]=stack[stack.length-1],A=attrs(rest);
    if(tag==='line')out.push(['L',num(ox+ +A.x1),num(oy+ +A.y1),num(ox+ +A.x2),num(oy+ +A.y2),num(A['stroke-width'])].join(' '));
    else if(tag==='rect')out.push(['R',num(ox+ +A.x),num(oy+ +A.y),num(A.width),num(A.height)].join(' '));
    else if(tag==='ellipse')out.push(['E',num(ox+ +A.cx),num(oy+ +A.cy),num(A.rx),num(A.ry),A.fill==='none'?0:1].join(' '));
    else if(tag==='circle')out.push(['C',num(ox+ +A.cx),num(oy+ +A.cy),num(A.r)].join(' '));
    else if(tag==='text')out.push(['T',num(A.x),num(A.y),String(Number(A['font-size'])),text].join(' '));
    else if(tag==='path'){
      const p=/^M\s*([-\d.]+)[\s,]+([-\d.]+)/.exec(A.d.trim());
      out.push(['P',num(ox+ +p[1]),num(oy+ +p[2])].join(' '));
    }
  }
  return out;
}

fs.rmSync(OUT,{recursive:true,force:true});
fs.mkdirSync(OUT,{recursive:true});
const PER=3,TOP=46;
let n=0;
for(const f of fs.readdirSync(IN).filter(f=>/^demo-.*\.musicxml$/.test(f)).sort()){
  const sc=fromMusicXML(fs.readFileSync(path.join(IN,f),'utf8'));
  const parts=sc.parts.map(p=>{
    const items=toDisp(p.ms,false,p.bars,p.v2);
    const systems=[];
    for(let i=0;i<items.length;i+=PER){
      const slice=items.slice(i,i+PER),last=i+PER>=items.length;
      const opts={clef:p.clef,fifths:p.fifths,meter:p.meter,timesig:i===0,end:last,top:TOP};
      const r=goldenStaff(slice,opts);
      systems.push({from:i,to:i+slice.length,timesig:i===0,end:last,shapes:canon(r.body)});
    }
    return {name:p.name,clef:p.clef,fifths:p.fifths,measures:items.length,systems};
  });
  fs.writeFileSync(path.join(OUT,f.replace('.musicxml','.json')),JSON.stringify({top:TOP,parts},null,1)+'\n');
  n++;
}
console.log('еталонів верстки:',n,'→',path.relative(ROOT,OUT));
