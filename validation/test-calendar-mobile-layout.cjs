// Run against an isolated headless Chromium CDP endpoint; requires Node with WebSocket.
// No app data or native bridge is used. All five production stylesheets are loaded.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
const output=process.env.NEXO_LAYOUT_OUTPUT;
(async()=>{
 const tabs=await(await fetch((process.env.NEXO_CDP_URL||'http://127.0.0.1:9333')+'/json')).json();
 const tab=tabs.find(t=>t.type==='page');assert(tab,'Headless page required');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((ok,no)=>{ws.onopen=ok;ws.onerror=no;});
 let seq=0;const pending=new Map();
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
 try{
  const asset=name=>fs.readFileSync(path.join(root,'app/src/main/assets',name),'utf8');
  const css=[...asset('index.html').matchAll(/<link rel="stylesheet" href="([^"]+)"/g)].map(m=>asset(m[1])).join('\n');
  const days=Array.from({length:42},(_,i)=>`<button type="button" class="calendar-day ${i===9?'today selected':''}" data-calendar-day="${i}"><span class="day-heading"><span class="day-number">${i%31+1}</span><span class="day-total">3</span></span><span class="day-dots">${'<span class="calendar-category-marker"><svg class="ui-icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/></svg></span>'.repeat(3)}</span></button>`).join('');
  const html=`<!doctype html><html data-nexo-theme="vivo"><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>${css}</style></head><body><section class="screen active" data-screen="calendar"><div class="calendar-toolbar"><button class="calendar-month-button">Noviembre de 2026</button><button class="text-button">Hoy</button><button class="icon-button">‹</button><button class="icon-button">›</button></div><div class="calendar-card"><div class="calendar-weekdays">${'LMXJVSD'.split('').map(s=>'<span>'+s+'</span>').join('')}</div><div class="calendar-viewport"><div class="calendar-grid">${days}</div></div></div></section></body></html>`;
  const tree=await call('Page.getFrameTree');await call('Page.setDocumentContent',{frameId:tree.frameTree.frame.id,html});
  const metrics=()=>evaluate(`(()=>{const g=document.querySelector('.calendar-grid'),v=document.querySelector('.calendar-viewport'),b=v.getBoundingClientRect(),cells=[...g.children],headers=[...document.querySelector('.calendar-weekdays').children];return {viewport:innerWidth,scroll:document.documentElement.scrollWidth,gridWidth:g.scrollWidth,available:v.clientWidth,cut:cells.filter(e=>{const r=e.getBoundingClientRect();return r.right>b.right+1||r.left<b.left-1}).length,aligned:cells.slice(0,7).every((e,i)=>Math.abs(e.getBoundingClientRect().left-headers[i].getBoundingClientRect().left)<1),contentOverflow:cells.filter(e=>e.scrollWidth>e.clientWidth+1).length}})()`);
  await call('Emulation.setDeviceMetricsOverride',{width:360,height:800,deviceScaleFactor:1,mobile:true});
  // The test must reproduce the reported defect before accepting the production fix.
  await evaluate(`document.head.insertAdjacentHTML('beforeend','<style id="old-rule">.calendar-grid{grid-template-columns:repeat(7,minmax(48px,1fr))}</style>')`);
  assert((await metrics()).cut>0,'Old CSS did not reproduce the clipped Sunday');
  await evaluate(`document.getElementById('old-rule').remove()`);
  const results=[];
  for(const width of [320,360,393,412,600])for(const font of [16,20,24])for(const theme of ['vivo','mediterraneo','noche']){
   await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:true});
   await evaluate(`document.documentElement.style.fontSize='${font}px';document.documentElement.dataset.nexoTheme='${theme}'`);
   const m=await metrics();results.push({width,font,theme,...m});
   assert.equal(m.cut,0,JSON.stringify(results.at(-1)));assert.equal(m.aligned,true,JSON.stringify(results.at(-1)));
   assert.equal(m.contentOverflow,0,JSON.stringify(results.at(-1)));assert(m.gridWidth<=m.available+1,JSON.stringify(m));
   assert(m.scroll<=width+1,JSON.stringify(m));
  }
  if(output){
   fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'calendar-layout.json'),JSON.stringify(results,null,2));
   await call('Emulation.setDeviceMetricsOverride',{width:360,height:800,deviceScaleFactor:2,mobile:true});
   await evaluate(`document.documentElement.style.fontSize='16px';document.documentElement.dataset.nexoTheme='vivo'`);
   const image=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(output,'calendar-fixed.png'),Buffer.from(image.data,'base64'));
  }
  console.log(`Calendar layout: old clipping reproduced; ${results.length} viewport/font/theme combinations fit all 42 cells with aligned headers.`);
 }finally{ws.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
