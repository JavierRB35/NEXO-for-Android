from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')

sms=read('app/src/main/java/com/jruiz/nexo/input/SmsWatchEngine.java')
gmail=read('app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java')
filter_java=read('app/src/main/java/com/jruiz/nexo/communications/NotificationCandidateFilter.java')
sms_job=read('app/src/main/java/com/jruiz/nexo/input/SmsWatchJob.java')
cal_job=read('app/src/main/java/com/jruiz/nexo/calendar/CalendarWatchJob.java')
main=read('app/src/main/java/com/jruiz/nexo/MainActivity.java')
db=read('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java')

assert 'sms-filter-v6:' in sms
assert 'RECOVERY_MS = 7L * 86400000L' in sms
assert 'sms-error:' in sms
assert 'findRecentEquivalentInboxCapture' in sms

assert 'gmail-filter-v6:' in gmail
assert 'newer_than:30d -in:sent -in:drafts' in gmail
assert 'in:inbox newer_than:30d' not in gmail
assert '/attachments/' in gmail
assert 'gmail-error:' in gmail
assert 'findRecentEquivalentInboxCapture' in gmail

assert 'classification.optLong("dueAt", 0L)' in filter_java
assert 'candidateReason' in filter_java
assert 'OTP_PATTERN' in filter_java
assert 'DATE_PATTERN' in filter_java and 'septiembre' in filter_java and '\\\\d{1,2}' in filter_java

for job in (sms_job,cal_job):
    assert 'scheduleContentWatcher(this);' in job
    assert 'jobFinished(params, false);' in job

assert 'findRecentEquivalentInboxCapture' in db
assert 'smsRejected' in db and 'gmailRejected' in db
assert 'smsErrors' in db and 'gmailErrors' in db

# La fuente directa no debe apagar el salvavidas por notificación.
assert 'CommunicationCapturePrefs.setAppEnabled(this, "sms", false);' not in main
assert 'CommunicationCapturePrefs.setAppEnabled(this, "gmail", false);' not in main
assert 'CommunicationCapturePrefs.setAppEnabled(this, "sms", true);' in main
assert 'CommunicationCapturePrefs.setAppEnabled(this, "gmail", true);' in main

print('Input Hub beta4: alta sensibilidad, reintentos, fallback y deduplicación OK')
