from pathlib import Path

r=Path(__file__).resolve().parents[1]
db=(r/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
main=(r/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
app=(r/'app/src/main/assets/app.js').read_text()
importer=(r/'app/src/main/java/com/jruiz/nexo/migration/NexoFamiliaImporter.java').read_text()

checks={
 'past nonrecurring completed':"WHEN due_at>0 AND (CASE WHEN end_at>0 THEN end_at ELSE due_at END)<CAST(? AS INTEGER) THEN 'done'" in db,
 'manual status column':'status_manual INTEGER NOT NULL DEFAULT 0' in db and 'DB_VERSION = 23' in db,
 'date inference skips manual status':'in.has("dueAt") && !in.has("status") && !old.optBoolean("statusManual", false)' in db,
 'reconcile preserves manual status':'WHEN status_manual=1 THEN status' in db,
 'manual task state preserved':'AND status_manual=0 AND status IN' in db,
 'manual status serialized':'o.put("statusManual",statusManualIndex>=0 && c.getInt(statusManualIndex)==1)' in db,
 'nexo familia read respects manual':'item.optBoolean("statusManual", false)' in db,
 'imported status preserves source state':'local.put("statusManual",true)' in importer,
 'editor offers four states':all(x in app for x in ['>Pendiente</option>','>Completada</option>','>Pospuesta</option>','>Cancelada</option>']),
 'editor marks explicit state manual':'statusManual:true' in app and 'editStatusTouched' in app,
 'postpone persists postponed':"status:'postponed',statusManual:true" in app,
 'recurring manual pending is distinct':"done?'pending':'done'" in app and 'occStatus' in app and 'occStatusTouched' in app,
 'cancelled recurring occurrence is closed':"state NOT IN ('deleted','done','cancelled')" in db,
 'birthday current year':'yearStart=LocalDate.of(today.getYear(),1,1)' in db,
 'birthday today not done':'d.isBefore(today)?"done":"active"' in db,
 'birthday auto status does not overwrite manual':"\"active\".equals(current) && !\"active\".equals(wanted)" in db,
 'reconcile on resume':'db.reconcileTemporalStatuses();' in main,
 'reconcile in agenda':'public JSONArray getAppointments()' in db and 'reconcileTemporalStatuses();' in db,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    raise SystemExit('Fallos estados temporales: '+', '.join(failed))
print(f'Estados temporales: {len(checks)} comprobaciones OK')
