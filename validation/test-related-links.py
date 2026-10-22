from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

assert 'DB_VERSION = 33' in db
assert 'if (oldVersion < 30)' in db and 'createRecordLinksTable(db)' in db
for token in ['CREATE TABLE IF NOT EXISTS record_links','UNIQUE(owner_item_id,resource_kind,resource_id)',
              'idx_record_links_owner','idx_record_links_resource',
              'isRelatedOwner(ownerId)','allowedResourceKind(kind)',
              'getRelatedResources(long ownerId)','getRelatedOwners(String kind,long resourceId)']:
    assert token in db, token
for method in ('getRelatedResources','getRelatedOwners','linkRelatedResource','unlinkRelatedResource'):
    assert 'public ' in java and method+'(' in java, method
    assert 'NexoNative.'+method+'(' in js, method
for token in ('record-form-actions--compact','record-action-overflow','linked-resource-open',
              'related-picker-tabs','related-creation-form'):
    assert token in css, token
for token in ('relatedLinksSectionHtml(id)','relatedLinksSectionHtml(seriesId)',
              "relatedOwnersSectionHtml('note',Number(id))",
              "relatedOwnersSectionHtml('list',id)",
              'relatedOwnersSectionHtml("document",Number(id))',
              'state.pendingResourceLink','bindRelatedLinks(ownerId)',
              'function openRelatedPicker(ownerId)'):
    assert token in js, token
assert js.count('<details class="details-box" open>')==0, 'El editor de citas debe iniciar plegado'

# Simula duplicados y multiplicidad sobre el mismo DDL SQL que utiliza la migración.
cx=sqlite3.connect(':memory:')
cx.executescript("""
CREATE TABLE record_links (
id INTEGER PRIMARY KEY AUTOINCREMENT,
owner_item_id INTEGER NOT NULL,
resource_kind TEXT NOT NULL,
resource_id INTEGER NOT NULL,
created_at INTEGER NOT NULL,
UNIQUE(owner_item_id,resource_kind,resource_id));
CREATE INDEX idx_record_links_owner ON record_links(owner_item_id);
CREATE INDEX idx_record_links_resource ON record_links(resource_kind,resource_id);
""")
rows=[(42,'list',3,1),(42,'list',4,1),(42,'note',7,1),(42,'document',8,1),(99,'note',7,1)]
cx.executemany('INSERT INTO record_links(owner_item_id,resource_kind,resource_id,created_at) VALUES (?,?,?,?)',rows)
cx.execute('INSERT OR IGNORE INTO record_links(owner_item_id,resource_kind,resource_id,created_at) VALUES (?,?,?,?)',(42,'list',3,1))
assert cx.execute('SELECT COUNT(*) FROM record_links').fetchone()[0]==5
assert cx.execute("SELECT COUNT(*) FROM record_links WHERE resource_kind='note' AND resource_id=7").fetchone()[0]==2
cx.execute("DELETE FROM record_links WHERE owner_item_id=42 AND resource_kind='note' AND resource_id=7")
assert cx.execute('SELECT COUNT(*) FROM record_links').fetchone()[0]==4

# El contador de la tarjeta usa el mismo SELECT que el calendario y se
# calcula en SQLite sin llamadas individuales a la capa web.
import re
m=re.search(r'private static final String ITEM_SELECT_WITH_REMINDER\s*=\s*(.*?);',db,re.S)
assert m, 'Consulta de tarjetas no encontrada'
sql=''.join(re.findall(r'"([^"]*)"',m.group(1)))
assert ' AS link_count FROM items' in sql
assert 'idx_record_links_owner' in db
assert 'o.put("linkCount",linkCountIndex>=0' in db
assert 'JSONObject o = new JSONObject(base.toString());' in db # series heredan el contador
cx2=sqlite3.connect(':memory:')
cx2.executescript("""
CREATE TABLE items (id INTEGER PRIMARY KEY, type TEXT, deleted_at INTEGER);
CREATE TABLE lists (id INTEGER PRIMARY KEY);
CREATE TABLE reminders (item_id INTEGER);
CREATE TABLE record_links (owner_item_id INTEGER,resource_kind TEXT,resource_id INTEGER);
INSERT INTO items VALUES (42,'event',0),(50,'note',0),(51,'document',1),(99,'event',0);
INSERT INTO lists VALUES (7);
INSERT INTO record_links VALUES (42,'list',7),(42,'note',50),(42,'document',51),
  (42,'list',999),(99,'document',51);
""")
rows={row[0]:row[-1] for row in cx2.execute(sql+' WHERE items.id IN (42,99)')}
assert rows=={42:2,99:0},rows
cx2.execute("DELETE FROM record_links WHERE owner_item_id=42 AND resource_kind='note'")
rows={row[0]:row[-1] for row in cx2.execute(sql+' WHERE items.id IN (42,99)')}
assert rows=={42:1,99:0},rows

print('Vínculos Nexo: migración, tipos, duplicados, multiplicidad, navegación y UI: OK')
