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
