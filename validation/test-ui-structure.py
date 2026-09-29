from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "app/src/main/assets/index.html"

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
assert "\\n" not in html, "Hay un literal \\n visible en index.html"
parser = StrictStructureParser()
parser.feed(html)
parser.close_and_check()

home_start = html.index('<section class="screen active" data-screen="today"')
home_end = html.index('</section><section class="screen" data-screen="save">', home_start)
home = html[home_start:home_end]
for required in [
    'id="homeCreateBtn"',
    'id="homeTalkBtn"',
    'data-panel="inbox"',
    'id="inboxCount"',
    'data-panel="calendar"',
    'data-panel="appointments"',
    'data-panel="quotes"',
]:
    assert required in home, f"Falta en Inicio: {required}"

for screen in ["today","save","search","calendar","more","panel"]:
    assert f'data-screen="{screen}"' in html, f"Falta pantalla: {screen}"

print("Estructura UI Nexo: OK")
