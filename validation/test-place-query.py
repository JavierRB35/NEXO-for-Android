"""Run production SELECTs against temporary SQLite data; no Android runtime."""
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE items(id INTEGER PRIMARY KEY,type TEXT,title TEXT,body TEXT,subcategory TEXT,due_at INTEGER,recurrence TEXT,recurrence_until INTEGER,updated_at INTEGER,deleted_at INTEGER);
CREATE TABLE reminders(id INTEGER PRIMARY KEY,item_id INTEGER,title TEXT,trigger_at INTEGER,occurrence_at INTEGER,lead_days INTEGER,lead_minutes INTEGER,enabled INTEGER,created_at INTEGER);
''')
db.executemany('INSERT INTO items VALUES(?,?,?,?,?,?,?,?,?,?)',[
 (1,'place','Playa','Descripción','Quiero ir',120,'none',0,10,0),
 (2,'restaurant','Restaurante antiguo','Paella','',120,'weekly',999,20,0),
 (3,'event','Cita','Visita','',120,'none',0,30,0),
 (4,'event','Cita semanal','Revisión','',120,'weekly',999,40,0),
 (5,'place','Lugar nuevo','Vistas','He estado',0,'none',0,50,0),
 (6,'place','Lugar borrado','Anterior','',120,'none',0,60,1)])
db.executemany('INSERT INTO reminders VALUES(?,?,?,?,?,?,?,?,?)',[(1,1,'Playa',110,120,0,10,1,0),(2,2,'Restaurante',110,120,0,10,1,0),(3,3,'Cita',110,120,0,10,1,0)])
before='\n'.join(db.iterdump())
places=re.search(r'if \("places".equals\(collection\)\) \{\s*return queryItems\("([^"]+)", null, "([^"]+)", (\d+)\)',source)
assert places
where,order,limit=places.groups()
assert [r[0] for r in db.execute(f'SELECT * FROM items WHERE deleted_at=0 AND ({where}) ORDER BY {order} LIMIT {limit}')]==[5,2,1]
calendar=source.split('public JSONArray getCalendar(long start, long end)',1)[1].split('public JSONObject getOccurrence',1)[0]
queries=re.findall(r'rawQuery\("([^"]+)"',calendar)
assert len(queries)==2
assert [r[0] for r in db.execute(queries[0],(100,200))]==[3]
assert [r[0] for r in db.execute(queries[1],(200,100))]==[4]
reminders=source.split('private JSONArray queryReminders(',1)[1].split('private JSONObject itemJson',1)[0]
query=re.search(r'rawQuery\("([^"]+)" \+ limit',reminders).group(1)+'100'
assert [r[0] for r in db.execute(query,(100,200))]==[3]
place_alarms=source.split('public JSONArray getActiveArchiveReminders()',1)[1].split('\n    }',1)[0]
query=re.search(r'rawQuery\("([^"]+)"',place_alarms).group(1)
assert [r[0] for r in db.execute(query)]==[1,2]
assert before=='\n'.join(db.iterdump()),'Collection reads changed old records'
assert 'if (in.has("subcategory")) v.put("subcategory", in.optString("subcategory", ""));' in source
receiver=(root/'app/src/main/java/com/jruiz/nexo/reminders/AlarmReceiver.java').read_text()
guard=receiver.index('if (PlaceEntry.isPlace(')
assert guard<receiver.index('NotificationManager nm')
assert 'return;' in receiver[guard:receiver.index('NotificationManager nm')]
print('Places SQL: old/new cards retained; dated places excluded from both calendar queries and future alerts; labels writable; receiver guard present')
