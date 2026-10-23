from pathlib import Path
import hashlib

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

# 1. Estado manual gana a la inferencia temporal.
assert 'DB_VERSION = 33' in db
assert 'status_manual INTEGER NOT NULL DEFAULT 0' in db
assert 'WHEN status_manual=1 THEN status' in db
assert 'in.has("dueAt") && !in.has("status") && !old.optBoolean("statusManual", false)' in db
assert 'o.put("statusManual"' in db
for label in ['Pendiente','Completada','Pospuesta','Cancelada']:
    assert '>'+label+'</option>' in js

# 2. Guardar arriba y sin duplicado inferior. La misma barra se reutiliza
# para eventos normales y apariciones recurrentes.
assert 'function recordEditorActionBar' in js
assert 'record-form-actions--compact' in js
item=js[js.index('function openItem(id)'):js.index('function openOccurrence',js.index('function openItem(id)'))]
assert "recordEditorActionBar('saveEditBtn','editItemTopBtn','shareItemBtn','deleteItemBtn',i.type==='event'?'duplicateItemBtn':'',eventHasLocation(i)?'eventDetailMapsBtn':'')" in item
assert item.count("'saveEditBtn'") == 1
assert 'id="saveEditBtn"' not in item
occ=js[js.index('function openOccurrence'):js.index('function chooseRecurrenceScope',js.index('function openOccurrence'))]
assert "recordEditorActionBar('saveOccurrenceBtn','editOccurrenceTopBtn','shareOccurrenceBtn','deleteOccurrenceBtn',i.type==='event'?'duplicateOccurrenceBtn':'',eventHasLocation(i)?'occDetailMapsBtn':'')" in occ
assert occ.count("'saveOccurrenceBtn'") == 1
assert 'id="saveOccurrenceBtn"' not in occ
assert '<div class="modal-actions wrap-actions"></div>' not in occ
assert '.record-form-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))' in css
assert '@media(min-width:560px){.record-form-actions{grid-template-columns:repeat(4,minmax(0,1fr))}}' in css

# 3. Maps integrado en ubicación y sin campo visible independiente de enlace.
assert 'class="location-input-row"' in html
assert 'id="captureMapPreview"' in html and 'id="captureMapsUrl" type="hidden"' in html
assert 'placeholder="Dirección o enlace de Google Maps"' in html
for token in ['id="editLocationMapBtn"','id="occLocationMapBtn"','id="editMapsUrl" type="hidden"','id="occMapsUrl" type="hidden"']:
    assert token in js, token
assert "eventLocationShortcutHtml(i,'eventDetailLocationBtn')" in item
assert "eventLocationShortcutHtml(i,'occDetailLocationBtn')" in occ
assert "bindEventMapActions(i,'eventDetailMapsBtn','eventDetailLocationBtn')" in item
assert "bindEventMapActions(i,'occDetailMapsBtn','occDetailLocationBtn')" in occ
assert 'function bindEventLocationField' in js
assert "bindEventLocationField('#captureLocation','#captureMapsUrl','#captureMapPreview'" in js
assert "bindEventLocationField('#editLocation','#editMapsUrl','#editLocationMapBtn')" in item
assert "bindEventLocationField('#occLocation','#occMapsUrl','#occLocationMapBtn')" in occ
assert "if(maps)maps.value='';" in js
assert "item?.locationText||item?.mapsUrl" in js
assert "if(maps||place){NexoNative.openLocationDirections(maps||place);return;}" in js
assert 'Enlace de Google Maps' not in html
assert '<label>Enlace de Google Maps' not in item
assert '<label>Enlace de Google Maps' not in occ

# 4. Calendario de Inicio abre Agenda; el acceso redundante desaparece.
assert 'id="homeAgendaLink"' not in html
assert 'Ver agenda' not in html
assert "$('#homeCalendarCard')?.addEventListener('click',()=>{state.appointmentsWindow='';state.appointmentsFilter='pending';openPanel('appointments');});" in js
assert '<button class="nav-item" data-nav="calendar">' in html
assert "navigate(b.dataset.nav,{root:true})" in js
assert "function openCalendar(returnScreen='today',selectedDate='')" in js and "navigate('calendar');" in js

# 5. Mes abreviado: inicial mayúscula y resto minúscula.
home_card=js[js.index('function homeUpcomingCard'):js.index('function bindHomeUpcoming',js.index('function homeUpcomingCard'))]
assert "const monthRaw=" in home_card
assert ".toLocaleLowerCase('es-ES')" in home_card
assert "const month=monthRaw.replace(/^./,c=>c.toLocaleUpperCase('es-ES'));" in home_card

# Branding actual: cabecera + icono adaptativo Android.
screen_logo=root/'app/src/main/assets/nexo-toolbar-logo.png'
launcher_foreground=root/'app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png'
launcher_xml=(root/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').read_text(encoding='utf-8')
launcher_round_xml=(root/'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml').read_text(encoding='utf-8')
colors=(root/'app/src/main/res/values/colors.xml').read_text(encoding='utf-8')
assert '<img class="brand-logo" src="nexo-toolbar-logo.png"' in html
assert screen_logo.exists() and launcher_foreground.exists()
assert screen_logo.stat().st_size > 1000
assert launcher_foreground.stat().st_size > 1000
assert '@drawable/ic_launcher_foreground' in launcher_xml and '@drawable/ic_launcher_foreground' in launcher_round_xml
assert '@color/nexo_launcher_background' in launcher_xml and '@color/nexo_launcher_background' in launcher_round_xml
assert '<color name="nexo_launcher_background">#F4FBFF</color>' in colors
assert 'v3.7.54' in html

print('Beta17: criterios de aceptación + mes de Inicio + branding actual OK')
