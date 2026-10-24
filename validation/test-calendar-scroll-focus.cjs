#!/usr/bin/env node
/* Regresión: al entrar en Calendario, ver el mes; al abrir/cerrar ficha, conservar la agenda. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const base=path.resolve(__dirname,'..');
const grid=fs.readFileSync(path.join(base,'app/src/main/assets/time-grid.js'),'utf8');
const css=fs.readFileSync(path.join(base,'app/src/main/assets/time-grid.css'),'utf8');
const app=fs.readFileSync(path.join(base,'app/src/main/assets/app.js'),'utf8');
const noop=()=>{};
function scenario(calendar,allDay){
  const frames=[],windowScrolls=[],hostScrolls=[],styles=[];
  const win={scrollY:0,requestAnimationFrame:f=>frames.push(f),scrollTo:pos=>windowScrolls.push(pos)};
  const doc={querySelector:sel=>sel==='.topbar'?{getBoundingClientRect:()=>({height:60})}:null};
  const ctx={window:win,document:doc,Date,Map,Math,Array,
    getComputedStyle:el=>({overflowY:el.id==='calendarDayItems'?'auto':'visible'}),
    familyCategoryColor:()=> '#EA580C',isVehicleReservation:()=>false,
    familyCategoryIcon:()=> 'calendar',categoryLabel:()=> 'Trabajo',
    uiIcon:()=> '<svg></svg>',fmtDueItem:()=> '',
    itemEffectiveEndAt:i=>i.endAt||i.dueAt,
    esc:v=>String(v),escAttr:v=>String(v)
  };
  vm.runInNewContext(grid,ctx,{filename:'time-grid.js'});
  const host={
    id:calendar?'calendarDayItems':'homeTodayTimeGrid',
    classList:{add:noop},dataset:{},innerHTML:'',
    isConnected:true,parentElement:null,
    scrollTop:0,clientHeight:420,scrollHeight:2400,
    getBoundingClientRect:()=>({top:380}),
    scrollTo:position=>{hostScrolls.push(position);host.scrollTop=position.top;},
    style:{setProperty:(name,value)=>styles.push([name,value])},
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='.tgv-board'
      ?{getBoundingClientRect:()=>({top:520}),getClientRects:()=>[{}]}
      :selector==='.tgv-all-day'&&allDay
        ?{getBoundingClientRect:()=>({height:110})}:null
  };
  const start=new Date('2026-10-23T00:00:00').getTime();
  const items=[{id:1,title:'Primera cita',category:'Trabajo',hasTime:true,
    dueAt:start+10*3600000,endAt:start+11*3600000}];
  if(allDay)items.push({id:2,title:'Todo el día',category:'Familia',
    hasTime:false,allDay:true,dueAt:start,endAt:start+23*3600000});
  ctx.window.NexoTimeGrid.render(host,'2026-10-23',items);
  assert.equal(frames.length,1,'El foco espera al primer frame');
  frames.shift()();
  assert.equal(frames.length,1,'El foco espera al segundo frame');
  frames.shift()();
  return {ctx,host,frames,windowScrolls,hostScrolls,styles,items};
}
const month=scenario(true,true);
assert.equal(month.windowScrolls.length,0,'No se debe desplazar la pantalla del mes');
assert.equal(month.hostScrolls.length,1,'Solo se desplaza la agenda interior');
assert.ok(month.hostScrolls[0].top>700,'La primera cita está arriba en la agenda');
assert.deepEqual(month.styles,[['--tgv-top-offset','0px']],
  'Todo el día queda fijo dentro de la agenda');
assert.match(month.host.innerHTML,/tgv-all-day-event/);
const retained=month.host.scrollTop;
month.ctx.window.NexoTimeGrid.render(month.host,'2026-10-23',month.items);
assert.equal(month.frames.length,0,'Reabrir ficha no debe volver a enfocar la agenda');
assert.equal(month.host.scrollTop,retained,'Conservar posición en la agenda');
const home=scenario(false,true);
assert.equal(home.windowScrolls.length,1,'Inicio mantiene su scroll habitual');
assert.equal(home.hostScrolls.length,0,'Inicio no se convierte en agenda interior');
assert.match(css,/#calendarDayItems\.tgv-host\{[^}]*height:min\(55dvh,540px\);[^}]*overflow-y:auto;/s);
assert.match(app,/resetCalendarToToday\(\);navigate\('calendar',\{root:true,scrollY:0\}\)/);
assert.match(app,/screen==='calendar'&&previousScreen!=='calendar'&&options\.scrollY===undefined\?0:moved\.scrollY/);
assert.match(app,/state\.modalReturnFocus\.focus\(\{preventScroll:true\}\)/);
console.log('OK: calendario arriba, agenda enfocada, todo el día fijo y retorno sin salto');
