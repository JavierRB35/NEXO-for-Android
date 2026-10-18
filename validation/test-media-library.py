from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
js = (root / "app/src/main/assets/media.js").read_text()
index = (root / "app/src/main/assets/index.html").read_text()
app = (root / "app/src/main/assets/app.js").read_text()
db = (root / "app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text()
native = (root / "app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text()
media = (root / "app/src/main/java/com/jruiz/nexo/data/MediaRepository.java").read_text()
books = (root / "app/src/main/java/com/jruiz/nexo/data/BookRepository.java").read_text()

assert "DB_VERSION = 32" in db
assert "if (oldVersion < 31)" in db and "createMediaItemsTable(db);" in db
assert 'data-panel="media"' in index and 'src="media.js"' in index
assert "window.NexoMedia.render()" in app and "window.NexoMedia.openEditor()" in app
assert "window.NexoMedia?.preventClose?.()" in app
assert "window.NexoMedia.applyVoiceText" in app
assert "media-summary" in app and "media-summary" in js
assert "window.NexoMedia" in js and "data-media-type-filter" in js
for token in ("getMediaItems", "getMediaItem", "saveMediaItem", "deleteMediaItem"):
    assert "NexoNative." + token in js
    assert "public " in native and token + "(" in native
for token in ("mediaType", "creator", "favorite", "rating", "summary", "startDate",
              "finishDate", "abandonedDate", "progress", "status"):
    assert token in media and token in js, token
for state in ("want", "reading", "finished", "abandoned"):
    assert f'"{state}".equals(status)' in media
assert "mediaType.equals(c.getString(3))" in media
assert "rating < 0 || rating > 5" in media
assert "LocalDate.parse" in media
assert "BookRepository" in books  # El catálogo de libros existente no se sustituye.

# Ejecutar el SQL de creación extraído del código Java, no recrearlo a mano.
start = db.index('db.execSQL("CREATE TABLE IF NOT EXISTS media_items (')
finish = db.index(');', start)
fragments = re.findall(r'"([^"]*)"', db[start:finish])
sql = "".join(fragments)
con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE books(id INTEGER PRIMARY KEY, title TEXT)")
con.execute("INSERT INTO books VALUES (1, 'Libro anterior')")
con.execute(sql)
con.execute("INSERT INTO media_items(title, creator, media_type, status, progress, "
            "finish_date, abandoned_date, summary, start_date, favorite, rating, "
            "created_at, updated_at) VALUES "
            "('Película A', '', 'movie', 'want', 0, '', '', '', '', 0, 0, 1, 1)")
con.execute("INSERT INTO media_items(title, creator, media_type, status, progress, "
            "finish_date, abandoned_date, summary, start_date, favorite, rating, "
            "created_at, updated_at) VALUES "
            "('Serie B', 'Creador', 'series', 'reading', 30, '', '', '', '2026-09-01', 1, 4, 2, 2)")
assert con.execute("SELECT title FROM books").fetchone()[0] == "Libro anterior"
assert con.execute("SELECT media_type, status FROM media_items ORDER BY id").fetchall() == [
    ("movie", "want"), ("series", "reading")
]
print("OK: filmoteca independiente, tablas, migración aditiva, puente Android y UI")
