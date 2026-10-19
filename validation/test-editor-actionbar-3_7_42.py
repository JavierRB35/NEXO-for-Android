"""Verifica la jerarquía y las barras inferiores del alta y edición de eventos 3.7.42."""
from pathlib import Path
from html.parser import HTMLParser

root = Path(__file__).resolve().parents[1]
html = (root / 'app/src/main/assets/index.html').read_text(encoding='utf-8')
js = (root / 'app/src/main/assets/app.js').read_text(encoding='utf-8')
css = (root / 'app/src/main/assets/styles.css').read_text(encoding='utf-8')

class IDs(HTMLParser):
    def __init__(self):
        super().__init__(); self.ids = {}
    def handle_starttag(self, tag, attrs):
        for k,v in attrs:
            if k == 'id': self.ids[v] = self.ids.get(v,0) + 1

parser = IDs(); parser.feed(html)
for name in ('headerTodayDate','captureBackBtn','captureText','captureRelated',
             'captureLinkButton','captureLinkCounter','saveCaptureBtn','chooseFileBtn','clearCaptureBtn'):
    assert parser.ids.get(name) == 1, f'{name}: ID inexistente o duplicado'
assert html.index('class="capture-crud-toolbar"') < html.index('id="captureText"') < html.index('id="captureRelated"')
assert 'id="captureBackBtn" type="button" class="secondary-button"><svg' in html
assert '#nx-icon-left' in html and '#nx-icon-trash' in html
assert 'new Intl.DateTimeFormat(\'es-ES\',{weekday:\'long\',day:\'numeric\',month:\'long\',year:\'numeric\'})' in js
assert "if(!document.hidden)refreshHeaderDate();" in js

# Se usa la última decisión de diseño (la cascada de CSS permite reemplazar propuestas anteriores).
final_css = css.split('3.7.42 · Decisión final: barra inferior fija',1)[1]
assert 'position:fixed;left:50%;top:auto;bottom:calc(62px + env(safe-area-inset-bottom))' in final_css
assert 'grid-template-columns:repeat(4,minmax(0,1fr))' in final_css
assert '.capture-crud-toolbar{display:none}' in final_css
assert 'position:sticky;top:auto;bottom:0;z-index:14' in final_css
assert 'bar.parentNode.appendChild(bar)' in js
assert "editor.querySelector('#editLinkButton,#occLinkButton')" in js
for marker in ('saveEditBtn','saveOccurrenceBtn','openRelatedPicker(id)','openRelatedPicker(seriesId)'):
    assert marker in js, marker

print('Nexo 3.7.42: fecha local, captura prioritaria y barras inferiores en alta/edición: OK')
