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
    "Gemini primero, Groq después de cualquier fallo": 'if(geminiConfigured)' in client and 'catch(Exception failure)' in client and 'if(groqConfigured)' in client and 'validate(new JSONObject(gemini(prompt)))' in client and 'validate(new JSONObject(groq(prompt)))' in client,
    "Límites de respuesta": 'out.size()+n > 512000' in client and 'input.length()>1800' in client,
    "Modelo solo propone evento": 'out.put("type","event")' in client and '"dueDate"' in client and '"reminderLeadMinutes"' in client,
    "No escribe en base de datos": 'DatabaseHelper' not in client and 'insertItem' not in client and 'updateItem' not in client,
    "Uso mediante puente asincrónico etiquetado": 'analyzeNexoCapture(String text,int requestId)' in bridge and 'new Thread(' in bridge and 'response.put("requestId",requestId)' in bridge,
    "Consentimiento explícito del modo automático": 'setNexoAiAutomatic(boolean enabled)' in bridge and 'setAutomaticEnabled(boolean enabled)' in client and 'window.confirm(' in js.split('function setNexoAiAutomaticFromSettings(){',1)[1].split('function resetNexoAiCapture',1)[0],
    "La IA solo se consulta desde captura": "NexoNative.analyzeNexoCapture(text,state.captureAiPendingId)" in js and "onNexoAiCapture(raw)" in js,
    "Campos completos y categorías": all(token in client for token in ("personNames","mapsUrl","recurrenceUntilDate","reminderLeadMinutes","voiceRequested","timeZone","priority","dueEndDate","dueEndTime")),
    "Sin dirección ni personas inventadas": "No incluyas enlaces Maps generados" in client and "identificadores de personas" in client,
    "Comprobación de red antes de petición": "requireNetwork()" in client and "NET_CAPABILITY_INTERNET" in client,
    "Debounce y peticiones limitadas": "immediate?0:1100" in js and "state.captureAiActive" in js,
    "Evitar grabar una cita a medio interpretar": "if(state.captureAiActive||state.captureAiTimer)" in js,
    "Fallback local tras fallo": "localCaptureAnalysis(true);" in js and "state.captureAiSuppressed=true" in js,
    "No aplicar resultados obsoletos": "Number(result.requestId)!==state.captureAiPendingId" in js,
    "Autorización automática opt-in": 'id="nexoAiAutomatic"' in html and 'auto_enabled",false' in client,

    "Datos seleccionados a mano antes de guardar": 'renderClassification(proposal,true)' in js and 'captureFieldLocked(' in js and 'no se ha guardado' in js and 'if(state.captureAiActive||state.captureAiTimer)' in js,
    "Enlace de personas local": 'NexoNative.getPeople()' in js.split('function receiveNexoAiCapture(raw){',1)[1].split('function analyzeCapture',1)[0],
    "Ajustes de proveedor opt-in": 'id="nexoAiGeminiKey"' in html and 'id="nexoAiGroqEnable"' in html,
    "Gemini: URL oficial junto a su campo": 'id="nexoAiGeminiKeyLink"' in html and 'href="https://aistudio.google.com/apikey"' in html,
    "Groq: URL oficial junto a su campo": 'id="nexoAiGroqKeyLink"' in html and 'href="https://console.groq.com/keys"' in html,
    "Enlaces al navegador exterior": "NexoNative.openExternal(link.href)" in js and "@JavascriptInterface public void openExternal(String url)" in bridge,
    "Sin credenciales en copia": '"nexo_ai_credentials"' not in backup and 'app.getSharedPreferences' not in backup,
    "Se conserva Internet para actualizaciones e IA": 'android.permission.INTERNET' in manifest,
}
for description,ok in tests.items(): print(("PASS" if ok else "FAIL")+": "+description)
assert all(tests.values()), "Contrato Nexo IA incumplido"
