import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const actionStart=app.indexOf('function recordEditorActionBar(');
const actionEnd=app.indexOf('const RELATED_KIND_LABELS=',actionStart);
const mapsStart=app.indexOf('function isGoogleMapsLocation(');
const mapsEnd=app.indexOf('function itemEffectiveEndAt(',mapsStart);
assert(actionStart>=0 && actionEnd>actionStart && mapsStart>=0 && mapsEnd>mapsStart);

const dom=new JSDOM('<div id="modal"><div class="modal-sheet"><div id="modalBody"></div></div></div><input id="editLocation"><input id="editMapsUrl"><button id="editLocationMapBtn"></button>',{runScripts:'outside-only',url:'https://nexo.local/'});
const w=dom.window,d=w.document;
const destinations=[];
w.$=selector=>d.querySelector(selector);
w.uiIcon=name=>'<svg data-icon="'+name+'"></svg>';
w.esc=value=>String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;');
w.escAttr=w.esc;
w.toast=()=>{};
w.NexoNative={openLocationDirections:value=>destinations.push(value)};
w.eval(app.slice(actionStart,actionEnd)+'\n'+app.slice(mapsStart,mapsEnd));

const event={locationText:'TALLERES CIVERA CB',mapsUrl:''};
assert.match(w.eventLocationShortcutHtml(event,'eventDetailLocationBtn'),/TALLERES CIVERA CB/);
assert.equal(w.eventLocationShortcutHtml({locationText:'',mapsUrl:''},'emptyBtn'),'');
assert.equal(w.eventHasLocation({mapsUrl:'https://maps.app.goo.gl/abc'}),true);

const modal=d.querySelector('#modalBody');
modal.innerHTML=w.recordEditorActionBar('saveEditBtn','editItemTopBtn','shareItemBtn','deleteItemBtn','','eventDetailMapsBtn')+
  w.eventLocationShortcutHtml(event,'eventDetailLocationBtn')+
  '<details class="details-box"><summary>Editar</summary><div class="event-edit-related"><button id="editLinkButton"><span>Vincular</span></button></div></details>';
w.bindCompactRecordActions();
w.bindEventMapActions(event,'eventDetailMapsBtn','eventDetailLocationBtn');
const bar=d.querySelector('.modal-sheet>.record-form-actions--compact');
assert(bar,'La botonera debe estar fija fuera de la zona desplazable');
assert.deepEqual([...bar.children].filter(x=>!x.hidden).map(x=>x.id||x.className),[
 'editItemTopBtn','editLinkButton','eventDetailMapsBtn','record-action-overflow'
]);
d.querySelector('#eventDetailMapsBtn').click();
d.querySelector('#eventDetailLocationBtn').click();
assert.deepEqual(destinations,['TALLERES CIVERA CB','TALLERES CIVERA CB']);

d.querySelector('#editLocation').value='Talleres Civera';
d.querySelector('#editLocationMapBtn').click();
w.bindEventLocationField('#editLocation','#editMapsUrl','#editLocationMapBtn');
d.querySelector('#editLocationMapBtn').click();
assert.equal(destinations.at(-1),'Talleres Civera');

d.querySelector('#editMapsUrl').value='https://maps.app.goo.gl/test';
d.querySelector('#editLocationMapBtn').click();
assert.equal(destinations.at(-1),'https://maps.app.goo.gl/test');
d.querySelector('#editLocation').value='Otro lugar';
d.querySelector('#editLocation').dispatchEvent(new w.Event('input',{bubbles:true}));
assert.equal(d.querySelector('#editMapsUrl').value,'','Cambiar la ubicación no debe mantener un enlace antiguo');
d.querySelector('#editLocationMapBtn').click();
assert.equal(destinations.at(-1),'Otro lugar');
console.log('Mapas: pie fijo y acceso visible, enlaces heredados y campo edición OK');
