#!/usr/bin/env node
/* Creación por pulsación larga en la hora vacía, sin guardar automáticamente. */
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'app/src/main/assets/time-grid.js'),'utf8');
const app=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const noop=()=>{};
function makeScenario(){
  const scheduled=new Map(),calls=[],timerIds={next:0};
  const board={
    listeners:{},
    addEventListener(type,handler){(this.listeners[type] ||= []).push(handler);},
    fire(type,event){for(const f of this.listeners[type]||[])f(event);},
    getBoundingClientRect:()=>({top:100}),
    getClientRects:()=>[{}]
  };
  const host={
    id:'calendarDayItems',dataset:{},classList:{add:noop},
    innerHTML:'',isConnected:false,parentElement:null,
    style:{setProperty:noop},
    querySelector:selector=>selector==='.tgv-board'?board:null,
    querySelectorAll:()=>[]
  };
  const ctx={
    window:{},document:{querySelector:()=>null},
    Date,Map,Math,Array,
    setTimeout:fn=>{const id=++timerIds.next;scheduled.set(id,fn);return id;},
    clearTimeout:id=>scheduled.delete(id),
    navigator:{vibrate:noop},
    prepareTimeGridAppointment:(...args)=>calls.push(args),
    itemEffectiveEndAt:i=>i.endAt||i.dueAt,
    familyCategoryColor:()=> '#225fc5',
    isVehicleReservation:()=>false,familyCategoryIcon:()=> 'calendar',
    categoryLabel:()=> 'Trabajo',uiIcon:()=> '',
    fmtDueItem:()=> '',esc:x=>String(x),escAttr:x=>String(x)
  };
  vm.runInNewContext(source,ctx,{filename:'time-grid.js'});
  ctx.window.NexoTimeGrid.render(host,'2026-10-23',[]);
  const target={closest:()=>null};
  const event={closest:()=>({dataset:{tgvIndex:'0'}})};
  const touch=(x,y)=>({clientX:x,clientY:y});
  const emitStart=(x,y,tgt=target)=>board.fire('touchstart',{touches:[touch(x,y)],target:tgt});
  const emitMove=(x,y)=>board.fire('touchmove',{touches:[touch(x,y)]});
  const flush=()=>{for(const [id,fn] of [...scheduled]){scheduled.delete(id);fn();}};
  return {board,calls,scheduled,emitStart,emitMove,flush,event,touch};
}
let test=makeScenario();
test.emitStart(120,100+10.25*60*1.6); // 10:15 dentro de la franja 10:00–11:00
test.flush();
assert.deepEqual(test.calls,[[ '2026-10-23',600,660 ]]);
let blockedClick=false;
test.board.fire('touchend',{preventDefault:()=>{blockedClick=true;}});
assert.equal(blockedClick,true,'El click fantasma se elimina cuando se abrió el editor');
test.board.fire('touchend',{preventDefault:()=>test.calls.push('doble')});
assert.equal(test.calls.length,1,'Una pulsación no abre dos borradores');

// 23:55 genera 23:00–00:00, el editor resuelve fecha de fin posterior.
test=makeScenario();
test.emitStart(120,100+23.9*60*1.6);test.flush();
assert.deepEqual(test.calls,[['2026-10-23',1380,1440]]);

// Pulsación breve, desplazamiento y cancelación no crean nada.
test=makeScenario();
test.emitStart(120,100+8*60*1.6);
test.board.fire('touchend',{preventDefault:()=>{throw Error('Pulsación breve bloqueada');}});
test.flush();assert.equal(test.calls.length,0);
test.emitStart(120,300);test.emitMove(121,330);test.flush();
assert.equal(test.calls.length,0,'Un scroll vertical no debe crear una cita');
test.emitStart(120,300);test.board.fire('touchcancel',{});test.flush();
assert.equal(test.calls.length,0,'Touch cancel no crea citas');

// Una cita existente mantiene su propio gesto de mover/redimensionar.
test.emitStart(120,300,test.event);test.flush();
assert.equal(test.calls.length,0,'La pulsación sobre una tarjeta se ignora');

// Ratón: mantener pulsado crea, soltar antes del umbral no hace nada.
test=makeScenario();
test.board.fire('mousedown',{button:0,clientX:80,clientY:100+14.5*60*1.6,target:{closest:()=>null}});
test.flush();test.board.fire('mouseup',{});
assert.deepEqual(test.calls,[['2026-10-23',840,900]]);
test=makeScenario();
test.board.fire('mousedown',{button:0,clientX:80,clientY:300,target:{closest:()=>null}});
test.board.fire('mousemove',{clientX:120,clientY:300});test.flush();
assert.equal(test.calls.length,0,'Arrastrar con el ratón cancela la creación');

// Formulario real: fechas, hora de inicio y fin, 23:00→00:00 y sin autoguardar.
function extract(name){
  const start=app.indexOf('function '+name+'(');
  assert.ok(start>=0,'Falta función '+name);
  let depth=0,end=-1;
  const begin=app.indexOf('{',start);
  for(let i=begin;i<app.length;i++){
    if(app[i]==='{')depth++;
    else if(app[i]==='}'&&--depth===0){end=i+1;break;}
  }
  assert.ok(end>begin);
  return app.slice(start,end);
}
const fields={};
for(const id of ['captureDate','captureEndDate','captureTime','captureEndTime',
  'captureAllDay','captureSubtitle','captureTitle'])fields[id]={value:'',checked:false,textContent:'',focus:noop};
const done=[],manual=[];
const appCtx={
  Date,Number,String,
  state:{screen:'calendar',homeTimelineVisible:false},
  $:selector=>fields[selector.slice(1)],
  isoDateLocal:d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-'),
  prepareQuickNoteForType:(type,date)=>{done.push([type,date]);appCtx.state.screen='save';},
  markCaptureManual:(...values)=>manual.push(...values),
  syncCaptureAllDay:noop,refreshCaptureAlertEditor:noop,
  setTimeout:noop
};
vm.createContext(appCtx);
vm.runInContext(extract('prepareTimeGridAppointment'),appCtx);
appCtx.prepareTimeGridAppointment('2026-10-23',600,660);
assert.equal(fields.captureDate.value,'2026-10-23');
assert.equal(fields.captureEndDate.value,'2026-10-23');
assert.equal(fields.captureTime.value,'10:00');
assert.equal(fields.captureEndTime.value,'11:00');
assert.equal(fields.captureAllDay.checked,false);
assert.ok(fields.captureSubtitle.textContent.includes('10:00 – 11:00'));
assert.deepEqual(manual,['dateTime']);
assert.deepEqual(done,[['event','2026-10-23']]);
appCtx.state.screen='calendar';
appCtx.prepareTimeGridAppointment('2026-10-23',1380,1440);
assert.equal(fields.captureTime.value,'23:00');
assert.equal(fields.captureEndTime.value,'00:00');
assert.equal(fields.captureEndDate.value,'2026-10-24');
assert.ok(fields.captureSubtitle.textContent.includes('día siguiente'));
assert.equal(done.length,2,'No se guarda ninguna cita sin intervención del usuario');
console.log('OK: franja horaria completa, desplazamiento seguro, eventos existentes y formulario prellenado');
