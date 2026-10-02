from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
prefs=(root/'app/src/main/java/com/jruiz/nexo/input/InputHubPrefs.java').read_text(encoding='utf-8')
gmail=(root/'app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java').read_text(encoding='utf-8')

# Ajustes configura Gmail; el escaneo manual pertenece al Inbox.
for control in ['gmailConnectionBadge','gmailAccount','gmailConnectBtn','gmailDisconnectBtn','gmailDiagnostic','gmailDiagnosticText']:
    assert f'id="{control}"' in html, control
assert 'id="gmailScanNowBtn"' not in html
assert 'id="gmailDirectEnabled"' not in html
assert 'Conectar Gmail' in html and 'Desconectar' in html
assert 'Inbox → Escanear ahora' in html

# Puente JS -> Android para conexión; escaneo manual centralizado en Input Hub/Inbox.
for token in ['NexoNative.connectGmail()','NexoNative.disconnectGmail()']:
    assert token in js, token
assert 'NexoNative.scanGmailNow()' not in js
assert 'id="inboxScanNowBtn"' in js
assert "$('#inboxScanNowBtn')?.addEventListener('click',()=>{NexoNative.scanInputHubNow();" in js
for token in ['@JavascriptInterface public void connectGmail()','@JavascriptInterface public void scanGmailNow()','@JavascriptInterface public void disconnectGmail()']:
    assert token in main, token

# Manual por defecto y motor Gmail disponible para el escaneo centralizado.
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

assert 'v3.7.6' in html
assert 'newer_than:30d -in:drafts {-in:sent in:inbox}' in gmail
print('Beta14: Gmail configurado, escaneo centralizado y reenvíos a uno mismo incluidos: OK')
