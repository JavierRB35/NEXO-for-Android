# Nexo Input Hub v0.3 · AndroidIDE

Rama de compilación: `feature/nexo-input-hub-v0.3`.

Identidad de esta versión:
- Nexo **3.6.0-beta3**
- `versionCode 65`
- `applicationId com.jruiz.nexo`
- Base de datos `nexo.db`, esquema **18**

## Entorno AndroidIDE validado

- AndroidIDE-Rv2 / ARM64
- OpenJDK 17
- Android SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`

No actualizar estas versiones salvo necesidad.

## Compilar en el teléfono

Descomprime el ZIP de la rama directamente como una única carpeta de proyecto dentro de Download, por ejemplo:

```text
/storage/emulated/0/Download/Nexo_Input_Hub_v0_3/
```

Después:

```sh
cd "/storage/emulated/0/Download/Nexo_Input_Hub_v0_3"
sh build-on-phone.sh
```

No uses `./build-on-phone.sh` ni `./gradlew` desde Download.

El script:
1. descarga y verifica `sherpa-onnx 1.13.7` si falta;
2. prepara/restaura la firma Release permanente;
3. compila con `JAVA_OPTS=-Xmx768m`, `--no-daemon`, `--max-workers=1` y el AAPT2 de AndroidIDE;
4. verifica la firma con Build Tools 34.0.4;
5. copia el APK final directamente a:

```text
/storage/emulated/0/Download/Nexo_Input_Hub_v0_3.apk
```

La primera compilación necesita Internet para descargar dependencias que aún no estén en caché y el AAR de sherpa-onnx.

## Qué incorpora V0.3

- Calendar Provider directo.
- SMS Provider directo mediante permiso opcional `READ_SMS`.
- Línea base inicial para no importar el histórico de SMS.
- Gmail API en modo solo lectura.
- Consulta Gmail al abrir Nexo y periódicamente en segundo plano.
- Clasificación local antes de incorporar candidatos al Inbox.
- Deduplicación mediante `external_watch_items`.
- Las fuentes directas Gmail/SMS evitan su captura duplicada por notificación.
- WhatsApp y Telegram continúan mediante NotificationListener.
- Botón **Buscar entradas ahora**.
- Ninguna captura crea una cita/tarea automáticamente sin confirmación del usuario.

### Requisito externo para Gmail

Para activar Gmail directo debe existir una configuración OAuth válida en Google Cloud para:
- paquete `com.jruiz.nexo`;
- SHA-1 de la firma Release;
- Gmail API habilitada;
- consentimiento OAuth;
- scope `gmail.readonly`.

Este requisito no impide compilar ni instalar la aplicación; solo condiciona la conexión de Gmail.

## Validación

Antes de compilar puede ejecutarse:

```sh
sh validation/validate-project.sh
```

La rama mantiene el Gradle Wrapper completo y los scripts de compilación para AndroidIDE.
