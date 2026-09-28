#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP="${TMPDIR:-/tmp}/nexo-appointments-test-$$"
trap 'rm -rf "$TMP"' EXIT INT TERM
mkdir -p "$TMP"
javac -encoding UTF-8 -d "$TMP" \
  validation/regression/stubs/org/json/JSONObject.java \
  validation/regression/stubs/org/json/JSONArray.java \
  validation/appointment-runtime/stubs/android/content/Context.java \
  validation/appointment-runtime/stubs/com/jruiz/nexo/data/DatabaseHelper.java \
  validation/appointment-runtime/stubs/com/jruiz/nexo/reminders/ReminderScheduler.java \
  app/src/main/java/com/jruiz/nexo/core/TemporalParser.java \
  app/src/main/java/com/jruiz/nexo/core/LocationParser.java \
  app/src/main/java/com/jruiz/nexo/core/RecurrenceEngine.java \
  app/src/main/java/com/jruiz/nexo/core/MedicationParser.java \
  app/src/main/java/com/jruiz/nexo/core/DescriptionExtractor.java \
  app/src/main/java/com/jruiz/nexo/core/LocalTitleEngine.java \
  app/src/main/java/com/jruiz/nexo/core/RuleClassifier.java \
  app/src/main/java/com/jruiz/nexo/core/AppointmentParser.java \
  app/src/main/java/com/jruiz/nexo/core/PlaceEntry.java \
  app/src/main/java/com/jruiz/nexo/core/DocumentEntry.java \
  app/src/main/java/com/jruiz/nexo/documents/DocumentFiles.java \
  app/src/main/java/com/jruiz/nexo/core/NexoService.java \
  validation/AppointmentServiceSelfTest.java \
  validation/PlaceServiceSelfTest.java \
  validation/DocumentServiceSelfTest.java
java -Dfile.encoding=UTF-8 -cp "$TMP" AppointmentServiceSelfTest
java -Dfile.encoding=UTF-8 -cp "$TMP" PlaceServiceSelfTest
java -Dfile.encoding=UTF-8 -cp "$TMP" DocumentServiceSelfTest
