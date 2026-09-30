from pathlib import Path
root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

# Estado manual gana a la inferencia temporal.
assert 'in.has("dueAt") && !in.has("status")' in db
assert '"WHEN status=\'done\' THEN \'done\' "' in db
assert '"WHEN status=\'postponed\' THEN \'postponed\' "' in db
assert 'else if ("done".equals(current) || "postponed".equals(current))' in db

# Guardar arriba y sin duplicado inferior. La misma barra se reutiliza
# para eventos normales y apariciones recurrentes.
assert 'function recordEditorActionBar' in js
assert 'class="record-form-actions"' in js
item=js[js.index('function openItem(id)'):js.index('function openOccurrence',js.index('function openItem(id)'))]
assert "recordEditorActionBar('saveEditBtn','editItemTopBtn','shareItemBtn','deleteItemBtn')" in item
assert item.count("'saveEditBtn'") == 1
assert 'id="saveEditBtn"' not in item
occ=js[js.index('function openOccurrence'):js.index('function chooseRecurrenceScope',js.index('function openOccurrence'))]
assert "recordEditorActionBar('saveOccurrenceBtn','editOccurrenceTopBtn','shareOccurrenceBtn','deleteOccurrenceBtn')" in occ
assert occ.count("'saveOccurrenceBtn'") == 1
assert 'id="saveOccurrenceBtn"' not in occ
assert '<div class="modal-actions wrap-actions"></div>' not in occ
assert '.record-form-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))' in css
assert '@media(min-width:560px){.record-form-actions{grid-template-columns:repeat(4,minmax(0,1fr))}}' in css

# Maps integrado en ubicación y sin campo visible independiente de enlace.
assert 'class="location-input-row"' in html
assert 'id="captureMapPreview"' in html and 'id="captureMapsUrl" type="hidden"' in html
assert 'placeholder="Dirección o enlace de Google Maps"' in html
for token in ['id="editLocationMapBtn"','id="occLocationMapBtn"','id="editMapsUrl" type="hidden"','id="occMapsUrl" type="hidden"']:
    assert token in js, token
assert 'function bindEventLocationField' in js
assert "bindEventLocationField('#captureLocation','#captureMapsUrl','#captureMapPreview'" in js
assert "bindEventLocationField('#editLocation','#editMapsUrl','#editLocationMapBtn')" in item
assert "bindEventLocationField('#occLocation','#occMapsUrl','#occLocationMapBtn')" in occ
assert "if(maps)maps.value='';" in js  # al editar ubicación no prevalece un enlace heredado obsoleto
assert "item?.locationText||item?.mapsUrl" in js  # enlaces antiguos siguen siendo visibles/compatibles
assert "if(isGoogleMapsLocation(place)){NexoNative.openPlaceMap(place);return;}" in js
assert 'Enlace de Google Maps' not in html
assert '<label>Enlace de Google Maps' not in item
assert '<label>Enlace de Google Maps' not in occ

# Inicio: Calendario abre Agenda, Ver agenda desaparece y mes no se fuerza a mayúsculas.
assert 'id="homeAgendaLink"' not in html
assert "$('#homeCalendarCard')?.addEventListener('click',()=>{state.appointmentsWindow='';openPanel('appointments');});" in js
assert "monthRaw.toLocaleUpperCase" not in js[js.index('const monthRaw='):js.index('const members=',js.index('const monthRaw='))]
assert "monthRaw.replace(/^./" in js
assert 'v3.7.0-beta17' in html
print('Beta17: estado, guardar arriba, Maps en ubicación y Agenda desde Inicio OK')
