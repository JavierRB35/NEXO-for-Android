from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf8')
java=(root/'app/src/main/java/com/jruiz/nexo/core/RuleClassifier.java').read_text(encoding='utf8')
category_list=js.split('const CATEGORIES = [',1)[1].split('];',1)[0]
categories=re.findall(r"'([^']+)'",category_list)
assert len(categories)==20 and len(set(categories))==20
ix=categories.index('Analítica')
assert (categories[ix-1],categories[ix+1])==('Salud / Médico','Farmacia')
assert "'analitica':'syringe-blood'" in js
assert "'analitica':'#7F1D1D'" in js
assert 'nx-icon-syringe-blood' in html
assert 'fill="#B91C1C"' in html and 'stroke="#64748B"' in html
assert 'r.category = "Analítica"' in java
assert '"analisis de sangre"' in java
assert 'CATEGORIES.forEach(c=>' in js
assert 'CATEGORIES.map(c=>' in js
assert 'familyCategoryIcon(item.category)' in js
assert 'familyCategoryColor(item.category)' in js
assert 'categoryIconLabel(select.value)' in js
assert 'DB_VERSION = 29' in (root/'app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java').read_text(encoding='utf8')
assert 'versionCode 122' in (root/'app/build.gradle').read_text(encoding='utf8')
symbol='<symbol id="nx-icon-syringe-blood"'+html.split('<symbol id="nx-icon-syringe-blood"',1)[1].split('</symbol>',1)[0]+'</symbol>'
root_svg=ET.fromstring('<svg xmlns="http://www.w3.org/2000/svg">'+symbol+'</svg>')
children=list(root_svg)[0]
assert children.attrib['viewBox']=='0 0 24 24'
parts=list(children)
assert len(parts)>=4, 'La jeringuilla debe mostrar aguja, émbolo, cilindro y sangre'
assert sum(el.tag.endswith('rect') for el in parts)>=2, 'Debe tener un cilindro visible con sangre'
assert any('rotate(-45' in el.attrib.get('transform','') for el in parts), 'El cuerpo debe estar claramente en diagonal'
assert any(el.attrib.get('fill')=='#B91C1C' for el in parts), 'Falta la sangre roja'
assert any(el.attrib.get('stroke')=='#64748B' for el in parts), 'Falta contorno del cuerpo de la jeringuilla'
print('Analítica: selección, posición, color, icono, clasificación y conservación de BD: OK')
