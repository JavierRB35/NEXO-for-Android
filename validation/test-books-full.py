from pathlib import Path
import sqlite3
import re

root=Path(__file__).resolve().parents[1]
books=(root/"app/src/main/assets/books.js").read_text()
css=(root/"app/src/main/assets/styles.css").read_text()
index=(root/"app/src/main/assets/index.html").read_text()
db=(root/"app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java").read_text()
repository=(root/"app/src/main/java/com/jruiz/nexo/data/BookRepository.java").read_text()
gradle=(root/"app/build.gradle").read_text()

for name in ("start_date", "favorite", "rating"):
    assert f'ALTER TABLE books ADD COLUMN {name} ' in db, "Falta migración: "+name
    assert f'"{name} ' in db, "Falta columna en nuevas instalaciones: "+name
assert "DB_VERSION = 34" in db
assert "if (oldVersion < 28)" in db
assert 'columnExists(db, "books", "favorite")' in db
assert "startDate" in repository and "finishDate" in repository and "abandonedDate" in repository
assert "favorite" in repository and "rating" in repository
assert "isDuplicate(sql, id, title, author)" in repository
assert "Normalizer.normalize" in repository
assert 'rating < 0 || rating > 5' in repository
assert "startDate.compareTo(finishDate) > 0" in repository

# Reproducir las instrucciones SQL reales de migración 27→28.
con=sqlite3.connect(":memory:")
con.executescript("""
CREATE TABLE books(
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 title TEXT NOT NULL, author TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'want',
 progress INTEGER NOT NULL DEFAULT 0,
 finish_date TEXT NOT NULL DEFAULT '',
 abandoned_date TEXT NOT NULL DEFAULT '',
 summary TEXT NOT NULL DEFAULT '',
 created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
INSERT INTO books(title,author,status,progress,finish_date,summary,created_at,updated_at)
VALUES('Libro guardado','Autor original','finished',100,'2025-03-07','No perder resumen',1,2);
""")
sqls=re.findall(r'db\.execSQL\("(ALTER TABLE books ADD COLUMN (?:start_date|favorite|rating) [^"]+)"\)',db)
assert len(sqls)==3, sqls
for stmt in sqls:
    con.execute(stmt)
saved=con.execute("SELECT title,author,status,progress,finish_date,summary,start_date,favorite,rating FROM books").fetchone()
assert saved==('Libro guardado','Autor original','finished',100,'2025-03-07','No perder resumen','',0,0),saved

for k in ("function bookDate", "function sortBooks", "function statsText", "function persistDetailChange",
          "data-book-detail-rating", "bookDetailFavorite", "data-book-count",
          "['favorites','Favoritos']", "bookStartDate", "startDate:today()",
          "editor.baseline", "refreshLibrary()", "function openDetails(id)"):
    assert k in books, "Falta UI libros: "+k
assert "return da>db?-1:1" in books, "Fechas recientes primero"
assert "return ub-ua" in books, "Desempate de actualización"
assert "filter==='favorites'" in books
assert "book-card-date" in books
assert 'id="nx-icon-heart"' in index
assert 'id="nx-icon-star"' in index
assert ".book-rating-star.is-set" in css and ".book-favorite-toggle.active" in css
assert "versionCode 163" in gradle and "versionName '3.7.70'" in gradle
print("OK: DB 27→28 sin pérdida, estrellas/corazón independientes, fechas y filtros")

# Los botones interactivos de valoración y favorito pertenecen solo a la ficha.
editor=books.split("  function openEditor(",1)[1].split("  function saveBook()",1)[0]
details=books.split("  function openDetails(id)",1)[1].split("  function readFields()",1)[0]
assert "bookFavorite" not in editor and "data-book-edit-rating" not in editor
assert "bookDetailFavorite" in details and "data-book-detail-rating" in details
assert "book-favorite-edit" not in editor
assert "favorite:editor.favorite" in books and "rating:editor.rating" in books
assert "if(savedId)openDetails(savedId)" not in books
assert "const scrollY=editor.scrollY" in books
assert "requestAnimationFrame(()=>window.scrollTo(0,scrollY))" in books

# UX de Libros: acciones de ficha arriba y guardado fijo, como en eventos.
assert "book-detail-quick-actions record-card-actions" in books
assert "bookDetailShare" in books and "NexoNative.sharePlainText(live.title" in books
assert "icon-action icon-action--danger" in books
assert "book-editor-toolbar" in books
assert 'class="record-form-actions--compact book-editor-actions"' in books
assert 'id="bookSave" class="primary-button nexo-unified-save"' in books
assert 'id="bookCancel" class="secondary-button"' in books
assert 'aria-label="Guardar libro"' in books
assert 'class="nexo-action-label">Guardar libro</span>' not in books
assert "appendChild(document.querySelector('.book-editor-actions'))" in books
assert ".modal-sheet>.book-editor-actions>button" in css
assert 'class="book-card-rating"' in books and "book-card-star" in books
assert ".book-card-rating" in css and ".book-detail-quick-actions" in css
