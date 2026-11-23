#!/usr/bin/env python3
"""Regresión del archivo único tras retirar Connect; sin modificar datos reales."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/java/com/jruiz/nexo/MainActivity.java").read_text()
auto = (root / "app/src/main/java/com/jruiz/nexo/backup/NexoAutoBackup.java").read_text()
manager = (root / "app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java").read_text()
ui = (root / "app/src/main/assets/app.js").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()

checks = {
    "Copia automática al abrir": "NexoAutoBackup.backupAsync(this,db,false" in app,
    "Selector explícito de ubicación": "ACTION_CREATE_DOCUMENT" in app and "ACTION_OPEN_DOCUMENT" in app and "REQ_BACKUP_EXISTING" in app,
    "Permisos SAF persistentes": "takePersistableUriPermission" in auto,
    "Mismo archivo de destino": 'getString("uri","")' in auto and 'putString("uri",uri.toString())' in auto,
    "Evitar sobrescritura sin cambios": "Result.UNCHANGED" in auto and 'before.equals(prefs(app).getString("fingerprint",""))' in auto,
    "Base y adjuntos en huella": "DatabaseHelper.databaseName()" in auto and 'appendFolder(value,new File(context.getFilesDir(),"attachments")' in auto,
    "No depender de base o perfil Connect": '"nexo-connect-pilot.db"' not in auto and '"nexo-connect-profiles"' not in auto,
    "Staging privado": '"nexo_backup_work_"' in manager and 'context.getFilesDir()' in manager,
    "Instantánea SQLite consistente": "VACUUM INTO" in manager and "PRAGMA quick_check" in manager,
    "Verificación SHA-256 y relectura": "sha256(archive).equals(sha256(readback))" in manager and "inspectFile(readback, true)" in manager,
    "Bloqueo restauración antigua con chat": 'manifest.optBoolean("chatIncluded")' in manager and 'throw new IOException("Esta copia incluye conversaciones' in manager,
    "Sin claves ni conversación en nuevas copias": 'manifest.put("chatIncluded",false)' in manager and 'NexoConnectChatBackup' not in manager,
    "Restauración coordinada": "NexoAutoBackup.setRestoring(true)" in app and "NexoAutoBackup.setRestoring(false)" in app,
    "Restauración invalida huella": "NexoAutoBackup.restored(this)" in app,
    "UI de copia y restauración sin chat": 'id="backupChooseBtn"' in ui and 'id="backupExportBtn"' in ui and 'backupPasswordBtn' not in ui,
    "Formato anterior válido sin chat": 'FORMAT_VERSION = 1' in manager and 'DB_ENTRY = "database/nexo.db"' in manager,
    "Sin servicios Connect": "NexoConnectInboxNoticeJob" not in manifest,
}
for title, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + title)
assert all(checks.values()), "Regresión de copia automática de Nexo"
