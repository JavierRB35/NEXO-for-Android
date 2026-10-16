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

# Inbox y dashboard de Inicio arrancan durante el splash; no se vuelve al calendario pesado.
scan_pos=js.index('NexoNative.startStartupInboxScan()')
home_pos=js.index('preloadStartupHomeDashboard();',scan_pos)
nav_pos=js.index('navigate(startScreen,{root:true});',home_pos)
assert scan_pos < home_pos < nav_pos
assert 'state.startupHomeDashboard=parseNative(NexoNative.getHomeDashboard(),{})' in js
assert 'function renderToday(dashboardOverride=null)' in js
assert 'onStartupInboxScanFinished()' in js
assert 'startupSplashMinReady' in js
assert 'state.startupSplashMinTimer=setTimeout' in js
assert '},3500);' in js, 'El splash debe permanecer visible al menos 3,5 s'
assert 'state.startupSplashTimeout=setTimeout' in js
assert '},8500);' in js, 'El splash tiene un límite seguro de 8,5 s para meteorología'
assert 'state.startupSplashMinReady&&state.startupHomeReady&&state.startupInboxReady&&state.startupWeatherReady' in js
assert 'state.startupWeatherReady' in js and 'getStartupWeatherSnapshot()' in js
assert 'Consultando el tiempo…' in js
assert 'startupWeatherReady:true' not in js, 'No fingir que la consulta terminó'
assert 'onHeaderWeather(raw)' in js
assert "source==='startup'&&state.startupSplashActive" in js

# El escaneo nativo es best-effort: cada fuente puede fallar y el callback final siempre sale.
assert 'private boolean startStartupInboxScan()' in java
assert 'private volatile boolean startupInboxScanRunning = false;' in java
assert 'private boolean firstResumeAfterCreate = true;' in java
assert java.count('catch (Exception ignored) { partial = true; }') >= 3
for token in ('SmsWatchEngine.scan(app)','GmailWatchEngine.scan(app)','CalendarWatchEngine.scan(app)'):
    assert token in java
assert 'jsCallback("onStartupInboxScanFinished", partial ? "partial" : "ok");' in java
assert '@JavascriptInterface public boolean startStartupInboxScan()' in java

print('OK: splash Inbox, Inicio y meteorología: mínimo 3,5 s, máximo 8,5 s y cache local')
