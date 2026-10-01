from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')
prefs=(root/'app/src/main/java/com/jruiz/nexo/input/InputHubPrefs.java').read_text(encoding='utf-8')
sms=(root/'app/src/main/java/com/jruiz/nexo/input/SmsWatchScheduler.java').read_text(encoding='utf-8')
gmail=(root/'app/src/main/java/com/jruiz/nexo/input/GmailWatchScheduler.java').read_text(encoding='utf-8')
calendar=(root/'app/src/main/java/com/jruiz/nexo/calendar/CalendarWatchScheduler.java').read_text(encoding='utf-8')

# 1. Las tarjetas de eventos abren directamente el formulario.
assert "function openEventInAgenda(id,occurrenceAt,recurring)" in js
assert "if(recurring)openOccurrence(Number(id),Number(occurrenceAt));" in js
assert "else openItem(Number(id));" in js
assert "navigate('calendar',{root:false})" not in js[js.index("function openEventInAgenda"):js.index("function openEventInAgenda")+500]
assert 'data-item-card-open' in js

# 2 y 7. Dos FAB y persistencia centralizada.
assert 'id="globalVoiceFab"' in html and 'id="calendarAddFab"' in html
assert 'function updateFloatingActions()' in js
assert "const panel=state.screen==='panel'?state.lastPanel:''" in js
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in js
assert "if(panel==='recordings'){voiceVisible=!modalOpen&&!recordingActive;addVisible=false;}" in js
assert "window.addEventListener('pageshow'" in js
assert "document.visibilityState==='visible'" in js
assert "requestAnimationFrame(updateFloatingActions)" in js
assert "NexoNative.startRecording()" in js

# 3. Automático/manual real.
assert 'id="inputHubAutoScan"' in html
assert 'id="inboxScanNowBtn"' in js
assert "NexoNative.scanInputHubNow()" in js
assert 'autoScanEnabled' in prefs and 'setAutoScanEnabled' in prefs
assert 'getBoolean("auto_scan_enabled", false)' in prefs
assert 'InputHubPrefs.autoScanEnabled(context)' in sms
assert 'InputHubPrefs.autoScanEnabled(context)' in gmail
assert 'InputHubPrefs.autoScanEnabled(context)' in calendar
assert 'if (InputHubPrefs.autoScanEnabled(this))' in main
assert 'CalendarWatchScheduler.cancel(app)' in main
assert 'SmsWatchScheduler.cancel(app)' in main
assert 'GmailWatchScheduler.cancel(app)' in main
assert '@JavascriptInterface public void setInputHubAutoScanEnabled(boolean enabled)' in main

# 4. Ajustes con acordeones de Familia.
for section in ['general','display','input','medication','data']:
    assert f'data-settings-accordion="{section}"' in html
assert 'settings-accordion-list' in html and '.settings-accordion__summary' in css
assert 'toggleSettingsAccordion' in js

# 5. Cumpleaños como Nexo Familia.
assert 'calendar-event-birthday' in js and '>🎂<' in js
assert '.calendar-event-birthday' in css
assert "return 'cake'" in js

# 6. Título correcto.
assert '<h2 id="homeFamilyInfoTitle">Accesos directos</h2>' in html
assert '<h2 id="homeFamilyInfoTitle">Información familiar</h2>' not in html

# 8. Acciones compactas editar/compartir/eliminar.
assert 'function recordIconAction' in js
for token in ['data-note-share','data-note-delete','data-document-share','data-document-delete',
              'data-place-share','data-place-delete','data-journal-share','data-journal-delete',
              'data-med-share','data-med-delete','data-list-share','data-list-delete',
              'data-recording-share','data-recording-delete']:
    assert token in js, f'Falta acción de ficha: {token}'
assert '.icon-action' in css and '.icon-action--danger' in css
assert '@JavascriptInterface public void sharePlainText' in main

# 9. Versión visible en cabecera.
assert 'id="headerVersion"' in html
assert "$('#headerVersion')" in js
assert 'NexoNative.getAppVersion()' in js

# 10. Maps desde formularios con ubicación.
assert 'id="captureMapPreview"' in html
assert "openMapsFromFields('#captureLocation','#captureMapsUrl')" in js
assert "openMapsFromFields('#editLocation','#editMapsUrl')" in js
assert "openMapsFromFields('#occLocation','#occMapsUrl')" in js
assert 'data-place-map' in js
assert '@JavascriptInterface public void openLocationDirections(String locationText)' in main

print('Beta12: navegación, FAB, scan manual por defecto, ajustes, fichas, versión y Maps OK')
