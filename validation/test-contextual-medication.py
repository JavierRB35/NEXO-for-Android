from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Pantalla principal: sin controles duplicados.
assert 'id="medPanelVoice"' not in app
assert 'id="medPanelAdd"' not in app
assert "$('#panelSubtitle').textContent='Tus tratamientos y tomas de hoy.';" in app
assert 'id="medVoiceInfo"' in app
assert '5 min antes · repetición hasta 30 min' in app

# Flotantes estrictamente contextuales.
assert "panel==='medications'?'Nuevo medicamento':" in app
assert "panel==='medications'?'Hablar con Medicación':" in app
assert "if(state.screen==='panel'&&state.lastPanel==='medications'){openMedicationComposer();return;}" in app
assert "if(state.screen==='panel'&&state.lastPanel==='medications'){startMedicationVoice('');return;}" in app

# Dentro del formulario, el micrófono completa la pauta pero no guarda.
assert "function startMedicationEditorVoice()" in app
assert "function applyMedicationVoiceToEditor(text)" in app
assert "medicationEditorActive?'Completar pauta por voz'" in app
assert "He actualizado el formulario. Revísalo antes de guardar." in app
assert "El micrófono flotante puede completar este formulario, pero nunca lo guarda automáticamente." in app

# Dictado general: puede proponer una pauta o actuar sobre tomas de hoy.
assert "function handleMedicationVoice(text)" in app
assert "function medicationVoiceDoseAction(text)" in app
assert "function medicationDoseVoiceCandidates(text)" in app
assert "NexoNative.setMedicationDoseStatus" in app
assert "openMedicationComposer(text);" in app

# Seguridad: no inventar una hora de toma y no confundir una dosis con una hora.
assert "if(!rows.length)rows=[''];" in app
assert "if(!rows.length)rows=['08:00'];" not in app
assert "const numeric=n.match(/\\ba\\s+las?\\s+" in app
assert "||n.match(/\\b(\\d{1,2}):(\\d{2})\\b/)" in app
assert "const words={cero:0,una:1" in app

# La alta ya no duplica un campo libre de dictado: para eso está el micrófono flotante.
assert 'id="medSource"' not in app
assert 'id="medVoiceBtn"' not in app

# El micrófono queda accesible por encima del formulario.
assert ".medication-editor-open .floating-actions" in css
assert ".medication-editor-open .floating-actions .fab-add" in css

print("OK: Medicación contextual, sin duplicados y con revisión de pauta validada")
