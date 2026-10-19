const fs=require('fs');
const vm=require('vm');
const assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
function extract(name){
  const start=source.indexOf('function '+name+'(');
  if(start<0)throw new Error('Función no encontrada: '+name);
  const brace=source.indexOf('{',start);
  let depth=0;
  for(let p=brace;p<source.length;p++){
    if(source[p]==='{')depth++;
    if(source[p]==='}'&&--depth===0)return source.slice(start,p+1);
  }
  throw new Error('Función sin cierre: '+name);
}
let openings=[];
const ctx={
  Date, Intl, Number, String,
  categoryLabel:()=> 'Cumpleaños',
  familyCategoryIcon:()=> 'cake',
  isVehicleReservation:i=>String(i?.subcategory||'')==='vehicle_reservation',
  familyCategoryColor:()=> '#D81B60',
  fmtTime:()=> '10:00',
  itemEffectiveEndAt:i=>i.endAt||i.dueAt,
  eventParticipantAvatarsHtml:()=> '<span class="avatar">A</span>',
  eventReminderBellHtml:()=> '',
  uiIcon:n=> '<svg data-icon="'+n+'"></svg>',
  esc:s=> String(s??'').replaceAll('&','&amp;').replaceAll('<','&lt;'),
  escAttr:s=> String(s??'').replaceAll('&','&amp;'),
  openEventInAgenda:(id,occ,rec)=> openings.push([id,occ,rec])
};
vm.createContext(ctx);
vm.runInContext(['homeUpcomingCard','bindHomeUpcoming','agendaCard'].map(extract).join('\n'),ctx);
const future=Date.now()+3*86400000;
const model={id:17,title:'Celebración',body:'Con la familia',type:'event',category:'Cumpleaños',dueAt:future,hasTime:true,endAt:future+3600000,status:'pending'};
const home=ctx.homeUpcomingCard(model);
assert.strictEqual(ctx.agendaCard(model,true),home,'Agenda no comparte componente con Inicio');
assert(home.startsWith('<button')&&home.endsWith('</button>'));
assert.strictEqual((home.match(/<button/g)||[]).length,1,'La tarjeta no debe contener acciones internas');
for(const forbidden of ['data-card-delete-item','data-share-item','data-postpone-event','icon-action']){
  assert(!home.includes(forbidden),'Botón redundante: '+forbidden);
}
assert(home.includes('--event-category-color:#D81B60'));
assert(home.includes('data-home-event="17"'));
assert(home.includes('Celebración'));
const linked=ctx.homeUpcomingCard({...model,linkCount:2});
assert(linked.includes('class="upcoming-card__links"'),'La tarjeta debe mostrar el icono de vínculos');
assert(linked.includes('data-icon="link"')&&linked.includes('<span>2</span>'),'Falta cadena azul con contador 2');
assert.strictEqual((linked.match(/<button/g)||[]).length,1,'No anidar botones dentro de la tarjeta');
assert(!home.includes('upcoming-card__links'),'Sin vínculos no debe haber icono');
const badCount=ctx.homeUpcomingCard({...model,linkCount:0});
assert(!badCount.includes('upcoming-card__links'));
assert(!ctx.homeUpcomingCard({...model,type:'task',linkCount:2}).includes('upcoming-card__links'),'Solo eventos');
const recurringLinked=ctx.homeUpcomingCard({...model,seriesId:5,recurring:true,linkCount:3});
assert(recurringLinked.includes('upcoming-card__links')&&recurringLinked.includes('<span>3</span>'),'La serie hereda el contador');
const undated=ctx.homeUpcomingCard({...model,id:18,dueAt:0,endAt:0});
assert(undated.includes('date-tile--undated'),'Falta tarjeta sin fecha');
assert(!undated.includes('1970'),'No debe mostrarse el año 1970');
const old=ctx.homeUpcomingCard({...model,dueAt:Date.now()-10*86400000,endAt:0,status:'done'});
assert(old.includes('upcoming-card--past')&&old.includes('Completada'));
const recurring=ctx.homeUpcomingCard({...model,recurring:true,seriesId:5,occurrenceAt:future});
assert(recurring.includes('data-home-event="5"')&&recurring.includes('data-home-recurring="1"'));
const nodes=[{dataset:{homeEvent:'5',homeOccurrence:String(future),homeRecurring:'1'},addEventListener(type,fn){this.events=this.events||{};this.events[type]=fn;this.count=(this.count||0)+1;}}];
const root={querySelectorAll:selector=>{assert.strictEqual(selector,'[data-home-event]');return nodes;}};
ctx.bindHomeUpcoming(root);
ctx.bindHomeUpcoming(root);
assert.strictEqual(nodes[0].count,1,'Listeners duplicados');
nodes[0].events.click();
assert.deepStrictEqual(openings,[[5,future,true]]);
console.log('Calendar event-card runtime: base, enlaces 0/2/3, recurrentes y sin botones anidados OK');
