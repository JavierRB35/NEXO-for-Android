from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Notas: + crea una nota y el micrófono dicta en el contexto de Notas.
assert "panel==='notes'?'Dictar nota':" in app
assert "panel==='notes'?'Nueva nota':" in app
assert "if(state.screen==='panel'&&state.lastPanel==='notes'){openNoteComposer();return;}" in app
assert "if(state.screen==='panel'&&state.lastPanel==='notes'){startNoteVoice();return;}" in app
assert "function renderNotesPanel()" in app and "$('#panelAction').innerHTML='';" in app

# Si el editor está abierto, el mismo FAB de voz actúa sobre ESA nota.
assert "const noteEditorActive=!!state.noteEditor&&panel==='notes'" in app
assert "noteEditorActive?'Dictar nota':" in app
assert "if(state.noteEditor&&state.screen==='panel'&&state.lastPanel==='notes'){startNoteEditorVoice();return;}" in app
assert "function startNoteEditorVoice()" in app
assert 'id="noteVoiceBtn"' not in app
assert 'id="notePanelVoice"' not in app
assert 'id="notePanelAdd"' not in app
assert "document.body.classList.add('note-editor-open')" in app
assert 'requestAnimationFrame(updateFloatingActions);' in app
assert '.note-editor-open .floating-actions{z-index:130}' in css
assert '.note-editor-open .floating-actions .fab-add{display:none!important}' in css
assert "restoreNote=state.voiceMode==='note'" in app
assert "if(restoreNote)openNoteComposer(noteId,noteBody,noteTitle,noteTitleManual);" in app

# Listas: + crea lista; micrófono añade elementos. La cabecera no duplica + Lista.
assert "panel==='lists'?'Añadir elementos por voz':" in app
assert "panel==='lists'?'Nueva lista':" in app
assert "if(state.screen==='panel'&&state.lastPanel==='lists'){openNewListModal();return;}" in app
assert "function chooseListForVoice()" in app
assert "if(state.currentList)startListVoiceFor(state.currentList);else chooseListForVoice();return;" in app
assert 'id="panelAddList"' not in app

print("OK: flotantes contextuales de Notas y Listas validados")
