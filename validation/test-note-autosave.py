from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text()
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
engine=(root/'app/src/main/java/com/jruiz/nexo/core/NoteTitleEngine.java').read_text()

# Editor sin botón Guardar y con autoguardado temporal + al salir/ocultarse.
start=js.index('function openNoteComposer(')
end=js.index('function renderNotesPanel()', start)
composer=js[start:end]
assert 'id="noteSave"' not in composer, 'El editor de notas no debe depender de un botón Guardar'
assert 'Autoguardado local' in composer
assert 'scheduleNoteAutosave' in composer
assert 'NexoNative.autosaveNote' in js
assert "setTimeout(()=>{ed.timer=null;flushNoteAutosave();},delay)" in js
assert 'const noteWasOpen=!!state.noteEditor;if(noteWasOpen){flushNoteAutosave();' in js
assert "document.visibilityState==='hidden'&&state.noteEditor" in js
assert "window.addEventListener('pagehide'" in js

# Título vacío al crear; se genera localmente desde el cuerpo y deja de regenerarse si el usuario lo edita.
assert "placeholder=\"Se generará automáticamente al escribir\"" in composer
assert 'if(!titleManual)title=noteSuggestedTitle(body);' in composer
assert 'NexoNative.suggestNoteTitle(text)' in js
assert 'if(titleEl.value.trim())ed.titleManual=true;' in composer
assert 'else{ed.titleManual=false;refreshAutoNoteTitle();}' in composer
assert 'noteTitleManual' in composer
assert 'NoteTitleEngine.derive(body)' in java

# Persistencia segura: nueva columna incremental y protección de títulos existentes.
assert 'DB_VERSION = 14' in db
assert 'note_title_manual INTEGER NOT NULL DEFAULT 0' in db
assert 'if (oldVersion < 14)' in db
assert "UPDATE items SET note_title_manual=1 WHERE type='note'" in db
assert 'public boolean autosaveNote(JSONObject in)' in db
assert 'note_title_manual' in db[db.index('public boolean autosaveNote'):]
assert '@JavascriptInterface public boolean autosaveNote(String json)' in java
assert '@JavascriptInterface public String suggestNoteTitle(String body)' in java

# El autoguardado no usa updateItem(), evitando una entrada de historial por cada pausa de escritura.
auto=db[db.index('public boolean autosaveNote(JSONObject in)'):db.index('public JSONArray getTasks', db.index('public boolean autosaveNote(JSONObject in)'))]
assert 'updateItem(' not in auto
assert 'getWritableDatabase().update("items"' in auto

# El motor es puramente local/determinista.
for forbidden in ['http://','https://','Gemini','OpenAI','apiKey','HttpURLConnection']:
    assert forbidden not in engine

print('Notas: título local + autoguardado + migración segura: OK')
