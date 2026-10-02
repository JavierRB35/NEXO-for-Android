from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Pantalla Medicación: sin acciones duplicadas en cabecera.
assert "$('#panelSubtitle').textContent='Tus tratamientos y tomas de hoy.';" in app
assert "state.lastPanel='medications'" in app
assert "id=\"medPanelVoice\"" not in app
assert "id=\"medPanelAdd\"" not in app
assert "med-module-note--compact" in app

# FAB + y micrófono son contextuales.
assert "panel==='medications'?'Nuevo medicamento':" in app
assert "panel==='medications'?'Hablar con Medicación':" in app
assert "if(state.screen==='panel'&&state.lastPanel==='medications'){openMedicationComposer();return;}" in app
assert "if(state.medicationEditor&&state.screen==='panel'&&state.lastPanel==='medications'){startMedicationEditorVoice();return;}" in app

# El formulario no duplica la entrada natural: + abre campos; el micrófono contextual los completa.
assert 'id="medSource"' not in app
assert "function applyMedicationVoiceToEditor(text)" in app
assert "He actualizado el formulario. Revísalo antes de guardar." in app
assert "nunca lo guarda automáticamente" in app

# La voz general de Medicación puede registrar TOMADO/OMITIDO de forma conservadora.
assert "function medicationVoiceDoseAction(text)" in app
assert "function medicationDoseVoiceCandidates(text)" in app
assert "function chooseMedicationDoseForVoice(candidates,status)" in app
assert "setMedicationDoseStatus" in app
assert "no\\s+(?:me\\s+)?(?:la|lo)?\\s*he\\s+tomad" not in app
assert "omit(?:e|ir|ida|ido)" in app

# No inventar una hora de toma.
assert "if(!rows.length)rows=[''];" in app
assert "next.push(next.length?addOneHourTime(next[next.length-1]):'');" in app
assert "function medicationVoiceTimeMinutes(text)" in app
assert "a\\s+las?" in app

# El micrófono permanece accesible dentro del editor, pero + queda oculto.
assert ".medication-editor-open .floating-actions" in css
assert ".medication-editor-open .floating-actions .fab-add" in css
assert "z-index:130" in css

print("OK: Medicación contextual, sin duplicados y con voz conservadora validada")
