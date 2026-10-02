from pathlib import Path

root=Path(__file__).resolve().parents[1]
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')

home=html[html.index('id="homeDashboardContent"'):html.index('data-screen="tools"')]

for removed in ('Resumen familiar','Accesos rápidos','Agenda familiar'):
    assert removed not in home, f'Sigue visible el título duplicado: {removed}'

for kept in ('Lo importante ahora','Accesos directos','Próximamente'):
    assert kept in home, f'Falta el título principal: {kept}'

style=css[css.index('#homeNowTitle,'):css.index('.tool-group .section-heading h2', css.index('#homeNowTitle,'))]
for token in (
    'font-size:.78rem',
    'font-weight:900',
    'letter-spacing:.075em',
    'text-transform:uppercase',
    'color:#118447',
):
    assert token in style, f'Falta estilo compacto de Inicio: {token}'

assert '#homeDashboardContent>.home-section--pulse{margin-top:20px}' in css, 'Falta separación entre buscador y primer bloque de Inicio'

print('OK: Inicio conserva un único título compacto por sección y separación bajo el buscador')
