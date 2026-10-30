#!/usr/bin/env python3
"""Evita publicar una versión cuyo resumen rechazaría el canal de actualizaciones."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
summary = json.loads((root / ".github/nexo-update-summary.json").read_text(encoding="utf-8"))
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
version = re.search(r"versionName\s+'([^']+)'", gradle)
assert version and summary.get("versionName") == version.group(1), "Versiones no coinciden"
notes = summary.get("notes")
assert isinstance(notes, str) and 40 <= len(notes.strip()) <= 360, "El resumen debe tener 40-360 caracteres"
assert not any(token in notes for token in ("#", "**", "`", "\n", "\r")), "Resumen sin Markdown ni saltos de línea"
print("OK: resumen de actualización publicable y versión coherente")
