from pathlib import Path

root=Path(__file__).resolve().parents[1]
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

# Persistencia local independiente de usuarios/cuentas.
assert 'DB_VERSION = 24' in db
assert 'CREATE TABLE IF NOT EXISTS people (' in db
for field in ['name TEXT NOT NULL','color TEXT NOT NULL','archived_at INTEGER NOT NULL DEFAULT 0']:
    assert field in db
assert 'public JSONArray getPeople()' in db
assert 'public JSONObject savePerson(JSONObject in)' in db
assert 'public boolean archivePerson(long id)' in db
assert 'UPDATE people' not in db or 'archived_at' in db
assert 'archived_at=0' in db

# Bridge local.
for bridge in ['getPeople()','getPerson(long id)','savePerson(String json)','archivePerson(long id)']:
    assert bridge in main, bridge

# Ajustes > Personas.
assert 'data-settings-accordion="people"' in html
assert '<strong>Personas</strong><small>Personas relacionadas con tus eventos</small>' in html
assert 'id="peopleAddBtn"' in html
assert 'id="peopleSettingsList"' in html

# Misma paleta de Nexo Familia.
for color in ['#3158D4','#16855B','#7C3AED','#D97706','#DC3F4F','#FACC15','#FFFFFF','#0F8A9D','#DB2777','#475569']:
    assert color in js, color
assert 'function renderPeopleSettings()' in js
assert 'function openPersonEditor(id=0)' in js
assert "NexoNative.savePerson(JSON.stringify" in js
assert 'NexoNative.archivePerson' in js
assert '.nexo-person-row{' in css
assert '.person-color-palette{' in css
assert '.person-color-swatch--selected{' in css

print('Personas locales: alta, edición, color y borrado lógico OK')
