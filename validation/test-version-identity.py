from pathlib import Path
root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text();html=(root/'app/src/main/assets/index.html').read_text();script=(root/'build-on-phone.sh').read_text();db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text();backup=(root/'app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java').read_text()
assert "applicationId 'com.jruiz.nexo'" in gradle
assert "versionCode 58" in gradle
assert "versionName '3.5.0-beta3'" in gradle
assert 'v3.5.0-beta3' in html
assert 'APP_NAME="Nexo_Input_Hub_v0_3"' in script
assert 'assembleRelease' in script and 'apksigner' in script
assert 'DB_NAME = "nexo.db"' in db and 'DB_VERSION = 18' in db
assert 'manifest.put("appVersion", BuildConfig.VERSION_NAME);' in backup
print('Nexo Input Hub v0.3 / 3.5.0-beta3, applicationId y BD 18: OK')
