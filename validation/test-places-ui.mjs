import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';
const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const section=app.slice(app.indexOf('function placeStatus('),app.indexOf('function parsePlaceVoice('));
assert(section.startsWith('function placeStatus('));
const dom=new JSDOM('<div id="panelTitle"></div><div id="panelSubtitle"></div><div id="panelAction"></div><div id="panelContent"></div><div id="modalBody"></div>',{url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,d=w.document;
let opened=[],scroll=200,requests=0;
let data=[
{id:1,type:'place',title:'Garbí',body:'Vistas',subcategory:'Mirador',locationText:'https://maps.app.goo.gl/x',placeState:'visited',placeVisitedOn:'2026-10-10',placeRating:4,favorite:false,createdAt:10},
{id:2,type:'place',title:'Casa',body:'Cerca de mí',subcategory:'Casa',locationText:'',placeState:'want',placeVisitedOn:'',placeRating:0,favorite:true,createdAt:20},
{id:3,type:'restaurant',title:'Restaurante antiguo',body:'Original',subcategory:'Restaurante',locationText:'',placeState:'',placeVisitedOn:'',placeRating:0,favorite:false,createdAt:5}
];
w.state={places:[],placeSearch:'',placeFilter:'all',placeTypeFilter:'all',placeEditor:null,screen:'panel',lastPanel:'places'};
w.PLACE_LABELS=['Restaurante','Mirador','Casa','Otros'];
w.$=sel=>d.querySelector(sel);w.$$=sel=>[...d.querySelectorAll(sel)];
w.uiIcon=n=>'<svg data-icon="'+n+'"></svg>';w.esc=s=>String(s??'');w.escAttr=w.esc;
w.parseNative=(s,f)=>{try{return JSON.parse(s);}catch{return f;}};
w.toast=()=>{};w.updateFloatingActions=()=>{};
w.requestAnimationFrame=f=>f();w.scrollTo=(x,y)=>{scroll=y;};
Object.defineProperty(w,'scrollY',{get:()=>scroll});
w.openModal=(title,html)=>{d.querySelector('#modalBody').innerHTML=html;};
w.closeModal=()=>{d.querySelector('#modalBody').innerHTML='';return true;};
w.openPanel=()=>{throw Error('No volver a abrir el módulo');};
w.confirmDeleteItem=()=>{};w.isPlaceType=t=>t==='place'||t==='restaurant';
w.NexoNative={
 getCollection:()=>JSON.stringify(data),getItem:id=>JSON.stringify(data.find(x=>x.id===id)||{}),
 savePlace:raw=>{let p=JSON.parse(raw),id=p.id||4,prior=data.find(x=>x.id===id)||{id,type:'place',createdAt:30};data=data.filter(x=>x.id!==id).concat({...prior,...p,id,subcategory:p.label});return JSON.stringify({ok:true,id});},
 openPlaceMap:x=>opened.push(x),shareNexoItem:()=>{},requestPlaceHere:()=>{requests++;}
};
w.eval(section);
w.renderPlacesPanel();
assert.equal(d.querySelector('[data-place-count="all"]').textContent,'3');
assert.equal(d.querySelector('[data-place-count="visited"]').textContent,'1');
assert.equal(d.querySelector('[data-place-count="unclassified"]').textContent,'1');
assert.equal(d.querySelectorAll('[data-place-map]').length,3);
d.querySelector('[data-place-map="1"]').click();assert.equal(opened[0],'https://maps.app.goo.gl/x');
d.querySelector('[data-open-place="1"]').click();assert.equal(d.querySelectorAll('[data-place-rating]').length,5);
d.querySelector('[data-place-rating="5"]').click();assert.equal(data.find(x=>x.id===1).placeRating,5);
assert.equal(data.find(x=>x.id===1).favorite,false);
d.querySelector('#placeDetailFavorite').click();assert.equal(data.find(x=>x.id===1).favorite,true);
assert.equal(data.find(x=>x.id===1).placeRating,5);
d.querySelector('#placeDetailEdit').click();assert(!d.querySelector('[data-place-rating]'));
assert(d.querySelector('#placeHere'));d.querySelector('#placeHere').click();assert.equal(requests,1);
w.onPlaceLocationResult(JSON.stringify({latitude:39.552,longitude:-0.3701,accuracy:12,approximate:false}));
assert.equal(d.querySelector('#placeLocation').value,'39.552000,-0.370100');
d.querySelector('#placeHere').click();
w.onPlaceLocationResult(JSON.stringify({latitude:40,longitude:-1,accuracy:550,approximate:true}));
assert.equal(d.querySelector('#placeLocation').value,'39.552000,-0.370100','Ubicación imprecisa requiere confirmar');
d.querySelector('#placeAcceptApproximate').click();assert.equal(d.querySelector('#placeLocation').value,'40.000000,-1.000000');
d.querySelector('#placeSave').click();assert.equal(data.find(x=>x.id===1).placeRating,5);
assert.equal(data.find(x=>x.id===1).favorite,true);
assert(!d.querySelector('#placeTitle'));
const query=d.querySelector('#placeSearch');query.value='casa';query.dispatchEvent(new w.Event('input'));
d.querySelector('[data-place-filter="want"]').click();
w.openPlaceEditor();d.querySelector('#placeTitle').value='Nuevo parque';d.querySelector('#placeSave').click();
assert.equal(w.state.placeSearch,'casa');assert.equal(w.state.placeFilter,'want');
assert.equal(scroll,200);assert.equal(data.find(x=>x.title==='Nuevo parque').placeState,'want');
console.log('OK: Lugares filtros, Maps, corazón/estrellas, GPS Aquí y vuelta al listado');
