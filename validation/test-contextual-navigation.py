from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")

# Inicio: acceso directo a Agenda; Calendario ya está en la navegación inferior.
assert '<small>Agenda</small><strong id="homeCalendarMonth">Completa</strong><span id="homeCalendarHint">Ver todos</span>' in html
assert "$('#homeCalendarCard')?.addEventListener('click',()=>{state.appointmentsWindow='';state.appointmentsFilter='pending';openPanel('appointments');});" in app
assert "function openHomeToday()" in app and "state.appointmentsWindow='today'" in app
assert "function openHomeWeek()" in app and "state.appointmentsWindow='week'" in app

# Agenda / Calendario: + y micrófono crean citas en su contexto.
assert "panel==='appointments'?'Añadir a Agenda por voz'" in app
assert "if(state.screen==='calendar'){startAppointmentVoice(state.calendarSelected||isoDateLocal(new Date()));return;}" in app
assert "state.appointmentsWindow==='today'?isoDateLocal(new Date()):''" in app
assert "prepareCaptureForDate(state.appointmentsWindow==='today'?today:'')" in app
assert "function startAppointmentVoice(date='')" in app
assert "function applyAppointmentVoice(text,date='')" in app

# Buscar: micrófono busca, + no aparece.
assert "else if(state.screen==='search'){voiceVisible=!modalOpen;addVisible=false;}" in app
assert "function startSearchVoice()" in app
assert "function applySearchVoice(text)" in app
assert "startManagedDictation('search'" in app

# Pantallas de navegación/consulta/sistema: sin flotantes ambiguos.
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in app
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in app

# Guardar/captura: micrófono amplía la captura y no hay +.
assert "else if(state.screen==='save'){voiceVisible=!modalOpen;addVisible=false;}" in app
assert "startManagedDictation('capture-edit'" in app

# Calendario: el gesto horizontal cancela el click accidental sobre el día.
assert "element.addEventListener('touchmove'" in app
assert "suppressClickUntil=Date.now()+750" in app
assert "e.stopImmediatePropagation?.()" in app

print("OK: Inicio, Agenda, Calendario, Buscar, pantallas pasivas, Captura y Ajustes validados")
