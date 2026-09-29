import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
seed_path=root/'app/src/main/assets/nexo_familia_seed_v1.json'
db_path=root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java'
main_path=root/'app/src/main/java/com/jruiz/nexo/MainActivity.java'
importer_path=root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java'

payload=json.loads(seed_path.read_text(encoding='utf-8'))
assert payload.get('format')=='nexo-familia-events-v1'
assert payload.get('snapshotVersion')=='2026-09-29-v5-clean-rebuild'
events=payload.get('events') or []
rules=payload.get('reminderRules') or []
members=payload.get('members') or []
audit=payload.get('sourceAudit') or {}

assert len(events)==33, f'Esperados 33 eventos, encontrados {len(events)}'
assert sum(1 for e in events if e.get('visibility')=='family')==17, 'Esperados 17 eventos family'
assert sum(1 for e in events if e.get('visibility')=='selected')==16, 'Esperados 16 eventos selected'
assert len(rules)==46, f'Esperadas 46 reglas de aviso, encontradas {len(rules)}'
assert len(members)==4, f'Esperados 4 miembros, encontrados {len(members)}'
assert audit.get('familyEventCount')==33
assert audit.get('reminderRuleCount')==46
assert audit.get('memberCount')==4

event_keys={
    'id','family_id','created_by','title','description','start_at','end_at','all_day',
    'start_date','end_date','location','category','status','visibility','shared_with',
    'participant_ids','recurrence_rule','created_at','updated_at','external_uid',
    'external_source','event_origin','social_type','venue_type','maps_url','guest_count',
    'guest_details','time_zone'
}
rule_keys={
    'id','family_id','event_id','minutes_before','remind_at','status','created_by',
    'sent_at','created_at','updated_at','delivery_attempts','next_attempt_at',
    'claimed_at','claim_token'
}

ids=[e.get('id') for e in events]
assert all(ids) and len(ids)==len(set(ids)), 'UUID de eventos vacíos o duplicados'
event_ids=set(ids)
rule_ids=[r.get('id') for r in rules]
assert all(rule_ids) and len(rule_ids)==len(set(rule_ids)), 'UUID de reglas vacíos o duplicados'
assert all(r.get('event_id') in event_ids for r in rules), 'Hay avisos que apuntan a eventos no incluidos'

for e in events:
    missing=event_keys-set(e)
    assert not missing, f'Evento {e.get("id")} pierde campos fuente: {sorted(missing)}'
    assert e.get('title'), f'Evento sin título: {e.get("id")}'
    assert e.get('start_date') or e.get('start_at'), f'Evento sin inicio: {e.get("id")}'
    assert e.get('time_zone'), f'Evento sin zona horaria: {e.get("id")}'
    for k,v in e.items():
        assert not (isinstance(v,str) and v.strip().lower()=='null'), f'Cadena null en {e.get("id")}:{k}'

for r in rules:
    missing=rule_keys-set(r)
    assert not missing, f'Regla {r.get("id")} pierde campos fuente: {sorted(missing)}'

db=db_path.read_text(encoding='utf-8')
for field in ['end_at','all_day','time_zone','maps_url','recurrence_rule','participants_text']:
    assert field in db, f'Falta campo local compatible: {field}'
assert 'DB_VERSION = 20' in db
assert 'getNexoFamiliaMetadataForLocalItem' in db
assert 'metadataComplete' in db and 'brokenMappings' in db
assert 'hasNexoFamiliaReminderMapping' in db
assert 'resetNexoFamiliaImportedData' in db

main=main_path.read_text(encoding='utf-8')
assert 'applyBundledNexoFamiliaMigration();' in main
assert 'nexo_familia_seed_v1.json' in main
assert 'auditBundledNexoFamiliaPayload' in main
assert 'repairBundledNexoFamiliaMigration' in main
assert 'cleanRebuildVersion' in main
assert 'verifiedSelected' in main and 'verifiedFamily' in main

importer=importer_path.read_text(encoding='utf-8')
for key in ['endAt','allDay','timeZone','mapsUrl','recurrenceRule','participantsText']:
    assert f'local.put("{key}"' in importer, f'El importador no preserva {key}'
assert 'sourceStartMillis' in importer
for token in ['validatePayload(payload, events, rules)','verifiedEvents','verifiedReminderRules','brokenMappings','metadataComplete']:
    assert token in importer, f'Falta verificación de migración: {token}'

print(f'Migración Nexo Familia completa: {len(events)} eventos, {len(rules)} reglas, {len(members)} miembros')

# Regression beta7: Nexo Familia no puede quedar atrapado en la conciliación genérica.
db_source=db_path.read_text(encoding='utf-8')
assert "source<>'nexo-familia'" in db_source
assert "source='nexo-familia'" in db_source
assert "System.currentTimeMillis()" in db_source
assert "source_status" in db_source
print('Estados Nexo Familia separados de la conciliación genérica: OK')

# Regression beta8: Agenda/Calendario normalizan Nexo Familia al leer, aunque SQLite arrastre done.
db_source=db_path.read_text(encoding='utf-8')
assert 'normalizeNexoFamiliaStatusForRead' in db_source
assert 'queryItems' in db_source and 'normalizeNexoFamiliaStatusForRead(itemJson(c))' in db_source
assert 'SELECT source_status FROM nexo_familia_events WHERE local_item_id=? LIMIT 1' in db_source
assert 'effectiveEnd < System.currentTimeMillis()' in db_source
print('Lectura tolerante a estados heredados de Nexo Familia: OK')
