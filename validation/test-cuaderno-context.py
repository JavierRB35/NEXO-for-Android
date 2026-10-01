from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")
db = (root / "app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text(encoding="utf-8")

# Cuaderno: pantalla limpia, sin acciones duplicadas en cabecera/toolbar.
assert "$('#panelSubtitle').textContent=quoteMode?'Citas textuales, autores y referencias · fuera del calendario':'Pensamientos, ideas y reflexiones personales.';" in app
assert "journal-toolbar journal-toolbar--cuaderno" in app
assert 'id="journalVoice"' in app  # sigue existiendo solo para Frases
assert "if(quoteMode){" in app
assert "$('#panelAction').innerHTML='';" in app

# FABs contextuales de Cuaderno.
assert "journalContext=state.screen==='panel'&&state.lastPanel==='journal'" in app
assert "journalContext?'Nueva entrada':'Añadir evento'" in app
assert "journalContext?'Dictar entrada':'Hablar con Nexo'" in app
assert "if(state.screen==='panel'&&state.lastPanel==='journal'){openJournalComposer();return;}" in app
assert "if(state.journalEditor&&state.screen==='panel'&&state.lastPanel==='journal'){startJournalEditorVoice();return;}" in app

# Editor simple + autoguardado + voz que continúa la entrada abierta.
assert "function flushJournalAutosave()" in app
assert "function scheduleJournalAutosave(" in app
assert "function startJournalEditorVoice()" in app
assert 'id="journalFavorite"' in app
assert 'id="journalRevisit"' in app
assert "Esta entrada no se añade al calendario." in app
assert "openJournalComposer(text,journalKind,journalId" in app

# Lectura antes de edición.
assert 'class="journal-read"' in app
assert 'id="journalEdit"' in app
assert "setTimeout(()=>openJournalComposer('','',Number(j.id)),60)" in app

# Releer es una marca persistente, no una selección aleatoria por antigüedad.
assert "DB_VERSION = 24" in db
assert '"revisit INTEGER NOT NULL DEFAULT 0,"' in db
assert 'ALTER TABLE journal_entries ADD COLUMN revisit INTEGER NOT NULL DEFAULT 0' in db
assert 'v.put("revisit", in.optBoolean("revisit", false) ? 1 : 0);' in db
assert 'if (in.has("revisit")) v.put("revisit", in.optBoolean("revisit", false) ? 1 : 0);' in db
assert 'o.put("revisit",c.getInt(7)==1)' in db
assert "rows=rows.filter(x=>!!x.revisit);" in app

# El micrófono flotante debe quedar por encima del editor.
assert ".journal-editor-open .floating-actions" in css
assert "z-index:130" in css

print("OK: Cuaderno contextual, autoguardado, lectura y Para releer validados")
