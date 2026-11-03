from pathlib import Path
root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
icons={'familia':'users','cumpleanos':'cake','santo / onomastica':'calendar-heart','mi salud':'heart-pulse','acompanar (salud)':'hand-heart','analitica':'syringe-blood','farmacia':'pill','colegio / estudios':'graduation','trabajo':'briefcase','viaje':'plane','vacaciones':'sun','casa':'home','vehiculos / itv':'car','seguros':'shield','tramites':'file-check','tarea / recado':'list-todo','compras':'cart','ocio / social':'party','deporte':'dumbbell','recordatorio':'bell','otro':'shapes'}
assert len(icons)==21 and len(set(icons.values()))==21
assert "'Mi salud','Acompañar (salud)','Analítica','Farmacia'" in js
assert "'farmacia':'pill'" in js
assert "'Salud / Médico':'Mi salud'" in js  # Compatibilidad sin migrar eventos guardados
assert "'mi salud':'#DC2626'" in js
assert "'acompanar (salud)':'#168FBB'" in js
rule=(root/'app/src/main/java/com/jruiz/nexo/core/RuleClassifier.java').read_text(encoding='utf-8')
assert 'r.category = "Farmacia";' in rule
assert '"farmacia", "parafarmacia"' in rule
assert 'r.category = "Salud / Médico"; r.type = "medication"' in rule
for category,icon in icons.items():
    assert f"'{category}':'{icon}'" in js, category
    assert f'id="nx-icon-{icon}"' in html, icon
assert "'analitica':'syringe-blood'" in js
assert 'id="nx-icon-syringe-blood"' in html
assert 'fill="#B91C1C"' in html and 'stroke="#64748B"' in html
assert 'r.category = "Analítica";' in rule
assert '"analisis de sangre"' in rule
assert 'FAMILY_CATEGORY_THEMES' not in js
assert 'shownMarkers.slice(0,markerLimit).map(calendarMarkerHtml)' in js
assert 'uiIcon(familyCategoryIcon(item.category))' in js
assert 'aria-label="Iconos de las 21 categorías"' in html
assert 'bindCategorySelectIcon' in js
assert '.calendar-category-marker .ui-icon' in css
assert '--event-category-color' in js
assert '--event-category-bg' not in js
assert '--event-category-border' not in js
print('21 categorias con iconos unicos y salud diferenciada: OK')
