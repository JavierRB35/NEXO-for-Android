#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
node --check app/src/main/assets/app.js
node --check app/src/main/assets/navigation.js
sh -n build-on-phone.sh
sh -n build-debug-on-phone.sh
sh -n setup-release-key.sh
sh -n backup-release-key.sh
sh -n gradlew
sh -n validation/test-location-parser.sh
sh validation/test-location-parser.sh
sh validation/test-medication-repeat-policy.sh
sh validation/test-note-title-engine.sh
sh validation/test-recording-naming.sh
sh validation/test-appointments.sh
python validation/test-appointment-query.py
python validation/test-place-query.py
python validation/test-document-query.py
python validation/test-recordings.py
python validation/test-note-autosave.py
sh validation/regression/run-benchmark.sh
sh validation/regression/run-benchmark.sh validation/regression/data/blind_cases.tsv
python validation/test-journal-seeds.py
python validation/test-list-management.py
python validation/test-nexoshare-intents.py
python validation/test-notes-home.py
python validation/test-medication-repeat-integration.py
python validation/test-transcription-integration.py
python validation/test-temporal-status.py
python validation/test-version-identity.py
python validation/test-input-hub-reliability.py
python validation/test-nexo-familia-migration.py
python validation/test-release-config.py
python validation/test-calendar-swipe.py
python validation/test-calendar-nonrecurring-range.py
node validation/test-calendar-swipe-runtime.js
if [ -n "${NEXO_QA_MODULES:-}" ]; then
    node validation/test-ui-runtime.cjs
fi
python - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET
for p in Path('app/src/main').rglob('*.xml'):
    ET.parse(p)
js=Path('app/src/main/assets/app.js').read_text()
java=Path('app/src/main/java/com/jruiz/nexo/MainActivity.java').read_text()
used=set(re.findall(r'\bNexoNative\.([A-Za-z_][A-Za-z0-9_]*)\s*\(',js))
exposed=set(re.findall(r'@JavascriptInterface\s+public\s+[\w<>\[\]]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(',java))
missing=sorted(used-exposed)
if missing:
    raise SystemExit('Métodos NexoNative sin puente Java: '+', '.join(missing))
required=[
    'data-go-location', 'goItemLocationBtn', 'goOccurrenceLocationBtn',
    'NexoNative.openLocationDirections(place)'
]
for token in required:
    if token not in js:
        raise SystemExit('Falta integración JS: '+token)
if '@JavascriptInterface public void openLocationDirections(String locationText)' not in java:
    raise SystemExit('Falta puente Java de navegación')
print('XML y puente JS↔Java OK')
PY
printf '%s\n' 'VALIDACIÓN LOCAL OK'
