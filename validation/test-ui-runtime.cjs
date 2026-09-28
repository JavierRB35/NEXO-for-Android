// DOM-level integration tests. No browser, Android services or real user data.
// Optional QA-only dependency: linkedom 0.18.13 (never part of the APK).
const fs=require('fs');
const path=require('path');
const vm=require('vm');
const assert=require('node:assert/strict');
const {parseHTML}=require(require.resolve('linkedom',{paths:[process.env.NEXO_QA_MODULES||process.cwd()]}));
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const nav=fs.readFileSync(path.join(root,'app/src/main/assets/navigation.js'),'utf8');
let passed=0;
function fixture(start='today',version='3.4.9'){
  const {window}=parseHTML(html),document=window.document;
  // Linkedom omits layout and a few browser form APIs. These shims only supply
  // standard form state and focus; they do not pretend to validate appearance.
  Object.defineProperty(window.HTMLSelectElement.prototype,'value',{configurable:true,get(){return this.querySelector('option[selected]')?.getAttribute('value')??this.querySelector('option')?.getAttribute('value')??this.querySelector('option')?.textContent??'';},set(v){for(const o of this.querySelectorAll('option')){const selected=(o.getAttribute('value')??o.textContent)===String(v);if(selected)o.setAttribute('selected','');else o.removeAttribute('selected');}}});
  window.HTMLElement.prototype.focus=function(){document.activeElement=this;};
  window.HTMLElement.prototype.getClientRects=function(){return [{width:100,height:48}];};
  let scroll=0;Object.defineProperty(window,'scrollY',{configurable:true,get:()=>scroll});
  window.scrollTo=(x,y)=>{scroll=Number(y)||0;};window.matchMedia=()=>({matches:true});
  const frames=[],timeouts=new Map();let timer=0;
  const data={start,deleteOk:true,display:'comfortable',shopping:0,notes:[],appointments:[],occurrences:[],reminders:[],calendar:[],lists:[{id:11,title:'Compra semanal',kind:'Compra',count:2,pending:2}],entries:[{id:21,text:'Leche',checked:false},{id:22,text:'Pan',checked:false}],recordings:[{id:'audio1',title:'Reunión semanal',startedAt:Date.now(),durationMs:123000,sizeBytes:2000}],calls:[],saved:[],collections:{documents:[],places:[]}};
  const json=x=>JSON.stringify(x);
  const methods={
    getAppVersion:()=>version,
    getDisplayMode:()=>data.display,setDisplayMode:v=>{data.display=v;},getStartScreen:()=>data.start,setStartScreen:v=>{data.start=v;},getPreferredShoppingList:()=>data.shopping,setPreferredShoppingList:v=>{data.shopping=v;},
    getMoreSummary:()=>json({inbox:2}),getToday:()=>json({}),getCalendar:()=>json(data.calendar),getMedicationTodayDoses:()=>json([]),getMedications:()=>json([]),getJournalDailyPick:()=>json({}),getJournalEntries:()=>json([]),getBackupStatus:()=>json({stats:{}}),getNotes:()=>json(data.notes),getAppointments:()=>json(data.appointments),getLists:()=>json(data.lists),getList:id=>json(data.lists.find(l=>l.id===id)||{}),getListEntries:()=>json(data.entries),getRecordingStatus:()=>json({active:false}),getRecordings:()=>json(data.recordings),getInbox:()=>json([]),getHistory:()=>json([]),getLastParking:()=>json({}),getCollection:k=>json(data.collections[k]||[]),canScheduleExactAlarms:()=>true,
    getItem:id=>json([...data.notes,...data.appointments,...data.calendar,...data.collections.documents,...data.collections.places].find(n=>n.id===id)||{}),getItemReminders:id=>json(data.reminders.filter(r=>r.itemId===id)),parseMedication:()=>json({}),synthesizeJournal:()=>json({}),search:()=>json(data.searchResults||[]),
    getNextOccurrence:(id,after)=>data.occurrences.find(o=>o.id===id&&o.occurrenceAt>after&&o.occurrenceStatus!=='done')?.occurrenceAt||0,
    getOccurrence:(id,at)=>json(data.occurrences.find(o=>o.id===id&&o.occurrenceAt===at)||{}),
    updateItem:raw=>{const p=JSON.parse(raw),i=data.appointments.find(i=>i.id===p.id)||data.calendar.find(i=>i.id===p.id);if(!i)return json({ok:false});Object.assign(i,p);return json({ok:true});},
    setOccurrenceStatus:(id,at,status)=>{const i=data.occurrences.find(o=>o.id===id&&o.occurrenceAt===at);if(!i)return json({ok:false});i.occurrenceStatus=status;i.status=status;return json({ok:true});},
    replaceItemReminders:(id,raw)=>{data.reminders=data.reminders.filter(r=>r.itemId!==id).concat(JSON.parse(raw).map((leadMinutes,j)=>({id:200+j,itemId:id,leadMinutes})));return json({ok:true});},
    deleteList:id=>{if(!data.deleteOk)return false;data.lists=data.lists.filter(l=>l.id!==id);return true;},
    toggleListEntry:(id,checked)=>{const e=data.entries.find(e=>e.id===id);if(!e)return false;e.checked=checked;return true;},
    classify:text=>json({title:text,type:'task',category:'Tareas',recurrence:'none',needsReview:false}),
    classifyAppointment:text=>json(data.classification||{title:text,description:text,type:'event',category:'Personal',recurrence:'none',needsReview:false}),
    saveCapture:raw=>{data.saved.push(JSON.parse(raw));return json({ok:true,id:100});},
    savePlace:raw=>{const p=JSON.parse(raw);if(data.placeSaveFails)return json({ok:false,error:'No se pudo guardar el lugar.'});let item=data.collections.places.find(i=>i.id===p.id);if(!item){item={id:301+data.collections.places.length};data.collections.places.push(item);}Object.assign(item,{title:p.title,body:p.body,locationText:p.locationText,subcategory:p.label,type:'place',category:'Lugares',dueAt:0,recurrence:'none'});data.saved.push(p);return json({ok:true,id:item.id});},
    openPlaceMap:()=>{},
    saveDocument:raw=>{const p=JSON.parse(raw);if(data.documentSaveFails)return json({ok:false,error:'No se pudo guardar el documento.'});let item=data.collections.documents.find(i=>i.id===p.id);if(!item){item={id:401+data.collections.documents.length};data.collections.documents.push(item);}Object.assign(item,{type:'document',title:p.description.split('\n')[0].slice(0,100),body:p.description,subcategory:p.label,attachmentPath:p.attachmentPath,attachmentName:p.attachmentName,attachmentMime:p.attachmentMime,dueAt:0,recurrence:'none'});data.saved.push(p);return json({ok:true,id:item.id});},
    chooseDocumentFile:()=>{},discardDocumentFile:()=>{},openDocumentFile:()=>{},downloadDocument:()=>{},shareDocument:()=>{},
    deleteItem:id=>{const doc=data.collections.documents.find(i=>i.id===id&&i.type==='document');if(doc){data.deletedDocument=doc;data.collections.documents=data.collections.documents.filter(i=>i.id!==id);return json({ok:true,undoToken:'document-delete'});}const i=data.collections.places.find(i=>i.id===id);if(!i)return json({ok:false});data.deletedPlace=i;data.collections.places=data.collections.places.filter(p=>p.id!==id);return json({ok:true,undoToken:'place-delete'});},
    restoreItem:token=>{if(token==='document-delete'&&data.deletedDocument){data.collections.documents.push(data.deletedDocument);data.deletedDocument=null;return json({ok:true});}if(token!=='place-delete'||!data.deletedPlace)return json({ok:false});data.collections.places.push(data.deletedPlace);data.deletedPlace=null;return json({ok:true});},finalizeDelete:()=>json({ok:true}),
    suggestNoteTitle:text=>text.slice(0,40),saveNote:raw=>{const n=JSON.parse(raw);data.notes.push({...n,id:101,type:'note'});return 101;},autosaveNote:raw=>{data.saved.push(JSON.parse(raw));return true;},
    playRecording:id=>{data.playing=id;},stopRecordingPlayback:()=>{},
    renameRecording:(id,title)=>{data.recordings.find(r=>r.id===id).title=title;return true;},
    shareRecording:()=>{},exportRecording:()=>{},startManagedVoice:()=>{},stopManagedVoice:()=>{},cancelManagedVoice:()=>{},startVoiceInput:()=>{},openLocationDirections:()=>{},shareNexoItem:()=>{},shareCalendarEvent:()=>{},chooseFile:()=>{}
  };
  const native=new Proxy(methods,{get(target,k){if(!(k in target))throw new Error('Unmocked native call '+String(k));return (...args)=>{data.calls.push([k,...args]);return target[k](...args);};}});
  const context=vm.createContext({window,document,NexoNative:native,console,Date,Intl,Map,Set,Math,JSON,Number,String,Boolean,Array,RegExp,Object,Promise,Error,requestAnimationFrame:f=>{frames.push(f);},setTimeout:(f,ms)=>{timeouts.set(++timer,{f,ms});return timer;},clearTimeout:id=>timeouts.delete(id),setInterval:()=>++timer,clearInterval:()=>{}});
  vm.runInContext(nav,context);vm.runInContext(source,context);
  const flush=()=>{while(frames.length)frames.shift()();};
  document.dispatchEvent(new window.Event('DOMContentLoaded'));flush();
  const click=selector=>{const e=document.querySelector(selector);assert.ok(e,'Missing control '+selector);e.click();flush();return e;};
  const run=code=>{const value=vm.runInContext(code,context);flush();return value;};
  return {window,document,data,run,click,flush,frames,timeouts};
}
function test(name,fn){fn();passed++;console.log('OK '+name);}

