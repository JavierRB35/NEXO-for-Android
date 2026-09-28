#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP="${TMPDIR:-/tmp}/nexo-note-title-test-$$"
trap 'rm -rf "$TMP"' EXIT INT TERM
mkdir -p "$TMP"
javac -encoding UTF-8 -d "$TMP" \
  app/src/main/java/com/jruiz/nexo/core/TemporalParser.java \
  app/src/main/java/com/jruiz/nexo/core/LocalTitleEngine.java \
  app/src/main/java/com/jruiz/nexo/core/NoteTitleEngine.java \
  validation/NoteTitleEngineSelfTest.java
java -Dfile.encoding=UTF-8 -cp "$TMP" NoteTitleEngineSelfTest
