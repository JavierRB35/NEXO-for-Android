from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')

# Inicio: las cuatro decisiones acordadas.
for token in ['homeTodayCard','homeTomorrowCard','homeWeekCard','homeCalendarCard']:
    assert token in html, f'Falta tarjeta de Inicio: {token}'
assert 'homeInboxPulseCard' not in html
for label in ['Hoy','Mañana','7 días','Agenda']:
    assert label in html
assert 'data-nav="inbox"' in html
for panel in ['notes','medications','lists','documents']:
    assert f'data-panel="{panel}"' in html
assert 'Buscar persona, tarea, evento, lugar...' in html
assert 'Próximamente' in html and 'homeUpcomingList' in html
assert 'homeAgendaLink' not in html

# 21 iconos de categoría exclusivos, con colores asignados.
icons={'familia':'users','cumpleanos':'cake','santo / onomastica':'calendar-heart','mi salud':'heart-pulse','acompanar (salud)':'hand-heart','analitica':'syringe-blood','farmacia':'pill','colegio / estudios':'graduation','trabajo':'briefcase','viaje':'plane','vacaciones':'sun','casa':'home','vehiculos / itv':'car','seguros':'shield','tramites':'file-check','tarea / recado':'list-todo','compras':'cart','ocio / social':'party','deporte':'dumbbell','recordatorio':'bell','otro':'shapes'}
assert len(icons)==21 and len(set(icons.values()))==21
for name,icon in icons.items():
    assert f"'{name}':'{icon}'" in js, name
assert 'FAMILY_CATEGORY_THEMES' not in js
assert 'calendar-category-marker' in js and 'calendarIconLegend' in js

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
assert 'long startTomorrow = today.plusDays(1)' in db
assert 'long endTomorrow = today.plusDays(2)' in db
assert 'out.put("tomorrowCount",tomorrowSeries.size())' in db
assert 'long end7 = today.plusDays(7)' in db
assert 'JSONArray near = getCalendar(start, end7);' in db
assert 'findNextOccurrence(id,end7)' in db
assert 'i<upcoming.size() && i<10' in db
assert 'NexoNative.getCalendar(start,horizon)' not in js
assert 'homeUpcomingCard' in js
assert 'event-participant-avatar' in js
assert '--event-category-border' not in js

# Las fichas de Inicio comparten la distribución visual de Nexo Familia:
# categoría bajo el contenido y participantes en la misma línea inferior que avisos/horario.
home_card=js[js.index('function homeUpcomingCard'):js.index('function bindHomeUpcoming',js.index('function homeUpcomingCard'))]
assert home_card.index('upcoming-category-pill') < home_card.index('upcoming-card__aside')
assert home_card.index('eventParticipantAvatarsHtml') < home_card.index('upcoming-card__time')
assert "const timed=!(item.allDay===true||item.hasTime===false);" in home_card
assert "const endAt=Number(item.endAt||0);" in home_card
assert "const endTime=timed&&endAt>Number(item.dueAt)?fmtTime(endAt):'';" in home_card
assert "const timeLabel=endTime?startTime+' – '+endTime:startTime;" in home_card
assert 'upcoming-card__note' in home_card
assert '.upcoming-card__aside .event-participant-avatars' in css

# Eventos: pasado -> done, hoy/futuro -> pending, recurrente -> pending, cancelado se respeta.
assert "WHEN status='cancelled' THEN 'cancelled'" in db
assert "WHEN recurrence<>'none' THEN 'pending'" in db
assert 'isBeforeToday(effectiveEnd) ? "done" : "pending"' in db
assert 'getFamilyStyleSearchIndex' in db and 'getFamilyStyleSearchIndex' in main

# Inicio reserva la cuarta tarjeta a Agenda; la grabadora sigue en Herramientas.
assert "homeCalendarCard" in html and "homeRecordCard" not in html
assert 'data-panel="recordings"' in html and "NexoNative.startRecording()" in js

print('Inicio, categorías, búsqueda, agenda y ciclo temporal con paridad Nexo Familia: OK')
