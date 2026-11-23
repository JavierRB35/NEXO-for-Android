/* Nexo 3.7.94 · Cuatro vistas de calendario sin modificar el almacenamiento nativo. */
(function(){
  'use strict';
  const VIEWS=['month','week','day','agenda'];
  const MINUTE_HEIGHT=0.95;
  const two=n=>String(n).padStart(2,'0');
  const dateIso=d=>d.getFullYear()+'-'+two(d.getMonth()+1)+'-'+two(d.getDate());
  const dateOf=iso=>new Date(iso+'T12:00:00');
  const dayAfter=(iso,days)=>{const d=dateOf(iso);d.setDate(d.getDate()+days);return dateIso(d);};
  const weekStart=iso=>{const d=dateOf(iso);d.setDate(d.getDate()-(d.getDay()+6)%7);return dateIso(d);};
  const dayName=iso=>new Intl.DateTimeFormat('es-ES',{weekday:'short',day:'numeric',month:'short'}).format(dateOf(iso));
  const longDay=iso=>new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long'}).format(dateOf(iso));
  const timed=item=>item.allDay!==true&&item.hasTime!==false;
  const isTask=item=>item.type==='task'||categoryLabel(item.category)==='Tarea / Recado';
  const isDone=item=>item.status==='done'||item.occurrenceStatus==='done';
  const element=id=>document.getElementById(id);
  const eventIdentity=i=>String(i.seriesId||i.id)+':'+String(i.occurrenceAt||0);
  const labelTime=i=>timed(i)?new Intl.DateTimeFormat('es-ES',{hour:'2-digit',minute:'2-digit'}).format(new Date(Number(i.dueAt))):'Sin hora';
  const color=i=>familyCategoryColor(i.category);
  const icon=i=>uiIcon(isTask(i)?'tasks':familyCategoryIcon(i.category));
  function load(){
    try{
      const stored=NexoNative.getCalendarViewPreference();
      if(VIEWS.includes(stored))state.calendarView=stored;
    }catch(_){}
    syncSelector();
  }
  function persist(view){
    try{NexoNative.setCalendarViewPreference(view);}catch(_){}
  }
  function syncSelector(){
    const select=element('calendarViewSelect');
    if(select)select.value=VIEWS.includes(state.calendarView)?state.calendarView:'month';
  }
  function showRegions(view){
    const regions={month:'calendarMonthRegion',week:'calendarWeekView',day:'calendarDayRegion',agenda:'calendarAgendaView'};
    Object.entries(regions).forEach(([name,id])=>{const node=element(id);if(node)node.hidden=name!==view;});
    syncSelector();
  }
  function setView(view,remember=true){
    if(!VIEWS.includes(view))return;
    state.calendarView=view;
    if(remember)persist(view);
    showRegions(view);
    renderCalendar();
  }
  function shift(delta){
    const view=state.calendarView;
    if(view==='month'){changeCalendarMonth(delta);return;}
    const jump=view==='week'?7:view==='agenda'?21:1;
    state.calendarSelected=dayAfter(state.calendarSelected||dateIso(new Date()),delta*jump);
    const d=dateOf(state.calendarSelected);
    state.calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);
    renderCalendar();
  }
  function query(startIso,endIsoExclusive){
    try{
      const items=parseNative(NexoNative.getCalendar(dateOf(startIso).setHours(0,0,0,0),dateOf(endIsoExclusive).setHours(0,0,0,0)-1),[]);
      return Array.isArray(items)?items.filter(i=>Number(i.dueAt)>0):[];
    }catch(_){return [];}
  }
  function inDay(items,iso){
    const result=new Map();
    items.filter(i=>itemCoversIsoDate(i,iso)).forEach(i=>result.set(eventIdentity(i),i));
    return [...result.values()].sort((a,b)=>Number(a.dueAt)-Number(b.dueAt));
  }
  function openDetail(i){
    if(!i)return;
    openEventInAgenda(Number(i.seriesId||i.id),Number(i.occurrenceAt||i.dueAt),!!i.recurring||(i.recurrence&&i.recurrence!=='none'));
  }
  function itemButton(i,extraClass=''){
    return '<button type="button" class="calendar-agenda-item '+extraClass+(isDone(i)?' calendar-item-done':'')+'" data-calendar-item="'+escAttr(eventIdentity(i))+'" style="--calendar-item-color:'+color(i)+'">'+
      '<span class="calendar-agenda-item-time">'+(timed(i)?esc(labelTime(i)):(isTask(i)?'Tarea':'Todo el día'))+'</span>'+
      '<span class="calendar-agenda-item-label">'+icon(i)+'<strong>'+esc(i.title||'Sin título')+'</strong></span></button>';
  }
  function bindItems(root,items){
    const byKey=new Map(items.map(i=>[eventIdentity(i),i]));
    root.querySelectorAll('[data-calendar-item]').forEach(btn=>btn.addEventListener('click',()=>openDetail(byKey.get(btn.dataset.calendarItem))));
  }
  function renderAgenda(items,selected){
    const host=element('calendarAgendaView');
    if(!host)return;
    let markup='';
    for(let j=0;j<21;j++){
      const iso=dayAfter(selected,j),events=inDay(items,iso);
      if(!events.length)continue;
      markup+='<section class="calendar-agenda-group"><h3>'+esc(longDay(iso))+'</h3>'+
        events.map(i=>itemButton(i)).join('')+'</section>';
    }
    host.innerHTML=markup||'<p class="calendar-empty">Sin eventos ni tareas fechadas en estos 21 días.</p>';
    bindItems(host,items);
  }
  function renderWeek(items,selected){
    const host=element('calendarWeekView');
    if(!host)return;
    const start=weekStart(selected),columns=[];
    for(let j=0;j<7;j++){
      const iso=dayAfter(start,j),entries=inDay(items,iso);
      const allDay=entries.filter(i=>!timed(i));
      const timedItems=entries.filter(timed).map(i=>{
        const bounds=new Date(iso+'T00:00:00').getTime(),startAt=Math.max(bounds,Number(i.dueAt)),endAt=Math.min(new Date(dayAfter(iso,1)+'T00:00:00').getTime(),itemEffectiveEndAt(i));
        const date=new Date(startAt),clock=date.getHours()*60+date.getMinutes(),duration=Math.max(20,Math.round((endAt-startAt)/60000));
        return {item:i,start:clock,end:Math.min(1440,clock+duration)};
      });
      const positioned=window.NexoTimeGrid?.assignColumns?window.NexoTimeGrid.assignColumns(timedItems):timedItems.map(x=>({...x,column:0,columns:1}));
      columns.push('<div class="calendar-week-column"><button type="button" class="calendar-week-date'+(iso===selected?' selected':'')+'" data-calendar-date="'+iso+'">'+esc(dayName(iso))+'</button>'+
        '<div class="calendar-week-all-day">'+allDay.map(i=>itemButton(i,'calendar-week-undated')).join('')+'</div>'+
        '<div class="calendar-week-timeline">'+positioned.map(row=>{
          const width=100/(row.columns||1),left=(row.column||0)*width;
          return '<button type="button" class="calendar-week-event'+(isDone(row.item)?' calendar-item-done':'')+'" data-calendar-item="'+escAttr(eventIdentity(row.item))+'" style="top:'+(row.start*MINUTE_HEIGHT)+'px;height:'+Math.max(24,(row.end-row.start)*MINUTE_HEIGHT)+'px;left:'+left+'%;width:'+width+'%;--calendar-item-color:'+color(row.item)+'" title="'+escAttr(row.item.title||'Evento')+'"><small>'+labelTime(row.item)+'</small><strong>'+esc(row.item.title||'Evento')+'</strong></button>';
        }).join('')+'</div></div>');
    }
    host.innerHTML='<div class="calendar-week-track">'+
      '<div class="calendar-week-hours">'+Array.from({length:24},(_,h)=>'<span style="top:'+(132+h*60*MINUTE_HEIGHT)+'px">'+two(h)+':00</span>').join('')+'</div>'+
      columns.join('')+'</div>';
    host.querySelectorAll('[data-calendar-date]').forEach(btn=>btn.addEventListener('click',()=>{state.calendarSelected=btn.dataset.calendarDate;setView('day');}));
    bindItems(host,items);
  }
  function render(){
    const selected=/^\d{4}-\d{2}-\d{2}$/.test(String(state.calendarSelected||''))?state.calendarSelected:dateIso(new Date());
    state.calendarSelected=selected;
    const view=VIEWS.includes(state.calendarView)?state.calendarView:'month';
    showRegions(view);
    if(view==='month')return;
    const start=view==='week'?weekStart(selected):selected;
    const end=dayAfter(start,view==='week'?7:view==='agenda'?21:1);
    const data=query(start,end);
    state.calendarItems=data;
    const title=view==='week'?'Semana del '+dayName(start):view==='day'?longDay(selected):'Agenda · '+dayName(start);
    element('calendarMonthTitle').textContent=title.replace(/^./,c=>c.toUpperCase());
    element('calendarMonthPickerBtn').setAttribute('aria-label','Seleccionar fecha. '+title);
    requestHolidayYears([...new Set([Number(start.slice(0,4)),Number(end.slice(0,4))])]);
    if(view==='day'){
      renderCalendarDay();
    }else if(view==='week'){
      renderWeek(data,selected);
    }else renderAgenda(data,selected);
  }
  function init(){
    load();
    element('calendarViewSelect')?.addEventListener('change',e=>setView(e.target.value));
    ['calendarWeekView','calendarDayRegion','calendarAgendaView'].forEach(id=>{
      const host=element(id);
      if(host&&typeof attachMonthSwipe==='function')attachMonthSwipe(host,()=>shift(-1),()=>shift(1));
    });
  }
  window.NexoCalendarViews={init,load,render,shift,setView,showRegions};
})();
