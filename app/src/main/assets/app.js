'use strict';

const CATEGORIES = ['Familia','Cumpleaños','Santo / Onomástica','Salud / Médico','Colegio / Estudios','Trabajo','Viaje','Vacaciones','Casa','Vehículos / ITV','Seguros','Trámites','Tarea / Recado','Compras','Ocio / Social','Deporte','Recordatorio','Otro'];
const LEGACY_CATEGORY_MAP = {
  Personal:'Familia',
  Coche:'Vehículos / ITV',
  Viajes:'Viaje',
  Salud:'Salud / Médico',
  Medicamentos:'Salud / Médico',
  Finanzas:'Trámites',
  Ocio:'Ocio / Social',
  Restaurantes:'Ocio / Social',
  Lugares:'Ocio / Social',
  Documentos:'Trámites',
  Ideas:'Otro',
  Notas:'Otro',
  Tareas:'Tarea / Recado',
  Recordatorios:'Recordatorio',
  Otros:'Otro'
};
const TYPE_LABEL = {note:'Nota',event:'Cita',task:'Cita',reminder:'Cita',expiry:'Cita',place:'Lugar',restaurant:'Restaurante',document:'Documento',product:'Producto',medication:'Medicamento',parking:'Aparcamiento'};
const RECURRENCE_LABEL = {none:'No se repite',daily:'Diaria',weekly:'Semanal',monthly:'Mensual',bimonthly:'Bimestral',quarterly:'Trimestral',semiannual:'Semestral',annual:'Anual'};
const PLACE_LABELS = ['Quiero ir','He estado','Restaurante','Lugar bonito','Cafetería','Naturaleza','Playa','Mirador','Alojamiento','Otros'];
const DOCUMENT_LABELS = ['Identificación','Salud','Factura','Contrato','Seguro','Garantía','Manual','Certificado','Justificante','Otros'];
const PERSON_COLORS = [
  {value:'#3158D4',label:'Azul'},
  {value:'#16855B',label:'Verde'},
  {value:'#7C3AED',label:'Morado'},
  {value:'#D97706',label:'Naranja'},
  {value:'#DC3F4F',label:'Rojo'},
  {value:'#FACC15',label:'Amarillo'},
  {value:'#FFFFFF',label:'Blanco'},
  {value:'#0F8A9D',label:'Turquesa'},
  {value:'#DB2777',label:'Rosa'},
  {value:'#475569',label:'Gris'}
];
const state = {
  screen:'today', classification:null, attachment:null, inboxCaptureId:0, inboxCaptureOriginal:null, calendarUpdateItemId:0, lastPanel:null, currentList:null,
  userAdjusted:false, captureManualFields:new Set(), pendingSave:false, pendingSaveTimer:null, confirmAction:null,
  calendarMonth:new Date(new Date().getFullYear(),new Date().getMonth(),1), calendarSelected:isoDateLocal(new Date()), calendarItems:[], calendarReturn:'more',
  capturePinnedDate:'', capturePinnedType:'', captureReturn:'today', voicePinnedDate:'', voicePinnedType:'', voiceBaseText:'',
  captureAlerts:[], captureSuggestedAlert:null, captureParticipantIds:[], todaySummaryExpanded:false, todaySummaryLines:[],
  journalFilter:'all', journalSearch:'', journalPick:null, journalEditor:null, voiceMode:'capture', journalVoiceBase:'', journalVoiceKind:'', journalVoiceId:0, journalVoiceTitle:'', journalVoiceTitleManual:false, journalVoiceFavorite:false, journalVoiceRevisit:false, medicationVoiceBase:'', medicationEditor:null, medicationVoiceEditor:false,
  noteVoiceId:0, noteVoiceTitle:'', noteVoiceTitleManual:false, noteEditor:null,
  placeFilter:'all', placeSearch:'', places:[],
  documentFilter:'all', documentSearch:'', documents:[], documentEditor:null, documentRequest:0,
  recordingPoll:0, recordingWasActive:false, recordingWasPaused:false, recordingActiveId:'', playingRecordingId:'', recordingPlaybackPoll:0, transcriptionPoll:0,
  displayMode:'comfortable', appointmentsFilter:'pending', appointmentsWindow:'', captureReturnRoute:null, searchFilter:'all', searchHistorical:false, modalReturnFocus:null,
  homeSearchIndex:[], homeSearchResults:[], homeUpcoming:[], homeMemberMap:new Map(),
  holidayYears:{}, holidayLoading:new Set(),
  managedVoiceTarget:'', managedVoiceBase:'', managedVoiceSegments:[], managedVoicePartial:'', managedVoiceListId:0, managedVoiceShopping:false
};

