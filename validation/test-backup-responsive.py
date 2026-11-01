#!/usr/bin/env python3
"""Mobile layout regression for the automatic-backup card in Nexo."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
css = (root / "app/src/main/assets/styles.css").read_text(encoding="utf-8")
js = (root / "app/src/main/assets/app.js").read_text(encoding="utf-8")

start = js.index("function renderBackupPanel()")
end = js.index("function renderAppointmentsPanel()", start)
panel = js[start:end]
card_start = panel.index('<div class="backup-card">')
card_end = panel.index('<div class="backup-card">', card_start + 1)
primary_card = panel[card_start:card_end]

# An auto-sized first grid column grew to the intrinsic width of a button stack,
# crushing the copy into a thin strip on 360px Android screens.
assert "grid-template-columns:42px minmax(0,1fr)" in css, "Icon and copy need explicit grid tracks"
assert ".backup-card>.backup-actions{grid-column:1/-1;" in css, "Actions must span both grid tracks"
assert ".backup-card>.backup-actions>button{" in css and "width:100%;min-width:0;" in css
assert '<div class="backup-actions">' in primary_card
assert 'class="stack-actions"' not in primary_card
assert 'class="backup-copy"' in primary_card
for name in ("backupChooseBtn", "backupExportBtn", "backupPasswordBtn"):
    assert name in primary_card, name
assert "Nexo actualizará el mismo archivo" in primary_card
assert "backupRestoreBtn" in panel, "Restore action must remain available"

print("Copia de seguridad: dos columnas ajustables y acciones a ancho completo en móvil OK")
