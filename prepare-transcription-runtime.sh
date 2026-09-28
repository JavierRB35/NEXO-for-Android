#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p app/libs
DEST="app/libs/sherpa-onnx-1.13.7.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-1.13.7.aar"
SHA="c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8"
check() {
  [ -f "$DEST" ] || return 1
  actual="$(sha256sum "$DEST" | awk '{print $1}')"
  [ "$actual" = "$SHA" ]
}
if check; then
  echo "sherpa-onnx 1.13.7 OK"
  exit 0
fi
rm -f "$DEST.tmp" "$DEST"
echo "Descargando sherpa-onnx 1.13.7 (solo la primera vez)..."
if command -v curl >/dev/null 2>&1; then
  curl -L --fail --retry 3 --connect-timeout 30 -o "$DEST.tmp" "$URL"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$DEST.tmp" "$URL"
else
  echo "ERROR: se necesita curl o wget para descargar sherpa-onnx"
  exit 1
fi
mv "$DEST.tmp" "$DEST"
check || { echo "ERROR: SHA-256 incorrecto para sherpa-onnx"; rm -f "$DEST"; exit 1; }
echo "sherpa-onnx descargado y verificado"
