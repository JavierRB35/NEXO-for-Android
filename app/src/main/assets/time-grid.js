/* Nexo 3.7.62 · Agenda diaria completa, movimiento y cambio de duración. */
(function(){
  'use strict';
  const PX_PER_MINUTE=1.6;
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
    // El fin del intervalo es exclusivo: una cita acabada a las 00:00 no reaparece mañana.
    return Number(item.dueAt)<bounds.end&&
      (itemEffectiveEndAt(item)>bounds.start||Number(item.dueAt)===bounds.start);
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
      item.locationText||item.mapsUrl||'',item.body||'',
      item.recurrence&&item.recurrence!=='none'?'Recurrente':'',
      item.hasReminder?'Con aviso':'',Number(item.linkCount)>0?item.linkCount+' vínculos':''].filter(Boolean).join(' · ');
  }
  function blockContents(item,row,height,width){
    const icon=isVehicleReservation(item)?'car':familyCategoryIcon(item.category);
    // Lo demás se consulta en la ficha completa al pulsar: sin avatares,
    // ubicación, descripción, avisos ni marcadores de recurrencia.
    const optionalChip=height>=100&&width>34?'<span class="tgv-event-status">'+esc(subtitle(item))+'</span>':'';
    return '<span class="tgv-event-time">'+hhmm(row.start)+' – '+hhmm(row.end)+'</span>'+
      '<span class="tgv-event-title">'+uiIcon(icon)+'<strong>'+esc(item.title||'Evento')+'</strong></span>'+
      optionalChip;
  }
  function minuteToTimestamp(iso,minute){
    const d=new Date(iso+'T00:00:00');
    d.setHours(Math.floor(minute/60),minute%60,0,0);
    return d.getTime();
  }
  // Tres operaciones distintas: mover (duración constante), cambiar inicio
  // (final constante), o cambiar fin (inicio constante). Día completo 00–24.
  function gestureRange(mode,start,end,delta){
    const snap=n=>Math.round(n/SNAP_MINUTES)*SNAP_MINUTES;
    const clamp=(n,min,max)=>Math.max(min,Math.min(max,n));
    if(mode==='start')return {start:clamp(snap(start+delta),0,end-SNAP_MINUTES),end};
    if(mode==='end')return {start,end:clamp(snap(end+delta),start+SNAP_MINUTES,1440)};
    const duration=end-start;
    const movedStart=clamp(snap(start+delta),0,1440-duration);
    return {start:movedStart,end:movedStart+duration};
  }
  function chooseGesture(btn,y,target){
    const direct=target?.closest?.('[data-tgv-resize]')?.dataset?.tgvResize;
    if(direct==='start'||direct==='end')return direct;
    const rect=btn.getBoundingClientRect?.();
    if(!rect)return 'move';
    const margin=Math.min(18,Math.max(6,rect.height*.24));
    if(y-rect.top<=margin)return 'start';
    if(rect.bottom-y<=margin)return 'end';
    return 'move';
  }
  function focusInitialHour(host,iso,minute){
    // No ocultamos ninguna hora: seguimos mostrando las 24 h y desplazamos
    // la vista al primer evento, tanto en HOY como al seleccionar una fecha.
    if(host.dataset.tgvFocusedDay===iso)return;
    const ticket=String((Number(host.dataset.tgvFocusTicket)||0)+1);
    host.dataset.tgvFocusTicket=ticket;
    const action=()=>{
      if(host.dataset.tgvFocusTicket!==ticket||host.dataset.tgvDay!==iso||!host.isConnected)return;
      const board=host.querySelector?.('.tgv-board');
      if(!board?.getBoundingClientRect||!board.getClientRects?.().length)return;
      // Algunas pantallas tienen un contenedor desplazable; en las demás
      // el scroll pertenece al documento. El cálculo usa el mismo viewport.
      // En Calendario desplazar solo la agenda de 24 h, nunca el calendario mensual.
      // En Inicio se mantiene el desplazamiento del documento.
      let scroller=host;
      while(scroller){
        const style=typeof getComputedStyle==='function'?getComputedStyle(scroller):null;
        if(style&&/(auto|scroll)/.test(style.overflowY)&&scroller.scrollHeight>scroller.clientHeight+4)break;
        scroller=scroller.parentElement;
      }
      const rect=board.getBoundingClientRect();
      const header=document.querySelector?.('.topbar');
      const headerHeight=Math.max(0,header?.getBoundingClientRect?.().height||0);
      const allDay=host.querySelector?.('.tgv-all-day');
      const allDayHeight=Math.max(0,allDay?.getBoundingClientRect?.().height||0);
      // La primera cita horaria debe quedar visible DEBAJO de los eventos
      // de todo el día (anclados bajo la cabecera), no oculta tras ellos.
      const ownCalendarScroll=scroller===host&&host.id==='calendarDayItems';
      const visibleInset=(ownCalendarScroll?0:headerHeight)+12+(allDayHeight?allDayHeight+12:0);
      const offset=Math.max(0,minute-8)*PX_PER_MINUTE;
      if(scroller){
        const origin=scroller.getBoundingClientRect().top;
        const position=Math.max(0,scroller.scrollTop+rect.top-origin+offset-visibleInset);
        scroller.scrollTo?.({top:position,behavior:'instant'});
      }else if(typeof window.scrollTo==='function'){
        const position=Math.max(0,(window.scrollY||0)+rect.top+offset-visibleInset);
        window.scrollTo({top:position,behavior:'instant'});
      }else return;
      host.dataset.tgvFocusedDay=iso;
    };
    // La navegación puede ejecutar scrollTo(0,0) durante el render.
    // Enfocar tras dos frames evita que esa llamada anule el destino.
    if(typeof window.requestAnimationFrame==='function')
      window.requestAnimationFrame(()=>window.requestAnimationFrame(action));
    else if(typeof requestAnimationFrame==='function')
      requestAnimationFrame(()=>requestAnimationFrame(action));
  }
  function render(host,iso,rawItems){
    if(!host||!iso)return;
    const bounds=dayBounds(iso);
    // La identidad no depende de la hora: una misma cita puede moverse o pasar a todo el día.
    // Si la fuente devuelve dos instantáneas de esa identidad, se representa solo una vez.
    const byIdentity=new Map();
    (Array.isArray(rawItems)?rawItems:[]).filter(item=>item?.dueAt&&visibleInDay(item,bounds))
      .forEach(item=>byIdentity.set(eventKey(item),item));
    const items=[...byIdentity.values()];
    const allDay=items.filter(item=>!isTimed(item));
    const timed=assignColumns(items.filter(isTimed).map(item=>segment(item,bounds)));
    // Toda la jornada, incluso citas de madrugada o a última hora.
    const first=0,last=1440,total=last*PX_PER_MINUTE;
    const hours=[];
    for(let m=first;m<=last;m+=60){
      hours.push('<div class="tgv-hour" style="top:'+(m-first)*PX_PER_MINUTE+'px"><span>'+hhmm(m)+'</span></div>');
    }
    const cards=timed.map((row,index)=>{
      const item=row.item,top=row.start*PX_PER_MINUTE,
        height=Math.max(24,(row.end-row.start)*PX_PER_MINUTE),
        width=100/row.columns,left=row.column*width,
        canResize=Number(item.dueAt)>=bounds.start&&itemEffectiveEndAt(item)<=bounds.end&&itemDuration(item)<86400000,
        density=height<84?' tgv-event--compact':'',
        narrow=width<=50?' tgv-event--narrow':'',
        accessible='Abrir '+fullLabel(item);
      return '<button type="button" class="tgv-event'+density+narrow+(writing?' tgv-event--busy':'')+'"'+
        ' data-tgv-index="'+index+'" style="top:'+top+'px;height:'+height+'px;left:calc('+left+'% + 3px);width:calc('+width+'% - 6px);--tgv-accent:'+validColor(item)+'"'+
        ' title="'+escAttr(fullLabel(item))+'" aria-label="'+escAttr(accessible)+'">'+
        blockContents(item,row,height,width)+
        (canResize?'<span class="tgv-resize-handle tgv-resize-handle--start" data-tgv-resize="start" aria-hidden="true"></span>'+
        '<span class="tgv-resize-handle tgv-resize-handle--end" data-tgv-resize="end" aria-hidden="true"></span>':'')+
        '</button>';
    }).join('');
    host.classList.add('tgv-host');
    host.dataset.tgvDay=iso;
    // Una sola referencia de altura para la cabecera sticky y la franja fija.
    const topBar=document.querySelector?.('.topbar');
    const topBarHeight=Math.max(0,Math.ceil(topBar?.getBoundingClientRect?.().height||0));
    // La franja «Todo el día» se fija dentro de la agenda desplazable.
    // En Inicio continúa fijada bajo la cabecera principal.
    host.style?.setProperty?.('--tgv-top-offset',host.id==='calendarDayItems'?'0px':(topBarHeight+6)+'px');
    host.innerHTML=(allDay.length?'<div class="tgv-all-day" role="region" aria-label="Eventos de todo el día"><span class="tgv-all-day-label">Todo el día · '+allDay.length+' evento'+(allDay.length===1?'':'s')+'</span>'+allDay.map((item,index)=>
      '<button type="button" class="tgv-all-day-event" data-tgv-allday="'+index+'" style="--tgv-accent:'+validColor(item)+'" title="'+escAttr(fullLabel(item))+'" aria-label="'+escAttr('Abrir '+fullLabel(item))+'">'+uiIcon(isVehicleReservation(item)?'car':familyCategoryIcon(item.category))+
      '<span><strong>'+esc(item.title||'Evento')+'</strong><small>'+esc(subtitle(item))+'</small></span></button>').join('')+'</div>':'')+
      '<div class="tgv-board" style="height:'+total+'px" role="group" aria-label="Agenda por horas, de 00:00 a 24:00">'+
      hours.join('')+'<div class="tgv-event-layer" style="height:'+total+'px">'+cards+'</div>'+
      (!timed.length?'<div class="tgv-no-events">No hay eventos con hora para este día.</div>':'')+
      '</div><p class="tgv-help">Mantén pulsado el centro para mover una cita, o los bordes para cambiar su inicio o final (15 min).</p>';
    host.querySelectorAll('[data-tgv-allday]').forEach(btn=>btn.addEventListener('click',()=>{
      const item=allDay[Number(btn.dataset.tgvAllday)];
      if(item)openExistingDetail(item);
    }));
    host.querySelectorAll('[data-tgv-index]').forEach(btn=>{
      const row=timed[Number(btn.dataset.tgvIndex)];
      attachDrag(btn,row,iso,host);
    });
    focusInitialHour(host,iso,timed.length?Math.min(...timed.map(r=>r.start)):0);
  }
  /** Abre la ficha única que ya utiliza Nexo, con el apartado Editar plegado. */
  function openExistingDetail(item){
    if(!item||writing)return;
    openEventInAgenda(Number(item.seriesId||item.id),Number(item.occurrenceAt||item.dueAt),isRecurring(item));
  }
  function notifyChange(){
    if(state.screen==='calendar')renderCalendar();
    else if(state.screen==='today')renderToday();
  }
  function submitMove(row,iso,host,newStart,newEnd){
    if(writing)return;
    const item=row.item,originalStart=Number(item.dueAt),originalEnd=Number(itemEffectiveEndAt(item));
    if(newStart===originalStart&&newEnd===originalEnd)return;
    if(newStart<dayBounds(iso).start||newEnd>dayBounds(iso).end||newEnd-newStart<15*60000)return;
    const recurrent=isRecurring(item),seriesId=Number(item.seriesId||item.id),
      occurrenceAt=Number(item.occurrenceAt||item.dueAt),status=String(item.occurrenceStatus||'active');
    const request={seriesId,occurrenceAt,id:Number(item.id||seriesId),recurring:recurrent,
      dueAt:newStart,endAt:newEnd,durationMinutes:Math.round((newEnd-newStart)/60000),
      hasTime:true,allDay:false,occurrenceState:status==='deleted'?'active':status};
    const requestId='tgv-'+(++nextRequest);
    const oldItem={...item},ctx={request,item,oldItem,oldStart:originalStart,oldEnd:originalEnd,newStart,iso,host,isUndo:false};
    writing=true;pending.set(requestId,ctx);
    item.dueAt=newStart;item.endAt=newEnd;item.durationMinutes=request.durationMinutes;
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
      ctx.item.durationMinutes=Math.round((ctx.oldEnd-ctx.oldStart)/60000);
      notifyChange();
      toast(result.error||'No se pudo actualizar la hora. Se ha restaurado la cita.','',null,5000);
      return;
    }
    notifyChange();
    if(ctx.isUndo){
      // La operación nativa ha confirmado el Deshacer: el modelo local acompaña a SQLite.
      ctx.item.dueAt=ctx.request.dueAt;
      ctx.item.endAt=ctx.request.endAt;
      ctx.item.durationMinutes=Math.round((ctx.request.endAt-ctx.request.dueAt)/60000);
      toast('Cambio de hora deshecho');return;
    }
    const newTime=fmtTime(ctx.newStart)+' – '+fmtTime(ctx.request.endAt),saved=ctx.request;
    toast('Horario actualizado: '+newTime,'Deshacer',()=>{
      if(writing){toast('Hay otra actualización en curso.');return;}
      const undo={...saved,dueAt:ctx.oldStart,endAt:ctx.oldEnd,
        durationMinutes:Math.round((ctx.oldEnd-ctx.oldStart)/60000)};
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
      openExistingDetail(item);
    });
    if(writing)return;
    const bounds=dayBounds(iso),originalStart=Number(item.dueAt),
      originalEnd=Number(itemEffectiveEndAt(item));
    // Multidía: consulta permitida, edición por gesto desactivada para no
    // modificar un tramo accidentalmente desde un día intermedio.
    if(originalStart<bounds.start||originalEnd>bounds.end||originalEnd-originalStart>=86400000)return;
    const startMinutes=clockMinutes(originalStart),
      endMinutes=originalEnd>=bounds.end?1440:Math.max(startMinutes+15,clockMinutes(originalEnd));
    let timer=0,active=false,startY=0,currentY=0,lastTouch=0,
      mouseEnabled=false,mode='move',scrollStart=0,rafId=0,preview=null;
    const viewportDelta=y=>y-startY+((window.scrollY||0)-scrollStart);
    const nextRange=y=>gestureRange(mode,startMinutes,endMinutes,viewportDelta(y)/PX_PER_MINUTE);
    const paint=range=>{
      preview=range;
      btn.style.top=(range.start*PX_PER_MINUTE)+'px';
      btn.style.height=(Math.max(24,(range.end-range.start)*PX_PER_MINUTE))+'px';
      const text=btn.querySelector?.('.tgv-event-time');
      if(text)text.textContent=hhmm(range.start)+' – '+hhmm(range.end);
      btn.setAttribute('aria-label','Nuevo horario '+hhmm(range.start)+' a '+hhmm(range.end));
    };
    const scrollTick=()=>{
      if(!active){rafId=0;return;}
      const viewport=window.innerHeight||0;
      if(viewport&&typeof window.scrollBy==='function'){
        const edge=88;
        const speed=currentY<edge?-Math.min(17,(edge-currentY)/5):
          currentY>viewport-edge?Math.min(17,(currentY-viewport+edge)/5):0;
        if(speed){
          window.scrollBy(0,speed);
          paint(nextRange(currentY));
        }
      }
      rafId=window.requestAnimationFrame?.(scrollTick)||0;
    };
    const begin=()=>{
      timer=0;active=true;btn._tgvSuppressUntil=Date.now()+1000;
      btn.classList.add('tgv-event--dragging');
      btn.classList.add('tgv-event--'+mode);
      document.body.classList.add('tgv-drag-active');
      try{navigator.vibrate?.(12);}catch(_){}
      if(typeof window.requestAnimationFrame==='function')rafId=window.requestAnimationFrame(scrollTick);
    };
    const stop=(y,cancel=false)=>{
      clearTimeout(timer);timer=0;
      if(!active)return;
      active=false;btn._tgvSuppressUntil=Date.now()+900;
      if(rafId&&typeof window.cancelAnimationFrame==='function')window.cancelAnimationFrame(rafId);
      rafId=0;
      btn.classList.remove('tgv-event--dragging','tgv-event--move','tgv-event--start','tgv-event--end');
      document.body.classList.remove('tgv-drag-active');
      const result=nextRange(y),moved=Math.abs(viewportDelta(y))>=7;
      btn.style.top=(row.start*PX_PER_MINUTE)+'px';
      btn.style.height=(Math.max(24,(row.end-row.start)*PX_PER_MINUTE))+'px';
      const time=btn.querySelector?.('.tgv-event-time');
      if(time)time.textContent=hhmm(row.start)+' – '+hhmm(row.end);
      if(cancel||!moved||(result.start===startMinutes&&result.end===endMinutes))return;
      submitMove(row,iso,host,minuteToTimestamp(iso,result.start),minuteToTimestamp(iso,result.end));
    };
    btn.addEventListener('contextmenu',e=>e.preventDefault());
    btn.addEventListener('touchstart',e=>{
      if(e.touches.length!==1||writing)return;
      lastTouch=Date.now();startY=e.touches[0].clientY;currentY=startY;
      scrollStart=window.scrollY||0;
      mode=chooseGesture(btn,startY,e.target);
      clearTimeout(timer);timer=setTimeout(begin,300);
    },{passive:true});
    btn.addEventListener('touchmove',e=>{
      if(e.touches.length!==1)return;
      const y=e.touches[0].clientY;
      if(!active){
        if(Math.abs(y-startY)>9){clearTimeout(timer);timer=0;btn._tgvSuppressUntil=Date.now()+650;}
        return; // El scroll vertical normal no queda bloqueado.
      }
      e.preventDefault();currentY=y;paint(nextRange(y));
    },{passive:false});
    btn.addEventListener('touchend',e=>{
      if(!active){clearTimeout(timer);timer=0;return;}
      stop(e.changedTouches[0]?.clientY??currentY);
    });
    btn.addEventListener('touchcancel',()=>stop(currentY,true));
    const onMouseMove=e=>{
      if(!mouseEnabled)return;
      if(!active&&Math.abs(e.clientY-startY)>9){clearTimeout(timer);timer=0;}
      if(active){e.preventDefault();currentY=e.clientY;paint(nextRange(e.clientY));}
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
      scrollStart=window.scrollY||0;
      mode=chooseGesture(btn,startY,e.target);
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
    const visibleKeys=new Set(items.filter(item=>item?.dueAt&&visibleInDay(item,bounds)).map(eventKey));
    const count=visibleKeys.size;
    section.querySelector('[data-tgv-home-count]').textContent=count+' evento'+(count===1?'':'s');
    render(host,iso,items);
  }
  function showHome(){
    state.homeTimelineVisible=true;
    delete document.getElementById('homeTodayTimeGrid')?.dataset.tgvFocusedDay;
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
