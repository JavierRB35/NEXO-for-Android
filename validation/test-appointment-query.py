"""Execute the production appointment SELECT against a temporary SQLite fixture.

Validates inclusion, uniqueness and read-only access, not Android cursor bindings.
"""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
method = source.split('public JSONArray getAppointments() {', 1)[1].split('\n    }', 1)[0]
match = re.search(r'return queryItems\("([^"]+)", null,\s*"([^"]+)",\s*(-?\d+)\);', method)
assert match, 'Appointment query not found'
where, order, limit = match.groups()
builder = source.split('private JSONArray queryItems(', 1)[1].split('\n    }', 1)[0]
assert 'ITEM_SELECT_WITH_REMINDER + " WHERE items.deleted_at=0 AND (" + where + ") ORDER BY " + order + " LIMIT " + limit' in builder
query = f'''SELECT items.*,
EXISTS(SELECT 1 FROM reminders reminder_flag WHERE reminder_flag.item_id=items.id) AS has_reminder
FROM items WHERE items.deleted_at=0 AND ({where}) ORDER BY {order} LIMIT {limit}'''
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE items(id INTEGER PRIMARY KEY, type TEXT, title TEXT, body TEXT, due_at INTEGER, status TEXT, created_at INTEGER, deleted_at INTEGER DEFAULT 0);
CREATE TABLE reminders(id INTEGER PRIMARY KEY, item_id INTEGER, lead_minutes INTEGER, enabled INTEGER);
CREATE TABLE recurrence_exceptions(series_id INTEGER, occurrence_at INTEGER, state TEXT, override_json TEXT);
''')
rows = []
for i in range(1, 306):
    typ = ['event', 'task', 'reminder', 'expiry'][(i - 1) % 4]
    rows.append((i, typ, f'Registro {i}', f'Detalle completo {i}', 0 if i % 5 == 0 else 1000 + i, 'done' if i % 7 == 0 else 'pending', i, 0))
rows.extend([(400, 'note', 'Nota', 'Nota independiente', 0, 'active', 400, 0),
             (401, 'document', 'Contrato', 'Documento', 5000, 'active', 401, 0),
             (402, 'task', 'Eliminada', 'Papelera', 2000, 'pending', 402, 123)])
db.executemany('INSERT INTO items VALUES(?,?,?,?,?,?,?,?)', rows)
db.executemany('INSERT INTO reminders VALUES(?,?,?,?)', [(1, 1, 0, 1), (2, 1, 60, 1), (3, 1, 1440, 1)])
db.execute('INSERT INTO recurrence_exceptions VALUES(?,?,?,?)', (2, 9000, 'done', '{"body":"Texto de la excepción"}'))
before = '\n'.join(db.iterdump())
found = db.execute(query).fetchall()
assert len(found) == 305, 'Lost records or one row per alert'
assert {r[0] for r in found} == set(range(1, 306)), 'Missing or duplicated legacy ids'
assert {r[1] for r in found} == {'event', 'task', 'reminder', 'expiry'}
assert any(r[4] == 0 for r in found) and any(r[5] == 'done' for r in found)
assert found[-1][5] == 'done', 'Completed appointments should follow pending ones'
flags={r[0]:r[8] for r in found}
assert flags[1] == 1 and flags[2] == 0, 'Reminder presence flag is incorrect'
assert before == '\n'.join(db.iterdump()), 'Reading Citas modified saved data or alert rules'
print('Appointment SQL: 305 records, reminder flag, undated/completed, alerts and exceptions preserved; no duplicates')
