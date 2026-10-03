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
assert "event-reminder-indicator--home" in js
assert "event-reminder-indicator--agenda" in js
assert "event-reminder-indicator--item" in js
assert "event-reminder-indicator--search" in js
assert ".event-reminder-indicator" in css

print("Campanilla de avisos: Inicio, Agenda, Calendario, tarjetas y búsqueda OK")
