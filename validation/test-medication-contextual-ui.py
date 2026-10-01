from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Pantalla Medicación: sin acciones duplicadas arriba.
assert "$('#panelSubtitle').textContent='Tus tratamientos y tomas de hoy.';" in app
assert "$('#panelAction').innerHTML='';" in app
assert 'id="medPanelVoice"' not in app
assert 'id="medPanelAdd"' not in app

# FABs contextuales.
assert "const medicationsContext=state.screen==='panel'&&state.lastPanel==='medications';" in app
assert "medicationsContext?'Hablar con Medicación':'Hablar con Nexo'" in app
assert "medicationsContext?'Nuevo medicamento':'Añadir evento'" in app
assert "if(state.screen==='panel'&&state.lastPanel==='medications'){openMedicationComposer();return;}" in app
assert "if(state.medicationEditor&&state.screen==='panel'&&state.lastPanel==='medications'){startMedicationEditorVoice();return;}" in app

# Voz de Medicación: nueva pauta, estado de toma y edición del formulario.
assert "function handleMedicationVoice(text)" in app
assert "function medicationVoiceDoseAction(text)" in app
assert "function medicationDoseVoiceCandidates(text)" in app
assert "function applyMedicationDoseVoiceStatus(dose,status)" in app
assert "function applyMedicationVoiceToEditor(text)" in app
assert "function startMedicationEditorVoice()" in app
assert "Revísalo antes de guardar." in app

# El formulario conserva la confirmación explícita y no tiene botón Dictar duplicado.
assert 'id="medVoiceBtn"' not in app
assert 'id="medSave"' in app
assert "Confirmar tratamiento" in app
assert "nunca lo guarda automáticamente" in app

# Aviso hablado compacto + explicación bajo demanda.
assert 'id="medVoiceInfo"' in app
assert ">Avisos por voz</strong>" in app
assert "5 min antes · repetición hasta 30 min" in app
assert ".med-module-note--compact" in css

# El micrófono debe quedar accesible sobre el formulario abierto.
assert ".medication-editor-open .floating-actions" in css
assert "z-index:130" in css

# No se puede interpretar "no he tomado" como tomada.
skipped_pos = app.index("return 'skipped';")
taken_pos = app.index("return 'taken';")
assert skipped_pos < taken_pos

# Los callbacks nativos siguen presentes: no se ha cortado NexoUI.
assert "window.NexoUI={" in app
assert "onVoiceTranscribed(text)" in app

print("OK: Medicación contextual, sin duplicados y con voz específica validada")