const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
function ensureCaptureManualFields(){if(!(state.captureManualFields instanceof Set))state.captureManualFields=new Set();return state.captureManualFields;}
function markCaptureManual(...fields){const set=ensureCaptureManualFields();fields.filter(Boolean).forEach(f=>set.add(f));state.userAdjusted=set.size>0;}
function captureFieldLocked(field){return ensureCaptureManualFields().has(field);}
function resetCaptureManualFields(){state.captureManualFields=new Set();state.userAdjusted=false;}
const parseNative = (raw,fallback) => { try { return JSON.parse(raw); } catch (_) { return fallback; } };
const esc = s => String(s ?? '').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
const escAttr = s => esc(s).replace(/`/g,'&#96;');
const typeLabel = t => TYPE_LABEL[t] || 'Elemento';

const FAMILY_CATEGORY_THEMES={
  'familia':['#7B1FA2','#F6E8FA','#D9A7E5'],
  'cumpleanos':['#F57C00','#FFF0D8','#F7BE73'],
  'santo / onomastica':['#C51162','#FFE5F0','#F18AB9'],
  'salud / medico':['#D32F2F','#FFE7E7','#F49A9A'],
  'colegio / estudios':['#1976D2','#E5F1FF','#8FC2F5'],
  'trabajo':['#263238','#ECEFF1','#A9B4B9'],
  'viaje':['#00695C','#DDF5F0','#76C8BA'],
  'vacaciones':['#00838F','#DFF7FA','#75D1D9'],
  'casa':['#6D4C41','#F4E8E3','#C9A89D'],
  'vehiculos / itv':['#283593','#E7E9FA','#9BA3E1'],
  'seguros':['#1B5E20','#E5F4E6','#91C494'],
  'tramites':['#827717','#F4F1D8','#C9C071'],
  'tarea / recado':['#A66A00','#FFF0C9','#E4BE62'],
  'compras':['#E64A19','#FFE8DF','#F3A083'],
  'ocio / social':['#689F38','#EDF6DF','#AFD17D'],
  'deporte':['#008856','#DFF5EA','#72C69F'],
  'recordatorio':['#546E7A','#E8EEF1','#A5B6BE'],
  'otro':['#64748B','#F1F5F9','#CBD5E1']
};
function familyNorm(value){return String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim();}
function familyCategoryTheme(category){return FAMILY_CATEGORY_THEMES[familyNorm(categoryLabel(category))]||FAMILY_CATEGORY_THEMES.otro;}
function familyCategoryIcon(category){
  const n=familyNorm(categoryLabel(category));
  if(/salud|medic|dent/.test(n))return 'heart-pulse';
  if(/cumple/.test(n))return 'cake';
  if(/viaje|vacacion/.test(n))return 'plane';
  if(/trabajo/.test(n))return 'briefcase';
  if(/colegio|estudio/.test(n))return 'graduation';
  if(/casa|hogar/.test(n))return 'home';
  if(/vehiculo|itv/.test(n))return 'car';
  if(/seguro/.test(n))return 'shield';
  if(/tramit|tarea|recado/.test(n))return 'clipboard';
  if(/compra/.test(n))return 'cart';
  if(/ocio|social/.test(n))return 'party';
  if(/deporte/.test(n))return 'dumbbell';
  if(/recordatorio/.test(n))return 'bell';
  if(/familia/.test(n))return 'users';
  return 'calendar';
}
function localDateKeyFromMs(ms){return ms?isoDateLocal(new Date(Number(ms))):'';}
function familySearchNormalize(value){return String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim();}
const FAMILY_SEARCH_STOPWORDS=new Set(['a','al','de','del','el','la','los','las','para','por','un','una']);
function familySearchTokens(query){return familySearchNormalize(query).split(/[^a-z0-9ñ]+/i).filter(t=>t&&!FAMILY_SEARCH_STOPWORDS.has(t));}
function nextOccurrenceForSearch(item,todayStart){
  if(!item?.dueAt)return 0;
  if(!item.recurrence||item.recurrence==='none')return Number(item.dueAt)||0;
  const next=Number(NexoNative.getNextOccurrence(Number(item.seriesId||item.id),todayStart-1))||0;
  return next&&next<=todayStart+740*86400000?next:0;
}
function familySearchCandidateForItem(item,todayStart){
  const recurring=!!item.recurrence&&item.recurrence!=='none';
  const next=nextOccurrenceForSearch(item,todayStart);
  const eventLike=isAppointmentType(item.type);
  const eventDate=next?localDateKeyFromMs(next):localDateKeyFromMs(item.dueAt);
  const historical=eventLike?(recurring?!next:(item.status==='done'||(eventDate&&eventDate<isoDateLocal(new Date())))):false;
  const type=item.type==='note'?'Nota':eventLike?'Evento':item.type==='document'?'Documento':isPlaceType(item.type)?'Lugar':typeLabel(item.type);
  const involved=item.participantsText?'Personas: '+item.participantsText:'';
  const detail=[categoryLabel(item.category),involved,item.locationText,eventDate,historical?'Finalizado':''].filter(Boolean).join(' · ');
  return {kind:'item',id:Number(item.seriesId||item.id),occurrenceAt:recurring?next:0,recurring,type,title:String(item.title||type),detail,
    searchText:[item.title,item.body,item.originalText,item.category,item.subcategory,item.tags,item.locationText,item.participantsText,item.attachmentName,'agenda','personas','involucrado'].filter(Boolean).join(' '),
    sortDate:eventDate||'',historical,raw:item};
}
function buildFamilyStyleSearchIndex(){
  const data=parseNative(NexoNative.getFamilyStyleSearchIndex(),{}),today=new Date();today.setHours(0,0,0,0);const start=today.getTime(),out=[];
  (parseNative(NexoNative.getNexoFamiliaMembers(),[])||[]).forEach(m=>out.push({kind:'member',id:String(m.user_id||''),type:'Persona',title:String(m.full_name||'Persona'),detail:m.role==='admin'?'Administrador':'Miembro',searchText:[m.full_name,m.role].filter(Boolean).join(' '),historical:false,raw:m}));
  (parseNative(NexoNative.getPeople(),[])||[]).forEach(p=>out.push({kind:'person',id:Number(p.id)||0,type:'Persona',title:String(p.name||'Persona'),detail:'Persona de Nexo',searchText:[p.name,'persona','eventos'].filter(Boolean).join(' '),historical:false,raw:p}));
  (data.items||[]).forEach(i=>out.push(familySearchCandidateForItem(i,start)));
  (data.lists||[]).forEach(x=>out.push({kind:'list',id:Number(x.id),type:'Lista',title:String(x.title||'Lista'),detail:String(x.kind||''),searchText:[x.title,x.kind,x.entriesText].filter(Boolean).join(' '),historical:false,raw:x}));
  (data.medications||[]).forEach(x=>out.push({kind:'medication',id:Number(x.id),type:'Medicación',title:String(x.name||x.title||'Medicamento'),detail:[x.dose,x.scheduleText,x.instructions,x.active===false?'Historial':''].filter(Boolean).join(' · '),searchText:[x.name,x.title,x.dose,x.instructions,x.notes,x.scheduleText,x.times].filter(Boolean).join(' '),historical:false,raw:x}));
  (data.journal||[]).forEach(x=>out.push({kind:'journal',id:Number(x.id),type:x.kind==='quote'?'Frase':'Cuaderno',title:String(x.title||'Entrada'),detail:[x.summary,x.tags].filter(Boolean).join(' · '),searchText:[x.title,x.body,x.summary,x.tags].filter(Boolean).join(' '),historical:false,raw:x}));
  (data.inbox||[]).forEach(x=>out.push({kind:'inbox',id:Number(x.id),type:'Inbox',title:String(x.detectedTitle||x.subject||x.attachmentName||String(x.rawText||'').split(/\n/)[0]||'Entrada'),detail:[x.source,x.sender].filter(Boolean).join(' · '),searchText:[x.detectedTitle,x.subject,x.rawText,x.sender,x.source,x.sourcePackage,x.attachmentName].filter(Boolean).join(' '),historical:false,raw:x}));
  (data.recordings||[]).forEach(x=>{let transcription='';try{if(NexoNative.hasTranscription(String(x.id)))transcription=String(NexoNative.getTranscription(String(x.id))||'');}catch(_){}
    out.push({kind:'recording',id:String(x.id||''),type:'Grabación',title:String(x.title||'Grabación'),detail:x.startedAt?fmtDate(Number(x.startedAt),true):'',searchText:[x.title,transcription].filter(Boolean).join(' '),historical:false,raw:x});});
  state.homeSearchIndex=out;return out;
}
function familyStyleSearch(query,includeHistorical=false){
  const items=state.homeSearchIndex.length?state.homeSearchIndex:buildFamilyStyleSearchIndex(),normalizedQuery=familySearchNormalize(query);
  const candidates=includeHistorical?items:items.filter(i=>!i.historical);
  if(!normalizedQuery)return candidates;
  const tokens=familySearchTokens(query);
  return candidates.map((item,index)=>{
    const title=familySearchNormalize(item.title),detail=familySearchNormalize(item.detail),searchText=familySearchNormalize(item.searchText),type=familySearchNormalize(item.type),haystack=[type,title,detail,searchText].join(' ');
    if(!tokens.every(t=>haystack.includes(t)))return null;
    let score=0;
    if(title===normalizedQuery)score+=120;else if(title.startsWith(normalizedQuery))score+=80;else if(title.includes(normalizedQuery))score+=55;
    if(detail.includes(normalizedQuery))score+=35;
    if(detail.includes('personas: '+normalizedQuery))score+=70;
    if(searchText.includes(normalizedQuery))score+=20;
    if(type.includes(normalizedQuery))score+=12;
    tokens.forEach(t=>{if(title.includes(t))score+=12;if(detail.includes(t))score+=7;if(detail.includes('personas: '+t))score+=28;if(searchText.includes(t))score+=4;});
    return {item,score,index};
  }).filter(Boolean).sort((a,b)=>{
    const relevance=b.score-a.score;if(relevance!==0)return relevance;
    if(a.item.sortDate&&b.item.sortDate){
      const today=new Date();today.setHours(12,0,0,0);
      const ad=new Date(a.item.sortDate+'T12:00:00'),bd=new Date(b.item.sortDate+'T12:00:00');
      const da=Math.abs(ad-today),db=Math.abs(bd-today);if(da!==db)return da-db;
      const ap=ad<today,bp=bd<today;if(ap!==bp)return ap?1:-1;
    }
    return a.index-b.index||String(a.item.title).localeCompare(String(b.item.title),'es');
  }).map(x=>x.item);
}
function familySearchCard(item,index,home=false){
  const avatars=item.kind==='item'&&isAppointmentType(item.raw?.type)?eventParticipantAvatarsHtml(item.raw,'event-participant-avatars--search'):'';
  return '<button type="button" class="family-result-card" data-family-search-result="'+index+'" data-family-search-home="'+(home?'1':'0')+'"><span class="family-result-main"><span class="pill">'+esc(item.type)+'</span><strong>'+esc(item.title)+'</strong>'+(item.detail?'<span>'+esc(item.detail)+'</span>':'')+avatars+'</span>'+uiIcon('right')+'</button>';
}
function openFamilySearchResult(item){
  if(!item)return;
  if(item.kind==='member'){navigate('more');return;}
  if(item.kind==='person'){navigate('more');setTimeout(()=>{toggleSettingsAccordion('people');if(item.id)openPersonEditor(Number(item.id));},60);return;}
  if(item.kind==='list'){openPanel('lists');setTimeout(()=>openList(Number(item.id)),40);return;}
  if(item.kind==='medication'){openPanel('medications');setTimeout(()=>openMedicationComposer('',item.raw),40);return;}
  if(item.kind==='journal'){openPanel(item.raw?.kind==='quote'?'quotes':'journal');setTimeout(()=>openJournal(Number(item.id)),40);return;}
  if(item.kind==='recording'){openPanel('recordings');setTimeout(()=>openRecordingActions(String(item.id)),40);return;}
  if(item.kind==='inbox'){openPanel('inbox');setTimeout(()=>reviewInboxCapture(Number(item.id)),40);return;}
  const raw=item.raw||{};
  if(raw.type==='note'){openPanel('notes');setTimeout(()=>openNoteComposer(Number(raw.id)),40);return;}
  if(raw.type==='document'){openPanel('documents');setTimeout(()=>openDocumentEditor(Number(raw.id)),40);return;}
  if(isPlaceType(raw.type)){openPanel('places');setTimeout(()=>openPlaceEditor(Number(raw.id)),40);return;}
  if(isAppointmentType(raw.type)){
    openEventInAgenda(Number(item.id),Number(item.occurrenceAt||raw.dueAt||0),!!item.recurring);
    return;
  }
  openItem(Number(item.id));
}
function openEventInAgenda(id,occurrenceAt,recurring){
  if(recurring)openOccurrence(Number(id),Number(occurrenceAt));
  else openItem(Number(id));
}

function isAppointmentType(type){return ['event','task','reminder','expiry'].includes(type);}
function isPlaceType(type){return type==='place'||type==='restaurant';}
function placeLabel(item){return String(item.subcategory||'').trim()||(item.type==='restaurant'||item.category==='Restaurantes'?'Restaurante':'Otros');}
function categoryLabel(category){const raw=String(category||'').trim();return LEGACY_CATEGORY_MAP[raw]||raw||'Otro';}
function itemTypeControl(id,type){
  if(isAppointmentType(type))return `<input id="${id}" type="hidden" value="event">`;
  return `<label>Tipo<select id="${id}">${Object.entries(TYPE_LABEL).filter(([k])=>!['parking','medication','task','reminder','expiry','place','restaurant'].includes(k)).map(([k,v])=>`<option value="${k}" ${k===type?'selected':''}>${esc(v)}</option>`).join('')}</select></label>`;
}

function toast(message, actionLabel='', actionFn=null, duration=3000) {
  const box=$('#toast'), text=$('#toastText'), action=$('#toastAction');
  text.textContent=message;
  if(actionLabel && actionFn){
    action.textContent=actionLabel; action.classList.remove('hidden');
    action.onclick=()=>{ clearTimeout(toast._timer); box.classList.add('hidden'); actionFn(); };
  } else { action.classList.add('hidden'); action.onclick=null; }
  box.classList.remove('hidden');
  clearTimeout(toast._timer);
  toast._timer=setTimeout(()=>box.classList.add('hidden'),duration);
}

function fmtDate(ms, withTime=true) {
  if(!ms) return '';
  const d=new Date(ms);
  return new Intl.DateTimeFormat('es-ES', withTime ? {day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'} : {day:'2-digit',month:'short',year:'numeric'}).format(d);
}
function fmtDueItem(item) {
  if(!item?.dueAt)return '';
  const end=Number(item.endAt)||((Number(item.durationMinutes)||0)?item.dueAt+Number(item.durationMinutes)*60000:item.dueAt);
  const sameDay=isoDateLocal(new Date(item.dueAt))===isoDateLocal(new Date(end));
  if(item.allDay===true||item.hasTime===false){
    return sameDay?`${fmtDate(item.dueAt,false)} · Todo el día`:`${fmtDate(item.dueAt,false)} → ${fmtDate(end,false)} · Todo el día`;
  }
  const base=fmtDate(item.dueAt,true);
  return end>item.dueAt?(sameDay?`${base}–${fmtTime(end)}`:`${base} → ${fmtDate(end,true)}`):base;
}
function fmtShort(ms) { return ms ? new Intl.DateTimeFormat('es-ES',{day:'2-digit',month:'short'}).format(new Date(ms)) : ''; }
function fmtTime(ms) { return ms ? new Intl.DateTimeFormat('es-ES',{hour:'2-digit',minute:'2-digit'}).format(new Date(ms)) : ''; }
function isoFromMs(ms) {
  if(!ms) return {date:'',time:''};
  const d=new Date(ms), pad=n=>String(n).padStart(2,'0');
  return {date:`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`,time:`${pad(d.getHours())}:${pad(d.getMinutes())}`};
}
function dateTimeToMs(date,time='') {
  if(!date) return 0;
  const t=time || '09:00';
  const d=new Date(`${date}T${t}:00`);
  return Number.isFinite(d.getTime()) ? d.getTime() : 0;
}
function zonedDateParts(ms,zone){
  const formatter=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'});
  const out={};
  formatter.formatToParts(new Date(ms)).forEach(p=>{if(p.type!=='literal')out[p.type]=p.value;});
  return {year:Number(out.year),month:Number(out.month),day:Number(out.day),hour:Number(out.hour),minute:Number(out.minute)};
}
function dateTimeToMsInZone(date,time='',zone=''){
  if(!date)return 0;
  const z=String(zone||'').trim();
  if(!z)return dateTimeToMs(date,time);
  const [year,month,day]=date.split('-').map(Number),[hour,minute]=(time||'09:00').split(':').map(Number);
  if(![year,month,day,hour,minute].every(Number.isFinite))return 0;
  const target=Date.UTC(year,month-1,day,hour,minute,0,0);
  let guess=target;
  try{
    for(let i=0;i<4;i++){
      const p=zonedDateParts(guess,z);
      const shown=Date.UTC(p.year,p.month-1,p.day,p.hour,p.minute,0,0);
      const delta=shown-target;
      if(delta===0)break;
      guess-=delta;
    }
    const p=zonedDateParts(guess,z);
    return p.year===year&&p.month===month&&p.day===day&&p.hour===hour&&p.minute===minute?guess:0;
  }catch(_){return 0;}
}
function addIsoDays(date,days){
  if(!date)return '';
  const [y,m,d]=date.split('-').map(Number),x=new Date(Date.UTC(y,m-1,d+Number(days||0),12,0,0));
  return `${x.getUTCFullYear()}-${String(x.getUTCMonth()+1).padStart(2,'0')}-${String(x.getUTCDate()).padStart(2,'0')}`;
}
function endOfDayMsInZone(date,zone){
  const at=dateTimeToMsInZone(date,'23:59',zone);
  return at?at+59999:0;
}
function displayAnalysisDate(a) {
  if(!a?.dueDate) return '';
  const ms=dateTimeToMs(a.dueDate,a.hasTime?a.dueTime:'');
  if(!ms) return a.dueDate;
  const base=fmtDate(ms,!!a.hasTime);
  return a.hasTime&&a.dueEndTime?`${base}–${a.dueEndTime}`:base;
}
const ALERT_PRESETS=[0,15,30,60,120,360,720,1440,2880,10080,21600,43200];
function humanLeadMinutes(minutes){
  const m=Math.max(0,Number(minutes)||0);
  if(m===0)return 'En el momento';
  if(m<60)return `${m} min antes`;
  if(m%10080===0){const w=m/10080;return `${w} semana${w===1?'':'s'} antes`;}
  if(m%1440===0){const d=m/1440;return `${d} día${d===1?'':'s'} antes`;}
  if(m%60===0){const h=m/60;return `${h} hora${h===1?'':'s'} antes`;}
  return `${m} min antes`;
}
function normalizeAlertMinutes(values){return [...new Set((values||[]).map(Number).filter(v=>Number.isFinite(v)&&v>=0&&v<=5256000))].sort((a,b)=>a-b);}
function getItemAlertMinutes(id){const rows=parseNative(NexoNative.getItemReminders(id),[]);return normalizeAlertMinutes(rows.map(r=>r.leadMinutes!==undefined?Number(r.leadMinutes):Number(r.leadDays||0)*1440));}
function sameAlerts(a,b){const x=normalizeAlertMinutes(a),y=normalizeAlertMinutes(b);return x.length===y.length&&x.every((v,i)=>v===y[i]);}
function alertSelectOptions(value){
  const v=Number(value);let html=ALERT_PRESETS.map(x=>`<option value="${x}" ${x===v?'selected':''}>${esc(humanLeadMinutes(x))}</option>`).join('');
  if(!ALERT_PRESETS.includes(v))html+=`<option value="${v}" selected>${esc(humanLeadMinutes(v))}</option>`;
  return html+'<option value="custom">Personalizado…</option>';
}
function renderAlertList(selector,values,onChange){
  const el=$(selector);if(!el)return;const vals=normalizeAlertMinutes(values);
  el.innerHTML=vals.length?vals.map((v,i)=>`<div class="alert-row"><select data-alert-index="${i}">${alertSelectOptions(v)}</select><button type="button" class="alert-remove" data-alert-remove="${i}" aria-label="Eliminar aviso"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-close"></use></svg></button></div>`).join(''):'<div class="alert-empty">Sin avisos. Añade uno si quieres que Nexo te avise.</div>';
  el.querySelectorAll('[data-alert-remove]').forEach(b=>b.addEventListener('click',()=>{const next=vals.filter((_,i)=>i!==Number(b.dataset.alertRemove));onChange(next);}));
  el.querySelectorAll('[data-alert-index]').forEach(sel=>sel.addEventListener('change',()=>{const idx=Number(sel.dataset.alertIndex);if(sel.value==='custom'){openCustomAlertOverlay(v=>{const next=[...vals];next[idx]=v;onChange(next);});return;}const next=[...vals];next[idx]=Number(sel.value);onChange(next);}));
}
function openAddAlertOverlay(callback){
  const overlay=document.createElement('div');overlay.className='modal scope-overlay';
  const choices=[0,15,30,60,120,1440,10080,43200];
  overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Añadir aviso</h2><p class="muted">¿Cuándo quieres que Nexo te avise?</p><div class="scope-buttons">${choices.map(v=>`<button class="secondary-button" data-alert-choice="${v}">${esc(humanLeadMinutes(v))}</button>`).join('')}<button class="secondary-button" data-alert-custom>Personalizado…</button><button class="ghost-button" data-alert-cancel>Cancelar</button></div></div>`;
  document.body.appendChild(overlay);overlay.querySelectorAll('[data-alert-choice]').forEach(b=>b.addEventListener('click',()=>{const v=Number(b.dataset.alertChoice);overlay.remove();callback(v);}));
  overlay.querySelector('[data-alert-custom]').addEventListener('click',()=>{overlay.remove();openCustomAlertOverlay(callback);});overlay.querySelector('[data-alert-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
}
function openCustomAlertOverlay(callback){
  const overlay=document.createElement('div');overlay.className='modal scope-overlay';
  overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Aviso personalizado</h2><p class="muted">Indica cuánto antes quieres recibir el aviso.</p><div class="custom-alert-grid"><label>Cantidad<input id="customAlertAmount" type="number" min="1" max="3650" value="2"></label><label>Unidad<select id="customAlertUnit"><option value="1">Minutos</option><option value="60" selected>Horas</option><option value="1440">Días</option><option value="10080">Semanas</option></select></label></div><div class="modal-actions"><button class="secondary-button" data-custom-cancel>Cancelar</button><button class="primary-button" data-custom-save>Añadir</button></div></div>`;
  document.body.appendChild(overlay);overlay.querySelector('[data-custom-save]').addEventListener('click',()=>{const amount=Number(overlay.querySelector('#customAlertAmount').value),unit=Number(overlay.querySelector('#customAlertUnit').value);if(!Number.isFinite(amount)||amount<=0){toast('Indica una cantidad válida.');return;}const value=Math.round(amount*unit);if(value>5256000){toast('El aviso puede anticiparse como máximo 10 años.');return;}overlay.remove();callback(value);});overlay.querySelector('[data-custom-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
}
function recurrenceLabel(r){return RECURRENCE_LABEL[r]||RECURRENCE_LABEL.none;}
function endOfDayMs(date){if(!date)return 0;const d=new Date(`${date}T23:59:59`);return Number.isFinite(d.getTime())?d.getTime():0;}
function isoDateLocal(d){const pad=n=>String(n).padStart(2,'0');return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;}
function durationMinutesFromTimes(start,end){
  if(!start||!end)return 0;
  const [sh,sm]=start.split(':').map(Number),[eh,em]=end.split(':').map(Number);
  let diff=(eh*60+em)-(sh*60+sm);
  if(diff<=0)diff+=24*60;
  return Math.min(diff,24*60);
}
function recurrenceRuleFor(value){
  return ({daily:'FREQ=DAILY',weekly:'FREQ=WEEKLY',monthly:'FREQ=MONTHLY',bimonthly:'FREQ=MONTHLY;INTERVAL=2',quarterly:'FREQ=MONTHLY;INTERVAL=3',semiannual:'FREQ=MONTHLY;INTERVAL=6',annual:'FREQ=YEARLY'})[value]||'';
}
function addOneHourTime(value){
  if(!value)return '10:00';
  const [h,m]=value.split(':').map(Number),minutes=((h*60+m)+60)%(24*60);
  return `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`;
}
function eventEndFromFields(startDate,startTime,endDate,endTime,allDay,zone=''){
  if(!startDate)return 0;
  const convert=(date,time)=>zone?dateTimeToMsInZone(date,time,zone):dateTimeToMs(date,time);
  const start=convert(startDate,allDay?'':startTime);
  if(!start)return 0;
  const finishDate=endDate||startDate;
  if(allDay)return convert(finishDate,'');
  if(!startTime)return start;
  if(!endTime)return start+60*60000;
  let finish=convert(finishDate,endTime);
  if(finishDate===startDate&&finish<=start){
    // El día siguiente se resuelve como fecha local de la cita, no sumando 24 h
    // reales, para respetar cambios de hora.
    finish=convert(addIsoDays(finishDate,1),endTime);
  }
  return finish?Math.max(start,finish):0;
}
function durationMinutesBetween(start,end){return start>0&&end>start?Math.min(5256000,Math.round((end-start)/60000)):0;}
function endTimeForItem(item){
  if(!item?.dueAt||item.hasTime===false)return '';
  const end=Number(item.endAt)||((Number(item.durationMinutes)||0)?item.dueAt+Number(item.durationMinutes)*60000:0);
  if(!end||end<=item.dueAt)return '';
  return fmtTime(end);
}
function fmtAgendaTime(item){
  if(!item?.dueAt)return '';
  if(item.allDay===true||item.hasTime===false)return 'Todo el día';
  const start=fmtTime(item.dueAt),end=Number(item.endAt)||((Number(item.durationMinutes)||0)?item.dueAt+Number(item.durationMinutes)*60000:0);
  return end>item.dueAt?`${start}–${fmtTime(end)}`:start;
}
function dayBounds(iso){const start=new Date(`${iso}T00:00:00`).getTime(),end=new Date(`${iso}T23:59:59.999`).getTime();return {start,end};}
function overlappingItems(dueAt,durationMinutes=0,excludeId=0){
  if(!dueAt)return [];
  const d=new Date(dueAt),iso=isoDateLocal(d),{start,end}=dayBounds(iso),items=parseNative(NexoNative.getCalendar(start,end),[]);
  const a0=dueAt,a1=dueAt+Math.max(1,durationMinutes||60)*60000;
  return items.filter(i=>Number(i.id)!==Number(excludeId)&&i.hasTime!==false&&i.dueAt&&a0<(i.dueAt+Math.max(1,Number(i.durationMinutes)||60)*60000)&&a1>i.dueAt);
}


function updateFloatingActions(){
  const voice=$('#globalVoiceFab'),add=$('#calendarAddFab');
  if(!voice||!add)return;
  const modalOpen=!$('#modal')?.classList.contains('hidden')||!$('#confirmModal')?.classList.contains('hidden')||!$('#voiceOverlay')?.classList.contains('hidden');
  const journalEditorActive=!!state.journalEditor&&state.screen==='panel'&&state.lastPanel==='journal'&&!$('#modal')?.classList.contains('hidden');
  const medicationEditorActive=!!state.medicationEditor&&state.screen==='panel'&&state.lastPanel==='medications'&&!$('#modal')?.classList.contains('hidden');
  const contextualEditorActive=journalEditorActive||medicationEditorActive;
  const contentPanel=state.screen==='panel'&&state.lastPanel!=='backup';
  const voiceVisible=(!modalOpen||contextualEditorActive)&&(state.screen==='today'||state.screen==='calendar'||state.screen==='search'||state.screen==='tools'||state.screen==='save'||contentPanel);
  const eventVisible=!modalOpen&&(state.screen==='today'||state.screen==='calendar'||state.screen==='search'||state.screen==='tools'||contentPanel||state.screen==='panel'&&state.lastPanel==='appointments');
  const notesContext=state.screen==='panel'&&state.lastPanel==='notes';
  const listsContext=state.screen==='panel'&&state.lastPanel==='lists';
  const journalContext=state.screen==='panel'&&state.lastPanel==='journal';
  const medicationsContext=state.screen==='panel'&&state.lastPanel==='medications';
  const voiceLabel=medicationEditorActive?'Completar pauta por voz':journalEditorActive?'Dictar contenido':notesContext?'Dictar nota':listsContext?'Añadir elementos por voz':journalContext?'Dictar entrada':medicationsContext?'Hablar con Medicación':'Hablar con Nexo';
  const addLabel=notesContext?'Nueva nota':listsContext?'Nueva lista':journalContext?'Nueva entrada':medicationsContext?'Nuevo medicamento':'Añadir evento';
  voice.classList.remove('is-recording');
  voice.setAttribute('aria-label',voiceLabel);
  voice.setAttribute('title',voiceLabel);
  add.setAttribute('aria-label',addLabel);
  add.setAttribute('title',addLabel);
  voice.classList.toggle('hidden',!voiceVisible);add.classList.toggle('hidden',!eventVisible);
}

function runEventAddFab(){
  if(state.screen==='panel'&&state.lastPanel==='notes'){openNoteComposer();return;}
  if(state.screen==='panel'&&state.lastPanel==='lists'){openNewListModal();return;}
  if(state.screen==='panel'&&state.lastPanel==='journal'){openJournalComposer();return;}
  if(state.screen==='panel'&&state.lastPanel==='medications'){openMedicationComposer();return;}
  const today=isoDateLocal(new Date());prepareCaptureForDate(state.screen==='calendar'?(state.calendarSelected||today):today);
}
function startListVoiceFor(listId){
  const id=Number(listId)||0;
  if(!id){toast('Primero crea una lista con el botón +.');return;}
  const l=parseNative(NexoNative.getList(id),{});
  if(!l.id){toast('La lista ya no existe.');return;}
  const shopping=(l.kind||'').toLowerCase()==='compra';
  const hint=shopping?'Di los productos que quieres añadir, quitar o marcar como comprados.':'Di los elementos que quieres añadir a la lista.';
  startManagedDictation('list',{listId:id,shopping,title:shopping?'LISTA DE LA COMPRA':'AÑADIR A '+String(l.title||'LA LISTA').toUpperCase(),hint});
}
function chooseListForVoice(){
  const lists=parseNative(NexoNative.getLists(),[]);
  if(!lists.length){toast('Primero crea una lista con el botón +.');return;}
  if(lists.length===1){startListVoiceFor(Number(lists[0].id));return;}
  openModal('Añadir por voz',`<p class="muted" style="margin-top:0">Elige la lista a la que quieres añadir elementos.</p><div class="scope-buttons">${lists.map(l=>`<button class="secondary-button" data-list-voice-target="${Number(l.id)}">${esc(l.title||'Lista')}</button>`).join('')}</div>`);
  $$('[data-list-voice-target]').forEach(b=>b.addEventListener('click',()=>{const id=Number(b.dataset.listVoiceTarget);closeModal();startListVoiceFor(id);}));
}


function runFloatingVoice(){
  if(state.medicationEditor&&state.screen==='panel'&&state.lastPanel==='medications'){startMedicationEditorVoice();return;}
  if(state.journalEditor&&state.screen==='panel'&&state.lastPanel==='journal'){startJournalEditorVoice();return;}
  if(state.screen==='save'){
    const base=$('#captureText')?.value.trim()||'';
    startManagedDictation('capture-edit',{base,title:'Habla con Nexo',hint:'Dicta o amplía la tarea. Nexo volverá a interpretar fecha, hora, lugar y avisos.'});
    return;
  }
  if(state.screen==='panel'&&state.lastPanel==='notes'){startNoteVoice();return;}
  if(state.screen==='panel'&&state.lastPanel==='quotes'){startJournalVoice('','quote');return;}
  if(state.screen==='panel'&&state.lastPanel==='journal'){startJournalVoice('','');return;}
  if(state.screen==='panel'&&state.lastPanel==='medications'){startMedicationVoice('');return;}
  if(state.screen==='panel'&&state.lastPanel==='lists'){
    if(state.currentList)startListVoiceFor(state.currentList);
    else chooseListForVoice();
    return;
  }
  startManagedDictation('universal',{title:'Habla con Nexo',hint:'Dime qué quieres hacer o guardar. Nexo identificará el tipo, la fecha, la hora y el lugar para que lo revises.'});
}
function navigate(screen, options={}) {
  if(!['today','save','search','more','calendar','tools','panel'].includes(screen))return;
  const route={screen};
  if(screen==='panel'){route.panel=options.panel||state.lastPanel;route.list=options.list??null;}
  if(screen==='save'&&state.screen!=='save')state.captureReturnRoute={...router.current,scrollY:window.scrollY};
  const moved=router.enter(route,{...options,currentScroll:window.scrollY});
  if(screen!=='panel'||route.panel!=='recordings')stopRecordingPolling();
  state.screen=screen;
  if(screen==='panel'){state.lastPanel=route.panel;state.currentList=route.list;}
  $$('.screen').forEach(el=>el.classList.toggle('active',el.dataset.screen===screen));
  const active=screen==='panel'
    ? (route.panel==='inbox'?'today':'tools')
    : screen==='save'
      ? (state.captureReturnRoute?.screen==='calendar'?'calendar':state.captureReturnRoute?.screen==='tools'?'tools':'today')
      : screen==='more'?'tools'
      : screen;
  $$('.nav-item').forEach(n=>{const selected=n.dataset.nav===active;n.classList.toggle('active',selected);if(selected)n.setAttribute('aria-current','page');else n.removeAttribute('aria-current');});
  if(screen==='today')renderToday();
  else if(screen==='more')renderMore();
  else if(screen==='save')renderCapturePeopleAssignment();
  else if(screen==='calendar')renderCalendar();
  else if(screen==='search'&&$('#searchInput').value.trim())runSearch();
  updateFloatingActions();
  const target=router.key(route);
  requestAnimationFrame(()=>{if(router.key(router.current)===target)window.scrollTo(0,moved.scrollY);});
}

function renderToday() {
  const today=new Date();today.setHours(0,0,0,0);
  const start=today.getTime(),end7=new Date(today.getFullYear(),today.getMonth(),today.getDate()+7).getTime()-1;
  const horizon=start+740*86400000;
  const all=parseNative(NexoNative.getCalendar(start,horizon),[]).filter(i=>isAppointmentType(i.type)&&i.status!=='cancelled'&&i.occurrenceStatus!=='deleted');
  const firstBySeries=new Map();
  all.sort((a,b)=>(a.dueAt||0)-(b.dueAt||0)).forEach(i=>{
    const key=String(i.seriesId||i.id);
    if(!firstBySeries.has(key)&&!(i.status==='done'&&!i.recurring&&(!i.recurrence||i.recurrence==='none')))firstBySeries.set(key,i);
  });
  state.homeUpcoming=[...firstBySeries.values()].filter(i=>Number(i.dueAt)>=start).sort((a,b)=>(a.dueAt||0)-(b.dueAt||0)).slice(0,10);
  const todayKey=isoDateLocal(today);
  const todayItems=state.homeUpcoming.filter(i=>localDateKeyFromMs(i.dueAt)===todayKey);
  const weekItems=state.homeUpcoming.filter(i=>Number(i.dueAt)>=start&&Number(i.dueAt)<=end7);
  const inboxCount=Number(NexoNative.getInboxCaptureCount())+(parseNative(NexoNative.getInbox(),[])||[]).length;

  $('#homeTodayCount').textContent=String(todayItems.length);
  $('#homeTodayHint').textContent=todayItems.length?String(todayItems[0].title||'Evento'):'Sin eventos previstos';
  $('#homeWeekCount').textContent=String(weekItems.length);
  $('#homeWeekHint').textContent=weekItems.length?'Eventos después de hoy':'Semana tranquila';
  $('#homeInboxCount').textContent=String(inboxCount);
  $('#homeInboxHint').textContent=inboxCount?inboxCount+' pendiente'+(inboxCount===1?'':'s')+' de organizar':'Todo organizado';

  const monthRaw=new Intl.DateTimeFormat('es-ES',{month:'short'}).format(today).replace('.','').toLocaleLowerCase('es-ES');
  const monthShort=monthRaw.replace(/^./,c=>c.toLocaleUpperCase('es-ES'));
  $('#homeCalendarMonth').textContent=monthShort;$('#homeCalendarHint').textContent='Abrir agenda';

  const localPeople=parseNative(NexoNative.getPeople(),[]).map(p=>({full_name:p.name,color:p.color,id:p.id}));
  const legacyMembers=parseNative(NexoNative.getNexoFamiliaMembers(),[]);
  state.homeMemberMap=new Map([...legacyMembers,...localPeople].map(m=>[familySearchNormalize(m.full_name),m]));
  $('#homeUpcomingList').innerHTML=state.homeUpcoming.length?state.homeUpcoming.map(homeUpcomingCard).join(''):'<div class="empty-card">No hay próximos eventos. Añade el primero desde Agenda.</div>';
  bindHomeUpcoming();
  buildFamilyStyleSearchIndex();
  const q=$('#homeSearchInput')?.value.trim()||'';
  if(q)runHomeSearch(q);
  requestAnimationFrame(updateFloatingActions);
}

function homeParticipantData(item){
  if(Array.isArray(item?.participants)&&item.participants.length){
    return item.participants.filter(p=>p&&p.name).slice(0,20).map(p=>({name:String(p.name),color:String(p.color||'#3158D4')}));
  }
  const raw=String(item.participantsText||'').trim();if(!raw)return [];
  return raw.split(/[,;·]/).map(x=>x.trim()).filter(Boolean).slice(0,20).map(name=>{
    const exact=state.homeMemberMap.get(familySearchNormalize(name));
    if(exact)return {name:exact.full_name,color:exact.color||'#16855B'};
    return {name,color:'#16855B'};
  });
}
function eventParticipantAvatarsHtml(item,extraClass=''){
  const participants=homeParticipantData(item);
  if(!participants.length)return '';
  const avatars=participants.slice(0,4).map(p=>{
    const color=String(p.color||'#3158D4'),fg=['#FFFFFF','#FACC15'].includes(color.toUpperCase())?'#182033':'#FFFFFF';
    const initial=String(p.name||'').trim().charAt(0).toUpperCase()||'?';
    return '<span class="event-participant-avatar" style="background:'+escAttr(color)+';color:'+fg+'" title="'+escAttr(p.name)+'">'+esc(initial)+'</span>';
  }).join('');
  const more=participants.length>4?'<span class="event-participant-avatar event-participant-avatar--more">+'+(participants.length-4)+'</span>':'';
  return '<span class="event-participant-avatars '+escAttr(extraClass)+'" aria-label="Personas incluidas">'+avatars+more+'</span>';
}
function homeUpcomingCard(item){
  const d=new Date(Number(item.dueAt)),theme=familyCategoryTheme(item.category),icon=familyCategoryIcon(item.category),participants=homeParticipantData(item);
  const weekday=new Intl.DateTimeFormat('es-ES',{weekday:'short'}).format(d).replace('.','').toUpperCase();
  const month=new Intl.DateTimeFormat('es-ES',{month:'short'}).format(d).replace('.','').toUpperCase();
  const recurring=!!item.recurring||(item.recurrence&&item.recurrence!=='none');
  const id=Number(item.seriesId||item.id),occ=Number(item.occurrenceAt||item.dueAt);
  const style='--event-category-color:'+theme[0]+';--event-category-bg:'+theme[1]+';--event-category-border:'+theme[2];
  const avatars=eventParticipantAvatarsHtml(item);
  return '<button type="button" class="upcoming-card upcoming-card--link" style="'+style+'" data-home-event="'+id+'" data-home-occurrence="'+occ+'" data-home-recurring="'+(recurring?'1':'0')+'">'+
    '<span class="date-tile"><span class="date-tile__weekday">'+esc(weekday)+'</span><strong>'+String(d.getDate()).padStart(2,'0')+'</strong><small>'+esc(month)+'</small></span>'+
    '<span class="upcoming-card__body"><strong class="upcoming-title">'+esc(item.title||'Evento')+'</strong>'+(item.locationText?'<span class="upcoming-card__location">'+uiIcon('pin')+esc(item.locationText)+'</span>':'')+avatars+'</span>'+
    '<span class="upcoming-card__aside"><span class="upcoming-category-pill">'+uiIcon(icon)+esc(categoryLabel(item.category))+'</span><span class="upcoming-card__time">'+uiIcon('clock')+(item.allDay===true||item.hasTime===false?'Todo el día':esc(fmtTime(item.dueAt)))+'</span></span></button>';
}
function bindHomeUpcoming(){
  $$('[data-home-event]').forEach(b=>b.addEventListener('click',()=>{
    const id=Number(b.dataset.homeEvent),occ=Number(b.dataset.homeOccurrence),rec=b.dataset.homeRecurring==='1';
    openEventInAgenda(id,occ,rec);
  }));
}
function runHomeSearch(query){
  const q=String(query||'').trim(),section=$('#homeSearchResultsSection'),dash=$('#homeDashboardContent');
  if(!q){section.classList.add('hidden');dash.classList.remove('hidden');$('#homeSearchResults').innerHTML='';return;}
  const results=familyStyleSearch(q,false);state.homeSearchResults=results;
  dash.classList.add('hidden');section.classList.remove('hidden');$('#homeSearchCount').textContent=String(results.length);
  $('#homeSearchResults').innerHTML=results.length?results.map((x,i)=>familySearchCard(x,i,true)).join(''):'<div class="empty-card">No hay coincidencias en tus datos.</div>';
  $$('[data-family-search-home="1"]').forEach(b=>b.addEventListener('click',()=>openFamilySearchResult(state.homeSearchResults[Number(b.dataset.familySearchResult)])));
}
function openHomeToday(){
  const now=new Date();state.calendarMonth=new Date(now.getFullYear(),now.getMonth(),1);state.calendarSelected=isoDateLocal(now);navigate('calendar',{root:false});
}
function openHomeWeek(){
  state.appointmentsWindow='week';state.appointmentsFilter='pending';openPanel('appointments',{keepWindow:true});
}

function renderParkingHomeCard(parking){
  const card=$('#parkingHomeCard');
  if(!card)return;
  if(!parking?.id){
    card.classList.add('hidden');
    return;
  }
  const note=String(parking.locationText||parking.body||'').trim();
  const when=parking.createdAt?`Guardado ${fmtDate(parking.createdAt,true)}`:'Ubicación guardada';
  $('#parkingHomeMeta').textContent=when;
  const noteEl=$('#parkingHomeNote');
  if(note){ noteEl.textContent=note; noteEl.classList.remove('hidden'); }
  else { noteEl.textContent=''; noteEl.classList.add('hidden'); }
  $('#parkingHomeOpen').onclick=()=>NexoNative.openParkingDirections();
  $('#parkingHomeDelete').onclick=()=>confirmGeneric(
    'Eliminar aparcamiento',
    'Se quitará la ubicación del coche de la pantalla principal.',
    ()=>{
      const ok=NexoNative.deleteParking();
      if(ok){ toast('Aparcamiento eliminado'); refreshCurrent(); }
      else toast('No se pudo eliminar el aparcamiento');
    }
  );
  card.classList.remove('hidden');
}

function journalPromptText(j){
  if(!j)return '';
  return String(j.body||j.summary||j.title||'').trim();
}

function refreshJournalPromptFavorite(){
  const btn=$('#journalPromptFavorite');
  if(!btn)return;
  const favorite=!!state.journalPick?.favorite;
  btn.classList.toggle('is-favorite',favorite);
  btn.setAttribute('aria-pressed',favorite?'true':'false');
  btn.setAttribute('aria-label',favorite?'Quitar de favoritas':'Marcar como favorita');
  $('#journalPromptFavoriteIcon').textContent=favorite?'♥':'♡';
}

function renderJournalPrompt(forceNew=false){
  const day=isoDateLocal(new Date());
  const j=parseNative(NexoNative.getJournalDailyPick(day,!!forceNew),{});
  state.journalPick=j&&j.id?j:null;
  const box=$('#journalPrompt');
  if(!box)return;
  if(!state.journalPick){box.classList.add('hidden');return;}
  const text=journalPromptText(state.journalPick);
  if(!text){box.classList.add('hidden');return;}
  $('#journalPromptText').textContent=text;
  const sourceEl=$('#journalPromptSource');
  const isBundledSource=String(state.journalPick.source||'').startsWith('seed-journal-v294-');
  const sourceText=(state.journalPick.kind==='quote'||isBundledSource)?String(state.journalPick.summary||'').trim():'';
  if(sourceEl){sourceEl.textContent=sourceText;sourceEl.classList.toggle('hidden',!sourceText);}
  refreshJournalPromptFavorite();
  box.classList.remove('hidden');
}


function journalCard(j){
  const isQuote=j.kind==='quote';
  const body=String(j.body||'').trim();
  const excerpt=body.length>280?body.slice(0,277).trimEnd()+'…':body;
  const flags=[];
  if(j.favorite)flags.push('<span class="journal-card-flag" aria-label="Favorita">★ Favorita</span>');
  if(!isQuote&&j.revisit)flags.push('<span class="journal-card-flag journal-card-flag--revisit" aria-label="Para releer">↺ Para releer</span>');
  return `<div class="journal-card"><button type="button" class="journal-card-open" data-open-journal="${j.id}"><div class="journal-card-top"><div><div class="journal-card-title">${esc(j.title||(isQuote?'Cita':'Entrada'))}</div><div class="journal-card-meta">${isQuote?'CITA / FRASE':esc(fmtDate(j.createdAt,true))}</div></div></div>${excerpt?`<div class="journal-card-summary journal-card-body-preview">${esc(excerpt)}</div>`:''}${flags.length?`<div class="journal-card-flags">${flags.join('')}</div>`:''}</button><div class="record-card-actions">${recordIconAction('edit','Editar',`data-edit-journal="${j.id}"`)}${recordIconAction('share','Compartir',`data-journal-share="${j.id}"`)}${recordIconAction('trash','Eliminar',`data-journal-delete="${j.id}"`,'icon-action--danger')}</div></div>`;
}
function journalGroupHtml(rows){
  if(!rows.length)return '<div class="empty-state">Todavía no hay entradas que coincidan.</div>';
  const today=isoDateLocal(new Date()),yd=new Date();yd.setDate(yd.getDate()-1);const yesterday=isoDateLocal(yd);
  const groups=[];let last='';
  rows.forEach(j=>{const key=isoDateLocal(new Date(j.createdAt));if(key!==last){const d=new Date(j.createdAt);const label=key===today?'HOY':key===yesterday?'AYER':new Intl.DateTimeFormat('es-ES',{day:'numeric',month:'long',year:'numeric'}).format(d).toLocaleUpperCase('es-ES');groups.push(`<div class="search-section-label">${esc(label)}</div>`);last=key;}groups.push(journalCard(j));});
  return groups.join('');
}


function bindJournalButtons(root=document){
  root.querySelectorAll('[data-open-journal]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openJournal(Number(b.dataset.openJournal));}));
  root.querySelectorAll('[data-edit-journal]').forEach(b=>b.addEventListener('click',e=>{
    e.stopPropagation();
    const id=Number(b.dataset.editJournal),j=parseNative(NexoNative.getJournal(id),{});
    if(j.kind==='quote')openJournal(id);else openJournalComposer('','',id);
  }));
  root.querySelectorAll('[data-journal-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.shareNexoJournal(Number(b.dataset.journalShare));}));
  root.querySelectorAll('[data-journal-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const id=Number(b.dataset.journalDelete),j=parseNative(NexoNative.getJournal(id),{});confirmGeneric('Eliminar entrada','Se eliminará “'+(j.title||'esta entrada')+'”.',()=>{if(NexoNative.deleteJournal(id)){toast('Entrada eliminada');renderJournalPanel();renderJournalPrompt(false);}else toast('No se pudo eliminar');},'Eliminar');}));
}

function startJournalVoice(base='',kind='',context={}){
  state.journalVoiceKind=kind==='quote'?'quote':'';
  state.journalVoiceId=Number(context.id||0);
  state.journalVoiceTitle=String(context.title||'');
  state.journalVoiceTitleManual=!!context.titleManual;
  state.journalVoiceFavorite=!!context.favorite;
  state.journalVoiceRevisit=!!context.revisit;
  startManagedDictation('journal',{
    base:String(base||'').trim(),
    title:kind==='quote'?'DICTAR CITA':'DICTAR EN CUADERNO',
    hint:kind==='quote'?'Di únicamente el texto de la cita. Después podrás poner título y autor o referencia.':'Habla con libertad. Lo que digas se añadirá a esta entrada del Cuaderno.'
  });
}

function setJournalSaveStatus(text,kind='saved'){
  const el=$('#journalSaveStatus');if(!el)return;el.textContent=text;el.dataset.state=kind;
}
function refreshAutoJournalTitle(){
  const ed=state.journalEditor,titleEl=$('#journalTitle'),bodyEl=$('#journalBody');
  if(!ed||!titleEl||!bodyEl||ed.titleManual)return;
  const body=bodyEl.value.trim(),a=body?parseNative(NexoNative.synthesizeJournal(body),{}):{};
  titleEl.value=String(a.title||'');
}
function flushJournalAutosave(){
  const ed=state.journalEditor,titleEl=$('#journalTitle'),bodyEl=$('#journalBody');
  if(!ed||!titleEl||!bodyEl)return ed?.id||0;
  if(ed.timer){clearTimeout(ed.timer);ed.timer=null;}
  const body=bodyEl.value.trim();
  if(!body){setJournalSaveStatus('Empieza a escribir…','idle');return ed.id||0;}
  if(!ed.titleManual)refreshAutoJournalTitle();
  const auto=parseNative(NexoNative.synthesizeJournal(body),{});
  const title=titleEl.value.trim()||String(auto.title||'Entrada');
  const favorite=!!$('#journalFavorite')?.checked,revisit=!!$('#journalRevisit')?.checked;
  const signature=JSON.stringify({body,title,favorite,revisit});
  if(signature===ed.lastSaved){setJournalSaveStatus('✓ Guardado','saved');return ed.id||0;}
  const payload={body,kind:'thought',title,summary:String(auto.summary||''),tags:String(auto.tags||''),favorite,revisit,source:'manual'};
  let ok=false;
  if(ed.id>0){ok=!!NexoNative.updateJournal(JSON.stringify({...payload,id:ed.id}));}
  else{
    const id=Number(NexoNative.saveJournal(JSON.stringify(payload)))||0;
    if(id>0){ed.id=id;ok=true;}
  }
  if(ok){ed.lastSaved=signature;setJournalSaveStatus('✓ Guardado','saved');}
  else setJournalSaveStatus('No se pudo guardar','error');
  return ed.id||0;
}
function scheduleJournalAutosave(delay=800){
  const ed=state.journalEditor;if(!ed)return;
  if(ed.timer)clearTimeout(ed.timer);
  setJournalSaveStatus('Sin guardar','dirty');
  ed.timer=setTimeout(()=>{ed.timer=null;flushJournalAutosave();},delay);
}
function startJournalEditorVoice(){
  const ed=state.journalEditor;if(!ed){startJournalVoice('','');return;}
  const id=flushJournalAutosave();
  const context={
    id,
    title:$('#journalTitle')?.value.trim()||'',
    titleManual:!!ed.titleManual,
    favorite:!!$('#journalFavorite')?.checked,
    revisit:!!$('#journalRevisit')?.checked
  };
  const base=$('#journalBody')?.value.trim()||'';
  state.journalEditor=null;
  document.body.classList.remove('journal-editor-open');
  closeModal();
  startJournalVoice(base,'',context);
}


function openJournalComposer(prefill='',forcedKind='',editId=0,voiceContext=null){
  const quoteMode=forcedKind==='quote';
  state.voiceMode='journal';
  state.journalVoiceKind=quoteMode?'quote':'';

  if(quoteMode){
    const initial=parseNative(NexoNative.synthesizeJournal(prefill||''),{});
    openModal('Nueva cita',`<div class="journal-compose modal-form">
      <div class="modal-actions" style="margin-top:0"><button id="journalVoiceBtn" class="secondary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-mic"></use></svg> Dictar cita</button></div>
      <label>Texto de la cita<textarea id="journalBody" rows="7" placeholder="Escribe o dicta la frase que quieres guardar.">${esc(prefill||'')}</textarea></label>
      <label>Título<input id="journalTitle" type="text" value="${escAttr(initial.title||'')}" placeholder="Un título breve para identificarla"></label>
      <label>Autor / referencia<input id="journalSummary" type="text" value="" placeholder="Autor, filósofo, canción, libro, película…"></label>
      <label>Favorito<span class="switch-row"><input id="journalFavorite" type="checkbox"><span>Marcar como favorita</span></span></label>
      <div class="small muted">Las citas no llevan fecha, hora, recordatorios ni recurrencia.</div>
    </div><div class="modal-actions"><button id="journalSave" class="primary-button">Guardar cita</button></div>`);
    $('#journalVoiceBtn').addEventListener('click',()=>{const base=$('#journalBody').value.trim();closeModal();startJournalVoice(base,'quote');});
    $('#journalSave').addEventListener('click',()=>{
      const body=$('#journalBody').value.trim(),title=$('#journalTitle').value.trim(),reference=$('#journalSummary').value.trim();
      if(!body){toast('Escribe o dicta el texto de la cita');return;}
      if(!title){toast('Pon un título a la cita');$('#journalTitle').focus();return;}
      const id=NexoNative.saveJournal(JSON.stringify({body,kind:'quote',title,summary:reference,tags:'',favorite:$('#journalFavorite').checked,revisit:false,source:'manual'}));
      if(Number(id)>0){closeModal();toast('Cita guardada');if(state.screen==='panel'&&(state.lastPanel==='journal'||state.lastPanel==='quotes'))renderJournalPanel();renderJournalPrompt(false);}
      else toast('No se pudo guardar la cita');
    });
    setTimeout(()=>$('#journalBody')?.focus(),80);
    return;
  }

  const existing=Number(editId)>0?parseNative(NexoNative.getJournal(Number(editId)),{}):{};
  const editing=!!existing.id;
  const body=prefill!==''?String(prefill):String(existing.body||'');
  const initial=body?parseNative(NexoNative.synthesizeJournal(body),{}):{};
  const context=voiceContext||{};
  let title=String(context.title!==undefined?context.title:(editing?existing.title:(initial.title||'')));
  const titleManual=context.titleManual!==undefined?!!context.titleManual:editing;
  const favorite=context.favorite!==undefined?!!context.favorite:!!existing.favorite;
  const revisit=context.revisit!==undefined?!!context.revisit:!!existing.revisit;
  state.journalEditor={
    id:Number(context.id||existing.id||0),
    titleManual,
    timer:null,
    lastSaved:editing&&prefill===''?JSON.stringify({body:String(existing.body||''),title:String(existing.title||''),favorite:!!existing.favorite,revisit:!!existing.revisit}):''
  };

  openModal(editing||state.journalEditor.id>0?'Editar entrada':'Nueva entrada',`<div class="journal-compose journal-compose--simple modal-form">
    <div class="journal-compose-status"><span id="journalSaveStatus" class="note-save-status" data-state="${editing?'saved':'idle'}">${editing?'✓ Guardado':'Empieza a escribir…'}</span></div>
    <label>Título <span class="optional">(opcional)</span><input id="journalTitle" type="text" maxlength="140" value="${escAttr(title)}" placeholder="Nexo propondrá uno automáticamente"></label>
    <label>Texto<textarea id="journalBody" rows="12" placeholder="Escribe lo que quieras recordar, pensar o conservar.">${esc(body)}</textarea></label>
    <div class="journal-compose-flags">
      <label class="switch-row"><input id="journalFavorite" type="checkbox" ${favorite?'checked':''}><span><strong>☆ Favorita</strong><small>Una entrada especialmente importante para ti.</small></span></label>
      <label class="switch-row"><input id="journalRevisit" type="checkbox" ${revisit?'checked':''}><span><strong>↺ Quiero releerla</strong><small>Aparecerá en el filtro “Para releer”.</small></span></label>
    </div>
    <div class="small muted">La fecha y la hora se guardan automáticamente. Esta entrada no se añade al calendario.</div>
  </div>`);

  document.body.classList.add('journal-editor-open');
  const titleEl=$('#journalTitle'),bodyEl=$('#journalBody');
  titleEl.addEventListener('input',()=>{
    const ed=state.journalEditor;if(!ed)return;
    ed.titleManual=!!titleEl.value.trim();
    if(!ed.titleManual)refreshAutoJournalTitle();
    scheduleJournalAutosave();
  });
  bodyEl.addEventListener('input',()=>{refreshAutoJournalTitle();scheduleJournalAutosave();});
  $('#journalFavorite').addEventListener('change',()=>scheduleJournalAutosave(150));
  $('#journalRevisit').addEventListener('change',()=>scheduleJournalAutosave(150));
  if(body&&!titleManual&&!title)refreshAutoJournalTitle();
  if(body&&prefill!=='')scheduleJournalAutosave(120);
  requestAnimationFrame(updateFloatingActions);
  setTimeout(()=>bodyEl?.focus(),80);
}

function openJournal(id){
  const j=parseNative(NexoNative.getJournal(id),{});if(!j.id)return;
  const isQuote=j.kind==='quote';
  if(isQuote){
    openModal('Cita / frase',`<div class="modal-form"><label>Título<input id="journalEditTitle" type="text" value="${escAttr(j.title||'')}"></label><label>Autor / referencia<input id="journalEditSummary" type="text" value="${escAttr(j.summary||'')}" placeholder="Autor, canción, libro, filósofo…"></label><label>Texto de la cita<textarea id="journalEditBody" class="journal-original" rows="8">${esc(j.body||'')}</textarea></label><label>Favorito<span class="switch-row"><input id="journalEditFavorite" type="checkbox" ${j.favorite?'checked':''}><span>Favorita</span></span></label></div><div class="modal-actions wrap-actions"><button id="journalDelete" class="danger-button">Eliminar</button><button id="journalShare" class="secondary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg> Compartir</button><button id="journalUpdate" class="primary-button">Guardar cambios</button></div>`);
    $('#journalShare').addEventListener('click',()=>NexoNative.shareNexoJournal(j.id));
    $('#journalUpdate').addEventListener('click',()=>{
      const body=$('#journalEditBody').value.trim(),title=$('#journalEditTitle').value.trim();
      if(!body){toast('La cita necesita texto');return;}if(!title){toast('Pon un título a la cita');return;}
      const ok=NexoNative.updateJournal(JSON.stringify({id:j.id,title,summary:$('#journalEditSummary').value.trim(),body,kind:'quote',tags:'',favorite:$('#journalEditFavorite').checked,revisit:false}));
      if(ok){closeModal();toast('Cita actualizada');refreshCurrent();renderJournalPrompt(false);}else toast('No se pudo guardar');
    });
  }else{
    const flags=[j.favorite?'<span class="journal-read-flag">★ Favorita</span>':'',j.revisit?'<span class="journal-read-flag">↺ Para releer</span>':''].filter(Boolean).join('');
    openModal('Cuaderno',`<article class="journal-read"><div class="journal-date-badge">${esc(fmtDate(j.createdAt,true))}</div><h3>${esc(j.title||'Entrada')}</h3>${flags?`<div class="journal-read-flags">${flags}</div>`:''}<div class="journal-read-body">${esc(j.body||'')}</div></article><div class="modal-actions wrap-actions"><button id="journalDelete" class="danger-button">Eliminar</button><button id="journalShare" class="secondary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg> Compartir</button><button id="journalEdit" class="primary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-edit"></use></svg> Editar</button></div>`);
    $('#journalShare').addEventListener('click',()=>NexoNative.shareNexoJournal(j.id));
    $('#journalEdit').addEventListener('click',()=>{closeModal();setTimeout(()=>openJournalComposer('','',Number(j.id)),60);});
  }
  $('#journalDelete').addEventListener('click',()=>{closeModal();$('#confirmTitle').textContent=isQuote?'Eliminar cita':'Eliminar del Cuaderno';$('#confirmText').textContent='Se eliminará esta entrada. Esta acción no afecta al calendario.';$('#confirmModal').classList.remove('hidden');state.confirmAction=()=>{const ok=NexoNative.deleteJournal(j.id);$('#confirmModal').classList.add('hidden');toast(ok?'Entrada eliminada':'No se pudo eliminar');refreshCurrent();renderJournalPrompt(false);};});
}
function renderJournalPanel(){
  const quoteMode=state.lastPanel==='quotes';
  const kind=quoteMode?'quote':'thought';
  const filter=['favorite','revisit'].includes(state.journalFilter)?state.journalFilter:'all';
  const q=state.journalSearch||'',fav=filter==='favorite';
  let rows=parseNative(NexoNative.getJournalEntries(q,kind,fav),[]);
  if(filter==='revisit')rows=rows.filter(x=>!!x.revisit);

  $('#panelTitle').textContent=quoteMode?'Frases':'Cuaderno';
  $('#panelSubtitle').textContent=quoteMode?'Citas textuales, autores y referencias · fuera del calendario':'Pensamientos, ideas y reflexiones personales.';

  if(quoteMode){
    $('#panelAction').innerHTML='<button id="journalAddTop" class="secondary-button">+ Nueva frase</button>';
    $('#journalAddTop').addEventListener('click',()=>openJournalComposer('','quote'));
    $('#panelContent').innerHTML=`<div class="journal-toolbar">
      <input id="journalSearch" type="search" placeholder="Buscar por texto, título o autor…" value="${escAttr(q)}">
      <button id="journalVoice" class="secondary-button journal-voice"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-mic"></use></svg></button>
      <button id="journalAdd" class="primary-button journal-add">+ Nueva frase</button>
    </div>
    <div class="journal-filter-row">
      <button data-journal-filter="all" class="${filter==='all'?'active':''}">Todas</button>
      <button data-journal-filter="favorite" class="${filter==='favorite'?'active':''}">★ Favoritas</button>
    </div>
    <div id="journalList" class="stack">${journalGroupHtml(rows)}</div>`;
    bindJournalButtons($('#journalList'));
    $('#journalVoice').addEventListener('click',()=>startJournalVoice('','quote'));
    $('#journalAdd').addEventListener('click',()=>openJournalComposer('','quote'));
  }else{
    $('#panelAction').innerHTML='';
    $('#panelContent').innerHTML=`<div class="journal-toolbar journal-toolbar--cuaderno">
      <input id="journalSearch" type="search" placeholder="Buscar en tu Cuaderno…" value="${escAttr(q)}" aria-label="Buscar en tu Cuaderno">
    </div>
    <div class="journal-filter-row">
      <button data-journal-filter="all" class="${filter==='all'?'active':''}">Todas</button>
      <button data-journal-filter="favorite" class="${filter==='favorite'?'active':''}">★ Favoritas</button>
      <button data-journal-filter="revisit" class="${filter==='revisit'?'active':''}">↺ Para releer</button>
    </div>
    <div id="journalList" class="stack">${journalGroupHtml(rows)}</div>`;
    bindJournalButtons($('#journalList'));
  }

  $('#journalSearch').addEventListener('input',e=>{state.journalSearch=e.target.value;clearTimeout(renderJournalPanel._t);renderJournalPanel._t=setTimeout(renderJournalPanel,220);});
  $$('[data-journal-filter]').forEach(b=>b.addEventListener('click',()=>{state.journalFilter=b.dataset.journalFilter;renderJournalPanel();}));
  updateFloatingActions();
}
function noteSuggestedTitle(body){
  const text=String(body||'').trim();if(!text)return '';
  try{
    const title=String(NexoNative.suggestNoteTitle(text)||'').replace(/\s+/g,' ').trim();
    if(title)return title;
  }catch(_){ }
  const clean=text.replace(/\s+/g,' ').replace(/^[\-–—:;,\.\s]+/,'');
  const first=(clean.split(/[.!?;]\s+/)[0]||clean).trim();
  const words=first.split(' ').filter(Boolean).slice(0,8);
  return words.join(' ').replace(/[.!?,;:]+$/,'').trim();
}

function noteSnippet(body){
  const text=String(body||'').replace(/\s+/g,' ').trim();
  return text.length>180?`${text.slice(0,177).trim()}…`:text;
}

function noteCard(n){
  return `<div class="note-card"><button type="button" class="note-card-open" data-open-note="${n.id}"><span class="note-card-head"><strong>${esc(n.title||'Nota sin título')}</strong><small>${esc(fmtDate(n.createdAt,true))}</small></span>${n.body?`<span class="note-card-body">${esc(noteSnippet(n.body))}</span>`:''}</button><div class="record-card-actions">${recordIconAction('edit','Editar nota',`data-open-note="${n.id}"`)}${recordIconAction('share','Compartir nota',`data-note-share="${n.id}"`)}${recordIconAction('trash','Eliminar nota',`data-note-delete="${n.id}"`,'icon-action--danger')}</div></div>`;
}

function bindNoteButtons(root=document){
  root.querySelectorAll('[data-open-note]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openNoteComposer(Number(b.dataset.openNote));}));
  root.querySelectorAll('[data-note-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.shareNexoItem(Number(b.dataset.noteShare),0,false);}));
  root.querySelectorAll('[data-note-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();deleteItemFromCard(Number(b.dataset.noteDelete));}));
}

function setNoteSaveStatus(text,kind='saved'){
  const el=$('#noteSaveStatus');if(!el)return;
  el.textContent=text;el.dataset.state=kind;
}

function revealSavedNoteActions(){
  const actions=$('#noteSavedActions');if(actions)actions.classList.remove('hidden');
}

function refreshAutoNoteTitle(){
  const ed=state.noteEditor,titleEl=$('#noteTitle'),bodyEl=$('#noteBody');
  if(!ed||!titleEl||!bodyEl||ed.titleManual)return;
  const next=noteSuggestedTitle(bodyEl.value);
  if(titleEl.value!==next)titleEl.value=next;
}

function flushNoteAutosave(){
  const ed=state.noteEditor,titleEl=$('#noteTitle'),bodyEl=$('#noteBody');
  if(!ed||!titleEl||!bodyEl)return ed?.id||0;
  if(ed.timer){clearTimeout(ed.timer);ed.timer=null;}
  if(!ed.titleManual)refreshAutoNoteTitle();
  const body=bodyEl.value.trim(),title=titleEl.value.trim();
  if(!body&&!title){setNoteSaveStatus('Empieza a escribir…','idle');return ed.id||0;}
  const signature=`${ed.titleManual?'1':'0'}\n${title}\n${body}`;
  if(signature===ed.lastSaved){setNoteSaveStatus('✓ Guardado','saved');return ed.id||0;}
  setNoteSaveStatus('Guardando…','saving');
  try{
    if(ed.id>0){
      const ok=!!NexoNative.autosaveNote(JSON.stringify({id:ed.id,title,body,noteTitleManual:!!ed.titleManual}));
      if(!ok){setNoteSaveStatus('No se pudo guardar','error');return ed.id;}
    }else{
      const newId=Number(NexoNative.saveNote(JSON.stringify({title:title||noteSuggestedTitle(body),body,category:'Notas',source:'notepad',noteTitleManual:!!ed.titleManual}))||0);
      if(!newId){setNoteSaveStatus('No se pudo guardar','error');return 0;}
      ed.id=newId;revealSavedNoteActions();$('#modalTitle').textContent='Editar nota';
    }
    ed.lastSaved=signature;setNoteSaveStatus('✓ Guardado','saved');
    return ed.id;
  }catch(_){setNoteSaveStatus('No se pudo guardar','error');return ed.id||0;}
}

function scheduleNoteAutosave(delay=850){
  const ed=state.noteEditor;if(!ed)return;
  if(ed.timer)clearTimeout(ed.timer);
  setNoteSaveStatus('Sin guardar','dirty');
  ed.timer=setTimeout(()=>{ed.timer=null;flushNoteAutosave();},delay);
}

function startNoteVoice(base='',id=0,title='',titleManual=false){
  state.noteVoiceId=Number(id||0);state.noteVoiceTitle=String(title||'').trim();state.noteVoiceTitleManual=!!titleManual;
  startManagedDictation('note',{base:String(base||'').trim(),title:'Dictar nota',hint:'Dicta tu anotación. Puedes hacer pausas y pulsar TERMINAR cuando acabes.'});
}

function openNoteComposer(id=0,prefill='',titlePrefill='',titleManualPrefill=false){
  const editing=Number(id)>0,existing=editing?parseNative(NexoNative.getItem(Number(id)),{}):{};
  if(editing&&(!existing.id||existing.type!=='note')){toast('La nota ya no existe.');refreshNotesSurface();return;}
  const body=prefill!==''?String(prefill):String(existing.body||'');
  const titleManual=editing?!!existing.noteTitleManual:!!titleManualPrefill;
  let title=editing?String(existing.title||''):String(titlePrefill||'');
  if(!titleManual)title=noteSuggestedTitle(body);
  openModal(editing?'Editar nota':'Nueva nota',`<div class="note-compose modal-form"><div class="note-compose-top"><button id="noteVoiceBtn" class="secondary-button" type="button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-mic"></use></svg> Dictar nota</button><span id="noteSaveStatus" class="note-save-status" data-state="${editing?'saved':'idle'}">${editing?'✓ Guardado':'Empieza a escribir…'}</span></div><label>Título<input id="noteTitle" type="text" maxlength="120" value="${escAttr(title)}" placeholder="Se generará automáticamente al escribir"></label><div class="small muted note-title-help">Nexo propone el título a partir de la idea principal. Si lo cambias tú, dejará de modificarlo.</div><label>Anotación<textarea id="noteBody" rows="10" placeholder="Escribe o dicta lo que quieras conservar…">${esc(body)}</textarea></label><div class="small muted">Autoguardado local: no necesitas pulsar Guardar.</div></div><div id="noteSavedActions" class="modal-actions wrap-actions ${editing?'':'hidden'}"><button id="noteDelete" class="danger-button" type="button">Eliminar</button><button id="noteShare" class="secondary-button" type="button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg> Compartir</button></div>`);
  state.noteEditor={id:editing?Number(id):0,titleManual,lastSaved:editing?`${titleManual?'1':'0'}\n${title.trim()}\n${String(existing.body||'').trim()}`:'',timer:null};
  const titleEl=$('#noteTitle'),bodyEl=$('#noteBody');
  bodyEl.addEventListener('input',()=>{if(!state.noteEditor?.titleManual)refreshAutoNoteTitle();scheduleNoteAutosave();});
  titleEl.addEventListener('input',()=>{
    const ed=state.noteEditor;if(!ed)return;
    if(titleEl.value.trim())ed.titleManual=true;
    else{ed.titleManual=false;refreshAutoNoteTitle();}
    scheduleNoteAutosave();
  });
  $('#noteVoiceBtn').addEventListener('click',()=>{
    const ed=state.noteEditor;if(!ed)return;
    const noteId=flushNoteAutosave(),currentBody=bodyEl.value.trim(),currentTitle=titleEl.value.trim(),manual=!!ed.titleManual;
    state.noteEditor=null;closeModal();startNoteVoice(currentBody,noteId,currentTitle,manual);
  });
  const share=()=>{const ed=state.noteEditor;if(!ed)return;const noteId=flushNoteAutosave();if(noteId>0)NexoNative.shareNexoItem(noteId,0,false);};
  const del=()=>{
    const ed=state.noteEditor;if(!ed)return;const noteId=flushNoteAutosave();if(!noteId)return;
    const label=$('#noteTitle')?.value.trim()||'esta nota';
    $('#confirmTitle').textContent='Eliminar nota';$('#confirmText').textContent=`¿Eliminar “${label}”? Podrás deshacerlo justo después.`;$('#confirmModal').classList.remove('hidden');
    state.confirmAction=()=>{
      const current=state.noteEditor;state.noteEditor=null;
      const res=parseNative(NexoNative.deleteItem(noteId),{});$('#confirmModal').classList.add('hidden');closeModal();
      if(res.ok){
        const finalizeTimer=setTimeout(()=>NexoNative.finalizeDelete(res.undoToken),6500);
        toast('Nota eliminada','Deshacer',()=>{clearTimeout(finalizeTimer);const rr=parseNative(NexoNative.restoreItem(res.undoToken),{});toast(rr.ok?'Nota restaurada':'No se pudo restaurar');refreshNotesSurface();},6000);
        refreshNotesSurface();
      }else{state.noteEditor=current;toast(res.error||'No se pudo eliminar la nota.');}
    };
  };
  $('#noteShare')?.addEventListener('click',share);$('#noteDelete')?.addEventListener('click',del);
  setTimeout(()=>{if(!body)bodyEl?.focus();else if(titleManual)titleEl?.focus();else bodyEl?.focus();},80);
}

function renderNotesPanel(){
  state.lastPanel='notes';
  const rows=parseNative(NexoNative.getNotes(),[]);
  $('#panelTitle').textContent='Notas';$('#panelSubtitle').textContent='Anotaciones locales · más recientes primero';
  $('#panelAction').innerHTML='';
  $('#panelContent').innerHTML=rows.length?`<div class="note-list">${rows.map(noteCard).join('')}</div>`:'<div class="empty-state">Todavía no tienes notas. Usa + para crear una o el micrófono para dictarla.</div>';
  bindNoteButtons($('#panelContent'));
}

function recordingDuration(ms){
  const total=Math.max(0,Math.floor(Number(ms||0)/1000)),h=Math.floor(total/3600),m=Math.floor((total%3600)/60),sec=total%60;
  return h>0?`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`;
}
function recordingSize(bytes){
  const n=Math.max(0,Number(bytes||0));if(n<1024)return `${Math.round(n)} B`;if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`;return `${(n/(1024*1024)).toFixed(1)} MB`;
}
function stopRecordingPolling(){if(state.recordingPoll){clearInterval(state.recordingPoll);state.recordingPoll=0;}}
function updateRecordingLive(status){
  const timer=$('#recordingLiveTimer'),label=$('#recordingLiveState'),toggle=$('#recordingPauseBtn');
  if(timer)timer.textContent=recordingDuration(status.elapsedMs||0);
  if(label)label.textContent=status.paused?'EN PAUSA':'GRABANDO';
  if(toggle)toggle.textContent=status.paused?'▶ Continuar':'Ⅱ Pausar';
}
function startRecordingPolling(initial){
  stopRecordingPolling();state.recordingWasActive=!!initial.active;state.recordingWasPaused=!!initial.paused;state.recordingActiveId=String(initial.id||'');
  if(initial.active)updateRecordingLive(initial);
  state.recordingPoll=setInterval(()=>{
    if(state.screen!=='panel'||state.lastPanel!=='recordings'){stopRecordingPolling();return;}
    const current=parseNative(NexoNative.getRecordingStatus(),{}),id=String(current.id||'');
    if(!!current.active!==state.recordingWasActive||!!current.paused!==state.recordingWasPaused||id!==state.recordingActiveId){renderRecordingsPanel();return;}
    if(current.active)updateRecordingLive(current);
  },1000);
}
function transcriptionStatus(id){return parseNative(NexoNative.getTranscriptionStatus(String(id)),{});}
function transcriptionSummary(id){
  const t=transcriptionStatus(id),stateName=String(t.state||'idle');
  if(NexoNative.hasTranscription(String(id))||stateName==='done')return `<div class="transcription-status done"><strong>TRANSCRIPCIÓN TERMINADA</strong><span>El texto está guardado localmente.</span><button class="secondary-button" data-transcription-view="${escAttr(id)}">Ver texto</button></div>`;
  if(stateName==='running'||stateName==='starting')return `<div class="transcription-status running"><strong>${esc(t.message||'Transcribiendo…')}</strong><span>${Math.max(0,Number(t.progress||0))}%</span><progress max="100" value="${Math.max(0,Number(t.progress||0))}"></progress><button class="secondary-button" data-transcription-cancel="${escAttr(id)}">Cancelar</button></div>`;
  if(stateName==='error')return `<div class="transcription-status error"><strong>ERROR DE TRANSCRIPCIÓN</strong><span>${esc(t.message||'El motor no pudo completar la transcripción.')}</span><button class="secondary-button" data-transcription-diagnostic="${escAttr(id)}">Copiar diagnóstico</button></div>`;
  return '';
}
function stopTranscriptionPolling(){if(state.transcriptionPoll){clearInterval(state.transcriptionPoll);state.transcriptionPoll=0;}}
function startTranscriptionPolling(){stopTranscriptionPolling();state.transcriptionPoll=setInterval(()=>{if(state.screen!=='panel'||state.lastPanel!=='recordings'){stopTranscriptionPolling();return;}const rows=parseNative(NexoNative.getRecordings(),[]);const running=rows.some(r=>{const t=transcriptionStatus(r.id);return t.state==='running'||t.state==='starting';});if(!running){stopTranscriptionPolling();renderRecordingsPanel();return;}document.querySelectorAll('[data-transcription-host]').forEach(host=>{host.innerHTML=transcriptionSummary(host.dataset.transcriptionHost);bindRecordingButtons(host);});},1500);}
function openTranscription(id){
  const text=String(NexoNative.getTranscription(String(id))||'');if(!text){toast('Todavía no hay una transcripción terminada.');return;}
  openModal('Transcripción',`<div class="transcription-view"><pre>${esc(text)}</pre></div><div class="scope-buttons"><button class="secondary-button" data-transcription-copy="${escAttr(id)}">Copiar todo</button><button class="secondary-button" data-transcription-share="${escAttr(id)}">Compartir</button><button class="secondary-button" data-transcription-export="${escAttr(id)}">Guardar TXT</button><button class="danger-button" data-transcription-delete="${escAttr(id)}">Eliminar transcripción</button></div>`);bindRecordingButtons($('#modalBody'));
}

function recordingCard(r,modelStatus=null){
  const playing=String(r.id)===String(state.playingRecordingId);
  const player=playing?`<div class="recording-player" data-recording-player="${escAttr(r.id)}"><input class="recording-seek" data-recording-seek="${escAttr(r.id)}" type="range" min="0" max="${Math.max(1,Number(r.durationMs||1))}" value="0" step="250"><div class="recording-player-row"><span data-recording-time="${escAttr(r.id)}">00:00 / ${recordingDuration(r.durationMs||0)}</span><div class="recording-speed" role="group" aria-label="Velocidad"><button data-recording-speed="1" type="button">1×</button><button data-recording-speed="1.5" type="button">1,5×</button><button data-recording-speed="2" type="button">2×</button></div></div></div>`:'';
  const id=String(r.id),transcribed=NexoNative.hasTranscription(id),t=transcriptionStatus(id),stateName=String(t.state||'idle');
  const running=stateName==='running'||stateName==='starting';
  const model=modelStatus||parseNative(NexoNative.getTranscriptionModelStatus(),{});
  const transcribeAction=!transcribed&&!running
    ?(model.installed
      ?`<button class="secondary-button recording-transcribe-button" data-transcription-start="${escAttr(id)}">${stateName==='error'?'Reintentar transcripción':'Transcribir'}</button>`
      :`<button class="secondary-button recording-transcribe-button" data-transcription-needs-model="${escAttr(id)}">Transcribir</button>`)
    :'';
  return `<div class="recording-card"><div class="recording-card-main"><div><strong>${esc(r.title||'Grabación')}</strong><span>${esc(fmtDate(Number(r.startedAt||0),true))}</span></div><div class="recording-meta"><span>${esc(recordingDuration(r.durationMs||0))}</span><span>${esc(recordingSize(r.sizeBytes||0))}${transcribed?' · Transcrita':''}</span></div></div>${player}<div data-transcription-host="${escAttr(id)}">${transcriptionSummary(id)}</div><div class="recording-actions"><button class="primary-button" data-recording-play="${escAttr(id)}">${uiIcon(playing?'close':'play')}${playing?' Detener':' Reproducir'}</button>${transcribed?`<button class="secondary-button" data-transcription-view="${escAttr(id)}">Ver texto</button>`:transcribeAction}<button class="secondary-button recording-options-button" data-recording-menu="${escAttr(id)}" aria-label="Opciones de ${escAttr(r.title||'grabación')}">⋯ Opciones</button><div class="record-card-actions">${recordIconAction('edit','Renombrar grabación',`data-recording-rename="${escAttr(id)}"`)}${recordIconAction('share','Compartir audio',`data-recording-share="${escAttr(id)}"`)}${recordIconAction('trash','Eliminar grabación',`data-recording-delete="${escAttr(id)}"`,'icon-action--danger')}</div></div></div>`;
}

function openRenameRecording(id){
  const rows=parseNative(NexoNative.getRecordings(),[]),r=rows.find(x=>String(x.id)===String(id));if(!r){toast('La grabación ya no existe.');return;}
  openModal('Renombrar grabación',`<div class="modal-form"><label>Título<input id="recordingRenameTitle" type="text" maxlength="120" value="${escAttr(r.title||'')}"></label></div><div class="modal-actions"><button id="recordingRenameCancel" class="secondary-button">Cancelar</button><button id="recordingRenameSave" class="primary-button">Guardar</button></div>`);
  setTimeout(()=>{const input=$('#recordingRenameTitle');input?.focus();input?.select();},60);
  $('#recordingRenameCancel').addEventListener('click',closeModal);
  $('#recordingRenameSave').addEventListener('click',()=>{const title=$('#recordingRenameTitle').value.trim();if(!title){toast('Escribe un título.');return;}const ok=NexoNative.renameRecording(String(id),title);closeModal();toast(ok?'Título actualizado':'No se pudo renombrar la grabación.');refreshRecordingSurface();});
}
function stopRecordingPlaybackPolling(){if(state.recordingPlaybackPoll){clearInterval(state.recordingPlaybackPoll);state.recordingPlaybackPoll=0;}}
function updateRecordingPlaybackUi(){
  const p=parseNative(NexoNative.getRecordingPlaybackState(),{});if(!p.active){stopRecordingPlaybackPolling();return;}
  const id=String(p.id||'');const seek=document.querySelector(`[data-recording-seek="${CSS.escape(id)}"]`),time=document.querySelector(`[data-recording-time="${CSS.escape(id)}"]`);
  if(seek&&!seek.matches(':active')){seek.max=String(Math.max(1,Number(p.durationMs||1)));seek.value=String(Math.max(0,Number(p.positionMs||0)));}
  if(time)time.textContent=`${recordingDuration(p.positionMs||0)} / ${recordingDuration(p.durationMs||0)}`;
  document.querySelectorAll('[data-recording-speed]').forEach(b=>b.classList.toggle('active',Math.abs(Number(b.dataset.recordingSpeed)-Number(p.speed||1))<0.05));
}
function startRecordingPlaybackPolling(){stopRecordingPlaybackPolling();updateRecordingPlaybackUi();state.recordingPlaybackPoll=setInterval(updateRecordingPlaybackUi,500);}

function bindRecordingButtons(root){
  root.querySelectorAll('[data-recording-menu]').forEach(b=>b.addEventListener('click',()=>openRecordingActions(String(b.dataset.recordingMenu))));
  root.querySelectorAll('[data-recording-play]').forEach(b=>b.addEventListener('click',()=>{const id=String(b.dataset.recordingPlay);if(state.playingRecordingId===id)NexoNative.stopRecordingPlayback();else NexoNative.playRecording(id);}));
  root.querySelectorAll('[data-recording-share]').forEach(b=>b.addEventListener('click',()=>NexoNative.shareRecording(String(b.dataset.recordingShare))));
  root.querySelectorAll('[data-recording-export]').forEach(b=>b.addEventListener('click',()=>NexoNative.exportRecording(String(b.dataset.recordingExport))));
  root.querySelectorAll('[data-recording-seek]').forEach(el=>el.addEventListener('input',()=>NexoNative.seekRecordingPlayback(String(el.dataset.recordingSeek),Number(el.value||0))));
  root.querySelectorAll('[data-recording-speed]').forEach(b=>b.addEventListener('click',()=>{NexoNative.setRecordingPlaybackSpeed(Number(b.dataset.recordingSpeed||1));updateRecordingPlaybackUi();}));
  root.querySelectorAll('[data-transcription-view]').forEach(b=>b.addEventListener('click',()=>openTranscription(String(b.dataset.transcriptionView))));
  root.querySelectorAll('[data-transcription-start]').forEach(b=>b.addEventListener('click',()=>{const id=String(b.dataset.transcriptionStart),fromModal=!!b.closest('#modalBody');b.disabled=true;b.textContent='Iniciando…';NexoNative.transcribeRecording(id);if(fromModal)closeModal();startTranscriptionPolling();toast('Preparando el archivo para transcripción…');}));
  root.querySelectorAll('[data-transcription-needs-model]').forEach(b=>b.addEventListener('click',()=>{const fromModal=!!b.closest('#modalBody');confirmGeneric('Preparar transcripción','Para transcribir sin enviar el audio a Internet, Nexo necesita descargar una vez Whisper Base multilingüe INT8 (~161 MB).',()=>{if(fromModal)closeModal();NexoNative.downloadTranscriptionModel();toast('Descargando modelo de transcripción…','',null,5000);},'Descargar');}));
  root.querySelectorAll('[data-transcription-cancel]').forEach(b=>b.addEventListener('click',()=>NexoNative.cancelTranscription(String(b.dataset.transcriptionCancel))));
  root.querySelectorAll('[data-transcription-copy]').forEach(b=>b.addEventListener('click',()=>{NexoNative.copyTranscription(String(b.dataset.transcriptionCopy));toast('Transcripción copiada');}));
  root.querySelectorAll('[data-transcription-share]').forEach(b=>b.addEventListener('click',()=>NexoNative.shareTranscription(String(b.dataset.transcriptionShare))));
  root.querySelectorAll('[data-transcription-export]').forEach(b=>b.addEventListener('click',()=>NexoNative.exportTranscription(String(b.dataset.transcriptionExport))));
  root.querySelectorAll('[data-transcription-delete]').forEach(b=>b.addEventListener('click',()=>{const id=String(b.dataset.transcriptionDelete);confirmGeneric('Eliminar transcripción','Se eliminará solo el texto. La grabación de audio se conserva.',()=>{NexoNative.deleteTranscription(id);closeModal();refreshRecordingSurface();},'Eliminar');}));
  root.querySelectorAll('[data-transcription-diagnostic]').forEach(b=>b.addEventListener('click',()=>{const id=String(b.dataset.transcriptionDiagnostic),d=String(NexoNative.getTranscriptionDiagnostic(id)||'');if(d){navigator.clipboard?.writeText(d).catch(()=>{});toast('Diagnóstico preparado para copiar');openModal('Diagnóstico de transcripción',`<textarea rows="14" style="width:100%">${esc(d)}</textarea><div class="modal-actions"><button id="diagCopy" class="primary-button">Copiar diagnóstico</button></div>`);$('#diagCopy').addEventListener('click',()=>{const t=$('#modalBody textarea');t.select();document.execCommand('copy');toast('Diagnóstico copiado');});}else toast('No hay diagnóstico disponible.');}));
  root.querySelectorAll('[data-recording-rename]').forEach(b=>b.addEventListener('click',()=>openRenameRecording(String(b.dataset.recordingRename))));
  root.querySelectorAll('[data-recording-delete]').forEach(b=>b.addEventListener('click',()=>{const id=String(b.dataset.recordingDelete),rows=parseNative(NexoNative.getRecordings(),[]),r=rows.find(x=>String(x.id)===id),title=r?.title||'esta grabación';confirmGeneric('Eliminar grabación',`Se eliminará “${title}” y el archivo de audio. Esta acción no se puede deshacer.`,()=>{if(state.playingRecordingId===id)NexoNative.stopRecordingPlayback();const ok=NexoNative.deleteRecording(id);toast(ok?'Grabación eliminada':'No se pudo eliminar la grabación.');closeModal();refreshRecordingSurface();},'Eliminar');}));
}
function renderRecordingsPanel(){
  state.lastPanel='recordings';
  const status=parseNative(NexoNative.getRecordingStatus(),{}),all=parseNative(NexoNative.getRecordings(),[]),rows=all.filter(r=>String(r.id)!==String(status.id||''));
  $('#panelTitle').textContent='Grabaciones';$('#panelSubtitle').textContent='Audio local · reuniones y conversaciones autorizadas';
  $('#panelAction').innerHTML=status.active?'<button class="recording-active-pill" type="button" disabled>● EN CURSO</button>':'<button id="recordingStartTop" class="primary-button recording-start-button" type="button">● Grabar</button>';
  const live=status.active?`<div class="recording-live-card ${status.paused?'paused':''}"><div class="recording-live-head"><span class="recording-live-dot"></span><div><strong id="recordingLiveState">${status.paused?'EN PAUSA':'GRABANDO'}</strong><span>${esc(status.title||'Grabación')}</span></div><div id="recordingLiveTimer" class="recording-timer">${esc(recordingDuration(status.elapsedMs||0))}</div></div><div class="recording-live-actions"><button id="recordingPauseBtn" class="secondary-button" type="button">${status.paused?'▶ Continuar':'Ⅱ Pausar'}</button><button id="recordingStopBtn" class="danger-button solid" type="button">■ Terminar</button></div><div class="small muted">La grabación continúa con la pantalla apagada. Android mostrará el indicador de micrófono y una notificación silenciosa.</div></div>`:'';
  const empty=!rows.length&&!status.active?'<div class="empty-state">Todavía no tienes grabaciones. Pulsa Grabar para comenzar inmediatamente.</div>':'';
  const model=parseNative(NexoNative.getTranscriptionModelStatus(),{});const modelCard=model.installed?`<div class="recording-info-card transcription-model-ready"><strong>Transcripción local preparada</strong><span>${esc(model.name||'Whisper Base INT8')} · el audio no sale del teléfono.</span></div>`:`<div class="recording-info-card"><strong>Transcripción local</strong><span>Descarga una vez Whisper Base multilingüe INT8 (~161 MB) para transcribir sin enviar audio a Internet.</span><button id="downloadTranscriptionModel" class="secondary-button" type="button">Descargar modelo</button><div id="transcriptionModelProgress" class="small muted"></div></div>`;
  $('#panelContent').innerHTML=`${live}<div class="recording-info-card"><strong>Grabación local</strong><span>Nexo no emite sonidos al iniciar o terminar. Los archivos se guardan solo en este dispositivo. Usa “Descargas” para crear una copia accesible en Descargas/Nexo.</span></div>${modelCard}${rows.length?`<div class="recording-list">${rows.map(r=>recordingCard(r,model)).join('')}</div>`:empty}`;
  $('#recordingStartTop')?.addEventListener('click',()=>{state.playingRecordingId='';NexoNative.stopRecordingPlayback();NexoNative.startRecording();toast('Iniciando grabación…');});
  $('#recordingPauseBtn')?.addEventListener('click',()=>{if(status.paused)NexoNative.resumeRecording();else NexoNative.pauseRecording();});
  $('#recordingStopBtn')?.addEventListener('click',()=>{NexoNative.stopRecording();toast('Finalizando y guardando grabación…');});
  $('#downloadTranscriptionModel')?.addEventListener('click',()=>{const b=$('#downloadTranscriptionModel');b.disabled=true;b.textContent='Descargando…';NexoNative.downloadTranscriptionModel();});
  bindRecordingButtons($('#panelContent'));startRecordingPolling(status);if(rows.some(r=>{const t=transcriptionStatus(r.id);return t.state==='running'||t.state==='starting';}))startTranscriptionPolling();
}

function renderTodaySummary(){
  if(!$('#todaySummary'))return;
  const all=state.todaySummaryLines||[],shown=state.todaySummaryExpanded?all:all.slice(0,3);
  $('#todaySummary').innerHTML=shown.map(x=>`<div class="summary-item ${x.kind}"><span class="summary-dot"></span><span>${esc(x.text)}</span></div>`).join('');
  const more=$('#todaySummaryMore');
  more.classList.toggle('hidden',all.length<=3);
  more.textContent=state.todaySummaryExpanded?'Ver menos':`Ver todo (${all.length})`;
}

function calendarDotClass(i){
  const c=categoryLabel(i?.category).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es-ES');
  if(c==='familia')return 'family';
  if(c==='cumpleanos')return 'birthday';
  if(c==='santo / onomastica')return 'saint';
  if(c==='salud / medico')return 'health';
  if(c==='colegio / estudios')return 'study';
  if(c==='trabajo')return 'work';
  if(c==='viaje')return 'travel';
  if(c==='vacaciones')return 'vacation';
  if(c==='casa')return 'home';
  if(c==='vehiculos / itv')return 'vehicle';
  if(c==='seguros')return 'insurance';
  if(c==='tramites')return 'paperwork';
  if(c==='tarea / recado')return 'errand';
  if(c==='compras')return 'shopping';
  if(c==='ocio / social')return 'leisure';
  if(c==='deporte')return 'sport';
  if(c==='recordatorio')return 'reminder';
  return 'other';
}

function requestHolidayYears(years){[...new Set(years.map(Number).filter(y=>y>=2000&&y<=2100))].forEach(year=>{if(state.holidayYears[year]||state.holidayLoading.has(year))return;state.holidayLoading.add(year);NexoNative.requestHolidayCalendar(year);});}
function holidayName(h){const n=h?.name;if(typeof n==='string'&&n.trim())return n.trim();if(n&&typeof n==='object')return n.es||n['ca-valencia']||n.ca||n.gl||n.eu||Object.values(n)[0]||'Festivo';return 'Festivo';}
function holidayLevelLabel(level){return level==='national'?'Nacional':level==='regional'?'Autonómico':level==='provincial'?'Provincial':'Local';}
function holidayEntriesForYears(years){return [...new Set(years.map(Number))].flatMap(y=>{const r=state.holidayYears[y];return r&&Array.isArray(r.holidays)?r.holidays:[];});}
function holidayCard(h){const s=h?.source||{},ref=String(s.ref||'').trim(),url=String(s.url||'').trim();const sourceHtml=url?`<button type="button" class="holiday-source-link" data-holiday-source="${escAttr(url)}">${esc(ref?'Fuente: '+ref:'Ver fuente')}</button>`:ref?`<small>Fuente: ${esc(ref)}</small>`:'';return `<article class="selected-day-holiday"><span class="selected-day-holiday__level selected-day-holiday__level--${escAttr(h.level||'local')}">${esc(holidayLevelLabel(h.level))}</span><div><strong>${esc(holidayName(h))}</strong>${sourceHtml}</div></article>`;}
function bindHolidaySources(root=document){root.querySelectorAll('[data-holiday-source]').forEach(b=>b.addEventListener('click',()=>NexoNative.openExternal(String(b.dataset.holidaySource||''))));}

function homeDayLabel(iso){
  const d=new Date(`${iso}T12:00:00`),today=iso===isoDateLocal(new Date());
  const full=new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long'}).format(d).toLocaleUpperCase('es-ES');
  return today?`HOY · ${full}`:full;
}

function shiftCalendarMonthValue(value,delta){
  const current=value instanceof Date?value:new Date();
  return new Date(current.getFullYear(),current.getMonth()+delta,1);
}

function changeHomeCalendarMonth(delta){changeCalendarMonth(delta);}

function changeCalendarMonth(delta){
  const previous=state.calendarSelected?new Date(state.calendarSelected+'T12:00:00'):new Date();
  state.calendarMonth=shiftCalendarMonthValue(state.calendarMonth,delta);
  const m=state.calendarMonth,day=Math.min(previous.getDate(),new Date(m.getFullYear(),m.getMonth()+1,0).getDate());
  state.calendarSelected=isoDateLocal(new Date(m.getFullYear(),m.getMonth(),day));
  renderCalendar(delta);
}

function attachMonthSwipe(element,onPrevious,onNext){
  if(!element||element.dataset.monthSwipeReady==='1')return;
  element.dataset.monthSwipeReady='1';
  let startX=0,startY=0,tracking=false,suppressClickUntil=0;
  const reset=()=>{tracking=false;startX=0;startY=0;};
  element.addEventListener('touchstart',e=>{
    if(e.touches.length!==1){reset();return;}
    const t=e.touches[0];startX=t.clientX;startY=t.clientY;tracking=true;
  },{passive:true});
  element.addEventListener('touchend',e=>{
    if(!tracking||e.changedTouches.length!==1){reset();return;}
    const t=e.changedTouches[0],dx=t.clientX-startX,dy=t.clientY-startY;
    reset();
    const horizontal=Math.abs(dx)>=48&&Math.abs(dx)>Math.abs(dy)*1.35;
    if(!horizontal)return;
    suppressClickUntil=Date.now()+450;
    if(dx<0)onNext();else onPrevious();
  },{passive:true});
  element.addEventListener('touchcancel',reset,{passive:true});
  element.addEventListener('click',e=>{
    if(Date.now()<suppressClickUntil){e.preventDefault();e.stopPropagation();}
  },true);
}

function renderHomeCalendar(){if(state.screen==='calendar')renderCalendar();}

function renderHomeAgenda(){if(state.screen==='calendar')renderCalendarDay();}

function recordIconAction(icon,label,attrs='',extra=''){
  return `<button type="button" class="icon-action ${extra}" ${attrs} aria-label="${escAttr(label)}" title="${escAttr(label)}">${uiIcon(icon)}</button>`;
}
function recordEditorActionBar(saveId,editId,shareId,deleteId){
  return `<div class="record-form-actions">
    <button id="${saveId}" class="primary-button" type="button">${uiIcon('check')} Guardar</button>
    <button id="${editId}" class="secondary-button" type="button">${uiIcon('edit')} Editar</button>
    <button id="${shareId}" class="secondary-button" type="button">${uiIcon('share')} Compartir</button>
    <button id="${deleteId}" class="danger-button" type="button">${uiIcon('trash')} Eliminar</button>
  </div>`;
}
function deleteItemFromCard(id){
  const item=parseNative(NexoNative.getItem(Number(id)),{});
  if(item.id)confirmDeleteItem(item);
}
function shareMedicationCard(id){
  const m=parseNative(NexoNative.getMedication(Number(id)),{});
  if(!m.id)return;
  const text=[m.name,m.dose,m.instructions,medicationScheduleLabel(m)].filter(Boolean).join(' · ');
  NexoNative.sharePlainText(m.name||'Medicación',text);
}

function agendaCard(i,showDate=false){
  const recurring=!!i.recurring||(i.recurrence&&i.recurrence!=='none'),done=i.status==='done'||i.occurrenceStatus==='done',cancelled=i.status==='cancelled';
  const id=Number(i.seriesId||i.id),occ=Number(i.occurrenceAt||i.dueAt||0),tone=calendarDotClass(i);
  const open=recurring?`data-open-occurrence="${id}" data-occurrence-at="${occ}"`:`data-open-item="${id}"`;
  const when=i.seriesEnded?'Serie sin próximas fechas':!i.dueAt?'Sin fecha':showDate?fmtDueItem(i):fmtAgendaTime(i);
  const description=String(i.body||'').trim(),title=String(i.title||'Cita');
  return `<div class="agenda-row tone-${tone} ${done?'item-done':''}" data-appointment-row="${id}"><button type="button" class="agenda-open" ${open}><span class="agenda-meta"><span class="agenda-time">${esc(when)}</span><span class="agenda-category">${esc(categoryLabel(i.category))}</span>${done?'<span class="agenda-status">Completada</span>':cancelled?'<span class="agenda-status">Cancelada</span>':''}</span><strong class="agenda-title">${esc(title)}</strong>${description&&description!==title?`<span class="agenda-description">${esc(description)}</span>`:''}${i.locationText?`<span class="agenda-location">${uiIcon('pin')}${esc(i.locationText)}</span>`:''}${eventParticipantAvatarsHtml(i,'event-participant-avatars--agenda')}${recurring?`<span class="agenda-repeat">${esc(recurrenceLabel(i.recurrence))}</span>`:''}</button><div class="record-card-actions agenda-record-actions">${recordIconAction('edit','Editar evento',recurring?`data-open-occurrence="${id}" data-occurrence-at="${occ}"`:`data-open-item="${id}"`)}${recordIconAction('share','Compartir evento',`data-share-item="${id}" data-share-occurrence="${occ}" data-share-recurring="${recurring?'1':'0'}" data-share-has-date="${i.dueAt?'1':'0'}"`)}${recordIconAction('trash','Eliminar evento',`data-card-delete-item="${id}" data-card-delete-occurrence="${occ}" data-card-delete-recurring="${recurring?'1':'0'}"`,'icon-action--danger')}</div></div>`;
}

function prepareHomeCapture(type='',text=''){
  prepareQuickNoteForType(type,state.screen==='calendar'?state.calendarSelected:'');
  if(text){$('#captureText').value=text;analyzeCapture({immediate:true});}
}

function itemCard(i) {
  if(i.type==='document')return documentCard(i);
  if(isPlaceType(i.type))return placeCard(i);
  const due=fmtDueItem(i), recurring=!!i.recurring || (i.recurrence&&i.recurrence!=='none');
  const id=i.seriesId||i.id,occ=i.occurrenceAt||i.dueAt||0;
  const open=recurring?`data-open-occurrence="${id}" data-occurrence-at="${occ}"`:`data-open-item="${i.id}"`;
  const done=(i.status==='done'||i.occurrenceStatus==='done'),cancelled=i.status==='cancelled';
  return `<div class="item-card ${done?'item-done':''}" role="button" tabindex="0" data-item-card-open="${id}" data-item-card-occurrence="${occ}" data-item-card-recurring="${recurring?'1':'0'}">
    <div class="item-top"><div class="item-title">${esc(i.title)}</div><div class="record-card-actions">${recordIconAction('edit','Editar',open)}${recordIconAction('share','Compartir',`data-share-item="${id}" data-share-occurrence="${occ}" data-share-recurring="${recurring?'1':'0'}" data-share-has-date="${i.dueAt?'1':'0'}"`)}${recordIconAction('trash','Eliminar',`data-card-delete-item="${id}" data-card-delete-occurrence="${occ}" data-card-delete-recurring="${recurring?'1':'0'}"`,'icon-action--danger')}</div></div>
    <div class="item-meta"><span class="pill">${esc(categoryLabel(i.category))}</span><span class="meta-tag">${esc(typeLabel(i.type))}</span>${due?`<span class="meta-tag">${esc(due)}</span>`:''}${recurring?`<span class="meta-tag recurrence-tag">↻ ${esc(recurrenceLabel(i.recurrence))}</span>`:''}${done?'<span class="meta-tag done-tag">Realizada</span>':cancelled?'<span class="meta-tag done-tag">Cancelada</span>':''}</div>
    ${i.body&&i.body!==i.title?`<div class="item-body">${esc(i.body)}</div>`:''}
    ${i.locationText?`<div class="item-location"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-pin"></use></svg> ${esc(i.locationText)}</div>`:''}
    ${eventParticipantAvatarsHtml(i,'event-participant-avatars--item')}
    <div class="item-actions"><button class="mini-button" ${open}>Ver / editar</button>${i.locationText?`<button class="mini-button" data-go-location="${escAttr(i.locationText)}"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-right"></use></svg> Ir</button>`:''}<button class="mini-button" data-share-item="${id}" data-share-occurrence="${occ}" data-share-recurring="${recurring?'1':'0'}" data-share-has-date="${i.dueAt?'1':'0'}"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg> Compartir</button>${isAppointmentType(i.type)&&!done&&i.dueAt?`<button class="mini-button" data-postpone-event="${id}" data-postpone-occurrence="${occ}" data-postpone-recurring="${recurring?'1':'0'}">Posponer</button>`:''}</div>
  </div>`;
}
function bindItemButtons(root=document){
  bindPlaceButtons(root);
  bindDocumentButtons(root);
  root.querySelectorAll('[data-item-card-open]').forEach(card=>{
    const open=()=>{const id=Number(card.dataset.itemCardOpen),occ=Number(card.dataset.itemCardOccurrence||0),rec=card.dataset.itemCardRecurring==='1';if(rec)openOccurrence(id,occ);else openItem(id);};
    card.addEventListener('click',e=>{if(e.target.closest('button,a,input,select,textarea,label'))return;open();});
    card.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&!e.target.closest('button,a,input,select,textarea')){e.preventDefault();open();}});
  });
  root.querySelectorAll('[data-agenda-options]').forEach(b=>b.addEventListener('click',()=>openAgendaOptions(Number(b.dataset.agendaOptions),Number(b.dataset.agendaOccurrence),b.dataset.agendaRecurring==='1')));
  root.querySelectorAll('[data-open-item]').forEach(b=>b.addEventListener('click',()=>openItem(Number(b.dataset.openItem))));
  root.querySelectorAll('[data-open-occurrence]').forEach(b=>b.addEventListener('click',()=>openOccurrence(Number(b.dataset.openOccurrence),Number(b.dataset.occurrenceAt))));
  root.querySelectorAll('[data-share-item]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openItemShareMenu(Number(b.dataset.shareItem),Number(b.dataset.shareOccurrence||0),b.dataset.shareRecurring==='1',b.dataset.shareHasDate==='1');}));
  root.querySelectorAll('[data-card-delete-item]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const id=Number(b.dataset.cardDeleteItem),occ=Number(b.dataset.cardDeleteOccurrence||0),rec=b.dataset.cardDeleteRecurring==='1';if(rec)openAgendaOptions(id,occ,true);else deleteItemFromCard(id);}));
  root.querySelectorAll('[data-go-location]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const place=String(b.dataset.goLocation||'').trim();if(place)NexoNative.openLocationDirections(place);}));
  root.querySelectorAll('[data-postpone-event]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openPostponeSheet(Number(b.dataset.postponeEvent),Number(b.dataset.postponeOccurrence||0),b.dataset.postponeRecurring==='1');}));
}
function openItemShareMenu(id,occurrenceAt,recurring,hasDate){
  const overlay=document.createElement('div');overlay.className='modal scope-overlay';
  overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Compartir</h2><p class="muted">Elige el formato según quién vaya a recibirlo.</p><div class="scope-buttons"><button class="primary-button" data-share-format="nexo">Nexo · conservar todos los datos</button>${hasDate?'<button class="secondary-button" data-share-format="ics">Calendario (.ics)</button>':''}<button class="ghost-button" data-share-cancel>Cancelar</button></div></div>`;
  document.body.appendChild(overlay);
  overlay.querySelector('[data-share-format="nexo"]').addEventListener('click',()=>{overlay.remove();shareNexoItem(id,occurrenceAt,recurring);});
  if(overlay.querySelector('[data-share-format="ics"]'))overlay.querySelector('[data-share-format="ics"]').addEventListener('click',()=>{overlay.remove();shareCalendarEvent(id,occurrenceAt,recurring);});
  overlay.querySelector('[data-share-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
}
function shareNexoItem(id,occurrenceAt,recurring){
  if(recurring){
    const overlay=document.createElement('div');overlay.className='modal scope-overlay';
    overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Compartir con Nexo</h2><p class="muted">Puedes enviar solo esta aparición o toda la serie.</p><div class="scope-buttons"><button class="secondary-button" data-nexo-scope="single">Solo esta aparición</button><button class="primary-button" data-nexo-scope="series">Toda la serie</button><button class="ghost-button" data-nexo-cancel>Cancelar</button></div></div>`;
    document.body.appendChild(overlay);overlay.querySelector('[data-nexo-scope="single"]').addEventListener('click',()=>{overlay.remove();NexoNative.shareNexoItem(id,occurrenceAt,false);});overlay.querySelector('[data-nexo-scope="series"]').addEventListener('click',()=>{overlay.remove();NexoNative.shareNexoItem(id,occurrenceAt,true);});overlay.querySelector('[data-nexo-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
  }else NexoNative.shareNexoItem(id,occurrenceAt,false);
}
function shareCalendarEvent(id,occurrenceAt,recurring){
  if(recurring){
    const overlay=document.createElement('div');overlay.className='modal scope-overlay';
    overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Compartir evento</h2><p class="muted">El archivo .ics podrá importarse en cualquier calendario compatible.</p><div class="scope-buttons"><button class="secondary-button" data-share-scope="single">Solo esta aparición</button><button class="primary-button" data-share-scope="series">Toda la serie</button><button class="ghost-button" data-share-cancel>Cancelar</button></div></div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('[data-share-scope="single"]').addEventListener('click',()=>{overlay.remove();NexoNative.shareCalendarEvent(id,occurrenceAt,false);});
    overlay.querySelector('[data-share-scope="series"]').addEventListener('click',()=>{overlay.remove();NexoNative.shareCalendarEvent(id,occurrenceAt,true);});
    overlay.querySelector('[data-share-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
  }else NexoNative.shareCalendarEvent(id,occurrenceAt,false);
}
function openPostponeSheet(id,occurrenceAt,recurring){
  const source=recurring?parseNative(NexoNative.getOccurrence(id,occurrenceAt),{}):parseNative(NexoNative.getItem(id),{});if(!source.id)return;
  const overlay=document.createElement('div');overlay.className='modal scope-overlay';
  overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>Posponer cita</h2><p class="muted">Elige una nueva fecha para ${esc(source.title)}.${recurring?' Solo cambia esta aparición.':''}</p><div class="scope-buttons"><button class="secondary-button" data-postpone-days="1">Mañana</button><button class="secondary-button" data-postpone-days="7">Dentro de una semana</button><button class="secondary-button" data-postpone-custom>Elegir fecha…</button><button class="ghost-button" data-postpone-cancel>Cancelar</button></div></div>`;
  document.body.appendChild(overlay);
  const apply=date=>{const iso=isoDateLocal(date),time=source.hasTime!==false?isoFromMs(source.dueAt).time:'';const due=dateTimeToMs(iso,time);let r;if(recurring)r=parseNative(NexoNative.updateOccurrence(JSON.stringify({seriesId:id,occurrenceAt,dueAt:due,hasTime:source.hasTime!==false,durationMinutes:source.durationMinutes||0,occurrenceState:'postponed'}),'single'),{});else r=parseNative(NexoNative.updateItem(JSON.stringify({id,dueAt:due,hasTime:source.hasTime!==false,status:'postponed',statusManual:true})),{});overlay.remove();toast(r.ok?'Cita pospuesta':'No se pudo posponer');refreshCurrent();};
  overlay.querySelectorAll('[data-postpone-days]').forEach(b=>b.addEventListener('click',()=>{const d=new Date();d.setDate(d.getDate()+Number(b.dataset.postponeDays));apply(d);}));
  overlay.querySelector('[data-postpone-custom]').addEventListener('click',()=>{overlay.remove();openModal('Posponer cita',`<div class="modal-form"><label>Nueva fecha<div class="date-wrap"><input id="postponeDate" type="date" value="${escAttr(isoFromMs(source.dueAt).date)}"></div></label></div><div class="modal-actions"><button id="postponeSave" class="primary-button">Mover cita</button></div>`);$('#postponeSave').addEventListener('click',()=>{const v=$('#postponeDate').value;if(!v){toast('Elige una fecha');return;}const d=new Date(`${v}T12:00:00`);closeModal();apply(d);});});
  overlay.querySelector('[data-postpone-cancel]').addEventListener('click',()=>overlay.remove());overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
}

function prepareQuickNote(){prepareQuickNoteForType('');}

function prepareQuickNoteForType(type='event',date=''){
  if(type==='document'){openDocumentEditor();return;}
  if(isPlaceType(type)){openPlaceEditor();return;}
  type=(!type||isAppointmentType(type))?'event':type;
  navigate('save');clearCapture();
  state.capturePinnedType=type;state.capturePinnedDate=date||'';
  $('#captureType').value=type;
  if(date){$('#captureDate').value=date;$('#captureEndDate').value=date;}
  $('#captureDetails').open=false;
  $('#captureHeading').textContent=type==='event'?'Nueva cita':`Crear ${typeLabel(type).toLocaleLowerCase('es-ES')}`;
  updateCaptureContext();refreshCaptureAlertEditor();
  setTimeout(()=>$('#captureText').focus(),80);
}

function startVoiceCapture(pinnedDate='',pinnedType='') { state.voiceMode='capture'; const existing=(!pinnedDate&&state.screen==='save')?($('#captureText')?.value.trim()||''):''; state.voiceBaseText=existing;state.voicePinnedDate=pinnedDate||'';state.voicePinnedType=pinnedType||state.capturePinnedType||'event';navigate('save'); if(pinnedDate){state.capturePinnedDate=pinnedDate;state.capturePinnedType=pinnedType||'';state.captureReturn='today';clearCapture(true);state.voiceBaseText='';$('#captureDate').value=pinnedDate;$('#captureEndDate').value=pinnedDate;if(pinnedType)$('#captureType').value=pinnedType;refreshCaptureAlertEditor();} setInterpretationStatus('listening','Escuchando… puedes hacer una pausa corta'); NexoNative.startVoiceInput(); }

function analyzeCapture(options={}) {
  const text=$('#captureText').value.trim();
  if(!text){ resetAnalysis(); return; }
  const pinned=String(state.capturePinnedType||'');
  const appointment=!!pinned&&isAppointmentType(pinned);
  const local=parseNative(appointment?NexoNative.classifyAppointment(text):NexoNative.classify(text),{});
  if(appointment)local.type='event';
  state.classification=local;
  renderClassification(local,true);
  const review=!!local.needsReview;
  const message=review?(local.reason||'Hay un dato que conviene revisar.'):
    (appointment?'Datos extraídos. Revisa la cita.':'Nexo ha clasificado lo que has dicho. Revisa la propuesta.');
  setInterpretationStatus(review?'review':'success',message);
}

function setInterpretationStatus(kind,text) {
  const el=$('#localStatus'); if(!el)return; el.className=`interpretation-status ${kind}`; el.innerHTML=`<span class="status-dot"></span><span>${esc(text)}</span>`;
}

function updatePreviewAlerts(){
  const el=$('#previewAlerts');if(!el)return;const vals=normalizeAlertMinutes(state.captureAlerts),voice=!!$('#captureVoiceEnabled')?.checked;
  el.classList.toggle('hidden',!vals.length&&!voice);el.textContent=(vals.length?`🔔 ${vals.map(humanLeadMinutes).join(' · ')}`:'')+(voice?`${vals.length?' · ':''}🔊 VOZ`: '');
}
function setCaptureAlerts(values,userChange=false){
  state.captureAlerts=normalizeAlertMinutes(values);if(userChange)markCaptureManual('alerts');refreshCaptureAlertEditor();
}
function refreshCaptureAlertEditor(){
  updateCaptureContext();
  const hasDate=!!$('#captureDate')?.value,type=$('#captureType')?.value||state.classification?.type||'note';
  const voiceEligible=hasDate&&['event','reminder','task','expiry'].includes(type);
  $('#captureAlertsSection')?.classList.toggle('hidden',!hasDate);
  $('#captureVoiceRow')?.classList.toggle('hidden',!voiceEligible);
  if(!voiceEligible&&$('#captureVoiceEnabled'))$('#captureVoiceEnabled').checked=false;
  renderAlertList('#captureAlertList',state.captureAlerts,next=>setCaptureAlerts(next,true));updatePreviewAlerts();
}

function renderClassification(a, fillForm=true) {
  state.classification=a;
  const pinned=String(state.capturePinnedType||'');
  const inferredType=pinned||a.type||'note';
  $('#classificationPreview').classList.toggle('hidden',!!pinned&&isAppointmentType(pinned));
  $('#previewType').textContent=typeLabel(inferredType);
  $('#previewTitle').textContent=a.title||'Nota rápida';
  $('#previewCategory').textContent=a.category||'Otros';
  $('#previewLocation').textContent=a.locationText?`📍 ${a.locationText}`:'';
  $('#previewLocation').classList.toggle('hidden',!a.locationText);
  $('#previewDescription').textContent=a.description?`DESCRIPCIÓN: ${a.description}`:'';
  $('#previewDescription').classList.toggle('hidden',!a.description);
  $('#previewDate').textContent=a.dueDate?displayAnalysisDate(a):(state.capturePinnedDate?fmtDate(dateTimeToMs(state.capturePinnedDate,''),false):'Sin fecha');
  $('#previewRecurrence').textContent=a.recurrence&&a.recurrence!=='none'?`↻ ${recurrenceLabel(a.recurrence)}`:'';
  $('#previewReason').textContent=a.reason||'';

  if(fillForm){
    if(!captureFieldLocked('title'))$('#captureTitle').value=a.title||'';
    if(!captureFieldLocked('category'))$('#captureCategory').value=CATEGORIES.includes(categoryLabel(a.category))?categoryLabel(a.category):'Otros';
    if(!captureFieldLocked('type'))$('#captureType').value=inferredType;

    if(!captureFieldLocked('dateTime')){
      $('#captureDate').value=a.dueDate||state.capturePinnedDate||'';
      $('#captureEndDate').value=a.dueEndDate||a.dueDate||state.capturePinnedDate||'';
      $('#captureAllDay').checked=!a.hasTime;
      $('#captureTime').value=a.hasTime?(a.dueTime||''):'';
      $('#captureEndTime').value=a.hasTime?(a.dueEndTime||''):'';
      syncCaptureAllDay(false);
    }

    if(!captureFieldLocked('location'))$('#captureLocation').value=a.locationText||'';
    if(!captureFieldLocked('mapsUrl'))$('#captureMapsUrl').value=a.mapsUrl||'';
    if(!captureFieldLocked('timeZone'))$('#captureTimeZone').value=a.timeZone||'Europe/Madrid';
    if(!captureFieldLocked('description'))$('#captureDescription').value=a.description||'';
    if(!captureFieldLocked('priority'))$('#capturePriority').value=String(a.priority||1);

    if(!captureFieldLocked('recurrence')){
      const recurrence=(a.recurrence&&RECURRENCE_LABEL[a.recurrence])?a.recurrence:'none';
      $('#captureRecurrence').value=recurrence;
      if(recurrence!=='none'){
        $('#captureRecurrenceUntilLabel').classList.remove('hidden');
        $('#captureRecurrenceUntil').value=a.recurrenceUntilDate||'';
      }else{
        $('#captureRecurrenceUntilLabel').classList.add('hidden');
        $('#captureRecurrenceUntil').value='';
      }
    }

    if(!captureFieldLocked('alerts')){
      if($('#captureVoiceEnabled'))$('#captureVoiceEnabled').checked=!!a.voiceRequested;
      const parsedLeads=normalizeAlertMinutes(Array.isArray(a.reminderLeadMinutes)?a.reminderLeadMinutes:[]);
      const explicitReminder=!!a.reminderRequested || !!a.voiceRequested || a.type==='reminder';
      if($('#captureDate').value && explicitReminder){
        state.captureAlerts=parsedLeads.length?parsedLeads:[Math.max(0,Number(a.suggestedLeadDays)||0)*1440];
        state.captureSuggestedAlert=null;
      }else if($('#captureDate').value && a.type==='expiry'){
        state.captureAlerts=[];
        state.captureSuggestedAlert=parsedLeads[0] ?? (Math.max(0,Number(a.suggestedLeadDays)||30)*1440);
      }else{
        state.captureAlerts=[];state.captureSuggestedAlert=null;
      }
    }
  }

  refreshCaptureAlertEditor();
  const showSuggestion=state.captureSuggestedAlert!==null && !!$('#captureDate').value && !state.captureAlerts.includes(state.captureSuggestedAlert);
  $('#reminderSuggestion').classList.toggle('hidden',!showSuggestion);
  if(showSuggestion)$('#reminderSuggestionText').textContent=`${humanLeadMinutes(state.captureSuggestedAlert)} · ${a.dueDate?displayAnalysisDate(a):fmtDate(dateTimeToMs($('#captureDate').value,''),false)}`;
}

function resetAnalysis() {
  state.classification=null;
  state.captureAlerts=[];state.captureSuggestedAlert=null;
  $('#classificationPreview').classList.add('hidden'); $('#reminderSuggestion').classList.add('hidden');
  $('#captureTitle').value=''; $('#captureDescription').value=''; $('#captureCategory').value='Otro'; $('#captureType').value=state.capturePinnedType||'event'; $('#captureDate').value=state.capturePinnedDate||''; $('#captureEndDate').value=state.capturePinnedDate||''; $('#captureAllDay').checked=false; $('#captureTime').value='09:00'; $('#captureEndTime').value='10:00'; $('#captureLocation').value=''; $('#captureMapsUrl').value=''; $('#captureTimeZone').value='Europe/Madrid'; $('#capturePriority').value='1'; if($('#captureVoiceEnabled'))$('#captureVoiceEnabled').checked=false; $('#captureRecurrence').value='none'; $('#captureRecurrenceUntil').value=''; $('#captureRecurrenceUntilLabel').classList.add('hidden'); syncCaptureAllDay(false);
  state.captureParticipantIds=[];renderCapturePeopleAssignment();
  refreshCaptureAlertEditor();
  setInterpretationStatus('idle','Interpretación local preparada');
}

function clearCapture(keepPinned=false) {
  clearTimeout(state.pendingSaveTimer);
  state.pendingSave=false; state.attachment=null; resetCaptureManualFields(); state.inboxCaptureId=0; state.inboxCaptureOriginal=null; state.calendarUpdateItemId=0;
  if(!keepPinned){state.capturePinnedDate='';state.capturePinnedType='event';state.captureReturn='today';}
  $('#captureDetails').open=false;$('#captureHeading').textContent='Nueva cita';
  $('#captureText').value=''; $('#attachmentChip').classList.add('hidden'); $('#attachmentChip').textContent='';
  resetAnalysis();
}

function saveCapture() {
  const text=$('#captureText').value.trim();
  if(!text && !$('#captureTitle').value.trim() && !$('#captureDescription').value.trim() && !state.attachment){toast('Escribe qué necesitas guardar.');return;}
  commitCapture();
}

function commitCapture() {
  clearTimeout(state.pendingSaveTimer); state.pendingSave=false;
  const text=$('#captureText').value.trim();
  const date=$('#captureDate').value,allDay=!!$('#captureAllDay').checked,time=allDay?'':$('#captureTime').value;
  const endDate=$('#captureEndDate').value||date,endTime=allDay?'':$('#captureEndTime').value;
  const timeZone=$('#captureTimeZone').value.trim()||'Europe/Madrid';
  const dueAt=date?dateTimeToMsInZone(date,time,timeZone):0;
  const endAt=eventEndFromFields(date,time,endDate,endTime,allDay,timeZone),durationMinutes=durationMinutesBetween(dueAt,endAt);
  if(date&&!dueAt){toast('La fecha u hora no es válida en la zona horaria seleccionada.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}
  if(date&&endDate&&endDate<date){toast('La fecha de fin no puede ser anterior al inicio.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}
  if(dueAt&&endAt&&endAt<dueAt){toast('El final del evento no puede ser anterior al inicio.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}
  const alerts=normalizeAlertMinutes(state.captureAlerts);
  if(alerts.length&&!date){toast('Los avisos necesitan una fecha.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}
  const conflicts=(date&&time)?overlappingItems(dueAt,durationMinutes):[];
  if(($('#captureType').value||state.classification?.type)==='medication'){
    $('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';
    openMedicationComposer(text||$('#captureDescription').value.trim());
    return;
  }
  const payload={
    body:$('#captureDescription').value.trim(), originalText:text, title:$('#captureTitle').value.trim()||state.classification?.title||$('#captureDescription').value.trim().split('\n')[0]||'Cita',
    category:$('#captureCategory').value||'Otro', type:$('#captureType').value||'event', appointmentCapture:isAppointmentType($('#captureType').value||'event'),
    dueAt, endAt, hasTime:!!(date&&time&&!allDay), allDay, durationMinutes, locationText:$('#captureLocation').value.trim(), mapsUrl:$('#captureMapsUrl').value.trim(), timeZone, priority:Number($('#capturePriority').value||1), participantIds:normalizeParticipantIds(state.captureParticipantIds),
    recurrence:$('#captureRecurrence').value||'none', recurrenceRule:recurrenceRuleFor($('#captureRecurrence').value||'none'), recurrenceUntil:$('#captureRecurrence').value!=='none'?endOfDayMsInZone($('#captureRecurrenceUntil').value,timeZone):0,
    reminderLeadMinutes:alerts, voiceEnabled:!!$('#captureVoiceEnabled')?.checked,
    needsReview:false,
    source:state.inboxCaptureId?(state.inboxCaptureOriginal?.source||'android-share'):'manual',
    inboxCaptureId:state.inboxCaptureId||0,
    updateItemId:state.calendarUpdateItemId||0
  };
  if(payload.recurrence!=='none'){if(!date){toast('Indica la fecha de inicio de la serie.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}if(payload.recurrenceUntil&&payload.recurrenceUntil<dueAt){toast('La fecha final debe ser posterior al inicio.');$('#saveCaptureBtn').disabled=false;$('#saveCaptureBtn').textContent='Guardar en Nexo';return;}}
  if(state.attachment){ payload.attachmentPath=state.attachment.path||'';payload.attachmentName=state.attachment.name||'';payload.attachmentMime=state.attachment.mime||''; }
  const res=parseNative(NexoNative.saveCapture(JSON.stringify(payload)),{});
  $('#saveCaptureBtn').disabled=false; $('#saveCaptureBtn').textContent='Guardar en Nexo';
  if(res.ok){
    const avisos=alerts.length?` · ${alerts.length} aviso${alerts.length===1?'':'s'}`:'';
    const updated=!!state.calendarUpdateItemId;
    toast(updated?'Actualizado desde Calendar':(conflicts.length?`Guardado${avisos} · ya había ${conflicts.length} elemento${conflicts.length===1?'':'s'} a esa hora`:`Guardado en Nexo${avisos}`));
    clearCapture();navigateBack();
  }
  else toast(res.error||'No se pudo guardar.');
}

function runSearch(query=''){
  const q=(query||$('#searchInput').value).trim(),filter=$('#searchFilter').value||'all';state.searchFilter=filter;$('#searchInput').value=q;
  if(!q){$('#searchResults').innerHTML='';$('#searchStatus').textContent='Escribe una palabra para buscar en tus contenidos.';return;}
  buildFamilyStyleSearchIndex();
  let results=familyStyleSearch(q,!!state.searchHistorical);
  if(filter!=='all'){
    const accept=x=>filter==='lists'?x.kind==='list':
      filter==='appointments'?(x.kind==='item'&&isAppointmentType(x.raw?.type)):
      filter==='places'?(x.kind==='item'&&isPlaceType(x.raw?.type)):
      filter==='document'?(x.kind==='item'&&x.raw?.type==='document'):
      filter==='medications'?x.kind==='medication':
      filter==='recordings'?x.kind==='recording':
      filter==='journal'?x.kind==='journal':
      filter==='note'?(x.kind==='item'&&x.raw?.type==='note'):true;
    results=results.filter(accept);
  }
  state.homeSearchResults=results;
  $('#searchStatus').textContent=results.length?results.length+' resultado'+(results.length===1?'':'s'):'No hay resultados con esta búsqueda y este filtro.';
  $('#searchResults').innerHTML=results.length?results.map((x,i)=>familySearchCard(x,i,false)).join(''):'<div class="empty-state">Prueba otra palabra o selecciona Todo Nexo.</div>';
  $$('[data-family-search-home="0"]').forEach(b=>b.addEventListener('click',()=>openFamilySearchResult(state.homeSearchResults[Number(b.dataset.familySearchResult)])));
}

function listSearchCard(l){return `<div class="item-card"><div class="item-title">${esc(l.title)}</div><div class="item-meta"><span class="pill">Lista</span><span class="meta-tag">${esc(l.category||'General')}</span></div><div class="item-actions"><button class="mini-button" data-open-list="${l.id}">Abrir lista</button></div></div>`;}

function openCalendar(returnScreen='today',selectedDate=''){
  state.calendarReturn=returnScreen;
  if(selectedDate){const d=new Date(selectedDate+'T12:00:00');if(Number.isFinite(d.getTime())){state.calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);state.calendarSelected=selectedDate;}}
  navigate('calendar');
}

function isGoogleMapsLocation(value){
  const v=String(value||'').trim().toLowerCase();
  return /^https?:\/\/(?:www\.|maps\.)?google\.[^/]+\/maps(?:\/|$)/.test(v)
    || /^https?:\/\/maps\.app\.goo\.gl\//.test(v)
    || /^https?:\/\/goo\.gl\/maps\//.test(v)
    || /^(?:www\.|maps\.)google\.[^/]+\/maps(?:\/|$)/.test(v)
    || /^maps\.app\.goo\.gl\//.test(v)
    || /^goo\.gl\/maps\//.test(v);
}
function openMapsFromFields(locationSelector,mapsSelector){
  const maps=$(mapsSelector)?.value.trim()||'',place=$(locationSelector)?.value.trim()||'';
  // mapsUrl queda oculto únicamente por compatibilidad con eventos antiguos.
  // Mientras siga vigente conserva la precisión del enlace original.
  if(maps){NexoNative.openExternal(maps);return;}
  if(isGoogleMapsLocation(place)){NexoNative.openPlaceMap(place);return;}
  if(place){NexoNative.openLocationDirections(place);return;}
  toast('Escribe primero una dirección o ubicación.');
}
function bindEventLocationField(locationSelector,mapsSelector,mapButtonSelector,onUserChange=null){
  const location=$(locationSelector),maps=$(mapsSelector),button=$(mapButtonSelector);
  if(!location)return;
  location.addEventListener('input',()=>{
    // Si el usuario cambia lo que ve, un enlace heredado deja de representar
    // necesariamente esa ubicación y no debe imponerse al pulsar Maps.
    if(maps)maps.value='';
    if(typeof onUserChange==='function')onUserChange();
  });
  button?.addEventListener('click',()=>openMapsFromFields(locationSelector,mapsSelector));
}
function eventLocationValue(item){
  return String(item?.locationText||item?.mapsUrl||'');
}

function itemEffectiveEndAt(item){
  if(!item?.dueAt)return 0;
  const end=Number(item.endAt)||((Number(item.durationMinutes)||0)?Number(item.dueAt)+Number(item.durationMinutes)*60000:Number(item.dueAt));
  return Math.max(Number(item.dueAt),end);
}
function itemCoversIsoDate(item,iso){
  if(!item?.dueAt||!iso)return false;
  const start=isoDateLocal(new Date(item.dueAt)),end=isoDateLocal(new Date(itemEffectiveEndAt(item)));
  return iso>=start&&iso<=end;
}

function renderCalendar(direction=0){
  const m=state.calendarMonth,first=new Date(m.getFullYear(),m.getMonth(),1);
  const leading=(first.getDay()+6)%7,days=new Date(m.getFullYear(),m.getMonth()+1,0).getDate(),cells=Math.ceil((leading+days)/7)*7;
  const start=new Date(first.getFullYear(),first.getMonth(),1-leading),end=new Date(start.getFullYear(),start.getMonth(),start.getDate()+cells);
  const years=[...new Set(Array.from({length:cells},(_,c)=>new Date(start.getFullYear(),start.getMonth(),start.getDate()+c,12).getFullYear()))];requestHolidayYears(years);
  const holidaysByDay={};holidayEntriesForYears(years).forEach(h=>{if(/^\d{4}-\d{2}-\d{2}$/.test(String(h.date||'')))(holidaysByDay[h.date]||(holidaysByDay[h.date]=[])).push(h);});
  const items=parseNative(NexoNative.getCalendar(start.getTime(),end.getTime()-1),[]);state.calendarItems=items;
  const monthName=new Intl.DateTimeFormat('es-ES',{month:'long',year:'numeric'}).format(first);$('#calendarMonthTitle').textContent=monthName.replace(/^./,c=>c.toUpperCase());$('#calendarMonthPickerBtn').setAttribute('aria-label',monthName+'. Elegir mes y año');
  const today=isoDateLocal(new Date()),byDay={};items.forEach(i=>{if(!i.dueAt)return;const a=new Date(isoDateLocal(new Date(i.dueAt))+'T12:00:00'),z=new Date(isoDateLocal(new Date(itemEffectiveEndAt(i)))+'T12:00:00');for(let d=new Date(a),g=0;d<=z&&g<730;d.setDate(d.getDate()+1),g++){const k=isoDateLocal(d);(byDay[k]||(byDay[k]=[])).push(i);}});
  let html='';for(let cell=0;cell<cells;cell++){const d=new Date(start.getFullYear(),start.getMonth(),start.getDate()+cell,12),iso=isoDateLocal(d),list=byDay[iso]||[],hs=holidaysByDay[iso]||[],selected=iso===state.calendarSelected;const base=new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(d);const label=base+' · '+(list.length?list.length+' elementos':'sin elementos')+(hs.length?' · Festivo: '+hs.map(holidayName).join(', '):'');const dots=list.slice(0,3).map(e=>calendarDotClass(e)==='birthday'?'<span class="calendar-event-birthday" title="Cumpleaños" aria-hidden="true">🎂</span>':`<span class="calendar-dot ${calendarDotClass(e)}"></span>`).join('');html+=`<button type="button" class="calendar-day ${d.getMonth()!==m.getMonth()?'outside-month':''} ${iso===today?'today':''} ${selected?'selected':''} ${hs.length?'calendar-day--holiday':''}" data-calendar-day="${iso}" aria-label="${escAttr(label)}" aria-pressed="${selected}" ${iso===today?'aria-current="date"':''}><span class="day-heading"><span class="day-number">${d.getDate()}</span>${list.length>1?`<span class="day-total" aria-hidden="true">${list.length}</span>`:''}</span><span class="day-dots" aria-hidden="true">${dots}</span></button>`;}
  $('#calendarGrid').innerHTML=html;const partial=years.map(y=>state.holidayYears[y]).filter(Boolean).some(r=>r.status==='partial'||r.status==='unavailable');const note=$('#holidayCalendarNote');if(note){note.classList.toggle('hidden',!partial);note.textContent=partial?'Festivos parciales: se muestran los datos disponibles y Nexo volverá a actualizar cuando haya conexión.':'';}
  $$('[data-calendar-day]').forEach(b=>b.addEventListener('click',()=>{const d=new Date(b.dataset.calendarDay+'T12:00:00');state.calendarSelected=b.dataset.calendarDay;if(d.getMonth()!==state.calendarMonth.getMonth()||d.getFullYear()!==state.calendarMonth.getFullYear()){state.calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);renderCalendar();}else{renderCalendarDay();$$('[data-calendar-day]').forEach(x=>{const s=x.dataset.calendarDay===state.calendarSelected;x.classList.toggle('selected',s);x.setAttribute('aria-pressed',String(s));});}}));renderCalendarDay();
  const grid=$('#calendarGrid');if(direction&&typeof grid.animate==='function'&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches){grid.getAnimations?.().forEach(a=>a.cancel());grid.animate([{transform:`translateX(${direction*36}px)`,opacity:.45},{transform:'translateX(0)',opacity:1}],{duration:220,easing:'cubic-bezier(.2,0,0,1)'});}
}

function renderCalendarDay(){
  const iso=state.calendarSelected||isoDateLocal(new Date()),d=new Date(iso+'T12:00:00');const items=(state.calendarItems||[]).filter(i=>itemCoversIsoDate(i,iso)).sort((a,b)=>{if((a.allDay===true||a.hasTime===false)&&(b.allDay!==true&&b.hasTime!==false))return -1;if((a.allDay!==true&&a.hasTime!==false)&&(b.allDay===true||b.hasTime===false))return 1;return (a.dueAt||0)-(b.dueAt||0);});const hs=holidayEntriesForYears([Number(iso.slice(0,4))]).filter(h=>h.date===iso);const label=new Intl.DateTimeFormat('es-ES',{weekday:'short',day:'numeric',month:'short'}).format(d);$('#calendarSelectedTitle').textContent=(iso===isoDateLocal(new Date())?'Hoy · ':'')+label.replace(/^./,c=>c.toUpperCase());const ap=items.length?`${items.length} cita${items.length===1?'':'s'}`:'Sin citas';$('#calendarSelectedMeta').textContent=hs.length?`${ap} · ${hs.length===1?'Festivo':'Festivos'}`:ap;const host=$('#calendarDayHolidays');if(host){host.innerHTML=hs.map(holidayCard).join('');host.classList.toggle('hidden',!hs.length);bindHolidaySources(host);}$('#calendarDayItems').innerHTML=items.length?items.map(i=>agendaCard(i)).join(''):'<div class="empty-state">Sin citas para este día.</div>';const add=$('#calendarAddFab');if(add)add.setAttribute('aria-label','Añadir una cita el '+label);bindItemButtons($('#calendarDayItems'));
}

function prepareCaptureForDate(iso){openCreationMenu(iso);}

function toggleSettingsAccordion(section){
  $$('[data-settings-accordion]').forEach(card=>{
    const open=card.dataset.settingsAccordion===section&&!card.classList.contains('settings-accordion--open');
    card.classList.toggle('settings-accordion--open',open);
    const summary=card.querySelector('[data-settings-section]');
    const body=card.querySelector('.settings-accordion__body');
    summary?.setAttribute('aria-expanded',String(open));
    body?.classList.toggle('hidden',!open);
  });
}

function personInitials(name){
  return String(name||'').trim().split(/\s+/).filter(Boolean).slice(0,2).map(part=>part.charAt(0).toUpperCase()).join('')||'?';
}
function personTextColor(color){
  return ['#FFFFFF','#FACC15'].includes(String(color||'').toUpperCase())?'#182033':'#FFFFFF';
}
function normalizeParticipantIds(ids){
  const out=[],seen=new Set();
  (Array.isArray(ids)?ids:[]).forEach(value=>{const id=Number(value)||0;if(id>0&&!seen.has(id)){seen.add(id);out.push(id);}});
  return out;
}
function eventPeopleAssignmentHtml(hostId){
  return `<fieldset class="member-assignment event-people-assignment"><legend>Personas incluidas</legend><div id="${hostId}" class="member-chips"></div><p class="form-helper">Selecciona una, varias o todas las personas que también están involucradas en el evento.</p></fieldset>`;
}
function renderPersonAssignment(hostId,selectedIds,onChange){
  const host=$('#'+hostId);if(!host)return;
  const people=parseNative(NexoNative.getPeople(),[]);
  let selected=normalizeParticipantIds(selectedIds);
  const activeIds=people.map(p=>Number(p.id)||0).filter(Boolean);
  const allAssigned=activeIds.length>0&&activeIds.every(id=>selected.includes(id));
  if(!people.length){
    host.innerHTML='<span class="member-assignment-empty">No hay personas creadas. Añádelas desde Ajustes → Personas.</span>';
    return;
  }
  host.innerHTML=`<button type="button" class="${allAssigned?'member-chip member-chip--active':'member-chip'}" data-people-all>Todos</button>`+
    people.map(person=>`<button type="button" class="${selected.includes(Number(person.id))?'member-chip member-chip--active':'member-chip'}" data-person-assignment="${Number(person.id)}"><span class="member-color-dot" style="background:${escAttr(person.color||'#3158D4')}"></span>${esc(person.name||'Persona')}</button>`).join('');
  host.querySelector('[data-people-all]')?.addEventListener('click',()=>{
    const activeSet=new Set(activeIds),currentlyAll=activeIds.length>0&&activeIds.every(id=>selected.includes(id));
    selected=currentlyAll?selected.filter(id=>!activeSet.has(id)):normalizeParticipantIds([...selected,...activeIds]);
    onChange([...selected]);renderPersonAssignment(hostId,selected,onChange);
  });
  host.querySelectorAll('[data-person-assignment]').forEach(btn=>btn.addEventListener('click',()=>{
    const id=Number(btn.dataset.personAssignment)||0;
    selected=selected.includes(id)?selected.filter(x=>x!==id):normalizeParticipantIds([...selected,id]);
    onChange([...selected]);renderPersonAssignment(hostId,selected,onChange);
  }));
}
function renderCapturePeopleAssignment(){
  renderPersonAssignment('capturePeopleChips',state.captureParticipantIds,ids=>{state.captureParticipantIds=ids;markCaptureManual('participants');});
}
function renderPeopleSettings(){
  const host=$('#peopleSettingsList');if(!host)return;
  const people=parseNative(NexoNative.getPeople(),[]);
  host.innerHTML=people.length?people.map(person=>{
    const id=Number(person.id)||0,color=String(person.color||'#3158D4').toUpperCase(),name=String(person.name||'Persona');
    return `<button type="button" class="nexo-person-row" data-person-id="${id}">
      <span class="nexo-person-avatar" style="background:${escAttr(color)};color:${personTextColor(color)}">${esc(personInitials(name))}</span>
      <span class="nexo-person-identity"><strong>${esc(name)}</strong><small>${esc(color)}</small></span>
      <svg class="ui-icon nexo-person-chevron" aria-hidden="true"><use href="#nx-icon-right"></use></svg>
    </button>`;
  }).join(''):'<div class="people-empty"><strong>Aún no has añadido personas.</strong><span>Créala aquí y después podrás asociarla a tus eventos.</span></div>';
  host.querySelectorAll('[data-person-id]').forEach(row=>row.addEventListener('click',()=>openPersonEditor(Number(row.dataset.personId))));
}
function openPersonEditor(id=0){
  const person=id?parseNative(NexoNative.getPerson(id),{}):{};
  const editing=Number(person.id)>0;
  const initialColor=String(person.color||'#3158D4').toUpperCase();
  const palette=PERSON_COLORS.map(option=>{
    const light=['#FFFFFF','#FACC15'].includes(option.value),selected=option.value===initialColor;
    return `<button type="button" class="person-color-swatch ${light?'person-color-swatch--light':''} ${selected?'person-color-swatch--selected':''}" data-person-color="${option.value}" style="background:${option.value}" aria-label="Usar color ${escAttr(option.label)}" title="${escAttr(option.label)}">${selected?uiIcon('check'):''}</button>`;
  }).join('');
  openModal(editing?'Editar persona':'Nueva persona',`<div class="person-editor">
    <div class="person-editor-preview">
      <span id="personPreviewAvatar" class="nexo-person-avatar nexo-person-avatar--large" style="background:${escAttr(initialColor)};color:${personTextColor(initialColor)}">${esc(personInitials(person.name||'P'))}</span>
      <div><strong id="personPreviewName">${esc(person.name||'Nueva persona')}</strong><span>Se mostrará así en tus eventos.</span></div>
    </div>
    <label>Nombre<input id="personName" type="text" maxlength="100" value="${escAttr(person.name||'')}" placeholder="Ej.: Marta"></label>
    <div class="person-color-block"><strong>Color de la persona</strong><p class="small muted">La misma paleta y lógica visual que Nexo Familia.</p><div id="personColorPalette" class="person-color-palette" aria-label="Seleccionar color">${palette}</div></div>
  </div>
  <div class="modal-actions wrap-actions"><button id="personSave" class="primary-button">${uiIcon('check')} Guardar</button>${editing?'<button id="personDelete" class="danger-button">'+uiIcon('trash')+' Eliminar</button>':''}</div>`);
  let selectedColor=initialColor;
  const updatePreview=()=>{
    const name=$('#personName').value.trim()||'Nueva persona';
    const avatar=$('#personPreviewAvatar');
    avatar.textContent=personInitials(name);avatar.style.background=selectedColor;avatar.style.color=personTextColor(selectedColor);
    $('#personPreviewName').textContent=name;
    document.querySelectorAll('#personColorPalette [data-person-color]').forEach(btn=>{
      const active=btn.dataset.personColor===selectedColor;
      btn.classList.toggle('person-color-swatch--selected',active);
      btn.innerHTML=active?uiIcon('check'):'';
    });
  };
  $('#personName').addEventListener('input',updatePreview);
  document.querySelectorAll('#personColorPalette [data-person-color]').forEach(btn=>btn.addEventListener('click',()=>{selectedColor=btn.dataset.personColor;updatePreview();}));
  $('#personSave').addEventListener('click',()=>{
    const name=$('#personName').value.trim();
    if(!name){toast('Escribe el nombre de la persona.');$('#personName').focus();return;}
    const result=parseNative(NexoNative.savePerson(JSON.stringify({id:editing?Number(person.id):0,name,color:selectedColor})),{});
    if(!result.ok){toast(result.error||'No se pudo guardar la persona.');return;}
    closeModal();renderPeopleSettings();toast(editing?'Persona actualizada':'Persona añadida');
  });
  $('#personDelete')?.addEventListener('click',()=>confirmGeneric('Eliminar persona',`Se quitará “${person.name||'esta persona'}” de la lista. Los eventos históricos conservarán su referencia interna.`,()=>{
    const ok=!!NexoNative.archivePerson(Number(person.id));if(!ok){toast('No se pudo eliminar la persona.');return;}
    closeModal();renderPeopleSettings();toast('Persona eliminada');
  }));
  setTimeout(()=>$('#personName')?.focus(),70);
}

function renderMore(){
  $('#startScreen').value=String(NexoNative.getStartScreen()||'today');
  const lists=parseNative(NexoNative.getLists(),[]),preferred=Number(NexoNative.getPreferredShoppingList())||0;
  $('#preferredShoppingList').innerHTML='<option value="0">Elegir al pulsar Mi compra</option>'+lists.map(l=>`<option value="${Number(l.id)}">${esc(l.title)}</option>`).join('');
  $('#preferredShoppingList').value=lists.some(l=>Number(l.id)===preferred)?String(preferred):'0';
  $$('[data-display-mode]').forEach(b=>{const selected=b.dataset.displayMode===state.displayMode;b.classList.toggle('active',selected);b.setAttribute('aria-pressed',String(selected));});
  renderPeopleSettings();
  renderCommunicationCaptureSettings();
  renderMedicationMessageSettings();
  const backup=parseNative(NexoNative.getBackupStatus(),{}),last=Number(backup.lastExportAt)||0,total=Object.values(backup.stats||{}).reduce((a,n)=>a+(Number(n)||0),0),hint=$('#backupHint');
  const due=total>0&&(!last||Date.now()-last>30*86400000);hint.classList.toggle('hidden',!due);
  if(due)hint.innerHTML='<strong>Copia de seguridad pendiente</strong><span>Exporta tus datos y recuerda guardar los audios por separado.</span>';
}

function renderCommunicationCaptureSettings(){
  const settings=parseNative(NexoNative.getCommunicationCaptureSettings(),{
    enabled:true,autoScanEnabled:false,apps:[],calendarEnabled:false,calendarPermission:false,calendarBaselined:false,
    smsDirectEnabled:false,smsPermission:false,smsBaselined:false,smsLastSync:0,smsError:'',smsSeen:0,smsCaptured:0,smsRejected:0,smsErrors:0,
    gmailDirectEnabled:false,gmailAuthorized:false,gmailEmail:'',gmailBaselined:false,gmailLastSync:0,gmailError:'',gmailErrorDetail:'',gmailSeen:0,gmailCaptured:0,gmailRejected:0,gmailErrors:0
  });
  const auto=$('#inputHubAutoScan'),autoStatus=$('#inputHubAutoScanStatus');
  if(auto)auto.checked=settings.autoScanEnabled!==false;
  if(autoStatus)autoStatus.textContent=settings.autoScanEnabled===false
    ? 'Modo manual · sin jobs periódicos ni escaneo directo al volver a Nexo.'
    : 'Modo automático · Nexo vigila las fuentes directas en segundo plano.';
  const access=!!NexoNative.hasNotificationAccess();
  const status=$('#notificationAccessStatus'),master=$('#communicationCaptureEnabled'),apps=$('#communicationApps');
  if(status){
    status.className='notification-access-status '+(access?'enabled':'disabled');
    status.innerHTML=access?'<strong>Acceso concedido</strong><span>Nexo puede recibir las notificaciones de las apps activadas.</span>':'<strong>Acceso pendiente</strong><span>Activa “Nexo Inbox” en el acceso a notificaciones de Android.</span>';
  }
  if(master){master.checked=settings.enabled!==false;master.disabled=false;}
  if(apps){
    const notificationOnly=(settings.apps||[]).filter(a=>a.key==='whatsapp'||a.key==='telegram');
    apps.innerHTML=notificationOnly.map(a=>`<label class="toggle-row"><span><strong>${esc(a.label)}</strong><small>${a.key==='whatsapp'?'Mensajes de WhatsApp':'Mensajes de Telegram'}</small></span><input type="checkbox" data-communication-app="${escAttr(a.key)}" ${a.enabled!==false?'checked':''} ${settings.enabled===false?'disabled':''}></label>`).join('');
    apps.querySelectorAll('[data-communication-app]').forEach(el=>el.addEventListener('change',()=>{NexoNative.setCommunicationAppEnabled(el.dataset.communicationApp,el.checked);toast(`${el.checked?'Activada':'Desactivada'} captura de ${el.closest('label')?.querySelector('strong')?.textContent||'la aplicación'}`);}));
  }
  const btn=$('#notificationAccessBtn');if(btn)btn.textContent=access?'Revisar acceso a notificaciones':'Activar acceso a notificaciones';

  const sms=$('#smsDirectEnabled'),smsStatus=$('#smsDirectStatus');
  if(sms)sms.checked=!!settings.smsDirectEnabled;
  if(smsStatus){
    if(settings.smsDirectEnabled&&settings.smsPermission&&settings.smsBaselined){
      const last=settings.smsLastSync?(' · última consulta '+fmtDate(Number(settings.smsLastSync),true)):'';
      smsStatus.textContent=(settings.autoScanEnabled===false?'Preparado para escaneo manual':'Activo · lectura directa automática')+last+' · '+Number(settings.smsSeen||0)+' vistos · '+Number(settings.smsCaptured||0)+' candidatos · '+Number(settings.smsRejected||0)+' descartados'+(Number(settings.smsErrors||0)?' · '+Number(settings.smsErrors||0)+' con error':'');
    }else if(settings.smsError)smsStatus.textContent=String(settings.smsError);
    else if(settings.smsDirectEnabled&&settings.smsPermission)smsStatus.textContent=settings.autoScanEnabled===false?'Preparado · pulsa Escanear ahora para crear la referencia inicial.':'Preparando referencia inicial de SMS…';
    else if(settings.smsDirectEnabled&&!settings.smsPermission)smsStatus.textContent='Falta conceder permiso para leer SMS.';
    else smsStatus.textContent='Desactivado.';
  }

  const gmailStatus=$('#gmailDirectStatus'),gmailBtn=$('#gmailConnectBtn'),gmailScan=$('#gmailScanNowBtn'),gmailDisconnect=$('#gmailDisconnectBtn'),gmailBadge=$('#gmailConnectionBadge'),gmailAccount=$('#gmailAccount'),gmailDiagnostic=$('#gmailDiagnostic'),gmailDiagnosticText=$('#gmailDiagnosticText');
  const gmailConnected=!!settings.gmailAuthorized;
  const gmailAccountText=settings.gmailEmail?String(settings.gmailEmail):'';
  if(gmailBadge){
    gmailBadge.textContent=gmailConnected?'Conectado':'No conectado';
    gmailBadge.classList.toggle('connected',gmailConnected);
    gmailBadge.classList.toggle('error',!gmailConnected&&!!settings.gmailError);
  }
  if(gmailAccount){
    gmailAccount.textContent=gmailAccountText;
    gmailAccount.classList.toggle('hidden',!gmailConnected||!gmailAccountText);
  }
  if(gmailStatus){
    if(gmailConnected&&settings.gmailBaselined){
      const last=settings.gmailLastSync?'Último escaneo: '+fmtDate(Number(settings.gmailLastSync),true):'Preparado para escanear';
      gmailStatus.textContent=`${last} · ${Number(settings.gmailCaptured||0)} candidatos · ${Number(settings.gmailRejected||0)} descartados${settings.autoScanEnabled===false?' · manual':' · automático activado'}`;
    }else if(gmailConnected){
      gmailStatus.textContent=settings.autoScanEnabled===false?'Cuenta conectada · pulsa “Escanear Gmail” para empezar.':'Cuenta conectada · preparando Gmail…';
    }else if(settings.gmailError){
      gmailStatus.textContent=String(settings.gmailError);
    }else{
      gmailStatus.textContent='Conecta una cuenta de Google una sola vez. Después podrás escanear Gmail desde aquí.';
    }
  }
  if(gmailBtn){
    gmailBtn.textContent=gmailConnected?'Cambiar cuenta':'Conectar Gmail';
    gmailBtn.classList.toggle('secondary-button',gmailConnected);
    gmailBtn.classList.toggle('primary-button',!gmailConnected);
  }
  gmailScan?.classList.toggle('hidden',!gmailConnected);
  gmailDisconnect?.classList.toggle('hidden',!gmailConnected);
  if(gmailDiagnostic){
    const detail=String(settings.gmailErrorDetail||'').trim();
    gmailDiagnostic.classList.toggle('hidden',!settings.gmailError&&!detail);
    if(gmailDiagnosticText)gmailDiagnosticText.textContent=detail||String(settings.gmailError||'');
  }

  const calendar=$('#calendarCaptureEnabled'),calendarStatus=$('#calendarAccessStatus');
  if(calendar)calendar.checked=!!settings.calendarEnabled;
  if(calendarStatus){
    if(settings.calendarEnabled&&settings.calendarPermission&&settings.calendarBaselined)calendarStatus.textContent=settings.autoScanEnabled===false?'Preparado para escaneo manual.':'Activo · observando nuevos eventos y cambios de Calendar.';
    else if(settings.calendarEnabled&&settings.calendarPermission)calendarStatus.textContent='Preparando la referencia inicial del calendario…';
    else if(settings.calendarEnabled&&!settings.calendarPermission)calendarStatus.textContent='Falta conceder permiso de calendario.';
    else calendarStatus.textContent='Desactivado.';
  }
}

function medicationOffsetLabel(offset){return offset<0?`${Math.abs(offset)} min antes`:offset===0?'Hora exacta':`+${offset} min`;}
function renderMedicationMessageSettings(){
  const host=$('#medicationMessageFields');if(!host)return;
  const rows=parseNative(NexoNative.getMedicationMessages(),[]);
  host.innerHTML=rows.map(r=>`<label>${medicationOffsetLabel(Number(r.offset))}<textarea class="medication-message-input" data-med-offset="${Number(r.offset)}" rows="2" maxlength="220">${esc(r.text||'')}</textarea></label>`).join('');
  host.querySelectorAll('[data-med-offset]').forEach(el=>el.addEventListener('change',()=>{NexoNative.setMedicationMessage(Number(el.dataset.medOffset),el.value);renderMedicationMessageSettings();toast('Frase de medicación actualizada');}));
}

function openPanel(panel,options={}){
  if(panel==='tasks'||panel==='reminders')panel='appointments';
  if(panel==='appointments'&&!options.keepWindow)state.appointmentsWindow='';
  stopRecordingPolling();
  if(panel==='calendar'){navigate('calendar',options);return;}
  if(panel==='journal'||panel==='quotes')state.journalFilter='all';
  navigate('panel',{...options,panel,list:null});
  const cfg={
    appointments:['Agenda','Citas, tareas y compromisos · con fecha o sin ella'],lists:['Listas','Listas rápidas y reutilizables'],
    inbox:['Inbox','Contenido recibido que todavía no has organizado'],history:['Historial','Actividad reciente'],
    backup:['Copia de seguridad','Datos y adjuntos · audios por separado'],medications:['Medicamentos','Tratamientos, próximas tomas e historial'],
    journal:['Cuaderno','Pensamientos y reflexiones personales · fuera del calendario'],
    quotes:['Frases','Citas textuales, autores y referencias · fuera del calendario'],
    notes:['Notas','Anotaciones locales · más recientes primero'],recordings:['Grabaciones','Audio local · reuniones y conversaciones autorizadas']
  }[panel]||['Más',''];
  $('#panelTitle').textContent=cfg[0];$('#panelSubtitle').textContent=cfg[1];$('#panelAction').innerHTML='';
  if(panel==='appointments')renderAppointmentsPanel();
  else if(panel==='lists') renderListsPanel();
  else if(panel==='inbox'){renderInboxPanel();NexoNative.scanInputHubNow();}
  else if(panel==='history') renderHistoryPanel();
  else if(panel==='backup') renderBackupPanel();
  else if(panel==='medications') renderMedicationsPanel();
  else if(panel==='journal'||panel==='quotes') renderJournalPanel();
  else if(panel==='notes') renderNotesPanel();
  else if(panel==='recordings') renderRecordingsPanel();
  else if(panel==='places') renderPlacesPanel();
  else if(panel==='documents') renderCollectionPanel(panel);
  else if(panel==='parking') renderParkingPanel();
}
function renderItemsPanel(items,empty){ $('#panelContent').innerHTML=items.length?`<div class="stack">${items.map(itemCard).join('')}</div>`:`<div class="empty-state">${esc(empty)}</div>`;bindItemButtons($('#panelContent')); }

function inboxSourceLabel(capture){
  if(capture.source==='calendar'){
    const change=capture.classification?.calendarChangeType==='updated'?'cambio':'nuevo';
    return `Calendar · ${change}`;
  }
  if(capture.source==='sms-provider'){
    const sender=String(capture.sender||capture.subject||'').trim();
    return sender?`SMS · ${sender}`:'SMS';
  }
  if(capture.source==='gmail-api'){
    const sender=String(capture.sender||capture.subject||'').trim();
    return sender?`Gmail · ${sender}`:'Gmail';
  }
  const pkg=String(capture.sourcePackage||'');
  const app=pkg==='com.whatsapp'||pkg==='com.whatsapp.w4b'?'WhatsApp':pkg==='com.google.android.gm'?'Gmail':pkg.startsWith('org.telegram.messenger')?'Telegram':pkg.includes('messaging')||pkg==='com.android.mms'?'SMS':capture.source==='notification'?'Comunicación':'Compartido';
  const sender=String(capture.sender||capture.subject||'').trim();
  return sender?`${app} · ${sender}`:app;
}

function inboxCaptureCard(capture){
  const raw=String(capture.rawText||capture.subject||capture.attachmentName||'Contenido recibido').trim();
  const title=String(capture.detectedTitle||capture.classification?.title||capture.subject||capture.attachmentName||raw.split(/\n/)[0]||'Contenido recibido').trim();
  const type=String(capture.detectedType||capture.classification?.type||'').trim();
  const preview=raw.length>220?`${raw.slice(0,217)}…`:raw;
  const attachment=capture.attachmentName?`<div class="item-location">${uiIcon('file')} ${esc(capture.attachmentName)}</div>`:'';
  const source=inboxSourceLabel(capture);
  return `<div class="item-card inbox-capture-card">
    <div class="item-top"><div class="item-title">${esc(title)}</div></div>
    <div class="item-meta"><span class="pill">${esc(source)}</span>${type?`<span class="meta-tag">${esc(typeLabel(type))}</span>`:''}<span class="meta-tag">${esc(fmtDate(capture.createdAt,true))}</span></div>
    ${preview?`<div class="item-body">${esc(preview)}</div>`:''}
    ${attachment}
    <div class="item-actions"><button class="mini-button primary" data-review-capture="${Number(capture.id)}">Revisar</button><button class="mini-button" data-discard-capture="${Number(capture.id)}">Descartar</button></div>
  </div>`;
}

function renderInboxPanel(){
  const captures=parseNative(NexoNative.getInboxCaptures(),[]);
  const legacy=parseNative(NexoNative.getInbox(),[]);
  const sourceState=parseNative(NexoNative.getCommunicationCaptureSettings(),{});
  const smsReady=!!sourceState.smsDirectEnabled&&!!sourceState.smsPermission&&!!sourceState.smsBaselined;
  const gmailReady=!!sourceState.gmailDirectEnabled&&!!sourceState.gmailAuthorized&&!!sourceState.gmailBaselined;
  let html=`<div class="inbox-source-health">
    <div><strong>Fuentes directas</strong><small><span class="${smsReady?'source-ok':'source-off'}">SMS ${smsReady?'activo':'no activo'}</span> · <span class="${gmailReady?'source-ok':'source-off'}">Gmail ${gmailReady?'activo':'no activo'}</span></small></div>
    <button id="inboxScanNowBtn" class="mini-button" type="button">Buscar ahora</button>
  </div>`;
  if(captures.length)html+=`<div class="search-section-label">Capturas recibidas</div><div class="stack">${captures.map(inboxCaptureCard).join('')}</div>`;
  if(legacy.length)html+=`<div class="search-section-label">Elementos pendientes de revisión</div><div class="stack">${legacy.map(itemCard).join('')}</div>`;
  if(!captures.length&&!legacy.length)html+='<div class="empty-state">No hay nada pendiente de organizar.</div>';
  $('#panelContent').innerHTML=html;
  const root=$('#panelContent');
  $('#inboxScanNowBtn')?.addEventListener('click',()=>{NexoNative.scanInputHubNow();toast('Buscando SMS, Gmail y Calendar…');setTimeout(renderInboxPanel,1200);});
  bindItemButtons(root);
  root.querySelectorAll('[data-review-capture]').forEach(b=>b.addEventListener('click',()=>reviewInboxCapture(Number(b.dataset.reviewCapture))));
  root.querySelectorAll('[data-discard-capture]').forEach(b=>b.addEventListener('click',()=>{
    const id=Number(b.dataset.discardCapture);
    confirmGeneric('Descartar captura','La captura dejará de aparecer en Inbox. El contenido definitivo de Nexo no se verá afectado.',()=>{
      if(NexoNative.discardInboxCapture(id)){toast('Captura descartada');renderInboxPanel();renderToday();}
      else toast('No se pudo descartar la captura.');
    });
  }));
}

function openInboxCaptureEditor(capture,incoming=false){
  if(!capture)return;
  navigate('save');
  clearCapture();
  const id=Number(capture.id||capture.captureId||0);
  const raw=String(capture.rawText||capture.text||capture.subject||capture.attachmentName||'').trim();
  let detected=capture.classification&&typeof capture.classification==='object'?capture.classification:{};
  if(!Object.keys(detected).length&&raw)detected=parseNative(NexoNative.classify(raw),{});
  const allowed=['event','task','reminder','expiry','note','document','place','restaurant'];
  const suggested=allowed.includes(detected.type)?detected.type:(capture.attachmentPath?'document':'note');
  state.inboxCaptureId=id;
  state.inboxCaptureOriginal=capture;
  state.calendarUpdateItemId=Number(detected.existingItemId||0);
  if(state.calendarUpdateItemId){
    const existing=parseNative(NexoNative.getItem(state.calendarUpdateItemId),{});
    state.captureParticipantIds=normalizeParticipantIds(existing.participantIds||[]);
    renderCapturePeopleAssignment();
  }
  state.capturePinnedType=suggested;
  state.capturePinnedDate='';
  state.attachment=capture.attachmentPath?{path:capture.attachmentPath,name:capture.attachmentName||'archivo',mime:capture.attachmentMime||capture.mime||''}:null;
  $('#captureHeading').textContent=state.calendarUpdateItemId?'Actualizar desde Calendar':(capture.source==='calendar'?'Revisar evento de Calendar':'Revisar captura');
  $('#captureText').value=raw;
  if(state.attachment){$('#attachmentChip').textContent=`Adjunto: ${state.attachment.name}`;$('#attachmentChip').classList.remove('hidden');}
  if(Object.keys(detected).length){
    detected={...detected,type:suggested};
    renderClassification(detected,true);
    if(id)NexoNative.updateInboxCaptureClassification(id,JSON.stringify(detected));
  }else{
    $('#captureType').value=suggested;
    $('#captureTitle').value=capture.attachmentName||capture.subject||'Contenido recibido';
    setInterpretationStatus('idle','Contenido conservado · revisa antes de guardar');
  }
  if(incoming)toast(capture.source==='calendar'?'Evento de Calendar guardado en Inbox para revisión.':'Contenido guardado en Inbox. Revisa lo que Nexo ha entendido.');
}

function reviewInboxCapture(id){
  const capture=parseNative(NexoNative.getInboxCapture(id),{});
  if(!capture.id){toast('La captura ya no está disponible.');renderInboxPanel();return;}
  openInboxCaptureEditor(capture,false);
}

function backupStatLine(stats){
  const parts=[];if(Number(stats.items||0))parts.push(`${stats.items} elementos`);if(Number(stats.journal||0))parts.push(`${stats.journal} en Cuaderno`);if(Number(stats.lists||0))parts.push(`${stats.lists} listas`);if(Number(stats.attachments||0))parts.push(`${stats.attachments} adjuntos`);return parts.length?parts.join(' · '):'Sin datos guardados todavía';
}
function renderBackupPanel(){
  const b=parseNative(NexoNative.getBackupStatus(),{}),stats=b.stats||{},last=Number(b.lastExportAt||0),restored=Number(b.lastRestoreAt||0);
  const family=parseNative(NexoNative.getNexoFamiliaCloudStatus(),{}),connected=!!family.connected,
    baseline=!!family.baselineCompleted,lastSync=Number(family.lastSyncAt||0),
    imported=Number(family.importedEvents||0),known=Number(family.knownEventIds||0),
    localAppointments=Number(family.localAppointments||0);
  $('#panelAction').innerHTML='';

  const familyCard=connected
    ? `<div class="backup-card family-sync-card">
        <div class="backup-icon"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-calendar"></use></svg></div>
        <div class="backup-copy family-sync-copy">
          <div class="family-sync-heading"><strong>Nexo Familia</strong><span class="family-sync-badge family-sync-badge--ok">Conectado</span></div>
          <p class="family-sync-account">${esc(family.email||'Usuario vinculado')}</p>
          <p>${baseline
            ? 'Importa únicamente los eventos cuyo UUID todavía no existe en este teléfono. Los eventos ya presentes en Nexo no se modifican.'
            : 'Primera importación: Nexo descargará y validará todos los eventos antes de sustituir únicamente la agenda local. Notas, listas, medicación, documentos y grabaciones no se modifican.'}</p>
          <div class="family-sync-stats">
            <span>${imported} importado${imported===1?'':'s'}</span>
            <span>${known} UUID conocido${known===1?'':'s'}</span>
            ${lastSync?`<span>Última importación: ${esc(fmtDate(lastSync,true))}</span>`:''}
          </div>
          ${!baseline&&localAppointments?'<div class="family-sync-warning">La primera importación reemplazará '+localAppointments+' cita'+(localAppointments===1?'':'s')+' / evento'+(localAppointments===1?'':'s')+' de la agenda local.</div>':''}
        </div>
        <div class="stack-actions family-sync-actions">
          <button id="nexoFamiliaSyncBtn" class="primary-button">${baseline?'Importar eventos nuevos':'Importar todos los eventos'}</button>
          <button id="nexoFamiliaDisconnectBtn" class="secondary-button">Desvincular</button>
        </div>
      </div>`
    : `<div class="backup-card family-sync-card family-sync-card--login">
        <div class="backup-icon"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-calendar"></use></svg></div>
        <div class="backup-copy family-sync-copy">
          <div class="family-sync-heading"><strong>Conectar con Nexo Familia</strong><span class="family-sync-badge">Sin vincular</span></div>
          <p>Usa el correo y la contraseña con los que entras en Nexo Familia. No son las credenciales de Gmail ni las del propietario de Supabase.</p>
          <div class="family-sync-form">
            <label>Correo de Nexo Familia<input id="nexoFamiliaEmail" type="email" autocomplete="email" inputmode="email" placeholder="tu@correo.com"></label>
            <label>Contraseña de Nexo Familia<input id="nexoFamiliaPassword" type="password" autocomplete="current-password" placeholder="Contraseña"></label>
          </div>
          <div class="small muted">La contraseña solo se usa para iniciar sesión y no se guarda. La sesión queda cifrada con Android Keystore.</div>
        </div>
        <div class="stack-actions family-sync-actions"><button id="nexoFamiliaLinkBtn" class="primary-button">Vincular Nexo Familia</button></div>
      </div>`;

  $('#panelContent').innerHTML=`<div class="backup-stack">
    <div class="backup-card"><div class="backup-icon"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg></div><div class="backup-copy"><strong>Exportar copia</strong><p>Guarda la base de datos, listas, avisos y adjuntos en un archivo <b>.nexo</b>. <strong>Las grabaciones no se incluyen.</strong> Expórtalas por separado desde Grabaciones.</p><div class="small muted">${last?`Última copia: ${esc(fmtDate(last,true))}`:'Aún no has creado ninguna copia.'}</div><div class="small muted">${esc(backupStatLine(stats))}</div></div><button id="backupExportBtn" class="primary-button">Exportar copia</button></div>
    <div class="backup-card"><div class="backup-icon"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-download"></use></svg></div><div class="backup-copy"><strong>Restaurar copia</strong><p>Selecciona un archivo <b>.nexo</b> desde el teléfono, la nube, USB u otra ubicación disponible.</p><div class="small muted">${restored?`Última restauración: ${esc(fmtDate(restored,true))}`:'La restauración sustituye el contenido actual por el de la copia.'}</div></div><button id="backupRestoreBtn" class="secondary-button">Restaurar copia</button></div>
    ${familyCard}
    <div class="backup-security"><strong>Seguridad de la vinculación</strong><p>Nexo usa Supabase Auth como un usuario normal de Nexo Familia. Supabase aplica los mismos permisos y reglas RLS que en la aplicación familiar.</p></div>
  </div>`;

  $('#backupExportBtn').addEventListener('click',()=>{toast('Elige dónde guardar la copia.');NexoNative.exportBackup();});
  $('#backupRestoreBtn').addEventListener('click',()=>{toast('Selecciona una copia .nexo para revisarla antes de restaurar.');NexoNative.chooseBackupRestore();});

  if(connected){
    $('#nexoFamiliaSyncBtn').addEventListener('click',()=>{
      const button=$('#nexoFamiliaSyncBtn');button.disabled=true;button.textContent=baseline?'Buscando eventos nuevos…':'Importando desde Nexo Familia…';
      NexoNative.syncNexoFamiliaEvents();
    });
    $('#nexoFamiliaDisconnectBtn').addEventListener('click',()=>confirmGeneric(
      'Desvincular Nexo Familia',
      'Se cerrará la sesión guardada en este teléfono. Los eventos ya importados permanecerán en Nexo.',
      ()=>NexoNative.disconnectNexoFamilia(),
      'Desvincular'
    ));
  }else{
    const link=()=>{
      const email=$('#nexoFamiliaEmail').value.trim(),password=$('#nexoFamiliaPassword').value;
      if(!email){toast('Escribe el correo de Nexo Familia.');$('#nexoFamiliaEmail').focus();return;}
      if(!password){toast('Escribe la contraseña de Nexo Familia.');$('#nexoFamiliaPassword').focus();return;}
      const button=$('#nexoFamiliaLinkBtn');button.disabled=true;button.textContent='Conectando…';
      NexoNative.linkNexoFamilia(email,password);
    };
    $('#nexoFamiliaLinkBtn').addEventListener('click',link);
    $('#nexoFamiliaPassword').addEventListener('keydown',e=>{if(e.key==='Enter')link();});
  }
}

function renderAppointmentsPanel(){
  const today=new Date();today.setHours(0,0,0,0);
  if(state.appointmentsWindow==='week'){
    const end=new Date(today.getFullYear(),today.getMonth(),today.getDate()+7).getTime()-1;
    const rows=parseNative(NexoNative.getCalendar(today.getTime(),end),[]).filter(i=>isAppointmentType(i.type)&&i.status!=='done'&&i.status!=='cancelled'&&i.occurrenceStatus!=='deleted').sort((a,b)=>(a.dueAt||0)-(b.dueAt||0));
    $('#panelTitle').textContent='Próximos 7 días';$('#panelSubtitle').textContent='Hoy y los 6 días siguientes';
    $('#panelAction').innerHTML='<button id="weekShowAll" class="secondary-button" type="button">Agenda completa</button>';
    $('#weekShowAll').addEventListener('click',()=>{state.appointmentsWindow='';renderAppointmentsPanel();});
    const groups=new Map();
    rows.forEach(i=>{const key=localDateKeyFromMs(i.dueAt),list=groups.get(key)||[];list.push(i);groups.set(key,list);});
    $('#panelContent').innerHTML=rows.length?'<div class="agenda-week-groups">'+[...groups.entries()].map(([key,list])=>'<section class="agenda-week-group"><h3>'+esc(new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long'}).format(new Date(key+'T12:00:00')))+'</h3><div class="stack">'+list.map(i=>agendaCard(i,true)).join('')+'</div></section>').join('')+'</div>':'<div class="empty-state">No hay eventos en los próximos 7 días.</div>';
    bindItemButtons($('#panelContent'));return;
  }
  const rows=parseNative(NexoNative.getAppointments(),[]);
  const items=rows.map(original=>{
    if(!original.recurrence||original.recurrence==='none')return original;
    const next=Number(NexoNative.getNextOccurrence(original.id,today.getTime()-1));
    if(!next)return {...original,seriesEnded:true};
    const occurrence=parseNative(NexoNative.getOccurrence(original.id,next),{});
    return occurrence.id?occurrence:{...original,occurrenceAt:next,dueAt:next,recurring:true};
  });
  const done=i=>i.status==='done'||i.occurrenceStatus==='done',cancelled=i=>i.status==='cancelled',closed=i=>done(i)||cancelled(i);
  const filter=state.appointmentsFilter,shown=items.filter(i=>{if(filter==='all')return true;if(filter==='done')return done(i);if(filter==='undated')return !i.dueAt&&!closed(i);return !closed(i);});
  const group=i=>!i.dueAt?2:i.dueAt>=today.getTime()?0:1;
  shown.sort((a,b)=>group(a)-group(b)||(group(a)===1?(b.dueAt||0)-(a.dueAt||0):(a.dueAt||0)-(b.dueAt||0))||(b.createdAt||0)-(a.createdAt||0));
  $('#panelTitle').textContent='Agenda';$('#panelSubtitle').textContent='Citas, tareas y compromisos · con fecha o sin ella';$('#panelAction').innerHTML='';
  $('#panelContent').innerHTML='<div class="appointment-filters">'+[['pending','Pendientes'],['undated','Sin fecha'],['done','Completadas'],['all','Todas']].map(([key,label])=>'<button type="button" class="'+(key===filter?'active':'')+'" aria-pressed="'+(key===filter)+'" data-appointment-filter="'+key+'">'+label+'</button>').join('')+'</div><div id="appointmentList" class="stack">'+(shown.length?shown.map(i=>agendaCard(i,true)).join(''):'<div class="empty-state">No hay citas en esta vista. Pulsa + Cita para añadir una.</div>')+'</div>';
  $$('[data-appointment-filter]').forEach(b=>b.addEventListener('click',()=>{state.appointmentsFilter=b.dataset.appointmentFilter;renderAppointmentsPanel();}));
  bindItemButtons($('#appointmentList'));
}

function openAgendaOptions(id,occurrenceAt,recurring){
  const i=parseNative(recurring?NexoNative.getOccurrence(id,occurrenceAt):NexoNative.getItem(id),{});
  if(!i.id){toast('Este elemento ya no existe.');refreshCurrent();return;}
  const done=i.status==='done'||i.occurrenceStatus==='done',appointment=isAppointmentType(i.type);
  openModal(i.title||'Cita',`<div class="scope-buttons"><button id="agendaEditAction" class="primary-button">${uiIcon('edit')} Ver / editar</button>${appointment?`<button id="agendaDoneAction" class="secondary-button">${uiIcon('check')} ${done?'Marcar pendiente':'Marcar completada'}</button>`:''}${appointment&&!done&&i.dueAt?'<button id="agendaPostponeAction" class="secondary-button">Posponer</button>':''}${(i.locationText||i.mapsUrl)?`<button id="agendaDirectionsAction" class="secondary-button">${uiIcon('pin')} Ir al lugar</button>`:''}<button id="agendaShareAction" class="secondary-button">${uiIcon('share')} Compartir</button></div>`);
  $('#agendaEditAction').addEventListener('click',()=>{closeModal();if(recurring)openOccurrence(id,occurrenceAt);else openItem(id);});
  $('#agendaDoneAction')?.addEventListener('click',()=>{
    const result=parseNative(recurring?NexoNative.setOccurrenceStatus(id,occurrenceAt,done?'pending':'done'):NexoNative.updateItem(JSON.stringify({id,status:done?'pending':'done',statusManual:true})),{});
    if(!result.ok){toast('No se pudo actualizar la cita.');return;}
    closeModal();refreshCurrent();toast(done?'Cita pendiente':'Cita completada');
  });
  $('#agendaPostponeAction')?.addEventListener('click',()=>{closeModal();openPostponeSheet(id,occurrenceAt,recurring);});
  $('#agendaDirectionsAction')?.addEventListener('click',()=>{closeModal();if(i.mapsUrl)NexoNative.openExternal(String(i.mapsUrl));else NexoNative.openLocationDirections(String(i.locationText));});
  $('#agendaShareAction').addEventListener('click',()=>{closeModal();openItemShareMenu(id,occurrenceAt,recurring,!!i.dueAt);});
}

function renderTasksPanel(){renderAppointmentsPanel();}

function renderRemindersPanel(){renderAppointmentsPanel();}

function renderHistoryPanel(){
  const hs=parseNative(NexoNative.getHistory(),[]);
  $('#panelContent').innerHTML=hs.length?hs.map(h=>`<div class="history-row"><div class="history-time">${fmtShort(h.createdAt)}</div><div class="history-main"><strong>${esc(h.label||h.entityType)}</strong><span class="muted">${esc(h.action)} · ${fmtTime(h.createdAt)}</span></div></div>`).join(''):'<div class="empty-state">Todavía no hay actividad.</div>';
}

function renderListsPanel(){
  const lists=parseNative(NexoNative.getLists(),[]);
  $('#panelAction').innerHTML='';
  $('#panelContent').innerHTML=lists.length?lists.map(l=>{
    const count=Number(l.count||0),pending=Number(l.pending||0),done=Math.max(0,count-pending);
    return `<div class="list-row"><div class="list-row-shell"><button class="row-open" data-list-open="${l.id}"><div class="list-row-head"><div><strong>${esc(l.title)}</strong><div class="small muted">${esc(l.kind)} · ${pending} pendiente${pending===1?'':'s'} · ${count} elemento${count===1?'':'s'}${done?` · ${done} completado${done===1?'':'s'}`:''}</div></div></div></button><div class="record-card-actions">${recordIconAction('edit','Editar lista',`data-list-manage="${l.id}"`)}${recordIconAction('share','Compartir lista',`data-list-share="${l.id}"`)}${recordIconAction('trash','Eliminar lista',`data-list-delete="${l.id}"`,'icon-action--danger')}</div></div></div>`;
  }).join(''):'<div class="empty-state">No tienes listas.</div>';
  $$('[data-list-open]').forEach(b=>b.addEventListener('click',()=>openList(Number(b.dataset.listOpen))));
  $$('[data-list-manage]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openListManagement(Number(b.dataset.listManage));}));
  $$('[data-list-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.shareNexoList(Number(b.dataset.listShare));}));
  $$('[data-list-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const id=Number(b.dataset.listDelete),l=parseNative(NexoNative.getList(id),{});confirmGeneric('Eliminar lista','Se eliminará “'+(l.title||'esta lista')+'” y todos sus elementos.',()=>{if(NexoNative.deleteList(id)){toast('Lista eliminada');renderListsPanel();}else toast('No se pudo eliminar');},'Eliminar');}));
}

function openList(id,options={}){
  const l=parseNative(NexoNative.getList(id),{});
  if(!l.id){
    router.history=router.history.filter(r=>!(r.screen==='panel'&&r.panel==='lists'&&r.list===id));
    if(state.screen==='panel'&&state.currentList===id)navigateBack();
    toast('La lista ya no existe.');return;
  }
  navigate('panel',{...options,panel:'lists',list:id});
  const entries=parseNative(NexoNative.getListEntries(id),[]),shopping=(l.kind||'').toLowerCase()==='compra';
  $('#panelTitle').textContent=l.title||'Lista';$('#panelSubtitle').textContent=shopping?'Toca un producto para marcarlo. También puedes hablar.':(l.kind||'');
  $('#panelAction').innerHTML='<div class="list-header-actions"><button id="shareListBtn" class="secondary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-share"></use></svg> Compartir</button><button id="manageListBtn" class="secondary-button list-manage-button" aria-label="Gestionar lista"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-more"></use></svg></button></div>';
  $('#shareListBtn').addEventListener('click',()=>NexoNative.shareNexoList(id));
  $('#manageListBtn').addEventListener('click',()=>openListManagement(id));
  const voiceHint=shopping?'Para varios productos, sepáralos con «y»: “huevos y mantequilla y leche y pan”. También puedes decir “quita el azúcar” o “ya he comprado los huevos”.':'Para varios elementos, sepáralos con «y»: “pasaporte y cargador y medicamentos”.';
  $('#panelContent').innerHTML=`<div class="list-voice-hint list-voice-hint--fab">${esc(voiceHint)} Usa el botón flotante del micrófono para dictar.</div><div class="add-inline"><input id="listQuickInput" type="text" placeholder="${shopping?'Escribe productos…':'Escribe un elemento…'}"><button id="listAddBtn" class="primary-button">Añadir</button></div><div>${entries.length?entries.map(e=>`<div class="list-entry ${e.checked?'checked':''}"><input type="checkbox" data-entry-toggle="${e.id}" ${e.checked?'checked':''} aria-label="${e.checked?'Desmarcar':'Marcar'} ${escAttr(e.text)}"><button type="button" class="entry-text entry-text-button" data-entry-text-toggle="${e.id}" data-entry-checked="${e.checked?'1':'0'}">${esc(e.text)}</button>${e.category?`<span class="entry-cat">${esc(e.category)}</span>`:''}<button class="entry-delete" aria-label="Eliminar ${escAttr(e.text)}" data-entry-delete="${e.id}"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-close"></use></svg></button></div>`).join(''):'<div class="empty-state">Lista vacía. Escribe un elemento o usa el micrófono flotante.</div>'}</div>`;
  $('#listAddBtn').addEventListener('click',()=>{const v=$('#listQuickInput').value.trim();if(!v)return;NexoNative.addListItems(id,v,shopping);openList(id);});
  $('#listQuickInput').addEventListener('keydown',e=>{if(e.key==='Enter')$('#listAddBtn').click();});
  $$('[data-entry-toggle]').forEach(c=>c.addEventListener('change',()=>{updateListCheck(Number(c.dataset.entryToggle),c.checked,c.closest('.list-entry'));}));
  $$('[data-entry-text-toggle]').forEach(b=>b.addEventListener('click',()=>{updateListCheck(Number(b.dataset.entryTextToggle),b.dataset.entryChecked!=='1',b.closest('.list-entry'));}));
  $$('[data-entry-delete]').forEach(b=>b.addEventListener('click',()=>{NexoNative.deleteListEntry(Number(b.dataset.entryDelete));openList(id);}));
}
function openListManagement(id){
  const l=parseNative(NexoNative.getList(id),{});if(!l.id){toast('La lista ya no existe.');if(state.lastPanel==='lists')renderListsPanel();return;}
  const entries=parseNative(NexoNative.getListEntries(id),[]),count=entries.length,done=entries.filter(e=>e.checked).length;
  openModal('Gestionar lista',`<div class="list-manage-summary"><strong>${esc(l.title)}</strong><span>${count} elemento${count===1?'':'s'} · ${done} completado${done===1?'':'s'}</span></div><div class="list-manage-grid"><button id="listRenameAction" class="secondary-button"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-edit"></use></svg> Renombrar</button><button id="listDuplicateAction" class="secondary-button">⧉ Duplicar</button><button id="listResetAction" class="secondary-button" ${done?'':'disabled'}>↺ Reiniciar</button><button id="listClearAction" class="secondary-button" ${count?'':'disabled'}>Vaciar lista</button><button id="listDeleteAction" class="danger-button solid">Eliminar lista</button></div>`);
  $('#listRenameAction').addEventListener('click',()=>openRenameListModal(id));
  $('#listDuplicateAction').addEventListener('click',()=>{const newId=Number(NexoNative.duplicateList(id));closeModal();if(newId>0){toast('Lista duplicada');openList(newId);}else toast('No se pudo duplicar la lista.');});
  $('#listResetAction').addEventListener('click',()=>{closeModal();confirmGeneric('Reiniciar lista',`Se desmarcarán los ${done} elemento${done===1?'':'s'} completado${done===1?'':'s'} de “${l.title}”.`,()=>{const n=Number(NexoNative.resetList(id));toast(n?`${n} elemento${n===1?'':'s'} vuelto${n===1?'':'s'} a pendiente.`:'La lista ya estaba reiniciada.');if(state.currentList===id)openList(id);else renderListsPanel();},'Reiniciar');});
  $('#listClearAction').addEventListener('click',()=>{closeModal();confirmGeneric('Vaciar lista',`Se eliminarán los ${count} elemento${count===1?'':'s'} de “${l.title}”. La lista seguirá existiendo.`,()=>{NexoNative.clearList(id);toast('Lista vaciada');if(state.currentList===id)openList(id);else renderListsPanel();},'Vaciar');});
  $('#listDeleteAction').addEventListener('click',()=>{closeModal();confirmGeneric('Eliminar lista',`Se eliminará “${l.title}” y todos sus elementos. Esta acción no se puede deshacer.`,()=>{
    if(!NexoNative.deleteList(id)){toast('No se pudo eliminar la lista.');return;}
    router.history=router.history.filter(r=>!(r.screen==='panel'&&r.panel==='lists'&&r.list===id));
    if(Number(NexoNative.getPreferredShoppingList())===id)NexoNative.setPreferredShoppingList(0);
    toast('Lista eliminada');
    if(state.currentList===id)navigateBack();else renderListsPanel();
  },'Eliminar');});
}
function openRenameListModal(id){
  const l=parseNative(NexoNative.getList(id),{});if(!l.id){closeModal();toast('La lista ya no existe.');return;}
  openModal('Renombrar lista',`<div class="modal-form"><label>Nombre<input id="listRenameTitle" type="text" maxlength="120" value="${escAttr(l.title||'')}"></label></div><div class="modal-actions"><button id="listRenameCancel" class="secondary-button">Cancelar</button><button id="listRenameSave" class="primary-button">Guardar</button></div>`);
  setTimeout(()=>{const input=$('#listRenameTitle');input.focus();input.select();},70);
  $('#listRenameCancel').addEventListener('click',closeModal);
  $('#listRenameSave').addEventListener('click',()=>{const title=$('#listRenameTitle').value.trim();if(!title){toast('Escribe un nombre');return;}const ok=NexoNative.renameList(id,title);closeModal();toast(ok?'Nombre actualizado':'No se pudo renombrar la lista.');if(state.currentList===id)openList(id);else renderListsPanel();});
  $('#listRenameTitle').addEventListener('keydown',e=>{if(e.key==='Enter')$('#listRenameSave').click();});
}

function recurrenceOptions(selected='none'){
  return Object.entries(RECURRENCE_LABEL).map(([k,v])=>`<option value="${k}" ${k===selected?'selected':''}>${esc(v)}</option>`).join('');
}

function openItem(id){
  const i=parseNative(NexoNative.getItem(id),{});if(!i.id)return;
  if(i.type==='document'){openDocumentEditor(id);return;}
  if(isPlaceType(i.type)){openPlaceEditor(id);return;}
  if(i.recurrence&&i.recurrence!=='none'&&i.dueAt){const dayStart=new Date();dayStart.setHours(0,0,0,0);const next=Number(NexoNative.getNextOccurrence(i.id,dayStart.getTime()-1))||i.dueAt;openOccurrence(i.id,next);return;}
  const url=(i.body||'').match(/https?:\/\/\S+/)?.[0],iso=isoFromMs(i.dueAt),endAt=itemEffectiveEndAt(i),endIso=isoFromMs(endAt),allDay=i.allDay===true||i.hasTime===false,endTime=endTimeForItem(i),originalAlerts=getItemAlertMinutes(id);let editAlerts=[...originalAlerts];
  openModal(i.title,`${recordEditorActionBar('saveEditBtn','editItemTopBtn','shareItemBtn','deleteItemBtn')}<div class="detail-meta"><span class="pill">${esc(categoryLabel(i.category))}</span><span class="meta-tag">${esc(typeLabel(i.type))}</span>${i.dueAt?`<span class="meta-tag">${esc(fmtDueItem(i))}</span>`:''}</div>
    ${i.body?`<div class="detail-body">${esc(i.body)}</div>`:''}${i.attachmentName?`<div class="attachment-chip">Archivo guardado: ${esc(i.attachmentName)}</div>`:''}
    ${eventParticipantAvatarsHtml(i,'event-participant-avatars--detail')}
    <details class="details-box" open><summary>Editar</summary><div class="detail-grid">
      <label>Título<input id="editTitle" type="text" value="${escAttr(i.title)}"></label>
      <label>Descripción<textarea id="editBody" rows="4">${esc(i.body||'')}</textarea></label>
      ${i.originalText&&i.originalText!==i.body?`<details class="original-capture"><summary>Ver texto original capturado</summary><div>${esc(i.originalText)}</div></details>`:''}
      <div class="form-grid event-settings-grid"><label>Categoría<select id="editCategory">${CATEGORIES.map(c=>`<option ${c===categoryLabel(i.category)?'selected':''}>${esc(c)}</option>`).join('')}</select></label>${itemTypeControl('editType',i.type)}</div>
      <div class="form-grid event-range-grid"><label>Fecha inicio<div class="date-wrap"><input id="editDate" type="date" value="${i.dueAt?escAttr(iso.date):''}"></div></label><label>Fecha fin<div class="date-wrap"><input id="editEndDate" type="date" value="${endAt?escAttr(endIso.date):''}"></div></label></div>
      <label class="switch-row event-all-day-row"><input id="editAllDay" type="checkbox" ${allDay?'checked':''}><span><strong>Todo el día</strong><small>Desactívalo para indicar hora de inicio y fin.</small></span></label>
      <div class="form-grid event-range-grid"><label id="editStartTimeLabel">Hora inicio<div class="date-wrap"><input id="editTime" type="time" value="${!allDay&&i.dueAt?escAttr(iso.time):''}"></div></label><label id="editEndTimeLabel">Hora fin<div class="date-wrap"><input id="editEndTime" type="time" value="${!allDay?escAttr(endTime):''}"></div></label></div>
      <div class="location-field"><label for="editLocation">Ubicación <span class="optional">(opcional)</span></label><div class="location-input-row"><input id="editLocation" type="text" value="${escAttr(eventLocationValue(i))}" placeholder="Dirección o enlace de Google Maps"><button id="editLocationMapBtn" type="button" class="secondary-button location-map-button" aria-label="Abrir ubicación en Google Maps"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-pin"></use></svg><span>Maps</span></button></div><input id="editMapsUrl" type="hidden" value="${escAttr(i.mapsUrl||'')}"></div>
      <label>Zona horaria<input id="editTimeZone" type="text" value="${escAttr(i.timeZone||'Europe/Madrid')}"></label>
      ${eventPeopleAssignmentHtml('editPeopleChips')}
      <div class="form-grid event-settings-grid"><label>Repetición<select id="editRecurrence">${recurrenceOptions(i.recurrence||'none')}</select></label><label id="editUntilLabel" class="${i.recurrence&&i.recurrence!=='none'?'':'hidden'}">Repetir hasta <span class="optional">(opcional)</span><div class="date-wrap"><input id="editUntil" type="date" value="${i.recurrenceUntil?escAttr(isoFromMs(i.recurrenceUntil).date):''}"></div></label></div>
      <div id="editAlertsSection" class="alert-editor ${i.dueAt?'':'hidden'}"><div class="alert-editor-head"><div><strong><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-bell"></use></svg> Avisos</strong><div class="small muted">Se mostrarán en Nexo y se incluyen al compartir el evento como .ics.</div></div><button id="addEditAlert" type="button" class="mini-button primary">+ Añadir aviso</button></div><div id="editAlertList" class="alert-list"></div></div>
      ${isAppointmentType(i.type)?`<label class="switch-row voice-reminder-row"><input id="editVoiceEnabled" type="checkbox" ${i.voiceEnabled?'checked':''}><span><strong><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-speaker"></use></svg> Aviso por voz</strong><small>Lee en voz alta cada aviso configurado.</small></span></label>`:''}
      ${isAppointmentType(i.type)?`<label>Estado<select id="editStatus"><option value="pending" ${!['done','postponed','cancelled'].includes(i.status)?'selected':''}>Pendiente</option><option value="done" ${i.status==='done'?'selected':''}>Completada</option><option value="postponed" ${i.status==='postponed'?'selected':''}>Pospuesta</option><option value="cancelled" ${i.status==='cancelled'?'selected':''}>Cancelada</option></select></label>`:''}
      
    </div></details>
    <div class="modal-actions wrap-actions">${isAppointmentType(i.type)&&i.dueAt&&i.status!=='done'?'<button id="postponeItemBtn" class="secondary-button">Posponer</button>':''}${url?'<button id="openUrlBtn" class="secondary-button">Abrir enlace</button>':''}</div>`);

  let editParticipantIds=normalizeParticipantIds(i.participantIds||[]);
  renderPersonAssignment('editPeopleChips',editParticipantIds,ids=>{editParticipantIds=ids;});
  const syncEditAllDay=()=>{
    const checked=!!$('#editAllDay').checked;
    $('#editStartTimeLabel').classList.toggle('hidden',checked);$('#editEndTimeLabel').classList.toggle('hidden',checked);
    if(checked){$('#editTime').value='';$('#editEndTime').value='';}
    else{if(!$('#editTime').value)$('#editTime').value='09:00';if(!$('#editEndTime').value)$('#editEndTime').value=addOneHourTime($('#editTime').value);}
  };
  syncEditAllDay();
  $('#editItemTopBtn').addEventListener('click',()=>{const details=$('#modalBody .details-box');if(details)details.open=true;$('#editTitle')?.focus();});
  $('#editAllDay').addEventListener('change',syncEditAllDay);
  $('#editDate').addEventListener('change',()=>{if($('#editDate').value&&(!$('#editEndDate').value||$('#editEndDate').value<$('#editDate').value))$('#editEndDate').value=$('#editDate').value;});
  $('#editTime').addEventListener('change',()=>{if($('#editTime').value&&!$('#editEndTime').value)$('#editEndTime').value=addOneHourTime($('#editTime').value);});
  const renderEditAlerts=()=>renderAlertList('#editAlertList',editAlerts,next=>{editAlerts=normalizeAlertMinutes(next);renderEditAlerts();});renderEditAlerts();
  $('#addEditAlert').addEventListener('click',()=>openAddAlertOverlay(v=>{editAlerts=normalizeAlertMinutes([...editAlerts,v]);renderEditAlerts();}));
  if($('#editVoiceEnabled'))$('#editVoiceEnabled').addEventListener('change',()=>{if($('#editVoiceEnabled').checked&&!editAlerts.length){editAlerts=[0];renderEditAlerts();toast('Se ha añadido un aviso en el momento para poder leerlo por voz.');}});
  $('#editDate').addEventListener('change',()=>{$('#editAlertsSection').classList.toggle('hidden',!$('#editDate').value);if(!$('#editDate').value)editAlerts=[];renderEditAlerts();});
  $('#editRecurrence').addEventListener('change',()=>{const recurring=$('#editRecurrence').value!=='none';$('#editUntilLabel').classList.toggle('hidden',!recurring);});
  let editStatusTouched=false;
  if($('#editStatus'))$('#editStatus').addEventListener('change',()=>{editStatusTouched=true;});
  $('#saveEditBtn').addEventListener('click',()=>{
    const date=$('#editDate').value,allDay=!!$('#editAllDay').checked,time=allDay?'':$('#editTime').value,endDate=$('#editEndDate').value||date,endTime=allDay?'':$('#editEndTime').value,rec=$('#editRecurrence').value,dueAt=dateTimeToMs(date,time),endAt=eventEndFromFields(date,time,endDate,endTime,allDay),durationMinutes=durationMinutesBetween(dueAt,endAt);
    if(date&&endDate&&endDate<date){toast('La fecha de fin no puede ser anterior al inicio.');return;}
    if(rec!=='none'&&!date){toast('Una periodicidad necesita fecha de inicio.');return;}

    const selectedType=$('#editType').value,statusPayload=(isAppointmentType(i.type)&&isAppointmentType(selectedType)&&$('#editStatus')&&editStatusTouched)?{status:$('#editStatus').value,statusManual:true}:{};
    const conflicts=(date&&time)?overlappingItems(dueAt,durationMinutes,id):[];
    if(editAlerts.length&&!date){toast('Los avisos necesitan una fecha.');return;}
    const res=parseNative(NexoNative.updateItem(JSON.stringify({id,title:$('#editTitle').value.trim(),body:$('#editBody').value,category:$('#editCategory').value,type:selectedType,dueAt,endAt,hasTime:!!(date&&time&&!allDay),allDay,durationMinutes,locationText:$('#editLocation').value.trim(),mapsUrl:$('#editMapsUrl').value.trim(),timeZone:$('#editTimeZone').value.trim()||'Europe/Madrid',participantIds:normalizeParticipantIds(editParticipantIds),recurrence:rec,recurrenceRule:recurrenceRuleFor(rec),recurrenceUntil:rec!=='none'&&$('#editUntil').value?endOfDayMs($('#editUntil').value):0,voiceEnabled:$('#editVoiceEnabled')?$('#editVoiceEnabled').checked:false,needsReview:false,...statusPayload})),{});
    if(res.ok){let alertsOk=true;if(!sameAlerts(editAlerts,originalAlerts)){const ar=parseNative(NexoNative.replaceItemReminders(id,JSON.stringify(editAlerts)),{});alertsOk=!!ar.ok;}closeModal();toast(!alertsOk?'Cambios guardados, pero no se pudieron actualizar los avisos':conflicts.length?`Cambios guardados · coincide con ${conflicts.length} elemento${conflicts.length===1?'':'s'}`:'Cambios guardados');refreshCurrent();}else toast(res.error||'No se pudieron guardar los cambios.');
  });
  bindEventLocationField('#editLocation','#editMapsUrl','#editLocationMapBtn');
  if($('#shareItemBtn'))$('#shareItemBtn').addEventListener('click',()=>openItemShareMenu(i.id,i.dueAt||0,false,!!i.dueAt));
  if($('#postponeItemBtn'))$('#postponeItemBtn').addEventListener('click',()=>{closeModal();openPostponeSheet(i.id,i.dueAt,false);});
  if(url)$('#openUrlBtn').addEventListener('click',()=>NexoNative.openExternal(url));
  $('#deleteItemBtn').addEventListener('click',()=>confirmDeleteItem(i));
}

function openOccurrence(seriesId,occurrenceAt){
  const i=parseNative(NexoNative.getOccurrence(seriesId,occurrenceAt),{});if(!i.id){toast('Esta aparición ya no existe.');refreshCurrent();return;}
  if(i.type==='document'){openDocumentEditor(seriesId);return;}
  if(isPlaceType(i.type)){openPlaceEditor(seriesId);return;}
  const iso=isoFromMs(i.dueAt),endAt=itemEffectiveEndAt(i),endIso=isoFromMs(endAt),allDay=i.allDay===true||i.hasTime===false,until=i.recurrenceUntil?isoFromMs(i.recurrenceUntil).date:'',originalDate=iso.date,originalTime=!allDay?iso.time:'',originalEndDate=endIso.date,originalRecurrence=i.recurrence||'none',originalUntil=until||'',currentState=i.occurrenceStatus||'active',endTime=endTimeForItem(i),originalAlerts=getItemAlertMinutes(seriesId);let occAlerts=[...originalAlerts];
  openModal(i.title,`${recordEditorActionBar('saveOccurrenceBtn','editOccurrenceTopBtn','shareOccurrenceBtn','deleteOccurrenceBtn')}<div class="series-banner"><strong>↻ ${esc(recurrenceLabel(i.recurrence))}</strong><span>Esta es una aparición de una serie${i.recurrenceUntil?` · hasta ${esc(fmtDate(i.recurrenceUntil,false))}`:''}</span></div>
    <div class="detail-meta"><span class="pill">${esc(categoryLabel(i.category))}</span><span class="meta-tag">${esc(typeLabel(i.type))}</span><span class="meta-tag">${esc(fmtDueItem(i))}</span>${currentState==='done'?'<span class="meta-tag done-tag">Realizada</span>':''}</div>
    ${i.body?`<div class="detail-body">${esc(i.body)}</div>`:''}
    ${eventParticipantAvatarsHtml(i,'event-participant-avatars--detail')}
    <details class="details-box" open><summary>Editar</summary><div class="detail-grid">
      <label>Título<input id="occTitle" type="text" value="${escAttr(i.title)}"></label>
      <label>Descripción<textarea id="occBody" rows="4">${esc(i.body||'')}</textarea></label>
      ${i.originalText&&i.originalText!==i.body?`<details class="original-capture"><summary>Ver texto original capturado</summary><div>${esc(i.originalText)}</div></details>`:''}
      <div class="form-grid event-settings-grid"><label>Categoría<select id="occCategory">${CATEGORIES.map(c=>`<option ${c===categoryLabel(i.category)?'selected':''}>${esc(c)}</option>`).join('')}</select></label>${itemTypeControl('occType',i.type)}</div>
      <div class="form-grid event-range-grid"><label>Fecha inicio<div class="date-wrap"><input id="occDate" type="date" value="${escAttr(iso.date)}"></div></label><label>Fecha fin<div class="date-wrap"><input id="occEndDate" type="date" value="${escAttr(endIso.date)}"></div></label></div>
      <label class="switch-row event-all-day-row"><input id="occAllDay" type="checkbox" ${allDay?'checked':''}><span><strong>Todo el día</strong><small>Desactívalo para indicar hora de inicio y fin.</small></span></label>
      <div class="form-grid event-range-grid"><label id="occStartTimeLabel">Hora inicio<div class="date-wrap"><input id="occTime" type="time" value="${!allDay?escAttr(iso.time):''}"></div></label><label id="occEndTimeLabel">Hora fin<div class="date-wrap"><input id="occEndTime" type="time" value="${!allDay?escAttr(endTime):''}"></div></label></div>
      <div class="location-field"><label for="occLocation">Ubicación <span class="optional">(opcional)</span></label><div class="location-input-row"><input id="occLocation" type="text" value="${escAttr(eventLocationValue(i))}" placeholder="Dirección o enlace de Google Maps"><button id="occLocationMapBtn" type="button" class="secondary-button location-map-button" aria-label="Abrir ubicación en Google Maps"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-pin"></use></svg><span>Maps</span></button></div><input id="occMapsUrl" type="hidden" value="${escAttr(i.mapsUrl||'')}"></div>
      <label>Zona horaria<input id="occTimeZone" type="text" value="${escAttr(i.timeZone||'Europe/Madrid')}"></label>
      ${eventPeopleAssignmentHtml('occPeopleChips')}
      <div class="form-grid event-settings-grid"><label>Repetición<select id="occRecurrence">${recurrenceOptions(i.recurrence)}</select></label><label>Repetir hasta <span class="optional">(opcional)</span><div class="date-wrap"><input id="occUntil" type="date" value="${escAttr(until||'')}"></div></label></div><div class="small muted">Si cambias la repetición, Nexo te preguntará si debe aplicarse desde esta aparición o a toda la serie.</div>
      <div class="alert-editor"><div class="alert-editor-head"><div><strong><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-bell"></use></svg> Avisos de la serie</strong><div class="small muted">Los avisos se repiten en cada aparición. Si los cambias, se aplican a la serie correspondiente.</div></div><button id="addOccAlert" type="button" class="mini-button primary">+ Añadir aviso</button></div><div id="occAlertList" class="alert-list"></div></div>
      ${['event','reminder','task','expiry'].includes(i.type)?`<label class="switch-row voice-reminder-row"><input id="occVoiceEnabled" type="checkbox" ${i.voiceEnabled?'checked':''}><span><strong><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-speaker"></use></svg> Aviso por voz</strong><small>Se aplica a toda la serie recurrente.</small></span></label>`:''}
      ${isAppointmentType(i.type)?`<label>Estado<select id="occStatus"><option value="pending" ${currentState==='active'||currentState==='pending'?'selected':''}>Pendiente</option><option value="done" ${currentState==='done'?'selected':''}>Completada</option><option value="postponed" ${currentState==='postponed'?'selected':''}>Pospuesta</option><option value="cancelled" ${currentState==='cancelled'?'selected':''}>Cancelada</option></select></label>`:''}
      
    </div></details>
    ${isAppointmentType(i.type)?`<div class="occurrence-status-actions"><button id="occToggleDone" class="secondary-button">${currentState==='done'?'Marcar pendiente':'Marcar completada'}</button>${currentState!=='done'?'<button id="occPostpone" class="secondary-button">Posponer</button>':''}<span class="small muted">Estas acciones afectan solo a esta aparición.</span></div>`:''}
    `);

  $('#editOccurrenceTopBtn').addEventListener('click',()=>{const details=$('#modalBody .details-box');if(details)details.open=true;$('#occTitle')?.focus();});
  let occParticipantIds=normalizeParticipantIds(i.participantIds||[]);
  renderPersonAssignment('occPeopleChips',occParticipantIds,ids=>{occParticipantIds=ids;});
  const syncOccAllDay=()=>{
    const checked=!!$('#occAllDay').checked;
    $('#occStartTimeLabel').classList.toggle('hidden',checked);$('#occEndTimeLabel').classList.toggle('hidden',checked);
    if(checked){$('#occTime').value='';$('#occEndTime').value='';}
    else{if(!$('#occTime').value)$('#occTime').value='09:00';if(!$('#occEndTime').value)$('#occEndTime').value=addOneHourTime($('#occTime').value);}
  };
  syncOccAllDay();
  $('#occAllDay').addEventListener('change',syncOccAllDay);
  $('#occDate').addEventListener('change',()=>{if($('#occDate').value&&(!$('#occEndDate').value||$('#occEndDate').value<$('#occDate').value))$('#occEndDate').value=$('#occDate').value;});
  $('#occTime').addEventListener('change',()=>{if($('#occTime').value&&!$('#occEndTime').value)$('#occEndTime').value=addOneHourTime($('#occTime').value);});
  const renderOccAlerts=()=>renderAlertList('#occAlertList',occAlerts,next=>{occAlerts=normalizeAlertMinutes(next);renderOccAlerts();});renderOccAlerts();
  $('#addOccAlert').addEventListener('click',()=>openAddAlertOverlay(v=>{occAlerts=normalizeAlertMinutes([...occAlerts,v]);renderOccAlerts();}));
  if($('#occVoiceEnabled'))$('#occVoiceEnabled').addEventListener('change',()=>{if($('#occVoiceEnabled').checked&&!occAlerts.length){occAlerts=[0];renderOccAlerts();toast('Se ha añadido un aviso en el momento para poder leerlo por voz.');}});
  let occStatusTouched=false;
  if($('#occStatus'))$('#occStatus').addEventListener('change',()=>{occStatusTouched=true;});
  if($('#occToggleDone'))$('#occToggleDone').addEventListener('click',()=>{const next=currentState==='done'?'pending':'done',r=parseNative(NexoNative.setOccurrenceStatus(seriesId,occurrenceAt,next),{});if(r.ok){closeModal();toast(next==='done'?'Cita completada':'Cita marcada pendiente');refreshCurrent();}else toast('No se pudo actualizar.');});
  if($('#occPostpone'))$('#occPostpone').addEventListener('click',()=>{closeModal();openPostponeSheet(seriesId,occurrenceAt,true);});
  bindEventLocationField('#occLocation','#occMapsUrl','#occLocationMapBtn');
  $('#shareOccurrenceBtn').addEventListener('click',()=>openItemShareMenu(seriesId,occurrenceAt,true,true));
  $('#saveOccurrenceBtn').addEventListener('click',()=>{
    const date=$('#occDate').value,allDay=!!$('#occAllDay').checked,time=allDay?'':$('#occTime').value,endDate=$('#occEndDate').value||date,endTime=allDay?'':$('#occEndTime').value,rec=$('#occRecurrence').value,untilDate=$('#occUntil').value,dueAt=dateTimeToMs(date,time),endAt=eventEndFromFields(date,time,endDate,endTime,allDay),durationMinutes=durationMinutesBetween(dueAt,endAt);
    if(!date){toast('Indica una fecha.');return;}if(endDate<date){toast('La fecha de fin no puede ser anterior al inicio.');return;}
    const payload={seriesId,occurrenceAt,title:$('#occTitle').value.trim(),body:$('#occBody').value,category:$('#occCategory').value,type:$('#occType').value,dueAt,endAt,hasTime:!!time&&!allDay,allDay,durationMinutes,locationText:$('#occLocation').value.trim(),mapsUrl:$('#occMapsUrl').value.trim(),timeZone:$('#occTimeZone').value.trim()||'Europe/Madrid',participantIds:normalizeParticipantIds(occParticipantIds),priority:i.priority||1,needsReview:false,recurrence:rec,recurrenceRule:recurrenceRuleFor(rec),recurrenceUntil:rec!=='none'&&untilDate?endOfDayMs(untilDate):0,occurrenceState:(occStatusTouched&&$('#occStatus')?$('#occStatus').value:currentState),dateChanged:date!==originalDate||endDate!==originalEndDate||(time||'')!==(originalTime||'')};
    const seriesSettingsChanged=rec!==originalRecurrence||(rec!=='none'&&(untilDate||'')!==originalUntil);
    chooseRecurrenceScope('¿Qué quieres modificar?',scope=>{const r=parseNative(NexoNative.updateOccurrence(JSON.stringify(payload),scope),{});if(r.ok){let alertsOk=true;const targetId=scope==='following'&&r.newSeriesId?Number(r.newSeriesId):seriesId;if(!sameAlerts(occAlerts,originalAlerts)){const ar=parseNative(NexoNative.replaceItemReminders(targetId,JSON.stringify(occAlerts)),{});alertsOk=!!ar.ok;}if($('#occVoiceEnabled')){const vr=parseNative(NexoNative.updateItem(JSON.stringify({id:targetId,voiceEnabled:$('#occVoiceEnabled').checked})),{});alertsOk=alertsOk&&!!vr.ok;}closeModal();toast(!alertsOk?'Cambios guardados, pero no se pudieron actualizar los avisos':scope==='single'?'Aparición actualizada':scope==='following'?'Serie actualizada desde esta fecha':'Serie completa actualizada');refreshCurrent();}else toast(r.error||'No se pudo actualizar.');},false,!seriesSettingsChanged);
  });
  $('#deleteOccurrenceBtn').addEventListener('click',()=>chooseRecurrenceScope('¿Qué quieres eliminar?',scope=>{const r=parseNative(NexoNative.deleteOccurrence(seriesId,occurrenceAt,scope),{});if(r.ok){closeModal();toast(scope==='single'?'Aparición eliminada':scope==='following'?'Esta aparición y las siguientes se han eliminado':'Serie eliminada');refreshCurrent();}else toast(r.error||'No se pudo eliminar.');},true));
}

function chooseRecurrenceScope(title,callback,isDelete=false,allowSingle=true){
  const overlay=document.createElement('div');overlay.className='modal scope-overlay';
  const note=allowSingle?'Este elemento forma parte de una serie recurrente.':'Has cambiado la periodicidad. Ese cambio debe afectar desde esta aparición o a toda la serie.';
  overlay.innerHTML=`<div class="confirm-sheet scope-sheet"><h2>${esc(title)}</h2><p class="muted">${esc(note)}</p><div class="scope-buttons">${allowSingle?'<button class="secondary-button" data-scope="single">Solo esta aparición</button>':''}<button class="secondary-button" data-scope="following">Esta y las siguientes</button><button class="${isDelete?'danger-button solid':'primary-button'}" data-scope="all">Toda la serie</button><button class="ghost-button" data-scope-cancel>Cancelar</button></div></div>`;
  document.body.appendChild(overlay);
  overlay.querySelectorAll('[data-scope]').forEach(b=>b.addEventListener('click',()=>{const scope=b.dataset.scope;overlay.remove();callback(scope);}));
  overlay.querySelector('[data-scope-cancel]').addEventListener('click',()=>overlay.remove());
  overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove();});
}

function confirmDeleteItem(item){
  $('#confirmTitle').textContent='Eliminar elemento';$('#confirmText').textContent=`¿Eliminar “${item.title}”? Podrás deshacerlo justo después.`;
  $('#confirmModal').classList.remove('hidden');
  state.confirmAction=()=>{
    const res=parseNative(NexoNative.deleteItem(item.id),{});$('#confirmModal').classList.add('hidden');closeModal();
    if(res.ok){
      const finalizeTimer=setTimeout(()=>NexoNative.finalizeDelete(res.undoToken),6500);
      toast('Elemento eliminado','Deshacer',()=>{clearTimeout(finalizeTimer);const rr=parseNative(NexoNative.restoreItem(res.undoToken),{});toast(rr.ok?'Elemento restaurado':'No se pudo restaurar');refreshCurrent();},6000);
      refreshCurrent();
    } else toast(res.error||'No se pudo eliminar.');
  };
}

function openTaskModal(){prepareQuickNoteForType('event');}

function openReminderModal(){prepareQuickNoteForType('event');}

function openNewListModal(){
  openModal('Nueva lista',`<div class="modal-form"><label>Nombre<input id="listTitle" type="text" placeholder="Ej.: Compra semanal"></label><label>Tipo<select id="listKind"><option>Compra</option><option>Viaje</option><option>Cosas pendientes</option><option>Restaurantes</option><option>Lugares por visitar</option><option>Productos</option><option>Equipaje</option><option>Casa</option><option>General</option></select></label><label class="switch-row"><input id="listReusable" type="checkbox" checked><span>Lista reutilizable</span></label></div><div class="modal-actions"><button id="listSave" class="primary-button">Crear lista</button></div>`);
  setTimeout(()=>$('#listTitle').focus(),70);$('#listSave').addEventListener('click',()=>{const title=$('#listTitle').value.trim();if(!title){toast('Escribe un nombre');return;}const id=NexoNative.createList(title,$('#listKind').value,$('#listReusable').checked);closeModal();openList(Number(id));});
}
function openParkingModal(prefill=''){
  openModal('Guardar aparcamiento',`<p class="muted" style="margin-top:0">Nexo buscará durante unos segundos la ubicación más precisa disponible. Añade una referencia solo si te ayuda.</p><div class="modal-form"><label>Nota opcional<input id="parkingNote" type="text" value="${escAttr(prefill||'')}" placeholder="Ej.: Planta -2, zona B"></label></div><div class="modal-actions"><button id="parkingSave" class="primary-button">Guardar ubicación actual</button></div>`);
  $('#parkingSave').addEventListener('click',()=>{const b=$('#parkingSave');b.disabled=true;b.textContent='Obteniendo ubicación…';NexoNative.saveParking($('#parkingNote').value.trim());});
}

function openModal(title,html){
  discardDocumentDraft();
  if($('#modal').classList.contains('hidden'))state.modalReturnFocus=document.activeElement;
  $('#modalTitle').textContent=title;$('#modalBody').innerHTML=html;$('#modal').classList.remove('hidden');
  document.body.classList.add('modal-open');
  requestAnimationFrame(()=>$('#modalClose').focus());
}


function closeModal(){
  discardDocumentDraft();
  const noteWasOpen=!!state.noteEditor;if(noteWasOpen){flushNoteAutosave();state.noteEditor=null;}
  const journalWasOpen=!!state.journalEditor;if(journalWasOpen){flushJournalAutosave();state.journalEditor=null;}
  const medicationWasOpen=!!state.medicationEditor;if(medicationWasOpen)state.medicationEditor=null;
  $('#modal').classList.add('hidden');$('#modalBody').innerHTML='';document.body.classList.remove('modal-open','journal-editor-open','medication-editor-open');
  if(noteWasOpen)refreshNotesSurface();
  if(journalWasOpen){if(state.screen==='panel'&&state.lastPanel==='journal')renderJournalPanel();renderJournalPrompt(false);}
  if(state.modalReturnFocus?.isConnected)state.modalReturnFocus.focus({preventScroll:true});state.modalReturnFocus=null;
  updateFloatingActions();
}
function refreshCurrent(){
  if(state.screen==='today')renderToday();else if(state.screen==='search')runSearch();else if(state.screen==='more')renderMore();else if(state.screen==='calendar')renderCalendar();else if(state.screen==='panel'){if(state.currentList)openList(state.currentList);else if(state.lastPanel)openPanel(state.lastPanel);}updateFloatingActions();
}

function getShoppingListId(){const lists=parseNative(NexoNative.getLists(),[]),l=lists.find(x=>normVoiceText(x.title)==='lista de compra');return l?Number(l.id):Number(NexoNative.createList('Lista de compra','Compra',true));}
function ensureShoppingList(){
  const lists=parseNative(NexoNative.getLists(),[]),id=Number(NexoNative.getPreferredShoppingList())||0;
  if(id&&lists.some(l=>Number(l.id)===id)){openList(id);return;}
  openModal('Mi lista de compra',`<p class="muted">Elige la lista que quieres abrir directamente desde Inicio.</p><div class="scope-buttons">${lists.map(l=>`<button class="secondary-button" data-choose-shopping="${Number(l.id)}">${esc(l.title)}</button>`).join('')}<button id="createShoppingList" class="primary-button">Crear lista de compra</button></div>`);
  $$('[data-choose-shopping]').forEach(b=>b.addEventListener('click',()=>{const id=Number(b.dataset.chooseShopping);NexoNative.setPreferredShoppingList(id);closeModal();openList(id);}));
  $('#createShoppingList').addEventListener('click',()=>{const id=Number(NexoNative.createList('Lista de compra','Compra',true));if(!id){toast('No se pudo crear la lista.');return;}NexoNative.setPreferredShoppingList(id);closeModal();openList(id);});
}

function normVoiceText(value){return String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9áéíóúüñ\s]/gi,' ').replace(/\s+/g,' ').trim();}
function applyDisplayMode(mode,save=false){
  const m=['normal','comfortable','large'].includes(mode)?mode:'comfortable';state.displayMode=m;document.documentElement.dataset.displayMode=m;
  document.body.classList.remove('display-normal','display-comfortable','display-large');document.body.classList.add(`display-${m}`);
  $$('[data-display-mode]').forEach(b=>{b.classList.toggle('active',b.dataset.displayMode===m);b.setAttribute('aria-pressed',String(b.dataset.displayMode===m));});
  if(save)NexoNative.setDisplayMode(m);
}
function startManagedDictation(target,opts={}){
  state.voiceMode=target;state.managedVoiceTarget=target;state.managedVoiceBase=String(opts.base||'').trim();state.managedVoiceSegments=[];state.managedVoicePartial='';state.managedVoiceListId=Number(opts.listId||0);state.managedVoiceShopping=!!opts.shopping;
  if(target==='journal')state.journalVoiceBase=state.managedVoiceBase;if(target==='medication')state.medicationVoiceBase=state.managedVoiceBase;
  const overlay=$('#voiceOverlay');$('#managedVoiceTitle').textContent=opts.title||'Habla con Nexo';$('#managedVoiceStatus').textContent='Preparando micrófono…';$('#managedVoiceTranscript').textContent=state.managedVoiceBase||opts.hint||'Puedes hablar con normalidad. Pulsa TERMINAR cuando acabes.';$('#managedVoiceStop').disabled=false;$('#managedVoiceStop').textContent='Terminar';overlay.classList.remove('hidden');
  NexoNative.startManagedVoice();
}
function mergeVoiceSegments(parts){
  const clean=parts.map(x=>String(x||'').trim().replace(/\s+/g,' ')).filter(Boolean);if(!clean.length)return '';
  let words=clean[0].split(' ');
  for(let i=1;i<clean.length;i++){
    const next=clean[i].split(' ');let overlap=0,max=Math.min(10,words.length,next.length);
    for(let k=max;k>=1;k--){const a=words.slice(-k).join(' ').toLocaleLowerCase('es-ES'),b=next.slice(0,k).join(' ').toLocaleLowerCase('es-ES');if(a===b){overlap=k;break;}}
    words=words.concat(next.slice(overlap));
  }
  return words.join(' ').replace(/\s+/g,' ').trim();
}
function managedVoiceFullText(includePartial=false){const parts=[state.managedVoiceBase,...state.managedVoiceSegments];if(includePartial&&state.managedVoicePartial)parts.push(state.managedVoicePartial);return mergeVoiceSegments(parts);}
function updateManagedVoiceTranscript(){const text=managedVoiceFullText(true);$('#managedVoiceTranscript').textContent=text||'Te escucho…';}
function stopManagedDictation(){if($('#voiceOverlay').classList.contains('hidden'))return;$('#managedVoiceStop').disabled=true;$('#managedVoiceStop').textContent='Terminando…';$('#managedVoiceStatus').textContent='GUARDANDO LO QUE HAS DICHO…';NexoNative.stopManagedVoice();}

function resetManagedVoice(){
  state.managedVoiceTarget='';state.managedVoiceBase='';state.managedVoiceSegments=[];state.managedVoicePartial='';state.managedVoiceListId=0;state.managedVoiceShopping=false;
  state.voiceMode='capture';state.journalVoiceBase='';state.journalVoiceKind='';state.journalVoiceId=0;state.journalVoiceTitle='';state.journalVoiceTitleManual=false;state.journalVoiceFavorite=false;state.journalVoiceRevisit=false;
  state.medicationVoiceBase='';$('#voiceOverlay').classList.add('hidden');
}
function applyCaptureVoiceText(text){
  const value=String(text||'').trim();if(!value)return;
  if(state.screen!=='save')navigate('save');
  $('#captureText').value=value;
  setInterpretationStatus('working','Dictado añadido · interpretando localmente…');
  analyzeCapture({immediate:true});
  toast('Voz transcrita. Revisa lo que Nexo ha entendido.');
}

function finalizeManagedDictation(){
  let text=managedVoiceFullText(true);
  const target=state.managedVoiceTarget,listId=state.managedVoiceListId,shopping=state.managedVoiceShopping,noteId=state.noteVoiceId,noteTitle=state.noteVoiceTitle,noteTitleManual=state.noteVoiceTitleManual;
  const medicationEditorVoice=!!state.medicationVoiceEditor;
  const journalKind=state.journalVoiceKind,journalId=state.journalVoiceId,journalTitle=state.journalVoiceTitle,journalTitleManual=state.journalVoiceTitleManual,journalFavorite=state.journalVoiceFavorite,journalRevisit=state.journalVoiceRevisit;
  resetManagedVoice();state.noteVoiceId=0;state.noteVoiceTitle='';state.noteVoiceTitleManual=false;
  if(!text){toast('No he podido recoger ninguna frase. Inténtalo de nuevo.');return;}
  if(target==='universal')routeUniversalVoice(text);
  else if(target==='capture-edit')applyCaptureVoiceText(text);
  else if(target==='list')handleListVoice(text,listId,shopping);
  else if(target==='journal')openJournalComposer(text,journalKind,journalId,{id:journalId,title:journalTitle,titleManual:journalTitleManual,favorite:journalFavorite,revisit:journalRevisit});
  else if(target==='note')openNoteComposer(noteId,text,noteTitle,noteTitleManual);
  else if(target==='medication'){state.medicationVoiceEditor=false;if(medicationEditorVoice)applyMedicationVoiceToEditor(text);else handleMedicationVoice(text);}
  else{navigate('save');clearCapture();$('#captureText').value=text;analyzeCapture({immediate:true});}
}
function stripJournalVoicePrefix(text){return String(text||'').replace(/^\s*(?:guarda|apunta|anota)\s+(?:un\s+)?(?:pensamiento|reflexi[oó]n|frase|cita)\s*[:,-]?\s*/i,'').replace(/^\s*(?:pensamiento|reflexi[oó]n|frase|cita)\s*[:,-]\s*/i,'').trim();}
function looksLikeShoppingVoice(text){const n=normVoiceText(text),product=/(leche|huevos?|pan|agua|azucar|mantequilla|cafe|detergente|jamon|queso|yogur|papel|arroz|pasta|pollo|carne|pescado|tomate|lechuga|patata|cebolla|manzana|platano|aceite|harina|galleta|cereal)/.test(n);return /lista (?:de la )?compra|a la compra|en la compra/.test(n)||/^(?:comprar|compra|anade|añade|agrega|apunta|pon|mete)\b/.test(n)&&product||/^(?:quita|elimina|borra|saca|cambia|sustituye|reemplaza)\b/.test(n)&&product||/(?:ya he comprado|he comprado|todavia no|aun no|no he comprado)/.test(n)&&product||/(?:elimina|borra|quita|vacia) (?:lo|los) comprad/.test(n);}
function openAlarmConfirmation(alarm){
  if(!alarm||!alarm.isAlarm){toast('No he podido interpretar la alarma.');return;}
  if(!alarm.valid){const warnings=Array.isArray(alarm.warnings)?alarm.warnings:[];openModal('Alarma',`<div class="empty-state">${esc(warnings[0]||'Dime también la hora de la alarma.')}</div>`);return;}
  const when=alarm.targetAt?fmtDate(Number(alarm.targetAt),true):alarm.time;
  const warnings=Array.isArray(alarm.warnings)?alarm.warnings:[];
  openModal('Crear alarma',`<div class="alarm-preview"><div class="alarm-icon">⏰</div><div class="search-section-label">ALARMA DEL TELÉFONO</div><div class="alarm-time">${esc(alarm.time||'')}</div><div class="alarm-date">${esc(when||'')}</div><div class="alarm-repeat">${esc(alarm.repeatLabel||'No se repite')}</div>${warnings.length?`<div class="restore-warning"><strong>Revisa esto</strong><p>${warnings.map(esc).join('<br>')}</p></div>`:''}<p class="small muted">Nexo la enviará al sistema de alarmas del teléfono. Después podrás ajustar tono, vibración o repetición desde allí.</p></div><div class="modal-actions"><button id="alarmCancel" class="secondary-button">Cancelar</button><button id="alarmCreate" class="primary-button">${alarm.systemDateSafe===false?'Abrir alarmas y revisar':'Crear alarma'}</button></div>`);
  $('#alarmCancel').addEventListener('click',closeModal);$('#alarmCreate').addEventListener('click',()=>{const b=$('#alarmCreate');b.disabled=true;b.textContent='Abriendo alarmas…';NexoNative.createSystemAlarm(JSON.stringify(alarm));});
}
function looksLikeJournalQuoteVoice(text){
  const n=normVoiceText(text);
  if(/^(?:guarda|apunta|anota)\s+(?:una\s+)?frase\b/.test(n)||/^frase\s*[:,-]/.test(n))return true;
  if(/^(?:guarda|apunta|anota)\s+(?:una\s+)?cita\b/.test(n)||/^cita\s*[:,-]/.test(n)){
    const sourceCue=/(?:\bde\b|\bautor\b|\bfilosofo\b|\bcancion\b|\blibro\b|\bpelicula\b|\bpoema\b)/.test(n);
    const agendaCue=/(?:\bhoy\b|\bmanana\b|\bpasado manana\b|\blunes\b|\bmartes\b|\bmiercoles\b|\bjueves\b|\bviernes\b|\bsabado\b|\bdomingo\b|\ba las\b|\bmedico\b|\bdentista\b|\breunion\b|\bconsulta\b)/.test(n);
    return sourceCue&&!agendaCue;
  }
  return false;
}
function prepareUniversalCapture(raw){navigate('save');clearCapture();state.capturePinnedType='';state.capturePinnedDate='';$('#captureHeading').textContent='Habla con Nexo';$('#captureText').value=String(raw||'').trim();analyzeCapture({immediate:true,universal:true});}
function routeUniversalVoice(text){
  const raw=String(text||'').trim();if(!raw)return;const route=parseNative(NexoNative.interpretIntent(raw),{intent:'capture'});
  if(route.intent==='alarm'){openAlarmConfirmation(route.alarm||parseNative(NexoNative.parseAlarm(raw),{}));return;}
  if(route.intent==='journal'){openJournalComposer(stripJournalVoicePrefix(raw),looksLikeJournalQuoteVoice(raw)?'quote':'');return;}
  if(route.intent==='medication'){openMedicationComposer(raw);return;}
  if(route.intent==='shopping'){const id=getShoppingListId();handleListVoice(raw,id,true);return;}
  if(route.intent==='parking'){openParkingModal(raw);return;}
  prepareUniversalCapture(raw);
}

function listMatch(entries,spoken){const q=normVoiceText(spoken).replace(/^(?:el|la|los|las)\s+/,'');if(!q)return null;return entries.find(e=>{const x=normVoiceText(e.text).replace(/^(?:el|la|los|las)\s+/,'');return x===q||x.includes(q)||q.includes(x);})||null;}
function handleListVoice(text,listId,shopping){
  if(!listId){toast('No encuentro la lista.');return;}const parsed=parseNative(NexoNative.parseListVoice(text),{}),action=parsed.action||'add',items=Array.isArray(parsed.items)?parsed.items:[],entries=parseNative(NexoNative.getListEntries(listId),[]);
  if(action==='clear_checked'){const n=Number(NexoNative.deleteCheckedListEntries(listId));toast(n?`${n} producto${n===1?'':'s'} comprado${n===1?'':'s'} eliminado${n===1?'':'s'}.`:'No había productos comprados para eliminar.');openList(listId);return;}
  if(action==='replace'){const from=parsed.from||'',to=parsed.to||'',e=listMatch(entries,from);if(e&&to){NexoNative.updateListEntry(Number(e.id),to,shopping);toast(`${from} cambiado por ${to}.`);}else toast('No he encontrado el producto que quieres cambiar.');openList(listId);return;}
  if(action==='add'){
    if(!items.length){toast('No he podido separar productos. Puedes repetirlo más despacio.');openList(listId);return;}
    const pending=[];let restored=0,already=0;
    items.forEach(name=>{const e=listMatch(entries,name);if(!e){pending.push(name);return;}if(e.checked){NexoNative.toggleListEntry(Number(e.id),false);restored++;}else already++;});
    const count=pending.length?Number(NexoNative.addListItems(listId,pending.join('\n'),shopping)):0;
    const parts=[];if(count)parts.push(`${count} nuevo${count===1?'':'s'}`);if(restored)parts.push(`${restored} vuelto${restored===1?'':'s'} a pendiente`);if(already)parts.push(`${already} ya estaba${already===1?'':'n'} en la lista`);
    toast(parts.length?`Lista actualizada: ${parts.join(' · ')}.`:'La lista no ha cambiado.');openList(listId);return;
  }
  let changed=0;items.forEach(name=>{const e=listMatch(entries,name);if(!e)return;if(action==='remove'){if(NexoNative.deleteListEntry(Number(e.id)))changed++;}else if(action==='check'){if(NexoNative.toggleListEntry(Number(e.id),true))changed++;}else if(action==='uncheck'){if(NexoNative.toggleListEntry(Number(e.id),false))changed++;}});
  const label=action==='remove'?'eliminado':action==='check'?'marcado como comprado':'marcado como pendiente';toast(changed?`${changed} elemento${changed===1?'':'s'} ${label}${changed===1?'':'s'}.`:'No he encontrado esos productos en la lista.');openList(listId);
}

function confirmGeneric(title,text,fn,actionLabel='Eliminar'){$('#confirmTitle').textContent=title;$('#confirmText').textContent=text;$('#confirmDelete').textContent=actionLabel;$('#confirmModal').classList.remove('hidden');state.confirmAction=()=>{$('#confirmModal').classList.add('hidden');fn();};}

// -------------------- Nexo 2.1 · Medicamentos --------------------
function medicationScheduleLabel(m){
  const mode=m.scheduleMode||'once';
  if(mode==='interval'){const h=Number(m.intervalMinutes||0)/60;return h?`Cada ${Number.isInteger(h)?h:h.toFixed(1)} h`:'Intervalo sin definir';}
  if(mode==='times'){const t=Array.isArray(m.times)?m.times:[];return t.length?`A las ${t.join(' · ')}`:'Horas por confirmar';}
  return 'Una sola toma';
}
function medicationStatusLabel(s){return s==='taken'?'Tomado':s==='skipped'?'Omitido':s==='snoozed'?'Pospuesto':'Pendiente';}
function medicationCard(m){
  const next=Number(m.nextDoseAt||0),active=m.active!==false;
  return `<div class="med-card ${active?'':'med-inactive'}"><button type="button" class="med-card-open" data-open-medication="${m.id}"><div class="med-card-top"><div><div class="med-name">${esc(m.name||'Medicamento')}</div><div class="med-meta">${m.dose?`${esc(m.dose)} · `:''}${esc(medicationScheduleLabel(m))}</div></div><span class="med-voice">${m.voiceEnabled?'🔊':'🔕'}</span></div>${m.instructions?`<div class="med-instructions">${esc(m.instructions)}</div>`:''}<div class="med-next">${active&&next?`Próxima toma · <strong>${esc(fmtDate(next,true))}</strong>`:active?'Sin próxima toma programada':'Tratamiento finalizado'}</div></button><div class="record-card-actions">${recordIconAction('edit','Editar medicación',`data-open-medication="${m.id}"`)}${recordIconAction('share','Compartir medicación',`data-med-share="${m.id}"`)}${recordIconAction('trash','Eliminar medicación',`data-med-delete="${m.id}"`,'icon-action--danger')}</div></div>`;
}
function bindMedicationButtons(root=document){
  root.querySelectorAll('[data-open-medication]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openMedication(Number(b.dataset.openMedication));}));
  root.querySelectorAll('[data-med-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();shareMedicationCard(Number(b.dataset.medShare));}));
  root.querySelectorAll('[data-med-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const id=Number(b.dataset.medDelete),m=parseNative(NexoNative.getMedication(id),{});confirmGeneric('Eliminar medicación','Se eliminará “'+(m.name||'este medicamento')+'” y sus avisos.',()=>{if(NexoNative.deleteMedication(id)){toast('Medicación eliminada');renderMedicationsPanel();}else toast('No se pudo eliminar');},'Eliminar');}));
  root.querySelectorAll('[data-med-dose]').forEach(b=>b.addEventListener('click',()=>{const [id,at]=b.dataset.medDose.split(':').map(Number),status=b.dataset.medStatus;if(NexoNative.setMedicationDoseStatus(id,at,status)){toast(status==='taken'?'Toma marcada como realizada':'Toma marcada como omitida');renderMedicationsPanel();renderMedicationToday();}}));
}

function renderMedicationToday(){
  const box=$('#medicationToday');if(!box)return;const doses=parseNative(NexoNative.getMedicationTodayDoses(),[]),now=Date.now();
  const visible=doses.filter(d=>d.status!=='skipped').sort((a,b)=>Number(a.scheduledAt)-Number(b.scheduledAt));
  if(!visible.length){box.classList.add('hidden');box.innerHTML='';return;}
  const next=visible.filter(d=>d.status!=='taken'&&Number(d.scheduledAt)>=now-60*60*1000).slice(0,3);const rows=(next.length?next:visible.slice(-2));
  box.innerHTML=`<div class="med-today-head"><div><span><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-pill"></use></svg> Medicación hoy</span><small>${visible.length} toma${visible.length===1?'':'s'} prevista${visible.length===1?'':'s'}</small></div><button id="medTodayOpen" class="text-button">Ver</button></div><div class="med-today-rows">${rows.map(d=>`<div><strong>${esc(fmtTime(d.scheduledAt))}</strong><span>${esc(d.name)}${d.dose?` · ${esc(d.dose)}`:''}</span><small class="med-state ${esc(d.status)}">${esc(medicationStatusLabel(d.status))}</small></div>`).join('')}</div>`;
  box.classList.remove('hidden');$('#medTodayOpen').addEventListener('click',()=>openPanel('medications'));
}

function medicationVoiceNorm(value){
  return String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').replace(/[^a-z0-9: ]+/g,' ').replace(/\s+/g,' ').trim();
}
function medicationVoiceDoseAction(text){
  const n=medicationVoiceNorm(text);
  if(/\b(?:he|ya he|la he|lo he)\s+omitid[oa]\b/.test(n)||/\bmarca(?:r)?\b.*\bcomo\s+omitid[oa]\b/.test(n)||/\bno\s+(?:me\s+)?(?:la|lo)?\s*he\s+tomad[oa]\b/.test(n))return 'skipped';
  if(/\b(?:he|ya he|la he|lo he|me la he|me lo he)\s+tomad[oa]\b/.test(n)||/\bmarca(?:r)?\b.*\bcomo\s+tomad[oa]\b/.test(n))return 'taken';
  return '';
}
function medicationVoiceTimeMinutes(text){
  const n=medicationVoiceNorm(text),m=n.match(/\b(?:a\s+las?\s+)?(\d{1,2})(?::(\d{2}))?\b/);
  if(!m)return -1;
  const h=Number(m[1]),min=Number(m[2]||0);
  return h>=0&&h<=23&&min>=0&&min<=59?h*60+min:-1;
}
function medicationDoseVoiceCandidates(text){
  const rows=parseNative(NexoNative.getMedicationTodayDoses(),[]).filter(d=>d.status==='pending'||d.status==='snoozed');
  if(!rows.length)return [];
  const n=medicationVoiceNorm(text),wantedMinutes=medicationVoiceTimeMinutes(text);
  const named=rows.filter(d=>{
    const name=medicationVoiceNorm(d.name||'');
    if(!name)return false;
    if(n.includes(name))return true;
    const tokens=name.split(' ').filter(t=>t.length>=4);
    return tokens.length&&tokens.some(t=>n.includes(t));
  });
  let candidates=named.length?named:rows;
  if(wantedMinutes>=0){
    const byTime=candidates.filter(d=>{const dt=new Date(Number(d.scheduledAt||0));return dt.getHours()*60+dt.getMinutes()===wantedMinutes;});
    if(byTime.length)candidates=byTime;
  }
  if(!named.length&&wantedMinutes<0&&rows.length!==1)return [];
  return candidates;
}
function applyMedicationDoseVoiceStatus(dose,status){
  if(!dose)return false;
  const ok=!!NexoNative.setMedicationDoseStatus(Number(dose.medicationId),Number(dose.scheduledAt),status);
  if(ok){
    toast(status==='taken'?`${dose.name} · toma marcada como realizada.`:`${dose.name} · toma marcada como omitida.`);
    renderMedicationsPanel();
    renderMedicationToday();
  }else toast('No se pudo actualizar esa toma.');
  return ok;
}
function chooseMedicationDoseForVoice(candidates,status){
  if(!candidates.length){toast('No he podido identificar qué toma quieres actualizar.');return;}
  if(candidates.length===1){applyMedicationDoseVoiceStatus(candidates[0],status);return;}
  openModal(status==='taken'?'¿Qué toma has realizado?':'¿Qué toma quieres marcar como omitida?',`<div class="scope-buttons medication-dose-choice">${candidates.map((d,i)=>`<button type="button" class="secondary-button" data-med-dose-choice="${i}"><strong>${esc(fmtTime(d.scheduledAt))}</strong><span>${esc(d.name||'Medicamento')}</span></button>`).join('')}</div>`);
  $$('[data-med-dose-choice]').forEach(b=>b.addEventListener('click',()=>{const d=candidates[Number(b.dataset.medDoseChoice)];closeModal();applyMedicationDoseVoiceStatus(d,status);}));
}
function applyMedicationVoiceToEditor(text){
  const n=medicationVoiceNorm(text),parsed=parseNative(NexoNative.parseMedication(text),{});
  let changed=false;
  const set=(sel,value)=>{const el=$(sel);if(el&&value!==undefined&&value!==null&&String(value)!==''){el.value=String(value);changed=true;}};
  if(parsed.dose&&(/\bdosis\b/.test(n)||/\b(?:mg|mcg|ml|gramos?|miligramos?|comprimidos?|capsulas?|pastillas?)\b/.test(n)))set('#medDose',parsed.dose);
  if(parsed.instructions)set('#medInstructions',parsed.instructions);
  if(parsed.reason)set('#medReason',parsed.reason);
  if(parsed.scheduleMode){$('#medMode').value=parsed.scheduleMode;changed=true;$('#medMode').dispatchEvent(new Event('change'));}
  if(parsed.intervalMinutes)set('#medInterval',Number(parsed.intervalMinutes)/60);
  if(Array.isArray(parsed.times)&&parsed.times.length){
    const current=Array.from(document.querySelectorAll('#medTimesPicker input[type="time"]')).map(x=>x.value).filter(Boolean);
    const merged=[...new Set([...current,...parsed.times])].sort();
    const picker=$('#medTimesPicker');
    if(picker){
      picker.innerHTML=merged.map((value,index)=>`<div class="multi-time-row"><input type="time" value="${escAttr(value)}" aria-label="Hora de toma ${index+1}"><button type="button" class="multi-time-remove" data-med-time-remove="${index}" aria-label="Eliminar hora">×</button></div>`).join('');
      Array.from(document.querySelectorAll('[data-med-time-remove]')).forEach(button=>button.addEventListener('click',()=>button.closest('.multi-time-row')?.remove()));
      changed=true;
    }
  }
  if(parsed.startDate&&/\b(?:empieza|comienza|inicio|desde)\b/.test(n))set('#medStartDate',parsed.startDate);
  if(parsed.startTime&&/\b(?:primera|empieza|comienza|inicio|desde|a las?)\b/.test(n))set('#medStartTime',parsed.startTime);
  if(parsed.durationDays)set('#medDuration',parsed.durationDays);
  if(parsed.endDate)set('#medEndDate',parsed.endDate);
  if(parsed.name&&/\b(?:se llama|medicamento|medicacion)\b/.test(n))set('#medName',parsed.name);
  if(changed)toast('He actualizado el formulario. Revísalo antes de guardar.');
  else toast('No he identificado qué campo de la pauta quieres cambiar.');
}
function handleMedicationVoice(text){
  const status=medicationVoiceDoseAction(text);
  if(status){
    const candidates=medicationDoseVoiceCandidates(text);
    if(candidates.length){chooseMedicationDoseForVoice(candidates,status);return;}
    toast('No he podido identificar una toma pendiente. Puedes marcarla manualmente.');
    return;
  }
  openMedicationComposer(text);
}
function startMedicationEditorVoice(){
  state.medicationVoiceEditor=true;
  startManagedDictation('medication',{title:'HABLAR CON MEDICACIÓN',hint:'Di el cambio que quieres aplicar a este formulario. Nexo lo rellenará para que lo revises antes de guardar.'});
}
function startMedicationVoice(base=''){
  state.medicationVoiceEditor=false;
  startManagedDictation('medication',{
    base:String(base||'').trim(),
    title:'HABLAR CON MEDICACIÓN',
    hint:'Puedes dictar una pauta nueva o decir qué toma has realizado u omitido. Nexo te mostrará cualquier cambio de pauta antes de guardarlo.'
  });
}
function parseMedicationTimes(raw){return [...new Set(String(raw||'').split(/[;,\s]+/).map(x=>x.trim()).filter(Boolean).map(x=>{if(/^\d{1,2}:\d{2}$/.test(x)){const [h,m]=x.split(':').map(Number);if(h>=0&&h<24&&m>=0&&m<60)return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`;}return '';}).filter(Boolean))].sort();}
function medicationEndAt(startDate,durationDays,endDate){if(endDate)return endOfDayMs(endDate);const n=Number(durationDays||0);if(!startDate||!Number.isFinite(n)||n<=0)return 0;const d=new Date(`${startDate}T23:59:59`);d.setDate(d.getDate()+Math.max(0,Math.floor(n)-1));return d.getTime();}
function medicationDateValue(ms){return ms?isoFromMs(ms).date:'';}
function medicationTimeValue(ms){return ms?isoFromMs(ms).time:'';}

function openMedicationComposer(prefill='',existing=null){
  const parsed=prefill?parseNative(NexoNative.parseMedication(prefill),{}):{};const m=existing||{};const editing=!!m.id;
  const mode=m.scheduleMode||parsed.scheduleMode||'';const times=(m.times||parsed.times||[]);const startDate=editing?medicationDateValue(m.startAt):(parsed.startDate||isoDateLocal(new Date()));const startTime=editing?medicationTimeValue(m.startAt):(parsed.startTime||'');
  const intervalHours=editing&&m.intervalMinutes?Number(m.intervalMinutes)/60:(parsed.intervalMinutes?Number(parsed.intervalMinutes)/60:'');const endDate=editing?medicationDateValue(m.endAt):(parsed.endDate||'');const lead=editing?Number(m.leadMinutes??5):Number(parsed.leadMinutes??5);const voice=editing?m.voiceEnabled!==false:parsed.voiceEnabled!==false;
  const warnings=(parsed.warnings||[]);
  const medHistory=editing?parseNative(NexoNative.getMedicationDoses(m.id,Date.now()-7*24*60*60*1000,Date.now()+24*60*60*1000),[]).filter(d=>Number(d.scheduledAt)<=Date.now()).slice(-20).reverse():[];
  const medHistoryHtml=medHistory.length?`<details class="details-box med-dose-history"><summary>Últimas tomas registradas</summary><div class="med-history-list">${medHistory.map(d=>`<div><strong>${esc(fmtDate(d.scheduledAt,true))}</strong><span>${d.status==='pending'?'Sin registrar':esc(medicationStatusLabel(d.status))}</span></div>`).join('')}</div></details>`:'';
  openModal(editing?'Editar medicamento':'Nuevo medicamento',`<div class="medication-compose modal-form"><div class="med-safety"><strong>Nexo recuerda la pauta que tú introduces.</strong><span>No prescribe, no modifica dosis y no sustituye las indicaciones de un profesional sanitario.</span></div>${!editing?`<label>Texto original <span class="optional">(opcional, para que Nexo rellene los campos)</span><textarea id="medSource" rows="4" placeholder="Ej.: Amoxicilina 500 mg cada 8 horas durante 7 días empezando hoy a las 14:00">${esc(prefill)}</textarea></label>`:''}<div id="medWarnings" class="med-warnings ${warnings.length?'':'hidden'}">${warnings.map(w=>`<div>⚠ ${esc(w)}</div>`).join('')}</div><div class="form-grid"><label>Medicamento<input id="medName" type="text" value="${escAttr(m.name||parsed.name||'')}" placeholder="Nombre"></label><label>Dosis <span class="optional">(solo la que tú indiques)</span><input id="medDose" type="text" value="${escAttr(m.dose||parsed.dose||'')}" placeholder="Ej.: 500 mg"></label><label>Pauta<select id="medMode"><option value="" ${!mode?'selected':''}>Selecciona la pauta…</option><option value="interval" ${mode==='interval'?'selected':''}>Cada X horas</option><option value="times" ${mode==='times'?'selected':''}>Horas concretas</option><option value="once" ${mode==='once'?'selected':''}>Una sola toma</option></select></label><label id="medIntervalLabel">Cada cuántas horas<input id="medInterval" type="number" min="1" max="48" step="1" value="${escAttr(intervalHours)}" placeholder="Ej.: 8"></label><div id="medTimesLabel" class="hidden form-span-2 multi-time-field"><div class="multi-time-heading"><span class="form-field-label">Horas de toma</span><span class="optional">Selecciona cada hora con el reloj</span></div><div id="medTimesPicker" class="multi-time-picker"></div><button id="medAddTime" type="button" class="secondary-button multi-time-add">+ Añadir hora</button></div><label>Fecha de inicio<div class="date-wrap"><input id="medStartDate" type="date" value="${escAttr(startDate)}"></div></label><label id="medStartTimeLabel">Primera toma<div class="date-wrap"><input id="medStartTime" type="time" value="${escAttr(startTime)}"></div></label><label>Duración en días <span class="optional">(opcional)</span><input id="medDuration" type="number" min="1" max="3650" value="${escAttr(editing?'':parsed.durationDays||'')}" placeholder="Ej.: 7"></label><label>O hasta fecha <span class="optional">(opcional)</span><div class="date-wrap"><input id="medEndDate" type="date" value="${escAttr(endDate)}"></div></label><label class="form-span-2">Indicaciones <span class="optional">(solo si tú las has indicado)</span><input id="medInstructions" type="text" value="${escAttr(m.instructions||parsed.instructions||'')}" placeholder="Ej.: en ayunas, con comida"></label><label class="form-span-2">Motivo <span class="optional">(opcional)</span><input id="medReason" type="text" value="${escAttr(m.reason||parsed.reason||'')}"></label><label>Avisos<input type="text" value="-5, 0, +5, +10, +15 y +20 min" readonly aria-label="Secuencia fija de avisos"></label><label>Voz<span class="switch-row"><input id="medVoice" type="checkbox" ${voice?'checked':''}><span>Leer aviso en voz alta</span></span><button id="medTestVoice" type="button" class="mini-button med-test-voice"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-speaker"></use></svg> Probar voz</button></label>${editing?`<label>Tratamiento<span class="switch-row"><input id="medActive" type="checkbox" ${m.active!==false?'checked':''}><span>Activo</span></span></label>`:''}</div>${medHistoryHtml}<div class="med-confirm-note">Antes de guardar, revisa especialmente <strong>medicamento, dosis, horas y fecha de inicio</strong>. El micrófono flotante puede completar este formulario, pero nunca lo guarda automáticamente.</div></div><div class="modal-actions wrap-actions"><button id="medSave" class="primary-button">${editing?'Guardar cambios':'Confirmar tratamiento'}</button>${editing?'<button id="medDelete" class="danger-button">Eliminar tratamiento</button>':''}</div>`);
  state.medicationEditor={id:Number(m.id||0),editing};
  document.body.classList.add('medication-editor-open');
  requestAnimationFrame(updateFloatingActions);
  const medTimeValues=()=>Array.from(document.querySelectorAll('#medTimesPicker input[type="time"]')).map(input=>input.value.trim());
  const readMedTimes=()=>[...new Set(medTimeValues().filter(Boolean))];
  const renderMedTimes=(values=[])=>{
    let rows=(Array.isArray(values)?values:[]).map(value=>String(value||'').trim()).filter(Boolean);
    if(!rows.length)rows=['08:00'];
    $('#medTimesPicker').innerHTML=rows.map((value,index)=>`<div class="multi-time-row"><input type="time" value="${escAttr(value)}" aria-label="Hora de toma ${index+1}"><button type="button" class="multi-time-remove" data-med-time-remove="${index}" aria-label="Eliminar hora">×</button></div>`).join('');
    Array.from(document.querySelectorAll('[data-med-time-remove]')).forEach(button=>button.addEventListener('click',()=>{
      const next=medTimeValues();next.splice(Number(button.dataset.medTimeRemove),1);renderMedTimes(next);
    }));
  };
  renderMedTimes(times);
  $('#medAddTime').addEventListener('click',()=>{
    const next=medTimeValues().filter(Boolean);
    next.push(next.length?addOneHourTime(next[next.length-1]):'08:00');
    renderMedTimes(next);
  });
  const toggle=()=>{const v=$('#medMode').value;$('#medIntervalLabel').classList.toggle('hidden',v!=='interval');$('#medTimesLabel').classList.toggle('hidden',v!=='times');$('#medStartTimeLabel').classList.toggle('hidden',v==='times'||!v);};toggle();$('#medMode').addEventListener('change',toggle);
  if($('#medSource')){let timer;$('#medSource').addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(()=>{const a=parseNative(NexoNative.parseMedication($('#medSource').value),{});if(a.name)$('#medName').value=a.name;if(a.dose)$('#medDose').value=a.dose;if(a.scheduleMode){$('#medMode').value=a.scheduleMode;toggle();}if(a.intervalMinutes)$('#medInterval').value=Number(a.intervalMinutes)/60;if((a.times||[]).length)renderMedTimes(a.times);if(a.startDate)$('#medStartDate').value=a.startDate;if(a.startTime)$('#medStartTime').value=a.startTime;if(a.durationDays)$('#medDuration').value=a.durationDays;if(a.endDate)$('#medEndDate').value=a.endDate;if(a.instructions)$('#medInstructions').value=a.instructions;if(a.reason)$('#medReason').value=a.reason;const ws=a.warnings||[];$('#medWarnings').innerHTML=ws.map(w=>`<div>⚠ ${esc(w)}</div>`).join('');$('#medWarnings').classList.toggle('hidden',!ws.length);},300);});}
  $('#medTestVoice').addEventListener('click',()=>NexoNative.testMedicationVoice($('#medName').value.trim()||'el medicamento'));
  $('#medSave').addEventListener('click',()=>{
    const name=$('#medName').value.trim(),scheduleMode=$('#medMode').value,startDate=$('#medStartDate').value,startTime=$('#medStartTime').value,times=readMedTimes(),intervalHours=Number($('#medInterval')?.value||0);
    if(!name){toast('Indica el nombre del medicamento.');return;}if(!scheduleMode){toast('Selecciona la pauta del medicamento.');return;}if(!startDate){toast('Indica la fecha de inicio.');return;}if(scheduleMode==='interval'&&(!intervalHours||intervalHours<=0)){toast('Indica cada cuántas horas debe repetirse.');return;}if(scheduleMode==='interval'&&!startTime){toast('Indica la hora de la primera toma.');return;}if(scheduleMode==='once'&&!startTime){toast('Indica la hora de la toma.');return;}if(scheduleMode==='times'&&!times.length){toast('Indica las horas concretas de las tomas.');return;}
    const startAt=dateTimeToMs(startDate,scheduleMode==='times'?'00:00':startTime),endAt=medicationEndAt(startDate,$('#medDuration').value,$('#medEndDate').value);if(scheduleMode==='once'&&!editing&&startAt<=Date.now()){toast('La hora de esta única toma ya ha pasado. Revisa fecha y hora.');return;}if(endAt&&endAt<startAt){toast('La fecha final debe ser posterior al inicio.');return;}
    const payload={name,dose:$('#medDose').value.trim(),reason:$('#medReason').value.trim(),instructions:$('#medInstructions').value.trim(),scheduleMode,intervalMinutes:scheduleMode==='interval'?Math.round(intervalHours*60):0,times:scheduleMode==='times'?times:[],startAt,endAt,leadMinutes:5,voiceEnabled:$('#medVoice').checked,active:editing?$('#medActive').checked:true,sourceText:$('#medSource')?$('#medSource').value.trim():(m.sourceText||'')};
    let ok;if(editing){payload.id=m.id;ok=!!NexoNative.updateMedication(JSON.stringify(payload));}else ok=Number(NexoNative.saveMedication(JSON.stringify(payload)))>0;
    if(!ok){toast('No se pudo guardar. Revisa la pauta.');return;}closeModal();if(!editing&&state.screen==='save')clearCapture();toast(editing?'Tratamiento actualizado':(NexoNative.canScheduleExactAlarms()?'Tratamiento guardado · aviso 5 min antes':'Tratamiento guardado · activa alarmas exactas en Medicamentos para máxima puntualidad'),'','',5000);if(state.screen==='panel'&&state.lastPanel==='medications')renderMedicationsPanel();else if(state.screen==='save'){navigateBack();}else refreshCurrent();
  });
  if(editing)$('#medDelete').addEventListener('click',()=>confirmGeneric('Eliminar tratamiento',`Se eliminará ${m.name} y su historial de tomas.`,()=>{if(NexoNative.deleteMedication(m.id)){closeModal();toast('Tratamiento eliminado');if(state.screen==='panel'&&state.lastPanel==='medications')renderMedicationsPanel();else refreshCurrent();}}));
}

function openMedication(id){const m=parseNative(NexoNative.getMedication(id),{});if(m.id)openMedicationComposer('',m);}
function medicationDoseRows(rows){if(!rows.length)return '<div class="empty-state">No hay tomas previstas para hoy.</div>';return `<div class="med-dose-list">${rows.map(d=>`<div class="med-dose-row ${esc(d.status)}"><div class="med-dose-time">${esc(fmtTime(d.scheduledAt))}</div><div class="med-dose-main"><strong>${esc(d.name)}</strong><span>${d.dose?esc(d.dose):'Dosis no indicada'} · ${esc(medicationStatusLabel(d.status))}</span></div>${d.status==='pending'||d.status==='snoozed'?`<div class="med-dose-actions"><button class="mini-button primary" data-med-dose="${d.medicationId}:${d.scheduledAt}" data-med-status="taken">✓ Tomado</button><button class="mini-button" data-med-dose="${d.medicationId}:${d.scheduledAt}" data-med-status="skipped">Omitir</button></div>`:''}</div>`).join('')}</div>`;}
function renderMedicationsPanel(){
  state.lastPanel='medications';
  const active=parseNative(NexoNative.getMedications('','active'),[]),history=parseNative(NexoNative.getMedications('','history'),[]),today=parseNative(NexoNative.getMedicationTodayDoses(),[]);
  $('#panelTitle').textContent='Medicación';
  $('#panelSubtitle').textContent='Tus tratamientos y tomas de hoy.';
  $('#panelAction').innerHTML='';
  const exact=!!NexoNative.canScheduleExactAlarms();
  $('#panelContent').innerHTML=`${!exact?'<div class="med-exact-warning"><strong>Mejorar puntualidad de avisos</strong><span>Android puede retrasar alarmas en ahorro de energía. Permite alarmas exactas para que Nexo intente avisarte a la hora prevista.</span><button id="medExactBtn" class="secondary-button">Permitir alarmas exactas</button></div>':''}<button id="medVoiceInfo" type="button" class="med-module-note med-module-note--compact"><span class="med-module-note-icon"><svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-speaker"></use></svg></span><span class="med-module-note-copy"><strong>Avisos por voz</strong><small>5 min antes · repetición hasta 30 min</small></span><span class="med-module-note-chevron">›</span></button><div class="section-title"><h2>Hoy</h2></div>${medicationDoseRows(today)}<div class="section-title"><h2>Tratamientos activos</h2></div><div class="stack">${active.length?active.map(medicationCard).join(''):'<div class="empty-state">No tienes tratamientos activos.</div>'}</div>${history.length?`<div class="section-title"><h2>Historial</h2></div><div class="stack">${history.map(medicationCard).join('')}</div>`:''}`;
  if($('#medExactBtn'))$('#medExactBtn').addEventListener('click',()=>NexoNative.requestExactAlarms());
  $('#medVoiceInfo').addEventListener('click',()=>openModal('Avisos por voz',`<div class="med-safety med-voice-explanation"><strong>Nexo te avisa según la pauta que tú has guardado.</strong><span>El primer aviso se produce 5 minutos antes. Si la toma sigue pendiente, Nexo vuelve a avisar a la hora prevista y cada 5 minutos, como máximo hasta 30 minutos después.</span><span>Marcar <strong>TOMADO</strong> u <strong>OMITIR</strong> detiene los avisos de esa toma.</span></div>`));
  bindMedicationButtons($('#panelContent'));
  updateFloatingActions();
}

window.NexoUI={
  receiveImportPreview(raw){
    const p=parseNative(raw,{}),entity=p.entity||'item',typeLabel=entity==='list'?'Lista':entity==='journal'?'Cuaderno':'Elemento';
    const date=p.dueAt?fmtDate(Number(p.dueAt),p.hasTime!==false):'',where=p.locationText||'';
    openModal(`Recibido · ${typeLabel}`,`<div class="restore-preview"><div class="restore-check">↘</div><h3>${esc(p.title||'Contenido de Nexo')}</h3>${p.body?`<p>${esc(p.body)}</p>`:''}<div class="restore-stats">${entity==='list'?`<span>${Number(p.count||0)} elementos</span>`:''}${p.category?`<span>${esc(p.category)}</span>`:''}${date?`<span>${esc(date)}</span>`:''}${where?`<span>${esc(where)}</span>`:''}</div><div class="small muted">${p.format==='ics'?'Evento recibido en formato calendario (.ics).':'Contenido recibido en formato Nexo.'}</div><div class="modal-actions"><button id="sharedCancel" class="secondary-button">Cancelar</button><button id="sharedConfirm" class="primary-button">Añadir a Nexo</button></div></div>`);
    $('#sharedCancel').addEventListener('click',()=>{NexoNative.cancelSharedImport();closeModal();});
    $('#sharedConfirm').addEventListener('click',()=>{const b=$('#sharedConfirm');b.disabled=true;b.textContent='Añadiendo…';NexoNative.confirmSharedImport();});
  },
  onSharedImportDone(raw){const r=parseNative(raw,{});closeModal();toast(`${r.title||'Contenido'} añadido a Nexo`,'',null,5000);if(r.entity==='list'&&r.id){openPanel('lists');setTimeout(()=>openList(Number(r.id)),80);}else if(r.entity==='journal'&&r.id){const j=parseNative(NexoNative.getJournal(Number(r.id)),{});openPanel(j.kind==='quote'?'quotes':'journal');setTimeout(()=>openJournal(Number(r.id)),80);}else if(r.entity==='item'&&r.id){refreshCurrent();setTimeout(()=>openItem(Number(r.id)),80);}else refreshCurrent();},
  onSharedImportError(msg){closeModal();toast(msg||'No se pudo importar el contenido.','',null,6000);},
  onHolidayCalendarLoaded(raw){const r=parseNative(raw,{}),y=Number(r.year||0);if(!y)return;state.holidayLoading.delete(y);state.holidayYears[y]=r;if(state.screen==='calendar')renderCalendar();},
  onRecordingStateChanged(raw){if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();if(state.screen==='today')renderToday();updateFloatingActions();},
  onRecordingError(msg){toast(msg||'No se pudo completar la acción de grabación.','',null,5500);if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();},
  onRecordingPlaybackStarted(id){state.playingRecordingId=String(id||'');refreshRecordingSurface();setTimeout(startRecordingPlaybackPolling,80);},
  onRecordingPlaybackStopped(id){stopRecordingPlaybackPolling();if(!id||state.playingRecordingId===String(id))state.playingRecordingId='';refreshRecordingSurface();},
  onTranscriptionModelProgress(raw){const x=parseNative(raw,{}),el=$('#transcriptionModelProgress');if(el)el.textContent=`${x.message||'Descargando…'} ${Number(x.progress||0)}%`;},
  onTranscriptionModelReady(raw){toast('Modelo de transcripción preparado');if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();},
  onTranscriptionModelError(msg){toast(msg||'No se pudo descargar el modelo.','',null,7000);if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();},
  onTranscriptionStarted(id){startTranscriptionPolling();},
  onTranscriptionError(msg){toast(msg||'No se pudo iniciar la transcripción.','',null,7000);if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();},
  receiveShare(raw){
    const p=parseNative(raw,{});
    openInboxCaptureEditor(p,true);
  },
  reviewInboxCaptureFromNotification(id){
    reviewInboxCapture(Number(id));
  },
  onNotificationAccessChanged(){
    if(state.screen==='more')renderCommunicationCaptureSettings();
  },
  onCalendarAccessChanged(){
    if(state.screen==='more')renderCommunicationCaptureSettings();
    if(state.screen==='today')renderToday();
  },
  onInputHubChanged(){
    if(state.screen==='more')renderCommunicationCaptureSettings();
    renderToday();
    if(state.screen==='panel'&&state.lastPanel==='inbox')renderInboxPanel();
  },
  onFileChosen(raw){const f=parseNative(raw,{});state.attachment=f;$('#attachmentChip').textContent=`Adjunto: ${f.name||'archivo'}`;$('#attachmentChip').classList.remove('hidden');if(!$('#captureText').value.trim())$('#captureText').value=f.name||'Documento';analyzeCapture({immediate:true});},
  onDocumentFileChosen(raw){
    const result=parseNative(raw,{}),editor=state.documentEditor;
    if(!editor||result.request!==editor.request){if(result.file?.path)NexoNative.discardDocumentFile(result.file.path);return;}
    editor.busy=false;
    if(result.file?.path){
      if(editor.file?.path&&editor.file.path!==editor.originalPath)NexoNative.discardDocumentFile(editor.file.path);
      editor.file=result.file;
    }
    renderDocumentFile();
    if(result.error)showDocumentError(result.error);
  },
  onDocumentActionResult(message){toast(message||'No se pudo completar la acción.', '',null,5500);},
  onVoiceTranscribed(text){
    const spoken=String(text||'').trim(),managedBase=managedVoiceFullText(true);
    $('#voiceOverlay').classList.add('hidden');
    if(state.voiceMode==='universal'){const combined=[managedBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();routeUniversalVoice(combined);return;}
    if(state.voiceMode==='capture-edit'){const combined=[managedBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();applyCaptureVoiceText(combined);return;}
    if(state.voiceMode==='list'){const id=state.managedVoiceListId||state.currentList,shopping=state.managedVoiceShopping,combined=[managedBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();handleListVoice(combined,id,shopping);return;}
    if(state.voiceMode==='journal'){const kind=state.journalVoiceKind,journalId=state.journalVoiceId,journalTitle=state.journalVoiceTitle,journalTitleManual=state.journalVoiceTitleManual,journalFavorite=state.journalVoiceFavorite,journalRevisit=state.journalVoiceRevisit,combined=[managedBase||state.journalVoiceBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();openJournalComposer(combined,kind,journalId,{id:journalId,title:journalTitle,titleManual:journalTitleManual,favorite:journalFavorite,revisit:journalRevisit});return;}
    if(state.voiceMode==='note'){const noteId=state.noteVoiceId,noteTitle=state.noteVoiceTitle,noteTitleManual=state.noteVoiceTitleManual,combined=[managedBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();state.noteVoiceId=0;state.noteVoiceTitle='';state.noteVoiceTitleManual=false;openNoteComposer(noteId,combined,noteTitle,noteTitleManual);return;}
    if(state.voiceMode==='medication'){const editor=!!state.medicationVoiceEditor,combined=[managedBase||state.medicationVoiceBase,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();resetManagedVoice();state.medicationVoiceEditor=false;if(editor)applyMedicationVoiceToEditor(combined);else handleMedicationVoice(combined);return;}
    const pinned=state.voicePinnedDate,type=state.voicePinnedType,base=state.voiceBaseText||'';navigate('save');if(pinned){state.capturePinnedDate=pinned;state.capturePinnedType=type||'';state.captureReturn='today';clearCapture(true);$('#captureDate').value=pinned;if(type)$('#captureType').value=type;refreshCaptureAlertEditor();}else if(!base) clearCapture();state.voicePinnedDate='';state.voicePinnedType='';state.voiceBaseText='';const combined=[base,spoken].filter(Boolean).join(' ').replace(/\s+/g,' ').trim();$('#captureText').value=combined;setInterpretationStatus('working',base?'Dictado añadido · interpretando localmente…':'Voz transcrita · interpretando localmente…');analyzeCapture({immediate:true});toast('Voz transcrita. Revisa lo que Nexo ha entendido.');},
  onVoiceError(msg){$('#voiceOverlay').classList.add('hidden');state.voiceMode='capture';state.journalVoiceBase='';state.journalVoiceKind='';state.medicationVoiceBase='';state.medicationVoiceEditor=false;state.noteVoiceId=0;state.noteVoiceTitle='';state.noteVoiceTitleManual=false;state.voicePinnedDate='';state.voicePinnedType='';state.voiceBaseText='';setInterpretationStatus('idle','Interpretación local preparada');toast(msg);},
  onManagedVoiceFallback(msg){$('#voiceOverlay').classList.add('hidden');toast(msg||'Usando el dictado estándar de Android.');},
  onManagedVoiceState(kind){if($('#voiceOverlay').classList.contains('hidden'))return;const label=kind==='processing'?'PROCESANDO UNA PAUSA…':kind==='starting'?'PREPARANDO MICRÓFONO…':'ESCUCHANDO…';$('#managedVoiceStatus').textContent=label;},
  onManagedVoicePartial(text){state.managedVoicePartial=String(text||'').trim();updateManagedVoiceTranscript();},
  onManagedVoiceSegment(text){const value=String(text||'').trim();if(value&&state.managedVoiceSegments[state.managedVoiceSegments.length-1]!==value)state.managedVoiceSegments.push(value);state.managedVoicePartial='';updateManagedVoiceTranscript();},
  onManagedVoiceStopped(){finalizeManagedDictation();},
  onManagedVoiceError(msg){$('#voiceOverlay').classList.add('hidden');resetManagedVoice();state.medicationVoiceEditor=false;state.noteVoiceId=0;state.noteVoiceTitle='';state.noteVoiceTitleManual=false;toast(msg||'No se pudo continuar el dictado.');},
  onSystemAlarmCreated(msg){closeModal();toast(msg||'Alarma enviada al sistema del teléfono.','',null,5000);},
  onSystemAlarmError(msg){if($('#alarmCreate')){$('#alarmCreate').disabled=false;$('#alarmCreate').textContent='Intentar de nuevo';}toast(msg||'No se pudo crear la alarma.','',null,6000);},
  onNexoFamiliaLinked(raw){
    const r=parseNative(raw,{});
    toast(`Nexo Familia vinculado${r.email?` · ${r.email}`:''}.`,'',null,4500);
    if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();
  },
  onNexoFamiliaSyncDone(raw){
    const r=parseNative(raw,{}),inserted=Number(r.inserted||0),existing=Number(r.alreadyExisting||0),first=!!r.firstBaseline,removed=Number(r.removedLocalAppointments||0);
    if(first){
      toast(`Migración inicial completada: ${inserted} evento${inserted===1?'':'s'} importado${inserted===1?'':'s'}${removed?` · agenda anterior sustituida (${removed})`:''}.`,'',null,7500);
    }else if(inserted){
      toast(`${inserted} evento${inserted===1?'':'s'} nuevo${inserted===1?'':'s'} importado${inserted===1?'':'s'}${existing?` · ${existing} ya existían`:''}.`,'',null,6500);
    }else{
      toast('Nexo está actualizado. No hay eventos nuevos para importar.','',null,5000);
    }
    refreshCurrent();
    if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();
  },
  onNexoFamiliaCloudError(msg){
    toast(msg||'No se pudo conectar con Nexo Familia.','',null,7000);
    if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();
  },
  onNexoFamiliaSessionExpired(raw){
    toast('La sesión de Nexo Familia ha caducado. Vuelve a vincularla.','',null,7000);
    if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();
  },
  onNexoFamiliaDisconnected(raw){
    toast('Nexo Familia desvinculado. Los eventos importados se mantienen.','',null,5000);
    if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();
  },
  onNexoFamiliaImportDone(raw){const r=parseNative(raw,{}),n=Number(r.preserved||0),rem=Number(r.reminders||0),skipped=Number(r.skipped||0);toast(`Nexo Familia: ${n} evento${n===1?'':'s'} importado${n===1?'':'s'}${rem?` · ${rem} aviso${rem===1?'':'s'}`:''}${skipped?` · ${skipped} con revisión`:''}.`,'',null,6500);refreshCurrent();if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();},
  onNexoFamiliaRepairDone(raw){const r=parseNative(raw,{}),ok=!!r.complete,v=Number(r.verifiedEvents||0),e=Number(r.expectedEvents||0),vf=Number(r.verifiedFamily||0),ef=Number(r.expectedFamily||0),vs=Number(r.verifiedSelected||0),es=Number(r.expectedSelected||0),vr=Number(r.verifiedReminderRules||0),er=Number(r.expectedReminderRules||0);toast(ok?`Migración reparada: ${v}/${e} eventos · family ${vf}/${ef} · selected ${vs}/${es} · ${vr}/${er} avisos.`:`Reparación incompleta: ${v}/${e} · family ${vf}/${ef} · selected ${vs}/${es} · avisos ${vr}/${er}.`,'',null,8000);refreshCurrent();if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();},
  onNexoFamiliaImportError(msg){toast(msg||'No se pudo importar Nexo Familia.','',null,7000);},
  onBackupExported(raw){const r=parseNative(raw,{});toast('Copia de seguridad creada correctamente.', '', null, 4500);if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();renderMore();},
  onBackupRestorePreview(raw){
    const p=parseNative(raw,{}),st=p.stats||{},when=Number(p.createdAt||0);
    openModal('Restaurar copia',`<div class="restore-preview"><div class="restore-check">✓</div><h3>COPIA VÁLIDA</h3><p>${when?`Creada el <strong>${esc(fmtDate(when,true))}</strong>.`:''}</p><div class="restore-stats"><span>${Number(st.items||0)} elementos</span><span>${Number(st.lists||0)} listas</span><span>${Number(st.journal||0)} Cuaderno</span><span>${Number(st.medications||0)} medicamentos</span><span>${Number(st.attachments||0)} adjuntos</span></div><div class="restore-warning"><strong>Importante</strong><p>Al restaurar, Nexo quedará exactamente con los datos de esta copia. El contenido actual será sustituido. Antes de hacerlo, Nexo guardará automáticamente una copia interna de seguridad.</p></div><div class="modal-actions"><button id="restoreCancelBtn" class="secondary-button">Cancelar</button><button id="restoreConfirmBtn" class="danger-button solid">Restaurar ahora</button></div></div>`);
    $('#restoreCancelBtn').addEventListener('click',closeModal);$('#restoreConfirmBtn').addEventListener('click',()=>{const b=$('#restoreConfirmBtn');b.disabled=true;b.textContent='Restaurando…';NexoNative.confirmRestoreBackup();});
  },
  onBackupRestored(raw){closeModal();toast('Copia restaurada. Recargando Nexo…','',null,5000);setTimeout(()=>window.location.reload(),900);},
  onBackupError(msg){toast(msg||'No se pudo completar la operación de copia.','',null,6000);if(state.screen==='panel'&&state.lastPanel==='backup')renderBackupPanel();},
  onParkingSaved(){closeModal();toast('Aparcamiento guardado');refreshCurrent();},
  onParkingNeedsConfirmation(raw){
    const info=parseNative(raw,{}),accuracy=Math.max(0,Number(info.accuracy||0));
    openModal('Ubicación poco precisa',`<p style="margin-top:0">La mejor ubicación obtenida tiene una precisión aproximada de <strong>${Math.round(accuracy)} m</strong>.</p><p class="muted">Puedes volver a intentarlo para mejorarla o guardarla de todos modos.</p><div class="modal-actions"><button id="parkingRetry" class="secondary-button">Volver a intentar</button><button id="parkingKeep" class="primary-button">Guardar de todos modos</button></div>`);
    $('#parkingRetry').addEventListener('click',()=>{const b=$('#parkingRetry');b.disabled=true;b.textContent='Buscando…';NexoNative.retryParkingLocation();});
    $('#parkingKeep').addEventListener('click',()=>{const b=$('#parkingKeep');b.disabled=true;b.textContent='Guardando…';NexoNative.confirmParkingLocation();});
  },
  onParkingError(msg){closeModal();toast(msg);},
  handleBack(){
    if(!$('#voiceOverlay').classList.contains('hidden')){stopManagedDictation();return true;}
    if(!$('#confirmModal').classList.contains('hidden')){$('#confirmModal').classList.add('hidden');return true;}
    if(!$('#modal').classList.contains('hidden')){closeModal();return true;}
    return navigateBack();
  }
};

function init(){
  const appVersion=String(NexoNative.getAppVersion());
  $$('[data-app-version]').forEach(el=>{el.textContent='v'+appVersion;el.setAttribute('aria-label','Versión '+appVersion);});
  state.displayMode=String(NexoNative.getDisplayMode()||'comfortable');applyDisplayMode(state.displayMode,false);
  CATEGORIES.forEach(c=>{const o=document.createElement('option');o.value=o.textContent=c;$('#captureCategory').appendChild(o);}); $('#captureCategory').value='Otro';
  setInterpretationStatus('idle','Interpretación local preparada');
  $$('[data-nav]').forEach(b=>b.addEventListener('click',()=>navigate(b.dataset.nav,{root:true})));
  $('#settingsBtn').addEventListener('click',()=>navigate('more'));
  const runningVersion=String(NexoNative.getAppVersion()||'');
  if($('#headerVersion'))$('#headerVersion').textContent=runningVersion?'v'+runningVersion:'';
  document.querySelectorAll('[data-settings-section]').forEach(btn=>btn.addEventListener('click',()=>toggleSettingsAccordion(btn.dataset.settingsSection)));
  $('#peopleAddBtn')?.addEventListener('click',()=>openPersonEditor());
  $('#homeSearchInput')?.addEventListener('input',e=>runHomeSearch(e.target.value));
  $('#homeTodayCard')?.addEventListener('click',openHomeToday);
  $('#homeWeekCard')?.addEventListener('click',openHomeWeek);
  $('#homeInboxPulseCard')?.addEventListener('click',()=>openPanel('inbox'));
  $('#homeCalendarCard')?.addEventListener('click',()=>{state.appointmentsWindow='';openPanel('appointments');});
  $('#searchHistorical')?.addEventListener('change',e=>{state.searchHistorical=!!e.target.checked;if($('#searchInput').value.trim())runSearch();});
  $('#globalVoiceFab').addEventListener('click',runFloatingVoice);
  $('#calendarAddFab').addEventListener('click',runEventAddFab);
  $('#captureBackBtn').addEventListener('click',()=>navigateBack());
  $('#startScreen').addEventListener('change',()=>{NexoNative.setStartScreen($('#startScreen').value);toast('Pantalla de apertura actualizada');});
  $('#preferredShoppingList').addEventListener('change',()=>{NexoNative.setPreferredShoppingList(Number($('#preferredShoppingList').value));toast('Acceso a la compra actualizado');});
  $('#notificationAccessBtn')?.addEventListener('click',()=>NexoNative.openNotificationAccessSettings());
  $('#inputHubAutoScan')?.addEventListener('change',()=>{
    const enabled=$('#inputHubAutoScan').checked;
    NexoNative.setInputHubAutoScanEnabled(enabled);
    renderCommunicationCaptureSettings();
    toast(enabled?'Escaneo automático activado':'Modo manual activado · usa Escanear ahora');
  });
  $('#communicationCaptureEnabled')?.addEventListener('change',()=>{NexoNative.setCommunicationCaptureEnabled($('#communicationCaptureEnabled').checked);renderCommunicationCaptureSettings();toast($('#communicationCaptureEnabled').checked?'Captura automática activada':'Captura automática pausada');});
  $('#smsDirectEnabled')?.addEventListener('change',()=>{
    const enabled=$('#smsDirectEnabled').checked;
    NexoNative.setSmsCaptureEnabled(enabled);
    toast(enabled?'Activando lectura directa de SMS…':'Lectura directa de SMS desactivada');
    setTimeout(renderCommunicationCaptureSettings,500);
  });
  $('#gmailConnectBtn')?.addEventListener('click',()=>{
    NexoNative.connectGmail();
    toast('Abriendo Google para conectar Gmail…');
  });
  $('#gmailScanNowBtn')?.addEventListener('click',()=>{
    NexoNative.scanGmailNow();
    toast('Escaneando Gmail…');
    setTimeout(renderCommunicationCaptureSettings,900);
  });
  $('#gmailDisconnectBtn')?.addEventListener('click',()=>{
    NexoNative.disconnectGmail();
    toast('Gmail desconectado');
    setTimeout(renderCommunicationCaptureSettings,400);
  });
  $('#calendarCaptureEnabled')?.addEventListener('change',()=>{
    const enabled=$('#calendarCaptureEnabled').checked;
    NexoNative.setCalendarCaptureEnabled(enabled);
    toast(enabled?'Activando escucha de Calendar…':'Escucha de Calendar desactivada');
    setTimeout(renderCommunicationCaptureSettings,500);
  });
  $('#inputHubScanBtn')?.addEventListener('click',()=>{
    NexoNative.scanInputHubNow();
    toast('Buscando nuevas entradas…');
    setTimeout(renderCommunicationCaptureSettings,900);
  });
  $('#resetMedicationMessages')?.addEventListener('click',()=>{NexoNative.resetMedicationMessages();renderMedicationMessageSettings();toast('Frases restauradas');});
  $('#calendarMonthPickerBtn').addEventListener('click',openCalendarMonthPicker);
  $('#searchFilter').addEventListener('change',()=>runSearch());
  $('#captureText').addEventListener('input',()=>analyzeCapture());
  bindEventLocationField('#captureLocation','#captureMapsUrl','#captureMapPreview',()=>markCaptureManual('location','mapsUrl'));
  const captureFieldMap={
    captureTitle:'title',captureDescription:'description',captureCategory:'category',captureLocation:'location',
    capturePriority:'priority',captureRecurrence:'recurrence',captureRecurrenceUntil:'recurrence',captureTimeZone:'timeZone'
  };
  Object.entries(captureFieldMap).forEach(([id,field])=>$('#'+id).addEventListener('change',()=>markCaptureManual(field)));
  $('#captureType').addEventListener('change',()=>{markCaptureManual('type');refreshCaptureAlertEditor();});
  $('#captureVoiceEnabled').addEventListener('change',()=>{markCaptureManual('alerts');if($('#captureVoiceEnabled').checked&&!state.captureAlerts.length&&$('#captureDate').value)setCaptureAlerts([0],true);else refreshCaptureAlertEditor();});
  $('#captureDate').addEventListener('change',()=>{markCaptureManual('dateTime');if(!$('#captureDate').value){state.captureAlerts=[];state.captureSuggestedAlert=null;$('#reminderSuggestion').classList.add('hidden');}const start=$('#captureDate').value;if(start&&(!$('#captureEndDate').value||$('#captureEndDate').value<start))$('#captureEndDate').value=start;refreshCaptureAlertEditor();});
  $('#captureTitle').addEventListener('input',()=>markCaptureManual('title'));
  $('#captureDescription').addEventListener('input',()=>markCaptureManual('description'));
  $('#captureMapsUrl').addEventListener('input',()=>markCaptureManual('mapsUrl'));
  $('#captureEndDate').addEventListener('change',()=>markCaptureManual('dateTime'));
  $('#captureAllDay').addEventListener('change',()=>syncCaptureAllDay(true));
  $('#captureTime').addEventListener('change',()=>{if($('#captureTime').value&&!$('#captureEndTime').value)$('#captureEndTime').value=addOneHourTime($('#captureTime').value);markCaptureManual('dateTime');});
  $('#captureEndTime').addEventListener('change',()=>markCaptureManual('dateTime'));
  $('#addCaptureAlert').addEventListener('click',()=>openAddAlertOverlay(v=>setCaptureAlerts([...state.captureAlerts,v],true)));
  $('#acceptReminderSuggestion').addEventListener('click',()=>{if(state.captureSuggestedAlert===null)return;setCaptureAlerts([...state.captureAlerts,state.captureSuggestedAlert],true);state.captureSuggestedAlert=null;$('#reminderSuggestion').classList.add('hidden');});
  $('#voiceCaptureBtn')?.addEventListener('click',()=>startManagedDictation('capture',{base:$('#captureText').value.trim(),title:'DICTAR EN NEXO',hint:'Habla con normalidad y pulsa TERMINAR cuando acabes.'}));
  $('#saveCaptureBtn').addEventListener('click',saveCapture);$('#clearCaptureBtn').addEventListener('click',()=>clearCapture());$('#chooseFileBtn').addEventListener('click',()=>NexoNative.chooseFile());
  $('#searchBtn').addEventListener('click',()=>runSearch());$('#searchInput').addEventListener('keydown',e=>{if(e.key==='Enter')runSearch();});$$('.query-chips button').forEach(b=>b.addEventListener('click',()=>runSearch(b.textContent)));
  $('#todaySummaryMore')?.addEventListener('click',()=>{state.todaySummaryExpanded=!state.todaySummaryExpanded;renderTodaySummary();});

  $('#journalPromptOpen')?.addEventListener('click',()=>{if(state.journalPick?.id)openJournal(Number(state.journalPick.id));});
  $('#journalPromptFavorite')?.addEventListener('click',e=>{
    e.stopPropagation();
    if(!state.journalPick?.id)return;
    const favorite=!state.journalPick.favorite;
    const ok=NexoNative.updateJournal(JSON.stringify({id:Number(state.journalPick.id),favorite}));
    if(!ok){toast('No se pudo actualizar el favorito');return;}
    state.journalPick.favorite=favorite;
    refreshJournalPromptFavorite();
    toast(favorite?'Añadido a favoritos':'Quitado de favoritos');
    if(state.screen==='panel'&&(state.lastPanel==='journal'||state.lastPanel==='quotes'))renderJournalPanel();
  });
  $('#journalPromptNext')?.addEventListener('click',e=>{e.stopPropagation();renderJournalPrompt(true);});
  $$('[data-display-mode]').forEach(b=>b.addEventListener('click',()=>applyDisplayMode(b.dataset.displayMode,true)));

  $('#calendarPrev').addEventListener('click',()=>changeCalendarMonth(-1));
  $('#calendarNext').addEventListener('click',()=>changeCalendarMonth(1));
  $('#calendarToday').addEventListener('click',()=>{const now=new Date();state.calendarMonth=new Date(now.getFullYear(),now.getMonth(),1);state.calendarSelected=isoDateLocal(now);renderCalendar();});
  attachMonthSwipe($('#calendarGrid'),()=>changeCalendarMonth(-1),()=>changeCalendarMonth(1));
  $('#captureRecurrence').addEventListener('change',()=>{const recurring=$('#captureRecurrence').value!=='none';$('#captureRecurrenceUntilLabel').classList.toggle('hidden',!recurring);});
  $$('.quick-action').forEach(b=>b.addEventListener('click',()=>{const a=b.dataset.action;if(a==='voice')startManagedDictation('universal',{title:'Habla con Nexo'});else if(a==='quick-task')prepareQuickNoteForType('task');else if(a==='shopping-list')ensureShoppingList();else if(a==='parking')openParkingModal();}));
  $$('[data-panel]').forEach(b=>b.addEventListener('click',()=>openPanel(b.dataset.panel)));$('#panelBack').addEventListener('click',()=>navigateBack());
  $('#managedVoiceStop').addEventListener('click',stopManagedDictation);
  $('#modalClose').addEventListener('click',closeModal);$('#modal').addEventListener('click',e=>{if(e.target===$('#modal'))closeModal();});
  $('#confirmCancel').addEventListener('click',()=>{$('#confirmModal').classList.add('hidden');state.confirmAction=null;});
  $('#confirmDelete').addEventListener('click',()=>{const fn=state.confirmAction;state.confirmAction=null;if(fn)fn();});
  $('#confirmModal').addEventListener('click',e=>{if(e.target===$('#confirmModal')){$('#confirmModal').classList.add('hidden');state.confirmAction=null;}});
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState==='hidden'&&state.noteEditor)flushNoteAutosave();
    if(document.visibilityState==='visible')requestAnimationFrame(updateFloatingActions);
  });
  window.addEventListener('pageshow',()=>requestAnimationFrame(updateFloatingActions));
  window.addEventListener('pagehide',()=>{if(state.noteEditor)flushNoteAutosave();});
  document.addEventListener('keydown',handleDialogKeys);
  navigate(NexoNative.getStartScreen()==='calendar'?'calendar':'today',{root:true});
}


const router=new NexoRouter({screen:'today'});
function uiIcon(name){return `<svg class="ui-icon" aria-hidden="true"><use href="#nx-icon-${name}"></use></svg>`;}
function navigateBack(){
  const route=router.back();
  if(route){
    const options={replace:true,scrollY:route.scrollY||0};
    if(route.screen==='panel'){if(route.list)openList(route.list,options);else openPanel(route.panel,options);}
    else navigate(route.screen,options);
    return true;
  }
  if(state.screen!=='today'){navigate('today',{root:true});return true;}
  return false;
}
function updateCaptureContext(){
  const box=$('#captureDateContext');if(!box)return;
  const date=$('#captureDate')?.value||'';
  box.classList.toggle('hidden',!date);
  if(date)box.textContent='Se guardará para '+new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(new Date(date+'T12:00:00'));
}
function syncCaptureAllDay(userChange=false){
  const allDay=!!$('#captureAllDay')?.checked;
  $('#captureStartTimeLabel')?.classList.toggle('hidden',allDay);
  $('#captureEndTimeLabel')?.classList.toggle('hidden',allDay);
  const startDate=$('#captureDate')?.value||'';
  if(startDate&&$('#captureEndDate')&&!$('#captureEndDate').value)$('#captureEndDate').value=startDate;
  if(allDay){
    if($('#captureTime'))$('#captureTime').value='';
    if($('#captureEndTime'))$('#captureEndTime').value='';
  }else{
    if($('#captureTime')&&!$('#captureTime').value)$('#captureTime').value='09:00';
    if($('#captureEndTime')&&!$('#captureEndTime').value)$('#captureEndTime').value=addOneHourTime($('#captureTime').value);
  }
  if(userChange)markCaptureManual('dateTime');
}
function openCreationMenu(date=''){prepareQuickNoteForType('event',date);}

function openCalendarMonthPicker(){
  const month=state.calendarMonth,value=`${month.getFullYear()}-${String(month.getMonth()+1).padStart(2,'0')}`;
  openModal('Elegir mes y año',`<div class="modal-form"><label>Mes<input id="calendarMonthInput" type="month" min="1900-01" max="2100-12" value="${value}"></label></div><div class="modal-actions"><button id="calendarMonthApply" class="primary-button">Ir al mes</button></div>`);
  $('#calendarMonthApply').addEventListener('click',()=>{const raw=$('#calendarMonthInput').value;if(!/^\d{4}-\d{2}$/.test(raw)){toast('Selecciona un mes válido');return;}const [year,month]=raw.split('-').map(Number);if(year<1900||year>2100||month<1||month>12){toast('Selecciona un mes entre 1900 y 2100');return;}state.calendarMonth=new Date(year,month-1,1);state.calendarSelected=isoDateLocal(state.calendarMonth);closeModal();renderCalendar();});
}
function renderDailyContext(){
  $('#todayDate').textContent=new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long'}).format(new Date());
  const d=parseNative(NexoNative.getToday(),{}),lines=[];
  (d.overdueTasks||[]).slice(0,2).forEach(i=>lines.push({text:'Atrasado · '+i.title,kind:'urgent'}));
  (d.upcoming||[]).slice(0,3).forEach(i=>lines.push({text:'Próximo · '+i.title+' · '+fmtDueItem(i),kind:i.type==='expiry'?'urgent':''}));
  if(d.inboxCount)lines.push({text:d.inboxCount+' elementos pendientes de organizar',kind:'urgent'});
  if(!lines.length)lines.push({text:'No hay otros pendientes destacados para hoy.',kind:'good'});
  state.todaySummaryLines=lines;renderTodaySummary();renderMedicationToday();renderParkingHomeCard(d.lastParking||null);renderJournalPrompt(false);
}
function renderCollectionPanel(panel){
  state.documents=parseNative(NexoNative.getCollection('documents'),[]);
  const labels=[...new Set([...DOCUMENT_LABELS,...state.documents.filter(i=>i.type==='document').map(i=>i.subcategory||'').filter(Boolean)])];
  $('#panelTitle').textContent='Documentos';$('#panelSubtitle').textContent='Tus archivos, guardados y a mano';
  $('#panelAction').innerHTML=`<button id="collectionAdd" class="primary-button">${uiIcon('plus')} Guardar documento</button>`;
  $('#collectionAdd').addEventListener('click',()=>openDocumentEditor());
  $('#panelContent').innerHTML=`<div class="document-toolbar"><label>Buscar documento<input id="documentSearch" type="search" placeholder="Descripción o nombre del archivo" value="${escAttr(state.documentSearch)}"></label><label>Tipo de documento<select id="documentFilter"><option value="all">Todos los tipos</option><option value="">Sin etiqueta</option>${labels.map(label=>`<option value="${escAttr(label)}">${esc(label)}</option>`).join('')}</select></label></div><p id="documentCount" class="small muted" aria-live="polite"></p><div id="documentList" class="stack"></div>`;
  if(state.documentFilter!==''&&state.documentFilter!=='all'&&!labels.includes(state.documentFilter))state.documentFilter='all';
  $('#documentFilter').value=state.documentFilter;
  $('#documentSearch').addEventListener('input',()=>{state.documentSearch=$('#documentSearch').value;renderDocumentsList();});
  $('#documentFilter').addEventListener('change',()=>{state.documentFilter=$('#documentFilter').value;renderDocumentsList();});
  renderDocumentsList();
}

function documentDescription(item){
  const title=String(item.title||'').trim(),body=String(item.body||'').trim();
  return !body?title:!title||body.startsWith(title)?body:title+'\n\n'+body;
}
function documentCard(i){
  const own=i.type==='document',id=Number(i.id),description=documentDescription(i)||i.attachmentName||'Documento',label=own?String(i.subcategory||'').trim():'';
  return `<article class="document-card" data-document-card="${id}"><button type="button" class="document-open" ${own?`data-document-edit="${id}"`:`data-document-open="${id}"`} aria-label="${own?'Editar':'Abrir'} ${escAttr(i.attachmentName||'documento')}">${label?`<span class="document-label">${esc(label)}</span>`:''}<span class="document-description">${esc(description)}</span><span class="document-filename">${uiIcon('file')}${esc(i.attachmentName||'Sin archivo adjunto')}</span></button><div class="record-card-actions">${own?recordIconAction('edit','Editar documento',`data-document-edit="${id}"`):''}${i.attachmentPath?recordIconAction('share','Compartir documento',`data-document-share="${id}"`):''}${own?recordIconAction('trash','Eliminar documento',`data-document-delete="${id}"`,'icon-action--danger'):''}</div>${i.attachmentPath?`<div class="document-actions"><button type="button" class="secondary-button" data-document-open="${id}">Abrir archivo</button><button type="button" class="secondary-button" data-document-download="${id}">${uiIcon('download')} Descargar</button></div>`:''}${!own?`<button type="button" class="document-source text-button" data-document-source="${id}">Ver ${esc(typeLabel(i.type).toLowerCase())} de origen</button>`:''}</article>`;
}
function bindDocumentButtons(root){
  root.querySelectorAll('[data-document-edit]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openDocumentEditor(Number(b.dataset.documentEdit));}));
  root.querySelectorAll('[data-document-open]').forEach(b=>b.addEventListener('click',e=>{
    e.stopPropagation();const i=parseNative(NexoNative.getItem(Number(b.dataset.documentOpen)),{});
    NexoNative.openDocumentFile(String(i.attachmentPath||''),String(i.attachmentName||''),String(i.attachmentMime||''));
  }));
  root.querySelectorAll('[data-document-download]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.downloadDocument(Number(b.dataset.documentDownload));}));
  root.querySelectorAll('[data-document-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.shareDocument(Number(b.dataset.documentShare));}));
  root.querySelectorAll('[data-document-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();deleteItemFromCard(Number(b.dataset.documentDelete));}));
  root.querySelectorAll('[data-document-source]').forEach(b=>b.addEventListener('click',()=>openItem(Number(b.dataset.documentSource))));
}

function renderDocumentsList(){
  const tokens=normVoiceText(state.documentSearch).split(' ').filter(Boolean);
  const rows=state.documents.filter(i=>(state.documentFilter==='all'||(i.type==='document'?i.subcategory||'':'')===state.documentFilter)&&tokens.every(t=>normVoiceText([i.title,i.body,i.attachmentName,i.type==='document'?i.subcategory:''].join(' ')).includes(t)));
  $('#documentCount').textContent=`${rows.length} documento${rows.length===1?'':'s'}`;
  $('#documentList').innerHTML=rows.length?rows.map(documentCard).join(''):`<div class="empty-state">${state.documents.length?'No hay documentos con esta búsqueda y etiqueta.':'Guarda un archivo con una descripción para encontrarlo fácilmente.'}</div>`;
  bindDocumentButtons($('#documentList'));
}
function discardDocumentDraft(){
  const editor=state.documentEditor;state.documentEditor=null;
  if(editor?.file?.path&&editor.file.path!==editor.originalPath)NexoNative.discardDocumentFile(editor.file.path);
}
function showDocumentError(message){
  const error=$('#documentSaveError');if(error){error.textContent=message;error.classList.remove('hidden');}
}
function renderDocumentFile(){
  const editor=state.documentEditor;if(!editor||!$('#documentFileName'))return;
  const file=editor.file;
  $('#documentFileName').textContent=editor.busy?'Cargando archivo…':file?.name||'Ningún archivo seleccionado';
  $('#documentChoose').textContent=file?.path?'Cambiar archivo':'Seleccionar archivo';
  $('#documentChoose').disabled=editor.busy;$('#documentSave').disabled=editor.busy;
  $('#documentPreview').classList.toggle('hidden',!file?.path);$('#documentPreview').disabled=editor.busy;
}
function openDocumentEditor(id=0,incoming=null){
  const editing=Number(id)>0,i=editing?parseNative(NexoNative.getItem(Number(id)),{}):{};
  if(editing&&(!i.id||i.type!=='document')){toast('No se encuentra este documento.');return;}
  const label=String(i.subcategory||''),labels=[...new Set([...DOCUMENT_LABELS,label].filter(Boolean))];
  openModal(editing?'Editar documento':'Guardar documento',`<div class="modal-form document-form"><label>Descripción<textarea id="documentDescription" rows="4" placeholder="Ej. Seguro del coche o factura de la lavadora" required></textarea></label><label>Tipo de documento <span class="optional">(opcional)</span><select id="documentLabel"><option value="">Sin etiqueta</option>${labels.map(value=>`<option value="${escAttr(value)}" ${value===label?'selected':''}>${esc(value)}</option>`).join('')}</select></label><div class="document-file-field"><span class="document-field-label">Archivo</span><span id="documentFileName" class="document-file-name" aria-live="polite"></span><div class="document-file-buttons"><button id="documentChoose" type="button" class="secondary-button">Seleccionar archivo</button><button id="documentPreview" type="button" class="secondary-button hidden">Abrir archivo</button></div><p class="document-file-help">PDF, JPG, PNG, Word u otro formato.</p></div><p id="documentSaveError" class="document-error hidden" role="alert"></p></div><div class="modal-actions"><button id="documentCancel" type="button" class="secondary-button">Cancelar</button><button id="documentSave" type="button" class="primary-button">Guardar documento</button></div>${editing?'<button id="documentDelete" type="button" class="danger-link">Eliminar documento</button>':''}`);
  $('#documentDescription').value=editing?documentDescription(i):incoming?.description||'';
  state.documentEditor={id:Number(id)||0,originalPath:i.attachmentPath||'',file:incoming?.path?incoming:i.attachmentPath?{path:i.attachmentPath,name:i.attachmentName,mime:i.attachmentMime}:null,request:'',busy:false};
  renderDocumentFile();
  $('#documentChoose').addEventListener('click',()=>{
    const editor=state.documentEditor;if(!editor||editor.busy)return;
    editor.request='document-'+Date.now()+'-'+(++state.documentRequest);editor.busy=true;
    $('#documentSaveError').classList.add('hidden');renderDocumentFile();NexoNative.chooseDocumentFile(editor.request);
  });
  $('#documentPreview').addEventListener('click',()=>{const f=state.documentEditor?.file;if(f?.path)NexoNative.openDocumentFile(f.path,f.name||'',f.mime||'');});
  $('#documentCancel').addEventListener('click',closeModal);
  $('#documentSave').addEventListener('click',()=>{
    const editor=state.documentEditor;if(!editor||editor.busy)return;
    const description=$('#documentDescription').value.trim(),file=editor.file;$('#documentSaveError').classList.add('hidden');
    if(!description){showDocumentError('Escribe una descripción del documento.');$('#documentDescription').focus();return;}
    if(!file?.path){showDocumentError('Selecciona el archivo que quieres guardar.');$('#documentChoose').focus();return;}
    const payload={id:editor.id,description,label:$('#documentLabel').value,attachmentPath:file.path,attachmentName:file.name||'',attachmentMime:file.mime||''};
    const result=parseNative(NexoNative.saveDocument(JSON.stringify(payload)),{});
    if(!result.ok){showDocumentError(result.error||'No se pudo guardar el documento.');return;}
    editor.originalPath=file.path;closeModal();refreshCurrent();toast('Documento guardado');
  });
  $('#documentDelete')?.addEventListener('click',()=>confirmDeleteItem(i));
}

function placeCard(i){
  const location=String(i.locationText||'').trim(),mapLabel=/^(?:https?:\/\/|www\.|maps\.|goo\.gl\/)/i.test(location)?'Enlace de Google Maps':location,id=Number(i.id);
  return `<article class="place-card" data-place-card="${id}"><button type="button" class="place-open" data-open-place="${id}"><span class="place-label">${esc(placeLabel(i))}</span><strong>${esc(i.title||'Lugar guardado')}</strong>${i.body?`<span class="place-description">${esc(i.body)}</span>`:''}${location?`<span class="place-location">${uiIcon('pin')}${esc(mapLabel)}</span>`:''}</button><div class="record-card-actions">${recordIconAction('edit','Editar lugar',`data-open-place="${id}"`)}${recordIconAction('share','Compartir lugar',`data-place-share="${id}"`)}${recordIconAction('trash','Eliminar lugar',`data-place-delete="${id}"`,'icon-action--danger')}${location?recordIconAction('pin','Abrir en Google Maps',`data-place-map="${id}"`):''}</div></article>`;
}
function bindPlaceButtons(root){
  root.querySelectorAll('[data-open-place]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();openPlaceEditor(Number(b.dataset.openPlace));}));
  root.querySelectorAll('[data-place-share]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();NexoNative.shareNexoItem(Number(b.dataset.placeShare),0,false);}));
  root.querySelectorAll('[data-place-delete]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();deleteItemFromCard(Number(b.dataset.placeDelete));}));
  root.querySelectorAll('[data-place-map]').forEach(b=>b.addEventListener('click',e=>{e.stopPropagation();const i=parseNative(NexoNative.getItem(Number(b.dataset.placeMap)),{});if(i.id&&i.locationText)NexoNative.openPlaceMap(String(i.locationText));else toast('Este lugar ya no tiene una ubicación guardada.');}));
}

function renderPlacesPanel(){
  state.places=parseNative(NexoNative.getCollection('places'),[]);
  const labels=[...new Set([...PLACE_LABELS,...state.places.map(placeLabel)])];
  if(state.placeFilter!=='all'&&!labels.includes(state.placeFilter))state.placeFilter='all';
  $('#panelTitle').textContent='Lugares';$('#panelSubtitle').textContent='Sitios que te gustan o que quieres descubrir';
  $('#panelAction').innerHTML=`<button id="placeAdd" class="primary-button">${uiIcon('plus')} Guardar lugar</button>`;
  $('#placeAdd').addEventListener('click',()=>openPlaceEditor());
  $('#panelContent').innerHTML=`<div class="place-toolbar"><label>Buscar lugar<input id="placeSearch" type="search" placeholder="Nombre, descripción o ubicación" value="${escAttr(state.placeSearch)}"></label><label>Etiqueta<select id="placeFilter"><option value="all">Todas las etiquetas</option>${labels.map(label=>`<option value="${escAttr(label)}" ${label===state.placeFilter?'selected':''}>${esc(label)}</option>`).join('')}</select></label></div><p id="placeCount" class="small muted" aria-live="polite"></p><div id="placeList" class="stack"></div>`;
  $('#placeSearch').addEventListener('input',()=>{state.placeSearch=$('#placeSearch').value;renderPlacesList();});
  $('#placeFilter').addEventListener('change',()=>{state.placeFilter=$('#placeFilter').value;renderPlacesList();});
  renderPlacesList();
}
function renderPlacesList(){
  const tokens=normVoiceText(state.placeSearch).split(' ').filter(Boolean);
  const rows=state.places.filter(i=>(state.placeFilter==='all'||placeLabel(i)===state.placeFilter)&&tokens.every(t=>normVoiceText([i.title,i.body,i.locationText,placeLabel(i)].join(' ')).includes(t)));
  $('#placeCount').textContent=`${rows.length} lugar${rows.length===1?'':'es'}${state.placeFilter!=='all'||tokens.length?' en esta vista':rows.length===1?' guardado':' guardados'}`;
  $('#placeList').innerHTML=rows.length?rows.map(placeCard).join(''):`<div class="empty-state">${state.places.length?'No hay lugares con esta búsqueda y etiqueta.':'Guarda los sitios que te han gustado o que quieres visitar.'}${state.placeFilter!=='all'||tokens.length?'<button id="placeResetFilter" class="text-button">Ver todos los lugares</button>':''}</div>`;
  bindPlaceButtons($('#placeList'));
  $('#placeResetFilter')?.addEventListener('click',()=>{state.placeFilter='all';state.placeSearch='';$('#placeFilter').value='all';$('#placeSearch').value='';renderPlacesList();});
}
function openPlaceEditor(id=0){
  const editing=Number(id)>0,i=editing?parseNative(NexoNative.getItem(Number(id)),{}):{};
  if(editing&&(!i.id||!isPlaceType(i.type))){toast('No se encuentra este lugar.');return;}
  const label=editing?placeLabel(i):(state.placeFilter!=='all'?state.placeFilter:'Quiero ir'),labels=[...new Set([...PLACE_LABELS,label])];
  openModal(editing?'Editar lugar':'Guardar lugar',`<div class="modal-form place-form"><label>Título<input id="placeTitle" type="text" value="${escAttr(i.title||'')}" placeholder="Ej. Mirador del Garbí" required></label><label>Descripción <span class="optional">(opcional)</span><textarea id="placeDescription" rows="4" placeholder="Qué te gustó, qué quieres probar o qué merece la pena recordar">${esc(i.body||'')}</textarea></label><label>Ubicación en Google Maps <span class="optional">(opcional)</span><input id="placeLocation" type="text" value="${escAttr(i.locationText||'')}" placeholder="Pega el enlace de Maps o escribe la dirección" autocomplete="off" autocapitalize="off" spellcheck="false"></label><p class="place-map-help">En Google Maps puedes usar Compartir y Copiar enlace. También puedes escribir una dirección o coordenadas.</p><button id="placePreviewMap" type="button" class="secondary-button">${uiIcon('pin')} Comprobar en Maps</button><label>Etiqueta<select id="placeLabel">${labels.map(value=>`<option value="${escAttr(value)}" ${value===label?'selected':''}>${esc(value)}</option>`).join('')}</select></label><p id="placeSaveError" class="place-error hidden" role="alert"></p></div><div class="modal-actions"><button id="placeCancel" type="button" class="secondary-button">Cancelar</button><button id="placeSave" type="button" class="primary-button">Guardar lugar</button></div>${editing?'<button id="placeDelete" type="button" class="danger-link">Eliminar lugar</button>':''}`);
  const syncMap=()=>{$('#placePreviewMap').classList.toggle('hidden',!$('#placeLocation').value.trim());};syncMap();
  $('#placeLocation').addEventListener('input',syncMap);
  $('#placePreviewMap').addEventListener('click',()=>NexoNative.openPlaceMap($('#placeLocation').value.trim()));
  $('#placeCancel').addEventListener('click',closeModal);
  $('#placeSave').addEventListener('click',()=>{
    const payload={id:Number(id)||0,title:$('#placeTitle').value.trim(),body:$('#placeDescription').value.trim(),locationText:$('#placeLocation').value.trim(),label:$('#placeLabel').value};
    const error=$('#placeSaveError');error.classList.add('hidden');
    if(!payload.title){error.textContent='Escribe un título para el lugar.';error.classList.remove('hidden');$('#placeTitle').focus();return;}
    const result=parseNative(NexoNative.savePlace(JSON.stringify(payload)),{});
    if(!result.ok){error.textContent=result.error||'No se pudo guardar el lugar.';error.classList.remove('hidden');return;}
    closeModal();refreshCurrent();toast('Lugar guardado');
  });
  $('#placeDelete')?.addEventListener('click',()=>confirmDeleteItem(i));
}
function renderParkingPanel(){
  const p=parseNative(NexoNative.getLastParking(),{});$('#panelTitle').textContent='Aparcamiento';$('#panelSubtitle').textContent='El último lugar donde has dejado el coche';
  $('#panelAction').innerHTML='<button id="parkingPanelSave" class="primary-button">Guardar ubicación</button>';
  $('#parkingPanelSave').addEventListener('click',()=>openParkingModal());
  $('#panelContent').innerHTML=p.id?`<div class="parking-panel-card"><strong>${esc(p.title||'Coche aparcado')}</strong><p>${esc(p.body||p.locationText||'Ubicación guardada')}</p><p class="small muted">${esc(fmtDate(p.createdAt||p.updatedAt,true))}</p><div class="item-actions"><button id="parkingPanelDirections" class="primary-button">${uiIcon('pin')} Ir al coche</button><button id="parkingPanelDelete" class="danger-button">Eliminar ubicación</button></div></div>`:'<div class="empty-state">Aún no has guardado un aparcamiento.</div>';
  $('#parkingPanelDirections')?.addEventListener('click',()=>NexoNative.openParkingDirections());
  $('#parkingPanelDelete')?.addEventListener('click',()=>confirmGeneric('Eliminar aparcamiento','Se eliminará la ubicación guardada del coche.',()=>{const ok=NexoNative.deleteParking();toast(ok?'Aparcamiento eliminado':'No se pudo eliminar');refreshCurrent();}));
}
function updateListCheck(id,checked,row){
  if(!row)return;
  const box=row.querySelector('[data-entry-toggle]'),button=row.querySelector('[data-entry-text-toggle]');
  const ok=NexoNative.toggleListEntry(id,checked);
  if(!ok){box.checked=!checked;toast('No se pudo actualizar el producto');return;}
  box.checked=checked;row.classList.toggle('checked',checked);button.dataset.entryChecked=checked?'1':'0';
  box.setAttribute('aria-label',(checked?'Desmarcar ':'Marcar ')+button.textContent.trim());
}
function refreshNotesSurface(){if(state.screen==='panel'&&state.lastPanel==='notes')renderNotesPanel();else refreshCurrent();}
function refreshRecordingSurface(){if(state.screen==='search')runSearch();else if(state.screen==='panel'&&state.lastPanel==='recordings')renderRecordingsPanel();}
function openRecordingActions(id){
  const r=parseNative(NexoNative.getRecordings(),[]).find(r=>String(r.id)===String(id));if(!r){toast('La grabación ya no existe');return;}
  const model=parseNative(NexoNative.getTranscriptionModelStatus(),{}),has=NexoNative.hasTranscription(String(id)),t=transcriptionStatus(id),running=t.state==='running'||t.state==='starting';
  const transcription=has?`<button class="secondary-button" data-transcription-view="${escAttr(id)}">Ver transcripción</button>`:(running?`<button class="secondary-button" data-transcription-cancel="${escAttr(id)}">Cancelar transcripción</button>`:(model.installed?`<button class="primary-button" data-transcription-start="${escAttr(id)}">Transcribir grabación</button>`:`<button class="primary-button" data-transcription-needs-model="${escAttr(id)}">Preparar transcripción</button>`));
  openModal(r.title||'Grabación',`<div class="scope-buttons">${transcription}<button class="secondary-button" data-recording-share="${escAttr(id)}">${uiIcon('share')} Compartir audio</button><button class="secondary-button" data-recording-export="${escAttr(id)}">${uiIcon('download')} Guardar audio en Descargas</button><button class="secondary-button" data-recording-rename="${escAttr(id)}">${uiIcon('edit')} Renombrar</button><button class="danger-button" data-recording-delete="${escAttr(id)}">Eliminar grabación</button></div><p class="small muted">Los audios se exportan por separado de la copia .nexo.</p>`);
  bindRecordingButtons($('#modalBody'));
}

function handleDialogKeys(event){
  const active=['voiceOverlay','confirmModal','modal'].map(id=>$('#'+id)).find(el=>el&&!el.classList.contains('hidden'));if(!active)return;
  if(event.key==='Escape'){event.preventDefault();window.NexoUI.handleBack();return;}
  if(event.key!=='Tab')return;
  const controls=[...active.querySelectorAll('button,input,select,textarea,summary')].filter(el=>!el.disabled&&el.getClientRects().length);
  if(!controls.length)return;
  const first=controls[0],last=controls[controls.length-1];
  if(event.shiftKey&&(document.activeElement===first||!active.contains(document.activeElement))){event.preventDefault();last.focus();}
  else if(!event.shiftKey&&(document.activeElement===last||!active.contains(document.activeElement))){event.preventDefault();first.focus();}
}

document.addEventListener('DOMContentLoaded',init);
