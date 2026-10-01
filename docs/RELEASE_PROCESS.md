# Nexo Android · flujo oficial de compilación y releases

Este documento define el proceso estándar del repositorio `JavierRB35/NEXO-for-Android`.

## Regla principal

La compilación normal de Nexo Android se realiza en **GitHub Actions**.

- Cada `push` a cualquier rama que contenga este workflow compila una APK **Release firmada** y la publica como artefacto de GitHub Actions.
- Una ejecución manual desde **Actions > Nexo Android · APK Release** hace lo mismo.
- Cada tag con formato `vX.Y.Z` compila la misma APK firmada y además la publica en **GitHub Releases**.
- AndroidIDE y `build-on-phone.sh` se mantienen como método de contingencia, no como flujo normal de distribución.

## Baseline obligatorio

No cambiar sin necesidad:

- JDK 17
- Android SDK / compileSdk 34
- targetSdk 34
- AGP 8.7.3
- Gradle Wrapper 8.9
- minSdk 26
- applicationId `com.jruiz.nexo`

## Firma permanente

Todas las APK Release deben usar la misma identidad criptográfica:

- formato: PKCS12
- alias: `nexo-release`
- fichero esperado por CI: `nexo-release.p12`

La clave privada **nunca se sube al repositorio**.

GitHub Actions espera estos Repository Secrets:

- `NEXO_KEYSTORE_BASE64`: contenido Base64 completo de `nexo-release.p12`.
- `NEXO_STORE_PASSWORD`: contraseña del almacén PKCS12.
- `NEXO_KEY_PASSWORD`: opcional; si no existe, se usa `NEXO_STORE_PASSWORD`.

## Crear una versión oficial

Antes de crear el tag:

1. Actualizar `versionName` y `versionCode` en `app/build.gradle`.
2. Actualizar README/CHANGELOG si corresponde.
3. Validar y subir los cambios a `main`.
4. Crear el tag exactamente como `v<versionName>`, por ejemplo `v3.5.0`.
5. GitHub Actions rechazará el release si el tag y `versionName` no coinciden.
6. Si todo termina correctamente, el APK aparecerá en GitHub Releases con nombre `Nexo-vX.Y.Z-release.apk`.

## Descarga en Android

Para versiones oficiales:

1. Abrir el repositorio en GitHub desde el móvil.
2. Entrar en **Releases**.
3. Abrir la versión deseada.
4. Descargar `Nexo-vX.Y.Z-release.apk`.
5. Abrir el APK descargado y confirmar la actualización de Android.

Para compilaciones intermedias:

1. Abrir **Actions**.
2. Entrar en la ejecución correspondiente de **Nexo Android · APK Release**.
3. Descargar el artefacto `Nexo-vX.Y.Z-release`.

## Fallback AndroidIDE

Si GitHub Actions no está disponible, se conserva el flujo local:

```sh
cd "/storage/emulated/0/Download/NOMBRE_PROYECTO"
sh build-on-phone.sh
```

Este fallback debe conservar el baseline histórico del proyecto y la misma clave Release permanente.
