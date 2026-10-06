// Експорт демо-партитур прототипу в MusicXML для тестових даних dev-сервера.
// Бере ноти й toMusicXML() прямо з prototype/index.html, тож файли такі самі,
// як дає кнопка «Експорт» у прототипі.
//
//   cd prototype && npm install          # один раз, ставить jsdom
//   node ../server/dev/export-demos.js   # пише server/dev/seed/*.musicxml
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const { JSDOM } = require(path.join(ROOT, 'prototype', 'node_modules', 'jsdom'));

// id у прототипі → ім'я файлу
const DEMOS = {
  s12: 'band-march',   // бенд, 7 партій
  s2: 'shchedryk',     // 3 партії
  s1: 'oda',           // 2 партії
  s13: 'etude',        // 1 партія, уся нотація
};

const dom = new JSDOM(fs.readFileSync(path.join(ROOT, 'prototype', 'index.html'), 'utf8'), {
  runScripts: 'dangerously', url: 'https://example.test/',
  beforeParse(w) { w.matchMedia = q => ({ media: q, matches: false, addEventListener() {}, addListener() {} }); },
});
const w = dom.window;
const out = path.join(__dirname, 'seed');
fs.mkdirSync(out, { recursive: true });

for (const [id, name] of Object.entries(DEMOS)) {
  const sc = w.eval('SCORES').find(s => s.id === id);
  if (!sc) throw new Error('немає демо ' + id + ' у прототипі');
  const parts = sc.parts.filter(p => p.ms);
  const xml = w.eval('toMusicXML')({ title: sc.title, composer: sc.author, arranger: sc.arr, parts });
  fs.writeFileSync(path.join(out, name + '.musicxml'), xml);
  console.log(name + '.musicxml  ' + parts.length + ' партій  ' + sc.title);
}
