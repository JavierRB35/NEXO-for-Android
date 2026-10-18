from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
service=(root/'app/src/main/java/com/jruiz/nexo/recordings/RecordingService.java').read_text()
store=(root/'app/src/main/java/com/jruiz/nexo/recordings/RecordingStore.java').read_text()
provider=(root/'app/src/main/java/com/jruiz/nexo/recordings/RecordingProvider.java').read_text()
js=(root/'app/src/main/assets/app.js').read_text()
html=(root/'app/src/main/assets/index.html').read_text()
gradle=(root/'app/build.gradle').read_text()
db=(root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text()

ET.parse(root/'app/src/main/AndroidManifest.xml')
for perm in ['android.permission.RECORD_AUDIO','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MICROPHONE']:
    assert perm in manifest, perm
assert '.recordings.RecordingService' in manifest
assert 'android:foregroundServiceType="microphone"' in manifest
assert 'android:stopWithTask="false"' in manifest
assert '.recordings.RecordingProvider' in manifest
assert 'android:exported="false"' in manifest
assert 'android:grantUriPermissions="true"' in manifest

for token in [
    'MediaRecorder.AudioSource.MIC','MediaRecorder.OutputFormat.MPEG_4','MediaRecorder.AudioEncoder.AAC',
    'setAudioChannels(1)','setAudioEncodingBitRate(64000)','startForeground(',
    'NotificationManager.IMPORTANCE_LOW','ch.setSound(null, null)','ch.enableVibration(false)',
    'ACTION_PAUSE','ACTION_RESUME','ACTION_STOP','START_NOT_STICKY'
]: assert token in service, token
for forbidden in ['Ringtone','ToneGenerator','MediaPlayer.create','setStreamVolume']:
    assert forbidden not in service, forbidden

for token in ['getFilesDir()', '"recordings"', 'MediaMetadataRetriever', 'RecordingNaming.defaultTitle']:
    assert token in store, token
assert 'https://' not in service+store+provider
assert 'http://' not in service+store+provider
assert 'RecordingProvider.uriFor' in main
assert 'Intent.FLAG_GRANT_READ_URI_PERMISSION' in main
assert 'share.setType("audio/mp4")' in main
assert 'MediaStore.Downloads.EXTERNAL_CONTENT_URI' in main
assert 'Environment.DIRECTORY_DOWNLOADS + "/Nexo"' in main
assert 'Intent.ACTION_CREATE_DOCUMENT' in main
assert 'data-recording-export' in js
assert 'RecordingService.isRecordingActive()' in main

for bridge in ['getRecordingStatus','getRecordings','startRecording','pauseRecording','resumeRecording','stopRecording','renameRecording','deleteRecording','playRecording','stopRecordingPlayback','shareRecording','exportRecording']:
    assert re.search(r'@JavascriptInterface\s+public\s+[\w<>\[\]]+\s+'+bridge+r'\s*\(',main), bridge
    assert 'NexoNative.'+bridge+'(' in js, bridge+' JS'

assert 'data-panel="recordings"' in html
assert "recordings:['Grabaciones'" in js
assert 'function renderRecordingsPanel()' in js
assert 'La grabación continúa con la pantalla apagada' in js
assert 'Nexo no emite sonidos al iniciar o terminar' in js

# Feature release must not alter DB identity/schema merely to store recordings.
assert 'DB_NAME = "nexo.db"' in db
assert 'DB_VERSION = 30' in db
assert "versionName '3.7.33'" in gradle
assert 'versionCode 126' in gradle
print('Grabadora: integración, permisos, privacidad local y puentes OK')
