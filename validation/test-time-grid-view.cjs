#!/usr/bin/env node
/* Pure/unit regression test. Runs without Android: node validation/test-time-grid-view.cjs */
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'app/src/main/assets/time-grid.js'),'utf8');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const app=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const android=fs.readFileSync(path.join(root,'app/src/main/java/com/jruiz/nexo/MainActivity.java'),'utf8');

const noop=()=>{};
const ctx={
  window:{},document:{},console, Date, Map, Math, Array,
  familyCategoryColor:()=> '#EA580C',
  isVehicleReservation:()=>false, familyCategoryIcon:()=> 'calendar',
  categoryLabel:category=>category, uiIcon:x=>'<svg>'+x+'</svg>',
  fmtTime:ms=>new Date(ms).toISOString().slice(11,16),
  fmtDueItem:()=>'', eventParticipantAvatarsHtml:()=>'', openEventInAgenda:noop,
  itemEffectiveEndAt:i=>i.endAt||i.dueAt,
  esc:v=>String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;'),
  escAttr:v=>String(v).replace(/&/g,'&amp;').replace(/"/g,'&quot;'),
};
vm.runInNewContext(source,ctx,{filename:'time-grid.js'});
const {assignColumns,dayBounds,render}=ctx.window.NexoTimeGrid;
assert.equal(typeof render,'function','time-grid engine must load');
function places(rows){return Array.from(assignColumns(rows.map(([start,end])=>({start,end}))));}
let a=places([[720,840],[723,750],[750,810]]);
assert.deepEqual(a.map(x=>x.columns),[2,2,2],'adjacent shorter appointments reuse columns');
assert.notEqual(a[0].column,a[1].column,'overlaps get separate columns');
assert.equal(a[1].column,a[2].column,'non-overlaps reuse column');
a=places([[600,660],[660,720]]);
assert.deepEqual(a.map(x=>x.columns),[1,1],'adjacent appointments get full width');
a=places([[600,660],[615,645],[630,650]]);
assert.deepEqual(a.map(x=>x.columns),[3,3,3],'three simultaneous appointments');
assert.equal(dayBounds('2026-10-23').end-dayBounds('2026-10-23').start,86400000);
function makeHost(){return {classList:{add:noop},dataset:{},innerHTML:'',querySelectorAll:()=>[]};}
const day=dayBounds('2026-10-23').start;
const host=makeHost();
render(host,'2026-10-23',[
  {id:1,title:'Cita médica',category:'Salud / Médico',hasTime:true,dueAt:day+10*3600000,endAt:day+11*3600000},
  {id:2,title:'Reunión',category:'Trabajo',hasTime:true,dueAt:day+10*3600000,endAt:day+10*3600000+30*60000},
  {id:3,title:'Día completo',category:'Familia',allDay:true,hasTime:false,dueAt:day,endAt:day+23*3600000},
]);
assert.equal((host.innerHTML.match(/class="tgv-event"/g)||[]).length,2);
assert.match(host.innerHTML,/tgv-all-day-event/);
assert.match(host.innerHTML,/08:00/);
assert.match(host.innerHTML,/left:calc\(50% \+ 3px\)/);
assert.match(html,/time-grid\.js/);
assert.match(html,/homeTodayTimeGrid/);
assert.match(app,/NexoTimeGrid\.render\(gridHost,iso,items\)/);
assert.match(android,/moveCalendarEventAsync\(String json, String requestId\)/);
assert.match(android,/service\.updateOccurrence\(move,"single"\)/);
// No presentar dos veces un evento cuya hora de fin cae a medianoche.
const midnightHost=makeHost();
render(midnightHost,'2026-10-24',[
  {id:11,title:'Hasta medianoche',category:'Trabajo',dueAt:day+22*3600000,
    endAt:day+24*3600000,hasTime:true}
]);
assert.equal((midnightHost.innerHTML.match(/class="tgv-event"/g)||[]).length,0);

// Prueba real del controlador táctil y del ciclo asíncrono/Deshacer con un DOM simulado.
const fakeTimers=new Map(),writes=[],messages=[];
let timerId=0,prevented=0,opens=0,refreshes=0,dragButton=null;
const dragCtx={
  ...ctx,
  window:{addEventListener:noop,removeEventListener:noop},
  document:{body:{classList:{add:noop,remove:noop}}},
  navigator:{vibrate:noop},state:{screen:'calendar'},
  setTimeout:fn=>{fakeTimers.set(++timerId,fn);return timerId;},
  clearTimeout:id=>fakeTimers.delete(id),
  NexoNative:{moveCalendarEventAsync:(payload,id)=>writes.push({data:JSON.parse(payload),id})},
  toast:(...args)=>messages.push(args),
  renderCalendar:()=>{refreshes++;},
  renderToday:noop,
  openEventInAgenda:()=>{opens++;}
};
vm.runInNewContext(source,dragCtx,{filename:'time-grid.js'});
const makeButton=()=>{
  const listeners={},style={};
  return {
    dataset:{tgvIndex:'0'},style,
    classList:{add:noop,remove:noop},setAttribute:noop,
    addEventListener:(kind,cb)=>{(listeners[kind] ||= []).push(cb);},
    fire:(kind,e)=>listeners[kind]?.forEach(fn=>fn(e))
  };
};
const dragHost={
  classList:{add:noop},dataset:{},isConnected:false,_tgvItems:[],
  innerHTML:'',
  querySelectorAll:q=>q==='[data-tgv-index]'?[dragButton=makeButton()]:[]
};
const movable={
  id:73,category:'Trabajo',title:'Cita arrastrable',
  dueAt:day+10*3600000,endAt:day+11*3600000,durationMinutes:60,hasTime:true
};
dragHost._tgvItems=[movable];
dragCtx.window.NexoTimeGrid.render(dragHost,'2026-10-23',[movable]);
dragButton.fire('touchstart',{touches:[{clientY:180}]});
for(const [key,fn] of [...fakeTimers]){fakeTimers.delete(key);fn();}
dragButton.fire('touchmove',{touches:[{clientY:217.5}],preventDefault:()=>prevented++});
dragButton.fire('touchend',{changedTouches:[{clientY:217.5}]});
assert.equal(prevented,1);
assert.equal(writes.length,1);
assert.equal(writes[0].data.dueAt,movable.dueAt); // optimistic update
assert.equal(writes[0].data.endAt,writes[0].data.dueAt+3600000);
assert.equal(writes[0].data.dueAt,day+10*3600000+30*60000);
dragCtx.window.NexoTimeGrid.onSaved(writes[0].id,'{"ok":true}');
assert.equal(messages.at(-1)[1],'Deshacer');
messages.at(-1)[2]();
assert.equal(writes.length,2);
assert.equal(writes[1].data.dueAt,day+10*3600000);
dragCtx.window.NexoTimeGrid.onSaved(writes[1].id,'{"ok":true}');
assert.match(messages.at(-1)[0],/deshecho/);
assert.equal(refreshes,2);
assert.equal(movable.dueAt,day+10*3600000,'undo restores event in-memory');

// Un fallo nativo revierte la posición optimista y avisa, sin perder la hora anterior.
dragCtx.window.NexoTimeGrid.render(dragHost,'2026-10-23',[movable]);
dragButton.fire('touchstart',{touches:[{clientY:180}]});
for(const [key,fn] of [...fakeTimers]){fakeTimers.delete(key);fn();}
dragButton.fire('touchmove',{touches:[{clientY:198.75}],preventDefault:noop});
dragButton.fire('touchend',{changedTouches:[{clientY:198.75}]});
assert.equal(writes.length,3);
assert.equal(writes[2].data.dueAt,day+10*3600000+15*60000);
dragCtx.window.NexoTimeGrid.onSaved(writes[2].id,'{"ok":false,"error":"Base de datos ocupada"}');
assert.equal(movable.dueAt,day+10*3600000);
assert.match(messages.at(-1)[0],/Base de datos ocupada/);

// Las series solo pueden enviarse al servicio nativo como "esta aparición".
const rec={
  id:82,seriesId:82,recurring:true,occurrenceAt:day+10*3600000,
  dueAt:day+10*3600000,endAt:day+11*3600000,
  durationMinutes:60,category:'Trabajo',title:'Recurrente',hasTime:true
};
dragCtx.window.NexoTimeGrid.render(dragHost,'2026-10-23',[rec]);
dragButton.fire('touchstart',{touches:[{clientY:180}]});
for(const [key,fn] of [...fakeTimers]){fakeTimers.delete(key);fn();}
dragButton.fire('touchmove',{touches:[{clientY:217.5}],preventDefault:noop});
dragButton.fire('touchend',{changedTouches:[{clientY:217.5}]});
assert.equal(writes.length,4);
assert.equal(writes[3].data.recurring,true);
assert.equal(writes[3].data.seriesId,82);
assert.equal(writes[3].data.occurrenceAt,day+10*3600000);
assert.match(android,/service\.updateOccurrence\(move,"single"\)/);
dragCtx.window.NexoTimeGrid.onSaved(writes[3].id,'{"ok":true}');

console.log('OK: time-grid columns, midnight, drag +15/+30m, recurrence, persistence, undo and rollback');