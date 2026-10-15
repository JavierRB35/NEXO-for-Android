from pathlib import Path

root=Path(__file__).resolve().parents[1]
db=(root/"app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text(encoding="utf-8")
js=(root/"app/src/main/assets/app.js").read_text(encoding="utf-8")
css=(root/"app/src/main/assets/styles.css").read_text(encoding="utf-8")

# SQLite devuelve la presencia de reglas de aviso junto con el propio evento.
assert "ITEM_SELECT_WITH_REMINDER" in db
assert "EXISTS(SELECT 1 FROM reminders reminder_flag WHERE reminder_flag.item_id=items.id) AS has_reminder" in db
assert 'o.put("hasReminder",reminderIndex>=0 && c.getInt(reminderIndex)==1);' in db

# Los caminos que alimentan Inicio, Agenda, Calendario, detalle y búsqueda usan el flag.
assert 'ITEM_SELECT_WITH_REMINDER + " WHERE items.id=? AND items.deleted_at=0"' in db
assert db.count("ITEM_SELECT_WITH_REMINDER +") >= 5

# La UI solo pinta la campana en eventos/citas con aviso configurado.
assert "function eventReminderBellHtml(item,extraClass='')" in js
assert "if(!item?.hasReminder||!isAppointmentType(item.type))return '';" in js
assert 'aria-label="Aviso configurado"' in js
assert ".event-reminder-indicator" in css

# Inicio: la campana va abajo, en el bloque horario; si hay hora aparece antes del reloj.
# En eventos de día completo puede quedar sola, igual que en Nexo Familia.
home=js[js.index("function homeUpcomingCard"):js.index("function bindHomeUpcoming")]
assert "const reminder=eventReminderBellHtml(item,'event-reminder-indicator--time');" in home
assert "const timeMeta=(reminder||(timed&&hasDate))" in home
assert "reminder+(timed&&hasDate?uiIcon('clock')+esc(timeLabel):'')" in home
assert "upcoming-title-row" not in home

# Agenda/Calendario: la campana acompaña al bloque horario.
agenda=js[js.index("function agendaCard"):js.index("function prepareHomeCapture")]
assert "return homeUpcomingCard(i);" in agenda
assert "eventReminderBellHtml(item,'event-reminder-indicator--time')" in home
assert "event-reminder-indicator--agenda" not in agenda

# Tarjeta genérica: la campana queda integrada en el metadato de fecha/hora.
item=js[js.index("function itemCard"):js.index("function bindItemButtons")]
assert "if(isAppointmentType(i.type))return homeUpcomingCard(i);" in item
assert 'class="meta-tag event-time-meta"' in item
assert "eventReminderBellHtml(i,'event-reminder-indicator--time')" in item

# Búsqueda: no se pega una campana al título cuando no existe bloque horario.
search=js[js.index("function familySearchCard"):js.index("function openFamilySearchResult")]
assert "eventReminderBellHtml" not in search

assert ".event-reminder-indicator--time" in css
assert ".event-time-meta" in css

print("Campanilla de avisos: alineada con la hora en Inicio, Agenda/Calendario y tarjetas OK")
