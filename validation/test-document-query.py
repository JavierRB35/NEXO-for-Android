"""Run the production collection/calendar/reminder SELECTs on mixed legacy SQLite rows."""
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE items(id INTEGER PRIMARY KEY,type TEXT,title TEXT,body TEXT,subcategory TEXT,attachment_path TEXT,attachment_name TEXT,attachment_mime TEXT,due_at INTEGER,end_at INTEGER,recurrence TEXT,recurrence_until INTEGER,updated_at INTEGER,deleted_at INTEGER);
CREATE TABLE reminders(id INTEGER PRIMARY KEY,item_id INTEGER,title TEXT,trigger_at INTEGER,occurrence_at INTEGER,lead_days INTEGER,lead_minutes INTEGER,enabled INTEGER,created_at INTEGER);
''')
db.executemany('INSERT INTO items VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',[
 (1,'document','Póliza','Coberturas','Seguro','/stored/p.pdf','Póliza.pdf','application/pdf',120,0,'none',0,10,0),
 (2,'document','Contrato','Condiciones','Mi etiqueta','/stored/c.jpg','Contrato.jpg','image/jpeg',120,0,'weekly',999,20,0),
 (3,'event','Cita','Traer informe','','/stored/i.pdf','Informe.pdf','application/pdf',120,0,'none',0,30,0),
 (4,'event','Cita semanal','Revisión','','','','',120,0,'weekly',999,40,0),
 (5,'document','Nuevo','Texto completo','','/stored/n.docx','Texto.docx','application/octet-stream',0,0,'none',0,50,0),
 (6,'document','Borrado','Anterior','','/stored/b.pdf','Borrado.pdf','application/pdf',120,0,'none',0,60,1),
 (7,'document','Sin adjunto antiguo','Detalle','','','','',0,0,'none',0,70,0)])
db.executemany('INSERT INTO reminders VALUES(?,?,?,?,?,?,?,?,?)',[(1,1,'Póliza',110,120,0,10,1,0),(2,2,'Contrato',110,120,0,10,1,0),(3,3,'Cita',110,120,0,10,1,0)])
before='\n'.join(db.iterdump())
collection=re.search(r'if \("documents".equals\(collection\)\) \{\s*return queryItems\("([^"]+)", null, "([^"]+)", (\d+)\)',source)
where,order,limit=collection.groups()
assert [r[0] for r in db.execute(f'SELECT * FROM items WHERE deleted_at=0 AND ({where}) ORDER BY {order} LIMIT {limit}')]==[7,5,2,1]
calendar=source.split('public JSONArray getCalendar(long start, long end)',1)[1].split('public JSONObject getOccurrence',1)[0]
item_select=re.search(r'ITEM_SELECT_WITH_REMINDER\s*=\s*"([^"]+)"',source).group(1)
suffixes=re.findall(r'rawQuery\(ITEM_SELECT_WITH_REMINDER \+ "([^"]+)"',calendar)
assert len(suffixes)==2
queries=[item_select+suffix for suffix in suffixes]
assert [r[0] for r in db.execute(queries[0],(200,100))]==[3]
assert [r[0] for r in db.execute(queries[1],(200,100))]==[4]
reminders=source.split('private JSONArray queryReminders(',1)[1].split('private JSONObject itemJson',1)[0]
query=re.search(r'rawQuery\("([^"]+)" \+ limit',reminders).group(1)+'100'
assert [r[0] for r in db.execute(query,(100,200))]==[3]
archives=source.split('public JSONArray getActiveArchiveReminders()',1)[1].split('\n    }',1)[0]
query=re.search(r'rawQuery\("([^"]+)"',archives).group(1)
assert [r[0] for r in db.execute(query)]==[1,2]
reference=source.split('public boolean isAttachmentReferenced(',1)[1].split('\n    }',1)[0]
query=re.search(r'rawQuery\("([^"]+)"',reference).group(1)
for path in ['/stored/p.pdf','/stored/i.pdf','/stored/b.pdf']:
    assert db.execute(query,(path,)).fetchone() is not None
assert db.execute(query,('/stored/unreferenced.pdf',)).fetchone() is None
assert before=='\n'.join(db.iterdump()),'Reading documents changed existing records'
print('Documents SQL: document collection isolated; dated documents absent from calendar/alarms; shared and Undo references protected')
