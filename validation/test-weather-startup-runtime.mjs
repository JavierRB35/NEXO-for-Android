import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

// Simula la WebView al iniciar Nexo; controla explícitamente reloj y datos
// nativos para verificar el contrato de splash sin llamar a redes reales.
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const weather=source.slice(source.indexOf('function updateHeaderWeather(data){'),source.indexOf('function journalPromptText(',source.indexOf('function updateHeaderWeather(data){')));
const startup=source.slice(source.indexOf('function finishStartupSplash(){'),source.indexOf('function init(){',source.indexOf('function finishStartupSplash(){')));
assert(weather.includes('function updateHeaderWeather(data)'), 'Función meteorológica visible');
assert(startup.includes('function beginStartupSequence(startScreen)'),'Preload de arranque');

function setup(snapshot){
  const html='<body class="startup-loading"><div id="startupSplash"><div id="startupSplashStatus">Buscando en Inbox…</div></div>'+
    '<div id="headerWeatherBtn"><span id="headerWeatherIcon">⛅</span><span id="headerWeatherTemp">…</span>'+
    '<span id="headerWeatherPlace">Consultando tiempo…</span></div></body>';
  const dom=new JSDOM(html,{url:'https://nexo.local/',runScripts:'outside-only'});
  const w=dom.window,doc=w.document,queue=[];
  w.state={screen:'today',startupSplashActive:false,startupInboxReady:false,startupHomeReady:false,
    startupSplashMinReady:false,startupSplashMinTimer:0,startupSplashTimeout:0,startupWeatherReady:false,
    startupHomeDashboard:null};
  w.$=s=>doc.querySelector(s);
  w.NexoNative={getStartupWeatherSnapshot:()=>JSON.stringify(snapshot),startStartupInboxScan:()=>false,
    getHomeDashboard:()=>JSON.stringify({})};
  w.parseNative=(s,f)=>{try{return JSON.parse(s)}catch{return f}};
  w.navigate=()=>{w.state.screen='today'};
  w.updateFloatingActions=()=>{};
  w.requestAnimationFrame=fn=>fn();
  w.setTimeout=(fn,delay)=>{queue.push({fn,delay});return queue.length;};
  w.clearTimeout=()=>{};
  w.eval(weather+"\n"+startup);
  w.beginStartupSequence('today');
  const fire=delay=>{const timer=queue.find(x=>x.delay===delay);assert(timer,'Falta temporizador '+delay);timer.fn();};
  return {w,doc,fire};
}
const fresh={ok:true,temperature:23.5,code:0,isDay:true,name:'La Pobla',stale:false,cachedAt:Date.now()};
const cache=setup(fresh);
assert.equal(cache.doc.querySelector('#headerWeatherTemp').textContent,'24°');
assert.equal(cache.w.state.startupWeatherReady,true);
assert(cache.doc.body.classList.contains('startup-loading'));
cache.fire(3500);
assert.equal(cache.w.state.startupSplashActive,false,'Caché reciente habilita apertura con mínimo 3,5s');

const stale={...fresh,temperature:21,stale:true,cachedAt:Date.now()-35*60*1000};
const old=setup(stale);
assert.equal(old.doc.querySelector('#headerWeatherTemp').textContent,'21°');
assert.match(old.doc.querySelector('#headerWeatherPlace').textContent,/anterior/);
assert.equal(old.w.state.startupWeatherReady,false,'Caché antigua no evita actualización');
old.fire(3500);
assert(old.doc.body.classList.contains('startup-loading'),'Splash sigue mientras espera el clima');
old.w.updateHeaderWeather({...fresh,temperature:25});
old.w.state.startupWeatherReady=true;old.w.maybeFinishStartupSplash();
assert.equal(old.doc.querySelector('#headerWeatherTemp').textContent,'25°');
assert.equal(old.w.state.startupSplashActive,false,'Nueva lectura permite entrar al Inicio');

const offline=setup({});
offline.fire(3500);
assert(offline.doc.body.classList.contains('startup-loading'),'No aparentar datos disponibles');
offline.fire(8500);
assert(!offline.doc.body.classList.contains('startup-loading'),'Límite de seguridad evita splash infinito');
offline.w.updateHeaderWeather({ok:false,reason:'network'});
assert.equal(offline.doc.querySelector('#headerWeatherTemp').textContent,'N/D');
assert.equal(offline.doc.querySelector('#headerWeatherPlace').textContent,'Tiempo no disponible');

const error=setup({});
error.w.updateHeaderWeather({ok:false,reason:'permission'});
error.w.state.startupWeatherReady=true;error.w.maybeFinishStartupSplash();
error.fire(3500);
assert.equal(error.w.state.startupSplashActive,false);
assert.equal(error.doc.querySelector('#headerWeatherPlace').textContent,'Permite ubicación');

console.log('OK: clima durante splash, caché identificada, éxito, error y salida segura a 8,5s');
