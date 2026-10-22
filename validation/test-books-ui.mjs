import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const path='app/src/main/assets/books.js';
const dom=new JSDOM('<main><div id="panelContent"></div><div id="modal" class="modal"><div class="modal-sheet"><div id="modalBody"></div></div></div></main>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const {window}=dom,doc=window.document;
let rows=[
  {id:1,title:'Cien años de soledad',author:'Gabriel García Márquez',status:'finished',progress:100,finishDate:'2024-01-21',startDate:'2023-11-01',abandonedDate:'',synopsis:'Una saga familiar\nCon recuerdos.',summary:'Un clásico\nCon notas completas.',rating:3,favorite:false,updatedAt:50},
  {id:2,title:'La sombra del viento',author:'Carlos Ruiz Zafón',status:'want',progress:0,startDate:'',finishDate:'',abandonedDate:'',synopsis:'Un misterio en Barcelona.',summary:'',rating:0,favorite:false,updatedAt:60},
  {id:3,title:'Breve historia del tiempo',author:'Stephen Hawking',status:'reading',progress:45,startDate:'2024-08-01',finishDate:'',abandonedDate:'',summary:'',rating:0,favorite:false,updatedAt:70},
  {id:4,title:'El extranjero',author:'Albert Camus',status:'abandoned',progress:20,startDate:'2024-12-01',finishDate:'',abandonedDate:'2025-04-07',summary:'Quizá retomar',rating:1,favorite:false,updatedAt:80},
  {id:5,title:'El infinito en un junco',author:'Irene Vallejo',status:'finished',progress:100,startDate:'2026-09-01',finishDate:'2026-10-10',abandonedDate:'',summary:'',rating:5,favorite:true,updatedAt:1},
  {id:6,title:'Un libro sin fecha',author:'Sin autor conocido',status:'finished',progress:100,startDate:'',finishDate:'',abandonedDate:'',summary:'',rating:0,favorite:false,updatedAt:300}
];
let voice=null,confirmCount=0, lastError='',clock=1000,shareResult=null;
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
  deleteBook:id=>{rows=rows.filter(x=>x.id!==id);return true;},
  sharePlainText:(title,text)=>{shareResult={title,text};}
};
window.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
window.esc=s=>String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
window.escAttr=window.esc;
window.uiIcon=name=>`<svg data-icon="${name}"></svg>`;
window.toast=s=>{lastError=s;};
window.openModal=(title,html)=>{
  doc.querySelector('.modal-sheet > .record-form-actions--compact')?.remove();
  doc.querySelector('#modalBody').innerHTML=html;
};
window.closeModal=()=>{
  doc.querySelector('#modalBody').innerHTML='';
  doc.querySelector('.modal-sheet > .record-form-actions--compact')?.remove();
  return true;
};
window.confirmGeneric=(title,body,fn)=>{confirmCount++;fn();};
window.startManagedDictation=(target,opts)=>{voice={target,opts};};
window.requestAnimationFrame=fn=>{fn();return 1;};
window.scrollTo=()=>{};
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
assert.equal(doc.querySelectorAll('[data-book-open="5"] .book-card-star').length,5,'La tarjeta incluye cinco estrellas');
assert.equal(doc.querySelectorAll('[data-book-open="5"] .book-card-star.is-set').length,5,'Cinco estrellas rellenas en un libro valorado 5');
assert.equal(doc.querySelectorAll('[data-book-open="1"] .book-card-star.is-set').length,3,'Tres estrellas rellenas en un libro valorado 3');
assert.equal(doc.querySelectorAll('[data-book-open="2"] .book-card-star.is-set').length,0,'Sin valorar: todas vacías');
assert(doc.querySelector('[data-book-open="1"] .book-card-rating').getAttribute('aria-label').includes('3 de 5'),'Accesibilidad de valoración');
assert.equal(doc.querySelectorAll('[data-book-open="1"] .book-card-rating button').length,0,'Las estrellas del listado no modifican la puntuación');
assert(doc.querySelector('[data-book-open="1"]').getAttribute('aria-label').includes('Terminado'));

