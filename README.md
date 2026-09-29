# Nexo 3.7.0-beta7 · AndroidIDE

Rama de trabajo: `feature/nexo-input-hub-v0.7`.

Identidad:
- Nexo **3.7.0-beta7**
- `versionCode 72`
- `applicationId com.jruiz.nexo`
- Base de datos `nexo.db`, esquema **20**

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

Descomprime el proyecto como una única carpeta:

```text
/storage/emulated/0/Download/Nexo_3_7_0_beta7/
```

Después:

```sh
cd "/storage/emulated/0/Download/Nexo_3_7_0_beta7"
sh build-on-phone.sh
```

No uses `./gradlew` desde Download.

El script conserva la firma Release permanente, usa `JAVA_OPTS=-Xmx768m`, `--no-daemon`, `--max-workers=1` y el AAPT2 de AndroidIDE, verifica la firma y copia:

```text
/storage/emulated/0/Download/Nexo_3_7_0_beta7.apk
```

## Input Hub beta4

- Calendar Provider directo con observador de cambios y escaneo periódico de respaldo.
- SMS Provider directo + respaldo por NotificationListener.
- Gmail API de solo lectura + respaldo por NotificationListener.
- Gmail revisa correo recibido reciente aunque haya sido archivado automáticamente.
- Lectura de cuerpo completo y partes MIME de texto/calendario, incluidas partes servidas mediante `attachmentId`.
- Clasificación local de alta sensibilidad: ante una señal razonable, el contenido llega a Inbox para decisión humana.
- Deduplicación entre vía directa y notificación.
- Errores transitorios no consolidan un mensaje como descartado.
- Diagnóstico por fuente: vistos, candidatos, descartados y errores.
- WhatsApp y Telegram continúan mediante NotificationListener.
- Ninguna captura crea automáticamente una cita/tarea definitiva sin revisión.

## Migración Nexo Familia

La snapshot incluida se ha regenerado directamente desde el Supabase de Nexo Familia y contiene:

- **33 eventos** de la familia Ruiz-Villena.
- **46 reglas de aviso**.
- **4 miembros**.
- Todos los campos originales disponibles en la tabla `events` y las reglas originales.

Beta7 conserva la reconstrucción completa 33/33 y corrige además los estados locales: los eventos `selected` futuros ya no quedan marcados como `done`; se respetan los estados de Supabase y el tiempo real del evento. La auditoría local está disponible desde Copia de seguridad > Nexo Familia. Los campos que son realmente `null` en Supabase no se inventan; se preserva además el JSON original completo en la capa de compatibilidad.

## Validación

```sh
sh validation/validate-project.sh
```

La rama mantiene el Gradle Wrapper completo y los scripts de compilación para AndroidIDE.
