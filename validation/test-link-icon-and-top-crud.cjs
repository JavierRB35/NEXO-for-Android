// Regression: icono de cadena, contador dinámico y acciones CRUD superiores.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const src=fs.readFileSync('app/src/main/assets/app.js','utf8');
const index=fs.readFileSync('app/src/main/assets/index.html','utf8');
const css=fs.readFileSync('app/src/main/assets/styles.css','utf8');
function extract(name){
 const p=src.indexOf('function '+name+'(');
 assert(p>=0,'Falta '+name);
 const open=src.indexOf('{',p);let depth=0;
 for(let i=open;i<src.length;i++){
  if(src[i]==='{')depth++;
  if(src[i]==='}'&&--depth===0)return src.slice(p,i+1);
 }
 throw Error('Función sin cierre: '+name);
}
assert(index.includes('id="nx-icon-link"'));
assert(src.includes("uiIcon('link')+' Vincular"));
assert(src.includes("uiIcon('link')+' Vinculados"));
assert(css.includes('.upcoming-card__links')&&css.includes('#1263b5'));
assert(css.includes('.linked-add-count'));
assert(!src.includes('function eventLinkIndicatorHtml('), 'No repetir contador fuera del botón Vincular');
const ctx={Number,JSON,NexoNative:{getRelatedResources:()=>JSON.stringify([])},
 parseNative:(s,fallback)=>{try{return JSON.parse(s);}catch{return fallback;}},
 uiIcon:n=>'<svg data-icon="'+n+'"></svg>',esc:s=>String(s||''),escAttr:s=>String(s||''),
 RELATED_KIND_LABELS:{list:'Lista',note:'Nota',document:'Documento'}};
vm.createContext(ctx);
vm.runInContext(extract('relatedLinksSectionHtml'),ctx);
const empty=ctx.relatedLinksSectionHtml(42);
assert(empty.includes('data-related-add="42"')&&empty.includes('Vincular'));
assert(!empty.includes('linked-add-count'),'No mostrar cero dentro del botón');
ctx.NexoNative.getRelatedResources=()=>JSON.stringify([
 {linkId:1,kind:'list',id:11,title:'Compra'},
 {linkId:2,kind:'note',id:12,title:'Notas'}]);
const linked=ctx.relatedLinksSectionHtml(42);
assert(linked.includes('data-icon="link"'));
assert(linked.includes('linked-add-count">2</span>'),'El botón Vincular debe llevar el número dentro');
assert.equal((linked.match(/linked-add-count/g)||[]).length,1,'Sin contadores repetidos');
assert(!linked.includes('linked-count'),'No repetir el número en Vinculados');
const normal=src.slice(src.indexOf('function openItem(id)'),src.indexOf('function openOccurrence('));
const occurrence=src.slice(src.indexOf('function openOccurrence('),src.indexOf('function chooseRecurrenceScope('));
assert(normal.includes('relatedLinksSectionHtml(id)'));
assert(occurrence.includes('relatedLinksSectionHtml(seriesId)'));
assert(!normal.includes('data-related-indicator-host'));
assert(!occurrence.includes('data-related-indicator-host'));
const capture=index.indexOf('class="capture-crud-toolbar"');
assert(capture>0&&capture<index.indexOf('id="captureTitle"'));
assert(capture<index.indexOf('id="captureText"'));
for(const id of ['saveCaptureBtn','chooseFileBtn','clearCaptureBtn'])assert.equal((index.match(new RegExp('id="'+id+'"','g'))||[]).length,1,'Botón duplicado: '+id);
assert(src.includes("promoteRecordActions($('#modalBody'));"));
assert(css.includes('record-top-actions'));
assert(src.includes('id="noteReaderDelete" class="icon-action icon-action--danger"'));
assert(!src.includes('note-reader-bottom'));
const moved=[];
function group(ids,name){return{name,classList:{add(x){assert.equal(x,'record-top-actions');this.top=true;}},querySelectorAll:q=>{
 assert.equal(q,'button[id]');return ids.map(id=>({id}));
}};}
const crud=group(['placeSave','placeCancel'],'lugar');
const other=group(['confirmDelete','confirmCancel'],'confirmación');
const journal=group(['journalEdit','journalDelete'],'cuaderno');
const doc=group(['documentDetailEdit','documentDetailOpen'],'documento');
const root={firstChild:{},querySelector:()=>null,querySelectorAll:q=>{assert.equal(q,'.modal-actions,.place-detail-actions,.document-detail__actions');return[crud,other,journal,doc];},insertBefore:(el,node)=>{assert.equal(node,root.firstChild);moved.push(el.name);}};
vm.runInContext(extract('promoteRecordActions'),ctx);
ctx.promoteRecordActions(root);
assert.deepEqual(moved,['lugar','cuaderno','documento']);
assert.equal(other.classList.top,undefined,'Las confirmaciones no son CRUD del elemento');
for(const p of ['books.js','media.js']){
 const file=fs.readFileSync('app/src/main/assets/'+p,'utf8');
 assert(file.includes('book-detail-quick-actions')&&file.includes('book-editor-toolbar'));
}
console.log('Vínculos en botón Vincular, sin duplicación de contador; CRUD arriba: OK');
