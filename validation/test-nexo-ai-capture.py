#!/usr/bin/env python3
"""Contratos de seguridad y UX de la captura IA bajo demanda."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
client=(root/'app/src/main/java/com/jruiz/nexo/ai/NexoAiCapture.java').read_text()
bridge=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
js=(root/'app/src/main/assets/app.js').read_text()
html=(root/'app/src/main/assets/index.html').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
backup=(root/'app/src/main/java/com/jruiz/nexo/backup/NexoBackupManager.java').read_text()

tests={
    "Claves cifradas mediante Android Keystore y AES-GCM": 'KeyStore.getInstance("AndroidKeyStore")' in client and 'AES/GCM/NoPadding' in client,
    "Claves distintas por proveedor": '"nexo_ai_" + provider + "_aes_v1"' in client,
    "Modelos y endpoints restringidos": 'https://generativelanguage.googleapis.com/' in client and 'https://api.groq.com/' in client,
    "Sin redirecciones de claves": 'con.setInstanceFollowRedirects(false)' in client,
    "Respaldo desactivado inicialmente": 'getBoolean("groq_enabled", false)' in client,
    "Respaldo solo recuperable": 'catch (Recoverable primaryFailure)' in client and 'code==429' in client and 'SocketTimeoutException' in client,
    "Límites de respuesta": 'out.size()+n > 512000' in client and 'input.length() > 1800' in client,
    "Modelo solo propone evento": 'out.put("type", "event")' in client and '"dueDate"' in client and '"reminderLeadMinutes"' in client,
    "No escribe en base de datos": 'DatabaseHelper' not in client and 'insertItem' not in client and 'updateItem' not in client,
    "Uso mediante puente explícito": 'analyzeNexoCapture(String text)' in bridge and 'new Thread(' in bridge,
    "Confirmación antes de consulta": 'window.confirm(' in js.split('function requestNexoAiCapture(){',1)[1].split('function receiveNexoAiCapture',1)[0],
    "La IA no se ejecuta durante inicio": 'analyzeNexoCapture(raw);' in js and 'analyzeNexoCapture(raw)' not in js.split('function beginStartupSequence(',1)[1].split('function init(){',1)[0],
    "Datos seleccionados a mano antes de guardar": 'renderClassification(merged,true)' in js and 'captureFieldLocked(' in js and 'No se ha guardado' in js,
    "Enlace de personas local": 'NexoNative.getPeople()' in js.split('function receiveNexoAiCapture(raw){',1)[1].split('function analyzeCapture',1)[0],
    "Ajustes de proveedor opt-in": 'id="nexoAiGeminiKey"' in html and 'id="nexoAiGroqEnable"' in html,
    "Sin credenciales en copia": '"nexo_ai_credentials"' not in backup and 'app.getSharedPreferences' not in backup,
    "Se conserva Internet para actualizaciones e IA": 'android.permission.INTERNET' in manifest,
}
for description,ok in tests.items(): print(("PASS" if ok else "FAIL")+": "+description)
assert all(tests.values()), "Contrato Nexo IA incumplido"
