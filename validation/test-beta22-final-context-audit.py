from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Captura universal: revisión común y controles de Agenda solo para tipos de Agenda.
assert 'id="captureRouteHint"' in html
assert 'id="captureEventFields"' in html
assert "state.screen==='save'?'Dictar en esta captura':" in app
assert "function updateCaptureReviewMode()" in app
assert "$('#captureEventFields')?.classList.toggle('hidden',!appointment);" in app
assert "const date=appointment?$('#captureDate').value:'';" in app
assert "const alerts=appointment?normalizeAlertMinutes(state.captureAlerts):[];" in app
assert "locationText:appointment?$('#captureLocation').value.trim():''" in app
assert "participantIds:appointment?normalizeParticipantIds(state.captureParticipantIds):[]" in app
assert "if(type==='medication')" in app and "openMedicationComposer(text||$('#captureDescription').value.trim())" in app

# Ajustes configura; Inbox ejecuta el escaneo manual.
assert "Capturas e Inbox" in html
assert 'id="inputHubScanBtn"' not in html
assert 'id="gmailScanNowBtn"' not in html
assert 'data-settings-accordion="data"' not in html
assert "else if(panel==='inbox') renderInboxPanel();" in app
assert "renderInboxPanel();NexoNative.scanInputHubNow();" not in app
assert "id=\"inboxScanNowBtn\"" in app
assert "$('#inboxScanNowBtn')?.addEventListener('click',()=>{NexoNative.scanInputHubNow();" in app

# Editores contextuales y acciones no duplicadas.
assert "const noteEditorActive=!!state.noteEditor&&panel==='notes'" in app
assert "function startNoteEditorVoice()" in app
assert 'id="noteVoiceBtn"' not in app
assert 'data-document-open="${id}">Abrir archivo' not in app
assert 'class="document-open" data-document-open="${id}"' in app

# Pantallas de consulta/sistema sin flotantes.
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in app
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in app
assert "if(panel==='parking'){voiceVisible=false;}" in app
assert "if(panel==='recordings'){voiceVisible=!modalOpen&&!recordingActive;addVisible=false;}" in app

# Navegación y clasificación de Herramientas.
order = ["Para no olvidar", "Ideas y escritos", "Mi salud", "Mis lugares", "Datos y seguridad"]
positions = [html.index(x) for x in order]
assert positions == sorted(positions)
for panel in ("notes","recordings","lists","documents","journal","quotes","medications","places","parking","history","backup"):
    assert f'data-panel="{panel}"' in html

# Jerarquía visual verde acordada.
assert "#homeNowTitle" in css
assert ".tool-group .section-heading h2" in css

print("OK: auditoría contextual beta22 completada")