setFilter('finished');
assert.deepEqual(bookIds(),[5,1,6],'Fecha de finalización descendente; sin fecha al final');
open(1);
assert(doc.querySelector('.book-detail'),'La ficha se abre sin entrar al editor');
assert(doc.querySelector('#modalBody').textContent.includes('Con notas completas.'));
assert(doc.querySelector('#modalBody').textContent.includes('Una saga familiar'),'Argumento completo visible en detalle');
assert(doc.querySelector('#modalBody').textContent.includes('21/01/2024'));
assert(doc.querySelector('#modalBody').textContent.includes('01/11/2023'),'Inicio visible en detalle');
assert(doc.querySelectorAll('[data-book-detail-rating]').length===5,'Cinco estrellas en la ficha');
assert(doc.querySelector('#bookDetailFavorite'),'Corazón en ficha');
assert(doc.querySelector('#bookDetailEdit')&&doc.querySelector('#bookDetailDelete')&&doc.querySelector('#bookDetailShare'));
assert.equal(doc.querySelectorAll('.book-detail-quick-actions .icon-action').length,3,'Pincel, compartir y eliminar como en Notas');
assert.equal(doc.querySelector('.book-detail').firstElementChild.classList.contains('book-detail-quick-actions'),true,'Acciones arriba del resumen');
assert(doc.querySelector('.book-detail-quick-actions').compareDocumentPosition(doc.querySelector('.book-detail-summary')) & 4,'Acciones preceden a resumen largo');
doc.querySelector('#bookDetailShare').click();
assert.equal(shareResult.title,'Cien años de soledad');
assert(shareResult.text.includes('Un clásico'),'Compartir incluye el resumen');
assert(shareResult.text.includes('Una saga familiar'),'Compartir incluye la sinopsis');
assert.equal(doc.querySelectorAll('#bookDetailEdit').length,1,'Editar es único en ficha');
assert.equal(doc.querySelector('#bookTitle'),null,'Detalle no es editor');

