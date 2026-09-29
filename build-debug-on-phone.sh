#!/bin/sh
set -eu
APP_NAME="Nexo_3_7_0_beta1_DEBUG"
cd "$(dirname "$0")"
export JAVA_OPTS="-Xmx768m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8"
sh ./gradlew assembleDebug --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$HOME/.androidide/aapt2"
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: APK Debug no encontrado"; exit 1; }
DEST="/storage/emulated/0/Download/${APP_NAME}.apk"
cp "$APK" "$DEST"
echo "DEBUG BUILD OK"
echo "$DEST"
