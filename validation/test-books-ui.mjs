import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const path='app/src/main/assets/books.js';
const dom=new JSDOM('<main><div id="panelContent"></div><div id="modalBody"></div></main>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const {window}=dom,doc=window.document;
let rows=[
  {id:1,title:'Cien años de soledad',author:'Gabriel García Márquez',status:'finished',progress:100,finishDate:'2024-01-21',abandonedDate:'',summary:'Un clásico\nCon notas completas.'},
  {id:2,title:'La sombra del viento',author:'Carlos Ruiz Zafón',status:'want',progress:0,finishDate:'',abandonedDate:'',summary:''},
  {id:3,title:'Breve historia del tiempo',author:'Stephen Hawking',status:'reading',progress:45,finishDate:'',abandonedDate:'',summary:''},
  {id:4,title:'El extranjero',author:'Albert Camus',status:'abandoned',progress:20,finishDate:'',abandonedDate:'2025-04-07',summary:'Quizá retomar'}
];
let voice=null, confirmCount=0;
window.NexoNative={
  getBooks:()=>JSON.stringify(rows),
  getBook:id=>JSON.stringify(rows.find(x=>x.id===id)||{}),
  saveBook:raw=>{
    const b=JSON.parse(raw);
    if(!b.title||!b.author)return JSON.stringify({ok:false,error:'Faltan campos'});
    if(!b.id)b.id=5;
    if(b.status==='want'){b.progress=0;b.finishDate='';b.abandonedDate='';b.summary='';}
    rows=rows.filter(x=>x.id!==b.id).concat(b);
    return JSON.stringify({ok:true,id:b.id});
  },
  deleteBook:id=>{rows=rows.filter(x=>x.id!==id);return true;}
};
window.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
window.esc=s=>String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
window.escAttr=window.esc;
window.uiIcon=name=>`<svg data-icon="${name}"></svg>`;
window.toast=()=>{};
window.openModal=(title,html)=>{doc.querySelector('#modalBody').innerHTML=html;};
window.closeModal=()=>{doc.querySelector('#modalBody').innerHTML='';return true;};
window.confirmGeneric=(title,body,fn)=>{confirmCount++;fn();};
window.startManagedDictation=(target,opts)=>{voice={target,opts};};
window.requestAnimationFrame=fn=>{fn();return 1;};
window.eval(fs.readFileSync(path,'utf8'));

const panel=()=>doc.querySelector('#panelContent').textContent;
const setSearch=value=>{
  const el=doc.querySelector('#booksSearchInput');
  el.value=value;el.dispatchEvent(new window.Event('input',{bubbles:true}));
};
const setFilter=value=>doc.querySelector('[data-books-filter="'+value+'"]').click();

window.NexoBooks.render();
assert.equal(doc.querySelectorAll('[data-book-open]').length,4,'Todos muestra los cuatro estados');
assert.equal(doc.querySelectorAll('[data-books-filter]').length,5,'Todos y cuatro estados');
assert.equal(doc.querySelectorAll('.book-card-main').length,4);
assert.equal(doc.querySelectorAll('.book-card-actions,[data-book-edit],[data-book-start],[data-book-delete]').length,0,
  'Las tarjetas no incluyen acciones, solo abren la ficha');
assert.equal(doc.querySelectorAll('.book-excerpt').length,0,'El resumen no aumenta la altura del listado');
for(const status of ['want','reading','finished','abandoned']){
  assert(doc.querySelector('.book-card-symbol--'+status),'icono diferenciado para '+status);
  assert(doc.querySelector('[data-books-filter="'+status+'"]'),'filtro individual '+status);
}
assert(doc.querySelector('[data-book-open="1"]').getAttribute('aria-label').includes('Terminado'),
  'Los iconos tienen descripción accesible');

setSearch('SOM');
assert(panel().includes('La sombra del viento'),'La búsqueda consulta pendientes');
assert(!panel().includes('Cien años de soledad'),'Búsqueda por fragmentos');
setSearch('garcia');
assert(panel().includes('Cien años de soledad'),'Busca sin distinguir acentos');
setSearch('HAW');
assert(panel().includes('Breve historia del tiempo'),'Busca por autor');
setFilter('want');
assert.equal(doc.querySelectorAll('[data-book-open]').length,0,'Búsqueda y filtro se combinan');
setSearch('');
assert.equal(doc.querySelectorAll('[data-book-open]').length,1,'Filtro pendiente');
assert.equal(doc.querySelector('[data-books-filter="want"]').getAttribute('aria-pressed'),'true');
setFilter('finished');
assert.equal(doc.querySelectorAll('[data-book-open]').length,1,'Filtro terminados');
setFilter('abandoned');
assert.equal(doc.querySelectorAll('[data-book-open]').length,1,'Filtro abandonados');
setFilter('reading');
assert.equal(doc.querySelectorAll('[data-book-open]').length,1,'Filtro en lectura');
setFilter('all');
assert.equal(doc.querySelectorAll('[data-book-open]').length,4);

doc.querySelector('[data-book-open="1"]').click();
assert(doc.querySelector('.book-detail'),'Se abre ficha completa en vez del editor');
assert(doc.querySelector('#modalBody').textContent.includes('Con notas completas.'),'Resumen completo solo en detalle');
assert(doc.querySelector('#modalBody').textContent.includes('21/01/2024'),'Fecha completa en detalle');
assert(doc.querySelector('#bookDetailEdit')&&doc.querySelector('#bookDetailDelete'),
  'Editar y Eliminar dentro de la ficha');
assert(!doc.querySelector('#bookTitle'),'Detalle es de solo lectura por defecto');
doc.querySelector('#bookDetailEdit').click();
assert.equal(doc.querySelectorAll('#bookStatus option').length,4,'Los cuatro estados son editables');
assert.equal(doc.querySelector('#bookStatus').value,'finished');
doc.querySelector('#bookStatus').value='reading';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert.equal(doc.querySelector('#bookProgress').value,'99','Al volver a lectura se ajusta 100%');
doc.querySelector('#bookStatus').value='abandoned';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
const slider=doc.querySelector('#bookProgress');
slider.value='42';slider.dispatchEvent(new window.Event('input',{bubbles:true}));
doc.querySelector('#bookAbandonedDate').value='2026-10-15';
doc.querySelector('#bookSummary').value='Resumen de prueba';
doc.querySelector('#bookDictate').click();
assert.equal(voice.target,'book-summary','Dictado solo al resumen');
window.NexoBooks.applyVoiceText('Resumen dictado y editable');
assert.equal(doc.querySelector('#bookSummary').value,'Resumen dictado y editable');
doc.querySelector('#bookSave').click();
let changed=rows.find(x=>x.id===1);
assert.equal(changed.status,'abandoned');
assert.equal(changed.progress,42);
assert.equal(changed.abandonedDate,'2026-10-15');
assert.equal(changed.summary,'Resumen dictado y editable');

setFilter('want');
doc.querySelector('[data-book-open="2"]').click();
assert(doc.querySelector('#bookDetailStart'),'Empezar lectura dentro de la ficha pendiente');
assert(!doc.querySelector('#bookStatus'),'En detalle no hay campos editables');
doc.querySelector('#bookDetailStart').click();
assert.equal(rows.find(x=>x.id===2).status,'reading');
assert(panel().includes('La sombra del viento'),'El inicio de lectura queda visible');
assert.equal(doc.querySelector('[data-books-filter="reading"]').getAttribute('aria-pressed'),'true',
  'Al empezar, cambia al filtro en lectura');

doc.querySelector('[data-book-open="2"]').click();
doc.querySelector('#bookDetailEdit').click();
doc.querySelector('#bookStatus').value='want';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert(doc.querySelector('#bookReadingFields').classList.contains('hidden'),
  'Quiero leer oculta progreso y resumen, sin impedir volver a otra categoría');
doc.querySelector('#bookStatus').value='finished';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert(!doc.querySelector('#bookReadingFields').classList.contains('hidden'));
assert.equal(doc.querySelector('#bookProgress').value,'100','Estado Terminado implica 100%');
doc.querySelector('#bookSave').click();
assert.equal(rows.find(x=>x.id===2).status,'finished');
assert(panel().includes('La sombra del viento'),'Cambiar de estado actualiza el filtro visible');
assert.equal(doc.querySelector('[data-books-filter="finished"]').getAttribute('aria-pressed'),'true');

doc.querySelector('[data-book-open="2"]').click();
doc.querySelector('#bookDetailDelete').click();
assert(!rows.find(x=>x.id===2),'Eliminar solo desde la ficha completa');
assert(confirmCount>=1,'Eliminación requiere confirmación');

window.NexoBooks.openEditor();
assert.equal(doc.querySelectorAll('#bookStatus option').length,4,'Nuevo libro permite todas las categorías');
assert.equal(doc.querySelector('#bookStatus').value,'finished','La nueva ficha propone el filtro seleccionado');
doc.querySelector('#bookStatus').value='want';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
doc.querySelector('#bookTitle').value='Nuevo pendiente';
doc.querySelector('#bookAuthor').value='Autor de prueba';
doc.querySelector('#bookSave').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').status,'want');
assert(panel().includes('Nuevo pendiente'),'Tras guardar se cambia al filtro correcto');

console.log('OK: Libros - tarjetas compactas, 4 iconos/estados, filtros, búsqueda, detalle, edición, progreso, dictado y eliminación');
