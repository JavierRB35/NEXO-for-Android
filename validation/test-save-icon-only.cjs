// Regresión transversal: Guardar es solo disquete con aria-label, sin texto ni bucles.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {JSDOM}=require('jsdom');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
function extract(name){
  const start=source.indexOf('function '+name+'(');
  assert(start>=0,'Falta '+name);
  const open=source.indexOf('{',start);let depth=0;
  for(let i=open;i<source.length;i++){
    if(source[i]==='{')depth++;
    if(source[i]==='}'&&--depth===0)return source.slice(start,i+1);
  }
  throw new Error('Función incompleta '+name);
}
const dom=new JSDOM(`<html><body>
<button id="bookSave" class="primary-button" type="button">
  <svg><use href="#nx-icon-save"></use></svg><span class="nexo-action-label">Guardar libro</span>
</button>
<button id="documentSave" class="primary-button">Guardar documento</button>
<button id="saveEditBtn" class="primary-button">Guardar</button>
<button id="medSave" class="primary-button">Confirmar tratamiento</button>
<button id="parkingKeep" class="primary-button">Guardar</button>
<button id="saveCaptureBtn" class="primary-button">Guardar en Nexo</button>
<button id="relatedSave" class="primary-button" data-related-save>Crear y vincular</button>
<button id="downloadFile">Guardar archivo en Descargas</button>
</body></html>`,{url:'https://nexo.local/'});
const {document}=dom.window;
const ctx={document,uiIcon:name=>'<svg class="ui-icon"><use href="#nx-icon-'+name+'"></use></svg>',esc:v=>String(v)};
vm.createContext(ctx);
vm.runInContext(extract('normalizeSaveButtons'),ctx);
const ids=['bookSave','documentSave','saveEditBtn','medSave','parkingKeep','saveCaptureBtn','relatedSave'];
ctx.normalizeSaveButtons();
for(const id of ids){
  const b=document.getElementById(id);
  assert.equal(b.textContent.trim(),'','Sin texto en '+id);
  assert.equal(b.querySelectorAll('use[href="#nx-icon-save"]').length,1,'Un solo disquete en '+id);
  assert(b.classList.contains('nexo-unified-save'),'Estilo global en '+id);
  assert(b.getAttribute('aria-label'),'Accesibilidad en '+id);
  assert(b.getAttribute('title'),'Tooltip en '+id);
}
assert.equal(document.getElementById('relatedSave').getAttribute('aria-label'),'Guardar y volver');
const snapshot=ids.map(id=>document.getElementById(id).innerHTML);
ctx.normalizeSaveButtons();
assert.deepEqual(ids.map(id=>document.getElementById(id).innerHTML),snapshot,
  'Segunda normalización no modifica nodos ni provoca bucle');
const book=document.getElementById('bookSave');
book.textContent='Guardar libro';
ctx.normalizeSaveButtons();
assert.equal(book.textContent.trim(),'','Cambios heredados vuelven a solo icono');
assert.equal(book.querySelectorAll('use[href="#nx-icon-save"]').length,1);
assert.equal(document.getElementById('downloadFile').textContent.trim(),'Guardar archivo en Descargas',
  'No modificar exportación a Descargas');
const capture=document.getElementById('saveCaptureBtn');
capture.textContent='Revisar pauta';
ctx.normalizeSaveButtons();
assert.equal(capture.textContent.trim(),'Revisar pauta','Revisar pauta no guarda aún');
assert(!capture.classList.contains('nexo-unified-save'),'La revisión no lleva restricciones del botón icon-only');
capture.textContent='Guardar en Nexo';
ctx.normalizeSaveButtons();
assert.equal(capture.textContent.trim(),'','Al volver a crear cita se restaura el disquete');
assert.equal(capture.querySelectorAll('use[href="#nx-icon-save"]').length,1);
console.log('Guardar: icono único, accesibilidad, idempotencia y excepciones: OK');
