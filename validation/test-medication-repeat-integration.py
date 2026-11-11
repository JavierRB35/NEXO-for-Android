from pathlib import Path
root=Path(__file__).resolve().parents[1]
scheduler=(root/'app/src/main/java/com/jruiz/nexo/medications/MedicationScheduler.java').read_text()
receiver=(root/'app/src/main/java/com/jruiz/nexo/medications/MedicationAlarmReceiver.java').read_text()
action=(root/'app/src/main/java/com/jruiz/nexo/medications/MedicationActionReceiver.java').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()
policy=(root/'app/src/main/java/com/jruiz/nexo/medications/MedicationRepeatPolicy.java').read_text()
messages=(root/'app/src/main/java/com/jruiz/nexo/medications/MedicationMessageConfig.java').read_text()
js=(root/'app/src/main/assets/app.js').read_text()
checks={
 '20 minute cap':'MAX_AFTER_MINUTES=20' in policy,
 'fixed next slots':'int[] offsets={0,5,10,15,20}' in policy,
 'pre-alert fixed at five minutes':'scheduledAt-5L*60000L' in scheduler,
 'stale repeats cancelled':'!MedicationRepeatPolicy.withinRepeatWindow(at,now)' in scheduler,
 'receiver enforces window':'!MedicationRepeatPolicy.withinRepeatWindow(scheduledAt,now)' in receiver,
 'third snooze button':'POSPONER 10 MIN' in receiver and '"snooze",2' in receiver,
 'snooze action':'MedicationScheduler.scheduleSnooze(context,medId,scheduledAt,10)' in action,
 'pause fixed repeats':'MedicationScheduler.pauseDoseRepeats(context,medId,scheduledAt)' in action,
 'late snooze shown':'if("snooze".equals(kind))' in receiver and 'db.resumeMedicationDoseAfterSnooze' in receiver,
 'snoozed state blocks fixed repeats':'if("snoozed".equals(status)&&!"snooze".equals(kind))return;' in receiver,
 'snooze persists across reboot':'"actionAt"' in db and 'scheduleSnoozeAt(context,id,at,target)' in scheduler,
 'snooze before scheduled dose included':"d.scheduled_at<=? OR d.status='snoozed'" in db,
 'six configurable messages':'public static final int[] OFFSETS={-5,0,5,10,15,20}' in messages,
 'medication placeholder':'{medicacion}' in messages,
 'time placeholder':'{hora}' in messages,
 'humor +10':'no se va a tomar solo' in messages,
 'last alert explicit':'Último aviso' in messages,
 'settings editor':'medicationMessageFields' in js,
 'reset defaults':'resetMedicationMessages' in js,
 'medication payload fixed lead':'leadMinutes:5' in js,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('Fallos de integración medicación: '+', '.join(failed))
print(f'Política y mensajes de medicación OK: {len(checks)} comprobaciones')
