/* Pruebas de duplicación sin persistencia accidental ni series de avisos multiplicadas. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
function extract(name){
  const start=source.indexOf('function '+name+'(');
  assert(start>=0,'Falta '+name);
  const open=source.indexOf('{',start);let depth=0;
  for(let i=open;i<source.length;i++){
    if(source[i]==='{')depth++;
    if(source[i]==='}'&&!--depth)return source.slice(start,i+1);
  }
  throw Error('Función incompleta: '+name);
}
assert(html.includes('id="captureCloneNotice"'));
for(const token of [
  'function duplicateCalendarEvent(', 'id="agendaDuplicateAction"',
  "'duplicateItemBtn'", "'duplicateOccurrenceBtn'",
  "$('#duplicateItemBtn')?.addEventListener('click'",
  "$('#duplicateOccurrenceBtn').addEventListener('click'",
  "$('#agendaDuplicateAction')?.addEventListener('click'"
])assert(source.includes(token),'No está conectado: '+token);
const base={id:17,type:'event',title:'Cita con el dentista',body:'Revisar informe',
  category:'Salud',priority:2,dueAt:Date.UTC(2026,9,21,8,30),endAt:Date.UTC(2026,9,21,9,30),
  hasTime:true,allDay:false,locationText:'Calle Mayor 9',
  mapsUrl:'https://maps.google.com/?q=Valencia',timeZone:'Europe/Madrid',
  participantIds:[5,9,5],voiceEnabled:true,recurrence:'none',
  source:'nexo-familia',updateItemId:999,inboxCaptureId:50};
const recurring={...base,id:17,title:'Yoga',recurrence:'weekly',
  dueAt:Date.UTC(2026,9,28,18,0),endAt:Date.UTC(2026,9,28,19,0),
  recurrenceUntil:Date.UTC(2027,0,1,0),participantIds:[9]};
const fields=[
'captureText','captureType','captureHeading','captureTitle','captureDescription','captureCategory',
'captureDate','captureEndDate','captureAllDay','captureTime','captureEndTime','captureLocation',
'captureMapsUrl','captureTimeZone','capturePriority','captureRecurrence','captureRecurrenceUntil',
'captureRecurrenceUntilLabel','captureVoiceEnabled','captureDetails','captureCloneNotice'
];
function testCase(rec=false){
  const elements={};
  for(const id of fields){
    let hidden=true;
    elements[id]={id,value:'',checked:false,open:false,textContent:'',
      focus(){this.focused=true;},
      classList:{add(name){if(name==='hidden')hidden=true;},remove(name){if(name==='hidden')hidden=false;},contains(name){return name==='hidden'&&hidden;} }};
  }
  const calls={saved:0,updated:0,navigated:[],closed:0,cleared:0,alerts:0,rendered:0};
  const state={captureParticipantIds:[222],captureAlerts:[],captureSuggestedAlert:777,
    capturePinnedType:'',capturePinnedDate:'',inboxCaptureId:999,calendarUpdateItemId:777};
  const ctx={
    Number,String,Date,JSON,
    state,CATEGORIES:['Salud','Familia','Otro'],
    $:id=>elements[id.replace(/^#/,'')],
    parseNative:(s,fallback)=>{try{return JSON.parse(s);}catch{return fallback;}},
    isAppointmentType:t=>['event','task','reminder','expiry'].includes(t),
    normalizeAlertMinutes:values=>[...new Set(values)].sort((a,b)=>a-b),
    normalizeParticipantIds:ids=>[...new Set(ids.map(Number))],
    categoryLabel:c=>c,
    updateCategorySelectIcon:()=>{},
    isoFromMs:ms=>({date:new Date(ms).toISOString().slice(0,10),time:new Date(ms).toISOString().slice(11,16)}),
    itemEffectiveEndAt:i=>i.endAt||i.dueAt,
    endTimeForItem:i=>new Date(i.endAt).toISOString().slice(11,16),
    eventLocationValue:i=>i.locationText||i.mapsUrl||'',
    getItemAlertMinutes:()=>[15,60],
    closeModal:forced=>{assert.equal(forced,true);calls.closed++;},
    navigate:screen=>calls.navigated.push(screen),
    clearCapture:()=>{
      calls.cleared++;state.inboxCaptureId=0;state.calendarUpdateItemId=0;
      state.captureAlerts=[];state.captureParticipantIds=[];elements.captureCloneNotice.classList.add('hidden');
    },
    syncCaptureAllDay:()=>{},renderCapturePeopleAssignment:()=>calls.rendered++,
    refreshCaptureAlertEditor:()=>calls.alerts++,
    requestAnimationFrame:fn=>fn(),
    toast:message=>calls.toast=message,
    NexoNative:{
      getItem:id=>{assert.equal(id,17);return JSON.stringify(base);},
      getOccurrence:(id,at)=>{assert.equal(id,17);assert.equal(at,recurring.dueAt);return JSON.stringify(recurring);},
      saveCapture:()=>{calls.saved++;throw Error('Guardado automático inesperado');},
      updateItem:()=>{calls.updated++;throw Error('Modificación del original inesperada');}
    }
  };
  vm.createContext(ctx);vm.runInContext(extract('duplicateCalendarEvent'),ctx);
  ctx.duplicateCalendarEvent(17,rec?recurring.dueAt:0,rec);
  const e=id=>elements[id];
  assert.equal(calls.saved,0);assert.equal(calls.updated,0);
  assert.equal(calls.closed,1);assert.equal(calls.cleared,1);
  assert.deepEqual(calls.navigated,['save']);
  assert.equal(e('captureHeading').textContent,'Duplicar evento');
  assert.equal(e('captureTitle').value,rec?'Yoga':base.title);
  assert.equal(e('captureDescription').value,base.body);
  assert.equal(e('captureCategory').value,'Salud');
  assert.equal(e('captureDate').value,rec?'2026-10-28':'2026-10-21');
  assert.equal(e('captureEndDate').value,e('captureDate').value);
  assert.equal(e('captureTime').value,rec?'18:00':'08:30');
  assert.equal(e('captureEndTime').value,rec?'19:00':'09:30');
  assert.equal(e('captureLocation').value,base.locationText);
  assert.equal(e('captureMapsUrl').value,base.mapsUrl);
  assert.equal(e('capturePriority').value,'2');
  assert.equal(e('captureType').value,'event');
  assert.equal(e('captureRecurrence').value,'none');
  assert.equal(e('captureRecurrenceUntil').value,'');
  assert.equal(state.inboxCaptureId,0);
  assert.equal(state.calendarUpdateItemId,0);
  assert.equal(state.capturePinnedType,'event');
  assert.deepEqual([...state.captureParticipantIds],rec?[9]:[5,9]);
  assert.deepEqual([...state.captureAlerts],[15,60]);
  assert.equal(e('captureVoiceEnabled').checked,true);
  assert.equal(e('captureDetails').open,true);
  assert.equal(e('captureCloneNotice').classList.contains('hidden'),false);
  assert(e('captureCloneNotice').textContent.includes('no se modificará'));
  if(rec)assert(e('captureCloneNotice').textContent.includes('sin repetición'));
  assert.equal(e('captureDate').focused,true);
  assert.equal(calls.rendered,1);
  assert.equal(calls.alerts,1);
}
testCase(false);
testCase(true);
console.log('Duplicar eventos: ficha normal + aparición recurrente, prellenado, avisos, participantes, sin escrituras ni repetición accidental: OK');
