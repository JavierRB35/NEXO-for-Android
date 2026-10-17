'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const css=fs.readFileSync('app/src/main/assets/styles.css','utf8');
const start=app.indexOf('/** La persona elige el uso de la lista; Nexo le asigna un nombre provisional. */');
const end=app.indexOf('function openNewListModal(){',start);
assert(start>0 && end>start,'No se localiza la lógica de creación rápida');
const items=[];const openings=[],notifications=[];let closed=0;
const context={
  NexoNative:{
    getLists:()=>JSON.stringify(items),
    createList:(title,kind,reusable)=>{
      if(!title || !kind) return 0;
      const id=items.length+1;items.push({id,title,kind,reusable});return id;
    }
  },
  parseNative:(raw,fallback)=>{try{return JSON.parse(raw);}catch(_){return fallback;}},
  closeModal:()=>{closed++;return true;},
  openList:(id,opts)=>{openings.push({id,opts});},
  toast:(message)=>{notifications.push(message);}
};
vm.createContext(context);
vm.runInContext(app.slice(start,end),context,{filename:'nexo-list-quick-create.js'});
context.quickCreateList('Compra');
assert.equal(items.length,1);
assert.match(items[0].title,/^Compra · /);
assert.equal(items[0].kind,'Compra');
assert.equal(items[0].reusable,true);
assert.equal(openings[0].id,1);
assert.equal(openings[0].opts.focusInput,true);
context.quickCreateList('Compra');
assert.equal(items.length,2);
assert.equal(items[1].title,items[0].title+' (2)');
assert.notEqual(items[0].title,items[1].title);
context.quickCreateList('Cosas pendientes');
assert.match(items[2].title,/^Pendientes · /);
assert.equal(items[2].reusable,false);
context.quickCreateList('Viaje');
assert.equal(items[3].reusable,true);
context.quickCreateList('General');
assert.match(items[4].title,/^Lista · /);
context.quickCreateList('No existe');
assert.equal(items.length,5,'No crear listas de tipos desconocidos');
assert.equal(closed,5);
assert.equal(openings.length,5);
assert(notifications.some(x=>x.includes('Elige un tipo')));
const createUi=app.slice(end,app.indexOf('function openParkingModal(',end));
for(const token of ["data-list-preset","quick-list-presets","quick-list-other","No necesitas escribir un nombre."])
  assert(createUi.includes(token),token);
assert(!createUi.includes('id="listTitle"'),'No exigir título antes de crear');
const render=app.slice(app.indexOf('function renderListsPanel(){'),app.indexOf('function openListManagement(id){'));
for(const token of ['list-row-shell--compact','list-card-menu','list-progress','listRenameInline',
 'listQuickInput','listAddBtn','list-voice-help','listCountText','relatedOwnersSectionHtml'])
 assert(render.includes(token),token);
assert(!render.includes('data-list-delete'),'No apilar botón eliminar en cada tarjeta');
assert(css.includes('.quick-list-presets') && css.includes('.list-card-compact') && css.includes('.list-add-inline'));
console.log('Listas 3.7.32: nombres automáticos únicos, tipos, alta, foco y diseño compacto OK');
