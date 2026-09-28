const fs=require('fs');
const vm=require('vm');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const start=source.indexOf('function attachMonthSwipe(');
if(start<0)throw new Error('attachMonthSwipe no encontrada');
let brace=source.indexOf('{',start),depth=0,end=-1;
for(let i=brace;i<source.length;i++){
  if(source[i]==='{')depth++;
  else if(source[i]==='}'&&--depth===0){end=i+1;break;}
}
if(end<0)throw new Error('No se pudo extraer attachMonthSwipe');
const context={Date};
vm.createContext(context);
vm.runInContext(source.slice(start,end),context);
const attach=context.attachMonthSwipe;
function fakeElement(){
  return {dataset:{},listeners:{},addEventListener(type,cb){(this.listeners[type]||(this.listeners[type]=[])).push(cb);}};
}
function emit(el,type,event){for(const cb of el.listeners[type]||[])cb(event);}
function touch(x,y){return {clientX:x,clientY:y};}
let prev=0,next=0;
const el=fakeElement();attach(el,()=>prev++,()=>next++);
if(el.dataset.monthSwipeReady!=='1')throw new Error('No marca inicialización');
emit(el,'touchstart',{touches:[touch(200,100)]});emit(el,'touchend',{changedTouches:[touch(120,102)]});
if(next!==1||prev!==0)throw new Error('Swipe izquierda no abre mes siguiente');
emit(el,'touchstart',{touches:[touch(100,100)]});emit(el,'touchend',{changedTouches:[touch(170,104)]});
if(prev!==1||next!==1)throw new Error('Swipe derecha no abre mes anterior');
emit(el,'touchstart',{touches:[touch(100,100)]});emit(el,'touchend',{changedTouches:[touch(120,180)]});
if(prev!==1||next!==1)throw new Error('Scroll vertical cambió de mes');
emit(el,'touchstart',{touches:[touch(100,100)]});emit(el,'touchend',{changedTouches:[touch(135,102)]});
if(prev!==1||next!==1)throw new Error('Gesto corto cambió de mes');
let prevented=false,stopped=false;
emit(el,'click',{preventDefault(){prevented=true;},stopPropagation(){stopped=true;}});
if(!prevented||!stopped)throw new Error('No suprime el click posterior a swipe');
console.log('Calendar swipe runtime: 5 tests OK');
