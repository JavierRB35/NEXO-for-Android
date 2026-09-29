from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
html=(root/'app/src/main/assets/index.html').read_text()
script=(root/'build-on-phone.sh').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
backup=(root/'app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java').read_text()
seed=json.loads((root/'app/src/main/assets/nexo_familia_seed_v1.json').read_text())
importer=(root/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text()

assert "applicationId 'com.jruiz.nexo'" in gradle
assert "versionCode 74" in gradle
assert "versionName '3.7.0-beta9'" in gradle
assert 'v3.7.0-beta9' in html
assert 'APP_NAME="Nexo_3_7_0_beta9"' in script
assert 'assembleRelease' in script and 'apksigner' in script
assert 'DB_NAME = "nexo.db"' in db and 'DB_VERSION = 20' in db
assert 'manifest.put("appVersion", BuildConfig.VERSION_NAME);' in backup
assert seed.get('snapshotVersion') == '2026-09-29-v5-clean-rebuild'
assert len(seed.get('events', [])) == 33
assert len(seed.get('reminderRules', [])) == 46
assert seed.get('sourceAudit', {}).get('familyEventCount') == 33
assert seed.get('sourceAudit', {}).get('reminderRuleCount') == 46
assert 'validatePayload(payload, events, rules)' in importer
print('Nexo 3.7.0-beta9, applicationId, BD 20 y snapshot completa: OK')
