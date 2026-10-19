#!/bin/sh
set -eu

APP_NAME="Nexo_3_7_42"
DOWNLOAD_DIR="/storage/emulated/0/Download"
KEY_DIR="${NEXO_KEY_DIR:-$HOME/.nexo-release}"
KEYSTORE="$KEY_DIR/nexo-release.p12"
PASSWORD_FILE="$KEY_DIR/nexo-release.password"
ALIAS="nexo-release"
APKSIGNER="$HOME/android-sdk/build-tools/34.0.4/apksigner"

cd "$(dirname "$0")"

sh ./prepare-transcription-runtime.sh

# Primera ejecución: crea automáticamente la firma permanente y su carpeta
# Nexo_Signing_Backup. En ejecuciones futuras restaura/verifica la misma clave.
sh ./setup-release-key.sh

[ -f "$KEYSTORE" ] || { echo "ERROR: falta la clave Release."; exit 1; }
[ -f "$PASSWORD_FILE" ] || { echo "ERROR: falta el archivo de contraseña Release."; exit 1; }
[ -x "$APKSIGNER" ] || {
    echo "ERROR: no se encuentra apksigner 34.0.4 en:"
    echo "$APKSIGNER"
    exit 1
}

NEXO_STORE_PASSWORD="$(tr -d '\r\n' < "$PASSWORD_FILE")"
[ -n "$NEXO_STORE_PASSWORD" ] || { echo "ERROR: contraseña Release vacía."; exit 1; }

export NEXO_STORE_PASSWORD
export NEXO_KEY_PASSWORD="$NEXO_STORE_PASSWORD"
export NEXO_RELEASE_KEYSTORE="$KEYSTORE"
export JAVA_OPTS="-Xmx768m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8"

# Verificación temprana de clave, contraseña y alias.
keytool -list \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -storepass "$NEXO_STORE_PASSWORD" \
  -alias "$ALIAS" >/dev/null

sh ./gradlew assembleRelease \
  --no-daemon \
  --max-workers=1 \
  -Pandroid.aapt2FromMavenOverride="$HOME/.androidide/aapt2"

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "ERROR: APK Release no encontrado"; exit 1; }

"$APKSIGNER" verify --verbose --print-certs "$APK"

# Copia final SIEMPRE directamente a Descargas, nunca a subcarpetas del proyecto.
mkdir -p "$DOWNLOAD_DIR"
DEST="$DOWNLOAD_DIR/${APP_NAME}.apk"
cp -f "$APK" "$DEST"
sync
[ -f "$DEST" ] || { echo "ERROR: no se pudo copiar el APK a Descargas"; exit 1; }

echo ""
echo "RELEASE BUILD OK"
echo "FIRMA VERIFICADA"
echo "APK FINAL EN DESCARGAS:"
echo "$DEST"
ls -lh "$DEST"
echo ""
echo "COPIA PERMANENTE DE FIRMA:"
echo "$DOWNLOAD_DIR/Nexo_Signing_Backup"
