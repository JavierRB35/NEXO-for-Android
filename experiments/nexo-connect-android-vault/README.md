# Nexo Connect: Android Keystore experimental

Prueba aislada del cifrado de claves privadas, en **biblioteca Android** que NO se enlaza ni se ejecuta desde Nexo principal.

- La clave AES-256 se crea en Android Keystore y no es exportable.
- Los bytes de dos keysets privados se cifran con AES-GCM y AAD ligado a identidad UUID, paquete y versión.
- El blob se almacena en `Context.getNoBackupFilesDir()`, en un archivo escrito atómicamente.
- Si se pierde la clave local no se crea otra automáticamente durante la restauración.
- Se impide sobrescribir una identidad existente sin una operación explícita de borrado.
- Hay pruebas Android instrumentadas en emulador, no una prueba en el teléfono del usuario.
- **No** implementa todavía la serialización de keysets Tink, la cuenta remota ni la transferencia segura entre teléfonos.
- No protege ante ejecución maliciosa dentro del proceso Android que ya tiene acceso a las claves descifradas.

Compilar en CI (JDK 17/Gradle 8.9/AGP 8.7.3/SDK 34):

```sh
gradle -p experiments/nexo-connect-android-vault :vault:assembleDebugAndroidTest --no-daemon --max-workers=1
```

No es un proyecto APK instalable ni una actualización pública de Nexo.
