from pathlib import Path
import re, sqlite3

root=Path(__file__).resolve().parents[1]
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
app=(root/'app/src/main/assets/app.js').read_text()
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
place=(root/'app/src/main/java/com/jruiz/nexo/core/PlaceEntry.java').read_text()
books=(root/'app/src/main/assets/books.js').read_text()

assert 'DB_VERSION = 33' in db and 'if (oldVersion < 29)' in db
for col in ('place_state','place_rating','place_visited_on'):
    assert f'ALTER TABLE items ADD COLUMN {col} ' in db, col
    assert f'"{col} ' in db, col
assert 'placeState' in place and 'placeVisitedOn' in place and 'placeRating' in place
assert 'requestPlaceHere()' in java and 'onPlaceLocation' in java
assert 'onPlaceLocation(raw)' in app and 'onPlaceLocationError(message)' in app
for token in ('placeHere','placeAcceptApproximate','data-place-rating','placeDetailFavorite',
              'data-place-map','placeVisitStatus','placeTypeFilter','data-place-count'):
    assert token in app, token
assert 'if(savedId)openDetails(savedId)' not in books
assert 'requestAnimationFrame(()=>window.scrollTo(0,scrollY))' in books

con=sqlite3.connect(':memory:')
con.executescript("""
CREATE TABLE items(id INTEGER PRIMARY KEY,type TEXT,subcategory TEXT,body TEXT,favorite INTEGER,location_text TEXT);
INSERT INTO items VALUES(1,'place','Quiero ir','Conserva descripción',0,'');
INSERT INTO items VALUES(2,'place','He estado','Viaje',1,'https://maps.app.goo.gl/example');
INSERT INTO items VALUES(3,'restaurant','Restaurante','Paella',0,'');
INSERT INTO items VALUES(4,'restaurant','He estado','Paella nueva',1,'');
""")
statements=re.findall(r'db\.execSQL\("(ALTER TABLE items ADD COLUMN place_[^"]+)"\)',db)
assert len(statements)==3,statements
for sql in statements: con.execute(sql)
migration=db.split('if (oldVersion < 29)',1)[1].split('\n        }',1)[0]
sqls=re.findall(r'db\.execSQL\("(UPDATE items SET place_state[^"]+)"\)',migration)
assert len(sqls)==2,sqls
for sql in sqls:con.execute(sql)
assert con.execute('SELECT place_state FROM items WHERE id=1').fetchone()[0]=='want'
assert con.execute('SELECT place_state FROM items WHERE id=2').fetchone()[0]=='visited'
assert con.execute('SELECT place_state FROM items WHERE id=3').fetchone()[0]==''
assert con.execute('SELECT subcategory FROM items WHERE id=4').fetchone()[0]=='Restaurante'
assert con.execute('SELECT body,favorite,location_text FROM items WHERE id=2').fetchone()==('Viaje',1,'https://maps.app.goo.gl/example')
print('OK: Lugares BD28→29 aditiva, sin perder lugares, GPS, Maps, filtros y retorno de Libros')
