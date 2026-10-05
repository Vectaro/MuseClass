const fs=require('fs');
const {JSDOM}=require('jsdom');
const path=require('path');
const ROOT=path.resolve(__dirname,'..');
const PAGE=path.join(ROOT,'index.html');
const dom=new JSDOM(fs.readFileSync(PAGE,'utf8'),
  {runScripts:'dangerously',url:'https://example.test/',
   beforeParse(w){w.matchMedia=q=>({media:q,matches:false,addEventListener(){},addListener(){}});}});
const {window}=dom,doc=window.document;
window.Element.prototype.getBoundingClientRect=()=>({x:0,y:0,left:0,top:0,right:360,bottom:200,width:360,height:200});
const $=s=>doc.querySelector(s),$$=s=>[...doc.querySelectorAll(s)];
const click=el=>el&&el.dispatchEvent(new window.MouseEvent('click',{bubbles:true}));
const byText=(s,t)=>$$(s).find(e=>e.textContent.trim().includes(t));

click(byText('button','Продовжити з Google'));
$('#uname').value='Влад';$('#uname').dispatchEvent(new window.Event('input',{bubbles:true}));
click(byText('button','Далі'));
click(byText('button','Для себе'));click(byText('button','Далі'));
click($('[data-inst="Фортепіано"]'));click(byText('button','Готово'));
click($('[data-open="s12"]'));

function dump(partIdx,file,label){
  click($('[data-act="parts"]'));
  click($$('.prow .pmain')[partIdx]);
  const systems=$$('#viewer .system svg');
  const W=Math.max(...systems.map(s=>+s.getAttribute('viewBox').split(' ')[2]));
  let H=0,inner='';
  systems.forEach(s=>{
    const vb=s.getAttribute('viewBox').split(' ').map(Number);
    inner+='<g transform="translate(0,'+H+')">'+s.innerHTML+'</g>';H+=vb[3];
  });
  fs.writeFileSync(file,
    '<svg xmlns="http://www.w3.org/2000/svg" width="'+W+'" height="'+(H+24)+'" viewBox="0 0 '+W+' '+(H+24)+'">'
    +'<rect width="100%" height="100%" fill="#FAF8F3"/>'
    +'<text x="8" y="16" font-family="sans-serif" font-size="11" fill="#8A8A92">'+label+'</text>'
    +'<g transform="translate(0,20)">'+inner+'</g></svg>');
  console.log(label,'→',systems.length,'систем');
}
dump(0,'b_tr1.svg','Труба 1 in B♭ — партитура без пауз');
dump(1,'b_tr2.svg','Труба 2 in B♭ — 4 такти тацету стиснуті в одну паузу');
dump(3,'b_tbn.svg','Тромбон — басовий ключ, B♭ мажор');
dump(4,'b_gtr.svg','Гітара — акорди');
dump(6,'b_drm.svg','Ударні — ударний ключ і хрестики');
