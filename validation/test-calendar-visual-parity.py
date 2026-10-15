from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')

palette=js.split('const CATEGORY_COLORS=Object.freeze({',1)[1].split('});',1)[0]
tones=dict(re.findall(r"'([^']+)':'(#[0-9A-Fa-f]{6})'",palette))
assert len(tones)==19, f'Faltan colores para las 19 categorías: {len(tones)}'
assert len(set(tones.values()))==19, 'Cada categoría debe tener un color propio'
assert tones['familia']!=tones['salud / medico']
assert tones['casa']!=tones['cumpleanos']

home=js.split('function homeUpcomingCard(item){',1)[1].split('function bindHomeUpcoming',1)[0]
agenda=js.split('function agendaCard(i,showDate=false){',1)[1].split('function prepareHomeCapture',1)[0]
item=js.split('function itemCard(i) {',1)[1].split('function bindItemButtons',1)[0]
binder=js.split('function bindItemButtons(root=document){',1)[1].split('function openItemShareMenu',1)[0]
month=js.split('function renderCalendar(direction=0){',1)[1].split('function renderCalendarDay',1)[0]

assert 'return homeUpcomingCard(i);' in agenda
assert 'if(isAppointmentType(i.type))return homeUpcomingCard(i);' in item
assert 'bindHomeUpcoming(root);' in binder
assert "b.dataset.homeEventBound='1'" in js, 'No duplicar escuchadores al refrescar'
for bad in ('recordIconAction(', 'data-card-delete-item', 'data-share-item', 'data-agenda-options'):
    assert bad not in home, f'Acción redundante en la tarjeta: {bad}'
assert 'data-home-event' in home and 'openEventInAgenda(id,occ,rec);' in js
assert 'date-tile--undated' in home, 'El evento sin fecha no debe mostrar 1970'
assert 'upcoming-card--past' in home
assert 'statusText' in home
assert 'markerLimit=list.length>3?2:3' in month
assert 'calendar-marker-more' in month
assert 'shownMarkers.slice(0,markerLimit).map(calendarMarkerHtml)' in month
assert 'style="--event-category-color:' in home
assert 'familyCategoryColor(item.category)' in js
assert "calendar-day.selected" in css
assert "background:#d6e1ed" in css and "background:#fbfdff" in css
assert '.calendar-category-marker,.calendar-day.selected .calendar-category-marker' in css
assert '.calendar-icon-legend__item .ui-icon' in css
assert '.upcoming-card--past' in css
assert 'attachMonthSwipe(document.querySelector' in js
assert 'aria-label="Iconos de las 19 categorías"' in html
print('Calendario 3.7.19: 19 colores, tarjetas homogéneas, sin acciones y +N: OK')
