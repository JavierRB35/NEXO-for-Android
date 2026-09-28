#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/nexo-location-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  app/src/main/java/com/jruiz/nexo/core/LocationParser.java \
  validation/LocationParserSelfTest.java
java -cp "$OUT" com.jruiz.nexo.core.LocationParserSelfTest
