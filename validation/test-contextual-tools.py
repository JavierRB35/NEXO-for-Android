from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Frases: solo flotantes para crear/dictar, sin Releer.
assert 'id="journalAddTop"' not in app
assert 'id="journalVoice"' not in app
assert 'id="journalAdd"' not in app
assert "Citas y frases que quieres conservar." in app
assert "function startQuoteEditorVoice()" in app
assert "function applyQuoteEditorVoice(text)" in app

# Grabaciones: micrófono = grabar, sin + ni botón superior.
assert "recordingStartTop" not in app
assert "panel==='recordings'?'Iniciar grabación'" in app
assert "if(panel==='recordings'){voiceVisible=!modalOpen&&!recordingActive;addVisible=false;}" in app

# Lugares: Maps busca por nombre y no existe campo de ubicación.
assert 'id="placeLocation"' not in app
assert "placePreviewMap" not in app
assert "NexoNative.openPlaceMap(String(i.title))" in app
assert 'class="secondary-button place-map-direct"' in app
assert "function startPlaceVoice()" in app
assert "function startPlaceEditorVoice()" in app

# Aparcamiento: solo + contextual; sin micrófono ni botón superior.
assert "parkingPanelSave" not in app
assert "if(panel==='parking')voiceVisible=false;" in app
assert "Actualizar aparcamiento" in app

# Documentos: + guarda; micrófono busca o dicta descripción en editor.
assert "collectionAdd" not in app
assert "function startDocumentSearchVoice()" in app
assert "function startDocumentEditorVoice()" in app
assert "function applyDocumentEditorVoice(text)" in app
assert 'class="document-open" data-document-open=' in app

# Clasificación acordada.
order = ["Para no olvidar", "Ideas y escritos", "Mi salud", "Mis lugares", "Datos y seguridad"]
positions = [html.index(x) for x in order]
assert positions == sorted(positions)
for panel in ("notes","recordings","lists","documents","journal","quotes","medications","places","parking","history","backup"):
    assert f'data-panel="{panel}"' in html

# Historial e Inbox son contextos de consulta: sin flotantes.
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in app
assert "function openHistoryEntry(h)" in app
assert 'data-history-index' in app

# Jerarquía visual verde.
assert "#homeNowTitle" in css
assert ".tool-group .section-heading h2" in css
assert "text-transform:uppercase" in css

print("OK: filosofía contextual y nueva clasificación de Herramientas validadas")
