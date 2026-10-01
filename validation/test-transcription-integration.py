from pathlib import Path
r=Path(__file__).resolve().parents[1]
gradle=(r/'app/build.gradle').read_text(); manifest=(r/'app/src/main/AndroidManifest.xml').read_text(); main=(r/'app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text(); service=(r/'app/src/main/java/com/jruiz/nexo/transcription/TranscriptionService.java').read_text(); sherpa=(r/'app/src/main/java/com/jruiz/nexo/transcription/SherpaWhisper.java').read_text(); model=(r/'app/src/main/java/com/jruiz/nexo/transcription/TranscriptionModelManager.java').read_text(); js=(r/'app/src/main/assets/app.js').read_text(); build=(r/'build-on-phone.sh').read_text(); prep=(r/'prepare-transcription-runtime.sh').read_text()
checks={
 'sherpa aar':'sherpa-onnx-1.13.7.aar' in gradle,
 'kotlin runtime':'kotlin-stdlib:1.9.24' in gradle,
 'separate process':'android:process=":transcription"' in manifest,
 'data sync fgs':'foregroundServiceType="dataSync"' in manifest,
 'internet permission':'android.permission.INTERNET' in manifest,
 'direct kotlin api':'new OfflineRecognizer(null,cfg)' in sherpa,
 'spanish whisper':'whisper.setLanguage("es")' in sherpa,
 'base int8 encoder':'base-encoder.int8.onnx' in model,
 'chunked decode':'AudioChunkDecoder.decode(audio,16000,28' in service,
 'diagnostics':'JAVA_ERROR' in service and 'getTranscriptionDiagnostic' in main,
 'export txt':'exportTranscription' in main,
 'model download ui':'downloadTranscriptionModel' in js,
 'visible transcribe button':'recording-transcribe-button' in js and 'data-transcription-start=' in js,
 'recording options restored':'data-recording-menu=' in js and '⋯ Opciones' in js,
 'direct start does not blindly close modal':"fromModal=!!b.closest('#modalBody')" in js and 'if(fromModal)closeModal()' in js,
 'card gets model once':"rows.map(r=>recordingCard(r,model))" in js,
 'build prepares runtime':'prepare-transcription-runtime.sh' in build,
 'runtime sha pinned':'c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8' in prep,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('Fallos transcripción: '+', '.join(failed))
print(f'Transcripción local: {len(checks)} comprobaciones OK')
