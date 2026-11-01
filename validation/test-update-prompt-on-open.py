from pathlib import Path

root = Path(__file__).resolve().parents[1]
manager = (root / 'app/src/main/java/com/jruiz/nexo/updates/NexoUpdateManager.java').read_text()
activity = (root / 'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
js = (root / 'app/src/main/assets/app.js').read_text()
job = (root / 'app/src/main/java/com/jruiz/nexo/updates/NexoUpdateJob.java').read_text()
gradle = (root / 'app/build.gradle').read_text()

# Una apertura nueva no se limita por la última consulta correcta de 24h.
assert 'public void checkOnOpen()' in manager
assert 'check(false, true);' in manager
assert 'if (!manual && !onOpen && last > 0' in manager
assert 'if (manual || onOpen || alerted != remote.versionCode)' in manager
assert 'FOREGROUND_DEDUP_MS' in manager
assert 'updatePromptVisible' in manager
assert 'setOnDismissListener(d -> updatePromptVisible = false)' in manager
assert '.setNegativeButton("Más tarde"' in manager
assert '.setPositiveButton("Actualizar"' in manager

# Primera consulta tras portada; siguientes al regresar a primer plano.
assert 'private boolean updatesStartupReady;' in activity
assert 'if (updatesStartupReady) updateManager.checkOnOpen();' in activity
assert '@JavascriptInterface public void onStartupReadyForUpdates()' in activity
assert 'if (updateManager != null) updateManager.checkOnOpen();' in activity
assert 'NexoNative.onStartupReadyForUpdates?.()' in js
start = js.index('function finishStartupSplash()')
end = js.index('function maybeFinishStartupSplash()', start)
assert 'NexoNative.onStartupReadyForUpdates?.()' in js[start:end]

# Los controles manuales y el job de notificaciones conservan su funcionalidad.
assert '@JavascriptInterface public void checkForUpdates()' in activity
assert 'updateManager.check(true)' in activity
assert 'setPeriodic(24L * 60 * 60 * 1000)' in job
assert 'isInstalledSignatureOfficial(activity)' in manager
assert 'hasOfficialSigner(pkg)' in manager
assert 'release.sha256.equals(hex(digest.digest()))' in manager
assert "versionCode 171" in gradle
assert "versionName '3.7.78'" in gradle
print('Nexo 3.7.70: aviso al abrir, sin duplicados, y seguridad conservada: OK')
