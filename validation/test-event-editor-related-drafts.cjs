/**
 * Regressions for in-editor links, staged create/link, retry with original resource IDs,
 * recurring events, and consistent save actions.
 */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const css=fs.readFileSync('app/src/main/assets/styles.css','utf8');
function extract(name){
 const start=app.indexOf('function '+name+'(');assert(start>=0,'Falta '+name);
 const open=app.indexOf('{',start);let depth=0;
 for(let i=open;i<app.length;i++){
  if(app[i]==='{')depth++;if(app[i]==='}'&&--depth===0)return app.slice(start,i+1);
 }
 throw Error('Función incompleta: '+name);
}
for(const name of ['openRelatedPicker','stageOrLinkRelated','finishPendingEventLinks','renderCaptureRelated','discardPendingRelatedDrafts','normalizeSaveButtons','installSaveIconConsistency'])assert(app.includes('function '+name+'('));
for(const id of ['captureRelated','captureLinkButton','captureRelatedItems','captureRelatedStatus','captureLinkCounter'])assert(html.includes('id="'+id+'"'),id);
for(const id of ['editLinkButton','occLinkButton'])assert(app.includes('id="'+id+'"'),id);
for(const id of ['editLinkButton','occLinkButton'])assert(app.includes("'#"+id+"')?.addEventListener('click',()=>openRelatedPicker("),id+' listener');
assert(app.includes('saveCapture(JSON.stringify(payload))'));
assert(app.includes('finishPendingEventLinks(state.calendarUpdateItemId)'));
assert(app.includes("state.calendarUpdateItemId=Number(res.id)||state.calendarUpdateItemId"));
assert(app.includes("if(state.relatedDocumentCallback)state.relatedDocumentCallback(result)"));
assert(app.includes('data-related-expiry'));
assert(css.includes('.related-event-origin')&&css.includes('.event-capture-links-head'));
assert(app.includes("uiIcon('save')+(contextual?"));
assert(app.includes("observer.observe(document.body,{subtree:true,childList:true})"));
const changes={list:0,note:0,doc:0,link:[],discard:[],savedIDs:[],failLink:false,failDoc:false};
let id=100;
const native={
 createList:(title,kind)=>{changes.list++;changes.savedIDs.push('list:'+title+':'+kind);return ++id;},
 saveNote:raw=>{changes.note++;const obj=JSON.parse(raw);assert.equal(obj.noteTitleManual,true);changes.savedIDs.push('note:'+obj.title);return ++id;},
 saveDocument:raw=>{changes.doc++;const o=JSON.parse(raw);assert.equal(o.expiryAt,1735646400000);changes.savedIDs.push('document:'+o.title);return changes.failDoc?JSON.stringify({ok:false}):JSON.stringify({ok:true,id:++id});},
 linkRelatedResource:(owner,kind,resource)=>{changes.link.push({owner,kind,resource});return JSON.stringify({ok:!changes.failLink});},
 discardDocumentFile:path=>changes.discard.push(path)
};
const state={capturePendingLinks:[]},ctx={state,NexoNative:native,JSON,Number,Array,
 parseNative:(v,f)=>{try{return JSON.parse(v);}catch{return f;}},
 renderCaptureRelated:()=>{},refreshRelatedLinks:()=>{},toast:()=>{}};
