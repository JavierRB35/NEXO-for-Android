'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const start=source.indexOf('function requestNexoAiCapture(){');
const end=source.indexOf('function analyzeCapture(options={}) {',start);
assert(start>0&&end>start, 'Localizar el módulo de consulta IA');
const requests=[],toasts=[],statuses=[],applied=[],confirmed=[];
const elements={
  '#captureText':{value:'El viernes acompañar a Paco al médico a las once'},
  '#captureAiAnalyze':{disabled:false,textContent:'✦ Mejorar propuesta con IA'}
};
const state={screen:'save',classification:{type:'event',title:'Propuesta local',category:'Otro'},captureParticipantIds:[]};
let gemini=true,allow=true,category='event',locked=false,persons=[{id:4,name:'Paco',color:'#8B5CF6'}];
const context={
  state,
  $:id=>elements[id]||null,
  window:{confirm:msg=>{confirmed.push(msg);return allow;}},
  NexoNative:{
    getNexoAiSettings:()=>JSON.stringify({geminiConfigured:gemini,groqEnabled:false}),
    analyzeNexoCapture:text=>requests.push(text),
    getPeople:()=>JSON.stringify(persons)
  },
  parseNative:(raw,fallback)=>{try{return JSON.parse(raw)}catch(_){return fallback}},
  isAppointmentType:type=>type==='event'||type==='reminder'||type==='task',
  captureReviewType:()=>category,
  toast:msg=>toasts.push(msg),
  setInterpretationStatus:(kind,text)=>statuses.push({kind,text}),
  renderClassification:(value,fill)=>applied.push({value,fill}),
  familySearchNormalize:value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim(),
  captureFieldLocked:()=>locked,
  renderCapturePeopleAssignment:()=>{}
};
const {requestNexoAiCapture,receiveNexoAiCapture}=vm.runInNewContext(
  source.slice(start,end)+'\n({requestNexoAiCapture,receiveNexoAiCapture})',context);
gemini=false;requestNexoAiCapture();
assert.equal(requests.length,0,'Sin clave: no se envía texto');
gemini=true;allow=false;requestNexoAiCapture();
assert.equal(requests.length,0,'Sin consentimiento: no se envía texto');
allow=true;category='note';requestNexoAiCapture();
assert.equal(requests.length,0,'Solo citas y recordatorios');
category='event';requestNexoAiCapture();
assert.equal(requests.length,1);
assert.equal(requests[0],elements['#captureText'].value);
assert.equal(elements['#captureAiAnalyze'].disabled,true);
assert.match(confirmed.at(-1),/únicamente este texto/);
receiveNexoAiCapture(JSON.stringify({ok:true,provider:'Gemini',classification:{
  type:'event',title:'Acompañar a Paco al médico',category:'Acompañar (salud)',
  dueDate:'2026-11-27',dueTime:'11:00',hasTime:true,personName:'Paco'
}}));
assert.equal(applied.length,1);
assert.equal(applied[0].fill,true);
assert.equal(applied[0].value.title,'Acompañar a Paco al médico');
assert.deepEqual(Array.from(state.captureParticipantIds),[4],'Asociación exacta y única');
assert.equal(elements['#captureAiAnalyze'].disabled,false);
assert.equal(state.captureAiPendingText,'');
assert.match(statuses.at(-1).text,/No se ha guardado/);
persons=[{id:4,name:'Paco'},{id:5,name:'Paco'}];
requestNexoAiCapture();
receiveNexoAiCapture(JSON.stringify({ok:true,classification:{title:'Otra cita',category:'Acompañar (salud)',personName:'Paco'}}));
assert.equal(state.captureParticipantIds[0],4,'Una coincidencia ambigua no cambia la asignación');
locked=true;persons=[{id:9,name:'Paco'}];
requestNexoAiCapture();
receiveNexoAiCapture(JSON.stringify({ok:true,classification:{title:'Tercera cita',category:'Acompañar (salud)',personName:'Paco'}}));
assert.equal(state.captureParticipantIds[0],4,'No reemplazar la asignación manual');
locked=false;const before=applied.length;
requestNexoAiCapture();
elements['#captureText'].value+=' y otra cosa';
receiveNexoAiCapture(JSON.stringify({ok:true,classification:{title:'Respuesta obsoleta',category:'Otro'}}));
assert.equal(applied.length,before,'Si el texto cambió, la respuesta antigua se ignora');
elements['#captureText'].value=requests[requests.length-1];
requestNexoAiCapture();
receiveNexoAiCapture(JSON.stringify({ok:false,error:'Sin cuota'}));
assert.equal(elements['#captureAiAnalyze'].disabled,false,'Permitir reintento');
assert.equal(applied.length,before,'No sobrescribir captura cuando la IA falla');
assert(toasts.includes('Sin cuota'));
assert(!source.slice(start,end).includes('saveCapture('));
assert(!source.slice(start,end).includes('commitCapture('));
console.log('OK: autorización, IA bajo demanda, asociación inequívoca, borradores y respuestas obsoletas');
