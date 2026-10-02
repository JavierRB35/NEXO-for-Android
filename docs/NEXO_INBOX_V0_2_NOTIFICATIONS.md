# Nexo Inbox v0.2 — captura automática desde comunicaciones

## Objetivo

Detectar información potencialmente accionable en notificaciones de aplicaciones de comunicación y convertirla en capturas revisables dentro de Nexo Inbox.

## Fuentes iniciales

- WhatsApp / WhatsApp Business
- Gmail
- Telegram
- SMS (Google Messages, Samsung Messages y AOSP)

## Flujo

1. Android publica una notificación.
2. `NexoNotificationListener` la recibe si el usuario ha concedido acceso.
3. Solo se procesan paquetes incluidos en la lista blanca.
4. Se extraen remitente y texto visible.
5. Se descarta ruido habitual y códigos de autenticación detectables.
6. El clasificador local de Nexo analiza el texto.
7. Si parece accionable, se crea una captura persistente en `inbox_captures`.
8. Nexo muestra una notificación con **Revisar** e **Ignorar**.
9. Revisar abre directamente la captura en el formulario actual.
10. El usuario confirma o corrige antes de crear cualquier elemento definitivo.

## Seguridad

- No se usa Accessibility.
- No se leen bases de datos de otras apps.
- No se accede al historial completo de conversaciones.
- Solo se procesa el contenido que Android expone en la notificación.
- El filtrado y clasificación inicial son locales.
- Ninguna acción definitiva se ejecuta sin confirmación humana.
- Se excluyen patrones de OTP/códigos de verificación cuando son detectables.
- El usuario puede pausar toda la captura o desactivar fuentes individuales.

## Persistencia

DB schema 16 añade a `inbox_captures`:

- `source_package`
- `sender`
- `source_key`

`source_key` se usa para reducir duplicados de actualizaciones repetidas de una misma notificación.

## UX

Ajustes → **Inbox automático**

- estado de acceso a notificaciones;
- botón para abrir el ajuste especial de Android;
- interruptor general;
- WhatsApp;
- Gmail;
- Telegram;
- SMS.

## Limitaciones conocidas

- Si una aplicación oculta el contenido en su notificación, Nexo no puede recuperarlo.
- Adjuntos originales de WhatsApp/Gmail/Telegram no se obtienen mediante NotificationListener.
- Mensajes antiguos no se importan retroactivamente.
- El filtrado heurístico necesitará ajuste con uso real.
- La primera versión prioriza evitar ruido y acciones automáticas.

## Compilación AndroidIDE

Se mantiene el baseline:

- JDK 17
- SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3

Ejecutar:

```sh
sh build-on-phone.sh
```

El APK final debe quedar directamente en:

```text
/storage/emulated/0/Download/Nexo_Inbox_v0_2.apk
```
