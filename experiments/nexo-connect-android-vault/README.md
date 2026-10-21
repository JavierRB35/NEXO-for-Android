# Nexo Connect · laboratorio Android aislado

Prueba experimental de cifrado E2EE y emparejamiento entre **dos cuentas verificadas** con Supabase. No forma parte de la aplicación Nexo publicada ni altera el móvil real de nadie. La PR #65 sigue en borrador.

## Funciones del APK piloto

- Autenticación por email OTP, JWT validado en Supabase, segundo OTP para registrar/sustituir el dispositivo y sesión protegida con Android Keystore.
- Claves privadas Ed25519 (firma) y HPKE X25519 protegidas en almacenamiento privado con clave AES-256 no exportable del Keystore. No se restauran silenciosamente claves perdidas.
- Generación de identidad pública y de invitación de un solo uso en QR.
- Dos formas de leer el QR: cámara ZXing **o seleccionar una imagen con el gestor de archivos**. Importar imagen **no necesita cámara ni permisos de almacenamiento** y no envía el archivo a ningún servidor.
- **La importación del QR no establece confianza**: ambas huellas SHA-256 completas deben verificarse independientemente del QR y de Supabase, con confirmación expresa de la persona.
- Firmas Ed25519 con desafío de un solo uso; vínculo verificado, permisos, recepción y desvinculación.
- Envío experimental de notas Nexo con Google Tink HPKE y contexto NXC1, bandeja cifrada, importación privada y ACK solo tras guardar; sin bucles automáticos de SMS, OTP o reenvíos.

## Importar una imagen QR sin cámara

En el APK `Nexo Connect Pruebas`, una vez iniciada una sesión válida, selecciona **3a. Importar QR de imagen (sin cámara)**. El selector de Android permite escoger un PNG/JPEG con el QR de la otra persona. El archivo se descodifica **localmente** y pasa a la misma pantalla de comparación manual de las dos huellas de identidad que el escáner con cámara.

Por seguridad, el lector no acepta archivos de más de 4 MiB, imágenes con dimensiones superiores a 8192 píxeles por lado, ni QR que no sigan el protocolo Nexo. Las huellas de referencia deben proceder **del otro teléfono o de un canal confiable distinto**. Una imagen reenviada por sí sola no demuestra la identidad del contacto.

El emparejamiento por imagen sirve también para pruebas en emuladores **sin cámaras ni salas virtuales**, pero no sustituye la verificación humana en el uso real.

## Pruebas de consola y automatizadas

Windows desde `experiments/nexo-connect-android-vault`:

```powershell
..\..\gradlew.bat -p . :vault:connectedDebugAndroidTest --no-daemon --max-workers=1
```

La batería Android incluye pruebas del descodificador de imágenes, QR reales con ZXing, huellas, KeyStore, importación, mensajes y revocación. No abre cámaras en las pruebas nuevas de imagen.

La fase de laboratorio **HTTPS E2EE de extremo a extremo** con dos cuentas efímeras, Google Tink, notas en ambas direcciones, ACK y revocación finalizó correctamente y se documentó en la PR #65. Las dos cuentas de ese laboratorio fueron eliminadas tras autorización, sin afectar otros usuarios. No vuelvas a ejecutar el ensayo `e2eeLab` con aquellos UUID: las cuentas ya no existen.

## Límites antes de integrar en Nexo principal

- El intercambio HTTPS real se verificó desde una **consola JVM**, no como recorrido humano completo dentro del piloto Android.
- La comparación independiente de identidad, los estados de recuperación, accesibilidad y pruebas de usabilidad de Android deben validarse antes de conectar estas pantallas con la aplicación estable.
- Ninguna prueba de laboratorio implica certificación de seguridad, comercialización o despliegue de producción.
- No fusionar la PR #65 ni trasladar claves o credenciales del laboratorio a producción sin revisión separada.

Compatibilidad conocida: Java 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## Puerta de entrada en Nexo completo (experimental)

**Solo en el build DEBUG de la rama experimental** se añade `Ajustes → Nexo Connect · experimental → Abrir Nexo Connect Pruebas`. El enlace abre explícitamente el APK independiente `com.jruiz.nexo.connect.pilot`, si está instalado. En la APK RELEASE, la sección permanece oculta y el puente bloquea el lanzamiento. El control **no** copia ni expone contenidos, cuentas, claves o sesiones de Nexo principal al piloto.

Es una integración de navegación, **NO la incorporación de E2EE a la base de datos principal**, ni sustituye un ensayo HTTPS real entre dos instancias Android. El piloto sigue con claves y base de datos independientes.

Desde PowerShell en `experiments/nexo-connect-android-vault` se pueden pasar las pruebas y compilar las dos APK localmente:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -IncludeNexoMain
```
Omitir los tests del emulador: añadir `-SkipEmulator`. Ninguna variante usa GitHub Actions.

### Integración E2EE pendiente
- Sustituir configuración LAB por perfil de producción y diseño de autenticación, sin reutilizar claves entre paquetes.
- Integrar en la interfaz nativa de Nexo consentimiento por destinatario, envío de notas y listas, bandeja de entrada y recepción verificada con inserción SQLite atómica.
- Manejar revocaciones, reenvíos inciertos sin bucles, fallos de ACK, cambios de teléfono, recuperación y accesibilidad.
- Ensayo HTTPS real desde **dos Android** y revisión independiente de seguridad antes de unir PR #65. Los 102 tests por AVD prueban componentes locales, no este escenario completo.

## Windows: solución al error de biblioteca Sherpa ausente

La APK completa Nexo requiere la dependencia local `app/libs/sherpa-onnx-1.13.7.aar`, ignorada expresamente por Git (`.gitignore`). El instalador Linux `prepare-transcription-runtime.sh` no se ejecutaba al usar `run-local-tests.ps1` en Windows, lo que ocasionaba `checkDebugAarMetadata FAILED`.

El script PowerShell ahora ejecuta `Ensure-SherpaRuntime` **solamente si se compila Nexo principal**. Si falta, descarga una vez la versión v1.13.7 desde la release oficial `k2-fsa/sherpa-onnx`, verifica SHA-256 `c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8` (mismo pin que el script Linux), y solo entonces la instala en `app/libs`. Rechaza archivos con un hash erróneo. No cambia versiones de JDK, Gradle o Android y **no utiliza GitHub Actions**.

Para reintentar únicamente Nexo principal sin repetir el piloto ni los 102 tests por AVD, en PowerShell desde `experiments/nexo-connect-android-vault`:

```powershell
git pull --ff-only origin experiment/nexo-connect-phase0-crypto
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -MainOnly
```

Esta instrucción genera solo un APK de depuración de Nexo y no lo instala ni fusiona la PR. La primera descarga necesita conexión a GitHub Releases, no a Actions.
