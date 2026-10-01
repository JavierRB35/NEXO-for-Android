# Nexo Android · flujo oficial de compilación y releases

Este documento define el proceso estándar del repositorio `JavierRB35/NEXO-for-Android`.

## Regla principal

La compilación normal de Nexo Android se realiza en **GitHub Actions**.

- Cada push a una rama que contenga el workflow oficial ejecuta validaciones y compila una APK **Release firmada** como artefacto.
- Una ejecución manual desde Actions hace lo mismo.
- Un tag con formato `vX.Y.Z` compila la APK firmada y puede publicarla en **GitHub Releases**.
- AndroidIDE y `build-on-phone.sh` se mantienen como método de contingencia.

## Baseline obligatorio

No cambiar sin necesidad:

- JDK 17
- Android SDK / compileSdk 34
- targetSdk 34
- AGP 8.7.3
- Gradle 8.9
- minSdk 26
- applicationId `com.jruiz.nexo`

## Firma permanente

Todas las APK Release deben usar la misma identidad criptográfica:

- formato: PKCS12
- alias: `nexo-release`
- fichero esperado por CI: `nexo-release.p12`
- huella SHA-256 fijada: `59:57:99:E3:51:23:34:18:F0:5B:C5:05:3E:42:B3:AF:F4:39:D6:77:FF:BB:0F:59:A1:D9:2E:29:D1:5F:EC:48`

La clave privada **nunca se sube al repositorio**.

GitHub Actions usa:

- `NEXO_KEYSTORE_BASE64`
- `NEXO_STORE_PASSWORD`
- `NEXO_KEY_PASSWORD` (opcional; si falta se usa la contraseña del almacén)

## Promover cambios a main

1. Crear una rama desde `main` o desde la base estable correspondiente.
2. Desarrollar y validar el cambio.
3. Abrir Pull Request hacia `main`.
4. Comprobar que las validaciones y la compilación Release terminan correctamente.
5. Revisar el diff.
6. Hacer merge solo cuando el cambio esté aprobado.

`main` representa la fuente de verdad de la versión estable.

## Crear una versión oficial

1. Actualizar `versionName` y `versionCode`.
2. Actualizar README/CHANGELOG.
3. Integrar el cambio validado en `main`.
4. Crear el tag exactamente como `v<versionName>`, por ejemplo `v3.7.0`.
5. El workflow rechazará el release si el tag no coincide con `versionName`.
6. La GitHub Release solo se crea al usar el tag; un merge a `main` por sí solo no publica una Release.

## Fallback AndroidIDE

```sh
cd "/storage/emulated/0/Download/NOMBRE_PROYECTO"
sh build-on-phone.sh
```

El fallback debe conservar el baseline y la misma firma Release permanente.
