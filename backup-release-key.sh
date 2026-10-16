#!/bin/sh
set -eu

KEY_DIR="${NEXO_KEY_DIR:-$HOME/.nexo-release}"
SRC_KEY="$KEY_DIR/nexo-release.p12"
SRC_PASSWORD="$KEY_DIR/nexo-release.password"
DEST_DIR="${NEXO_SIGNING_BACKUP_DIR:-/storage/emulated/0/Download/Nexo_Signing_Backup}"
DEST_KEY="$DEST_DIR/nexo-release.p12"
DEST_PASSWORD="$DEST_DIR/nexo-release.password.txt"
DEST_README="$DEST_DIR/LEEME_NEXO_SIGNING.txt"

[ -f "$SRC_KEY" ] || { echo "ERROR: no existe $SRC_KEY"; exit 1; }
[ -f "$SRC_PASSWORD" ] || { echo "ERROR: no existe $SRC_PASSWORD"; exit 1; }

mkdir -p "$DEST_DIR"
cp "$SRC_KEY" "$DEST_KEY"
cp "$SRC_PASSWORD" "$DEST_PASSWORD"
cat > "$DEST_README" <<'TXT'
NEXO · COPIA DE FIRMA RELEASE

Conserva juntos estos archivos:
- nexo-release.p12
- nexo-release.password.txt

Son necesarios para firmar futuras versiones Release de Nexo con la misma identidad.
Guarda una copia completa de esta carpeta fuera del teléfono y no la compartas.
TXT

echo "COPIA DE FIRMA ACTUALIZADA"
echo "$DEST_DIR"
