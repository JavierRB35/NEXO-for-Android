import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
seed_path=root/'app/src/main/assets/nexo_familia_seed_v1.json'
db_path=root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java'
main_path=root/'app/src/main/java/com/jruiz/nexo/MainActivity.java'
importer_path=root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java'

payload=json.loads(seed_path.read_text(encoding='utf-8'))
assert payload.get('format')=='nexo-familia-events-v1'
events=payload.get('events') or []
rules=payload.get('reminderRules') or []
members=payload.get('members') or []

assert len(events)==33, f'Esperados 33 eventos, encontrados {len(events)}'
assert len(rules)==46, f'Esperadas 46 reglas de aviso, encontradas {len(rules)}'
assert len(members)==4, f'Esperados 4 miembros, encontrados {len(members)}'

ids=[e.get('id') for e in events]
assert all(ids) and len(ids)==len(set(ids)), 'UUID de eventos vacíos o duplicados'
event_ids=set(ids)
assert all(r.get('event_id') in event_ids for r in rules), 'Hay avisos que apuntan a eventos no incluidos'

for e in events:
    assert e.get('title'), f'Evento sin título: {e.get("id")}'
    assert e.get('start_date') or e.get('start_at'), f'Evento sin inicio: {e.get("id")}'
    assert e.get('time_zone') or e.get('all_day'), f'Evento sin zona horaria: {e.get("id")}'

db=db_path.read_text(encoding='utf-8')
for field in ['end_at','all_day','time_zone','maps_url','recurrence_rule','participants_text']:
    assert field in db, f'Falta campo local compatible: {field}'
assert 'DB_VERSION = 20' in db

main=main_path.read_text(encoding='utf-8')
assert 'applyBundledNexoFamiliaMigration();' in main
assert 'nexo_familia_seed_v1.json' in main

importer=importer_path.read_text(encoding='utf-8')
for key in ['endAt','allDay','timeZone','mapsUrl','recurrenceRule','participantsText']:
    assert f'local.put("{key}"' in importer, f'El importador no preserva {key}'

print(f'Migración Nexo Familia OK: {len(events)} eventos, {len(rules)} avisos, {len(members)} miembros')
