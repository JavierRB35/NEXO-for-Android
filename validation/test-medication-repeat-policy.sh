#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP="${TMPDIR:-/tmp}/nexo-med-policy-$$"
trap 'rm -rf "$TMP"' EXIT INT TERM
mkdir -p "$TMP"
javac -d "$TMP" \
  app/src/main/java/com/jruiz/nexo/medications/MedicationRepeatPolicy.java \
  validation/MedicationRepeatPolicySelfTest.java
java -cp "$TMP" MedicationRepeatPolicySelfTest
