from pathlib import Path

root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
html=(root/'app/src/main/assets/index.html').read_text()
script=(root/'build-on-phone.sh').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
backup=(root/'app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java').read_text()
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text()
seed=root/'app/src/main/assets/nexo_familia_seed_v1.json'

assert "applicationId 'com.jruiz.nexo'" in gradle
assert "versionCode 153" in gradle
assert "versionName '3.7.60'" in gradle
assert 'v3.7.60' in html
assert 'APP_NAME="Nexo_3_7_60"' in script
assert 'assembleRelease' in script and 'apksigner' in script
assert 'DB_NAME = "nexo.db"' in db and 'DB_VERSION = 33' in db
assert 'manifest.put("appVersion", BuildConfig.VERSION_NAME);' in backup
assert not seed.exists(), 'La APK no debe volver a incluir una snapshot familiar privada'
assert 'validatePayload(payload, events, rules)' in importer
print('Nexo 3.7.60, versionCode 153, applicationId, BD 33 y snapshot privada retirada: OK')
