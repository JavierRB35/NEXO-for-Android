from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")

# Calendario: la pantalla seleccionada determina el contexto de creación.
assert "state.screen==='calendar'?'Añadir cita por voz'" in app
assert "state.screen==='calendar'?'Nueva cita'" in app
assert "prepareQuickNoteForType('event',state.screen==='calendar'?(state.calendarSelected||today):today)" in app
assert "startAppointmentVoice(state.calendarSelected||isoDateLocal(new Date()))" in app
assert "if(state.voiceMode==='appointment')" in app

# El dictado del Calendario abre una cita para el día seleccionado y siempre pasa por revisión.
assert "function startAppointmentVoice(date='')" in app
assert "function applyAppointmentVoice(text,date='')" in app
assert "prepareQuickNoteForType('event',String(date||''));" in app
assert "toast('Revisa la cita antes de guardarla.');" in app

# El gesto horizontal actúa sobre toda la pantalla de Calendario y bloquea clics accidentales.
assert "attachMonthSwipe(document.querySelector('[data-screen=\"calendar\"]')" in app
assert "Math.abs(dx)>=52&&Math.abs(dx)>Math.abs(dy)*1.45" in app
assert "suppressClickUntil=Date.now()+800" in app
assert "e.stopImmediatePropagation?.();" in app

# Los flotantes describen explícitamente el día seleccionado.
assert "Añadir una cita el '+label" in app
assert "Añadir una cita por voz el '+label" in app

# Hoy sigue restaurando el calendario a la fecha actual.
assert "$('#calendarToday').addEventListener('click',()=>{const now=new Date();" in app
assert "state.calendarSelected=isoDateLocal(now);renderCalendar();" in app

print("OK: Calendario contextual y gesto de mes protegido")
