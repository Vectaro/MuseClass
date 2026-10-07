// Проходить прототип у вікні 400 px у світлій і темній темі й знімає кожен екран.
// NODE_PATH=<тека з node_modules/puppeteer-core> node shoot-proto.js <outDir>
// Edge — звичайний Microsoft Edge на Windows.
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');
const OUT = process.argv[2];
const PAGE = 'file:///' + path.resolve(__dirname, '..', '..', '..', 'prototype', 'index.html').split(path.sep).join('/');
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

// кожен крок: [назва, функція в сторінці] — функція виставляє стан і малює
const STEPS = [
  ['01-onb-auth', () => { state.onb = 'auth'; renderOnb(); }],
  ['02-onb-email', () => { state.onb = 'auth'; renderOnb(); document.querySelector('[data-act=email]').click(); }],
  ['03-onb-name', () => { closeDialog(); state.onbName = 'Тарас'; state.onb = 'name'; renderOnb(); }],
  ['04-onb-role', () => { state.onbRole = 'student'; state.onb = 'role'; renderOnb(); }],
  ['05-onb-code', () => { state.onbRole = 'student'; state.onb = 'code'; renderOnb(); }],
  ['06-onb-code-err', () => { document.querySelector('#ccode').value = 'XYZ-9K'; document.querySelector('[data-act=join]').click(); }],
  ['07-onb-class', () => { state.onbRole = 'teacher'; state.onb = 'class'; renderOnb(); }],
  ['08-onb-inst', () => { state.onbRole = 'student'; state.onbInst = ['Фортепіано']; state.onb = 'inst'; renderOnb(); }],
  ['10-home', () => {
    state.user.name = 'Тарас'; state.user.instruments = ['Фортепіано']; state.user.classes = ['c1'];
    state.tab = 'home'; state.query = ''; state.filter = 'Усі'; render(); scr.scrollTop = 0;
  }],
  ['11-home-scrolled', () => { scr.scrollTop = 520; }],
  ['12-home-filter', () => { state.filter = 'Народна'; render(); scr.scrollTop = 0; }],
  ['13-home-search-empty', () => { state.filter = 'Усі'; state.query = 'zzz'; render(); }],
  ['20-lib-student', () => { state.query = ''; state.tab = 'lib'; render(); scr.scrollTop = 0; }],
  ['21-lib-student-scrolled', () => { scr.scrollTop = 600; }],
  ['22-lib-noclass', () => { state.user.classes = []; render(); scr.scrollTop = 9999; }],
  ['23-lib-teacher', () => { state.user.classes = []; state.user.teaching = ['c1']; render(); scr.scrollTop = 0; }],
  ['30-me', () => { state.user.teaching = []; state.user.classes = ['c1']; state.tab = 'me'; render(); scr.scrollTop = 0; }],
  ['31-me-scrolled', () => { scr.scrollTop = 9999; }],
  ['32-dlg-join', () => { document.querySelector('[data-act=joinprompt]').click(); }],
  ['33-dlg-mkclass', () => { closeDialog(); document.querySelector('[data-act=mkclass]').click(); }],
  ['34-toast', () => { closeDialog(); toast('Ти в класі «Фортепіано, 3 клас»'); }],
  ['40-viewer-score', () => { const t = document.querySelector('#toast'); if (t) t.classList.remove('show'); state.tab = 'home'; render(); openScore('s2'); state.mode = 'score'; renderViewer(); }],
  ['41-viewer-part', () => { state.mode = 'part'; state.part = 0; renderViewer(); }],
  ['42-viewer-vset', () => { state.vset = true; state.parts = false; renderViewer(); }],
  ['43-viewer-parts', () => { state.vset = false; state.parts = true; renderViewer(); }],
  ['45-viewer-single', () => { state.parts = false; closeScore(); openScore('s13'); }],
];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] });
  for (const theme of ['light', 'dark']) {
    const page = await browser.newPage();
    await page.setViewport({ width: 400, height: 860, deviceScaleFactor: 2 });
    await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: theme }]);
    await page.goto(PAGE, { waitUntil: 'networkidle0' });
    await page.evaluate(() => document.fonts.ready);
    for (const [name, fn] of STEPS) {
      try {
        await page.evaluate(fn);
      } catch (e) {
        console.log('крок', name, 'упав:', e.message);
      }
      await new Promise(r => setTimeout(r, 250));
      await page.screenshot({ path: path.join(OUT, name + '-' + theme + '.png') });
    }
    await page.close();
  }
  await browser.close();
  console.log('готово');
})().catch(e => { console.error(e); process.exit(1); });
