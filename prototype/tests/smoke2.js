const fs=require('fs');
const {JSDOM,VirtualConsole}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const HTML=fs.readFileSync(PAGE,'utf8');

function boot(systemDark){
  const errs=[];
  const vc=new VirtualConsole();
  vc.on('jsdomError',e=>{const m=(e.detail?e.detail.message:e.message)||'';
    if(!/navigation to another Document/.test(m))errs.push(m);});
  const dom=new JSDOM(HTML,{runScripts:'dangerously',virtualConsole:vc,url:'https://example.test/',
    beforeParse(w){
      const listeners=[];
      w.matchMedia=q=>({media:q,
        get matches(){return /dark/.test(q)?!!systemDark:!systemDark;},
        addEventListener:(t,f)=>listeners.push(f),removeEventListener(){},
        addListener:f=>listeners.push(f)});
      w.__flipSystem=dark=>{systemDark=dark;listeners.forEach(f=>f({matches:dark}));};
      w.DecompressionStream=DecompressionStream;w.Blob=Blob;w.Response=Response;
      w.Element.prototype.scrollTo=function(){};
    }});
  const {window}=dom,doc=window.document;
  window.addEventListener('error',e=>errs.push('window.error: '+((e.error&&e.error.stack)||e.message)));
  window.Element.prototype.getBoundingClientRect=()=>({x:0,y:0,left:0,top:0,right:360,bottom:200,width:360,height:200});
  const api={window,doc,errs,
    $:s=>doc.querySelector(s),
    $$:s=>[...doc.querySelectorAll(s)],
    click(el){if(!el){errs.push('немає елемента для кліку');return false;}
      el.dispatchEvent(new window.MouseEvent('click',{bubbles:true,cancelable:true}));return true;},
    byText(sel,t){return [...doc.querySelectorAll(sel)].find(e=>e.textContent.trim().includes(t));},
    theme(){return doc.documentElement.getAttribute('data-theme');},
    st(){return window.eval('state');}};
  return api;
}

const out=[];let fails=0;
const ok=(n,c,x)=>{out.push((c?'  ok  ':'  FAIL')+'  '+n+(x?'  — '+x:''));if(!c)fails++;};
const sec=t=>out.push('\n'+t);

