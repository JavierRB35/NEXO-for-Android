/* Regressions for Nexo 3.7.96 mobile month/day calendar layout. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const asset=file=>fs.readFileSync(path.join(root,'app/src/main/assets',file),'utf8');
const app=asset('app.js'),css=asset('calendar-redesign.css'),views=asset('calendar-views.js');

assert.match(app,/uiIcon\(item\.type==='task'\?'tasks':familyCategoryIcon\(item\.category\)\)/,
  'Month event chips must show their category icons');
assert.match(css,/calendar-month-event-content>\.ui-icon\{/,
  'Month event icons require an explicit visible size');
assert.match(css,/\.calendar-toolbar\{\s*display:flex;align-items:center;flex-wrap:nowrap;/,
  'Five day controls must occupy one row');
assert.match(css,/calendar-toolbar \[hidden\]\{display:none!important\}/,
  'Back to month must not consume space in month view');
assert.match(views,/view==='day'\?dayName\(selected\)/,
  'Daily toolbar date must remain short and localized');
assert.match(css,/#calendarMonthRegion #calendarGrid\{grid-auto-rows:var\(--calendar-row-height/,
  'Month grid must receive responsive row heights');
assert.match(css,/#calendarMonthRegion #calendarGrid>\.calendar-day\{min-height:0;/,
  'Old min-height must not force an off-screen sixth week');

const begin=app.indexOf('let calendarMonthFitScheduled=false;');
const end=app.indexOf('function renderCalendar(direction=0){',begin);
assert(begin>0&&end>begin,'Missing responsive month sizing controller');
const controller=app.slice(begin,end);
let rowCount=42,active=true,hidden=false,top=184,navTop=656,gap=3,rowHeight=72;
const grid={
  get childElementCount(){return rowCount;},
  getBoundingClientRect(){const rows=Math.ceil(rowCount/7);return {top,bottom:top+rows*rowHeight+(rows-1)*gap};},
  style:{setProperty(name,value){assert.equal(name,'--calendar-row-height');rowHeight=parseInt(value,10);}},
  dataset:{}
};
const card={getBoundingClientRect(){return {bottom:grid.getBoundingClientRect().bottom+34};}};
const region={get hidden(){return hidden;},closest(){return active?{}:null;},querySelector(){return card;}};
const nav={getBoundingClientRect:()=>({top:navTop})};
const elements={'#calendarMonthRegion':region,'#calendarGrid':grid};
const ctx={
  $:selector=>elements[selector],
  document:{querySelector:selector=>selector==='.bottom-nav'?nav:null},
  window:{innerHeight:760,addEventListener(){},visualViewport:null},
  getComputedStyle:element=>element===nav?{display:'block'}:{rowGap:gap+'px'},
  requestAnimationFrame:callback=>callback()
};
vm.createContext(ctx);vm.runInContext(controller,ctx);
for(const count of [42,35,28]){
  rowCount=count;rowHeight=72;
  ctx.fitCalendarMonthToViewport();
  const rows=Math.ceil(count/7);
  const after=top+rows*rowHeight+(rows-1)*gap+34+8;
  assert(after<=navTop,'Month '+rows+' weeks must end before bottom navigation: '+after);
  assert(rowHeight>=42,'Calendar cells must remain usable');
}
rowCount=42;navTop=602;ctx.fitCalendarMonthToViewport();
assert.equal(grid.dataset.calendarDensity,'tight','Short screens should use compact chips');
const prior=rowHeight;hidden=true;ctx.fitCalendarMonthToViewport();
assert.equal(rowHeight,prior,'Hidden calendar must not be remeasured');
hidden=false;active=false;ctx.fitCalendarMonthToViewport();
assert.equal(rowHeight,prior,'Inactive calendar must not be remeasured');
console.log('OK: category icons, five-control toolbar and 4/5/6 week viewport fit.');
