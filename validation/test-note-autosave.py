from pathlib import Path

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text()
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
engine=(root/'app/src/main/java/com/jruiz/nexo/core/NoteTitleEngine.java').read_text()

start=js.index('function openNoteComposer(')
end=js.index('function renderNotesPanel()', start)
composer=js[start:end]

# Guardado explícito: el editor depende del disquete y no escribe la nota por temporizador.
assert 'id="noteSave"' in composer
assert "uiIcon('save')" in composer
assert 'La nota solo se guarda al pulsar el disquete' in composer
assert 'function saveNoteEditor()' in js
assert "NexoNative.autosaveNote" in js
assert 'scheduleNoteAutosave' not in js
assert 'flushNoteAutosave' not in js

# Cerrar una nota modificada nunca debe descartar el texto silenciosamente.
assert 'function noteHasUnsavedChanges()' in js
assert 'function showNoteUnsavedGuard()' in js
assert 'data-note-close-save' in js
assert 'data-note-close-discard' in js
assert 'data-note-close-cancel' in js
assert "if(noteWasOpen&&!force&&noteHasUnsavedChanges())" in js

# El borrador temporal es solo una red de recuperación; ocultar/cerrar WebView no guarda en BD.
assert 'nexo-note-recovery-v1-' in js
assert 'localStorage.setItem' in js and 'localStorage.removeItem' in js
assert "document.visibilityState==='hidden'&&state.noteEditor)persistNoteRecoveryDraft()" in js
assert "window.addEventListener('pagehide',()=>{if(state.noteEditor)persistNoteRecoveryDraft();});" in js
assert "Se encontró una edición de esta nota que no llegó a guardarse" in js

# Título local: se genera a partir del cuerpo y deja de regenerarse si el usuario lo edita.
assert 'placeholder="Se generará automáticamente al escribir"' in composer
assert 'if(!titleManual&&!title)title=noteSuggestedTitle(body);' in composer
assert 'NexoNative.suggestNoteTitle(text)' in js
assert 'if(titleEl.value.trim())ed.titleManual=true;' in composer
assert 'else{ed.titleManual=false;refreshAutoNoteTitle();}' in composer
assert 'noteTitleManual' in composer
assert 'NoteTitleEngine.derive(body)' in java

# Migración y actualización explícita de notas existentes.
assert 'DB_VERSION = 25' in db
assert 'note_title_manual INTEGER NOT NULL DEFAULT 0' in db
assert 'if (oldVersion < 14)' in db
assert "UPDATE items SET note_title_manual=1 WHERE type='note'" in db
assert 'public boolean autosaveNote(JSONObject in)' in db
assert '@JavascriptInterface public boolean autosaveNote(String json)' in java
assert '@JavascriptInterface public String suggestNoteTitle(String body)' in java

# La actualización directa de una nota no genera historial por cada edición intermedia.
auto=db[db.index('public boolean autosaveNote(JSONObject in)'):db.index('public JSONArray getTasks', db.index('public boolean autosaveNote(JSONObject in)'))]
assert 'updateItem(' not in auto
assert 'getWritableDatabase().update("items"' in auto

for forbidden in ['http://','https://','Gemini','OpenAI','apiKey','HttpURLConnection']:
    assert forbidden not in engine

print('Notas: guardado manual + recuperación temporal + migración segura: OK')
