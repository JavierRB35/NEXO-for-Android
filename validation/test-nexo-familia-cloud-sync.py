from pathlib import Path

root=Path(__file__).resolve().parents[1]
cloud=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf-8')
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')

# Supabase Auth del usuario de Nexo Familia, no credenciales del proyecto ni Gmail.
assert '/auth/v1/token?grant_type=password' in cloud
assert '/auth/v1/token?grant_type=refresh_token' in cloud
assert 'email' in cloud and 'password' in cloud
assert 'AndroidKeyStore' in cloud
assert 'AES/GCM/NoPadding' in cloud
assert 'putString("password"' not in cloud
assert 'PREF_SESSION_BLOB' in cloud and 'PREF_SESSION_IV' in cloud

# Lectura directa bajo RLS usando el access token del usuario.
assert '/rest/v1/events?select=*' in cloud
assert '/rest/v1/event_reminder_rules?select=*' in cloud
assert '/rest/v1/family_members?select=' in cloud
assert '/rest/v1/profiles?select=' in cloud
assert 'Authorization' in cloud and 'Bearer ' in cloud

# Primera migración limpia; las siguientes solo incorporan UUID nuevos.
assert 'resetAllAppointmentsForNexoFamiliaMigration' in cloud
assert 'importNewOnly' in cloud
assert 'online_baseline_completed' in cloud
assert 'No se ha recibido ningún evento de Nexo Familia. La agenda local no se ha modificado.' in cloud
assert 'countLocalAppointments' in db
assert "type IN ('event','task','reminder','expiry')" in db
assert 'db.getNexoFamiliaLocalItemId(id) > 0L' in importer
assert 'local.put("statusManual",true)' in importer

# La snapshot empaquetada deja de ejecutarse automáticamente al arrancar.
on_create=main.split('@Override protected void onCreate',1)[1].split('@Override protected void onResume',1)[0]
assert 'familiaCloudSync = new NexoFamiliaCloudSync' in on_create
assert 'applyBundledNexoFamiliaMigration();' not in on_create

# Interfaz compacta: login una vez y, después, botón de importación incremental.
panel=js.split('function renderBackupPanel(){',1)[1].split('function renderAppointmentsPanel(){',1)[0]
for token in [
    'Conectar con Nexo Familia',
    'Vincular Nexo Familia',
    'Importar eventos nuevos',
    'Importar todos los eventos',
    'syncNexoFamiliaEvents',
    'La contraseña solo se usa para iniciar sesión y no se guarda',
]:
    assert token in panel, f'Falta UI cloud: {token}'
assert 'Comprobar y reparar' not in panel
assert 'Importar JSON' not in panel

for callback in [
    'onNexoFamiliaLinked',
    'onNexoFamiliaSyncDone',
    'onNexoFamiliaCloudError',
    'onNexoFamiliaSessionExpired',
    'onNexoFamiliaDisconnected',
]:
    assert callback in js

print('Sincronización Nexo Familia -> Nexo Android por Supabase Auth: OK')
