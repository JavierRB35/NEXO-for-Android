#!/usr/bin/env node
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const app=fs.readFileSync(path.join(root,'app/src/main/assets/app.js'),'utf8');
const native=fs.readFileSync(path.join(root,'app/src/main/java/com/jruiz/nexo/MainActivity.java'),'utf8');

// The action belongs to General settings, is visible without enabling Connect,
// and has a single handler. The URL is stable across APK version bumps.
const general=html.split('data-settings-accordion="general"',2)[1]
  .split('data-settings-accordion="updates"',1)[0];
assert.match(general,/id="shareNexoApp"/);
assert.match(general,/Compartir Nexo/);
assert.match(general,/Sin compartir tus datos/);
assert.equal((html.match(/id="shareNexoApp"/g)||[]).length,1);
assert.equal((app.match(/addEventListener\('click',shareNexoApp\)/g)||[]).length,1);
assert.match(native,/@JavascriptInterface public void sharePlainText\(String title,String text\)/);
assert.match(native,/Intent\.createChooser\(share, "Compartir"\)/);

const start=app.indexOf('const NEXO_PUBLIC_DOWNLOAD_URL=');
const end=app.indexOf('function renderMore(){',start);
assert.ok(start>0&&end>start,'the share handler must precede renderMore');
const excerpt=app.slice(start,end),calls=[],errors=[];
const context={NexoNative:{sharePlainText:(...args)=>calls.push(args)},toast:msg=>errors.push(msg)};
vm.runInNewContext(excerpt+'\nshareNexoApp();',context);
assert.equal(calls.length,1,'tap uses the standard Android share sheet');
assert.equal(errors.length,0);
const [title,message]=calls[0];
assert.match(title,/Nexo/);
const stable='https://github.com/JavierRB35/nexo-android-updates/releases/latest';
assert.ok(message.includes(stable),'the shared link must always point to the latest public release');
assert.match(message,/\.apk/);
assert.match(message,/sin compartir mis datos/i);
assert.match(message,/sin.*vincular Nexo Connect/i);
assert.doesNotMatch(message,/releases\/download\/v\d/,'never freeze the download to one APK version');
assert.doesNotMatch(excerpt,/getCalendar|getItems|getContacts|getPeople|getProfile|shareNexoItem|syncProfiles/,
  'sharing the installation link must not access or export private data');

console.log('OK: Nexo install link share is version-independent, private and wired to Android chooser');
