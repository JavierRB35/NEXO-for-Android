from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")

# Inicio: Agenda tiene acceso propio; Calendario ya vive en la barra inferior.
assert '<small>Agenda</small>' in html
assert '<strong id="homeCalendarMonth">Completa</strong>' not in html
assert "state.appointmentsFilter='pending';openPanel('appointments')" in app
assert 'data-nav="calendar"' in html
assert "function openHomeToday()" in app and "state.appointmentsWindow='today'" in app
assert "function openHomeTomorrow()" in app and "state.appointmentsWindow='tomorrow'" in app
assert "function openHomeWeek()" in app and "state.appointmentsWindow='week'" in app
assert "if(state.appointmentsWindow==='tomorrow')" in app
assert 'data-nav="inbox"' in html

# Agenda: + y micrófono pertenecen a Agenda.
assert "panel==='appointments'?'Añadir a Agenda por voz'" in app
assert "panel==='appointments'?'Nueva cita'" in app
assert "if(state.screen==='panel'&&state.lastPanel==='appointments'){startAgendaVoice();return;}" in app
assert "function appointmentsWindowDateIso()" in app
assert "state.appointmentsWindow==='tomorrow'" in app
assert "prepareQuickNoteForType('event',appointmentsWindowDateIso())" in app

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

# Historial y copia viven exclusivamente bajo Datos y seguridad en Ajustes.
tools = html.split('data-screen="tools"', 1)[1].split('data-screen="save"', 1)[0]
settings = html.split('data-screen="more"', 1)[1].split('data-screen="about"', 1)[0]
assert 'data-settings-accordion="security"' in settings
assert 'data-panel="history"' in settings and 'data-panel="backup"' in settings
assert 'data-panel="history"' not in tools and 'data-panel="backup"' not in tools
assert settings.index('data-panel="history"') < settings.index('data-panel="backup"')

# Captura: solo micrófono contextual; Ajustes y Herramientas no crean contenido.
assert "else if(state.screen==='save'){voiceVisible=!modalOpen;addVisible=false;}" in app
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in app
assert "startManagedDictation('capture-edit'" in app

print("OK: navegación, calendario, búsqueda, inbox, historial y captura contextuales")
