#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
OUT="${TMPDIR:-/tmp}/nexo-parser-regression"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
 validation/regression/stubs/org/json/JSONObject.java validation/regression/stubs/org/json/JSONArray.java \
 app/src/main/java/com/jruiz/nexo/core/EventSemanticParser.java \
 app/src/main/java/com/jruiz/nexo/core/TemporalParser.java \
 app/src/main/java/com/jruiz/nexo/core/LocationParser.java \
 app/src/main/java/com/jruiz/nexo/core/RecurrenceEngine.java \
 app/src/main/java/com/jruiz/nexo/core/MedicationParser.java \
 app/src/main/java/com/jruiz/nexo/core/ListVoiceParser.java \
 app/src/main/java/com/jruiz/nexo/core/AlarmIntentParser.java \
 app/src/main/java/com/jruiz/nexo/core/DescriptionExtractor.java \
 app/src/main/java/com/jruiz/nexo/core/LocalTitleEngine.java \
 app/src/main/java/com/jruiz/nexo/core/RuleClassifier.java \
 app/src/main/java/com/jruiz/nexo/core/UserMessageInterpreter.java \
 validation/regression/ParserRegressionBenchmark.java
DATA="${1:-validation/regression/data/cases.tsv}"
java -cp "$OUT" com.jruiz.nexo.core.ParserRegressionBenchmark "$DATA"
