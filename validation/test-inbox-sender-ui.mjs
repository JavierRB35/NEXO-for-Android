import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {JSDOM} from 'jsdom';

const code=fs.readFileSync('app/src/main/assets/app.js','utf8');
const from=code.indexOf('function inboxSourceLabel(capture){');
const until=code.indexOf('\nfunction renderInboxPanel(){',from);
assert(from>0 && until>from, 'No se encuentra la lógica real del Inbox');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const java=fs.readFileSync('app/src/main/java/com/jruiz/nexo/communications/NexoNotificationListener.java','utf8');
const db=fs.readFileSync('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java','utf8');
const sms=fs.readFileSync('app/src/main/java/com/jruiz/nexo/input/SmsWatchEngine.java','utf8');
const gmail=fs.readFileSync('app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java','utf8');
const manifest=fs.readFileSync('app/src/main/AndroidManifest.xml','utf8');
const esc=x=>String(x??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const sandbox={esc,uiIcon:()=>'',typeLabel:x=>x,fmtDate:()=> '19/10/2026'};
vm.createContext(sandbox);
vm.runInContext(code.slice(from,until),sandbox);
const info=c=>sandbox.inboxSenderInfo(c);
const card=c=>sandbox.inboxCaptureCard({id:1,createdAt:1,rawText:'Nos vemos mañana a las diez',detectedTitle:'Reunión de mañana',...c});
const gmailCase={source:'gmail-api',sender:'"María García" <maria@ejemplo.com>',subject:'Una cita el miércoles'};
assert.equal(info(gmailCase).name,'María García');
assert.equal(info(gmailCase).details,'Gmail · maria@ejemplo.com');
assert(card(gmailCase).includes('<strong class="inbox-sender-name">De: María García</strong>'));
assert(card(gmailCase).includes('Gmail · maria@ejemplo.com'));
assert(card(gmailCase).indexOf('De: María García') < card(gmailCase).indexOf('Reunión de mañana'));

const gmailAddress={source:'gmail-api',sender:'avisos@example.org'};
assert.equal(info(gmailAddress).name,'avisos@example.org');
assert.equal(info(gmailAddress).details,'Gmail');
assert.equal(info({source:'gmail-api',subject:'Una reunión personal'}).name,
  'Remitente desconocido','El asunto de un correo no es su remitente');

const smsCase={source:'sms-provider',sender:'+34 600 000 000',subject:'+34 600 000 000'};
assert.equal(info(smsCase).name,'+34 600 000 000');
assert.equal(info(smsCase).details,'SMS');
assert.equal(info({source:'sms-provider',subject:'+34 611 111 111'}).name,'+34 611 111 111');

const whatsapp={source:'notification',sourcePackage:'com.whatsapp',sender:'Javier',
  classification:{sourceGroup:'Familia'}};
assert.equal(info(whatsapp).name,'Javier');
assert.equal(info(whatsapp).details,'WhatsApp · Grupo: Familia');
assert(card(whatsapp).includes('WhatsApp · Grupo: Familia'));

const groupWithoutSender={source:'notification',sourcePackage:'com.whatsapp',
  classification:{sourceGroup:'Familia'}};
assert.equal(info(groupWithoutSender).name,'Remitente desconocido');
assert.equal(info(groupWithoutSender).group,'Familia');
assert.equal(info({source:'notification',sourcePackage:'com.whatsapp',sender:'3 mensajes nuevos'}).name,
 'Remitente desconocido','Una notificación agrupada no identifica a una persona');
assert.equal(info({source:'notification',sourcePackage:'org.telegram.messenger',sender:'Ana'}).details,'Telegram');
assert.equal(info({source:'calendar',classification:{calendarChangeType:'updated'}}).details,'Calendar · cambio');
assert.equal(info({source:'android-share',subject:'Texto compartido'}).name,
 'Remitente desconocido','El asunto compartido no identifica a quien lo compartió');
const injected=card({source:'gmail-api',sender:'<img src=x onerror=alert(1)> <evil@example.org>'});
assert(!injected.includes('<img'),'La identidad debe escaparse al pintar HTML');
assert(injected.includes('&lt;img'));

assert(html.includes('id="captureSenderInfo"'),'Debe existir identidad en pantalla de revisión');
assert(code.includes("senderContext.innerHTML=inboxSenderHtml(capture)"));
assert(code.includes("identity.classList.add('hidden')"),'Nueva captura manual no debe mostrar remitente anterior');
const doc=new JSDOM('<main><div id="captureSenderInfo"></div></main>').window.document;
doc.querySelector('#captureSenderInfo').innerHTML=sandbox.inboxSenderHtml(whatsapp);
assert.equal(doc.querySelector('.inbox-sender-name').textContent,'De: Javier');
assert.equal(doc.querySelector('.inbox-sender-origin').textContent,'WhatsApp · Grupo: Familia');
assert(java.includes('Notification.EXTRA_CONVERSATION_TITLE'));
assert(java.includes('Notification.MessagingStyle.Message.getMessagesFromBundleArray'));
assert(java.includes('classification.put("sourceGroup", payload.group)'));
assert(java.includes('group.equalsIgnoreCase(title)'), 'No atribuir nombres de grupo a un remitente inexistente');
assert(java.includes('in.put("sender", payload.sender)'));
assert(db.includes('o.put("sender", c.getString(17))'));
assert(sms.includes('in.put("sender", sms.address)'));
assert(gmail.includes('in.put("sender", message.from)'));
assert(!manifest.includes('android.permission.READ_CONTACTS'), 'No pedir permisos adicionales de contactos');
console.log('OK: remitentes Gmail/SMS/WhatsApp/Telegram, grupos, sin identidad inventada, HTML seguro y vista detalle');
