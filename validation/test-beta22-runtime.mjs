import fs from 'node:fs';
import assert from 'node:assert/strict';
import {JSDOM, VirtualConsole} from 'jsdom';

const root=process.cwd();
const html=fs.readFileSync(root+'/app/src/main/assets/index.html','utf8');
const nav=fs.readFileSync(root+'/app/src/main/assets/navigation.js','utf8');
const app=fs.readFileSync(root+'/app/src/main/assets/app.js','utf8');

const errors=[];
const calls=[];
const virtualConsole=new VirtualConsole();
virtualConsole.on('jsdomError',e=>errors.push('jsdom: '+e.message));

const dom=new JSDOM(html,{
  runScripts:'outside-only',
  pretendToBeVisual:true,
  url:'https://nexo.local/',
  virtualConsole
});
const {window}=dom;
await new Promise(resolve=>{
  if(window.document.readyState==='complete')resolve();
  else window.addEventListener('load',resolve,{once:true});
});

window.scrollTo=()=>{};
window.requestAnimationFrame=cb=>{cb(Date.now());return 1;};
window.cancelAnimationFrame=()=>{};
window.HTMLElement.prototype.scrollIntoView=()=>{};
window.addEventListener('error',event=>errors.push(String(event.error?.stack||event.message||event.error||'window error')));

const objectMethods=new Set([
  'getHomeDashboard','getCommunicationCaptureSettings','getRecordingStatus','getLastParking',
  'getBackupStats','getMoreSummary','getToday','getTranscriptionModelStatus','getNexoFamiliaCloudStatus',
  'getNexoFamiliaImportStats','getMedicationMessageSettings','getInputHubSettings'
]);
const arrayMethods=new Set([
  'getNotes','getRecordings','getLists','getJournalEntries','getMedications','getMedicationTodayDoses',
  'getHistory','getInbox','getInboxCaptures','getPeople','getNexoFamiliaMembers','getCalendar',
  'getReminders','getFavorites','getCollection','getMedicationMessages'
]);
const nativeBase={
  getAppVersion:()=> '3.7.0-beta22',
  getDisplayMode:()=> 'comfortable',
  getStartScreen:()=> 'today',
  getHomeDashboard:()=> JSON.stringify({upcoming:[],todayCount:0,weekCount:0,inboxCount:0}),
  getFamilyStyleSearchIndex:()=> JSON.stringify({items:[],lists:[],medications:[],journal:[],inbox:[],recordings:[]}),
  getCommunicationCaptureSettings:()=> JSON.stringify({}),
  getRecordingStatus:()=> JSON.stringify({active:false,paused:false}),
  getLastParking:()=> JSON.stringify({}),
  getBackupStats:()=> JSON.stringify({}),
  getToday:()=> JSON.stringify({}),
  getMoreSummary:()=> JSON.stringify({}),
  getTranscriptionModelStatus:()=> JSON.stringify({installed:false}),
  getNexoFamiliaCloudStatus:()=> JSON.stringify({}),
  getNexoFamiliaImportStats:()=> JSON.stringify({}),
  getInboxCaptureCount:()=> 0,
  getPreferredShoppingList:()=> 0,
  getNextOccurrence:()=> 0,
  hasTranscription:()=> false,
  canScheduleExactAlarms:()=> true,
  getInputHubAutoScanEnabled:()=> false,
  isCommunicationCaptureEnabled:()=> false,
  isSmsCaptureEnabled:()=> false,
  isCalendarCaptureEnabled:()=> false
};
window.NexoNative=new Proxy(nativeBase,{
  get(target,prop){
    if(prop in target){
      const fn=target[prop];
      return (...args)=>{calls.push(String(prop));return fn(...args);};
    }
    return (...args)=>{
      const name=String(prop);calls.push(name);
      if(arrayMethods.has(name))return '[]';
      if(objectMethods.has(name))return '{}';
      if(/^get.*Count$/.test(name))return 0;
      if(/^get/.test(name))return '[]';
      if(/^(is|has|can)/.test(name))return false;
      if(/^(save|insert)/.test(name))return 1;
      return false;
    };
  }
});

window.eval(nav+'\n'+app+'\nwindow.__nexoTestInit=init;window.__nexoTestState=state;');
window.__nexoTestInit();

assert.equal(errors.length,0,'La inicialización lanzó errores: '+errors.join('\n'));
assert.equal(window.__nexoTestState.screen,'today');
assert.ok(calls.includes('getHomeDashboard'),'Inicio debe usar el resumen ligero getHomeDashboard');
assert.ok(!calls.includes('getCalendar'),'Inicio no debe expandir el calendario completo desde JavaScript');
assert.ok(!calls.includes('getFamilyStyleSearchIndex'),'Inicio no debe construir el índice global antes de buscar');

const expected={
  notes:'Notas',
  recordings:'Grabaciones',
  lists:'Listas',
  documents:'Documentos',
  journal:'Cuaderno',
  quotes:'Frases',
  medications:'Medicación',
  places:'Lugares',
  parking:'Aparcamiento',
  history:'Historial',
  backup:'Copia de seguridad'
};

for(const [panel,title] of Object.entries(expected)){
  const button=window.document.querySelector('[data-panel="'+panel+'"]');
  assert.ok(button,'Falta acceso data-panel='+panel);
  button.dispatchEvent(new window.MouseEvent('click',{bubbles:true,cancelable:true}));
  assert.equal(errors.length,0,'Click en '+panel+' lanzó errores: '+errors.join('\n'));
  assert.equal(window.__nexoTestState.screen,'panel','Click en '+panel+' no abrió pantalla panel');
  assert.equal(window.__nexoTestState.lastPanel,panel,'Click en '+panel+' abrió '+window.__nexoTestState.lastPanel);
  assert.equal(window.document.querySelector('#panelTitle')?.textContent,title,'Título incorrecto al abrir '+panel);
  assert.ok(window.document.querySelector('[data-screen="panel"]')?.classList.contains('active'),'Panel '+panel+' no quedó visible');
}

// Los cuatro accesos directos de Inicio deben usar el mismo cableado robusto.
for(const panel of ['notes','medications','lists','documents']){
  const button=window.document.querySelector('[data-screen="today"] [data-panel="'+panel+'"]');
  assert.ok(button,'Falta acceso directo de Inicio a '+panel);
  button.dispatchEvent(new window.MouseEvent('click',{bubbles:true,cancelable:true}));
  assert.equal(window.__nexoTestState.lastPanel,panel,'Acceso directo de Inicio no abre '+panel);
}

// El índice pesado se crea solo al buscar.
const beforeSearch=calls.filter(x=>x==='getFamilyStyleSearchIndex').length;
const input=window.document.querySelector('#homeSearchInput');
input.value='seguro';
input.dispatchEvent(new window.Event('input',{bubbles:true}));
const afterSearch=calls.filter(x=>x==='getFamilyStyleSearchIndex').length;
assert.equal(afterSearch,beforeSearch+1,'La búsqueda debe crear el índice una sola vez bajo demanda');

assert.equal(errors.length,0,'Errores finales de runtime: '+errors.join('\n'));
console.log('OK: runtime WebView simulado, Inicio no bloqueante y accesos data-panel operativos');
