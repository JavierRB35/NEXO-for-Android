from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')

manager=read('app/src/main/java/com/jruiz/nexo/transcription/TranscriptionModelManager.java')
sherpa=read('app/src/main/java/com/jruiz/nexo/transcription/SherpaWhisper.java')
service=read('app/src/main/java/com/jruiz/nexo/transcription/TranscriptionService.java')
store=read('app/src/main/java/com/jruiz/nexo/transcription/TranscriptionStore.java')
main=read('app/src/main/java/com/jruiz/nexo/MainActivity.java')
js=read('app/src/main/assets/app.js')
html=read('app/src/main/assets/index.html')

# Tres perfiles multilingües cuantizados; Large queda explícitamente fuera del diseño.
for token in ['QUALITY_FAST="fast"','QUALITY_HIGH="high"','QUALITY_MAX="max"',
              'base-encoder.int8.onnx','small-encoder.int8.onnx','medium-encoder.int8.onnx',
              'base-decoder.int8.onnx','small-decoder.int8.onnx','medium-decoder.int8.onnx']:
    assert token in manager, token
assert 'whisper-large' not in manager.lower()
assert 'setLanguage("es")' in sherpa
assert 'setProvider("cpu")' in sherpa

# La selección persiste y Base conserva la ruta histórica para instalaciones 3.7.2.
assert 'getSharedPreferences(PREFS,Context.MODE_PRIVATE)' in manager
assert 'PREF_QUALITY="quality"' in manager
assert '"sherpa-onnx-whisper-base"' in manager
assert 'public static File modelDir(Context c){return modelDir(c,QUALITY_FAST);}' in manager

# Descarga bajo demanda, integridad y borrado local.
assert 'download(Context c,String quality,Progress progress)' in manager
assert 'sha256(tmp)' in manager
assert 'VERIFIED_MARKER' in manager
assert 'public static boolean delete(Context c,String quality)' in manager
assert 'downloadTranscriptionModelQuality' in main
assert 'deleteTranscriptionModel' in main

# Protección de RAM y almacenamiento antes de descarga y antes de inicializar ONNX.
assert 'ActivityManager.MemoryInfo' in manager
assert 'new StatFs(' in manager
assert 'availableRam' in manager and 'storageAvailable' in manager
assert 'ensureSafeToLoad(Context c,String quality)' in manager
assert 'TranscriptionModelManager.ensureSafeToLoad(c,this.quality)' in sherpa
assert 'TranscriptionModelManager.ensureSafeToLoad(this,quality)' in service
assert 'OutOfMemoryError' in service

# Cada trabajo fija su calidad; cambiar Ajustes después no altera la transcripción en curso.
assert 'EXTRA_QUALITY="quality"' in service
assert '.putExtra(TranscriptionService.EXTRA_QUALITY,quality)' in main
assert 'writeStatus(this,id,"running"' in service
assert '"qualityId"' in store and '"qualityLabel"' in store

# Inferencia local fuera del hilo de UI y proceso Android separado (manifest cubierto por integración existente).
assert 'new Thread(()->runTranscription' in service
assert 'AudioChunkDecoder.decode(audio,16000,28' in service

# Ajustes y UX: selector, recomendación, descarga/eliminación, progreso y cancelación.
assert 'data-settings-accordion="transcription"' in html
assert 'id="transcriptionQualitySettings"' in html
assert 'function renderTranscriptionQualitySettings()' in js
assert 'Recomendado para este dispositivo' in js
assert 'data-transcription-model-download' in js
assert 'data-transcription-model-delete' in js
assert 'data-transcription-model-progress' in js
assert "data-transcription-cancel" in js
assert "qualityLabel" in js
assert "renderTranscriptionQualitySettings();" in js
assert "function transcriptionModelStatusSafe()" in js
assert "TRANSCRIPTION_PROFILE_FALLBACKS" in js
assert "fallbackStatus(Context c,Throwable failure)" in manager
assert 'out.put("models",models);' in manager
assert "catch(Throwable failure)" in manager

print('Transcripción 3.7.4: perfiles, recursos, fallback y UX validados')
