import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

// Simula la WebView al iniciar Nexo: tiempo, Inbox y actualizaciones no bloquean la UI.
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const weather=source.slice(source.indexOf('function updateHeaderWeather(data){'),source.indexOf('function journalPromptText(',source.indexOf('function updateHeaderWeather(data){')));
const startup=source.slice(source.indexOf('function finishStartupSplash(){'),source.indexOf('function init(){',source.indexOf('function finishStartupSplash(){')));
assert(weather.includes('function updateHeaderWeather(data)'));
assert(startup.includes('function beginStartupSequence(startScreen)'));

function setup(snapshot,{scanStarted=false,navigateFails=false,startScreen='today'}={}){
  const html='<body class="startup-loading"><div id="startupSplash"><div id="startupSplashStatus">Buscando en Inbox…</div></div>'+
    '<div id="headerWeatherBtn"><span id="headerWeatherIcon">⛅</span><span id="headerWeatherTemp">…</span>'+
    '<span id="headerWeatherPlace">Consultando tiempo…</span></div></body>';
  const dom=new JSDOM(html,{url:'https://nexo.local/',runScripts:'outside-only'});
  const w=dom.window,doc=w.document,queue=[],calls={inbox:0,home:0,updates:0,navigate:0,calendarReset:0,calendarRender:0};
  w.state={screen:'today',startupSplashActive:false,startupInboxReady:false,startupHomeReady:false,
    startupUiReady:false,startupSplashTimeout:0,startupWeatherReady:false,startupHomeDashboard:null};
  w.$=s=>doc.querySelector(s);
  w.NexoNative={
    getStartupWeatherSnapshot:()=>JSON.stringify(snapshot),
    startStartupInboxScan:()=>{calls.inbox++;return scanStarted;},
    getHomeDashboard:()=>{calls.home++;return JSON.stringify({});},
    onStartupReadyForUpdates:()=>{calls.updates++;}
  };
  w.parseNative=(s,f)=>{try{return JSON.parse(s)}catch{return f}};
  w.resetCalendarToToday=()=>{calls.calendarReset++};
  w.renderCalendar=()=>{calls.calendarRender++};
  w.navigate=screen=>{calls.navigate++;if(navigateFails)throw new Error('fallo al construir pantalla');w.state.screen=screen};
  w.updateFloatingActions=()=>{};
  w.requestAnimationFrame=fn=>fn();
  w.setTimeout=(fn,delay)=>{queue.push({fn,delay});return queue.length;};
  w.clearTimeout=()=>{};
  w.eval(weather+"\n"+startup);
  if(navigateFails)assert.throws(()=>w.beginStartupSequence(startScreen),/fallo al construir pantalla/);
  else w.beginStartupSequence(startScreen);
  const fire=delay=>{const timer=queue.find(x=>x.delay===delay);assert(timer,'Falta temporizador '+delay);timer.fn();};
  return {w,doc,fire,calls};
}
const fresh={ok:true,temperature:23.5,code:0,isDay:true,name:'La Pobla',stale:false,cachedAt:Date.now()};
const cache=setup(fresh);
assert.equal(cache.doc.querySelector('#headerWeatherTemp').textContent,'24°');
assert.equal(cache.w.state.startupWeatherReady,true);
assert(!cache.doc.body.classList.contains('startup-loading'),'Sin espera mínima al tener clima reciente');
assert.equal(cache.calls.inbox,1,'El escaneo de Inbox sigue arrancando');
assert.equal(cache.calls.home,1,'Inicio sigue precargando su información');
assert.equal(cache.calls.navigate,1,'La navegación ocurre antes de retirar la portada');
cache.fire(350);
assert.equal(cache.calls.updates,1,'Las actualizaciones se siguen comprobando después de abrir');

const stale={...fresh,temperature:21,stale:true,cachedAt:Date.now()-35*60*1000};
const old=setup(stale);
assert.match(old.doc.querySelector('#headerWeatherPlace').textContent,/anterior/);
assert.equal(old.w.state.startupWeatherReady,false,'Se distingue el tiempo obsoleto');
assert(!old.doc.body.classList.contains('startup-loading'),'No bloquea por tiempo pendiente');
old.w.updateHeaderWeather({...fresh,temperature:25});
assert.equal(old.doc.querySelector('#headerWeatherTemp').textContent,'25°','El tiempo se actualiza después de abrir');

const pending=setup({}, {scanStarted:true});
assert.equal(pending.w.state.startupInboxReady,false,'Inbox sigue en segundo plano');
assert(!pending.doc.body.classList.contains('startup-loading'),'Inbox no bloquea la interacción');
assert.equal(pending.calls.inbox,1);

const calendar=setup({}, {scanStarted:true,startScreen:'calendar'});
assert.equal(calendar.w.state.screen,'calendar');
assert.equal(calendar.calls.calendarReset,1,'El calendario se abre centrado en hoy');
assert.equal(calendar.calls.calendarRender,1,'Se conserva el repintado inicial del calendario');
assert(!calendar.doc.body.classList.contains('startup-loading'),'Calendario usable sin esperar a Inbox ni tiempo');

const offline=setup({});
assert(!offline.doc.body.classList.contains('startup-loading'),'No depende de red');
offline.w.updateHeaderWeather({ok:false,reason:'network'});
assert.equal(offline.doc.querySelector('#headerWeatherTemp').textContent,'N/D');
assert.equal(offline.doc.querySelector('#headerWeatherPlace').textContent,'Tiempo no disponible');

const error=setup({});
error.w.updateHeaderWeather({ok:false,reason:'permission'});
assert.equal(error.doc.querySelector('#headerWeatherPlace').textContent,'Permite ubicación');

const broken=setup({}, {navigateFails:true});
assert(broken.doc.body.classList.contains('startup-loading'),'Si falla la UI, no se oculta prematuramente');
broken.fire(8500);
assert(!broken.doc.body.classList.contains('startup-loading'),'El límite de seguridad sigue funcionando');

console.log('OK: pantalla rápida; Inbox, Inicio, clima y actualizaciones conservados');
