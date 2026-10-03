from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

# Acerca de Nexo
for token in [
    'id="brandInfoBtn"', 'data-screen="about"', 'id="aboutBackBtn"',
    'id="aboutVersionCode"', 'id="aboutDatabaseVersion"', 'id="aboutCopyDiagnostics"',
    'Privacidad y funcionamiento', 'Desarrollado por Carlos Javier Ruiz Bermúdez'
]:
    assert token in html, token
assert "if(!['today','save','search','more','calendar','tools','panel','about'].includes(screen))return;" in js
assert "else if(screen==='about')renderAbout();" in js
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in js
assert "if(state.screen==='about'){voiceVisible=false;addVisible=false;}" in js
assert "function renderAbout()" in js
assert "function copyAboutDiagnostics()" in js
assert "NexoNative.getAboutInfo()" in js
assert "NexoNative.copyText('Diagnóstico Nexo',text)" in js
assert '@JavascriptInterface public String getAboutInfo()' in java
assert '@JavascriptInterface public void copyText(String label, String text)' in java
assert 'DatabaseHelper.schemaVersion()' in java
assert '.about-hero{' in css and '.about-tech-list{' in css

# Primer arranque del Calendario: hoy se fija explícitamente y el calendario base
# se pinta antes de solicitar festivos o depender de datos nativos.
assert 'function resetCalendarToToday()' in js
assert "if(startScreen==='calendar')resetCalendarToToday();" in js
assert "if(startScreen==='calendar')requestAnimationFrame(()=>{if(state.screen==='calendar')renderCalendar();});" in js
assert "try{NexoNative.requestHolidayCalendar(year);}catch(_){state.holidayLoading.delete(year);}" in js
render=js[js.index('function renderCalendar(direction=0){'):js.index('function renderCalendarDay(){')]
assert render.index("$('#calendarMonthTitle').textContent") < render.index('NexoNative.getCalendar(')
assert 'try{' in render and 'items=[];' in render
assert render.index("$('#calendarGrid').innerHTML=calendarHtml") < render.index('requestHolidayYears(years);')
assert "$('#calendarToday').addEventListener('click',()=>{resetCalendarToToday();renderCalendar();});" in js

print('Acerca de Nexo y arranque resiliente del Calendario: OK')
