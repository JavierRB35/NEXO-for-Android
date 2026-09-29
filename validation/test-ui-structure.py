from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "app/src/main/assets/index.html"
APP_JS = ROOT / "app/src/main/assets/app.js"

class StrictStructureParser(HTMLParser):
    VOID = {"area","base","br","col","embed","hr","img","input","link","meta","param","source","track","wbr"}
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.ids = set()
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            if attrs["id"] in self.ids:
                raise AssertionError(f"ID duplicado: {attrs['id']}")
            self.ids.add(attrs["id"])
        if tag not in self.VOID:
            self.stack.append(tag)
    def handle_endtag(self, tag):
        if tag in self.VOID:
            return
        if not self.stack:
            raise AssertionError(f"Cierre sin apertura: </{tag}>")
        opened = self.stack.pop()
        if opened != tag:
            raise AssertionError(f"HTML mal anidado: se abrió <{opened}> y se cerró </{tag}>")
    def close_and_check(self):
        super().close()
        if self.stack:
            raise AssertionError(f"Etiquetas sin cerrar: {self.stack[-10:]}")

html = HTML.read_text(encoding="utf-8")
app_js = APP_JS.read_text(encoding="utf-8")
assert "\\n" not in html, "Hay un literal \\n visible en index.html"

parser = StrictStructureParser()
parser.feed(html)
parser.close_and_check()

for screen in ["today","tools","save","search","calendar","more","panel"]:
    assert f'data-screen="{screen}"' in html, f"Falta pantalla: {screen}"

home_start = html.index('data-screen="today"')
home_start = html.rfind('<section', 0, home_start + 1)
home_end = html.index('<section class="screen" data-screen="tools"', home_start)
home = html[home_start:home_end]
for required in [
    'id="homeSearchInput"',
    'id="homeTodayCard"',
    'id="homeWeekCard"',
    'id="homeInboxPulseCard"',
    'id="homeRecordCard"',
    'data-panel="notes"',
    'data-panel="medications"',
    'data-panel="lists"',
    'data-panel="documents"',
    'id="homeUpcomingList"',
    'id="homeAgendaLink"',
]:
    assert required in home, f"Falta en Inicio: {required}"

for forbidden in [
    'id="homeCreateBtn"',
    'id="homeShoppingBtn"',
    'data-panel="history"',
    'data-panel="appointments"',
]:
    assert forbidden not in home, f"Acceso redundante en Inicio: {forbidden}"

tools_start = html.index('<section class="screen" data-screen="tools"')
tools_end = html.index('<section class="screen" data-screen="save"', tools_start)
tools = html[tools_start:tools_end]
for panel in ["lists","notes","journal","quotes","medications","recordings","places","parking","documents","backup"]:
    assert f'data-panel="{panel}"' in tools, f"Falta herramienta: {panel}"

nav_start = html.index('<nav class="bottom-nav"')
nav_end = html.index('</nav>', nav_start)
nav = html[nav_start:nav_end]
for dest in ["today","calendar","search","tools"]:
    assert f'data-nav="{dest}"' in nav, f"Falta navegación principal: {dest}"

assert 'id="globalVoiceFab"' in html, "Falta FAB de voz"
assert 'id="calendarAddFab"' in html, "Falta FAB de añadir cita"
assert "$$('.screen').forEach" in app_js, "La navegación debe iterar todas las pantallas"
assert "$$('.nav-item').forEach" in app_js, "La navegación debe iterar todos los botones inferiores"
import re
assert not re.search(r"(?<!\$)\$\('\.screen'\)\.forEach", app_js), "Selector simple incorrecto para pantallas"
assert not re.search(r"(?<!\$)\$\('\.nav-item'\)\.forEach", app_js), "Selector simple incorrecto para navegación"

print("Estructura UI Nexo: OK")
