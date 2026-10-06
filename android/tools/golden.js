/* Еталони для Java-парсера MusicXML.
   Бере демо-партитури прототипу (SCORES) і фікстури з prototype/tests/fixtures,
   проганяє їх через fromMusicXML з prototype/index.html і пише поруч:
     <id>.musicxml | <id>.mxl — вхід,
     <id>.json               — що з нього розбирає прототип.
   Java-тест (musicxml/.../GoldenTest) розбирає вхід своїм парсером і звіряє.

   Запуск з кореня репо (потрібен jsdom: cd prototype && npm install):
     node android/tools/golden.js
   Перезапускати, коли міняється парсер або демо в прототипі. */
'use strict';
const fs=require('fs'),path=require('path');
const ROOT=path.resolve(__dirname,'..','..');
const PROTO=path.join(ROOT,'prototype');
const OUT=path.join(ROOT,'android','musicxml','src','test','resources','golden');
const {JSDOM}=require(path.join(PROTO,'node_modules','jsdom'));

const dom=new JSDOM(fs.readFileSync(path.join(PROTO,'index.html'),'utf8'),{
  runScripts:'dangerously',url:'https://x.test/',
  beforeParse(w){
    w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});
    Object.assign(w,{TextDecoder,TextEncoder,DecompressionStream,Blob,Response});
  }});
const w=dom.window;
const fromMusicXML=w.eval('fromMusicXML'),toMusicXML=w.eval('toMusicXML');
const musicxmlFromBytes=w.eval('musicxmlFromBytes');

fs.rmSync(OUT,{recursive:true,force:true});
fs.mkdirSync(OUT,{recursive:true});
const dump=(id,parsed)=>fs.writeFileSync(path.join(OUT,id+'.json'),
  JSON.stringify(JSON.parse(JSON.stringify(parsed)),null,1)+'\n');

(async()=>{
  let n=0;
  for(const sc of w.eval('SCORES')){
    if(!sc.parts.every(p=>p.ms&&p.ms.length))continue;   /* лише PDF — нот немає */
    const xml=toMusicXML({title:sc.title,composer:sc.author,arranger:sc.arr,parts:sc.parts});
    fs.writeFileSync(path.join(OUT,'demo-'+sc.id+'.musicxml'),xml);
    dump('demo-'+sc.id,fromMusicXML(xml));n++;
  }
  for(const f of ['sample.musicxml','sample.mxl']){
    const bytes=fs.readFileSync(path.join(PROTO,'tests','fixtures',f));
    const id='fixture-'+f.replace('.','-');
    fs.writeFileSync(path.join(OUT,id+path.extname(f)),bytes);
    dump(id,fromMusicXML(await musicxmlFromBytes(new w.Uint8Array(bytes))));n++;
  }
  console.log('еталонів:',n,'→',path.relative(ROOT,OUT));
})().catch(e=>{console.error(e);process.exit(1);});
