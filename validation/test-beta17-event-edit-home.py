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

# Guardar arriba y sin duplicado inferior.
item=js[js.index('function openItem(id)'):js.index('function openOccurrence',js.index('function openItem(id)'))]
assert item.index('id="saveEditBtn"') < item.index('<details class="details-box"')
assert item.count('id="saveEditBtn"') == 1
occ=js[js.index('function openOccurrence'):js.index('function chooseRecurrenceScope',js.index('function openOccurrence'))]
assert occ.index('id="saveOccurrenceBtn"') < occ.index('<details class="details-box"')
assert occ.count('id="saveOccurrenceBtn"') == 1
assert 'grid-template-columns:repeat(2,minmax(0,1fr))' in css

# Maps integrado en ubicación y sin campo visible de enlace.
assert 'class="location-input-row"' in html
assert 'id="captureMapPreview"' in html and 'id="captureMapsUrl" type="hidden"' in html
for token in ['id="editLocationMapBtn"','id="occLocationMapBtn"','id="editMapsUrl" type="hidden"','id="occMapsUrl" type="hidden"']:
    assert token in js, token
assert 'Enlace de Google Maps' not in html
assert 'Enlace de Google Maps' not in item
assert 'Enlace de Google Maps' not in occ

# Inicio: Calendario abre Agenda, Ver agenda desaparece y mes no se fuerza a mayúsculas.
assert 'id="homeAgendaLink"' not in html
assert "$('#homeCalendarCard')?.addEventListener('click',()=>{state.appointmentsWindow='';openPanel('appointments');});" in js
assert "toLocaleUpperCase('es-ES')" not in js[js.index('const monthRaw='):js.index('const members=',js.index('const monthRaw='))]
assert "monthRaw.replace(/^./" in js
assert 'v3.7.0-beta17' in html
print('Beta17: estado, guardar arriba, Maps en ubicación y Agenda desde Inicio OK')
