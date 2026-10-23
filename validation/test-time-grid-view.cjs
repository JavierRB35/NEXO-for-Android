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
console.log('OK: time grid, collisions, all-day and native bindings');