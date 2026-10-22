import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const css=fs.readFileSync('app/src/main/assets/styles.css','utf8');
const start=app.indexOf('function noteSnippet(body){');
const end=app.indexOf('function setNoteSaveStatus(',start);
assert(start>0&&end>start,'Encontrar sección de lectura de notas');
const fragment=app.slice(start,end);

const {window:w}=new JSDOM('<main id="notes"></main><h2 id="modalTitle"></h2><div id="modalBody"></div>',{
  url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
});
const d=w.document;
const body='Primera línea de información\nSegunda línea, con datos precisos\n'+('Texto largo para lectura continua. '.repeat(120))+'\nÚltima línea de la nota.';
const entries=[
  {id:12,type:'note',title:'Nota de prueba larga',body,createdAt:1700000000000},
  {id:13,type:'note',title:'Nota vacía',body:'',createdAt:1700001000000}
];
const events={edit:[],share:[],delete:[],links:0,read:0,errors:[]};
w.state={noteReaderFontSize:17};
w.$=sel=>d.querySelector(sel);
w.$$=sel=>[...d.querySelectorAll(sel)];
w.esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
w.escAttr=w.esc;
w.fmtDate=()=> '14 nov 2023';
w.uiIcon=name=>'<svg data-icon="'+name+'"></svg>';
w.recordIconAction=(icon,label,attrs='',extra='')=>'<button type="button" class="icon-action '+extra+'" '+attrs+' aria-label="'+label+'">'+w.uiIcon(icon)+'</button>';
w.parseNative=(raw,fallback)=>{try{return JSON.parse(raw);}catch{return fallback;}};
w.NexoNative={
 getItem:id=>JSON.stringify(entries.find(x=>x.id===id)||{}),
 shareNexoItem:(id)=>events.share.push(id)
};
w.openModal=(title,html)=>{d.querySelector('#modalTitle').textContent=title;d.querySelector('#modalBody').innerHTML=html;};
w.openNoteComposer=id=>events.edit.push(id);
w.confirmDeleteItem=n=>events.delete.push(n.id);
w.deleteItemFromCard=id=>events.delete.push(Number(id));
w.bindRelatedOwners=()=>{events.links++};
w.relatedOwnersSectionHtml=()=>'<section id="noteRelated">Referencias a esta nota</section>';
w.refreshNotesSurface=()=>{events.read++};
w.toast=msg=>events.errors.push(msg);

const shareStart=app.indexOf('function openConnectShareChoice(');
const shareEnd=app.indexOf('\nfunction ',shareStart+1);
w.eval(app.slice(shareStart,shareEnd));
w.NexoNative.isNexoConnectExperimentEnabled=()=>true;
const connectShares=[];
w.NexoNative.openNexoConnectShare=(section,id)=>connectShares.push([section,id]);
w.eval(fragment);
d.querySelector('#notes').innerHTML=entries.map(w.noteCard).join('');
w.bindNoteButtons(d.querySelector('#notes'));
assert.equal(d.querySelectorAll('[data-note-read]').length,2,'Una zona táctil por tarjeta');
assert.equal(d.querySelectorAll('[data-open-note]').length,2,'Lápiz separado');
assert(d.querySelector('.note-card-preview').textContent.length<200,'El listado no despliega la nota completa');
assert(!d.querySelector('.note-card-body'),'No usar scroll anidado en tarjetas');
assert(!d.querySelector('#notes').textContent.includes('Última línea de la nota.'),'La lista muestra resumen');

d.querySelector('[data-note-read="12"]').click();
assert.equal(d.querySelector('#modalTitle').textContent,'Leer nota');
assert(d.body.classList.contains('note-reading-open'));
assert.equal(d.querySelector('#noteReaderText').textContent,body,'El visor muestra TODO el contenido sin alterar saltos de línea');
assert.equal(d.querySelectorAll('#modalBody textarea,#modalBody input').length,0,'Lectura sin edición accidental');
assert.equal(events.edit.length,0,'Tocar ficha no edita');
assert(d.querySelector('#noteRelated'),'Conserva vínculos con eventos y tareas');
assert.equal(events.links,1);

d.querySelector('#noteReaderFontDown').click();
assert.equal(d.querySelector('#noteReaderText').style.fontSize,'16px');
assert.equal(d.querySelector('#noteReaderFontDown').disabled,true,'Límite inferior de lectura');
d.querySelector('#noteReaderFontUp').click();
assert.equal(d.querySelector('#noteReaderText').style.fontSize,'17px');
d.querySelector('#noteReaderShare').click();
assert(d.querySelector('[data-connect-share]'),'Ofrece compartir por Nexo');
d.querySelector('[data-normal-share]').click();
assert.deepEqual(events.share,[12]);
d.querySelector('#noteReaderDelete').click();
assert.deepEqual(events.delete,[12]);
d.querySelector('#noteReaderEdit').click();
assert.deepEqual(events.edit,[12]);

// El lápiz de la tarjeta sigue editando; tocar el título solo abre lectura.
d.querySelector('[data-open-note="13"]').click();
assert.deepEqual(events.edit,[12,13]);
d.querySelector('[data-note-read="13"]').click();
assert(d.querySelector('#noteReaderText').textContent.includes('Nota sin anotación.'));
d.querySelector('[data-note-share="13"]').click();
d.querySelector('[data-normal-share]').click();
assert.deepEqual(events.share,[12,13]);
d.querySelector('[data-note-delete="13"]').click();
assert.deepEqual(events.delete,[12,13]);
d.querySelector('[data-note-share="13"]').click();
d.querySelector('[data-connect-share]').click();
assert.deepEqual(connectShares,[['items',13]]);
w.openNoteReader(999);
assert.equal(events.errors.at(-1),'La nota ya no existe.');

// Estructura táctil: la cabecera y el botón de cierre permanecen visibles,
// mientras el cuerpo largo se desplaza verticalmente.
assert(css.includes('.note-reading-open .modal-sheet'));
assert(css.includes('.note-reading-open #modalBody'));
assert(css.includes('overflow-y:auto'));
assert(css.includes('white-space:pre-wrap'));
assert(css.includes('user-select:text'));

console.log('OK: tarjetas pulsables, lectura completa y desplazable, letra ajustable, edición/compartir/eliminar independientes.');
