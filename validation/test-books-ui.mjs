import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const path='app/src/main/assets/books.js';
const dom=new JSDOM('<main><div id="panelContent"></div><div id="modalBody"></div></main>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const {window}=dom,doc=window.document;
let rows=[
  {id:1,title:'Cien años de soledad',author:'Gabriel García Márquez',status:'finished',progress:100,finishDate:'2024-01-21',startDate:'2023-11-01',abandonedDate:'',summary:'Un clásico\nCon notas completas.',rating:3,favorite:false,updatedAt:50},
  {id:2,title:'La sombra del viento',author:'Carlos Ruiz Zafón',status:'want',progress:0,startDate:'',finishDate:'',abandonedDate:'',summary:'',rating:0,favorite:false,updatedAt:60},
  {id:3,title:'Breve historia del tiempo',author:'Stephen Hawking',status:'reading',progress:45,startDate:'2024-08-01',finishDate:'',abandonedDate:'',summary:'',rating:0,favorite:false,updatedAt:70},
  {id:4,title:'El extranjero',author:'Albert Camus',status:'abandoned',progress:20,startDate:'2024-12-01',finishDate:'',abandonedDate:'2025-04-07',summary:'Quizá retomar',rating:1,favorite:false,updatedAt:80},
  {id:5,title:'El infinito en un junco',author:'Irene Vallejo',status:'finished',progress:100,startDate:'2026-09-01',finishDate:'2026-10-10',abandonedDate:'',summary:'',rating:5,favorite:true,updatedAt:1},
  {id:6,title:'Un libro sin fecha',author:'Sin autor conocido',status:'finished',progress:100,startDate:'',finishDate:'',abandonedDate:'',summary:'',rating:0,favorite:false,updatedAt:300}
];
let voice=null,confirmCount=0, lastError='',clock=1000;
window.NexoNative={
  getBooks:()=>JSON.stringify(rows),
  getBook:id=>JSON.stringify(rows.find(x=>x.id===id)||{}),
  saveBook:raw=>{
    const b=JSON.parse(raw);
    if(!b.title||!b.author)return JSON.stringify({ok:false,error:'Faltan campos'});
    if(!Number.isInteger(b.rating)||b.rating<0||b.rating>5)return JSON.stringify({ok:false,error:'Estrellas incorrectas'});
    const norm=s=>String(s).normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase().replace(/\s+/g,' ');
    if(rows.some(x=>x.id!==b.id&&norm(x.title)===norm(b.title)&&norm(x.author)===norm(b.author)))
      return JSON.stringify({ok:false,error:'Este libro y autor ya están en tu biblioteca.'});
    if(!b.id)b.id=7;
    if(b.status==='want'){b.progress=0;b.startDate='';b.finishDate='';b.abandonedDate='';b.summary='';}
    b.updatedAt=++clock;
    rows=rows.filter(x=>x.id!==b.id).concat(b);
    return JSON.stringify({ok:true,id:b.id});
  },
  deleteBook:id=>{rows=rows.filter(x=>x.id!==id);return true;}
};
window.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
window.esc=s=>String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
window.escAttr=window.esc;
window.uiIcon=name=>`<svg data-icon="${name}"></svg>`;
window.toast=s=>{lastError=s;};
window.openModal=(title,html)=>{doc.querySelector('#modalBody').innerHTML=html;};
window.closeModal=()=>{doc.querySelector('#modalBody').innerHTML='';return true;};
window.confirmGeneric=(title,body,fn)=>{confirmCount++;fn();};
window.startManagedDictation=(target,opts)=>{voice={target,opts};};
window.requestAnimationFrame=fn=>{fn();return 1;};
window.eval(fs.readFileSync(path,'utf8'));

const panel=()=>doc.querySelector('#panelContent').textContent;
const bookIds=()=>[...doc.querySelectorAll('[data-book-open]')].map(x=>Number(x.dataset.bookOpen));
const setSearch=value=>{
  const el=doc.querySelector('#booksSearchInput');
  el.value=value;el.dispatchEvent(new window.Event('input',{bubbles:true}));
};
const setFilter=value=>doc.querySelector('[data-books-filter="'+value+'"]').click();
const open=id=>doc.querySelector('[data-book-open="'+id+'"]').click();

window.NexoBooks.render();
assert.equal(doc.querySelectorAll('[data-books-filter]').length,6,'Cinco filtros previos y Favoritos');
assert.equal(doc.querySelectorAll('[data-book-open]').length,6);
assert.equal(doc.querySelectorAll('.book-card-actions,[data-book-edit],[data-book-start],[data-book-delete]').length,0,'Sin botones en tarjetas');
assert.equal(doc.querySelectorAll('.book-excerpt').length,0,'Resumen fuera del listado');
for(const s of ['want','reading','finished','abandoned']){
  assert(doc.querySelector('.book-card-symbol--'+s),'Icono '+s);
  assert(doc.querySelector('[data-books-filter="'+s+'"]'),'Filtro '+s);
}
assert.equal(doc.querySelector('[data-book-count="all"]').textContent,'6');
assert.equal(doc.querySelector('[data-book-count="finished"]').textContent,'3');
assert.equal(doc.querySelector('[data-book-count="favorites"]').textContent,'1');
assert(doc.querySelector('#booksStatsContent').textContent.includes('Terminados en total'));
assert(doc.querySelector('#booksStatsContent').textContent.includes('2026'),'Estadística anual');
assert(doc.querySelector('[data-book-open="5"] .book-card-heart'),'Corazón visible en tarjeta favorita');
assert(!doc.querySelector('[data-book-open="1"] .book-card-heart'),'Favorito independiente de estrellas');
assert(doc.querySelector('[data-book-open="5"] .book-card-date').textContent.includes('10/10/2026'));
assert(doc.querySelector('[data-book-open="1"]').getAttribute('aria-label').includes('Terminado'));

setFilter('finished');
assert.deepEqual(bookIds(),[5,1,6],'Fecha de finalización descendente; sin fecha al final');
open(1);
assert(doc.querySelector('.book-detail'),'La ficha se abre sin entrar al editor');
assert(doc.querySelector('#modalBody').textContent.includes('Con notas completas.'));
assert(doc.querySelector('#modalBody').textContent.includes('21/01/2024'));
assert(doc.querySelector('#modalBody').textContent.includes('01/11/2023'),'Inicio visible en detalle');
assert(doc.querySelectorAll('[data-book-detail-rating]').length===5,'Cinco estrellas en la ficha');
assert(doc.querySelector('#bookDetailFavorite'),'Corazón en ficha');
assert(doc.querySelector('#bookDetailEdit')&&doc.querySelector('#bookDetailDelete'));
assert.equal(doc.querySelector('#bookTitle'),null,'Detalle no es editor');

// Cambiar valoración y favorito sin editar la ficha, de manera independiente.
doc.querySelector('[data-book-detail-rating][data-rating="4"]').click();
assert.equal(rows.find(x=>x.id===1).rating,4);
assert.equal(rows.find(x=>x.id===1).favorite,false,'Puntuar 4 no marca favorito');
doc.querySelector('#bookDetailFavorite').click();
assert.equal(rows.find(x=>x.id===1).favorite,true);
assert.equal(rows.find(x=>x.id===1).rating,4,'Corazón no modifica las estrellas');
assert(doc.querySelector('#bookDetailFavorite').classList.contains('active'));
assert.equal(doc.querySelector('[data-book-count="favorites"]').textContent,'2');
assert.deepEqual(bookIds(),[5,1,6],'Editar puntuación/favorito no cambia la fecha de fin ni el orden');
doc.querySelector('[data-book-detail-rating][data-rating="4"]').click();
assert.equal(rows.find(x=>x.id===1).rating,0,'Pulsar la misma valoración la borra');
doc.querySelector('[data-book-detail-rating][data-rating="5"]').click();
assert.equal(rows.find(x=>x.id===1).rating,5,'La valoración definitiva se elige en la ficha');
assert.equal(doc.querySelector('#bookDetailFavorite').querySelector('span'),null,'Corazón sin texto ni check');
assert.equal(doc.querySelector('#bookDetailFavorite').querySelector('svg').getAttribute('data-icon'),'heart');
doc.querySelector('#bookDetailEdit').click();
assert.equal(doc.querySelectorAll('#bookStatus option').length,4);
assert.equal(doc.querySelector('#bookStatus').value,'finished');
assert.equal(doc.querySelector('#bookFavorite'),null,'No checkbox ni corazón en formulario de edición');
assert.equal(doc.querySelectorAll('[data-book-edit-rating]').length,0,'Las estrellas no están en edición');
assert.equal(doc.querySelector('#bookStartDate').value,'2023-11-01');
doc.querySelector('#bookStatus').value='reading';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert.equal(doc.querySelector('#bookProgress').value,'99');
doc.querySelector('#bookStatus').value='abandoned';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
const slider=doc.querySelector('#bookProgress');
slider.value='42';slider.dispatchEvent(new window.Event('input',{bubbles:true}));
doc.querySelector('#bookAbandonedDate').value='2026-10-15';
doc.querySelector('#bookSummary').value='Resumen de prueba';
doc.querySelector('#bookDictate').click();
assert.equal(voice.target,'book-summary');
window.NexoBooks.applyVoiceText('Resumen dictado y editable');
assert.equal(doc.querySelector('#bookSummary').value,'Resumen dictado y editable');
doc.querySelector('#bookSave').click();
const changed=rows.find(x=>x.id===1);
assert.equal(changed.status,'abandoned');assert.equal(changed.progress,42);
assert.equal(changed.abandonedDate,'2026-10-15');
assert.equal(changed.summary,'Resumen dictado y editable');
assert.equal(changed.favorite,true);assert.equal(changed.rating,5);
assert.equal(doc.querySelector('[data-books-filter="abandoned"]').getAttribute('aria-pressed'),'true');
assert(doc.querySelector('#bookDetailFavorite'),'Tras editar se abre de nuevo la ficha');
assert.equal(doc.querySelectorAll('[data-book-detail-rating]').length,5,'La ficha conserva las cinco estrellas');

setFilter('all');
assert.equal(bookIds()[0],1,'La fecha de abandono recién introducida prevalece');
setFilter('favorites');
assert.deepEqual(bookIds(),[1,5],'Filtro favoritos por fecha real, sin depender de valoración');
setSearch('JUNCO');
assert.deepEqual(bookIds(),[5],'Buscador y favoritos combinados');
assert.equal(doc.querySelector('[data-book-count="favorites"]').textContent,'1','Contadores se adaptan a búsqueda');
setSearch('garcia');
assert.deepEqual(bookIds(),[1],'Busca sin tildes');
setSearch('HAW');
assert.deepEqual(bookIds(),[],'Busca en autores y respeta filtro de favoritos');
setFilter('all');
assert.deepEqual(bookIds(),[3],'Busca autor en todos los estados');
setSearch('SOM');
assert.deepEqual(bookIds(),[2]);
setSearch('');
setFilter('want');
open(2);
assert(doc.querySelector('#bookDetailStart'),'Empezar lectura dentro de la ficha pendiente');
doc.querySelector('#bookDetailStart').click();
assert.equal(rows.find(x=>x.id===2).status,'reading');
assert.match(rows.find(x=>x.id===2).startDate,/^\d{4}-\d{2}-\d{2}$/,'Registra inicio al comenzar');
assert.equal(doc.querySelector('[data-books-filter="reading"]').getAttribute('aria-pressed'),'true');
open(2);
doc.querySelector('#bookDetailEdit').click();
doc.querySelector('#bookStatus').value='want';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert(doc.querySelector('#bookReadingFields').classList.contains('hidden'));
doc.querySelector('#bookStatus').value='finished';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert.equal(doc.querySelector('#bookProgress').value,'100');
doc.querySelector('#bookSave').click();
assert.equal(rows.find(x=>x.id===2).status,'finished');
assert.equal(doc.querySelector('[data-books-filter="finished"]').getAttribute('aria-pressed'),'true');
open(2);doc.querySelector('#bookDetailDelete').click();
assert(!rows.find(x=>x.id===2),'Borrado confirmado desde la ficha');
assert(confirmCount>=1);
window.NexoBooks.openEditor();
assert.equal(doc.querySelector('#bookFavorite'),null,'Alta sin checkbox de favorito');
assert.equal(doc.querySelectorAll('[data-book-edit-rating]').length,0,'Alta sin estrellas');
assert.equal(doc.querySelector('#bookStatus').value,'finished');
assert.equal(doc.querySelectorAll('#bookStatus option').length,4);
doc.querySelector('#bookStatus').value='want';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
doc.querySelector('#bookTitle').value='Nuevo pendiente';
doc.querySelector('#bookAuthor').value='Autor de prueba';
doc.querySelector('#bookSave').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').status,'want');
assert(panel().includes('Nuevo pendiente'));
assert.equal(doc.querySelector('[data-books-filter="want"]').getAttribute('aria-pressed'),'true');
assert(doc.querySelector('#bookDetailFavorite'),'Guardar un libro nuevo abre su ficha directamente');
assert(!doc.querySelector('#bookFavorite'),'En la ficha nueva tampoco hay checkbox');
assert.equal(doc.querySelectorAll('[data-book-detail-rating]').length,5);
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').rating,0);
doc.querySelector('#bookDetailFavorite').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').favorite,true,'Un toque marca favorito y lo guarda');
doc.querySelector('[data-book-detail-rating][data-rating="3"]').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').rating,3,'Tres estrellas se guardan al tocar');
doc.querySelector('#bookDetailFavorite').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').favorite,false,'Segundo toque desmarca favorito');
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').rating,3,'Desmarcar el corazón no toca la valoración');

// Duplicados, sin perder el formulario y sin datos borrados.
window.NexoBooks.openEditor();
doc.querySelector('#bookTitle').value='  CIEN  AÑOS DE SOLEDAD  ';
doc.querySelector('#bookAuthor').value='Gabriel Garcia Marquez';
doc.querySelector('#bookSave').click();
assert(doc.querySelector('#bookError').textContent.includes('ya están'));
assert.equal(rows.length,6,'El duplicado no se añade');

console.log('OK: Libros - 6 filtros con contadores, orden por fecha, estrellas y corazón independientes, estadísticas, dictado, migración UI y duplicados');
