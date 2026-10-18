from pathlib import Path

root=Path(__file__).resolve().parents[1]
seed_path=root/'app/src/main/assets/nexo_familia_seed_v1.json'
db_path=root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java'
main_path=root/'app/src/main/java/com/jruiz/nexo/MainActivity.java'
importer_path=root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java'
cloud_path=root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java'

# La distribución no debe contener datos familiares reales.
assert not seed_path.exists(), 'La snapshot familiar privada sigue empaquetada'

db=db_path.read_text(encoding='utf-8')
assert 'DB_VERSION = 32' in db
assert 'columnExists(db, "journal_entries", "revisit")' in db
assert 'getNexoFamiliaMetadataForLocalItem' in db
assert 'metadataComplete' in db and 'brokenMappings' in db
assert 'hasNexoFamiliaReminderMapping' in db
assert 'resetNexoFamiliaImportedData' in db
assert 'resetAllAppointmentsForNexoFamiliaMigration' in db
assert 'boolean ownsTransaction = !db.inTransaction()' in db

main=main_path.read_text(encoding='utf-8')
assert 'nexo_familia_seed_v1.json' not in main
assert 'readBundledNexoFamiliaPayload' not in main
assert 'repairBundledNexoFamiliaMigration' not in main
assert 'NexoFamiliaCloudSync' in main

importer=importer_path.read_text(encoding='utf-8')
for key in ['endAt','allDay','timeZone','mapsUrl','recurrenceRule','participantsText']:
    assert f'local.put("{key}"' in importer, f'El importador no preserva {key}'
for token in ['validatePayload(payload, events, rules)','verifiedEvents','verifiedReminderRules','brokenMappings','metadataComplete']:
    assert token in importer, f'Falta verificación de migración: {token}'
assert 'deferAlarmSideEffects' in importer
assert 'mergeRemoteChanges' in importer
assert 'REMOTE_SYNC_FIELDS' in importer
assert 'db.updateItem(new JSONObject().put("id",existing).put("status"' not in importer
cloud=cloud_path.read_text(encoding='utf-8')
assert 'event_kind=in.(event,vehicle_reservation)' in cloud
assert 'sql.beginTransaction()' in cloud
assert 'sql.setTransactionSuccessful()' in cloud
assert 'sql.endTransaction()' in cloud
assert 'scheduleCommittedFamiliaReminders()' in cloud
assert cloud.index('sql.endTransaction()') < cloud.index('scheduleCommittedFamiliaReminders()')

# Regression temporal.
assert 'normalizeNexoFamiliaStatusForRead' in db
assert 'queryItems' in db and 'normalizeNexoFamiliaStatusForRead(itemJson(c))' in db
assert "WHEN recurrence<>'none' THEN 'pending'" in db
assert "WHEN status='cancelled' THEN 'cancelled'" in db

print('Nexo Familia: importación validada/transaccional y snapshot privada retirada: OK')
