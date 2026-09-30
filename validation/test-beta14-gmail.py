from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
prefs=(root/'app/src/main/java/com/jruiz/nexo/input/InputHubPrefs.java').read_text(encoding='utf-8')
gmail=(root/'app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java').read_text(encoding='utf-8')

# UI sencilla: conectar, escanear solo Gmail y desconectar.
for control in ['gmailConnectionBadge','gmailAccount','gmailConnectBtn','gmailScanNowBtn','gmailDisconnectBtn','gmailDiagnostic','gmailDiagnosticText']:
    assert f'id="{control}"' in html, control
assert 'id="gmailDirectEnabled"' not in html
assert 'Conectar Gmail' in html and 'Escanear Gmail' in html and 'Desconectar' in html

# Puente JS -> Android.
for token in ['NexoNative.connectGmail()','NexoNative.scanGmailNow()','NexoNative.disconnectGmail()']:
    assert token in js, token
for token in ['@JavascriptInterface public void connectGmail()','@JavascriptInterface public void scanGmailNow()','@JavascriptInterface public void disconnectGmail()']:
    assert token in main, token

# Manual por defecto y escaneo Gmail independiente.
assert 'autoScanEnabled:false' in js
assert 'private void scanGmailNow()' in main
assert '"nexo-gmail-manual-scan"' in main

# Diagnóstico comprensible, sin ocultar el código real.
assert 'GoogleSignInStatusCodes.getStatusCodeString(code)' in main
assert 'code == 10' in main
assert 'Google todavía no reconoce esta versión de Nexo.' in main
assert 'gmailErrorDetail' in main and 'gmailErrorDetail' in prefs
assert 'Gmail API:' in gmail

# Conectar Gmail no debe disparar permiso de notificaciones.
start=main.index('private void connectGmail()')
end=main.index('private void disconnectGmail()', start)
assert 'ensureNotificationPermission()' not in main[start:end]

assert 'v3.7.0-beta14' in html
print('Beta14: Gmail conectar/escanear/desconectar y diagnóstico: OK')
