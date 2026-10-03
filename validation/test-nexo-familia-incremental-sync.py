from pathlib import Path

root=Path(__file__).resolve().parents[1]
cloud=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java').read_text(encoding='utf-8')
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
bell=(root/'validation/test-reminder-bell.py').read_text(encoding='utf-8')

# La agenda Android solo sincroniza eventos de agenda, no reservas de vehículo.
assert 'event_kind=eq.event' in cloud
assert 'JSONArray allRules' in cloud
assert 'eventIds.contains' in cloud

# Un UUID existente puede volver a procesarse si cambió el remoto o sus avisos.
assert 'sourceChanged(event, previousSource)' in importer
assert 'reminderRulesChanged(remoteRules, previousRules)' in importer
assert 'updatedFromSource' in importer
assert 'unchangedExisting' in importer

# Mezcla de tres vías: remoto nuevo / remoto anterior / estado local.
assert 'mergeRemoteChanges(remoteLocal,previousLocal,currentLocal)' in importer.replace(' ','')
assert 'REMOTE_SYNC_FIELDS' in importer
fields=importer.split('REMOTE_SYNC_FIELDS',1)[1].split('};',1)[0]
for local_only in ['favorite','priority','voiceEnabled','needsReview']:
    assert f'"{local_only}"' not in fields, f'{local_only} no debe sobrescribirse desde Familia'

# Una actualización remota existente solo escribe una vez el item.
assert 'db.updateItem(new JSONObject().put("id",existing).put("status"' not in importer

# UI describe sincronización, no importación solo de UUID nuevos.
assert 'Sincronizar eventos' in js
assert 'Sincronizando cambios…' in js
assert 'updatedFromSource' in js
assert 'No hay cambios nuevos en Nexo Familia.' in js

# La campanilla sigue fijada a la izquierda del reloj en la línea horaria.
assert "eventReminderBellHtml(item,'event-reminder-indicator--time')+uiIcon('clock')" in js
home=js[js.index('function homeUpcomingCard'):js.index('function bindHomeUpcoming')]
assert 'upcoming-title-row' not in home
assert 'Campanilla de avisos' in bell

print('Nexo Familia incremental + campanilla horaria: OK')
