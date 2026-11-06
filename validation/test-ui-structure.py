from html.parser import HTMLParser
from pathlib import Path
import re

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

for screen in ["today","tools","save","search","calendar","more","about","panel"]:
    assert f'data-screen="{screen}"' in html, f"Falta pantalla: {screen}"

home_start = html.index('data-screen="today"')
home_start = html.rfind('<section', 0, home_start + 1)
home_end = html.index('<section class="screen" data-screen="tools"', home_start)
home = html[home_start:home_end]
for required in [
    'id="homeSearchInput"',
    'id="homeTodayCard"',
    'id="homeTomorrowCard"',
    'id="homeWeekCard"',
    'id="homeCalendarCard"',
    '<small>Agenda</small>',
    'id="homeUpcomingList"',
]:
    assert required in home, f"Falta en Inicio: {required}"
assert 'Accesos directos' not in home
assert 'home-section--quick-access' not in home
assert 'module-grid--family-info' not in home
assert home.index('id="homeNowTitle"') < home.index('id="homeUpcomingTitle"')
assert 'id="homeInboxPulseCard"' not in home
assert '<strong id="homeCalendarMonth">Completa</strong>' not in home

# Calendario vive en la barra inferior; Inicio no debe duplicarlo como tarjeta.
assert 'data-nav="calendar"' not in home
assert 'data-panel="appointments"' not in home

tools_start = html.index('<section class="screen" data-screen="tools"')
tools_end = html.index('<section class="screen" data-screen="save"', tools_start)
tools = html[tools_start:tools_end]
for panel in ["notes","recordings","lists","documents","journal","quotes","medications","places","parking"]:
    assert f'data-panel="{panel}"' in tools, f"Falta herramienta: {panel}"

# Historial y Copia de seguridad se gestionan exclusivamente desde la rueda de Ajustes.
settings_start = html.index('<section class="screen" data-screen="more"')
settings_end = html.index('<section class="screen about-screen"', settings_start)
settings = html[settings_start:settings_end]
assert 'data-settings-accordion="security"' in settings
for panel in ["history","backup"]:
    entry = f'data-panel="{panel}"'
    assert entry in settings, f"Falta acceso de seguridad en Ajustes: {panel}"
    assert entry not in tools, f"Acceso de seguridad duplicado en Herramientas: {panel}"
    assert html.count(entry) == 1, f"Acceso de seguridad duplicado: {panel}"

nav_start = html.index('<nav class="bottom-nav"')
nav_end = html.index('</nav>', nav_start)
nav = html[nav_start:nav_end]
for dest in ["today","calendar","notes","search","inbox","tools"]:
    assert f'data-nav="{dest}"' in nav, f"Falta navegación principal: {dest}"

for dest, label in [("today","Inicio"),("calendar","Calendario"),("notes","Notas"),
                    ("search","Buscar"),("inbox","Inbox"),("tools","Herramientas")]:
    assert f'data-nav="{dest}"' in nav and f'>{label}</small>' in nav
nav_positions = [nav.index(f'data-nav="{dest}"') for dest in ["today","calendar","notes","search","inbox","tools"]]
assert nav_positions == sorted(nav_positions), "El orden inferior debe ser Inicio, Calendario, Notas, Buscar, Inbox, Herramientas"
assert 'id="globalSearchBtn"' in html and 'id="homeSearchInput"' in html, "La búsqueda se conserva fuera de la barra"
assert "if(b.dataset.nav==='notes')openPanel('notes',{root:true})" in app_js
assert "else if(b.dataset.nav==='inbox')openPanel('inbox',{root:true})" in app_js
assert "route.panel==='notes'?'notes':route.panel==='inbox'?'inbox'" in app_js

assert 'id="brandInfoBtn"' in html and 'data-screen="about"' in html, "Falta pantalla Acerca de Nexo"
assert 'id="aboutCopyDiagnostics"' in html, "Falta copiar diagnóstico"
assert "navigate('about')" in app_js and "function renderAbout()" in app_js
assert 'id="globalVoiceFab"' in html, "Falta FAB de voz"
assert 'id="calendarAddFab"' in html, "Falta FAB contextual de añadir"
assert "function updateFloatingActions()" in app_js
assert "else if(state.screen==='save'){voiceVisible=!modalOpen;addVisible=false;}" in app_js
assert "if(state.screen==='tools'||state.screen==='more'){voiceVisible=false;addVisible=false;}" in app_js
assert "if(['backup','inbox','history'].includes(panel)){voiceVisible=false;addVisible=false;}" in app_js
assert "function runEventAddFab()" in app_js and "$('#calendarAddFab').addEventListener('click',runEventAddFab);" in app_js
assert "$$('.screen').forEach" in app_js, "La navegación debe iterar todas las pantallas"
assert "$$('.nav-item').forEach" in app_js, "La navegación debe iterar todos los botones inferiores"
assert not re.search(r"(?<!\$)\$\('\.screen'\)\.forEach", app_js)
assert not re.search(r"(?<!\$)\$\('\.nav-item'\)\.forEach", app_js)

print("Estructura UI Nexo beta22: OK")
