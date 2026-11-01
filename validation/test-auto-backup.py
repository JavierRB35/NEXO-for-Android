#!/usr/bin/env python3
"""Non-destructive regression checks for Nexo's Scriptura-style single-file backup."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text()
auto = (root / "app/src/main/java/com/jruiz/nexo/backup/NexoAutoBackup.java").read_text()
manager = (root / "app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java").read_text()
secret = (root / "app/src/main/java/com/jruiz/nexo/backup/NexoBackupPasswordStore.java").read_text()
ui = (root / "app/src/main/assets/app.js").read_text()

checks = {
    "Launch backup is automatic": "NexoAutoBackup.backupAsync(this,db,false" in app,
    "Explicit location selector": "ACTION_CREATE_DOCUMENT" in app and "ACTION_OPEN_DOCUMENT" in app and "REQ_BACKUP_EXISTING" in app,
    "Persistable document permissions": "takePersistableUriPermission" in auto,
    "Same document reused": 'getString("uri","")' in auto and 'putString("uri",uri.toString())' in auto,
    "No rewrite when data unchanged": "Result.UNCHANGED" in auto and 'before.equals(prefs(app).getString("fingerprint",""))' in auto,
    "Local database and attachments tracked": "DatabaseHelper.databaseName()" in auto and 'appendFolder(value,new File(context.getFilesDir(),"attachments")' in auto,
    "Chat changes tracked": '"nexo-connect-pilot.db"' in auto and '"nexo-connect-profiles"' in auto,
    "Private staging outside cache": '"nexo_backup_work_"' in manager and 'context.getFilesDir()' in manager,
    "Consistent live SQLite snapshot": "VACUUM INTO" in manager and "PRAGMA quick_check" in manager,
    "Readback with SHA-256 and validation": "sha256(archive).equals(sha256(readback))" in manager and "inspectFile(readback, true)" in manager,
    "Backup passphrase encrypted locally": '"AndroidKeyStore"' in secret and '"AES/GCM/NoPadding"' in secret,
    "Restore guarded against concurrent backups": "NexoAutoBackup.setRestoring(true)" in app and "NexoAutoBackup.setRestoring(false)" in app,
    "Restore invalidates prior fingerprint": "NexoAutoBackup.restored(this)" in app,
    "UI offers destination, force and password": 'id="backupChooseBtn"' in ui and 'id="backupExportBtn"' in ui and 'id="backupPasswordBtn"' in ui,
    "Existing backup format retained": 'FORMAT_VERSION = 1' in manager and 'DB_ENTRY = "database/nexo.db"' in manager,
}
for title, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + title)
assert all(checks.values()), "Nexo automatic-backup regression check failed"
