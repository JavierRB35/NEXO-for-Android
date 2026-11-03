#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT INT TERM
javac -encoding UTF-8 --release 17 -d "$OUT" \
 validation/regression/stubs/org/json/JSONObject.java \
 validation/regression/stubs/org/json/JSONArray.java \
 app/src/main/java/com/jruiz/nexo/core/TemporalParser.java \
 app/src/main/java/com/jruiz/nexo/core/EventSemanticParser.java \
 app/src/main/java/com/jruiz/nexo/core/LocationParser.java \
 app/src/main/java/com/jruiz/nexo/core/RecurrenceEngine.java \
 app/src/main/java/com/jruiz/nexo/core/MedicationParser.java \
 app/src/main/java/com/jruiz/nexo/core/DescriptionExtractor.java \
 app/src/main/java/com/jruiz/nexo/core/LocalTitleEngine.java \
 app/src/main/java/com/jruiz/nexo/core/RuleClassifier.java \
 app/src/main/java/com/jruiz/nexo/core/AppointmentParser.java \
 app/src/main/java/com/jruiz/nexo/core/title/*.java \
 validation/title-engine/IntegratedTitleSelfTest.java \
 validation/title-engine/BoundarySelfTest.java
java -Dfile.encoding=UTF-8 -Duser.timezone=Europe/Madrid -cp "$OUT" IntegratedTitleSelfTest validation/title-engine/approved_titles.tsv
java -Dfile.encoding=UTF-8 -cp "$OUT" BoundarySelfTest
