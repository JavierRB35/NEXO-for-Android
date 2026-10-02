#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP="${TMPDIR:-/tmp}/nexo-gmail-filter-test-$$"
trap 'rm -rf "$TMP"' EXIT INT TERM
mkdir -p "$TMP"

javac -encoding UTF-8 -d "$TMP" \
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
  app/src/main/java/com/jruiz/nexo/communications/NotificationCandidateFilter.java \
  validation/GmailFilterSelfTest.java

java -Dfile.encoding=UTF-8 -cp "$TMP" GmailFilterSelfTest
