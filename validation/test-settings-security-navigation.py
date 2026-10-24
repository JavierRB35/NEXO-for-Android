from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
html = (root / 'app/src/main/assets/index.html').read_text(encoding='utf-8')
js = (root / 'app/src/main/assets/app.js').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')

tools = html.split('data-screen="tools"', 1)[1].split('data-screen="save"', 1)[0]
settings = html.split('data-screen="more"', 1)[1].split('data-screen="about"', 1)[0]
group_start = settings.find('data-settings-accordion="security"')
assert group_start >= 0, 'Falta Datos y seguridad en Ajustes'
security_group = settings[group_start:settings.find('</section>', group_start)]
assert 'data-settings-section="security"' in security_group
assert 'Historial' in security_group and 'Copia de seguridad' in security_group

for panel in ('history', 'backup'):
    attr = 'data-panel="' + panel + '"'
    assert html.count(attr) == 1, 'Acceso duplicado o inexistente: ' + panel
    assert attr in security_group, 'Acceso fuera de Ajustes: ' + panel
    assert attr not in tools, 'No debe aparecer en Herramientas: ' + panel

assert 'Datos y seguridad' not in tools, 'El grupo antiguo debe desaparecer de Herramientas'
assert 'data-settings-accordion="updates"' in settings, 'No ocultar las actualizaciones'
assert "const button=event.target?.closest?.('[data-panel]');" in js
assert 'openPanel(panel);' in js
assert 'function navigateBack()' in js
assert "else navigate(route.screen,options);" in js
assert "versionName '3.7.66'" in gradle and "versionCode 159" in gradle
print('Nexo 3.7.47: Datos y seguridad solo en Ajustes, accesos únicos y navegación conservada: OK')
