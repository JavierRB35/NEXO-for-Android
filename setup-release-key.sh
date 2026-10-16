#!/bin/sh
set -eu

KEY_DIR="${NEXO_KEY_DIR:-$HOME/.nexo-release}"
KEYSTORE="$KEY_DIR/nexo-release.p12"
PASSWORD_FILE="$KEY_DIR/nexo-release.password"
ALIAS="nexo-release"
BACKUP_DIR="${NEXO_SIGNING_BACKUP_DIR:-/storage/emulated/0/Download/Nexo_Signing_Backup}"
BACKUP_KEYSTORE="$BACKUP_DIR/nexo-release.p12"
BACKUP_PASSWORD="$BACKUP_DIR/nexo-release.password.txt"
BACKUP_README="$BACKUP_DIR/LEEME_NEXO_SIGNING.txt"

make_backup_readme() {
    cat > "$BACKUP_README" <<'TXT'
NEXO · COPIA DE FIRMA RELEASE

Esta carpeta contiene la identidad criptográfica permanente de Nexo:
- nexo-release.p12            -> clave privada de firma
- nexo-release.password.txt   -> contraseña necesaria para usar la clave

IMPORTANTE:
1. Guarda una copia completa de esta carpeta fuera del teléfono.
2. No publiques ni compartas estos archivos.
3. No pierdas la clave: las futuras APK Release de Nexo deben firmarse con la misma.
4. Para restaurarla en otro teléfono, copia esta carpeta a Download y ejecuta
   el build-on-phone.sh de cualquier proyecto Release de Nexo.
TXT
}

verify_pair() {
    KS="$1"
    PF="$2"
    [ -f "$KS" ] || return 1
    [ -f "$PF" ] || return 1
    PASS="$(tr -d '\r\n' < "$PF")"
    [ -n "$PASS" ] || return 1
    keytool -list \
      -keystore "$KS" \
      -storetype PKCS12 \
      -storepass "$PASS" \
      -alias "$ALIAS" >/dev/null 2>&1
}

# Compatibilidad: las versiones anteriores guardaban la copia bajo HOME.
# Solo se usa si aún no existe respaldo en Descargas y la pareja antigua es válida.
LEGACY_BACKUP_DIR="$HOME/.nexo-release/backup"
if [ "$BACKUP_DIR" != "$LEGACY_BACKUP_DIR" ] && \
   [ ! -e "$BACKUP_KEYSTORE" ] && [ ! -e "$BACKUP_PASSWORD" ] && \
   [ -f "$LEGACY_BACKUP_DIR/nexo-release.p12" ] && \
   [ -f "$LEGACY_BACKUP_DIR/nexo-release.password.txt" ]; then
    if verify_pair "$LEGACY_BACKUP_DIR/nexo-release.p12" "$LEGACY_BACKUP_DIR/nexo-release.password.txt"; then
        mkdir -p "$BACKUP_DIR"
        cp "$LEGACY_BACKUP_DIR/nexo-release.p12" "$BACKUP_KEYSTORE"
        cp "$LEGACY_BACKUP_DIR/nexo-release.password.txt" "$BACKUP_PASSWORD"
    fi
fi

mkdir -p "$KEY_DIR" "$BACKUP_DIR"
chmod 700 "$KEY_DIR" 2>/dev/null || true

# 1) Si la copia permanente ya existe, se considera la fuente de verdad y
#    restaura automáticamente la copia privada de trabajo si hiciera falta.
if verify_pair "$BACKUP_KEYSTORE" "$BACKUP_PASSWORD"; then
    cp "$BACKUP_KEYSTORE" "$KEYSTORE"
    cp "$BACKUP_PASSWORD" "$PASSWORD_FILE"
    chmod 600 "$KEYSTORE" "$PASSWORD_FILE" 2>/dev/null || true
    make_backup_readme
    echo "FIRMA RELEASE RESTAURADA/VERIFICADA"
    echo "$BACKUP_DIR"
    exit 0
fi

# 2) Si ya existe una pareja válida en HOME, crea/actualiza automáticamente
#    la carpeta de respaldo en Download.
if verify_pair "$KEYSTORE" "$PASSWORD_FILE"; then
    cp "$KEYSTORE" "$BACKUP_KEYSTORE"
    cp "$PASSWORD_FILE" "$BACKUP_PASSWORD"
    make_backup_readme
    echo "FIRMA RELEASE EXISTENTE VERIFICADA"
    echo "COPIA DE SEGURIDAD ACTUALIZADA:"
    echo "$BACKUP_DIR"
    exit 0
fi

# No sobrescribir una clave parcial/desconocida: podría ser una clave válida
# cuya contraseña no esté disponible en archivo.
if [ -e "$KEYSTORE" ] || [ -e "$PASSWORD_FILE" ] || [ -e "$BACKUP_KEYSTORE" ] || [ -e "$BACKUP_PASSWORD" ]; then
    echo "ERROR: existe una configuración de firma incompleta o no verificable."
    echo "No se ha sobrescrito ningún archivo para evitar perder una posible clave válida."
    echo "Revisa: $KEY_DIR"
    echo "y: $BACKUP_DIR"
    exit 1
fi

# 3) Primera ejecución: generar una contraseña criptográficamente aleatoria,
#    sin pedir nada al usuario, y guardarla junto a la copia de seguridad.
PASSWORD="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n\r')"
[ "${#PASSWORD}" -ge 40 ] || { echo "ERROR: no se pudo generar una contraseña aleatoria segura."; exit 1; }

printf '%s\n' "$PASSWORD" > "$PASSWORD_FILE"
chmod 600 "$PASSWORD_FILE" 2>/dev/null || true

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Nexo Release, OU=Personal, O=Nexo, L=Valencia, ST=Valencia, C=ES" >/dev/null

chmod 600 "$KEYSTORE" 2>/dev/null || true
verify_pair "$KEYSTORE" "$PASSWORD_FILE" || { echo "ERROR: la clave recién creada no supera la verificación."; exit 1; }

cp "$KEYSTORE" "$BACKUP_KEYSTORE"
cp "$PASSWORD_FILE" "$BACKUP_PASSWORD"
make_backup_readme

# Verificar también la copia que el usuario conservará.
verify_pair "$BACKUP_KEYSTORE" "$BACKUP_PASSWORD" || { echo "ERROR: la copia de seguridad de firma no supera la verificación."; exit 1; }

echo "CLAVE RELEASE CREADA AUTOMÁTICAMENTE"
echo "No necesitas introducir ninguna contraseña."
echo "COPIA DE SEGURIDAD CREADA EN:"
echo "$BACKUP_DIR"
echo "GUARDA ESA CARPETA TAMBIÉN FUERA DEL TELÉFONO."
