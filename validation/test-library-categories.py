from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[1]
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
books=(root/'app/src/main/java/com/jruiz/nexo/data/BookRepository.java').read_text()
media=(root/'app/src/main/java/com/jruiz/nexo/data/MediaRepository.java').read_text()
books_js=(root/'app/src/main/assets/books.js').read_text()
media_js=(root/'app/src/main/assets/media.js').read_text()

assert "DB_VERSION = 34" in db and "if (oldVersion < 32)" in db
for table in ("books", "media_items"):
    assert f'columnExists(db, "{table}", "category")' in db
    assert f'ALTER TABLE {table} ADD COLUMN category TEXT NOT NULL DEFAULT' in db
for repo,idx in ((books,13),(media,14)):
    assert f'o.put("category", c.getString({idx}));' in repo
    assert 'v.put("category", category);' in repo
    assert 'category.length() > 80' in repo
for js in (books_js,media_js):
    assert "category:document.querySelector('#bookCategorySelect')" in js
    assert "data-book-open" in js
    assert "bookCategoryCustom" in js and "categoryPresets" in js
    assert "refreshCategoryFilter()" in js
    assert "categoryOf(b)===categoryFilter" in js
    assert "Categoría: '+categoryOf(live)" in js
    assert "book-category-chip" in js
    assert "selectedCategory" in js
assert '"Novela"' in books_js
assert '"Documental"' in media_js and '"Novela"' not in media_js
assert '"Documental"' not in books_js

# Emular datos reales con tablas existentes y ejecutar exactamente las sentencias de la migración.
con=sqlite3.connect(":memory:")
con.executescript("""
CREATE TABLE books(id INTEGER PRIMARY KEY, title TEXT, favorite INTEGER, rating INTEGER);
CREATE TABLE media_items(id INTEGER PRIMARY KEY, title TEXT, favorite INTEGER, rating INTEGER, media_type TEXT);
INSERT INTO books VALUES(7,'Libro antiguo',1,4);
INSERT INTO media_items VALUES(9,'Serie antigua',1,5,'series');
""")
alter=re.findall(r'db\.execSQL\("(ALTER TABLE (?:books|media_items) ADD COLUMN category [^"]+)"\)',db)
assert len(alter)==2,alter
for sql in alter: con.execute(sql)
assert con.execute("SELECT id,title,favorite,rating,category FROM books").fetchone()==(7,'Libro antiguo',1,4,'')
assert con.execute("SELECT id,title,favorite,rating,media_type,category FROM media_items").fetchone()==(9,'Serie antigua',1,5,'series','')
con.execute("UPDATE books SET category='Novela' WHERE id=7")
con.execute("UPDATE media_items SET category='Drama' WHERE id=9")
assert con.execute("SELECT category FROM books").fetchone()[0]=="Novela"
assert con.execute("SELECT category FROM media_items").fetchone()[0]=="Drama"
assert "category TEXT NOT NULL DEFAULT" in db[db.index("private static void createBooksTable"):db.index("private static void createJournalTables")]
print("OK: migración BD 31→32 aditiva, categorías separadas, lectura/edición y filtros")
