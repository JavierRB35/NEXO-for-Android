from pathlib import Path
root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
temporal=(root/'app/src/main/java/com/jruiz/nexo/core/TemporalParser.java').read_text(encoding='utf-8')
assert "state.screen==='save'" in js[js.index('function updateFloatingActions'):js.index('function navigate(screen')]
voice=js[js.index('function runFloatingVoice'):js.index('function navigate(screen')]
assert "startManagedDictation('capture-edit'" in voice
assert "if(state.screen==='save')" in voice
assert "target==='capture-edit'" in js
assert "state.voiceMode==='capture-edit'" in js
assert 'function applyCaptureVoiceText(text)' in js
assert 'clearCapture();' not in js[js.index('function applyCaptureVoiceText'):js.index('function finalizeManagedDictation')]
assert 'adjustDayPartFromContext' in temporal
assert 'esta\\s+(?:manana|tarde|noche|madrugada)' in temporal
print('Beta16: franja horaria contextual y voz en captura OK')
