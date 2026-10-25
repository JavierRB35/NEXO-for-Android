#!/usr/bin/env node
/* Regresión visual aislada: temas sin mutar navegación, datos ni funciones. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const js=fs.readFileSync(path.join(root,'app/src/main/assets/nexo-themes.js'),'utf8');
const css=fs.readFileSync(path.join(root,'app/src/main/assets/nexo-themes.css'),'utf8');
const main=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const THEME_KEY='nexo.visual.theme.v1';
const names=['vivo','mediterraneo','noche'];
assert.ok(html.indexOf('<script src="nexo-themes.js"></script>')<html.indexOf('<link rel="stylesheet" href="styles.css">'),'El tema debe aplicarse antes del primer pintado');
assert.ok(html.indexOf('<link rel="stylesheet" href="nexo-themes.css">')>html.indexOf('vehicles.css'),'No alterar estilo existente ni tamaño del calendario');
assert.equal((html.match(/data-settings-accordion="appearance"/g)||[]).length,1);
assert.equal((html.match(/data-settings-section="appearance"/g)||[]).length,1);
assert.ok(html.indexOf('data-settings-accordion="appearance"')<html.indexOf('data-settings-accordion="display"'));
for(const name of names){
  assert.equal((html.match(new RegExp('data-nexo-theme-choice="'+name+'"','g'))||[]).length,1,name+' debe tener selector único');
  assert.match(css,new RegExp(':root\\[data-nexo-theme="'+name+'"\\]'),'Faltan variables '+name);
}
assert.match(css,/prefers-reduced-motion:reduce/,'Accesibilidad: animaciones reducibles');
assert.match(css,/--nx-on-primary/,'Contraste de los botones');
assert.ok(!js.includes('NexoNative'),'El selector visual no debe acceder a datos ni al bridge de Android');
assert.ok(!js.includes('fetch('),'Los temas deben funcionar sin red');
assert.ok(!main.includes('nexo.visual.theme.v1'),'La lógica de negocio no debe gestionar temas');
assert.ok(!css.includes('.tgv-event--dragging{'),'No tocar comportamiento visual de arrastre');
assert.ok(!css.includes('touch-action:'),'No modificar gestos');

function execute(saved,denyStorage=false){
  const data=new Map();
  if(saved!==undefined)data.set(THEME_KEY,saved);
  const writes=[];
  const buttons=names.map(name=>({
    dataset:{nexoThemeChoice:name},
    attrs:{},
    classes:new Set(),
    handlers:{},
    setAttribute(k,v){this.attrs[k]=v;},
    classList:{toggle:()=>{}},
    addEventListener(k,fn){this.handlers[k]=fn;}
  }));
  for(const button of buttons){
    button.classList.toggle=(cls,on)=>{if(on)button.classes.add(cls);else button.classes.delete(cls);};
  }
  const meta={
    'theme-color':{attrs:{},setAttribute(k,v){this.attrs[k]=v;}},
    'color-scheme':{attrs:{},setAttribute(k,v){this.attrs[k]=v;}}
  };
  const domEvents={};
  const doc={
    readyState:'loading',
    documentElement:{dataset:{}},
    querySelector(s){const n=s.match(/^meta\[name="([^"]+)"\]$/);return n?meta[n[1]]:null;},
    querySelectorAll(s){return s==='[data-nexo-theme-choice]'?buttons:[];},
    addEventListener(event,fn){domEvents[event]=fn;}
  };
  const storage={
    getItem(key){if(denyStorage)throw Error('denied');return data.get(key)||null;},
    setItem(key,val){if(denyStorage)throw Error('denied');data.set(key,val);writes.push([key,val]);}
  };
  const context={document:doc,window:{localStorage:storage}};
  vm.runInNewContext(js,context,{filename:'nexo-themes.js'});
  assert.ok(domEvents.DOMContentLoaded,'Registrar botones sin interrumpir la carga');
  domEvents.DOMContentLoaded();
  return {doc,buttons,meta,data,writes,theme:context.window.NexoVisualTheme};
}
let app=execute();
assert.equal(app.theme.get(),'vivo','Primera instalación empieza en Nexo Vivo');
assert.equal(app.doc.documentElement.dataset.nexoTheme,'vivo');
app.buttons[1].handlers.click();
assert.equal(app.theme.get(),'mediterraneo');
assert.deepEqual(app.writes,[[THEME_KEY,'mediterraneo']]);
assert.equal(app.buttons.filter(b=>b.attrs['aria-pressed']==='true').length,1);
assert.ok(app.buttons[1].classes.has('is-selected'));
assert.equal(app.meta['color-scheme'].attrs.content,'light');
app.buttons[2].handlers.click();
assert.equal(app.theme.get(),'noche');
assert.equal(app.meta['color-scheme'].attrs.content,'dark');
assert.equal(app.meta['theme-color'].attrs.content,'#152237');
assert.equal(app.theme.select('inexistente'),false,'Rechazar temas desconocidos');
assert.equal(app.theme.get(),'noche');
app=execute('noche');
assert.equal(app.theme.get(),'noche','Persistencia en nuevos arranques');
assert.equal(app.doc.documentElement.dataset.nexoTheme,'noche');
app=execute('corrupto');
assert.equal(app.theme.get(),'vivo','Preferencia inválida cae a tema seguro');
app=execute(undefined,true);
app.buttons[2].handlers.click();
assert.equal(app.theme.get(),'noche','Sin almacenamiento sigue funcionando durante la sesión');
assert.ok(html.includes('data-settings-accordion="display"'),'El control de tamaño sigue intacto');
assert.ok(html.includes('data-settings-accordion="security"'),'Las funciones de seguridad siguen intactas');

/* Contraste WCAG AA (texto normal) de las combinaciones principales de cada tema. */
function luminance(hex){
  const rgb=[0,2,4].map(i=>parseInt(hex.slice(i+1,i+3),16)/255)
    .map(x=>x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4));
  return 0.2126*rgb[0]+0.7152*rgb[1]+0.0722*rgb[2];
}
function contrast(a,b){
  const x=luminance(a),y=luminance(b);
  return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
}
for(const name of names){
  const head=':root[data-nexo-theme="'+name+'"]{';
  const from=css.indexOf(head);
  assert.ok(from>=0,'Tema '+name+' no existe');
  const block=css.slice(from+head.length,css.indexOf('}',from));
  const get=token=>{
    const value=block.match(new RegExp('--'+token+':(#[0-9a-fA-F]{6})'));
    assert.ok(value,'Falta token '+token+' en '+name);
    return value[1];
  };
  assert.ok(contrast(get('primary'),get('nx-on-primary'))>=4.5,name+': botón primario legible');
  assert.ok(contrast(get('text'),get('bg'))>=4.5,name+': texto principal legible');
  assert.ok(contrast(get('muted'),get('nx-surface'))>=4.5,name+': texto secundario legible');
  for(const tint of ['blue','green','coral','sun','violet']){
    assert.match(block,new RegExp('--nx-card-'+tint+':#[0-9a-fA-F]{6}'),
      name+': falta el fondo suave '+tint);
  }
}
assert.ok(css.includes(':root[data-nexo-theme] .calendar-day.today .day-number{'),
  'Hoy no seleccionado debe usar color de texto adecuado también en Noche');
assert.ok(css.includes('color:var(--nx-on-primary)'),
  'En Noche el azul claro de hoy requiere texto oscuro');
assert.ok(css.includes(':root[data-nexo-theme] .family-home-search>.ui-icon{color:var(--primary)}'),
  'Icono de búsqueda legible en tema Noche');
assert.ok(css.includes(':root[data-nexo-theme="noche"] .tgv-all-day-event small{color:var(--muted)}'),
  'Texto secundario legible en eventos de todo el día');
assert.ok(css.includes('@media(max-width:420px)'),
  'La rejilla horaria debe adaptarse también a móviles estrechos');

console.log('OK: 3 temas, selector único, persistencia visual, estado accesible, fallback, modo oscuro y gestos intactos');
