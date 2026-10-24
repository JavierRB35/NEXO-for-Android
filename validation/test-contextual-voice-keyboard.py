"""Nexo 3.7.70: el dictado no depende del FAB oculto por el teclado."""
from html.parser import HTMLParser
from pathlib import Path

root = Path(__file__).resolve().parents[1]
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
js = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

class IDs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = {}
    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name == "id":
                self.ids[value] = self.ids.get(value, 0) + 1

parser = IDs()
parser.feed(html)
assert parser.ids.get("voiceCaptureBtn") == 1
assert parser.ids.get("modalVoiceBtn") == 1
assert 'class="capture-mic-button"' in html
assert 'class="contextual-voice-button hidden"' in html
assert 'id="voiceCaptureBtn"' in html.split('class="capture-mode-row"', 1)[1].split('id="captureText"', 1)[0]
assert "$('#voiceCaptureBtn')?.addEventListener('click',runFloatingVoice);" in js
assert "$('#modalVoiceBtn')?.addEventListener('click',runFloatingVoice);" in js
assert "if(state.screen==='save'||contextualEditorActive)voiceVisible=false;" in js
assert "startManagedDictation('capture-edit',{base" in js
assert "function applyCaptureVoiceText(text)" in js
assert "if(focused&&/^(INPUT|TEXTAREA|SELECT)$/.test(focused.tagName))focused.blur();" in js
assert 'body:has(.screen[data-screen="save"].active) .floating-actions{display:none}' in css
assert ".contextual-voice-button.hidden{display:none!important}" in css
assert "position:sticky;top:0;z-index:10" in css
print("Nexo 3.7.70: dictado accesible con teclado en Citas y editores: OK")
