"""Verifica la jerarquía y las barras inferiores del alta y edición de eventos 3.7.44."""
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
assert html.index('id="captureText"') < html.index('id="captureRelated"') < html.index('class="capture-crud-toolbar"')
assert 'id="captureBackBtn" type="button" class="secondary-button"><svg' in html
assert '#nx-icon-left' in html and '#nx-icon-trash' in html
assert "const weekdays=['dom','lun','mar','mié','jue','vie','sáb']" in js
assert "const months=['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic']" in js
assert "weekdays[now.getDay()]+', '+now.getDate()+' '+months[now.getMonth()]+' '+now.getFullYear()" in js
assert html.index('class="brand-copy"') < html.index('id="headerTodayDate"') < html.index('class="header-actions"')
assert '.brand-info-button .brand-copy{display:flex;flex-direction:column' in css
assert 'flex-wrap:nowrap' in css
assert 'width:100%;margin:0;padding:0;' in css
assert "if(!document.hidden)refreshHeaderDate();" in js

# Regresión 3.7.43: el foco NO oculta una barra si el teclado no está presente.
fixed = css.split('Nexo 3.7.43 · Acciones inferiores fiables',1)[1]
assert 'bottom:calc(var(--nexo-bottom-nav-height, 76px) + 1px)' in fixed
assert 'grid-template-columns:repeat(4,minmax(0,1fr))' in fixed
# Regresión 3.7.44: las acciones no tienen margen heredado ni quedan bajo el FAB.
assert 'gap:3px;height:70px;min-height:70px;max-height:70px' in fixed
assert 'grid-row:auto;grid-column:auto;min-width:0;min-height:70px;margin:0' in fixed
assert 'body:has(.screen[data-screen="save"].active) .floating-actions' in fixed
assert 'var(--nexo-capture-bar-height,90px) + 12px' in fixed
assert "document.documentElement.style.setProperty('--nexo-capture-bar-height'" in js
assert 'body.nexo-keyboard-visible:has(.screen[data-screen="save"].active) .floating-actions{display:none}' in fixed
assert 'button.hidden{display:none!important}' in fixed
assert ':has(input:focus,textarea:focus,select:focus)' not in css
assert 'body.nexo-keyboard-visible .screen[data-screen="save"] .capture-crud-toolbar{display:none}' in fixed
assert "const viewport=window.visualViewport" in js
assert 'fullHeight-visibleHeight>Math.max(160,fullHeight*.20)' in js
assert "document.addEventListener('DOMContentLoaded',installFixedActionBarViewport);" in js

# En edición, las acciones son HERMANAS de #modalBody, nunca hijas del scroll.
assert "bar.parentNode.appendChild(bar)" not in js
assert "$('#modal .modal-sheet')?.appendChild(bar);" in js
assert '.modal-sheet:has(> .record-form-actions--compact)' in fixed
assert 'flex:1 1 auto;min-height:0;overflow-y:auto' in fixed
assert 'body.nexo-keyboard-visible .modal-sheet>.record-form-actions--compact{display:none}' in fixed
assert js.count("$('#modal .modal-sheet > .record-form-actions--compact')?.remove();") >= 2
assert "editor.querySelector('#editLinkButton,#occLinkButton')" in js
for marker in ('saveEditBtn','saveOccurrenceBtn','openRelatedPicker(id)','openRelatedPicker(seriesId)'):
    assert marker in js, marker

print('Nexo 3.7.44: fecha compacta, barra uniforme, micro fuera de Guardar y editor fijo: OK')