(async()=>{
/* ============ 1. «Для себе» більше не питає код класу ============ */
sec('Онбординг');
{
  const a=boot(true);
  a.click(a.byText('button','Продовжити з Google'));
  ok('після входу — крок з іменем',!!a.$('#uname'));
  ok('до вибору сценарію індикатор показує коротший шлях',a.$$('.steps i').length===3,a.$$('.steps i').length+'');
  a.click(a.byText('button','Далі'));
  ok('порожнє імʼя не пропускає',!!a.$('#uname'),'лишились на тому ж кроці');
  a.$('#uname').value='  Влад  ';
  a.$('#uname').dispatchEvent(new a.window.Event('input',{bubbles:true}));
  ok('літера в аватарі йде за іменем',a.$('.avatar').textContent==='В',a.$('.avatar').textContent);
  a.click(a.byText('button','Далі'));
  ok('після імені — вибір сценарію',!!a.byText('button','Для себе'));
  a.click(a.byText('button','Для себе'));
  ok('для «для себе» індикатор на 3 кроки',a.$$('.steps i').length===3,a.$$('.steps i').length+'');
  a.click(a.byText('button','Далі'));
  ok('коду класу не питає',!a.$('#ccode')&&!!a.$('[data-inst]'),
     a.$('.h-title')?a.$('.h-title').textContent:'?');
  a.click(a.$('[data-inst="Фортепіано"]'));
  a.click(a.byText('button','Готово'));
  ok('імʼя обрізане й збережене',a.st().user.name==='Влад','«'+a.st().user.name+'»');
  a.click(a.$('[data-tab="me"]'));
  ok('у профілі те саме імʼя',a.$('#pname').value==='Влад');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
  fails+=0;
}
{
  const a=boot(false);
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Оксана';
  a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Я вчусь у викладача'));
  a.click(a.byText('button','Далі'));
  ok('учня код класу питає',!!a.$('#ccode'));
  a.$('#ccode').value='pno-3a';
  a.click(a.byText('button','Приєднатися'));
  ok('після коду — інструменти',!!a.$('[data-inst]'));
}
{
  const a=boot(false);
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Ігор';
  a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Я викладаю'));
  a.click(a.byText('button','Далі'));
  const code=a.$('.codebox').textContent.trim();
  ok('код класу у форматі XXX-0X',/^[A-Z]{3}-[2-9][A-Z]$/.test(code),code);
  ok('у коді немає I, O, 0, 1',!/[IO01]/.test(code),code);
  const codes=Array.from({length:200},()=>a.window.makeCode('Гітара, початковий'));
  ok('200 згенерованих кодів усі валідні',codes.every(c=>/^[A-Z]{3}-[2-9][A-Z]$/.test(c)&&!/[IO01]/.test(c)));
  a.click(a.byText('button','Створити клас'));
  ok('після класу — інструменти',!!a.$('[data-inst]'));
}

/* ============ 2. Фото ============ */
sec('Фото');
{
  const a=boot(true);
  a.click(a.byText('button','Продовжити з Google'));
  ok('спершу аватар — знак питання',a.$('.avatar').tagName==='DIV'&&a.$('.avatar').textContent==='?',
     a.$('.avatar').textContent);
  a.$('#uname').value='Влад';
  a.$('#uname').dispatchEvent(new a.window.Event('input',{bubbles:true}));
  const inp=a.$('#photofile');
  a.click(a.byText('button','Додати фото'));
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==','base64');
  Object.defineProperty(inp,'files',{configurable:true,
    value:[new a.window.File([png],'ava.png',{type:'image/png'})]});
  inp.dispatchEvent(new a.window.Event('change',{bubbles:true}));
  await new Promise(r=>setTimeout(r,1900));
  ok('аватар став фото',a.$('.avatar')&&a.$('.avatar').tagName==='IMG',a.$('.avatar').tagName);
  ok('імʼя не загубилось при виборі фото',a.$('#uname').value==='Влад','«'+a.$('#uname').value+'»');
  ok('зʼявилась кнопка «Прибрати»',!!a.byText('button','Прибрати'));
  a.click(a.byText('button','Прибрати'));
  ok('фото прибрано',a.$('.avatar').tagName==='DIV');
  /* не-зображення відхиляється */
  Object.defineProperty(inp,'files',{configurable:true,
    value:[new a.window.File([Buffer.from('x')],'x.txt',{type:'text/plain'})]});
  inp.dispatchEvent(new a.window.Event('change',{bubbles:true}));
  await new Promise(r=>setTimeout(r,60));
  ok('не-зображення відхилено з підказкою',/зображення/.test(a.$('#toast').textContent),a.$('#toast').textContent);
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ============ 3. Тема ============ */
sec('Тема');
{
  const a=boot(true);
  ok('система темна → темна тема',a.theme()==='dark',a.theme());
  a.window.__flipSystem(false);
  ok('система перемкнулась на світлу → сторінка теж',a.theme()==='light',a.theme());
  /* доходимо до профілю */
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Влад';a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Для себе'));a.click(a.byText('button','Далі'));
  a.click(a.$('[data-inst="Гітара"]'));a.click(a.byText('button','Готово'));
  a.click(a.$('[data-tab="me"]'));
  ok('у профілі три варіанти теми',a.$$('[data-settheme]').length===3);
  a.click(a.$('[data-settheme="dark"]'));
  ok('ручна темна',a.theme()==='dark',a.theme());
  a.window.__flipSystem(true);a.window.__flipSystem(false);
  ok('системна зміна не збиває ручний вибір',a.theme()==='dark',a.theme());
  a.click(a.$('[data-settheme="light"]'));
  ok('ручна світла',a.theme()==='light',a.theme());
  a.click(a.$('[data-settheme="system"]'));
  ok('повернення до системної',a.theme()==='light',a.theme());
  a.window.__flipSystem(true);
  ok('і знову стежить за системою',a.theme()==='dark',a.theme());
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ============ 4. Налаштування в плеєрі ============ */
sec('Плеєр');
{
  const a=boot(true);
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Влад';a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Я вчусь у викладача'));a.click(a.byText('button','Далі'));
  a.$('#ccode').value='PNO-3A';a.click(a.byText('button','Приєднатися'));
  a.click(a.$('[data-inst="Труба in B♭"]'));a.click(a.byText('button','Готово'));

  a.click(a.$('[data-tab="me"]'));
  ok('у профілі більше немає розділу «Відтворення»',!/Відтворення/.test(a.$('#scr').textContent));
  a.click(a.$('[data-tab="home"]'));
  a.click(a.$('[data-open="s2"]'));
  ok('партитура відкрилась',a.$('#viewer').classList.contains('open'));
  ok('на кілька партій відкривається партитурою',/партитура/.test(a.$('#viewer .vtop p').textContent),
     a.$('#viewer .vtop p').textContent);
  a.click(a.$('[data-act="mode"][data-v="part"]'));
  ok('у режимі партії обрана партія під мій інструмент',/Труба/.test(a.$('#viewer .vtop p').textContent),
     a.$('#viewer .vtop p').textContent);
  ok('панелі за замовчуванням немає',!a.$('.sheetpanel'));
  a.click(a.$('[data-act="vset"]'));
  ok('шестерня відкриває панель',!!a.$('.sheetpanel'));
  ok('у панелі 5 перемикачів',a.$$('.sheetpanel .sw').length===5,a.$$('.sheetpanel .sw').length+'');
  ok('панель пояснює стрій цієї партії',/на 2 півтони нижче/.test(a.$('.sheetpanel').textContent));

  const nums=()=>(a.$('#viewer').innerHTML.match(/font-size="9\.5"/g)||[]).length;
  const before=nums();
  a.click(a.$('.sheetpanel [data-act="barnum"]'));
  ok('номери тактів вимикаються',nums()===0&&before>0,before+' → '+nums());
  a.click(a.$('.sheetpanel [data-act="barnum"]'));
  ok('і вмикаються назад',nums()===before,nums()+'');

  a.click(a.$('.sheetpanel [data-act="metro"]'));
  ok('метроном перемкнувся',a.st().metro===true);
  a.click(a.$('.sheetpanel [data-act="tosound"]'));
  ok('стрій перемкнувся',a.st().toSounding===false);
  a.click(a.$('.sheetpanel [data-act="awake"]'));
  ok('екран перемкнувся',a.st().keepAwake===false);
  a.click(a.$('.sheetpanel'));
  ok('клік по підкладці закриває панель',!a.$('.sheetpanel'));
  a.click(a.$('[data-act="vset"]'));
  a.click(a.$('.sheetpanel [data-act="vset"]'));
  ok('хрестик теж закриває',!a.$('.sheetpanel'));
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ============ 5. Регресія: ноти, звук, імпорт ============ */
sec('Регресія');
{
  const a=boot(false);
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Влад';a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Для себе'));a.click(a.byText('button','Далі'));
  a.click(a.$('[data-inst="Фортепіано"]'));a.click(a.byText('button','Готово'));
  let heads=0,opened=0,tried=0;
  for(const id of ['s1','s2','s3','s11','s4','s5','s10']){
    const c=a.$('[data-open="'+id+'"]');if(!c)continue;
    tried++;
    a.click(c);
    if(a.$('#viewer').classList.contains('open'))opened++;
    heads+=a.$$('#viewer .nh').length;
    a.$$('.partbar [data-part]').forEach((b,i)=>{if(i>0)a.click(b);});
    a.click(a.$('[data-act="close"]'));
    a.click(a.$('[data-tab="home"]'));
  }
  ok('усі публічні партитури відкриваються',opened===tried&&tried===7,opened+'/'+tried);
  ok('ноти малюються',heads>100,heads+' голівок');

  const mxl=new Uint8Array(fs.readFileSync(path.join(__dirname,'fixtures','sample.mxl')));
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
  ok('експорт працює',!!saved&&/\.zip$/.test(saved.filename),saved&&saved.filename);
  a.click(a.$('[data-act="close"]'));

  a.click(a.$('[data-tab="home"]'));
  a.click(a.byText('button','Відкрити редактор'));
  const sv=a.$('#edstaff svg');
  [120,130,140].forEach(y=>sv.dispatchEvent(new a.window.MouseEvent('click',{bubbles:true,clientY:y,clientX:100})));
  ok('редактор приймає ноти',a.$$('#edstaff .nh').length===3,a.$$('#edstaff .nh').length+'');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

