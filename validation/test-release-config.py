from pathlib import Path
r=Path(__file__).resolve().parents[1]
g=(r/'app/build.gradle').read_text()
b=(r/'build-on-phone.sh').read_text()
s=(r/'setup-release-key.sh').read_text()
bk=(r/'backup-release-key.sh').read_text()

required_gradle=[
    "signingConfigs", "release {", "debuggable false", "minifyEnabled true",
    "shrinkResources true", "keyAlias 'nexo-release'", "storeType 'PKCS12'",
    "NEXO_STORE_PASSWORD", "NEXO_RELEASE_KEYSTORE"
]
for x in required_gradle:
    assert x in g, x
for forbidden in ["storePassword '", "keyPassword '"]:
    assert forbidden not in g, 'No debe haber contraseñas incrustadas'

assert 'sh ./setup-release-key.sh' in b
assert 'assembleRelease' in b
assert 'verify --verbose --print-certs' in b
assert 'nexo-release.password' in b
assert 'read -r NEXO_STORE_PASSWORD' not in b
assert 'stty -echo' not in b

assert 'keytool -genkeypair' in s
assert '-keysize 4096' in s
assert '-validity 10000' in s
assert '/dev/urandom' in s
assert 'nexo-release.password.txt' in s
assert 'Nexo_Signing_Backup' in s
assert 'read_secret' not in s
assert 'stty -echo' not in s

assert 'nexo-release.password.txt' in bk
assert 'Nexo_Signing_Backup' in bk

# No se empaqueta ninguna clave privada ni contraseña generada.
assert not list(r.rglob('*.jks'))
assert not list(r.rglob('*.p12'))
assert not list(r.rglob('*.keystore'))
assert not [p for p in r.rglob('nexo-release.password*') if p.is_file()]
print('Configuración Release automática y ausencia de secretos: OK')
