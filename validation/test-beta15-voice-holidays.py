from pathlib import Path
root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
holiday=(root/'app/src/main/java/com/jruiz/nexo/calendar/HolidayCalendarService.java').read_text(encoding='utf-8')

# Inicio conserva Agenda como tarjeta y Calendario en navegación inferior.
assert 'id="homeCalendarCard"' in html and 'id="homeRecordCard"' not in html
assert '<small>Agenda</small>' in html and "$('#homeCalendarCard')" in js
assert 'data-nav="calendar"' in html
assert 'aria-label="Hablar con Nexo"' in html

# Voz contextual: Inicio cae en Hablar con Nexo; Grabaciones usa el mismo FAB para grabar.
start=js.index('function runFloatingVoice()');end=js.index('function navigate(screen',start);block=js[start:end]
assert "startManagedDictation('universal'" in block
assert "if(state.screen==='panel'&&state.lastPanel==='recordings')" in block
assert 'NexoNative.startRecording()' in block
assert block.index("if(state.screen==='panel'&&state.lastPanel==='recordings')") < block.rindex("startManagedDictation('universal'")

route=js[js.index('function routeUniversalVoice'):js.index('function listMatch',js.index('function routeUniversalVoice'))]
assert 'NexoNative.interpretIntent(raw)' in route
for intent in ["route.intent==='alarm'","route.intent==='journal'","route.intent==='medication'","route.intent==='shopping'","route.intent==='parking'"]:
    assert intent in route
assert 'prepareUniversalCapture(raw)' in route
assert "const inferredType=pinned||a.type||'note',appointment=isAppointmentType(inferredType);" in js

# Calendario festivo.
assert '@JavascriptInterface public void requestHolidayCalendar(int year)' in main
assert 'calendariosnacionales.com' in holiday and 'MUNICIPALITY_INE="46202"' in holiday and 'MUNICIPALITY_NAME="Pobla de Vallbona, la"' in holiday and 'CCAA_CODE="ES-VC"' in holiday
assert 'CACHE_MS=7L*24L*60L*60L*1000L' in holiday and 'Viernes Santo' in holiday and 'easter(' in holiday
assert 'id="calendarDayHolidays"' in html and 'calendar-day--holiday' in js and '.calendar-day--holiday .day-number' in css
for level in ['national','regional','provincial','local']:
    assert f'selected-day-holiday__level--{level}' in css

assert 'Escribe o usa el micrófono flotante para dictar.' not in html
print('Beta15: voz contextual, Agenda/Calendario y festivos Nexo Familia OK')
