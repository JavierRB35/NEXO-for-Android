from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
db = (root / "app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text()
assert "DB_VERSION = 33" in db
assert "if (oldVersion < 33)" in db

for table, second in (("books", "author"), ("media_items", "creator")):
    # Comprobar el esquema de una instalación nueva leyendo su SQL Java real.
    start = db.index('db.execSQL("CREATE TABLE IF NOT EXISTS ' + table + ' (')
    stop = db.index(');', start)
    sql = "".join(re.findall(r'"([^"]*)"', db[start:stop]))
    con = sqlite3.connect(":memory:")
    con.execute(sql)
    columns = [row[1] for row in con.execute("PRAGMA table_info(" + table + ")")]
    assert "synopsis" in columns and "summary" in columns
    # Simular una base de datos anterior v32, sin perder los datos previos.
    legacy_sql = sql.replace("synopsis TEXT NOT NULL DEFAULT '',", "")
    assert legacy_sql != sql
    old = sqlite3.connect(":memory:")
    old.execute(legacy_sql)
    old.execute(
        f"INSERT INTO {table}(title,{second},status,summary,category,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
        ("Título anterior", "Autor", "finished", "Notas previas", "Historia", 10, 20),
    )
    token = 'db.execSQL("ALTER TABLE ' + table + ' ADD COLUMN synopsis '
    assert token in db
    tail = db.split(token, 1)[1].split('")', 1)[0]
    old.execute("ALTER TABLE " + table + " ADD COLUMN synopsis " + tail)
    row = old.execute(f"SELECT title,{second},status,summary,category,synopsis,created_at,updated_at FROM {table}").fetchone()
    assert row == ("Título anterior", "Autor", "finished", "Notas previas", "Historia", "", 10, 20), row
    old.execute(f"UPDATE {table} SET synopsis=?", ("Argumento escrito antes de leer o ver",))
    assert old.execute(f"SELECT synopsis,summary FROM {table}").fetchone() == (
        "Argumento escrito antes de leer o ver", "Notas previas"
    )
    old.close()
    con.close()

for kind, ui, rep in (
    ("books", "app/src/main/assets/books.js", "BookRepository.java"),
    ("media", "app/src/main/assets/media.js", "MediaRepository.java"),
):
    js = (root / ui).read_text()
    src = (root / "app/src/main/java/com/jruiz/nexo/data" / rep).read_text()
    for token in ('id="bookSynopsis"', "synopsis:document.querySelector('#bookSynopsis')", "source.synopsis",
                  "b.synopsis?esc(b.synopsis)", "live.synopsis?'Argumento / sinopsis:"):
        assert token in js, (kind, token)
    for token in ('o.put("synopsis",', 'v.put("synopsis", synopsis)', 'synopsis.length() > 20000'):
        assert token in src, (kind, token)
    assert js.index('id="bookSynopsis"') < js.index('id="bookReadingFields"'), kind
    assert src.index('v.put("synopsis", synopsis)') > src.index('v.put("summary", summary)'), kind

print("OK: esquema 33, upgrade 32→33 sin pérdida y sinopsis independiente de las notas")