test('All ten retained modules open from home; retired shortcuts are absent',()=>{
  for(const module of ['calendar','appointments','medications','lists','notes','recordings','parking','journal','documents','places']){
    const f=fixture();f.click('[data-panel="'+module+'"]');
    assert.equal(f.run('state.screen'),module==='calendar'?'calendar':'panel');
    if(module!=='calendar')assert.equal(f.run('state.lastPanel'),module);
    for(const retired of ['tasks','reminders','favorites'])assert.equal(f.document.querySelector('[data-panel="'+retired+'"]'),null);
  }
});
test('Back from Notes restores the home route and its scroll position',()=>{
  const f=fixture();f.window.scrollTo(0,420);f.click('[data-panel="notes"]');f.click('#panelBack');
  assert.equal(f.run('state.screen'),'today');assert.equal(f.window.scrollY,420);
});
test('Checking shopping items updates the same nodes without scrolling',()=>{
  const f=fixture();f.data.shopping=11;f.click('#homeShoppingBtn');
  f.window.scrollTo(0,650);const input=f.document.querySelector('[data-entry-toggle="21"]');
  f.click('[data-entry-text-toggle="21"]');assert.equal(f.data.entries[0].checked,true);
  assert.equal(f.document.querySelector('[data-entry-toggle="21"]'),input);assert.equal(f.window.scrollY,650);
  f.click('[data-entry-text-toggle="21"]');assert.equal(f.data.entries[0].checked,false);assert.equal(f.window.scrollY,650);
  f.click('#panelBack');assert.equal(f.run('state.screen'),'today');
});
test('Missing preferred shopping list offers a choice without creating data',()=>{
  const f=fixture();f.data.shopping=999;f.click('#homeShoppingBtn');
  assert.equal(f.run('state.screen'),'today');assert.ok(!f.document.querySelector('#modal').classList.contains('hidden'));
  f.click('[data-choose-shopping="11"]');assert.equal(f.data.shopping,11);assert.equal(f.run('state.currentList'),11);
  assert.ok(!f.data.calls.some(c=>c[0]==='createList'));
});
test('Calendar maintains one selection across tab switches and clamps month days',()=>{
  const f=fixture();f.run("state.calendarMonth=new Date(2028,0,1);state.calendarSelected='2028-01-31'");f.click('[data-nav="calendar"]');
  assert.equal(f.document.querySelectorAll('[data-calendar-day]').length,42);f.click('#calendarNext');
  assert.equal(f.run('state.calendarSelected'),'2028-02-29');f.click('[data-nav="today"]');f.click('[data-nav="calendar"]');
  assert.equal(f.run('state.calendarSelected'),'2028-02-29');assert.equal(f.document.querySelectorAll('[aria-pressed="true"][data-calendar-day]').length,1);
});
test('Calendar capture uses the selected day and returns to that calendar',()=>{
  const f=fixture();f.run("state.calendarMonth=new Date(2028,8,1);state.calendarSelected='2028-09-14'");f.click('[data-nav="calendar"]');f.click('#calendarAddBtn');
  assert.equal(f.document.querySelector('#captureDate').value,'2028-09-14');assert.equal(f.document.querySelector('#captureDetails').open,false);
  f.document.querySelector('#captureText').value='Revisar reserva';f.run('analyzeCapture({immediate:true})');f.click('#saveCaptureBtn');
  assert.equal(f.data.saved.length,1);assert.equal(f.data.saved[0].type,'event');assert.equal(f.data.saved[0].appointmentCapture,true);assert.equal(new Date(f.data.saved[0].dueAt).getDate(),14);assert.equal(f.run('state.screen'),'calendar');assert.equal(f.run('state.calendarSelected'),'2028-09-14');
});
test('Creating an appointment returns to Citas',()=>{
  const f=fixture();f.click('[data-panel="appointments"]');f.click('#panelAddAppointment');
  f.document.querySelector('#captureText').value='Preparar viaje';f.run('analyzeCapture({immediate:true})');f.click('#saveCaptureBtn');
  assert.equal(f.run('state.lastPanel'),'appointments');assert.equal(f.run('state.screen'),'panel');
});
test('Global search finds audio titles with accent normalization',()=>{
  const f=fixture();f.click('[data-nav="search"]');f.document.querySelector('#searchInput').value='reunion';f.click('#searchBtn');
  assert.ok(f.document.querySelector('[data-recording-play="audio1"]'));assert.match(f.document.querySelector('#searchStatus').textContent,/1 resultado/);
  f.click('[data-recording-menu="audio1"]');assert.ok(f.document.querySelector('[data-recording-export="audio1"]'));
});
test('Audio rename from search stays in search and refreshes matching results',()=>{
  const f=fixture();f.click('[data-nav="search"]');f.document.querySelector('#searchFilter').value='recordings';f.click('#searchBtn');f.click('[data-recording-menu="audio1"]');f.click('[data-recording-rename="audio1"]');
  f.document.querySelector('#recordingRenameTitle').value='Entrevista';f.click('#recordingRenameSave');
  assert.equal(f.run('state.screen'),'search');assert.match(f.document.querySelector('#searchResults').textContent,/Entrevista/);
});
test('Recording filter does not show notes, and note filter excludes recordings',()=>{
  const f=fixture();f.click('[data-nav="search"]');f.document.querySelector('#searchInput').value='reunion';f.document.querySelector('#searchFilter').value='note';f.click('#searchBtn');
  assert.equal(f.document.querySelector('[data-recording-play]'),null);
});
test('Startup preference and all text size choices persist through the native bridge',()=>{
  const f=fixture();f.click('#settingsBtn');f.document.querySelector('#startScreen').value='calendar';f.document.querySelector('#startScreen').dispatchEvent(new f.window.Event('change'));assert.equal(f.data.start,'calendar');
  const g=fixture(f.data.start,'42.9.1');assert.equal(g.run('state.screen'),'calendar');
  assert.equal(g.document.querySelector('.header-version').textContent,'v42.9.1');
  assert.equal(g.document.querySelector('.version-info [data-app-version]').textContent,'v42.9.1');
  for(const mode of ['normal','comfortable','large']){
    g.click('#settingsBtn');g.click('[data-display-mode="'+mode+'"]');
    assert.equal(g.data.display,mode);assert.equal(g.document.documentElement.dataset.displayMode,mode);
    assert.equal(g.document.querySelector('[data-display-mode="'+mode+'"]').getAttribute('aria-pressed'),'true');
    assert.ok(g.document.querySelector('#toast').classList.contains('hidden'),'Size selection must stay silent');
    for(const screen of ['today','calendar','search']){
      g.click('[data-nav="'+screen+'"]');assert.equal(g.run('state.displayMode'),mode);
      assert.ok(g.document.querySelector('#toast').classList.contains('hidden'),'Navigation must stay silent');
    }
  }
});
test('Month picker changes the month and rejects out-of-range values',()=>{
  const f=fixture();f.click('[data-nav="calendar"]');f.click('#calendarMonthPickerBtn');f.document.querySelector('#calendarMonthInput').value='2031-12';f.click('#calendarMonthApply');assert.equal(f.run('state.calendarSelected'),'2031-12-01');
  f.click('#calendarMonthPickerBtn');f.document.querySelector('#calendarMonthInput').value='9999-12';f.click('#calendarMonthApply');assert.equal(f.run('state.calendarSelected'),'2031-12-01');
});
test('User-controlled audio titles remain text, without injected markup',()=>{
  const f=fixture();f.data.recordings[0].title='<img src=x onerror=alert(1)>';f.click('[data-panel="recordings"]');assert.equal(f.document.querySelector('#panelContent img'),null);assert.match(f.document.querySelector('#panelContent').textContent,/<img/);
});
test('Closing the note editor flushes pending text before removing its controls',()=>{
  const f=fixture();f.click('[data-panel="notes"]');f.click('#notePanelAdd');f.document.querySelector('#noteBody').value='Pendiente importante';f.document.querySelector('#noteBody').dispatchEvent(new f.window.Event('input'));f.click('#modalClose');assert.ok(f.data.calls.some(c=>c[0]==='saveNote'));assert.equal(f.run('state.noteEditor'),null);
});
test('Deleting a list returns to its origin and removes the obsolete back route',()=>{
  for(const fromShopping of [false,true]){
    const f=fixture();f.data.shopping=11;
    if(fromShopping)f.click('#homeShoppingBtn');else {f.click('[data-panel="lists"]');f.click('[data-list-open="11"]');}
    f.click('#manageListBtn');f.click('#listDeleteAction');f.click('#confirmDelete');
    assert.equal(f.data.lists.length,0);assert.equal(f.data.shopping,0);
    assert.equal(f.run('router.history.some(r=>r.list===11)'),false);
    if(fromShopping)assert.equal(f.run('state.screen'),'today');
    else {assert.equal(f.run('state.currentList'),null);f.click('#panelBack');assert.equal(f.run('state.screen'),'today');}
  }
});
test('A failed list deletion keeps the open list and preferred shortcut',()=>{
  const f=fixture();f.data.shopping=11;f.data.deleteOk=false;f.click('#homeShoppingBtn');
  f.click('#manageListBtn');f.click('#listDeleteAction');f.click('#confirmDelete');
  assert.equal(f.run('state.currentList'),11);assert.equal(f.data.shopping,11);assert.equal(f.data.lists.length,1);
});
test('A completed legacy task can be reopened as a Cita and postponed',()=>{
  const f=fixture();f.data.appointments=[{id:81,type:'task',title:'Preparar reserva',status:'done',category:'Viajes',dueAt:Date.now()+86400000}];f.click('[data-panel="appointments"]');
  assert.equal(f.document.querySelector('[data-appointment-row]'),null);f.click('[data-appointment-filter="done"]');f.window.scrollTo(0,200);f.click('[data-agenda-options="81"]');assert.equal(f.document.querySelector('#agendaPostponeAction'),null);
  f.click('#agendaDoneAction');assert.equal(f.data.appointments[0].status,'pending');assert.equal(f.window.scrollY,200);
  f.click('[data-appointment-filter="pending"]');f.click('[data-agenda-options="81"]');assert.ok(f.document.querySelector('#agendaPostponeAction'));f.click('#agendaPostponeAction');assert.ok(f.document.querySelector('[data-postpone-custom]'));
});
test('General dictation always opens the full Cita form without executing another module',()=>{
  for(const phrase of ['Tengo una tarea para mañana','Recuérdame llamar a Ana','Cita con el dentista','Comprar leche','Pon una alarma a las ocho','Anota renovar la póliza del coche']){
    const f=fixture();f.click('#homeTalkBtn');f.run('window.NexoUI.onManagedVoiceSegment('+JSON.stringify(phrase)+')');f.run('window.NexoUI.onManagedVoiceStopped()');
    assert.equal(f.run('state.screen'),'save');assert.equal(f.document.querySelector('#captureType').value,'event');assert.equal(f.document.querySelector('#captureType').type,'hidden');
    for(const id of ['captureTitle','captureDescription','captureDate','captureTime','captureLocation'])assert.ok(f.document.querySelector('#'+id));
    assert.ok(f.data.calls.some(c=>c[0]==='classifyAppointment'&&c[1]===phrase));assert.ok(!f.data.calls.some(c=>['classify','interpretIntent','createSystemAlarm','saveNote','parseListVoice'].includes(c[0])));assert.equal(f.data.saved.length,0);
    f.click('#saveCaptureBtn');assert.equal(f.data.saved[0].type,'event');assert.equal(f.data.saved[0].originalText,phrase);
  }
});
test('Manual Citas accept a title with no date or place',()=>{
  const f=fixture();f.click('#homeCreateBtn');f.document.querySelector('#captureTitle').value='Ordenar los documentos del cajón';f.click('#saveCaptureBtn');
  assert.equal(f.data.saved.length,1);const p=f.data.saved[0];assert.equal(p.title,'Ordenar los documentos del cajón');assert.equal(p.dueAt,0);assert.equal(p.locationText,'');assert.equal(p.type,'event');assert.equal(p.needsReview,false);
});
test('Clearing a suggested date and location does not silently restore them',()=>{
  const f=fixture();f.data.classification={type:'event',title:'Revisión',description:'Revisión en Clínica Norte',category:'Salud',dueDate:'2030-04-22',dueTime:'16:00',hasTime:true,locationText:'Clínica Norte',reminderRequested:true,reminderLeadMinutes:[60],recurrence:'none'};
  f.click('#homeCreateBtn');f.document.querySelector('#captureText').value='Revisión el 22 de abril de 2030 a las 16 en Clínica Norte, avisa una hora antes';f.run('analyzeCapture({immediate:true})');
  const date=f.document.querySelector('#captureDate');date.value='';date.dispatchEvent(new f.window.Event('change'));const location=f.document.querySelector('#captureLocation');location.value='';location.dispatchEvent(new f.window.Event('input'));f.click('#saveCaptureBtn');
  assert.equal(f.data.saved.length,1);assert.equal(f.data.saved[0].dueAt,0);assert.equal(f.data.saved[0].locationText,'');assert.equal(f.data.saved[0].reminderLeadMinutes.length,0);
});
test('Legacy tasks, reminders and expiries share Citas without one row per alert',()=>{
  const f=fixture();f.data.appointments=['event','task','reminder','expiry'].map((type,k)=>({id:k+1,type,title:'Registro '+type,status:k===3?'done':'pending',dueAt:k===1?0:Date.now()+86400000,category:'Personal'}));f.data.reminders=[{id:11,itemId:1,leadMinutes:60},{id:12,itemId:1,leadMinutes:1440}];
  f.click('[data-panel="appointments"]');assert.equal(f.document.querySelectorAll('[data-appointment-row]').length,3);f.click('[data-appointment-filter="all"]');assert.equal(f.document.querySelectorAll('[data-appointment-row]').length,4);
  f.click('[data-appointment-filter="undated"]');assert.equal(f.document.querySelectorAll('[data-appointment-row]').length,1);assert.ok(f.document.querySelector('[data-appointment-row="2"]'));
  f.click('[data-appointment-filter="done"]');assert.ok(f.document.querySelector('[data-appointment-row="4"]'));assert.equal(f.data.reminders.length,2);
});
test('Editing a legacy reminder keeps details, status and unchanged alert rules',()=>{
  const f=fixture();f.data.appointments=[{id:42,type:'reminder',title:'Llevar informes a la revisión de cardiología',body:'Llevar informes, análisis y autorización de la aseguradora.',locationText:'Clínica Norte, planta 2',category:'Salud',status:'done',dueAt:Date.now()+86400000,hasTime:true,recurrence:'none',favorite:true}];f.data.reminders=[{id:7,itemId:42,leadMinutes:60},{id:8,itemId:42,leadMinutes:1440}];
  f.click('[data-panel="appointments"]');f.click('[data-appointment-filter="done"]');f.click('[data-open-item="42"]');assert.equal(f.document.querySelector('#editType').value,'event');assert.equal(f.document.querySelector('#editFavorite'),null);
  f.document.querySelector('#editTitle').value='Revisión de cardiología';f.click('#saveEditBtn');const i=f.data.appointments[0];assert.equal(i.type,'event');assert.equal(i.status,'done');assert.equal(i.locationText,'Clínica Norte, planta 2');assert.match(i.body,/autorización/);assert.equal(i.favorite,true);assert.equal(f.data.reminders.length,2);assert.ok(!f.data.calls.some(c=>c[0]==='replaceItemReminders'));
});
test('Calendar shows only the needed four, five or six weeks',()=>{
  for(const [month,selected,cells] of [[1,'2027-02-01',28],[2,'2027-03-01',35],[4,'2027-05-01',42]]){
    const f=fixture();f.run('state.calendarMonth=new Date(2027,'+month+',1);state.calendarSelected='+JSON.stringify(selected));f.click('[data-nav="calendar"]');assert.equal(f.document.querySelectorAll('[data-calendar-day]').length,cells);assert.equal(f.document.querySelectorAll('[aria-pressed="true"][data-calendar-day]').length,1);
  }
});
test('Selected days retain distinct category markers and complete agenda text',()=>{
  const f=fixture(),date=new Date(2030,3,22,10).getTime(),title='Consulta para revisar todos los resultados de la prueba y el tratamiento pendiente',body='Traer todos los documentos.\nNo olvidar el informe firmado por la doctora y la autorización.';
  f.data.calendar=['Personal','Salud','Documentos','Casa','Viajes','Cumpleaños','Salud'].map((category,k)=>({id:k+1,type:'event',category,dueAt:date+k*60000,hasTime:true,title:k===0?title:category,body:k===0?body:'',locationText:k===0?'Clínica <Norte> & recepción':'',status:'pending'}));
  f.run("state.calendarMonth=new Date(2030,3,1);state.calendarSelected='2030-04-22'");f.click('[data-nav="calendar"]');const day=f.document.querySelector('.calendar-day.selected');assert.equal(day.querySelectorAll('.calendar-dot').length,6);assert.equal(day.querySelector('.day-total').textContent,'7');
  const row=f.document.querySelector('[data-appointment-row="1"]');assert.equal(row.querySelector('.agenda-title').textContent,title);assert.equal(row.querySelector('.agenda-description').textContent,body);assert.match(row.querySelector('.agenda-location').textContent,/<Norte> & recepción/);assert.equal(row.querySelector('.agenda-location norte'),null);
  assert.equal(f.document.querySelector('[data-appointment-row="2"] .agenda-time').textContent,'10:01');f.click('[data-open-item="1"]');assert.equal(f.document.querySelector('#editBody').value,body);
});
test('Agenda directions and sharing dispatch the selected item',()=>{
  const f=fixture();f.data.appointments=[{id:52,type:'event',title:'Recoger pedido',body:'Pedido 123',locationText:'Calle Luna 8',category:'Compras',dueAt:Date.now()+86400000,status:'pending'}];f.click('[data-panel="appointments"]');f.click('[data-agenda-options="52"]');f.click('#agendaDirectionsAction');assert.ok(f.data.calls.some(c=>c[0]==='openLocationDirections'&&c[1]==='Calle Luna 8'));
  f.click('[data-agenda-options="52"]');f.click('#agendaShareAction');f.click('[data-share-format="nexo"]');assert.ok(f.data.calls.some(c=>c[0]==='shareNexoItem'&&c[1]===52));
});
test('Completing one recurrence leaves the series and next occurrence available',()=>{
  const f=fixture(),at=Date.now()+86400000,series={id:60,type:'event',title:'Revisión semanal',category:'Salud',status:'pending',dueAt:at,recurrence:'weekly',recurrenceUntil:at+86400000*30};f.data.appointments=[series];f.data.occurrences=[at,at+86400000*7].map(dueAt=>({...series,seriesId:60,recurring:true,occurrenceAt:dueAt,dueAt,occurrenceStatus:'active'}));
  f.click('[data-panel="appointments"]');f.click('[data-agenda-options="60"]');f.click('#agendaDoneAction');assert.equal(f.data.occurrences[0].occurrenceStatus,'done');assert.equal(series.status,'pending');assert.equal(f.document.querySelector('[data-open-occurrence="60"]').dataset.occurrenceAt,String(at+86400000*7));assert.ok(!f.data.calls.some(c=>c[0]==='updateItem'));
});
test('Postponing an overdue Cita to tomorrow uses tomorrow, preserving its time',()=>{
  const f=fixture(),old=new Date();old.setDate(old.getDate()-20);old.setHours(14,30,0,0);f.data.appointments=[{id:70,type:'event',title:'Recoger documentos',dueAt:old.getTime(),hasTime:true,status:'pending',category:'Documentos'}];
  f.click('[data-panel="appointments"]');f.click('[data-agenda-options="70"]');f.click('#agendaPostponeAction');f.click('[data-postpone-days="1"]');const expected=new Date();expected.setDate(expected.getDate()+1);expected.setHours(14,30,0,0);assert.equal(f.data.appointments[0].dueAt,expected.getTime());
});
test('An expired recurring series is still pending until explicitly completed',()=>{
  const f=fixture();f.data.appointments=[{id:73,type:'event',title:'Revisar contrato',category:'Documentos',status:'pending',dueAt:Date.now()-20*86400000,recurrence:'weekly',recurrenceUntil:Date.now()-86400000}];f.click('[data-panel="appointments"]');assert.ok(f.document.querySelector('[data-appointment-row="73"]'));assert.match(f.document.querySelector('#appointmentList').textContent,/sin próximas fechas/);f.click('[data-appointment-filter="done"]');assert.equal(f.document.querySelector('[data-appointment-row="73"]'),null);
});
test('The Citas search filter includes all legacy appointment types',()=>{
  const f=fixture();f.data.searchResults=['event','task','reminder','expiry','note','document'].map((type,k)=>({id:k+1,type,title:'Revisión '+type,category:'Personal'}));f.click('[data-nav="search"]');f.document.querySelector('#searchFilter').value='appointments';f.document.querySelector('#searchInput').value='revisión';f.click('#searchBtn');assert.equal(f.document.querySelectorAll('#searchResults .item-card').length,4);assert.match(f.document.querySelector('#searchStatus').textContent,/4 resultados/);
});
test('Places use four own fields and never the Cita capture form',()=>{
  const f=fixture();f.click('[data-panel="places"]');f.click('#placeAdd');const modal=f.document.querySelector('#modal');
  for(const id of ['placeTitle','placeDescription','placeLocation','placeLabel'])assert.ok(modal.querySelector('#'+id));assert.equal(modal.querySelectorAll('input,textarea,select').length,4);assert.equal(modal.querySelector('input[type="date"],input[type="time"],#captureText'),null);assert.equal(f.run('state.screen'),'panel');
  f.document.querySelector('#placeTitle').value='Cena mañana a las diez';f.document.querySelector('#placeDescription').value='Un sitio que me gustó.\nPedir la mesa del fondo.';f.document.querySelector('#placeLocation').value='https://maps.app.goo.gl/ejemplo';f.document.querySelector('#placeLabel').value='Restaurante';f.click('#placeSave');
  assert.ok(f.data.calls.some(c=>c[0]==='savePlace'));assert.ok(!f.data.calls.some(c=>['classify','classifyAppointment','saveCapture','replaceItemReminders'].includes(c[0])));assert.equal(f.data.collections.places[0].dueAt,0);assert.equal(f.run('state.lastPanel'),'places');assert.equal(f.document.querySelector('[data-place-card="301"] .place-label').textContent,'Restaurante');
});
test('All requested place labels persist when reopened',()=>{
  for(const label of ['Quiero ir','He estado','Restaurante','Lugar bonito']){
    const f=fixture();f.click('[data-panel="places"]');f.click('#placeAdd');f.document.querySelector('#placeTitle').value='Lugar de prueba';f.document.querySelector('#placeLabel').value=label;f.click('#placeSave');f.click('[data-open-place="301"]');assert.equal(f.document.querySelector('#placeLabel').value,label);
  }
});
test('Old restaurants open in the place editor and keep details and identity',()=>{
  const f=fixture();f.data.collections.places=[{id:77,type:'restaurant',title:'La terraza',body:'Paella y vistas al mar',locationText:'Calle Mar 4, Valencia',dueAt:12345678,recurrence:'weekly',attachmentPath:'/stored/photo',tags:'antiguo'}];f.click('[data-panel="places"]');f.click('[data-open-place="77"]');assert.equal(f.document.querySelector('#placeLabel').value,'Restaurante');assert.equal(f.document.querySelector('#placeLocation').value,'Calle Mar 4, Valencia');assert.equal(f.document.querySelector('#modal input[type="date"]'),null);f.document.querySelector('#placeLabel').value='He estado';f.click('#placeSave');assert.equal(f.data.collections.places[0].id,77);assert.equal(f.data.collections.places[0].attachmentPath,'/stored/photo');assert.equal(f.data.collections.places[0].body,'Paella y vistas al mar');assert.equal(f.data.collections.places[0].dueAt,0);
});
test('Place filters and accent-insensitive search work without losing focus',()=>{
  const f=fixture();f.data.collections.places=[{id:80,type:'place',title:'Garbí',body:'Vistas del mar',subcategory:'Lugar bonito'},{id:81,type:'place',title:'Cafetería',body:'Café excelente',subcategory:'He estado'},{id:82,type:'restaurant',title:'Casa Mar',body:'Arroces'}];f.click('[data-panel="places"]');const search=f.document.querySelector('#placeSearch');search.focus();search.value='garbi';search.dispatchEvent(new f.window.Event('input'));assert.equal(f.document.querySelectorAll('[data-place-card]').length,1);assert.equal(f.document.querySelector('#placeSearch'),search);assert.equal(f.document.activeElement,search);
  search.value='';search.dispatchEvent(new f.window.Event('input'));const filter=f.document.querySelector('#placeFilter');filter.value='Restaurante';filter.dispatchEvent(new f.window.Event('change'));assert.ok(f.document.querySelector('[data-place-card="82"]'));assert.equal(f.document.querySelectorAll('[data-place-card]').length,1);
  search.value='inexistente';search.dispatchEvent(new f.window.Event('input'));f.click('#placeResetFilter');assert.equal(f.document.querySelectorAll('[data-place-card]').length,3);
});
test('Place Maps actions use the saved or currently edited location exactly',()=>{
  const f=fixture();f.data.collections.places=[{id:83,type:'place',title:'Mirador',locationText:'https://maps.app.goo.gl/exacto'}];f.click('[data-panel="places"]');f.click('[data-place-map="83"]');assert.ok(f.data.calls.some(c=>c[0]==='openPlaceMap'&&c[1]==='https://maps.app.goo.gl/exacto'));
  f.click('[data-open-place="83"]');const location=f.document.querySelector('#placeLocation');location.value='Calle Luna 8, Valencia';location.dispatchEvent(new f.window.Event('input'));f.click('#placePreviewMap');assert.ok(f.data.calls.some(c=>c[0]==='openPlaceMap'&&c[1]==='Calle Luna 8, Valencia'));assert.ok(!f.data.calls.some(c=>c[0]==='openLocationDirections'));
});
test('A place without a location has no inactive Maps button',()=>{
  const f=fixture();f.data.collections.places=[{id:84,type:'place',title:'Pendiente de ubicar'}];f.click('[data-panel="places"]');assert.equal(f.document.querySelector('[data-place-map]'),null);f.click('[data-open-place="84"]');assert.ok(f.document.querySelector('#placePreviewMap').classList.contains('hidden'));
});
test('Blank titles and failed saves keep the place form and user text',()=>{
  const f=fixture();f.click('[data-panel="places"]');f.click('#placeAdd');f.click('#placeSave');assert.ok(!f.document.querySelector('#placeSaveError').classList.contains('hidden'));assert.equal(f.data.saved.length,0);
  f.document.querySelector('#placeTitle').value='Garbí';f.document.querySelector('#placeDescription').value='Texto por conservar';f.data.placeSaveFails=true;f.click('#placeSave');assert.equal(f.document.querySelector('#placeDescription').value,'Texto por conservar');assert.ok(!f.document.querySelector('#modal').classList.contains('hidden'));
});
test('Search results for old places use their own card and editor',()=>{
  const f=fixture(),place={id:85,type:'restaurant',title:'Terraza',body:'Vistas',locationText:'Valencia',dueAt:Date.now(),recurrence:'weekly'};f.data.collections.places=[place];f.data.searchResults=[place];f.click('[data-nav="search"]');f.document.querySelector('#searchInput').value='terraza';f.document.querySelector('#searchFilter').value='places';f.click('#searchBtn');assert.ok(f.document.querySelector('#searchResults [data-open-place="85"]'));assert.equal(f.document.querySelector('#searchResults [data-open-occurrence]'),null);f.click('[data-open-place="85"]');assert.ok(f.document.querySelector('#placeTitle'));assert.equal(f.document.querySelector('#editDate'),null);
});
test('Custom labels and markup-looking place text remain plain text',()=>{
  const f=fixture();f.data.collections.places=[{id:86,type:'place',title:'<img src=x onerror=alert(1)>',body:'<script>ejemplo</script>',subcategory:'Ruta personal <norte>',locationText:''}];f.click('[data-panel="places"]');assert.equal(f.document.querySelector('#panelContent img'),null);assert.equal(f.document.querySelector('#panelContent script'),null);f.click('[data-open-place="86"]');assert.equal(f.document.querySelector('#placeLabel').value,'Ruta personal <norte>');f.click('#placeSave');assert.equal(f.data.collections.places[0].subcategory,'Ruta personal <norte>');
});
test('Places can be deleted and restored through the existing undo action',()=>{
  const f=fixture();f.data.collections.places=[{id:87,type:'place',title:'Lugar temporal'}];f.click('[data-panel="places"]');f.click('[data-open-place="87"]');f.click('#placeDelete');f.click('#confirmDelete');assert.equal(f.data.collections.places.length,0);f.click('#toastAction');assert.equal(f.data.collections.places[0].id,87);assert.ok(f.document.querySelector('[data-place-card="87"]'));
});
function attachDocument(f,name='documento.pdf',path='/stored/documento.pdf'){
  f.click('#documentChoose');const request=f.run('state.documentEditor.request');
  f.window.NexoUI.onDocumentFileChosen(JSON.stringify({request,file:{path,name,mime:name.endsWith('.jpg')?'image/jpeg':'application/pdf'}}));
  f.flush();
}
function savedDocument(id=91,extra={}){return {id,type:'document',title:'Póliza',body:'Póliza\nTodas las coberturas',subcategory:'Seguro',attachmentPath:'/stored/poliza.pdf',attachmentName:'Póliza.pdf',attachmentMime:'application/pdf',...extra};}
test('Documents have only a description, optional type selector and file picker, without classification',()=>{
  const f=fixture();f.click('[data-panel="documents"]');f.click('#collectionAdd');const form=f.document.querySelector('.document-form');
  assert.equal(form.querySelectorAll('input,textarea,select').length,2);assert.ok(form.querySelector('#documentDescription'));assert.ok(form.querySelector('#documentLabel'));assert.equal(form.querySelector('input[type="date"],input[type="time"],#captureTitle'),null);
  f.document.querySelector('#documentDescription').value='Factura mañana a las 12\nTodas sus condiciones';f.document.querySelector('#documentLabel').value='Factura';attachDocument(f);f.click('#documentSave');
  assert.equal(f.data.collections.documents[0].type,'document');assert.equal(f.data.collections.documents[0].dueAt,0);assert.equal(f.data.saved[0].description,'Factura mañana a las 12\nTodas sus condiciones');assert.ok(!f.data.calls.some(c=>['classify','classifyAppointment','saveCapture'].includes(c[0])));assert.equal(f.run('state.lastPanel'),'documents');
});
test('Missing description or file and failed saves keep the document editor and contents',()=>{
  const f=fixture();f.click('[data-panel="documents"]');f.click('#collectionAdd');f.click('#documentSave');assert.match(f.document.querySelector('#documentSaveError').textContent,/descripción/);
  f.document.querySelector('#documentDescription').value='Contenido a conservar';f.click('#documentSave');assert.match(f.document.querySelector('#documentSaveError').textContent,/archivo/);attachDocument(f);f.data.documentSaveFails=true;f.click('#documentSave');assert.equal(f.document.querySelector('#documentDescription').value,'Contenido a conservar');assert.equal(f.run('state.documentEditor.file.path'),'/stored/documento.pdf');assert.equal(f.data.saved.length,0);
});
test('Picker cancellation and failed import keep the original file and enable retry',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-edit="91"]');
  for(const error of [undefined,'No se pudo cargar el archivo']){f.click('#documentChoose');assert.equal(f.document.querySelector('#documentSave').disabled,true);const request=f.run('state.documentEditor.request');f.window.NexoUI.onDocumentFileChosen(JSON.stringify({request,error}));assert.equal(f.run('state.documentEditor.file.path'),'/stored/poliza.pdf');assert.equal(f.document.querySelector('#documentSave').disabled,false);}
});
test('Replacing a draft attachment discards only its former temporary copy',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-edit="91"]');attachDocument(f,'new.pdf','/stored/new.pdf');assert.ok(!f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/poliza.pdf'));attachDocument(f,'next.jpg','/stored/next.jpg');assert.ok(f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/new.pdf'));f.click('#documentCancel');assert.ok(f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/next.jpg'));assert.equal(f.data.collections.documents[0].attachmentPath,'/stored/poliza.pdf');
});
test('Saving a replacement commits the chosen name and MIME without discarding the saved file',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-edit="91"]');attachDocument(f,'Foto.jpg','/stored/new.jpg');f.click('#documentSave');const i=f.data.collections.documents[0];assert.equal(i.attachmentPath,'/stored/new.jpg');assert.equal(i.attachmentName,'Foto.jpg');assert.equal(i.attachmentMime,'image/jpeg');assert.ok(!f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/new.jpg'));
});
test('A late file-picker response cannot attach a file to another editor',()=>{
  const f=fixture();f.click('[data-panel="documents"]');f.click('#collectionAdd');f.click('#documentChoose');const request=f.run('state.documentEditor.request');f.click('#modalClose');f.click('#collectionAdd');f.window.NexoUI.onDocumentFileChosen(JSON.stringify({request,file:{path:'/stored/late.pdf',name:'late.pdf'}}));assert.equal(f.run('state.documentEditor.file'),null);assert.ok(f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/late.pdf'));
});
test('Legacy titles, descriptions, custom labels and markup stay complete and literal',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument(92,{title:'<b>Contrato</b>',body:'<script>detalle</script>\nÚltima línea',subcategory:'Tipo personal',recurrence:'weekly',dueAt:123})];f.click('[data-panel="documents"]');assert.equal(f.document.querySelector('#documentList script'),null);f.click('[data-document-edit="92"]');assert.equal(f.document.querySelector('#documentDescription').value,'<b>Contrato</b>\n\n<script>detalle</script>\nÚltima línea');assert.equal(f.document.querySelector('#documentLabel').value,'Tipo personal');assert.equal(f.document.querySelector('#modal input[type="date"]'),null);f.click('#documentSave');assert.equal(f.data.collections.documents[0].id,92);
});
test('Blank and selected document labels persist when reopening',()=>{
  for(const label of ['', 'Identificación','Salud','Factura']){const f=fixture();f.click('[data-panel="documents"]');f.click('#collectionAdd');f.document.querySelector('#documentDescription').value='Documento';f.document.querySelector('#documentLabel').value=label;attachDocument(f);f.click('#documentSave');f.click('[data-document-edit="401"]');assert.equal(f.document.querySelector('#documentLabel').value,label);}
});
test('Open, download and share use the document file actions and never the calendar-sharing menu',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-open="91"]');f.click('[data-document-download="91"]');f.click('[data-document-share="91"]');assert.ok(f.data.calls.some(c=>c[0]==='openDocumentFile'&&c[1]==='/stored/poliza.pdf'&&c[2]==='Póliza.pdf'));assert.ok(f.data.calls.some(c=>c[0]==='downloadDocument'&&c[1]===91));assert.ok(f.data.calls.some(c=>c[0]==='shareDocument'&&c[1]===91));assert.ok(!f.data.calls.some(c=>['shareNexoItem','shareCalendarEvent'].includes(c[0])));
});
test('Preview opens the selected replacement instead of the previously stored attachment',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-edit="91"]');attachDocument(f,'Foto.jpg','/stored/new.jpg');f.click('#documentPreview');assert.ok(f.data.calls.some(c=>c[0]==='openDocumentFile'&&c[1]==='/stored/new.jpg'&&c[3]==='image/jpeg'));
});
test('Attachments associated with other modules stay accessible without being converted to documents',()=>{
  const f=fixture(),event=savedDocument(93,{type:'event',title:'Revisión',body:'Traer informe',dueAt:123,status:'pending',category:'Salud',subcategory:'Personal'});f.data.collections.documents=[event];f.data.appointments=[event];f.click('[data-panel="documents"]');assert.equal(f.document.querySelector('[data-document-edit="93"]'),null);f.click('[data-document-download="93"]');f.click('[data-document-share="93"]');assert.equal(event.type,'event');assert.equal(event.dueAt,123);assert.equal(event.subcategory,'Personal');assert.ok(!f.data.calls.some(c=>c[0]==='saveDocument'));
});
test('Searching documents matches accent-insensitive descriptions and filenames and keeps focus',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument(),savedDocument(94,{title:'Factura',body:'Factura lavadora',attachmentName:'Garantía.pdf',subcategory:'Factura'})];f.click('[data-panel="documents"]');const search=f.document.querySelector('#documentSearch');search.focus();search.value='garantia';search.dispatchEvent(new f.window.Event('input'));assert.equal(f.document.querySelectorAll('[data-document-card]').length,1);assert.ok(f.document.querySelector('[data-document-card="94"]'));assert.equal(f.document.activeElement,search);search.value='';search.dispatchEvent(new f.window.Event('input'));const filter=f.document.querySelector('#documentFilter');filter.value='Seguro';filter.dispatchEvent(new f.window.Event('change'));assert.equal(f.document.querySelectorAll('[data-document-card]').length,1);assert.ok(f.document.querySelector('[data-document-card="91"]'));
});
test('Global document search opens the simple editor even for an old dated record',()=>{
  const f=fixture(),doc=savedDocument(95,{recurrence:'weekly',dueAt:123});f.data.collections.documents=[doc];f.data.searchResults=[doc];f.click('[data-nav="search"]');f.document.querySelector('#searchInput').value='póliza';f.document.querySelector('#searchFilter').value='document';f.click('#searchBtn');f.click('[data-document-edit="95"]');assert.ok(f.document.querySelector('#documentDescription'));assert.equal(f.document.querySelector('#editDate'),null);
});
test('Sharing a file into Nexo opens Documents directly and skips date classification',()=>{
  const f=fixture();f.window.NexoUI.receiveShare(JSON.stringify({text:'Mañana a las 10',attachmentPath:'/stored/shared.pdf',attachmentName:'archivo.pdf',attachmentMime:'application/pdf'}));f.flush();assert.equal(f.run('state.lastPanel'),'documents');assert.equal(f.document.querySelector('#documentDescription').value,'Mañana a las 10');assert.ok(!f.data.calls.some(c=>['classify','classifyAppointment'].includes(c[0])));f.click('#documentCancel');assert.ok(f.data.calls.some(c=>c[0]==='discardDocumentFile'&&c[1]==='/stored/shared.pdf'));
});
test('Deleting a document supports Undo and restores its original file reference',()=>{
  const f=fixture();f.data.collections.documents=[savedDocument()];f.click('[data-panel="documents"]');f.click('[data-document-edit="91"]');f.click('#documentDelete');f.click('#confirmDelete');assert.equal(f.data.collections.documents.length,0);f.click('#toastAction');assert.equal(f.data.collections.documents[0].attachmentPath,'/stored/poliza.pdf');assert.ok(f.document.querySelector('[data-document-card="91"]'));
});
console.log(`UI integration: ${passed} scenarios passed`);
