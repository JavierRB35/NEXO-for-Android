'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

// Ejecutar las funciones reales de la cuadrícula sin Android ni datos privados.
const source=fs.readFileSync('app/src/main/assets/app.js','utf8');
const coreStart=source.indexOf('const CATEGORY_ICONS=Object.freeze({');
const coreEnd=source.indexOf('function categoryIconLabel(category)',coreStart);
const markerStart=source.indexOf('function calendarAccompanyPerson(item){');
const markerEnd=source.indexOf('\nfunction requestHolidayYears(years)',markerStart);
assert(coreStart>=0&&coreEnd>coreStart&&markerStart>=0&&markerEnd>markerStart);

const runtime=vm.runInNewContext(
  source.slice(coreStart,coreEnd)+'\n'+source.slice(markerStart,markerEnd)+
  '\n({calendarAccompanyPerson,calendarMonthItemLabel,calendarMarkerHtml})',
  {
    categoryLabel:value=>String(value||'Otro'),
    isVehicleReservation:item=>item?.subcategory==='vehicle_reservation',
    uiIcon:name=>'<svg data-icon="'+name+'"></svg>',
    escAttr:value=>String(value??'').replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
  }
);
const marker=runtime.calendarMarkerHtml;
const p=(id,name,color)=>({id,name,color});
const accompany=participants=>({category:'Acompañar (salud)',participants});
const mother=marker(accompany([p(1,'Marta','#8B5CF6')]));
const father=marker(accompany([p(2,'Padre','#E89B33')]));
assert(mother.includes('data-icon="hand-heart"')&&father.includes('data-icon="hand-heart"'));
assert(mother.includes('--event-category-color:#8B5CF6'));
assert(father.includes('--event-category-color:#E89B33'));
assert(mother.includes('Acompañar (salud) · Marta'));
assert.equal(runtime.calendarMonthItemLabel(accompany([p(2,'Padre','#E89B33')])), 'Acompañar (salud) · Padre');

// El color de la categoría se mantiene si falta identidad inequívoca.
for(const item of [
  accompany([]),
  accompany([p(1,'Marta','#8B5CF6'),p(2,'Padre','#E89B33')]),
  {category:'Acompañar (salud)',participantsText:'Marta'},
  accompany([{name:'Marta',color:'#8B5CF6'}]),
  accompany([p(1,'Marta','#BAD;COLOR')])
]){
  const html=marker(item);
  assert(html.includes('--event-category-color:#168FBB'),html);
  assert(!html.includes('calendar-category-marker--person'),html);
}
// Ni categorías distintas ni reservas de vehículo cambian de icono o color.
const birthday=marker({category:'Cumpleaños',participants:[p(1,'Marta','#8B5CF6')]});
assert(birthday.includes('data-icon="cake"')&&birthday.includes('--event-category-color:#D81B60'));
assert(!birthday.includes('calendar-category-marker--person'));
const vehicle=marker({...accompany([p(1,'Marta','#8B5CF6')]),subcategory:'vehicle_reservation'});
assert(vehicle.includes('calendar-event-vehicle')&&vehicle.includes('data-icon="car"'));
assert(vehicle.includes('--event-category-color:#EA580C'));

const white=marker(accompany([p(3,'Blanco','#FFFFFF')]));
const yellow=marker(accompany([p(4,'Amarillo','#FACC15')]));
assert(white.includes('calendar-category-marker--person-light'));
assert(yellow.includes('calendar-category-marker--person-light'));
assert(marker(accompany([p(5,'Ana "<', '#3158D4')])).includes('Ana &quot;&lt;'));
assert(source.includes('list.map(calendarMonthItemLabel)'), 'El nombre se comunica en la etiqueta accesible del día');

const css=fs.readFileSync('app/src/main/assets/styles.css','utf8');
assert(css.includes('.calendar-category-marker--person-light .ui-icon{filter:drop-shadow('));
console.log('OK: iconos de Acompañar por persona, ambiguos y otras categorías intactos');
