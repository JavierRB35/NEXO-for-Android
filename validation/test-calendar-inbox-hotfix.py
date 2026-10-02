from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

db = (ROOT / "app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text(encoding="utf-8")
calendar = (ROOT / "app/src/main/java/com/jruiz/nexo/calendar/CalendarWatchEngine.java").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/assets/app.js").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

items_schema = db.split('db.execSQL("CREATE TABLE items (",', 1)[1].split('db.execSQL("CREATE INDEX idx_items_due', 1)[0]
insert_item = db.split("public long insertItem(JSONObject in)", 1)[1].split("public boolean updateItem(JSONObject in)", 1)[0]

assert '"revisit INTEGER' not in items_schema, "items no debe declarar revisit"
assert 'v.put("revisit"' not in insert_item, "insertItem no debe escribir revisit"
assert 'ALTER TABLE journal_entries ADD COLUMN revisit' in db, "Cuaderno mantiene su campo revisit"
assert "public long findMatchingCalendarItem" in db
assert "ORDER BY updated_at DESC LIMIT 2" in db, "el enlace debe rechazar coincidencias ambiguas"
assert calendar.count("findMatchingCalendarItem") >= 3, "Calendar debe enlazar eventos nuevos y estados previos"
assert "normalizedDueAt(event)" in calendar
assert "@JavascriptInterface public long findMatchingCalendarItem" in main
assert "NexoNative.findMatchingCalendarItem" in app, "Inbox debe reparar capturas Calendar antiguas al abrirlas"
assert "versionCode 98" in gradle and "versionName '3.7.5'" in gradle
assert "private static final int DB_VERSION = 25;" in db, "3.7.5 usa migración incremental segura"

print("OK: Calendar/Inbox 3.7.5 validado")