// Cambiar valoración y favorito sin editar la ficha, de manera independiente.
doc.querySelector('[data-book-detail-rating][data-rating="4"]').click();
assert.equal(rows.find(x=>x.id===1).rating,4);
assert.equal(doc.querySelectorAll('[data-book-open="1"] .book-card-star.is-set').length,4,'Las estrellas del listado se refrescan inmediatamente');
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
assert.equal(doc.querySelector('#bookSynopsis').value,'Una saga familiar\nCon recuerdos.','Sinopsis precargada al editar');
assert.equal(doc.querySelector('.book-editor').firstElementChild.classList.contains('book-editor-toolbar'),true,'Cabecera solo informativa');
assert.equal(doc.querySelectorAll('#bookSave').length,1,'Una única acción de guardar');
assert.equal(doc.querySelector('.modal-sheet>.book-editor-actions #bookSave')!==null,true,'Guardar está en el pie fijo, no encima del formulario');
assert.equal(doc.querySelector('.modal-sheet>.book-editor-actions #bookCancel')!==null,true,'Cancelar comparte el pie del editor');
assert.equal(doc.querySelector('#bookSave').classList.contains('primary-button'),true,'Mismo botón principal que en eventos');
assert.equal(doc.querySelector('#bookSave').textContent.trim(),'','Guardar solo tiene icono, sin texto');
assert.equal(doc.querySelector('#bookSave').getAttribute('aria-label'),'Guardar libro','Etiqueta accesible del disquete');
assert.equal(doc.querySelector('#bookSave').getAttribute('title'),'Guardar libro');
assert.equal(doc.querySelector('#bookSave').classList.contains('nexo-unified-save'),true);
assert.equal(doc.querySelector('#bookSave svg').getAttribute('data-icon'),'save','Icono de disquete');
assert.equal(doc.querySelectorAll('.book-editor .modal-actions').length,0,'Sin botonera duplicada al final del formulario');
doc.querySelector('#bookStatus').value='reading';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
assert.equal(doc.querySelector('#bookProgress').value,'99');
doc.querySelector('#bookStatus').value='abandoned';
doc.querySelector('#bookStatus').dispatchEvent(new window.Event('change',{bubbles:true}));
const slider=doc.querySelector('#bookProgress');
slider.value='42';slider.dispatchEvent(new window.Event('input',{bubbles:true}));
doc.querySelector('#bookAbandonedDate').value='2026-10-15';
doc.querySelector('#bookSummary').value='Resumen de prueba';
doc.querySelector('#bookSynopsis').value='Un argumento corregido';
doc.querySelector('#bookDictate').click();
assert.equal(voice.target,'book-summary');
window.NexoBooks.applyVoiceText('Resumen dictado y editable');
assert.equal(doc.querySelector('#bookSummary').value,'Resumen dictado y editable');
doc.querySelector('#bookSave').click();
const changed=rows.find(x=>x.id===1);
assert.equal(changed.status,'abandoned');assert.equal(changed.progress,42);
assert.equal(changed.abandonedDate,'2026-10-15');
assert.equal(changed.summary,'Resumen dictado y editable');
assert.equal(changed.synopsis,'Un argumento corregido','Sinopsis independiente del resumen');
assert.equal(changed.favorite,true);assert.equal(changed.rating,5);
assert.equal(doc.querySelector('[data-books-filter="finished"]').getAttribute('aria-pressed'),'true','Editar mantiene el filtro anterior');
assert.equal(doc.querySelector('#bookDetailFavorite'),null,'Guardar vuelve al listado y no reabre ficha');

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
assert(doc.querySelector('#modalBody').textContent.includes('Un misterio en Barcelona.'),'Sinopsis visible incluso antes de leer');
doc.querySelector('#bookDetailStart').click();
assert.equal(rows.find(x=>x.id===2).status,'reading');
assert.equal(rows.find(x=>x.id===2).synopsis,'Un misterio en Barcelona.','Empezar lectura conserva el argumento');
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
assert.equal(doc.querySelector('[data-books-filter="reading"]').getAttribute('aria-pressed'),'true','No cambiar filtro al guardar');
setFilter('finished');
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
assert(!doc.querySelector('#bookSynopsis').closest('.hidden'),'La sinopsis está disponible en alta pendiente');
doc.querySelector('#bookSynopsis').value='Una historia para recordar';
doc.querySelector('#bookSave').click();
assert.equal(rows.find(x=>x.title==='Nuevo pendiente').status,'want');
assert.equal(doc.querySelector('[data-books-filter="finished"]').getAttribute('aria-pressed'),'true','El filtro permanece tras añadir un libro fuera de él');
assert.equal(doc.querySelector('#bookDetailFavorite'),null,'Nuevo libro retorna al listado, no a la ficha');
setFilter('want');
assert(panel().includes('Nuevo pendiente'),'El libro nuevo existe y aparece en su categoría');
const newBook=rows.find(x=>x.title==='Nuevo pendiente');
assert.equal(newBook.rating,0);
assert.equal(newBook.synopsis,'Una historia para recordar');
doc.querySelector('[data-book-open="'+newBook.id+'"]').click();
assert(doc.querySelector('#modalBody').textContent.includes('Una historia para recordar'),'Sinopsis visible en ficha pendiente');
doc.querySelector('#bookDetailFavorite').click();
assert.equal(rows.find(x=>x.id===newBook.id).favorite,true,'Corazón guardado desde detalle');
doc.querySelector('[data-book-detail-rating][data-rating="3"]').click();
assert.equal(rows.find(x=>x.id===newBook.id).rating,3);
doc.querySelector('#bookDetailFavorite').click();
assert.equal(rows.find(x=>x.id===newBook.id).favorite,false);
assert.equal(rows.find(x=>x.id===newBook.id).rating,3);

// Duplicados, sin perder el formulario y sin datos borrados.
window.NexoBooks.openEditor();
doc.querySelector('#bookTitle').value='  CIEN  AÑOS DE SOLEDAD  ';
doc.querySelector('#bookAuthor').value='Gabriel Garcia Marquez';
doc.querySelector('#bookSave').click();
assert(doc.querySelector('#bookError').textContent.includes('ya están'));
assert.equal(rows.length,6,'El duplicado no se añade');

console.log('OK: Libros - 6 filtros con contadores, orden por fecha, estrellas y corazón independientes, estadísticas, dictado, migración UI y duplicados');
