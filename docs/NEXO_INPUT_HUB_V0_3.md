# Nexo Input Hub v0.3

## Objetivo

Separar la captación de información de las notificaciones Android.

Nexo Inbox pasa a recibir candidatos desde varias fuentes independientes:

- Calendar Provider Android
- SMS Provider Android
- Gmail API
- NotificationListener como fallback para WhatsApp/Telegram
- Share Intent para archivos, URLs y contenido compartido manualmente

## SMS directo

Permiso: `READ_SMS`.

Flujo:

1. El usuario activa SMS directo.
2. Nexo pide permiso.
3. Se crea una línea base de los SMS existentes.
4. `JobScheduler + TriggerContentUri` observa cambios en `Telephony.Sms.CONTENT_URI`.
5. Nuevos SMS de Inbox se clasifican localmente.
6. Solo candidatos accionables se guardan en `inbox_captures`.

No depende de las notificaciones de la app Mensajes.

## Gmail directo

Scope:

`https://www.googleapis.com/auth/gmail.readonly`

Flujo:

1. El usuario conecta una cuenta Google desde Ajustes.
2. Google solicita consentimiento.
3. Nexo crea una línea base de mensajes recientes del Inbox.
4. Nexo consulta Gmail al abrir la app y mediante un Job periódico.
5. Se usan remitente, asunto y snippet para la clasificación local.
6. Solo candidatos accionables entran en Inbox.

### Requisito externo

Para que Google autorice la app es necesario habilitar Gmail API y registrar la aplicación Android en Google Cloud con:

- package: `com.jruiz.nexo`
- SHA-1 de la firma Release permanente de Nexo
- pantalla de consentimiento OAuth
- Gmail API habilitada

La app no incluye secretos OAuth en el repositorio.

## Calendar directo

Se mantiene la implementación de v0.2:

- `READ_CALENDAR`
- línea base inicial
- `CalendarContract.Events`
- vínculo event ID ↔ item Nexo
- cambios posteriores proponen actualizar sin duplicar

## Notificaciones

NotificationListener se conserva solo como vía para fuentes que no ofrecen acceso directo apropiado.

Fuentes visibles por notificación en Ajustes:

- WhatsApp
- Telegram

Gmail y SMS usan preferentemente sus conectores directos.

## Persistencia

DB schema 18 añade:

`external_watch_items(source, external_id, fingerprint, capture_id, last_seen_at)`

Clave primaria:

`(source, external_id)`

## Privacidad

- Clasificación local.
- Gmail usa scope de solo lectura.
- Nexo no envía contenido a un servidor propio.
- Ninguna cita/tarea/recordatorio definitivo se crea sin confirmación.
- La primera activación de cada fuente crea una referencia para evitar importar histórico.

## AndroidIDE

Baseline sin cambios:

- JDK 17
- SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3

Compilar:

```sh
sh build-on-phone.sh
```

APK final:

```text
/storage/emulated/0/Download/Nexo_Input_Hub_v0_3.apk
```
