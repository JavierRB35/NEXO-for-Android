from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text()
js=(root/'app/src/main/assets/app.js').read_text()
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()

# Orden de HOY: agenda/resumen -> medicación -> aparcamiento -> Para recordar.
summary=html.index('id="todaySummary"')
med=html.index('id="medicationToday"')
parking=html.index('id="parkingHomeCard"')
journal=html.index('id="journalPrompt"')
assert summary < med < parking < journal, 'Orden de módulos HOY incorrecto'

# Inicio modular. El único calendario está en su pantalla dedicada.
assert 'class="module-grid"' in html
for module in ['calendar','appointments','medications','lists','notes','recordings','parking','journal','documents','places']:
    assert 'data-panel="'+module+'"' in html
assert 'id="homeCalGrid"' not in html
assert html.count('id="calendarGrid"') == 1
assert 'data-nav="calendar"' in html

# Módulo de notas: CRUD básico, voz y orden descendente por fecha de creación.
for token in ['function renderNotesPanel()', 'function openNoteComposer(', "startManagedDictation('note'", 'NexoNative.getNotes()', 'NexoNative.saveNote(', 'NexoNative.autosaveNote(']:
    assert token in js, f'Falta integración JS de notas: {token}'
for token in ['@JavascriptInterface public String getNotes()', '@JavascriptInterface public long saveNote(String json)', '@JavascriptInterface public boolean autosaveNote(String json)', '@JavascriptInterface public String suggestNoteTitle(String body)']:
    assert token in java, f'Falta puente Java de notas: {token}'
assert 'return queryItems("type=\'note\' AND status=\'active\'", null, "created_at DESC", 500);' in db, 'Las notas no están ordenadas por fecha descendente'

print('Bloc de notas y orden de HOY: OK')
