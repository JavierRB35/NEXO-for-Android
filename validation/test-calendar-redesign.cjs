/* Regresión focalizada Nexo 3.7.94: controlador de calendario y tipos conservados. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const script=fs.readFileSync(path.join(root,'app/src/main/assets/calendar-views.js'),'utf8');
const app=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const css=fs.readFileSync(path.join(root,'app/src/main/assets/calendar-redesign.css'),'utf8');
const selected='2026-11-23';
const millis=(iso,h=9)=>new Date(iso+'T'+String(h).padStart(2,'0')+':00:00').getTime();
const data=[
  {id:1,seriesId:1,type:'event',title:'Reunión',category:'Trabajo',dueAt:millis(selected,10),endAt:millis(selected,11),hasTime:true,recurrence:'none'},
  {id:2,seriesId:2,type:'task',title:'Preparar informe',category:'Tarea / Recado',dueAt:millis(selected),endAt:millis(selected),hasTime:false,recurrence:'none'},
  {id:3,seriesId:3,type:'task',title:'Sin fecha',category:'Tarea / Recado',dueAt:0,hasTime:false}
];
const nodes=new Map();
function element(id){
  if(!nodes.has(id))nodes.set(id,{id,innerHTML:'',value:'',hidden:false,handlers:{},attrs:{},
    addEventListener(type,fn){this.handlers[type]=fn;},
    querySelectorAll(){return [];},setAttribute(k,v){this.attrs[k]=v;}});
  return nodes.get(id);
}
const storage=new Map(),asked=[],selectedEvents=[];
const state={calendarView:'month',calendarSelected:selected,calendarItems:[],calendarMonth:new Date(2026,10,1)};
const ctx={window:{NexoTimeGrid:{assignColumns(rows){return rows.map(row=>({...row,column:0,columns:1}));}}},
  state,document:{getElementById:element},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},
  NexoNative:{getCalendar:(start,end)=>{asked.push({start,end});return JSON.stringify(data.filter(i=>i.dueAt>0&&i.dueAt>=start&&i.dueAt<=end));}},
  parseNative:JSON.parse,categoryLabel:v=>v,familyCategoryColor:()=> '#3758cc',familyCategoryIcon:()=> 'calendar',
  uiIcon:()=>'',esc:s=>String(s),escAttr:s=>String(s),
  itemCoversIsoDate:(i,iso)=>new Date(i.dueAt).toLocaleDateString('en-CA')===iso || new Date(i.dueAt).toISOString().slice(0,10)===iso,
  itemEffectiveEndAt:i=>i.endAt||i.dueAt,openEventInAgenda:(...args)=>selectedEvents.push(args),
  renderCalendar:()=>{},renderCalendarDay:()=>{element('calendarDayRegion').innerHTML='day rendered';},
  requestHolidayYears:()=>{},attachMonthSwipe:()=>{}};
vm.createContext(ctx);
vm.runInContext(script,ctx);
const views=ctx.window.NexoCalendarViews;
views.init();
assert.equal(state.calendarView,'month','primera vista mensual');
views.setView('day');
assert.equal(storage.get('nexo.calendar.view'),'day');
assert.equal(element('calendarDayRegion').hidden,false);
assert.equal(element('calendarMonthRegion').hidden,true);
assert.equal(state.calendarItems.length,2,'solo elementos fechados');
assert.equal(element('calendarDayRegion').innerHTML,'day rendered');
views.setView('week');
assert.equal(element('calendarWeekView').hidden,false);
assert.match(element('calendarWeekView').innerHTML,/calendar-week-track/);
assert.match(element('calendarWeekView').innerHTML,/Preparar informe/);
assert.match(element('calendarWeekView').innerHTML,/Reunión/);
views.setView('agenda');
assert.match(element('calendarAgendaView').innerHTML,/Preparar informe/);
assert.match(element('calendarAgendaView').innerHTML,/Reunión/);
assert.doesNotMatch(element('calendarAgendaView').innerHTML,/Sin fecha/);
views.shift(1);
assert.equal(state.calendarSelected,'2026-12-14','agenda avanza 21 días');
assert.equal(element('calendarMonthRegion').hidden,true);
storage.set('nexo.calendar.view','week');
state.calendarView='month';
views.load();
assert.equal(state.calendarView,'week','recupera última vista');
assert.match(app,/type, appointmentCapture:appointment&&type!=='task'/,'el formulario avanzado no transforma tareas en eventos');
assert.match(app,/appointmentCapture:type==='event'/,'el editor rápido conserva el tipo task');
assert.match(app,/state\.calendarQuickOpen=true/,'protección de borrador iniciada');
assert.ok(html.includes('id="homeDailyQuoteText"'));
assert.ok(!html.slice(html.indexOf('id="calendarMonthRegion"'),html.indexOf('data-screen="more"')).includes('calendarDailyQuoteText'));
assert.match(css,/calendar-week-track/);
console.log('OK Nexo 3.7.94: 4 vistas, persistencia, tareas fechadas, frases en Inicio y creación sin conversión');
