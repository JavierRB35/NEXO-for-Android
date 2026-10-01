from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")

# En la pantalla Notas, ambos flotantes deben ser estrictamente contextuales.
assert "const notesContext=state.screen==='panel'&&state.lastPanel==='notes';" in app
assert "notesContext?'Dictar nota':'Hablar con Nexo'" in app
assert "notesContext?'Nueva nota':'Añadir evento'" in app
assert "if(state.screen==='panel'&&state.lastPanel==='notes'){openNoteComposer();return;}" in app
assert "if(state.screen==='panel'&&state.lastPanel==='notes'){startNoteVoice();return;}" in app

# No cambiar todavía el comportamiento de las demás pantallas en esta iteración.
assert "if(state.screen==='panel'&&state.lastPanel==='medications'){startMedicationVoice('');return;}" in app
assert "if(state.screen==='panel'&&state.lastPanel==='quotes'){startJournalVoice('','quote');return;}" in app

print("OK: flotantes contextuales de Notas validados")
