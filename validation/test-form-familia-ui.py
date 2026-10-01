from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
html = (root / "app/src/main/assets/index.html").read_text(encoding="utf-8")
app = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")

for field_id in ("captureDate", "captureEndDate"):
    assert f'id="{field_id}" type="date"' in html
for field_id in ("captureTime", "captureEndTime"):
    assert f'id="{field_id}" type="time"' in html

assert 'class="form-grid capture-basics event-form-grid"' in html
assert html.count('class="form-span-2 event-range-grid"') >= 2
assert 'class="form-grid event-settings-grid"' in html
assert '>Repetición<select id="captureRecurrence"' in html

for field_id in ("editTime", "editEndTime", "occTime", "occEndTime", "medStartTime"):
    assert f'id="{field_id}" type="time"' in app
assert app.count('class="form-grid event-range-grid"') >= 4
assert app.count('class="form-grid event-settings-grid"') >= 4

assert 'id="medTimes" type="text"' not in app
assert 'id="medTimesPicker"' in app
assert 'class="multi-time-row"><input type="time"' in app
assert "document.querySelectorAll('#medTimesPicker input[type=\"time\"]')" in app
assert "times=readMedTimes()" in app

assert "/* Nexo 3.7.0-beta21 · formularios compactos y branding moderno */" in css
assert ".event-range-grid" in css
assert ".event-settings-grid" in css
assert ".multi-time-picker" in css
assert "grid-template-columns:repeat(2,minmax(0,1fr))" in css

for field_id in (
    "captureTitle", "captureDate", "captureEndDate", "captureTime", "captureEndTime",
    "captureTimeZone", "captureCategory", "captureRecurrence",
    "captureRecurrenceUntil", "capturePriority", "captureType", "captureMapsUrl",
):
    assert len(re.findall(rf'id="{re.escape(field_id)}"', html)) == 1, field_id

print("OK: fechas/horas compactas, selectores nativos y formulario Nexo Familia validados")
