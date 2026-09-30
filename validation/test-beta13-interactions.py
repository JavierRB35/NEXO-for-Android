from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
js=(root/'app/src/main/assets/app.js').read_text(encoding='utf-8')
css=(root/'app/src/main/assets/styles.css').read_text(encoding='utf-8')
html=(root/'app/src/main/assets/index.html').read_text(encoding='utf-8')

# querySelector devuelve un solo Element: nunca debe usarse con forEach.
assert not re.search(r"(?<!\\$)\\$\\([^\\n;]+?\\)\\.forEach", js), 'No debe usarse un selector simple con forEach'

for fixed in [
    "$$('[data-settings-section]').forEach",
    "$$('[data-settings-accordion]').forEach",
    "$$('[data-home-event]').forEach",
]:
    assert fixed in js, fixed

# Controles que beta12 dejaba sin enlazar al abortar init().
for token in [
    "$('#globalVoiceFab').addEventListener('click',runFloatingVoice)",
    "$('#calendarAddFab').addEventListener('click',runEventAddFab)",
    "$$('[data-panel]').forEach",
    "$('#modalClose').addEventListener('click',closeModal)",
    "function bindHomeUpcoming()",
    "openEventInAgenda(id,occ,rec)",
]:
    assert token in js, token

# Acciones visibles arriba en fichas de citas.
for control in ['editItemTopBtn','shareItemBtn','deleteItemBtn','editOccurrenceTopBtn','shareOccurrenceBtn','deleteOccurrenceBtn']:
    assert control in js, control
assert '.event-form-actions' in css

# FAB en horizontal.
start=css.index('.floating-actions{')
block=css[start:css.index('}',start)+1]
assert 'display:flex' in block
assert 'flex-direction:row' in block

assert 'v3.7.0-beta16' in html
print('Beta16 conserva las correcciones beta13 de interacción: OK')
