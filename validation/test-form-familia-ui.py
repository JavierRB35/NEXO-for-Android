from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

# Eventos: fecha y hora deben usar controles semánticos y parrillas de dos columnas.
for field_id in ("captureDate", "captureEndDate"):
    assert f'id="{field_id}" type="date"' in html
for field_id in ("captureTime", "captureEndTime"):
    assert f'id="{field_id}" type="time"' in html
assert 'class="form-grid capture-basics event-form-grid"' in html
assert 'class="form-grid event-settings-grid"' in html
assert ">Repetición<select id=\"captureRecurrence\"" in html

for field_id in ("editTime", "editEndTime", "occTime", "occEndTime", "medStartTime"):
    assert f'id="{field_id}" type="time"' in app
assert 'class="form-grid event-range-grid"' in app
assert 'class="form-grid event-settings-grid"' in app

# Medicación: no se permite volver al campo de texto libre para una lista de horas.
assert 'id="medTimes" type="text"' not in app
assert 'id="medTimesPicker"' in app
assert 'class="multi-time-row"><input type="time"' in app
assert "document.querySelectorAll('#medTimesPicker input[type=\"time\"]')" in app
assert "times=readMedTimes()" in app

# Estética/composición inspirada en Nexo Familia.
assert "/* Nexo 3.7.0-beta19 · formularios con composición Nexo Familia */" in css
assert ".event-range-grid" in css
assert ".event-settings-grid" in css
assert ".multi-time-picker" in css
assert "grid-template-columns:repeat(2,minmax(0,1fr))" in css

# IDs críticos del formulario estático no pueden duplicarse tras reordenar campos.
for field_id in (
    "captureTitle", "captureDate", "captureEndDate", "captureTime", "captureEndTime",
    "captureTimeZone", "captureCategory", "captureRecurrence",
    "captureRecurrenceUntil", "capturePriority", "captureType", "captureMapsUrl",
):
    assert len(re.findall(rf'id="{re.escape(field_id)}"', html)) == 1, field_id

print("OK: formularios Nexo Familia y selectores horarios validados")
