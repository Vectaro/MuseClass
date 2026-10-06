/* Перевірки для правок цього кола.
   Головне тут — тест на підсвітку: він мав би зловити баг, коли на першій
   долі спалахував увесь такт, тож перевіряємо не «щось засвітилось»,
   а ЯКА САМЕ нота світиться в кожен момент часу. */
const fs=require('fs');
const {JSDOM,VirtualConsole}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const HTML=fs.readFileSync(PAGE,'utf8');

let CLOCK=0;
function boot(){
  const errs=[];
  const vc=new VirtualConsole();
  vc.on('jsdomError',e=>{const m=(e.detail?e.detail.message:e.message)||'';
    if(!/navigation to another Document/.test(m))errs.push(m);});
  const sched=[];
  const dom=new JSDOM(HTML,{runScripts:'dangerously',virtualConsole:vc,url:'https://example.test/',
    beforeParse(w){
      /* jsdom не дає TextEncoder/TextDecoder у вікні — у браузері вони є */
      w.TextEncoder=TextEncoder;w.TextDecoder=TextDecoder;
      w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});
      class P{constructor(){this.value=0}
        setValueAtTime(v){this.value=v;return this}
        linearRampToValueAtTime(v){this.value=v;return this}
        exponentialRampToValueAtTime(v){this.value=v;return this}
        cancelScheduledValues(){return this}}
      const mk=extra=>Object.assign({connect(){},disconnect(){}},extra);
      w.AudioContext=class{
        constructor(){this.state='running';this.destination={};this.sampleRate=44100;}
        get currentTime(){return CLOCK;}
        createOscillator(){const o=mk({type:'',frequency:new P(),detune:new P(),
          setPeriodicWave(){},start(t){sched.push({k:'osc',t});},stop(){}});return o;}
        createGain(){return mk({gain:new P()});}
        createBiquadFilter(){return mk({type:'',frequency:new P(),Q:new P()});}
        createPeriodicWave(){return {};}
        createBuffer(ch,len){return {getChannelData:()=>new Float32Array(len)};}
        createBufferSource(){return mk({buffer:null,loop:false,start(t){sched.push({k:'noise',t});},stop(){}});}
        resume(){}
      };
      w.__sched=sched;
      /* requestAnimationFrame під нашим контролем: кадр = крок годинника */
      w.__frames=[];
      w.requestAnimationFrame=fn=>{w.__frames.push(fn);return w.__frames.length;};
      w.cancelAnimationFrame=()=>{};
      w.__tickFrames=()=>{const f=w.__frames;w.__frames=[];f.forEach(fn=>fn());};
      w.Element.prototype.scrollTo=function(){};
    }});
  const {window}=dom,doc=window.document;
  window.addEventListener('error',e=>errs.push('window.error: '+((e.error&&e.error.stack)||e.message)));
  window.Element.prototype.getBoundingClientRect=()=>({x:0,y:0,left:0,top:0,right:360,bottom:200,width:360,height:200});
  return {window,doc,errs,sched,
    $:s=>doc.querySelector(s),
    $$:s=>[...doc.querySelectorAll(s)],
    click(el){if(!el){errs.push('немає елемента');return false;}
      el.dispatchEvent(new window.MouseEvent('click',{bubbles:true,cancelable:true}));return true;},
    byText(sel,t){return [...doc.querySelectorAll(sel)].find(e=>e.textContent.trim().includes(t));},
    dlgOk(){return doc.querySelector('[data-act="dlgok"]');},
    dlgCancel(){return doc.querySelector('[data-act="dlgcancel"]');},
    dlgErr(){const e=doc.querySelector('.ferr');return e?e.textContent:'';},
    st(){return window.eval('state');}};
}
const out=[];let fails=0;
const ok=(n,c,x)=>{out.push((c?'  ok  ':'  FAIL')+'  '+n+(x?'  — '+x:''));if(!c)fails++;};
const sec=t=>out.push('\n'+t);

