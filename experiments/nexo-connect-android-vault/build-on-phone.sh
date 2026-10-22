#!/bin/sh
set -e
APP_NAME="NexoConnectPruebas"
cd "$(dirname "$0")"
export JAVA_OPTS="-Xmx768m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8"
sh ./gradlew :pilot:assembleDebug --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$HOME/.androidide/aapt2"
APK="pilot/build/outputs/apk/debug/pilot-debug.apk"
[ -f "$APK" ] || { echo "ERROR: APK no encontrado"; exit 1; }
DEST="/storage/emulated/0/Download/${APP_NAME}.apk"
cp "$APK" "$DEST"
echo "BUILD OK"
echo "$DEST"
