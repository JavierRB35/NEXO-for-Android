from pathlib import Path
r=Path(__file__).resolve().parents[1]; db=(r/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(); main=(r/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
checks={
 'past nonrecurring completed':"due_at<? AND recurrence='none'" in db and "status IN ('active','pending','postponed')" in db,
 'date inference only without explicit status':'in.has("dueAt") && !in.has("status")' in db,
 'manual completed states preserved':"WHEN status='done' THEN 'done'" in db,
 'manual postponed states preserved':"WHEN status='postponed' THEN 'postponed'" in db,
 'birthday current year':'yearStart=LocalDate.of(today.getYear(),1,1)' in db,
 'birthday today not done':'d.isBefore(today)?"done":"active"' in db,
 'birthday recurrence exception':'setOccurrenceStatus(id,key,wanted)' in db,
 'birthday nonrecurring pending reset':'thisYear.isBefore(today)?"done":"pending"' in db,
 'reconcile on resume':'db.reconcileTemporalStatuses();' in main,
 'reconcile in agenda':'public JSONArray getAppointments()' in db and 'reconcileTemporalStatuses();' in db,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('Fallos estados temporales: '+', '.join(failed))
print(f'Estados temporales: {len(checks)} comprobaciones OK')
