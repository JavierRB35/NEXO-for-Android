#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP="${TMPDIR:-/tmp}/nexo-recording-test-$$"
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT INT TERM
javac -d "$TMP" app/src/main/java/com/jruiz/nexo/recordings/RecordingNaming.java validation/RecordingNamingSelfTest.java
java -cp "$TMP" RecordingNamingSelfTest
