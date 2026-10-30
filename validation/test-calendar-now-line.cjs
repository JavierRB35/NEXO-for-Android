#!/usr/bin/env node
/* Regresión: línea del instante actual y apertura centrada en la hora real. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'..');
const js=fs.readFileSync(path.join(root,'app/src/main/assets/time-grid.js'),'utf8');
const css=fs.readFileSync(path.join(root,'app/src/main/assets/time-grid.css'),'utf8');
const themes=fs.readFileSync(path.join(root,'app/src/main/assets/nexo-themes.css'),'utf8');
const noop=()=>{};
let current=new Date(2026,9,30,12,34,30); // Hora local, sin dependencia del reloj CI.
class FakeDate extends Date{
  constructor(...args){if(args.length)super(...args);else super(current.getTime());}
  static now(){return current.getTime();}
}
const frames=[],intervals=[],visibility={};
const hosts=[];
const context={
  window:{requestAnimationFrame:fn=>frames.push(fn),scrollTo:noop,scrollY:0},
  document:{
    hidden:false,
    querySelector:s=>s==='.topbar'?{getBoundingClientRect:()=>({height:64})}:null,
    querySelectorAll:s=>s==='.tgv-host[data-tgv-day]'?hosts:[],
    addEventListener:(name,fn)=>visibility[name]=fn
  },
  getComputedStyle:el=>({overflowY:el.id==='calendarDayItems'?'auto':'visible'}),
  setInterval:(fn,ms)=>{intervals.push({fn,ms});return intervals.length;},
  Date:FakeDate,Map,Math,Array,
  familyCategoryColor:()=>'#18A48A',isVehicleReservation:()=>false,
  familyCategoryIcon:()=>'calendar',categoryLabel:()=> 'Trabajo',uiIcon:()=> '',
  fmtDueItem:()=>'',itemEffectiveEndAt:item=>item.endAt||item.dueAt,
  esc:v=>String(v),escAttr:v=>String(v)
};
vm.runInNewContext(js,context,{filename:'time-grid.js'});
assert.equal(intervals.length,1,'Una actualización única, no un timer por evento');
assert.equal(intervals[0].ms,30000,'La línea se actualiza periódicamente');
assert.equal(typeof visibility.visibilitychange,'function');
function host(){
  const scrolls=[],label={textContent:'12:34'};
  const marker={style:{},attrs:{},setAttribute(k,v){this.attrs[k]=v;},
    querySelector:s=>s==='.tgv-now-label'?label:null};
  const board={getBoundingClientRect:()=>({top:520}),getClientRects:()=>[{}]};
  const h={
    id:'calendarDayItems',dataset:{},classList:{add:noop},innerHTML:'',
    parentElement:null,isConnected:true,scrollTop:0,scrollHeight:2500,clientHeight:420,
    getBoundingClientRect:()=>({top:380}),
    style:{setProperty:noop},
    scrollTo:p=>{scrolls.push(p);h.scrollTop=p.top;},
    querySelector:s=>s==='.tgv-board'?board:
      s==='.tgv-now-marker'&&h.innerHTML.includes('tgv-now-marker')?marker:null,
    querySelectorAll:()=>[]
  };
  hosts.push(h);
  return {h,scrolls,marker,label};
}
function flushFrames(){while(frames.length)frames.shift()();}
const today=host();
const nowISO='2026-10-30';
const later=new FakeDate('2026-10-30T17:00:00').getTime();
context.window.NexoTimeGrid.render(today.h,nowISO,[
  {id:9,title:'Cita posterior',dueAt:later,endAt:later+3600000,hasTime:true,category:'Trabajo'}
]);
assert.match(today.h.innerHTML,/class="tgv-now-marker"/,'La línea está presente hoy');
assert.match(today.h.innerHTML,/Hora actual: 12:34/);
assert.match(today.h.innerHTML,/tgv-now-label">12:34<\/span>/);
assert.match(today.h.innerHTML,/tgv-event/,'Las citas siguen presentes');
assert.equal(today.scrolls.length,0,'No desplazar hasta terminar la navegación');
flushFrames();
assert.equal(today.scrolls.length,1);
const expected=140+(12*60+34+30/60-45)*1.6-12;
assert.ok(Math.abs(today.scrolls[0].top-expected)<1,'En hoy, dejar 45 min encima de la línea');
assert.ok(today.scrolls[0].top<1500,'No enfocar la cita de las 17:00');
const retained=today.h.scrollTop;
context.window.NexoTimeGrid.render(today.h,nowISO,[]);
assert.equal(frames.length,0,'Actualizar citas no reposiciona de nuevo');
assert.equal(today.h.scrollTop,retained);
assert.match(today.h.innerHTML,/tgv-now-marker/,'La raya aparece incluso sin citas horarias');

const other=host();
const yesterday=new FakeDate('2026-10-29T10:00:00').getTime();
context.window.NexoTimeGrid.render(other.h,'2026-10-29',[
  {id:8,title:'Cita pasada',dueAt:yesterday,endAt:yesterday+3600000,hasTime:true,category:'Trabajo'}
]);
assert.doesNotMatch(other.h.innerHTML,/tgv-now-marker/,'No dibujar hora real en otros días');
flushFrames();
assert.ok(other.scrolls[0].top>900,'En otra fecha sigue centrando la primera cita');

current=new Date(2026,9,30,12,36,15);
intervals[0].fn();
assert.equal(today.marker.style.top,((12*60+36+15/60)*1.6)+'px');
assert.equal(today.label.textContent,'12:36');
assert.equal(today.marker.attrs['aria-label'],'Hora actual: 12:36');
assert.equal(today.scrolls.length,1,'El avance del reloj NO fuerza scroll');
current=new Date(2026,9,30,12,37,10);
visibility.visibilitychange();
assert.equal(today.label.textContent,'12:37','Actualiza al volver del segundo plano');
current=new Date(2026,9,31,0,1,0);
intervals[0].fn();
assert.equal(today.marker.style.display,'none','No mostrar la línea de ayer tras medianoche');

assert.match(css,/\.tgv-now-marker\{[\s\S]*?pointer-events:none/,'Nunca interceptar gestos táctiles');
assert.match(css,/\.tgv-now-marker\{[\s\S]*?border-top:2px solid/);
assert.match(css,/\.tgv-now-label/);
assert.match(themes,/data-nexo-theme="noche"\]\{[\s\S]*?--tgv-now-color:#FB7185/);
assert.match(js,/function attachDrag\(/,'Se conserva el arrastre');
assert.match(js,/function attachEmptySlotCreate\(/,'Se conserva crear cita por pulsación larga');
console.log('OK: línea actual, 45 min de contexto, agenda histórica intacta, avance del reloj y gestos libres');
