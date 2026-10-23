/* Nexo 3.7.56 · Agenda diaria por horas. Solo Inicio/HOY y Calendario. */
(function(){
  'use strict';
  const PX_PER_MINUTE=1.25;
  const SNAP_MINUTES=15;
  const pending=new Map();
  let nextRequest=0;
  let writing=false;

  function two(n){return String(n).padStart(2,'0');}
  function hhmm(n){return two(Math.floor(n/60))+':'+two(n%60);}
  function isTimed(item){return !!Number(item.dueAt)&&item.allDay!==true&&item.hasTime!==false;}
  function isRecurring(item){return !!item.recurring||(!!item.recurrence&&item.recurrence!=='none');}
  function eventKey(item){return String(item.seriesId||item.id)+':'+String(item.occurrenceAt||0);}
  function dayBounds(iso){
    const d=new Date(iso+'T00:00:00');
    return {start:d.getTime(),end:new Date(d.getFullYear(),d.getMonth(),d.getDate()+1).getTime()};
  }
  function clockMinutes(ms){const d=new Date(ms);return d.getHours()*60+d.getMinutes();}
  function validColor(item){return familyCategoryColor(isVehicleReservation(item)?'Vehículos / ITV':item.category);}
  function visibleInDay(item,bounds){
    return Number(item.dueAt)<bounds.end&&itemEffectiveEndAt(item)>=bounds.start;
  }
  function itemDuration(item){
    const actual=Math.max(0,itemEffectiveEndAt(item)-Number(item.dueAt));
    return actual||0;
  }
  function segment(item,bounds){
    const from=Math.max(bounds.start,Number(item.dueAt));
    const endValue=Math.max(Number(item.dueAt),itemEffectiveEndAt(item));
    const until=Math.min(bounds.end,endValue);
    const start=from<=bounds.start?0:clockMinutes(from);
    const finish=until>=bounds.end?1440:clockMinutes(until);
    return {item,start,end:Math.max(start+15,finish),duration:itemDuration(item)};
  }
  /* Interval partitioning: solo las citas realmente simultaneas comparten columnas. */
  function assignColumns(rows){
    rows.sort((a,b)=>a.start-b.start||b.end-a.end);
    let group=[],lastEnd=-1;
    function distribute(){
      if(!group.length)return;
      const ends=[];
      group.forEach(entry=>{
        let c=ends.findIndex(end=>end<=entry.start);
        if(c<0){c=ends.length;ends.push(entry.end);}
        else ends[c]=entry.end;
        entry.column=c;
      });
      group.forEach(entry=>entry.columns=ends.length);
      group=[];
    }
    rows.forEach(entry=>{
      if(group.length&&entry.start>=lastEnd){distribute();lastEnd=-1;}
      group.push(entry);
      lastEnd=Math.max(lastEnd,entry.end);
    });
    distribute();
    return rows;
  }
  function subtitle(item){
    const status=item.status==='done'||item.occurrenceStatus==='done'?'Completada':
      item.status==='cancelled'||item.occurrenceStatus==='cancelled'?'Cancelada':
      item.status==='postponed'||item.occurrenceStatus==='postponed'?'Pospuesta':'';
    const raw=String(item.subcategory||'').trim();
    const subcategory=raw==='vehicle_reservation'?'Reserva de coche':raw.replace(/_/g,' ');
    return [status,subcategory||categoryLabel(item.category)].filter(Boolean).join(' · ');
  }
  function fullLabel(item){
    return [item.title,fmtDueItem(item),categoryLabel(item.category),subtitle(item),
      item.locationText||item.mapsUrl||'',item.body||''].filter(Boolean).join(' · ');
  }
  function blockContents(item,row){
    const start=fmtTime(item.dueAt);
    const end=itemEffectiveEndAt(item)>Number(item.dueAt)?fmtTime(itemEffectiveEndAt(item)):'';
    const icon=isVehicleReservation(item)?'car':familyCategoryIcon(item.category);
    const loc=String(item.locationText||item.mapsUrl||'').trim();
    return '<span class="tgv-event-time">'+esc(start)+(end?' – '+esc(end):'')+'</span>'+
      '<span class="tgv-event-title">'+uiIcon(icon)+'<strong>'+esc(item.title||'Evento')+'</strong></span>'+
      '<span class="tgv-event-status">'+esc(subtitle(item))+'</span>'+
      (loc?'<span class="tgv-event-location">'+uiIcon('pin')+esc(loc)+'</span>':'')+
      (item.body?'<span class="tgv-event-description">'+esc(item.body)+'</span>':'')+
      eventParticipantAvatarsHtml(item,'tgv-avatars');
  }
  function render(host,iso,rawItems){
    if(!host||!iso)return;
    const bounds=dayBounds(iso);
    const items=(Array.isArray(rawItems)?rawItems:[]).filter(item=>item?.dueAt&&visibleInDay(item,bounds));
    const allDay=items.filter(item=>!isTimed(item));
    const timed=assignColumns(items.filter(isTimed).map(item=>segment(item,bounds)));
    const minimum=timed.length?Math.min(480,Math.floor(Math.min(...timed.map(i=>i.start))/60)*60):480;
    const maximum=timed.length?Math.max(1200,Math.ceil(Math.max(...timed.map(i=>i.end))/60)*60):1200;
    const first=Math.max(0,minimum),last=Math.min(1440,Math.max(first+60,maximum));
    const total=(last-first)*PX_PER_MINUTE;
    const hours=[];
    for(let m=first;m<=last;m+=60){
      hours.push('<div class="tgv-hour" style="top:'+(m-first)*PX_PER_MINUTE+'px"><span>'+hhmm(m)+'</span></div>');
    }
    const cards=timed.map((row,index)=>{
      const item=row.item,top=(row.start-first)*PX_PER_MINUTE,
        height=Math.max(26,(Math.min(last,row.end)-Math.max(first,row.start))*PX_PER_MINUTE),
        width=100/row.columns,left=row.column*width,
        accessible='Abrir '+fullLabel(item);
      return '<button type="button" class="tgv-event'+(writing?' tgv-event--busy':'')+'"'+
        ' data-tgv-index="'+index+'" style="top:'+top+'px;height:'+height+'px;left:calc('+left+'% + 3px);width:calc('+width+'% - 6px);--tgv-accent:'+validColor(item)+'"'+
        ' title="'+escAttr(fullLabel(item))+'" aria-label="'+escAttr(accessible)+'">'+blockContents(item,row)+'</button>';
    }).join('');
    host.classList.add('tgv-host');
    host.dataset.tgvDay=iso;
    host.innerHTML=(allDay.length?'<div class="tgv-all-day"><span class="tgv-all-day-label">Todo el día</span>'+allDay.map((item,index)=>
      '<button type="button" class="tgv-all-day-event" data-tgv-allday="'+index+'" style="--tgv-accent:'+validColor(item)+'">'+uiIcon(isVehicleReservation(item)?'car':familyCategoryIcon(item.category))+
      '<span><strong>'+esc(item.title||'Evento')+'</strong><small>'+esc(subtitle(item))+
      ((item.locationText||item.mapsUrl)?' · '+esc(item.locationText||item.mapsUrl):'')+'</small></span></button>').join('')+'</div>':'')+
      '<div class="tgv-board" style="height:'+total+'px" role="group" aria-label="Agenda por horas">'+
      hours.join('')+'<div class="tgv-event-layer" style="height:'+total+'px">'+cards+'</div>'+
      (!timed.length?'<div class="tgv-no-events">No hay eventos con hora para este día.</div>':'')+
      '</div><p class="tgv-help">Mantén pulsada una cita para cambiar la hora en intervalos de 15 minutos.</p>';
    host.querySelectorAll('[data-tgv-allday]').forEach(btn=>btn.addEventListener('click',()=>{
      const item=allDay[Number(btn.dataset.tgvAllday)];
      if(item)openEventInAgenda(Number(item.seriesId||item.id),Number(item.occurrenceAt||item.dueAt),isRecurring(item));
    }));
    host.querySelectorAll('[data-tgv-index]').forEach(btn=>{
      const row=timed[Number(btn.dataset.tgvIndex)];
      attachDrag(btn,row,iso,host);
    });
  }
  function notifyChange(){
    if(state.screen==='calendar')renderCalendar();
    else if(state.screen==='today')renderToday();
  }
  function submitMove(row,iso,host,newStart,newEnd){
    if(writing)return;
    const item=row.item,originalStart=Number(item.dueAt),originalEnd=Number(itemEffectiveEndAt(item));
    if(newStart===originalStart)return;
    const recurrent=isRecurring(item),seriesId=Number(item.seriesId||item.id),
      occurrenceAt=Number(item.occurrenceAt||item.dueAt),status=String(item.occurrenceStatus||'active');
    const request={seriesId,occurrenceAt,id:Number(item.id||seriesId),recurring:recurrent,
      dueAt:newStart,endAt:newEnd,durationMinutes:Number(item.durationMinutes)||Math.round(itemDuration(item)/60000),
      hasTime:true,allDay:false,occurrenceState:status==='deleted'?'active':status};
    const requestId='tgv-'+(++nextRequest);
    const oldItem={...item},ctx={request,item,oldItem,oldStart:originalStart,oldEnd:originalEnd,newStart,iso,host,isUndo:false};
    writing=true;pending.set(requestId,ctx);
    item.dueAt=newStart;item.endAt=newEnd;
    if(host.isConnected&&host.dataset.tgvDay===iso)render(host,iso,host._tgvItems||[]);
    try {
      if(typeof NexoNative.moveCalendarEventAsync==='function'){
        NexoNative.moveCalendarEventAsync(JSON.stringify(request),requestId);
      }else{
        // Compatibilidad con las pruebas JS sin puente Android nativo.
        setTimeout(()=>{
          let result;
          try{result=recurrent?NexoNative.updateOccurrence(JSON.stringify(request),'single'):NexoNative.updateItem(JSON.stringify(request));}
          catch(_){result='{"ok":false}';}
          onSaved(requestId,result);
        },0);
      }
    }catch(_){onSaved(requestId,'{"ok":false}');}
  }
  function onSaved(requestId,json){
    const ctx=pending.get(requestId);
    if(!ctx)return;
    pending.delete(requestId);writing=false;
    let result={ok:false};
    try{result=typeof json==='string'?JSON.parse(json):json||result;}catch(_){}
    if(!result.ok){
      ctx.item.dueAt=ctx.oldStart;ctx.item.endAt=ctx.oldEnd;
      notifyChange();
      toast(result.error||'No se pudo actualizar la hora. Se ha restaurado la cita.','',null,5000);
      return;
    }
    notifyChange();
    if(ctx.isUndo){toast('Cambio de hora deshecho');return;}
    const newTime=fmtTime(ctx.newStart),saved=ctx.request;
    toast('Hora actualizada a las '+newTime,'Deshacer',()=>{
      if(writing){toast('Hay otra actualización en curso.');return;}
      const undo={...saved,dueAt:ctx.oldStart,endAt:ctx.oldEnd};
      const id='tgv-'+(++nextRequest);
      writing=true;
      pending.set(id,{...ctx,request:undo,newStart:ctx.oldStart,oldStart:ctx.newStart,oldEnd:ctx.request.endAt,isUndo:true});
      try{
        if(typeof NexoNative.moveCalendarEventAsync==='function')NexoNative.moveCalendarEventAsync(JSON.stringify(undo),id);
        else setTimeout(()=>{
          const r=undo.recurring?NexoNative.updateOccurrence(JSON.stringify(undo),'single'):NexoNative.updateItem(JSON.stringify(undo));
          onSaved(id,r);
        },0);
      }catch(_){onSaved(id,'{"ok":false}');}
    },7000);
  }
  function attachDrag(btn,row,iso,host){
    const item=row.item;
    btn.addEventListener('click',e=>{
      if(btn._tgvSuppressUntil&&Date.now()<btn._tgvSuppressUntil){e.preventDefault();return;}
      if(writing)return;
      openEventInAgenda(Number(item.seriesId||item.id),Number(item.occurrenceAt||item.dueAt),isRecurring(item));
    });
    if(writing)return;
    // No mover desde un día intermedio ni desplazar citas que abarcan varios días.
    const bounds=dayBounds(iso);
    if(Number(item.dueAt)<bounds.start||itemEffectiveEndAt(item)>bounds.end||itemDuration(item)>=86400000)return;
    let timer=0,active=false,startY=0,currentY=0,lastTouch=0,mouseEnabled=false;
    const heightMs=itemDuration(item),originalStart=Number(item.dueAt);
    const startMinutes=clockMinutes(originalStart);
    const maxMinutes=Math.max(0,1440-Math.ceil(heightMs/60000));
    const bounded=mins=>Math.max(0,Math.min(maxMinutes,Math.round(mins/SNAP_MINUTES)*SNAP_MINUTES));
    const move=y=>{
      if(!active)return;
      currentY=y;
      const next=bounded(startMinutes+(y-startY)/PX_PER_MINUTE);
      btn.style.transform='translateY('+((next-startMinutes)*PX_PER_MINUTE)+'px)';
      btn.classList.add('tgv-event--dragging');
      btn.setAttribute('aria-label','Nueva hora '+hhmm(next));
    };
    const begin=()=>{
      timer=0;active=true;btn._tgvSuppressUntil=Date.now()+1000;
      btn.classList.add('tgv-event--dragging');document.body.classList.add('tgv-drag-active');
      try{navigator.vibrate?.(12);}catch(_){}
    };
    const stop=(y,cancel=false)=>{
      clearTimeout(timer);timer=0;
      if(!active)return;
      active=false;btn._tgvSuppressUntil=Date.now()+900;
      btn.classList.remove('tgv-event--dragging');document.body.classList.remove('tgv-drag-active');
      const next=bounded(startMinutes+(y-startY)/PX_PER_MINUTE);
      btn.style.transform='';
      if(cancel||next===startMinutes)return;
      const newDate=new Date(originalStart);
      const originalDate=newDate.toDateString();
      newDate.setHours(Math.floor(next/60),next%60,0,0);
      if(newDate.toDateString()!==originalDate)return;
      submitMove(row,iso,host,newDate.getTime(),newDate.getTime()+heightMs);
    };
    btn.addEventListener('contextmenu',e=>e.preventDefault());
    btn.addEventListener('touchstart',e=>{
      if(e.touches.length!==1||writing)return;
      lastTouch=Date.now();startY=e.touches[0].clientY;currentY=startY;
      clearTimeout(timer);timer=setTimeout(begin,300);
    },{passive:true});
    btn.addEventListener('touchmove',e=>{
      if(e.touches.length!==1)return;
      const y=e.touches[0].clientY;
      if(!active){
        if(Math.abs(y-startY)>9){clearTimeout(timer);timer=0;}
        return;
      }
      e.preventDefault();move(y);
    },{passive:false});
    btn.addEventListener('touchend',e=>{
      if(!active){clearTimeout(timer);timer=0;return;}
      stop(e.changedTouches[0]?.clientY??currentY);
    });
    btn.addEventListener('touchcancel',()=>stop(currentY,true));
    const onMouseMove=e=>{
      if(!mouseEnabled)return;
      if(!active&&Math.abs(e.clientY-startY)>9){clearTimeout(timer);timer=0;}
      if(active){e.preventDefault();move(e.clientY);}
    };
    const onMouseUp=e=>{
      window.removeEventListener('mousemove',onMouseMove);
      window.removeEventListener('mouseup',onMouseUp);
      if(!mouseEnabled)return;
      mouseEnabled=false;stop(e.clientY);
    };
    btn.addEventListener('mousedown',e=>{
      if(e.button!==0||Date.now()-lastTouch<800||writing)return;
      mouseEnabled=true;startY=e.clientY;currentY=startY;
      clearTimeout(timer);timer=setTimeout(begin,300);
      window.addEventListener('mousemove',onMouseMove);
      window.addEventListener('mouseup',onMouseUp);
    });
  }
  function renderHome(){
    const host=document.getElementById('homeTodayTimeGrid');
    const section=document.getElementById('homeTodayTimeline');
    if(!host||!section||!state.homeTimelineVisible)return;
    const iso=isoDateLocal(new Date()),bounds=dayBounds(iso);
    let items=[];
    try{items=parseNative(NexoNative.getCalendar(bounds.start,bounds.end-1),[]);}catch(_){}
    if(!Array.isArray(items))items=[];
    host._tgvItems=items;
    section.querySelector('[data-tgv-home-title]').textContent='Hoy · '+new Intl.DateTimeFormat('es-ES',{weekday:'short',day:'numeric',month:'short'}).format(new Date(iso+'T12:00:00'));
    section.querySelector('[data-tgv-home-count]').textContent=items.length+' evento'+(items.length===1?'':'s');
    render(host,iso,items);
  }
  function showHome(){
    state.homeTimelineVisible=true;
    const screen=document.querySelector('.screen[data-screen="today"]');
    screen?.classList.add('home-timeline-mode');
    document.getElementById('homeTodayTimeline')?.classList.remove('hidden');
    renderHome();
    window.scrollTo(0,0);
  }
  function hideHome(){
    state.homeTimelineVisible=false;
    document.querySelector('.screen[data-screen="today"]')?.classList.remove('home-timeline-mode');
    document.getElementById('homeTodayTimeline')?.classList.add('hidden');
    if(state.screen==='today')renderToday();
    window.scrollTo(0,0);
  }
  window.NexoTimeGrid={render,renderHome,showHome,hideHome,onSaved,assignColumns,dayBounds};
})();