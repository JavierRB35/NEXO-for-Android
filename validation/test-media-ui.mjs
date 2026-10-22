import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const dom=new JSDOM('<main><div id="panelContent"></div><div id="modal"><div class="modal-sheet"><div id="modalBody"></div></div></div></main>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const {window}=dom, document=window.document;
let rows=[], nextId=1, shared='';
window.requestAnimationFrame=cb=>cb();
window.scrollTo=()=>{};
window.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
window.esc=v=>String(v??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
window.escAttr=v=>window.esc(v).replace(/"/g,'&quot;');
window.uiIcon=name=>'<svg data-icon="'+name+'"></svg>';
window.toast=()=>{};
window.openModal=(_title,html)=>{
  document.querySelector('.modal-sheet>.record-form-actions--compact')?.remove();
  document.querySelector('#modalBody').innerHTML=html;
};
window.closeModal=()=>{
  document.querySelector('#modalBody').innerHTML='';
  document.querySelector('.modal-sheet>.record-form-actions--compact')?.remove();
};
window.confirmGeneric=(_title,_body,callback)=>callback();
window.startManagedDictation=()=>{};
window.openPanel=()=>window.NexoMedia.render();
window.NexoNative={
  getMediaItems:()=>JSON.stringify(rows),
  getMediaItem:id=>JSON.stringify(rows.find(x=>x.id===id)||{}),
  saveMediaItem:raw=>{
    const b=JSON.parse(raw);
    if(!b.title)return JSON.stringify({ok:false,error:'Falta título'});
    const existing=rows.findIndex(x=>x.id===b.id);
    if(existing>=0)rows[existing]={...b,updatedAt:Date.now()};
    else {b.id=nextId++; rows.push({...b,updatedAt:Date.now()});}
    return JSON.stringify({ok:true,id:b.id});
  },
  deleteMediaItem:id=>{const n=rows.length;rows=rows.filter(x=>x.id!==id);return rows.length<n;},
  sharePlainText:(_title,text)=>{shared=text;}
};
window.eval(fs.readFileSync('app/src/main/assets/media.js','utf8'));
window.NexoMedia.render();
assert.equal(document.querySelectorAll('[data-media-type-filter]').length,3);
window.NexoMedia.openEditor();
assert.equal(document.querySelectorAll('#bookSave').length,1,'Solo un disquete de guardar');
assert(document.querySelector('.modal-sheet>.book-editor-actions #bookSave'),'Guardar permanece fijo abajo');
assert.equal(document.querySelector('#bookSave').textContent.trim(),'','Disquete sin texto');
assert.equal(document.querySelector('#bookSave').getAttribute('aria-label'),'Guardar título');
assert.equal(document.querySelector('#bookSave svg').getAttribute('data-icon'),'save');
document.querySelector('#bookTitle').value='El viaje';
document.querySelector('#bookAuthor').value='Ana';
document.querySelector('#bookSynopsis').value='Una aventura que quiero recordar';
document.querySelector('#mediaKind').value='series';
document.querySelector('#bookStatus').value='reading';
document.querySelector('#bookStatus').dispatchEvent(new window.Event('change'));
document.querySelector('#bookSave').click();
assert.equal(rows.length,1);
assert.equal(rows[0].mediaType,'series');
assert.equal(rows[0].status,'reading');
assert.equal(rows[0].synopsis,'Una aventura que quiero recordar','Sinopsis persistida en alta');
assert.equal(document.querySelectorAll('[data-book-open]').length,1);
document.querySelector('[data-media-type-filter="movie"]').click();
assert.equal(document.querySelectorAll('[data-book-open]').length,0);
document.querySelector('[data-media-type-filter="series"]').click();
assert.equal(document.querySelectorAll('[data-book-open]').length,1);
document.querySelector('[data-book-open]').click();
assert(document.querySelector('#modalBody').textContent.includes('Una aventura que quiero recordar'),'Sinopsis visible en detalle');
document.querySelector('#bookDetailFavorite').click();
assert.equal(rows[0].favorite,true);
document.querySelector('[data-book-detail-rating][data-rating="4"]').click();
assert.equal(rows[0].rating,4);
document.querySelector('#bookDetailShare').click();
assert.match(shared,/Tipo: Serie/);
assert.match(shared,/Argumento \/ sinopsis:\nUna aventura/);
document.querySelector('#bookDetailEdit').click();
assert.equal(document.querySelector('#bookSynopsis').value,'Una aventura que quiero recordar');
document.querySelector('#bookSummary').value='Mi opinión';
document.querySelector('#bookSave').click();
assert.equal(rows[0].summary,'Mi opinión');
assert.equal(rows[0].synopsis,'Una aventura que quiero recordar','Editar notas no borra argumento');
document.querySelector('[data-book-open]').click();
document.querySelector('#bookDetailDelete').click();
assert.equal(rows.length,0);
window.NexoMedia.openEditor();
assert.equal(document.querySelector('#mediaKind').value,'movie','Nueva ficha de película');
assert(!document.querySelector('#bookSynopsis').closest('.hidden'),'Argumento disponible antes de verla');
document.querySelector('#bookTitle').value='Película pendiente';
document.querySelector('#bookSynopsis').value='Una trama de misterio';
document.querySelector('#bookSave').click();
assert.equal(rows[0].status,'want');
assert.equal(rows[0].synopsis,'Una trama de misterio');
document.querySelector('[data-media-type-filter="movie"]').click();
document.querySelector('[data-book-open]').click();
assert(document.querySelector('#modalBody').textContent.includes('Una trama de misterio'),'Argumento visible antes del visionado');
console.log('OK: libros y filmoteca · sinopsis opcional, datos editables e independientes del resumen');
