from pathlib import Path

root=Path(__file__).resolve().parents[1]
cloud=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java').read_text(encoding='utf-8')
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
bell=(root/'validation/test-reminder-bell.py').read_text(encoding='utf-8')

# La agenda Android mantiene paridad con Nexo Familia: incluye eventos y reservas de vehículo.
assert 'event_kind=in.(event,vehicle_reservation)' in cloud
assert 'JSONArray allRules' in cloud
assert 'eventIds.contains' in cloud
assert 'local.put("subcategory",vehicleReservation?"vehicle_reservation":"");' in importer
assert '"event_kind","vehicle_id","reservation_user_id","reservation_reason"' in importer

# Un UUID existente puede volver a procesarse si cambió el remoto o sus avisos.
assert 'sourceChanged(event, previousSource)' in importer
assert 'reminderRulesChanged(remoteRules, previousRules)' in importer
assert 'updatedFromSource' in importer
assert 'unchangedExisting' in importer

# Un evento borrado en Android queda suprimido localmente y no se reimporta.
assert 'isNexoFamiliaEventSuppressed' in db
assert 'NEXO_FAMILIA_SUPPRESSED_KEY' in db
assert 'setNexoFamiliaSuppressedForLocalItem(db, id, deleted);' in db
assert 'setNexoFamiliaSuppressedForLocalItem(db, id, true);' in db
assert 'if (db.isNexoFamiliaEventSuppressed(id))' in importer
assert 'suppressedLocal++' in importer
assert '.put("suppressedLocal", suppressedLocal)' in importer
assert 'suppressedLocalMappings' in db

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

# Las reservas tienen marcador de coche y conservan la campanilla horaria.
assert "function isVehicleReservation(i)" in js
assert "shownMarkers.slice(0,markerLimit).map(calendarMarkerHtml" in js
assert "calendar-event-vehicle" in js
assert "calendar-category-marker calendar-event-vehicle" in js
home=js[js.index('function homeUpcomingCard'):js.index('function bindHomeUpcoming')]
assert "const reminder=eventReminderBellHtml(item,'event-reminder-indicator--time');" in home
assert "reminder+(timed&&hasDate?uiIcon('clock')+esc(timeLabel):'')" in home
assert 'upcoming-title-row' not in home
assert 'Campanilla de avisos' in bell

print('Nexo Familia incremental + supresión local + campanilla horaria: OK')