vm.createContext(ctx);
for(const name of ['discardPendingRelatedDrafts','stageOrLinkRelated','finishPendingEventLinks']){
 vm.runInContext(extract(name),ctx);
}
function own(entry){return JSON.parse(JSON.stringify(entry));}
const stored=own({kind:'list',id:7,title:'Lista existente'});
const note=own({kind:'note',title:'Nueva nota',body:'Recordar detalles'});
const list=own({kind:'list',title:'Nueva lista',listKind:'Viaje'});
const doc=own({kind:'document',title:'Contrato',body:'Adjunto',expiryAt:1735646400000,category:'Seguro',file:{path:'/private/contract.pdf',name:'contract.pdf',mime:'application/pdf'}});
for(const entry of [stored,note,list,doc])assert.equal(ctx.stageOrLinkRelated(0,entry),true);
assert.deepEqual(changes.savedIDs,[],'No se deben crear recursos antes de guardar la cita');
assert.equal(changes.link.length,0,'No vincular antes de que exista el evento');
changes.failLink=true;
assert.equal(ctx.finishPendingEventLinks(42),false,'Fallo reversible');
assert.equal(changes.link.length,1);
assert.equal(state.capturePendingLinks.length,4,'Retener los vínculos pendientes');
changes.failLink=false;
assert.equal(ctx.finishPendingEventLinks(42),true);
assert.equal(state.capturePendingLinks.length,0);
assert.equal(changes.list,1);assert.equal(changes.note,1);assert.equal(changes.doc,1);
assert.deepEqual(changes.link.slice(1).map(x=>x.kind),['list','note','list','document']);
assert(changes.link.every(x=>x.owner===42),'Todos los vínculos usan el ID real');
assert.equal(changes.discard.length,0,'No descartar documentos ya guardados');
assert.equal(ctx.stageOrLinkRelated(0,stored),true);
assert.equal(ctx.stageOrLinkRelated(0,stored),false,'No duplicar vínculos en el borrador');
ctx.discardPendingRelatedDrafts();
assert.equal(state.capturePendingLinks.length,0);
ctx.stageOrLinkRelated(0,doc);
ctx.discardPendingRelatedDrafts();
assert.deepEqual(changes.discard,['/private/contract.pdf'],'Descartar adjunto temporal al cancelar');
const writesBefore=changes.list+changes.note+changes.doc;
assert.equal(ctx.stageOrLinkRelated(99,{kind:'note',id:11,title:'Nota existente'}),true);
assert.equal(changes.link.at(-1).owner,99,'En edición la asociación usa el ID del evento original');
assert.equal(changes.list+changes.note+changes.doc,writesBefore,'Vincular existente no crea recursos');
// The global normalization preserves IDs and listeners and renders exactly the same SVG symbol as Notas.
const iconCtx={uiIcon:name=>'<svg class="ui-icon"><use href="#nx-icon-'+name+'"></use></svg>',document:{}};
vm.createContext(iconCtx);vm.runInContext(extract('normalizeSaveButtons'),iconCtx);
const buttons=[
 {id:'documentSave',textContent:'Guardar documento',attributes:{},innerHTML:'',classList:new Set()},
 {id:'medSave',textContent:'Confirmar tratamiento',attributes:{},innerHTML:'',classList:new Set()},
 {id:'saveCaptureBtn',textContent:'Revisar pauta',attributes:{},innerHTML:'',classList:new Set()},
 {id:'downloadFile',textContent:'Guardar archivo en Descargas',attributes:{},innerHTML:'',classList:new Set()}
].map(o=>({...o,hasAttribute:k=>k in o.attributes,getAttribute:k=>o.attributes[k]||'',setAttribute(k,v){this.attributes[k]=v;},
 classList:{contains:k=>o.classList.has(k),add:k=>o.classList.add(k)},querySelector:()=>null}));
iconCtx.normalizeSaveButtons({querySelectorAll:q=>{assert.equal(q,'button');return buttons;}});
assert(buttons[0].innerHTML.includes('#nx-icon-save'));
assert(buttons[1].innerHTML.includes('#nx-icon-save'));
assert.equal(buttons[2].innerHTML,'','Revisar pauta no es Guardar');
assert.equal(buttons[3].innerHTML,'','Exportar audio/archivo no es Guardar del CRUD');
assert.equal(buttons[0].attributes['aria-label'],'Guardar documento');
console.log('Eventos: vínculos al crear/editar, documentos con caducidad, regreso sin perder cambios, reintentos y disquetes: OK');