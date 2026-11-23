'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const start=source.indexOf('function renderNexoAiSettings(){');
const end=source.indexOf('function setInterpretationStatus(kind,text) {',start);
assert(start>0&&end>start);
let auto=false,gemini=true,groq=true,groqEnabled=true,allow=true,manualLocked=false;
const sent=[],applied=[],statuses=[],toasts=[],confirmations=[],timers=new Map(),saved=[];
let nextTimer=0;
let people=[{id:4,name:'Paco'},{id:7,name:'María'}];
const inputs={
 '#captureText':{value:'El viernes acompañar a Paco y María al médico a las once'},
 '#captureAiAnalyze':{disabled:false,textContent:'✦ Reintentar con IA'},
 '#nexoAiAutomatic':{checked:false,disabled:false},
 '#nexoAiGroqEnable':{checked:true,disabled:false},
 '#nexoAiGeminiStatus':{textContent:''},
 '#nexoAiGroqStatus':{textContent:''}
};
const state={
 screen:'save',capturePinnedType:'event',capturePinnedDate:'',inboxCaptureId:0,
 classification:null,captureParticipantIds:[],captureAiTimer:0,
 captureAiActive:false,captureAiSerial:0,captureAiPendingId:0,
 captureAiPendingText:'',captureAiDesiredText:'',captureAiLastSuccessfulText:'',captureAiSuppressed:false
};
const context={
 state, $:id=>inputs[id]||null,
 window:{confirm:msg=>{confirmations.push(msg);return allow}},
 NexoNative:{
   getNexoAiSettings:()=>JSON.stringify({autoEnabled:auto,geminiConfigured:gemini,groqConfigured:groq,groqEnabled}),
   setNexoAiAutomatic:enabled=>{saved.push(enabled);auto=enabled;return '{"ok":true}'},
   analyzeNexoCapture:(text,id)=>sent.push({text,id}),
   classifyAppointment:()=>JSON.stringify({type:'event',title:'Algoritmo local',category:'Otro'}),
   classify:()=>JSON.stringify({type:'note',title:'Nota local'}),
   getPeople:()=>JSON.stringify(people)
 },
 setTimeout:(cb,ms)=>{let id=++nextTimer;timers.set(id,cb);return id},
 clearTimeout:id=>timers.delete(id),
 parseNative:(v,fb)=>{try{return JSON.parse(v)}catch{return fb}},
 isAppointmentType:type=>type==='event'||type==='reminder'||type==='task',
 captureReviewType:()=>state.capturePinnedType,
 setInterpretationStatus:(kind,msg)=>statuses.push({kind,msg}),
 toast:msg=>toasts.push(msg),
 captureFieldLocked:()=>manualLocked,
 familySearchNormalize:s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim(),
 normalizeParticipantIds:ids=>[...new Set(ids)],
 renderCapturePeopleAssignment:()=>{},
 renderClassification:(proposal,fill)=>{applied.push({proposal,fill});state.classification=proposal}
};
const api=vm.runInNewContext(source.slice(start,end)+
 '\n({renderNexoAiSettings,setNexoAiAutomaticFromSettings,requestNexoAiCapture,'+
 'receiveNexoAiCapture,analyzeCapture,resetNexoAiCapture})',context);
function fire(){const tasks=[...timers.values()];timers.clear();for(const cb of tasks)cb();}
function receive(data){api.receiveNexoAiCapture(JSON.stringify({...data,requestId:sent.at(-1).id}));}
const original=inputs['#captureText'].value;
api.analyzeCapture();
assert.equal(sent.length,0,'Sin consentimiento: algoritmo local');
assert.equal(applied.at(-1).proposal.title,'Algoritmo local');
auto=true;gemini=false;groq=false;groqEnabled=false;
api.analyzeCapture();fire();
assert.equal(sent.length,0,'Sin claves: algoritmo local');
assert.equal(applied.at(-1).proposal.title,'Algoritmo local');
gemini=true;groq=true;groqEnabled=true;
let before=applied.length;
api.analyzeCapture();
assert.equal(sent.length,0,'Debounce: no consultar en cada tecla');
assert.equal(applied.length,before,'IA prioritaria: no aplicar local antes de la respuesta');
fire();
assert.equal(sent.length,1);
assert.equal(sent[0].text,original);
assert.equal(inputs['#captureAiAnalyze'].disabled,true);
receive({ok:true,provider:'Gemini',classification:{
  type:'event',title:'Visita al médico',category:'Acompañar (salud)',
  dueDate:'2026-11-27',dueTime:'11:00',dueEndDate:'2026-11-27',dueEndTime:'12:00',
  hasTime:true,allDay:false,timeZone:'Europe/Madrid',locationText:'Calle Mayor 3',
  mapsUrl:'',priority:2,recurrence:'weekly',recurrenceUntilDate:'2027-02-01',
  reminderLeadMinutes:[30,1440],reminderRequested:true,voiceRequested:true,
  personNames:['Paco','María']
}});
assert.equal(applied.length,before+1);
assert.equal(applied.at(-1).proposal.locationText,'Calle Mayor 3');
assert.equal(applied.at(-1).proposal.priority,2);
assert.equal(applied.at(-1).proposal.recurrence,'weekly');
assert.equal(applied.at(-1).proposal.voiceRequested,true);
assert.deepEqual(Array.from(state.captureParticipantIds),[4,7],'Varias personas identificadas localmente');
assert.equal(state.captureAiLastSuccessfulText,original);
assert.equal(inputs['#captureAiAnalyze'].disabled,false);
assert.equal(state.captureAiActive,false);
api.analyzeCapture();fire();
assert.equal(sent.length,1,'Sin consultar dos veces el mismo texto ya completado');

inputs['#captureText'].value=original+' y traer resultados';
api.analyzeCapture();fire();
assert.equal(sent.length,2);
inputs['#captureText'].value+=' de analítica';
api.analyzeCapture();
receive({ok:true,provider:'Gemini',classification:{title:'Respuesta antigua',category:'Otro'}});
assert.notEqual(applied.at(-1).proposal.title,'Respuesta antigua','Ignorar respuesta obsoleta');
fire();assert.equal(sent.length,3,'Reintentar con la última versión del texto');
const currentText=inputs['#captureText'].value;
receive({ok:false,error:'Sin IA disponible'});
assert.equal(applied.at(-1).proposal.title,'Algoritmo local','Dos IAs fallidas: fallback real');
assert.equal(state.captureAiSuppressed,true);
assert.match(statuses.at(-1).msg,/algoritmo local/);
inputs['#captureText'].value+=' nuevo detalle';
api.analyzeCapture();fire();
assert.equal(sent.length,3,'Sin tormenta de peticiones tras fallo de proveedores');
inputs['#captureText'].value=currentText;
auto=false;allow=false;
api.requestNexoAiCapture();fire();
assert.equal(sent.length,3,'Reintento manual sin consentimiento no envía');
allow=true;api.requestNexoAiCapture();fire();
assert.equal(sent.length,4,'El reintento manual sí es posible');
manualLocked=true;
receive({ok:true,provider:'Groq (respaldo)',classification:{title:'Cita nueva',category:'Familia',personNames:['Otra persona']}});
assert.deepEqual(Array.from(state.captureParticipantIds),[4,7],'No pisar personas seleccionadas manualmente');
manualLocked=false;
inputs['#nexoAiAutomatic'].checked=true;
api.setNexoAiAutomaticFromSettings();
assert.equal(saved.at(-1),true);
assert.match(confirmations.at(-1),/envío automático/);
api.renderNexoAiSettings();
assert.equal(inputs['#nexoAiAutomatic'].checked,true);
api.resetNexoAiCapture();
api.receiveNexoAiCapture(JSON.stringify({ok:true,requestId:sent.at(-1).id,classification:{title:'Tardía'}}));
assert.equal(applied.at(-1).proposal.title,'Cita nueva','Respuesta después de limpiar, ignorada');
assert(source.includes("if(state.captureAiActive||state.captureAiTimer){"),'Impedir guardado antes de revisión IA');
console.log('OK: prioridad IA, debounce, consentimiento, campos completos, personas, respuestas caducadas, Groq y fallback local');
