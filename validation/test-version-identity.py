from pathlib import Path
root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text();html=(root/'app/src/main/assets/index.html').read_text();script=(root/'build-on-phone.sh').read_text();db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text();backup=(root/'app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java').read_text()
assert "applicationId 'com.jruiz.nexo'" in gradle
assert "versionCode 66" in gradle
assert "versionName '3.7.0-beta1'" in gradle
assert 'v3.7.0-beta1' in html
assert 'APP_NAME="Nexo_3_7_0_beta1"' in script
assert 'assembleRelease' in script and 'apksigner' in script
assert 'DB_NAME = "nexo.db"' in db and 'DB_VERSION = 19' in db
assert 'manifest.put("appVersion", BuildConfig.VERSION_NAME);' in backup
print('Nexo Input Hub v0.3 / 3.7.0-beta1, applicationId y BD 18: OK')
