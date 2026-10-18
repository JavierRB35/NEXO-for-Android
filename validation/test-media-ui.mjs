import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const dom=new JSDOM('<main><div id="panelContent"></div><div id="modalBody"></div></main>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const {window}=dom, document=window.document;
let rows=[], nextId=1, shared='';
window.requestAnimationFrame=cb=>cb();
window.scrollTo=()=>{};
window.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
window.esc=v=>String(v??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
window.escAttr=v=>window.esc(v).replace(/"/g,'&quot;');
window.uiIcon=name=>'<i>'+name+'</i>';
window.toast=()=>{};
window.openModal=(_title,html)=>{document.querySelector('#modalBody').innerHTML=html;};
window.closeModal=()=>{document.querySelector('#modalBody').innerHTML='';};
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
document.querySelector('#bookTitle').value='El viaje';
document.querySelector('#bookAuthor').value='Ana';
document.querySelector('#mediaKind').value='series';
document.querySelector('#bookStatus').value='reading';
document.querySelector('#bookStatus').dispatchEvent(new window.Event('change'));
document.querySelector('#bookSave').click();
assert.equal(rows.length,1);
assert.equal(rows[0].mediaType,'series');
assert.equal(rows[0].status,'reading');
assert.equal(document.querySelectorAll('[data-book-open]').length,1);
document.querySelector('[data-media-type-filter="movie"]').click();
assert.equal(document.querySelectorAll('[data-book-open]').length,0);
document.querySelector('[data-media-type-filter="series"]').click();
assert.equal(document.querySelectorAll('[data-book-open]').length,1);
document.querySelector('[data-book-open]').click();
document.querySelector('#bookDetailFavorite').click();
assert.equal(rows[0].favorite,true);
document.querySelector('[data-book-detail-rating][data-rating="4"]').click();
assert.equal(rows[0].rating,4);
document.querySelector('#bookDetailShare').click();
assert.match(shared,/Tipo: Serie/);
document.querySelector('#bookDetailEdit').click();
document.querySelector('#bookSummary').value='Mi opinión';
document.querySelector('#bookSave').click();
assert.equal(rows[0].summary,'Mi opinión');
document.querySelector('[data-book-open]').click();
document.querySelector('#bookDetailDelete').click();
assert.equal(rows.length,0);
console.log('OK: películas y series · crear, filtrar, valorar, favorito, editar, compartir y borrar');