/* ============ 6. Бенд: партії, ударні, стиснені паузи ============ */
sec('Бенд і витягання партій');
{
  const a=boot(true);
  a.click(a.byText('button','Продовжити з Google'));
  a.$('#uname').value='Влад';a.$('#uname').dispatchEvent(new a.window.Event('input',{bubbles:true}));
  a.click(a.byText('button','Далі'));
  a.click(a.byText('button','Для себе'));a.click(a.byText('button','Далі'));
  a.click(a.$('[data-inst="Труба in B♭"]'));a.click(a.byText('button','Готово'));
  a.click(a.$('[data-open="s12"]'));
  ok('бенд відкрився',a.$('#viewer').classList.contains('open'));
  ok('бенд відкрився партитурою, а не партією',a.st().mode==='score'
     &&/партитура, 7/.test(a.$('#viewer .vtop p').textContent),
     a.$('#viewer .vtop p').textContent);
  ok('у партитурі сім станів',(a.$('#viewer .system svg').innerHTML.match(/stroke-width="1" opacity=".85"/g)||[]).length===35,
     ((a.$('#viewer .system svg').innerHTML.match(/stroke-width="1" opacity=".85"/g)||[]).length/5)+' станів');
  a.click(a.$('[data-act="mode"][data-v="part"]'));
  ok('партій сім',a.$$('.partbar [data-part]').length===7,a.$$('.partbar [data-part]').length+'');
  ok('одразу обрана партія під мій інструмент',/Труба 1/.test(a.$('#viewer .vtop p').textContent),
     a.$('#viewer .vtop p').textContent);

  a.click(a.$('[data-act="parts"]'));
  ok('панель партій відкрилась',!!a.$('.plist'));
  ok('у списку сім рядків',a.$$('.prow').length===7,a.$$('.prow').length+'');
  ok('мої партії позначені',a.$$('.pn .mine').length===2,a.$$('.pn .mine').length+' (дві труби)');
  const rows=a.$$('.prow .ps').map(e=>e.textContent);
  ok('тромбон підписаний басовим ключем',/басовий/.test(rows[3]),rows[3]);
  ok('саксофон показує стрій',/9 півтони/.test(rows[2]),rows[2]);
  ok('ударні — ударний стан',/ударний стан/.test(rows[6]),rows[6]);
  ok('тацети пораховані',/тацет 4/.test(rows[1]),rows[1]);

  a.click(a.$$('.prow .pmain')[1]);
  ok('вибір партії зі списку закриває панель',!a.$('.plist'));
  ok('показується друга труба',/Труба 2/.test(a.$('#viewer .vtop p').textContent));
  ok('чотири порожні такти згорнулись в одну паузу',a.$$('#viewer .mmr').length===1,
     a.$$('#viewer .mmr').length+'');
  ok('над паузою стоїть 4',/>4</.test(a.$('#viewer .system').innerHTML));
  const barsCompressed=a.$$('#viewer .system').length;

  a.click(a.$('[data-act="vset"]'));
  a.click(a.$('.sheetpanel [data-act="compress"]'));
  ok('вимкнення стискання прибирає багатотактову паузу',a.$$('#viewer .mmr').length===0);
  ok('і додає системи',a.$$('#viewer .system').length>barsCompressed,
     barsCompressed+' → '+a.$$('#viewer .system').length);
  a.click(a.$('.sheetpanel [data-act="compress"]'));
  a.click(a.$('[data-act="vset"]'));

  a.click(a.$('[data-act="parts"]'));
  a.click(a.$$('.prow .pmain')[6]);
  ok('ударні малюються хрестиками',(a.$('#viewer').innerHTML.match(/class="nh" data-p="\d+" data-e="\d+" d="M/g)||[]).length>0);
  ok('ударний ключ — дві смуги',(a.$('#viewer .system').innerHTML.match(/<rect x="24"/g)||[]).length===1,
     'ключ намальовано');
  ok('у ударних немає ключових знаків',!/♭|♯/.test(a.$('#viewer .system').innerHTML));

  /* --- витягання --- */
  const saves=[];
  a.window.claude={use:async n=>n==='downloads'?{save:async r=>{saves.push(r);return{status:'saved'};}}:null};
  a.click(a.$('[data-act="parts"]'));
  a.click(a.$$('.prow [data-pull]')[2]);          // альт-саксофон
  await new Promise(r=>setTimeout(r,200));
  ok('одна партія вивантажилась',saves.length===1&&/саксофон/i.test(saves[0].filename),
     saves[0]&&saves[0].filename);

  const bytes=new Uint8Array(await saves[0].data.arrayBuffer());
  const xml=await a.window.musicxmlFromBytes(bytes);
  const back=a.window.fromMusicXML(xml);
  ok('у витягнутому файлі рівно одна партія',back.parts.length===1,back.parts.length+'');
  ok('назва каже, що це за партія',/Альт-саксофон/.test(back.title),back.title);
  ok('стрій збережено',back.parts[0].ts===9,'ts='+back.parts[0].ts);
  ok('тональність партії збережена',back.parts[0].fifths===1,'fifths='+back.parts[0].fifths);
  ok('тацети на місці',back.parts[0].ms.filter(m=>m.every(n=>!n.p.length)).length===4,
     back.parts[0].ms.filter(m=>m.every(n=>!n.p.length)).length+'');

  saves.length=0;
  a.click(a.$('[data-act="pullall"]'));
  await new Promise(r=>setTimeout(r,300));
  ok('архів усіх партій зібрався',saves.length===1&&/партії\.zip$/.test(saves[0].filename),
     saves[0]&&saves[0].filename);
  const entries=await a.window.unzip(new Uint8Array(await saves[0].data.arrayBuffer()));
  ok('у архіві сім файлів',entries.length===7,entries.map(e=>e.name).join(', '));
  const dec=new TextDecoder();
  let allOk=true,drums=null;
  for(const e of entries){
    const sc=a.window.fromMusicXML(dec.decode(e.data));
    if(sc.parts.length!==1)allOk=false;
    if(/Ударні/.test(e.name))drums=sc.parts[0];
  }
  ok('кожен файл — одна самостійна партія',allOk);
  ok('ударні пережили круг через MusicXML',
     !!drums&&drums.clef==='perc'&&drums.ms.some(m=>m.some(n=>n.p.some(x=>x.u))),
     drums?drums.clef:'немає');
  ok('помилок немає',a.errs.length===0,a.errs[0]||'');
}

console.log(out.join('\n'));
console.log('\n— провалено перевірок: '+fails);
process.exit(fails?1:0);
})();
