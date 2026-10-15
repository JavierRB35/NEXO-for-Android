from pathlib import Path

root=Path(__file__).resolve().parents[1]
cloud=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java').read_text(encoding='utf-8')
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

# La fuente remota incluye las reservas reales de Nexo Familia.
assert 'event_kind=in.(event,vehicle_reservation)' in cloud
assert '"event_kind","vehicle_id","reservation_user_id","reservation_reason"' in importer

# El subtipo se conserva en el item local sin cambiar el esquema SQLite.
assert 'boolean vehicleReservation="vehicle_reservation".equals(cleanString(event,"event_kind"));' in importer
assert 'local.put("subcategory",vehicleReservation?"vehicle_reservation":"");' in importer
assert '"title","body","category","subcategory","dueAt"' in importer
assert 'patch.put("subcategory","vehicle_reservation");' in importer

# Las instalaciones que ya tenían la reserva importada se reparan al arrancar,
# usando la metadata local y sin obligar al usuario a sincronizar manualmente.
assert 'public int repairNexoFamiliaVehicleReservationMarkers()' in db
assert '"vehicle_reservation".equals(cleanJsonString(source, "event_kind"))' in db
assert '!cleanJsonString(source, "vehicle_id").isBlank()' in db
assert '!cleanJsonString(source, "reservation_user_id").isBlank()' in db
assert 'db.repairNexoFamiliaVehicleReservationMarkers();' in main

# Misma semántica visual que Nexo Familia: coche visible y prioritario entre 3 marcadores.
assert "function isVehicleReservation(i)" in js
assert "function calendarMarkerItems(items)" in js
assert "const reservation=list.find(isVehicleReservation);" in js
assert "return [reservation,...list.filter(item=>item!==reservation)].slice(0,3);" in js
assert "function calendarMarkerHtml(item)" in js
assert "calendar-event-vehicle" in js
assert "calendar-category-marker calendar-event-vehicle" in js
assert "calendarMarkerItems(list).map(calendarMarkerHtml).join('')" in js

# En el detalle diario también queda identificado como Reserva coche.
assert "isVehicleReservation(i)?uiIcon('car')+'Reserva coche'" in js
assert '.calendar-event-vehicle{' in css or '.calendar-event-vehicle{' in css.replace('\n','')

print('Reservas de coche: sincronización, reparación automática y marcador de calendario OK')