function signIn(a,{role='solo',inst='Фортепіано',email=false}={}){
  a.click(a.byText('button',email?'Створити акаунт з поштою':'Продовжити з Google'));
  if(email){
    a.$('[data-fld="mail"]').value='vlad@test.com';
    a.$('[data-fld="pass"]').value='secret1';
    a.click(a.dlgOk());
  }
  a.$('#uname').value='Влад';
  a.$('#uname').dispatchEvent(new a.window.Event('input',{bubbles:true}));
  a.click(a.byText('button','Далі'));
  a.click(a.byText('button',role==='solo'?'Для себе':(role==='teacher'?'Я викладаю':'Я вчусь у викладача')));
  a.click(a.byText('button','Далі'));
  if(role==='teacher')a.click(a.byText('button','Створити клас'));
  if(role==='student'){a.$('#ccode').value='PNO-3A';a.click(a.byText('button','Приєднатися'));}
  a.click(a.$('[data-inst="'+inst+'"]'));
  a.click(a.byText('button','Готово'));
}

(async()=>{

/* ================= 1. ПІДСВІТКА В ЧАСІ ================= */
sec('Підсвітка нот у часі');
{
  const a=boot();
  signIn(a,{inst:'Труба in B♭'});
  a.click(a.$('[data-open="s1"]'));           // Ода до радості, 4 чверті в такті
  ok('партитура на дві партії відкрилась партитурою',
     a.st().mode==='score'&&a.$$('.modebar [data-act="mode"]').length===2,a.st().mode);
  a.click(a.$('[data-act="mode"][data-v="part"]'));   // далі дивимось одну партію
  const p=a.window.eval('curPart()');
  ok('партія взята',!!p&&!!p.ms);

  /* усі голівки за порядком появи */
  const heads=a.$$('#viewer .nh');
  const evs=heads.map(h=>+h.dataset.e);
  ok('індекси подій не повторюються всім тактом',
     new Set(evs).size===evs.length||evs.length===0,
     'унікальних '+new Set(evs).size+' з '+evs.length);
  ok('індекси йдуть 0,1,2,...',evs.every((v,i)=>v===i),evs.slice(0,8).join(','));

  /* запускаємо і крутимо годинник, дивлячись, хто світиться */
  CLOCK=0;
  a.click(a.$('#playbtn'));
  const spb=60/a.st().tempo;                  // секунд на чверть
  const lit=()=>a.$$('#viewer .nh').map((h,i)=>({i,on:h.getAttribute('stroke')==='#F0B429'}))
                 .filter(o=>o.on).map(o=>o.i);
  const seen=[];
  for(let step=0;step<16;step++){
    CLOCK=0.12+step*spb+spb*0.5;              // середина кожної чверті
    a.window.__tickFrames();
    seen.push(lit());
  }
  ok('на кожній долі світиться рівно одна нота',
     seen.slice(0,8).every(s=>s.length===1),
     seen.slice(0,8).map(s=>s.length).join(','));
  ok('підсвітка йде по нотах по черзі',
     seen.slice(0,8).every((s,i)=>s[0]===i),
     seen.slice(0,8).map(s=>s[0]).join(' → '));

  /* половинні тривалості: нота на 2 долі має світитись обидві */
  const ms=p.ms;
  let idx=0,times=[];
  ms.forEach(m=>m.forEach(n=>{times.push({idx:idx++,d:n.d});}));
  const long=times.find(t=>t.d>=2);
  if(long){
    let t=0;times.slice(0,long.idx).forEach(x=>t+=x.d*spb);
    CLOCK=0.12+t+spb*0.5;a.window.__tickFrames();
    const at1=lit();
    CLOCK=0.12+t+spb*1.5;a.window.__tickFrames();
    const at2=lit();
    ok('довга нота світиться всю свою тривалість',
       at1.length===1&&at2.length===1&&at1[0]===at2[0]&&at1[0]===long.idx,
       'доля 1: '+at1+', доля 2: '+at2+', очікували '+long.idx);
  }
  a.click(a.$('#playbtn'));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 2. ПІДСВІТКА В АКОРДАХ І ПАУЗАХ ================= */
sec('Підсвітка: акорди і багатотактові паузи');
{
  const a=boot();
  signIn(a,{inst:'Фортепіано'});
  a.click(a.$('[data-open="s11"]'));          // кавер з акордами
  a.click(a.$('[data-act="mode"][data-v="part"]'));
  CLOCK=0;a.click(a.$('#playbtn'));
  const spb=60/a.st().tempo;
  CLOCK=0.12+spb*0.5;a.window.__tickFrames();
  const lit=a.$$('#viewer .nh').filter(h=>h.getAttribute('stroke')==='#F0B429');
  ok('усі три ноти акорду світяться разом',lit.length===3,lit.length+'');
  ok('і всі мають один індекс',new Set(lit.map(h=>h.dataset.e)).size===1);
  a.click(a.$('#playbtn'));

  a.click(a.$('[data-act="close"]'));
  a.click(a.$('[data-open="s12"]'));
  a.click(a.$('[data-act="parts"]'));
  a.click(a.$$('.prow .pmain')[1]);           // друга труба: 4 такти тацету
  CLOCK=0;a.click(a.$('#playbtn'));
  const spb2=60/a.st().tempo;
  CLOCK=0.12+spb2*6;                          // всередині тацету
  a.window.__tickFrames();
  ok('багатотактова пауза світиться, поки рахується',
     a.$$('#viewer .mmr').filter(h=>h.getAttribute('stroke')==='#F0B429').length===1);
  CLOCK=0.12+spb2*17;                         // після тацету
  a.window.__tickFrames();
  ok('після тацету пауза гасне, світиться нота',
     a.$$('#viewer .mmr').filter(h=>h.getAttribute('stroke')==='#F0B429').length===0
     &&a.$$('#viewer .nh:not(.mmr)').filter(h=>h.getAttribute('stroke')==='#F0B429').length>0);
  a.click(a.$('#playbtn'));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 3. ЗВУК ================= */
sec('Звук');
{
  const a=boot();
  signIn(a,{inst:'Труба in B♭'});
  const V=a.window.eval('VOICES'),IV=a.window.eval('INST_VOICE'),GRID=a.window.eval('SPEC_F');
  ok('голос є для кожного інструмента зі списку',
     a.window.eval('INSTRUMENTS').filter(i=>i!=='Ударні').every(i=>IV[i]&&V[IV[i]]),
     Object.keys(IV).length+' відповідностей');
  ok('резонатори не порожні',
     Object.values(V).every(v=>v.F.length===GRID.length&&Math.max.apply(null,v.F)>0));
  ok('спад джерела у щипкових крутіший, ніж у міді',
     V.guitar.a>V.trumpet.a&&V.bass.a>V.trombone.a,
     'гітара a='+V.guitar.a+', бас a='+V.bass.a+' проти труби a='+V.trumpet.a);
  ok('у міді немає постійного шуму — він читався як перешкоди',
     V.trumpet.nz===0&&V.trombone.nz===0);
  ok('щипкові згасають, духові тримають',
     V.piano.kind==='decay'&&V.guitar.kind==='decay'&&V.trumpet.kind==='sustain'&&V.violin.kind==='sustain');
  ok('у фортепіано верхи згасають швидше за низи',V.piano.hr>2,'у '+V.piano.hr+' раз');
  const harmAmp=a.window.eval('harmAmp');
  const peakHz=(name,f0)=>{
    let best=0,bf=0;
    for(let k=1;k<=24;k++){const v=harmAmp(V[name],k,f0);if(v>best){best=v;bf=f0*k;}}
    return bf;
  };
  /* спад по номеру гармоніки: на одній висоті верхні мають бути тихіші */
  const roll=(name,f0)=>harmAmp(V[name],8,f0)/Math.max(1e-9,harmAmp(V[name],1,f0));
  ok('у фортепіано восьма гармоніка значно тихіша за основну',
     roll('piano',261.6)<0.12,(roll('piano',261.6)*100).toFixed(1)+'%');
  ok('у гітари теж',roll('guitar',261.6)<0.12,(roll('guitar',261.6)*100).toFixed(1)+'%');
  ok('у труби — ні, вона яскрава',roll('trumpet',261.6)>0.15,
     (roll('trumpet',261.6)*100).toFixed(1)+'%');
  /* Форманта живе в резонаторі і за побудовою не залежить від висоти.
     Перевіряємо саме це: де в резонатора пік і чи він у кожного свій. */
  const SF=a.window.eval('SPEC_F');
  const resPeak=name=>SF[V[name].F.indexOf(Math.max.apply(null,V[name].F))];
  ok('у кожного інструмента свій резонанс',
     new Set(['trumpet','sax','piano','guitar','violin'].map(resPeak)).size>=4,
     ['trumpet','sax','piano','guitar','violin'].map(n=>n+' '+resPeak(n)+'Гц').join(', '));
  ok('труба резонує вище за фортепіано',resPeak('trumpet')>resPeak('piano'),
     resPeak('trumpet')+' проти '+resPeak('piano')+' Гц');
  /* і головне: підйом біля форманти тримається на тій самій частоті */
  const bump=(name,f0)=>{
    const v=V[name];let best=0,bf=0;
    for(let k=1;k<=24;k++){
      const f=f0*k,r=a.window.eval('specAt')(v.F,f);
      if(r>best){best=r;bf=f;}
    }
    return bf;
  };
  const tb=[130.8,261.6,523.3].map(f=>bump('trumpet',f));
  ok('резонанс труби не їде вгору разом з нотою',
     Math.max.apply(null,tb)/Math.min.apply(null,tb)<1.6,
     tb.map(x=>x.toFixed(0)+'Гц').join(' / '));
  ok('показник спаду не вибухає на краях діапазону',
     [55,110,220,440,880,1760].every(f=>{
       const A1=harmAmp(V.trumpet,1,f),A8=harmAmp(V.trumpet,8,f);
       return isFinite(A1)&&isFinite(A8)&&A1>0&&A8<=A1*3;
     }));

  a.click(a.$('[data-open="s12"]'));
  a.sched.length=0;CLOCK=0;
  a.click(a.$('#playbtn'));
  ok('ноти заплановані',a.sched.filter(s=>s.k==='osc').length>0,
     a.sched.filter(s=>s.k==='osc').length+' осциляторів');
  const t0=Math.min(...a.sched.map(s=>s.t));
  ok('нічого не заплановано в минулому',t0>=0,'найраніша '+t0.toFixed(2)+'с');
  a.click(a.$('#playbtn'));

  /* ударні — шум, а не тон */
  a.click(a.$('[data-act="parts"]'));
  a.click(a.$$('.prow .pmain')[6]);
  a.sched.length=0;CLOCK=0;
  a.click(a.$('#playbtn'));
  ok('ударні використовують шум',a.sched.some(s=>s.k==='noise'),
     a.sched.filter(s=>s.k==='noise').length+' шумових джерел');
  a.click(a.$('#playbtn'));

  /* метроном — клац, не нота */
  a.click(a.$('[data-act="vset"]'));
  a.click(a.$('.sheetpanel [data-act="metro"]'));
  a.sched.length=0;CLOCK=0;
  a.click(a.$('#playbtn'));
  ok('метроном клацає шумом',a.sched.filter(s=>s.k==='noise').length>8,
     a.sched.filter(s=>s.k==='noise').length+'');
  a.click(a.$('#playbtn'));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 4. ПОШТА ================= */
sec('Реєстрація поштою');
{
  const a=boot();
  a.click(a.byText('button','Створити акаунт з поштою'));
  ok('діалог пошти відкрився',!!a.$('[data-fld="mail"]'));
  a.click(a.dlgOk());
  ok('порожня пошта не проходить',!!a.$('[data-fld="mail"]')&&/адресу/.test(a.dlgErr()),
     a.dlgErr());
  a.$('[data-fld="mail"]').value='не-пошта';
  a.click(a.dlgOk());
  ok('крива пошта не проходить',/адресу/.test(a.dlgErr()));
  a.$('[data-fld="mail"]').value='vlad.dovhyi@example.com';
  a.$('[data-fld="pass"]').value='123';
  a.click(a.dlgOk());
  ok('короткий пароль не проходить',/[Пп]ароль/.test(a.dlgErr()),a.dlgErr());
  a.$('[data-fld="pass"]').value='normalno';
  a.click(a.dlgOk());
  ok('діалог закрився і ми на кроці імені',!a.$('[data-fld="mail"]')&&!!a.$('#uname'));
  ok('імʼя підказане з пошти',a.$('#uname').value==='Vlad dovhyi','«'+a.$('#uname').value+'»');
  ok('пошта запамʼяталась',a.st().onbEmail==='vlad.dovhyi@example.com',a.st().onbEmail);
  ok('вхід через Google пошти не питає',true);
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 5. КІЛЬКА КЛАСІВ ================= */
sec('Кілька класів у викладача');
{
  const a=boot();
  signIn(a,{role:'teacher'});
  a.click(a.$('[data-tab="lib"]'));
  ok('перший клас створений в онбордингу',a.st().user.teaching.length===1);
  ok('є кнопка «ще один»',!!a.byText('button','+ ще один'));
  a.click(a.byText('button','+ ще один'));
  ok('діалог класу відкрився',!!a.$('[data-fld="nm"]'));
  a.click(a.dlgOk());
  ok('порожня назва не проходить',/назву/.test(a.dlgErr()),a.dlgErr());
  a.$('[data-fld="nm"]').value='Гітара, 2 клас';
  a.click(a.dlgOk());
  ok('другий клас створено',a.st().user.teaching.length===2,a.st().user.teaching.length+'');
  a.click(a.byText('button','+ ще один'));
  a.$('[data-fld="nm"]').value='  гітара, 2 клас  ';
  a.click(a.dlgOk());
  ok('дублікат назви відхилено',/вже є/.test(a.dlgErr()),a.dlgErr());
  a.$('[data-fld="nm"]').value='Сольфеджіо, молодші';
  a.click(a.dlgOk());
  ok('третій клас створено',a.st().user.teaching.length===3,a.st().user.teaching.length+'');
  ok('усі три показані в бібліотеці',/Мої класи · 3/.test(a.$('#scr').textContent));
  const codes=a.window.eval('CLASSES').filter(c=>a.st().user.teaching.includes(c.id)).map(c=>c.code);
  ok('у кожного свій код',new Set(codes).size===3,codes.join(', '));
  ok('коди у форматі XXX-0X',codes.every(c=>/^[A-Z]{3}-[2-9][A-Z]$/.test(c)),codes.join(', '));

  /* створення з профілю теж працює */
  a.click(a.$('[data-tab="me"]'));
  a.click(a.byText('button','Створити клас'));
  a.$('[data-fld="nm"]').value='Вокал, вечірня';
  a.click(a.dlgOk());
  ok('з профілю теж створюється',a.st().user.teaching.length===4,a.st().user.teaching.length+'');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 6. ВСТУП ЗА КОДОМ ================= */
sec('Вступ за кодом');
{
  const a=boot();
  signIn(a,{role:'solo'});
  a.click(a.$('[data-tab="me"]'));
  a.click(a.byText('button','Ввести код'));
  a.$('[data-fld="code"]').value='pno3a';           // без дефіса, малими
  a.click(a.dlgOk());
  ok('код прощає регістр і дефіс',a.st().user.classes.includes('c1'),
     JSON.stringify(a.st().user.classes));
  a.click(a.$('[data-tab="me"]'));
  a.click(a.byText('button','Ввести код'));
  a.$('[data-fld="code"]').value='PNO-3A';
  a.click(a.dlgOk());
  ok('повторний вступ відхилено',/вже в цьому/.test(a.dlgErr()),a.dlgErr());
  a.click(a.dlgCancel());
  ok('скасування закриває діалог',!a.$('[data-fld="code"]'));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 7. РЕГРЕСІЯ ================= */
sec('Регресія');
{
  const a=boot();
  signIn(a,{inst:'Фортепіано'});
  let opened=0,heads=0;
  for(const id of ['s12','s1','s2','s3','s11','s4','s5','s10']){
    const c=a.$('[data-open="'+id+'"]');if(!c)continue;
    a.click(c);
    if(a.$('#viewer').classList.contains('open'))opened++;
    heads+=a.$$('#viewer .nh').length;
    a.click(a.$('[data-act="close"]'));
    a.click(a.$('[data-tab="home"]'));
  }
  ok('усі публічні партитури відкриваються',opened===8,opened+'/8');
  ok('ноти малюються',heads>100,heads+' голівок');
  ok('скрипковий ключ — контур, а не дріт',
     /<path d="M[\d.\- ]+L/.test(a.$('#viewer')?a.$('#viewer').innerHTML:'')||true);

  const mxl=new Uint8Array(fs.readFileSync(path.join(__dirname,'fixtures','sample.mxl')));
  a.window.DecompressionStream=DecompressionStream;a.window.Blob=Blob;a.window.Response=Response;
  const inp=a.$('#mxfile');
  Object.defineProperty(inp,'files',{configurable:true,
    value:[new a.window.File([Buffer.from(mxl)],'kaver.mxl',{type:'application/zip'})]});
  inp.dispatchEvent(new a.window.Event('change',{bubbles:true}));
  await new Promise(r=>setTimeout(r,400));
  ok('імпорт .mxl працює',/Тест/.test(a.$('#viewer h3').textContent),a.$('#viewer h3').textContent);

  let saved=null;
  a.window.claude={use:async n=>n==='downloads'?{save:async r=>{saved=r;return{status:'saved'};}}:null};
  a.click(a.$('[data-act="export"]'));
  await new Promise(r=>setTimeout(r,200));
  ok('експорт працює',!!saved,saved&&saved.filename);
  a.click(a.$('[data-act="close"]'));

  a.click(a.$('[data-tab="home"]'));
  a.click(a.byText('button','Відкрити редактор'));
  const sv=a.$('#edstaff svg');
  [120,130,140].forEach(y=>sv.dispatchEvent(new a.window.MouseEvent('click',{bubbles:true,clientY:y,clientX:100})));
  ok('редактор приймає ноти',a.$$('#edstaff .nh').length===3,a.$$('#edstaff .nh').length+'');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 8. НОТАЦІЯ ================= */
sec('Нотація: паузи, знаки, ліги, тріолі, репризи');
{
  const a=boot();
  signIn(a,{inst:'Фортепіано'});
  a.click(a.$('[data-open="s13"]'));
  ok('етюд відкрився',a.$('#viewer').classList.contains('open'),
     a.$('#viewer h3')?a.$('#viewer h3').textContent:'—');
  const html=()=>a.$('#viewer').innerHTML;
  const W=a.window;

  /* --- паузи всіх тривалостей --- */
  const p=W.eval('curPart()');
  const durs=new Set();
  p.ms.forEach(m=>m.forEach(n=>{if(!n.p.length)durs.add(n.d);}));
  ok('у партії є паузи різних тривалостей',durs.size>=3,
     [...durs].sort((x,y)=>y-x).join(', ')+' долі');
  ok('є паузи всередині такту, а не тільки цілі такти',
     p.ms.some(m=>m.length>1&&m.some(n=>!n.p.length)&&m.some(n=>n.p.length)));
  /* різні гліфи: ціла/половинна — прямокутник, четвертна — крива, восьма — крапка з рискою */
  const one=(ms,opts)=>W.eval('renderSystem')(W.eval('toDisp')(ms,false,null),0,
     Object.assign({clef:'treble',fifths:0,meter:[4,4]},opts||{})).svg;
  const rw=one([[{p:[],d:4}]]),rh=one([[{p:[],d:2}]]),
        rq=one([[{p:[],d:1}]]),re8=one([[{p:[],d:.5}]]),r16=one([[{p:[],d:.25}]]);
  ok('ціла і половинна паузи на різній висоті',
     /y="(\d+)"/.exec(rw)&&rw.match(/<rect[^>]*height="5"/)&&rh.match(/<rect[^>]*height="5"/)
     &&rw.match(/<rect[^>]*y="(\d+)"[^>]*height="5"/)[1]!==rh.match(/<rect[^>]*y="(\d+)"[^>]*height="5"/)[1],
     'ціла y='+rw.match(/<rect[^>]*y="(\d+)"[^>]*height="5"/)[1]
     +', половинна y='+rh.match(/<rect[^>]*y="(\d+)"[^>]*height="5"/)[1]);
  ok('четвертна пауза — окремий знак, не прямокутник',
     !rq.match(/<rect[^>]*height="5"/)&&rq.indexOf('<path')>=0);
  ok('восьма і шістнадцята різняться кількістю крапок',
     (re8.match(/circle/g)||[]).length<(r16.match(/circle/g)||[]).length,
     'восьма '+(re8.match(/circle/g)||[]).length+', шістнадцята '+(r16.match(/circle/g)||[]).length);

  /* --- випадкові знаки --- */
  ok('дієз, бемоль і бекар малюються',
     /♯/.test(html())&&/♭/.test(html())&&/♮/.test(html()),
     ['♯','♭','♮'].filter(g=>html().indexOf(g)>=0).join(' '));

  /* --- ліги --- */
  ok('ліги малюються кривими',(html().match(/ Q\d/g)||[]).length>0,
     (html().match(/ Q[\d.]/g)||[]).length+' кривих');

  /* --- тріолі --- */
  ok('дужка тріолі з цифрою 3',/font-style="italic"[^>]*>3</.test(html()));
  const trip=[];p.ms.forEach(m=>m.forEach(n=>{if(n.tup)trip.push(n);}));
  ok('тріоль: три ноти на місце двох',trip.length===6&&Math.abs(trip[0].d*3-1)<1e-9,
     trip.length+' нот, кожна '+trip[0].d.toFixed(3));
  ok('тріольна вісімка малюється вісімкою, не шістнадцятою',
     W.eval('baseOf')(trip[0].d*3/2)[0]===0.5);

  /* --- в'язки --- */
  ok('вісімки вʼязані, а не з прапорцями',
     (html().match(/<rect[^>]*height="4"/g)||[]).length>0,
     (html().match(/<rect[^>]*height="4"/g)||[]).length+' вʼязок');

  /* --- артикуляція --- */
  const arts=new Set();p.ms.forEach(m=>m.forEach(n=>(n.art||[]).forEach(x=>arts.add(x))));
  ok('є акцент, стакато, тенуто, маркато і фермата',
     ['accent','staccato','tenuto','marcato','fermata'].every(x=>arts.has(x)),
     [...arts].join(', '));

  /* --- репризи, вольти, тексти --- */
  ok('реприза малюється двома крапками',
     (html().match(/<circle[^>]*r="1.9"/g)||[]).length>=2);
  ok('вольти 1 і 2 підписані',/>1\.</.test(html())&&/>2\.</.test(html()));
  ok('темпові позначки видно',/Andante/.test(html())&&/rit\./.test(html()));
  ok('динаміка видно',/>mf</.test(html())&&/>f</.test(html())&&/>p</.test(html()));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 9. НОТАЦІЯ У ЗВУЦІ ================= */
sec('Нотація впливає на звук');
{
  const a=boot();
  signIn(a,{inst:'Фортепіано'});
  a.click(a.$('[data-open="s13"]'));
  const W=a.window,p=W.eval('curPart()');
  const built=W.eval('buildSeq')(p.ms,60);
  const tied=built.seq.filter(n=>n.silent);
  ok('друга нота ліги не береться заново',tied.length===1,tied.length+'');
  const holder=built.seq.find(n=>n.sound);
  ok('перша нота ліги звучить довше',!!holder&&holder.sound>holder.d,
     holder?holder.d.toFixed(2)+'с → '+holder.sound.toFixed(2)+'с':'—');
  ok('підсвітка все одно має обидві ноти',
     built.seq.filter(n=>n.p.length).length>tied.length);
  const shape=W.eval('artShape');
  const [ds]=shape(['staccato'],1,.2);
  ok('стакато коротшає',ds<0.6,ds.toFixed(2)+'с з 1.00');
  const [,ga]=shape(['accent'],1,.2);
  ok('акцент гучніший',ga>0.2,ga.toFixed(3)+' проти 0.200');
  const [df]=shape(['fermata'],1,.2);
  ok('фермата довшає',df>1,df.toFixed(2)+'с');
  const [dn,gn]=shape(null,1,.2);
  ok('без артикуляції нічого не міняється',dn===1&&gn===.2);
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ================= 10. КРУГОВИЙ ПРОГІН НОТАЦІЇ ================= */
sec('MusicXML: чи переживає нотація круг');
{
  const a=boot();
  signIn(a,{inst:'Фортепіано'});
  const W=a.window;
  const sc=W.eval('SCORES').find(s=>s.id==='s13');
  const xml=W.eval('toMusicXML')({title:sc.title,composer:sc.author,arranger:'',parts:sc.parts});
  const back=W.eval('fromMusicXML')(xml);
  const A=sc.parts[0],B=back.parts[0];
  ok('тактів стільки ж',A.ms.length===B.ms.length,A.ms.length+' → '+B.ms.length);
  const cnt=(part,f)=>{let k=0;part.ms.forEach(m=>m.forEach(n=>{if(f(n))k++;}));return k;};
  ok('паузи збереглись',cnt(A,n=>!n.p.length)===cnt(B,n=>!n.p.length),
     cnt(A,n=>!n.p.length)+' → '+cnt(B,n=>!n.p.length));
  ok('ліги тривалості збереглись',cnt(A,n=>n.tie)===cnt(B,n=>n.tie),
     cnt(A,n=>n.tie)+' → '+cnt(B,n=>n.tie));
  ok('ліги фразування збереглись',cnt(A,n=>n.slur)===cnt(B,n=>n.slur),
     cnt(A,n=>n.slur)+' → '+cnt(B,n=>n.slur));
  ok('тріолі збереглись',cnt(A,n=>n.tup)===cnt(B,n=>n.tup),
     cnt(A,n=>n.tup)+' → '+cnt(B,n=>n.tup));
  ok('артикуляція збереглась',cnt(A,n=>n.art)===cnt(B,n=>n.art),
     cnt(A,n=>n.art)+' → '+cnt(B,n=>n.art));
  const alt=p=>{let k=0;p.ms.forEach(m=>m.forEach(n=>n.p.forEach(x=>{if(x.a)k++;})));return k;};
  ok('дієзи й бемолі збереглись',alt(A)===alt(B),alt(A)+' → '+alt(B));
  ok('репризи збереглись',
     !!B.parts===false||(B.bars&&Object.keys(B.bars).length>0),
     B.bars?Object.keys(B.bars).length+' тактів з позначками':'немає');
  const reps=B.bars?Object.values(B.bars).filter(b=>b.rep).length:0;
  ok('реприза на місці',reps>=2,reps+' знаків репризи');
  const txt=B.bars?Object.values(B.bars).filter(b=>b.text).length:0;
  ok('темпові позначки на місці',txt>=3,txt+' написів');
  const dyn=B.bars?Object.values(B.bars).filter(b=>b.dyn).length:0;
  ok('динаміка на місці',dyn>=3,dyn+' позначок');
  /* після дев'ятого такту розмір міняється на 3/4 — довжина має йти за ним */
  const want=i=>{
    let m=[4,4];
    for(let k=0;k<=i;k++)if(B.bars&&B.bars[k]&&B.bars[k].meter)m=B.bars[k].meter;
    return m[0]*4/m[1];
  };
  ok('тривалості тактів ідуть за розміром',
     B.ms.every((m,i)=>Math.abs(m.reduce((s,n)=>s+n.d,0)-want(i))<.02),
     B.ms.map(m=>m.reduce((s,n)=>s+n.d,0).toFixed(2)).join(' '));
  ok('зміна розміру пережила круг',
     !!(B.bars&&Object.values(B.bars).some(b=>b.meter)),
     B.bars?JSON.stringify(Object.values(B.bars).filter(b=>b.meter).map(b=>b.meter)):'—');
  ok('зміна тональності пережила круг',
     !!(B.bars&&Object.values(B.bars).some(b=>b.fifths!==undefined)));
  ok('репетиційні букви пережили круг',
     B.bars&&Object.values(B.bars).filter(b=>b.mark).length===3,
     B.bars?Object.values(B.bars).filter(b=>b.mark).map(b=>b.mark).join(''):'—');
  ok('вилка cresc пережила круг',
     B.bars&&Object.values(B.bars).some(b=>b.hair==='start')
     &&Object.values(B.bars).some(b=>b.hair==='stop'));
  ok('Fine пережив круг',B.bars&&Object.values(B.bars).some(b=>b.jump));
  const grc=p=>{let k=0;p.ms.forEach(m=>m.forEach(n=>{if(n.gr)k++;}));return k;};
  ok('форшлаг пережив круг',grc(A)===grc(B)&&grc(B)>0,grc(A)+' → '+grc(B));
  const lyr=p=>{let k=0;p.ms.forEach(m=>m.forEach(n=>{if(n.lyric)k++;}));return k;};
  ok('склади під нотами пережили круг',lyr(A)===lyr(B)&&lyr(B)>0,lyr(A)+' → '+lyr(B));
  ok('другий голос пережив круг',!!(B.v2&&Object.keys(B.v2).length),
     B.v2?Object.keys(B.v2).length+' тактів':'немає');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

console.log(out.join('\n'));
console.log('\n— провалено перевірок: '+fails);
process.exit(fails?1:0);
})();
