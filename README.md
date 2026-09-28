# NEXO for Android

Repositorio de Nexo para Android.

## Versión actual
**3.4.9** — versionCode **55**

Esta versión ha sido reconstruida de forma controlada a partir de la copia persistente 3.3.2 y del historial técnico documentado de las versiones 3.4.x. La batería de validación local del proyecto finaliza con **VALIDACIÓN LOCAL OK**.

### Entorno AndroidIDE
- JDK 17
- Android SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- ARM64/aarch64

La compilación en el teléfono se realiza con:

```sh
sh build-on-phone.sh
```

La versión 3.4.9 incluye, entre otros cambios, transcripción local con Whisper Base multilingüe INT8 mediante sherpa-onnx, reproductor con seek y velocidades 1x/1.5x/2x, mensajes configurables de medicación y conciliación automática de estados temporales.

> Nota: la reconstrucción ha pasado la validación estática/regresión disponible, pero debe compilarse e instalarse en AndroidIDE para confirmar el APK final en el dispositivo.
