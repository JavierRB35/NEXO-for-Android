from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')

db=read('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java')
service=read('app/src/main/java/com/jruiz/nexo/core/NexoService.java')
main=read('app/src/main/java/com/jruiz/nexo/MainActivity.java')
calendar=read('app/src/main/java/com/jruiz/nexo/calendar/CalendarWatchEngine.java')
gmail=read('app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java')
cloud=read('app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaCloudSync.java')
importer=read('app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java')
js=read('app/src/main/assets/app.js')

# P1-01: una v24 defectuosa también recibe revisit al subir a v25.
assert 'DB_VERSION = 32' in db
assert 'oldVersion < 25 && !columnExists(db, "journal_entries", "revisit")' in db
assert 'PRAGMA table_info(' in db

# P1-02: Notas solo guardan a petición; cerrar con cambios exige decisión.
assert 'function saveNoteEditor()' in js
assert 'id="noteSave"' in js
assert 'scheduleNoteAutosave' not in js and 'flushNoteAutosave' not in js
assert 'showNoteUnsavedGuard()' in js
assert 'data-note-close-save' in js and 'data-note-close-discard' in js and 'data-note-close-cancel' in js
assert 'persistNoteRecoveryDraft()' in js
assert 'state.journalEditor?.saveFailed' in js
assert "if(ed.saveFailed){" in js and "El dictado no se ha iniciado" in js
assert "const editorHandoff=prefill!=='';" in js
assert "let title=editorHandoff?String(titlePrefill||'')" in js

# P1-03: la primera sustitución de agenda es transaccional y las alarmas se difieren.
assert 'sql.beginTransaction()' in cloud and 'sql.setTransactionSuccessful()' in cloud and 'sql.endTransaction()' in cloud
assert 'resetAllAppointmentsForNexoFamiliaMigration()' in cloud
assert 'deferAlarmSideEffects' in importer
assert 'scheduleCommittedFamiliaReminders()' in cloud

# P1-07: una cita voluntariamente sin fecha es válida, pero errores temporales siguen en revisión.
classification=service[service.index('public JSONObject classifyAppointment'):service.index('private void applyLearnedCategory')]
assert 'r.dueAt <= 0 && !r.invalidDate && !r.invalidTime && !r.ambiguousTime' in classification
assert 'r.needsReview = false' in classification
assert 'r.reason = ""' not in classification
assert "Revisa fecha/hora:" in js and "a.invalidDate||a.invalidTime||a.ambiguousTime" in js

# P1-08: texto idéntico no basta si ambos remitentes son diferentes.
dedupe=db[db.index('public long findRecentEquivalentInboxCapture'):db.index('private static boolean sameInboxSourceFamily')]
assert 'if (candidate.equals(target)) return' not in dedupe
assert 'senderMatch' in dedupe

# P1-09: crear elemento + marcar captura convertida comparten transacción.
bridge=main[main.index('@JavascriptInterface public String saveCapture(String json)'):main.index('@JavascriptInterface public String updateItem',main.index('@JavascriptInterface public String saveCapture(String json)'))]
assert 'atomicInboxCreate' in bridge
assert 'sql.beginTransaction()' in bridge
assert 'db.markInboxCaptureConverted' in bridge
assert 'sql.setTransactionSuccessful()' in bridge
assert 'service.setReminderSchedulingDeferred(true)' in bridge
assert 'scheduleCommittedCaptureReminders' in bridge

# P1-10: un fallo de persistencia no consolida Calendar/Gmail como procesados.
assert calendar.count('if (captureId > 0L)') >= 2
assert 'candidate && captureId <= 0L' in gmail
assert 'gmail-error:' in gmail

# Privacidad: la snapshot familiar privada no forma parte de la APK.
assert not (root/'app/src/main/assets/nexo_familia_seed_v1.json').exists()
assert 'nexo_familia_seed_v1.json' not in main

print('Nexo 3.7.2: regresiones de integridad de datos validadas')
