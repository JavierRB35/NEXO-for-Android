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
  renderCalendar:()=>{ctx.window.NexoCalendarViews.render();},renderCalendarDay:()=>{element('calendarDayRegion').innerHTML='day rendered';},
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


/* Recorrido de creación: el selector debe ser clicable y las tareas no convertirse
   en eventos al guardar. Usa NexoNative simulado, sin datos personales. */
const quickSource=app.slice(app.indexOf('/* Editor compacto:'),app.indexOf('function openCreationMenu('));
assert(quickSource.includes('function openCalendarQuickCreate'), 'Falta el editor rápido');
const fields={};
function quickNode(id){
  if(!fields[id])fields[id]={
    id,value:'',checked:false,disabled:false,handlers:{},
    classList:{toggle(){},add(){}},setAttribute(){},focus(){},
    nextElementSibling:{textContent:''},
    addEventListener(event,handler){this.handlers[event]=handler;},
    querySelectorAll(){return ['calendarQuickTitle','calendarQuickDate','calendarQuickStart','calendarQuickEnd','calendarQuickNoTime'].map(quickNode);}
  };
  return fields[id];
}
const kinds=['event','task'].map(quickType=>({
  dataset:{quickType},handlers:{},
  classList:{toggle(){}},setAttribute(){},
  addEventListener(event,handler){this.handlers[event]=handler;}
}));
const writes=[];
let shouldSave=true;
const quickState={screen:'calendar',calendarSelected:selected,calendarMonth:new Date(2026,10,1)};
const quickContext={
  state:quickState,document:{querySelectorAll:selector=>selector==='.calendar-quick-choice button'?kinds:[]},
  $:selector=>quickNode(selector.replace('#','')),
  isoDateLocal:date=>date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0')+'-'+String(date.getDate()).padStart(2,'0'),
  escAttr:String,
  openModal(){quickNode('calendarQuickTitle').value='';quickNode('calendarQuickDate').value=selected;quickNode('calendarQuickStart').value='10:00';quickNode('calendarQuickEnd').value='11:00';quickNode('calendarQuickNoTime').checked=false;},
  prepareQuickNoteForType(){},closeModal(){},syncCaptureAllDay(){},markCaptureManual(){},
  dateTimeToMsInZone:(date,time)=>new Date(date+'T'+(time||'00:00')+':00').getTime(),
  eventEndFromFields:(date,time,endDate,endTime,allDay)=>allDay?new Date(endDate+'T00:00:00').getTime()+86400000:new Date(endDate+'T'+endTime+':00').getTime(),
  addIsoDays:(date,days)=>{const d=new Date(date+'T12:00:00');d.setDate(d.getDate()+days);return d.toISOString().slice(0,10);},
  parseNative:JSON.parse,
  NexoNative:{saveCapture:payload=>{writes.push(JSON.parse(payload));return JSON.stringify(shouldSave?{ok:true,id:42}:{ok:false,error:'fallo simulado'});}},
  renderCalendar(){},toast(){},setTimeout:handler=>handler()
};
vm.createContext(quickContext);
vm.runInContext(quickSource,quickContext);
quickContext.openCalendarQuickCreate(selected);
quickNode('calendarQuickTitle').value='Preparar documentación';
kinds[1].handlers.click();
assert(quickNode('calendarQuickNoTime').checked, 'Seleccionar Tarea debe ocultar el horario');
quickNode('calendarQuickSave').handlers.click();
assert.equal(writes[0].type,'task');
assert.equal(writes[0].appointmentCapture,false);
assert.equal(writes[0].hasTime,false);
quickContext.openCalendarQuickCreate(selected,600,660);
quickNode('calendarQuickTitle').value='Reunión';
quickNode('calendarQuickSave').handlers.click();
assert.equal(writes[1].type,'event');
assert.equal(writes[1].hasTime,true);
assert.equal(writes[1].endAt-writes[1].dueAt,3600000);
quickContext.openCalendarQuickCreate(selected);
quickNode('calendarQuickTitle').value='Fallo de guardado';
shouldSave=false;
quickNode('calendarQuickSave').handlers.click();
assert.equal(quickState.calendarQuickOpen,true,'Error de escritura no debe cerrar el borrador');
assert.equal(quickNode('calendarQuickSave').disabled,false,'Debe permitir reintentar el guardado');
console.log('OK: selección Evento/Tarea, guardado nativo simulado y recuperación tras error');
