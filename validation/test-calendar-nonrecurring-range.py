import sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
db_source=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')

old_sql=(
    "SELECT count(*) FROM items WHERE deleted_at=0 "
    "AND type NOT IN ('place','restaurant','document') "
    "AND due_at>0 AND due_at<=? "
    "AND (CASE WHEN end_at>0 THEN end_at ELSE due_at END)>=? "
    "AND recurrence='none'"
)
new_sql=(
    "SELECT count(*) FROM items WHERE deleted_at=0 "
    "AND type NOT IN ('place','restaurant','document') "
    "AND due_at>0 AND due_at<=CAST(? AS INTEGER) "
    "AND (CASE WHEN end_at>due_at THEN end_at ELSE due_at+1 END)>CAST(? AS INTEGER) "
    "AND recurrence='none'"
)

con=sqlite3.connect(':memory:')
con.execute('''
CREATE TABLE items(
 id INTEGER PRIMARY KEY,
 type TEXT,
 due_at INTEGER,
 end_at INTEGER,
 recurrence TEXT,
 deleted_at INTEGER
)
''')

# 18 eventos no recurrentes dentro del rango. Los parámetros se pasan como str,
# igual que Android rawQuery(..., String[] selectionArgs).
start=1790805600000
end=1793487599999
for i in range(18):
    con.execute(
        "INSERT INTO items(id,type,due_at,end_at,recurrence,deleted_at) VALUES(?,?,?,?,?,0)",
        (i+1,'event',start+86_400_000*(i%20)+3_600_000,0,'none')
    )

old_count=con.execute(old_sql,(str(end),str(start))).fetchone()[0]
new_count=con.execute(new_sql,(str(end),str(start))).fetchone()[0]

assert old_count==0, f'La reproducción del bug debía devolver 0, devolvió {old_count}'
assert new_count==18, f'La consulta corregida debía devolver 18, devolvió {new_count}'

assert 'due_at<=CAST(? AS INTEGER)' in db_source
assert '(CASE WHEN items.end_at>items.due_at THEN items.end_at ELSE items.due_at+1 END)>CAST(? AS INTEGER)' in db_source
assert '(CASE WHEN end_at>0 THEN end_at ELSE due_at END)<CAST(? AS INTEGER)' in db_source

# El final a medianoche es exclusivo y no duplica la cita al día siguiente.
midnight=1793487600000
con.execute("INSERT INTO items(id,type,due_at,end_at,recurrence,deleted_at) VALUES(19,'event',?,?, 'none',0)",(midnight-7200000,midnight))
assert con.execute(new_sql,(str(midnight+86400000-1),str(midnight))).fetchone()[0]==0
# Un instante sin duración, a medianoche, sí pertenece al propio día.
con.execute("INSERT INTO items(id,type,due_at,end_at,recurrence,deleted_at) VALUES(20,'event',?,?, 'none',0)",(midnight,midnight))
assert con.execute(new_sql,(str(midnight+86400000-1),str(midnight))).fetchone()[0]==1

print('Calendario no recurrente: CAST numérico, fin exclusivo y eventos sin duración — OK')
