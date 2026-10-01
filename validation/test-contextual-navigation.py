from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")

# Inicio: Agenda tiene acceso propio; Calendario ya vive en la barra inferior.
assert '<small>Agenda</small>' in html
assert '<strong id="homeCalendarMonth">Completa</strong>' in html
assert "state.appointmentsFilter='pending';openPanel('appointments')" in app
assert 'data-nav="calendar"' in html
assert "function openHomeToday()" in app and "state.appointmentsWindow='today'" in app
assert "function openHomeWeek()" in app and "state.appointmentsWindow='week'" in app

# Agenda: + y micrófono pertenecen a Agenda.
assert "panel==='appointments'?'Añadir a Agenda por voz'" in app
assert "panel==='appointments'?'Nueva cita'" in app
assert "if(state.screen==='panel'&&state.lastPanel==='appointments'){startAgendaVoice();return;}" in app
assert "prepareQuickNoteForType('event',state.appointmentsWindow==='today'?today:'')" in app

# Calendario: los dos flotantes crean citas para el día seleccionado.
assert "state.screen==='calendar'?'Añadir cita por voz'" in app
assert "state.screen==='calendar'?'Nueva cita'" in app
assert "startAppointmentVoice(state.calendarSelected||isoDateLocal(new Date()))" in app
assert "prepareQuickNoteForType('event',state.screen==='calendar'?(state.calendarSelected||today):today)" in app
assert "attachMonthSwipe(document.querySelector('.calendar-viewport')" in app
assert "suppressClickUntil" in app
assert "e.stopImmediatePropagation?.()" in app

# Buscar: micrófono = búsqueda; nunca aparece +.
assert "state.screen==='search'?'Buscar por voz'" in app
assert "if(state.screen==='search'){startSearchVoice();return;}" in app
assert "else if(state.screen==='search'){voiceVisible=!modalOpen;addVisible=false;}" in app
assert app.count("state.voiceMode==='search'") == 1
assert app.count("else if(target==='search')") == 1

# Copia, Inbox e Historial son consulta/gestión sin flotantes.
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in app
assert "Todo organizado" in app
assert "Actividad reciente de Nexo" in app

# Historial es accesible desde Herramientas, junto a copia.
assert "<h2>Datos y seguridad</h2>" in html
assert 'data-panel="history"' in html
assert 'data-panel="backup"' in html
assert html.index('data-panel="history"') < html.index('data-panel="backup"')

# Captura: solo micrófono contextual; Ajustes y Herramientas no crean contenido.
assert "else if(state.screen==='save'){voiceVisible=!modalOpen;addVisible=false;}" in app
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in app
assert "startManagedDictation('capture-edit'" in app

print("OK: navegación, calendario, búsqueda, inbox, historial y captura contextuales")
