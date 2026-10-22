from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text()
js=(root/'app/src/main/assets/app.js').read_text()
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()

# Inicio compacto: Lo importante ahora -> Próximamente (sin bloque Accesos directos).
important=html.index('id="homeNowTitle"')
upcoming=html.index('id="homeUpcomingTitle"')
assert important < upcoming, 'Orden de secciones de Inicio incorrecto'
home=html[html.index('id="homeDashboardContent"'):html.index('data-screen="tools"')]
assert 'Accesos directos' not in home and 'home-section--quick-access' not in home
assert 'module-grid--family-info' not in home

# El resumen ligero de Inicio conserva sus cuatro tarjetas.
for control in ['homeTodayCard','homeTomorrowCard','homeWeekCard','homeCalendarCard']:
    assert f'id="{control}"' in html, f'Falta tarjeta de Inicio: {control}'

assert 'id="homeInboxPulseCard"' not in html, 'Inbox ahora debe estar en la navegación inferior'

# Los cuatro módulos siguen accesibles desde Herramientas.
tools=html[html.index('data-screen="tools"'):html.index('data-screen="save"')]
for module in ['notes','medications','lists','documents']:
    assert 'data-panel="'+module+'"' in tools, f'Falta el módulo de Herramientas: {module}'

# El calendario completo sigue estando solo en su pantalla dedicada.
assert 'id="homeCalGrid"' not in html
assert html.count('id="calendarGrid"') == 1
assert 'data-nav="calendar"' in html

# Módulo de notas: CRUD básico, voz y orden descendente por fecha de creación.
for token in ['function renderNotesPanel()', 'function openNoteComposer(', "startManagedDictation('note'", 'NexoNative.getNotes()', 'NexoNative.saveNote(', 'NexoNative.autosaveNote(']:
    assert token in js, f'Falta integración JS de notas: {token}'
for token in ['@JavascriptInterface public String getNotes()', '@JavascriptInterface public long saveNote(String json)', '@JavascriptInterface public boolean autosaveNote(String json)', '@JavascriptInterface public String suggestNoteTitle(String body)']:
    assert token in java, f'Falta puente Java de notas: {token}'
assert 'return queryItems("type=\'note\' AND status=\'active\'", null, "created_at DESC", 500);' in db, 'Las notas no están ordenadas por fecha descendente'

print('Bloc de notas y pantalla Inicio compacta: OK')
