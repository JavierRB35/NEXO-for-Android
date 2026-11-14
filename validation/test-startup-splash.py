from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
java=(root/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(encoding='utf-8')

# El splash existe desde el HTML inicial y tapa la app hasta terminar/timeout.
assert '<body class="startup-loading">' in html
assert 'id="startupSplash"' in html
assert html.index('id="startupSplash"') < html.index('id="app"')
assert 'src="nexo-toolbar-logo.png"' in html
assert 'Tu información. Tu día. Tu Nexo.' in html
assert 'Buscando en Inbox…' in html
assert 'Desarrollado por Carlos Javier Ruiz Bermúdez' in html
assert html.count('startup-splash__dot startup-splash__dot--') == 3

# Solo los puntos tienen movimiento. Logo, wordmark y fondo permanecen inmóviles.
assert '@keyframes nexo-splash-dot' in css
assert '.startup-splash__dot{' in css and 'animation:nexo-splash-dot' in css
for selector in ('.startup-splash__logo{','.startup-splash__wordmark{','.startup-splash__brand{'):
    start=css.index(selector)
    end=css.index('}',start)
    assert 'animation:' not in css[start:end], f'{selector} no debe animarse'
assert 'body.startup-loading #app{visibility:hidden}' in css

# La portada desaparece al terminar de construir la UI, sin esperar a red ni escaneo.
scan_pos=js.index('NexoNative.startStartupInboxScan()')
home_pos=js.index('preloadStartupHomeDashboard();',scan_pos)
nav_pos=js.index('navigate(startScreen,{root:true});',home_pos)
ui_pos=js.index('state.startupUiReady=true;',nav_pos)
assert scan_pos < home_pos < nav_pos < ui_pos
assert 'state.startupHomeDashboard=parseNative(NexoNative.getHomeDashboard(),{})' in js
assert 'function renderToday(dashboardOverride=null)' in js
assert 'onStartupInboxScanFinished()' in js
assert 'state.startupUiReady' in js
assert 'if(state.startupUiReady)finishStartupSplash();' in js
assert 'startupSplashMinTimer' not in js and 'startupSplashMinReady' not in js
assert '},3500);' not in js, 'La portada ya no tiene espera mínima artificial'
assert 'state.startupSplashTimeout=setTimeout(finishStartupSplash,8500)' in js, 'Respaldo de emergencia'
assert 'state.startupWeatherReady' in js and 'getStartupWeatherSnapshot()' in js
assert 'Consultar el tiempo…' not in js or 'Consultando el tiempo…' in js
assert 'startupWeatherReady:true' not in js, 'No fingir que la consulta terminó'
assert 'onHeaderWeather(raw)' in js
assert "if(source!=='startup')renderToday();" in js
assert 'NexoNative.onStartupReadyForUpdates?.()' in js, 'La búsqueda de actualizaciones continúa'
assert 'NexoAutoBackup.backupAsync(this,db,false' in java, 'La copia automática continúa'

# El escaneo nativo es best-effort: cada fuente puede fallar y el callback final siempre sale.
assert 'private boolean startStartupInboxScan()' in java
assert 'private volatile boolean startupInboxScanRunning = false;' in java
assert 'private boolean firstResumeAfterCreate = true;' in java
assert java.count('catch (Exception ignored) { partial = true; }') >= 3
for token in ('SmsWatchEngine.scan(app)','GmailWatchEngine.scan(app)','CalendarWatchEngine.scan(app)'):
    assert token in java
assert 'jsCallback("onStartupInboxScanFinished", partial ? "partial" : "ok");' in java
assert '@JavascriptInterface public boolean startStartupInboxScan()' in java

print('OK: apertura inmediata al estar lista la UI, tareas de inicio y respaldo conservados')
