// Regression real de los botones inferiores: foco sin teclado y edición desplazable.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {JSDOM}=require('jsdom');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
function extract(name){
  const start=source.indexOf('function '+name+'(');
  assert(start>=0,'Falta '+name);
  const open=source.indexOf('{',start);let level=0;
  for(let i=open;i<source.length;i++){
    if(source[i]==='{')level++;
    if(source[i]==='}'&&--level===0)return source.slice(start,i+1);
  }
  throw new Error('Función incompleta: '+name);
}
const dom=new JSDOM('<!DOCTYPE html><html><body>'+
  '<nav class="bottom-nav"></nav>'+
  '<textarea id="captureText"></textarea>'+
  '<div id="modal"><div class="modal-sheet"><header class="modal-head">Título</header>'+
  '<div id="modalBody"><div class="record-form-actions record-form-actions--compact">'+
  '<button id="editItemTopBtn" class="secondary-button">Editar</button>'+
  '<button id="saveEditBtn" class="primary-button" hidden>Guardar</button>'+
  '<details class="record-action-overflow"><summary>Más</summary></details>'+
  '</div><details class="details-box"><summary>Editar datos</summary>'+
  '<section class="event-edit-related"><button id="editLinkButton"><span>Vincular</span></button></section>'+
  '<input id="editTitle"></details></div></div></div></body></html>',{url:'https://localhost/'});
const {window}=dom,doc=window.document;
const $=q=>doc.querySelector(q),viewportListeners={};
const viewport={height:780,addEventListener:(event,fn)=>{viewportListeners[event]=fn;}};
Object.defineProperty(window,'visualViewport',{configurable:true,value:viewport});
Object.defineProperty(window,'innerHeight',{configurable:true,value:780});
$('.bottom-nav').getBoundingClientRect=()=>({height:72});
const ctx={document:doc,window,$,requestAnimationFrame:cb=>cb(),uiIcon:n=>'<svg>'+n+'</svg>',Math,Infinity};
vm.createContext(ctx);
vm.runInContext(extract('bindCompactRecordActions'),ctx);
ctx.bindCompactRecordActions();
const bar=$('.modal-sheet>.record-form-actions--compact');
assert(bar,'La barra de edición debe colgar directamente de la ventana modal');
assert.equal($('#modalBody .record-form-actions--compact'),null,'El pie no puede depender del scroll de los campos');
assert.equal(bar.querySelector('#editLinkButton')?.textContent,'Vincular');
const editor=$('#modalBody .details-box');
assert.equal($('#editItemTopBtn').hidden,false);
assert.equal($('#saveEditBtn').hidden,true);
editor.open=true;editor.dispatchEvent(new window.Event('toggle'));
assert.equal($('#editItemTopBtn').hidden,true);
assert.equal($('#saveEditBtn').hidden,false);
editor.open=false;editor.dispatchEvent(new window.Event('toggle'));
assert.equal($('#editItemTopBtn').hidden,false);

vm.runInContext(extract('installFixedActionBarViewport'),ctx);
ctx.installFixedActionBarViewport();
assert.equal(doc.documentElement.style.getPropertyValue('--nexo-bottom-nav-height'),'72px');
const capture=$('#captureText');
capture.focus();
assert.equal(doc.activeElement,capture);
assert(!doc.body.classList.contains('nexo-keyboard-visible'),'El foco sin teclado debe conservar los botones');
viewport.height=475;viewportListeners.resize();
assert(doc.body.classList.contains('nexo-keyboard-visible'),'Teclado visible: liberar espacio');
viewport.height=780;viewportListeners.resize();
assert.equal(doc.activeElement,capture,'El foco permanece cuando se cierra el teclado');
assert(!doc.body.classList.contains('nexo-keyboard-visible'),'Cerrar teclado devuelve el pie sin requerir blur');
console.log('Barras inferiores: foco/IME, edición, recurrencia base, scroll aislado: OK');
