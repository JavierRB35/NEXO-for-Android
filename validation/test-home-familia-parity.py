from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')

# Inicio: las cuatro decisiones acordadas.
for token in ['homeTodayCard','homeWeekCard','homeInboxPulseCard','homeCalendarCard']:
    assert token in html, f'Falta tarjeta de Inicio: {token}'
for label in ['Hoy','7 días','Inbox','Calendario']:
    assert label in html
for panel in ['notes','medications','lists','documents']:
    assert f'data-panel="{panel}"' in html
assert 'Buscar persona, tarea, evento, lugar...' in html
assert 'Próximamente' in html and 'homeUpcomingList' in html
assert 'homeAgendaLink' not in html

# Paleta canónica de Nexo Familia: texto, fondo y borde.
themes=[
('#7B1FA2','#F6E8FA','#D9A7E5'),('#F57C00','#FFF0D8','#F7BE73'),
('#C51162','#FFE5F0','#F18AB9'),('#D32F2F','#FFE7E7','#F49A9A'),
('#1976D2','#E5F1FF','#8FC2F5'),('#263238','#ECEFF1','#A9B4B9'),
('#00695C','#DDF5F0','#76C8BA'),('#00838F','#DFF7FA','#75D1D9'),
('#6D4C41','#F4E8E3','#C9A89D'),('#283593','#E7E9FA','#9BA3E1'),
('#1B5E20','#E5F4E6','#91C494'),('#827717','#F4F1D8','#C9C071'),
('#A66A00','#FFF0C9','#E4BE62'),('#E64A19','#FFE8DF','#F3A083'),
('#689F38','#EDF6DF','#AFD17D'),('#008856','#DFF5EA','#72C69F'),
('#546E7A','#E8EEF1','#A5B6BE'),('#64748B','#F1F5F9','#CBD5E1')]
for trio in themes:
    for value in trio:
        assert value in js, f'Falta color canónico {value}'

for icon in ['heart-pulse','cake','plane','briefcase','graduation','home','car','shield','clipboard','cart','party','dumbbell','bell','users']:
    assert f"return '{icon}'" in js, f'Falta icono de categoría {icon}'

# Búsqueda: mismos stopwords, pesos y filtro histórico que Familia.
for word in ["'a'","'al'","'de'","'del'","'el'","'la'","'los'","'las'","'para'","'por'","'un'","'una'"]:
    assert word in js
for score in ['score+=120','score+=80','score+=55','score+=35','score+=70','score+=20','score+=12','score+=7','score+=28','score+=4']:
    assert score in js
for kind in ["kind:'member'","kind:'list'","kind:'medication'","kind:'journal'","kind:'inbox'","kind:'recording'"]:
    assert kind in js, f'Falta dominio global {kind}'
assert 'searchHistorical' in html and 'includeHistorical' in js
assert 'familyStyleSearch(q,false)' in js

# Próximamente usa el resumen ligero: ventana de 7 días, próxima ocurrencia
# por serie y un máximo de 10 tarjetas sin expandir 740 días en JavaScript.
assert 'NexoNative.getHomeDashboard()' in js
assert 'public JSONObject getHomeDashboardData()' in db
assert 'long end7 = today.plusDays(7)' in db
assert 'JSONArray near = getCalendar(start, end7);' in db
assert 'findNextOccurrence(id,end7)' in db
assert 'i<upcoming.size() && i<10' in db
assert 'NexoNative.getCalendar(start,horizon)' not in js
assert 'homeUpcomingCard' in js
assert 'event-participant-avatar' in js
assert '--event-category-border' in js

# Eventos: pasado -> done, hoy/futuro -> pending, recurrente -> pending, cancelado se respeta.
assert "WHEN status='cancelled' THEN 'cancelled'" in db
assert "WHEN recurrence<>'none' THEN 'pending'" in db
assert 'isBeforeToday(effectiveEnd) ? "done" : "pending"' in db
assert 'getFamilyStyleSearchIndex' in db and 'getFamilyStyleSearchIndex' in main

# Inicio reserva la cuarta tarjeta al calendario; la grabadora sigue en Herramientas.
assert "homeCalendarCard" in html and "homeRecordCard" not in html
assert 'data-panel="recordings"' in html and "NexoNative.startRecording()" in js

print('Inicio, categorías, búsqueda, agenda y ciclo temporal con paridad Nexo Familia: OK')
