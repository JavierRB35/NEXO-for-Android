import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const path = 'app/src/main/assets/books.js';
const dom = new JSDOM('<main><div id="panelContent"></div><div id="modalBody"></div></main>', {
  url: 'https://nexo.local/',
  runScripts: 'outside-only',
  pretendToBeVisual: true
});
const {window} = dom, doc = window.document;
let rows = [
  {id:1,title:'Cien años de soledad',author:'Gabriel García Márquez',status:'finished',progress:100,finishDate:'2024-01-21',abandonedDate:'',summary:'Un clásico'},
  {id:2,title:'La sombra del viento',author:'Carlos Ruiz Zafón',status:'want',progress:0,finishDate:'',abandonedDate:'',summary:''},
  {id:3,title:'Breve historia del tiempo',author:'Stephen Hawking',status:'reading',progress:45,finishDate:'',abandonedDate:'',summary:''}
];
let voice = null;
window.NexoNative = {
  getBooks: () => JSON.stringify(rows),
  getBook: id => JSON.stringify(rows.find(x=>x.id===id)||{}),
  saveBook: raw => {
    const b=JSON.parse(raw);
    if (!b.title || !b.author) return JSON.stringify({ok:false,error:'Faltan campos'});
    if (!b.id) b.id=4;
    rows=rows.filter(x=>x.id!==b.id).concat(b);
    return JSON.stringify({ok:true,id:b.id});
  },
  deleteBook: id => {rows=rows.filter(x=>x.id!==id);return true;}
};
window.parseNative = (raw,fallback) => {try{return JSON.parse(raw)}catch{return fallback}};
window.esc = s => String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
window.escAttr = window.esc;
window.uiIcon = ()=>'';
window.toast = ()=>{};
window.openModal = (title,html)=>{doc.querySelector('#modalBody').innerHTML=html;};
window.closeModal = ()=>{doc.querySelector('#modalBody').innerHTML='';return true;};
window.confirmGeneric = (title,body,fn)=>fn();
window.startManagedDictation = (target,opts)=>{voice={target,opts}};
window.requestAnimationFrame = fn => {fn();return 1};
window.eval(fs.readFileSync(path,'utf8'));

const panel=()=>doc.querySelector('#panelContent').textContent;
const setSearch=value=>{
  const el=doc.querySelector('#booksSearchInput');
  el.value=value;
  el.dispatchEvent(new window.Event('input',{bubbles:true}));
};
window.NexoBooks.render();
assert(panel().includes('Cien años de soledad'));
assert(!panel().includes('La sombra del viento'), 'sin búsqueda, se respeta la pestaña leídos');

setSearch('SOM');
assert(panel().includes('La sombra del viento'), 'busca también en la lista pendiente');
assert(!panel().includes('Cien años de soledad'), 'filtra por fragmentos de texto');
setSearch('garcia');
assert(panel().includes('Cien años de soledad'), 'busca sin distinguir acentos');
setSearch('HAW');
assert(panel().includes('Breve historia del tiempo'), 'busca por autor');

setSearch('');
doc.querySelector('[data-books-tab="want"]').click();
assert(panel().includes('La sombra del viento'));
doc.querySelector('[data-book-start="2"]').click();
assert(rows.find(x=>x.id===2).status==='reading');
assert(panel().includes('La sombra del viento'), 'al empezar, se muestra en Leídos y empezados');

doc.querySelector('[data-book-edit="3"]').click();
const status=doc.querySelector('#bookStatus');
status.value='abandoned';status.dispatchEvent(new window.Event('change',{bubbles:true}));
const slider=doc.querySelector('#bookProgress');
slider.value='42';slider.dispatchEvent(new window.Event('input',{bubbles:true}));
const date=doc.querySelector('#bookAbandonedDate');
date.value='2026-10-15';
doc.querySelector('#bookSummary').value='Resumen de prueba';
doc.querySelector('#bookDictate').click();
assert(voice.target==='book-summary', 'dictado dirigido solo al resumen');
window.NexoBooks.applyVoiceText('Resumen dictado y editable');
assert(doc.querySelector('#bookSummary').value==='Resumen dictado y editable');
doc.querySelector('#bookSave').click();
const changed=rows.find(x=>x.id===3);
assert(changed.status==='abandoned'&&changed.progress===42&&changed.abandonedDate==='2026-10-15');
assert(changed.summary==='Resumen dictado y editable');

console.log('OK: Libros - búsqueda incremental en ambas listas, acentos, edición, progreso, abandono y dictado');
