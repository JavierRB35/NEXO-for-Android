# Changelog de Nexo

## 3.5.0-beta3 · versionCode 58 · Nexo Input Hub v0.3

- Nueva arquitectura de entradas desacoplada de las notificaciones.
- SMS directo: lectura opcional de la bandeja SMS de Android mediante READ_SMS + Calendar-style content watcher.
- La primera activación de SMS crea una línea base silenciosa para no importar mensajes históricos.
- Gmail directo: autorización de cuenta Google + Gmail API de solo lectura.
- Gmail se consulta al abrir Nexo y periódicamente en segundo plano mediante JobScheduler.
- La primera conexión Gmail crea una línea base de correos recientes para no importar el histórico.
- Los nuevos SMS/correos se clasifican localmente y solo los candidatos accionables llegan a Inbox.
- Gmail y SMS directos desactivan su captura por notificación para reducir duplicados.
- Calendar directo se mantiene como fuente del Input Hub.
- WhatsApp y Telegram continúan mediante NotificationListener por no existir acceso general a los datos privados de esas apps.
- Nueva tabla external_watch_items para deduplicar fuentes directas. DB schema 18.
- Ajustes reorganizados en fuentes directas y fuentes por notificación.
- Botón "Buscar entradas ahora".
- APK móvil: /storage/emulated/0/Download/Nexo_Input_Hub_v0_3.apk.

## 3.5.0-beta2 · versionCode 57 · Calendar → Inbox

- Observación del Calendar Provider de Android mediante JobScheduler + TriggerContentUri.
- Permiso READ_CALENDAR solicitado solo al activar la función.
- Primera activación crea una línea base silenciosa para no importar todo el calendario existente.
- Eventos nuevos de Calendar se incorporan a Nexo Inbox con título, descripción, fecha, hora, duración y ubicación.
- Cambios posteriores en un evento ya vinculado se reconocen por su Calendar event ID.
- Si el evento ya fue convertido a una cita Nexo, la revisión propone actualizar ese mismo elemento en vez de duplicarlo.
- Persistencia de fingerprints y vínculos Calendar ↔ Nexo en DB schema 17.
- Notificación Nexo con Revisar / Ignorar cuando se detecta un evento nuevo o modificado.
- El seguimiento funciona sobre el Calendar Provider del dispositivo, independientemente de qué app haya creado el evento.
- No se importan ni eliminan eventos automáticamente sin revisión humana.

## 3.5.0-beta1 · versionCode 56 · Nexo Inbox v0.2

- Captura automática opcional de información útil desde notificaciones.
- Fuentes iniciales: WhatsApp, Gmail, Telegram y SMS.
- Acceso explícito del usuario mediante el permiso especial de acceso a notificaciones de Android.
- Lista blanca configurable por aplicación desde Ajustes.
- Filtro local para conservar candidatos accionables y descartar ruido habitual.
- Exclusión de códigos OTP/verificación detectables para evitar almacenar información sensible de autenticación.
- Persistencia en Inbox con aplicación de origen y remitente.
- Notificación propia de Nexo con acciones Revisar e Ignorar.
- La acción Revisar abre directamente la captura en el formulario existente.
- Ninguna cita, tarea o recordatorio se crea sin confirmación humana.
- Esquema de base de datos 16.
- APK de prueba copiado directamente a /storage/emulated/0/Download/Nexo_Inbox_v0_2.apk.

## 3.4.9 · versionCode 55

- Conciliación automática de citas/tareas con fechas pasadas.
- Reprogramación a hoy/futuro restablece el estado a pendiente.
- Cumpleaños completados según la aparición del año actual y reinicio del ciclo anual.
- Se conserva la lógica de transcripción, reproductor y medicación de 3.4.8.
- Fuente reconstruido de forma controlada a partir de 3.3.2 y del historial validado de 3.4.x.

## 3.4.8 · versionCode 54

- Frases de avisos de medicación editables desde Ajustes.
- Variables `{medicacion}` y `{hora}`.
- Restauración de frases por defecto.

## 3.4.7 · versionCode 53

- Avisos de medicación fijos en -5, 0, +5, +10, +15 y +20 min.
- Fin definitivo de avisos después de +20 min.
- Eliminación de “Posponer 10 min” para no crear avisos fuera de la secuencia.
- Nombre y dosis presentes en los mensajes.
- Humor progresivo en los avisos tardíos.

## 3.4.6 · versionCode 52

- Dependencia explícita `kotlin-stdlib 1.9.24` para el AAR Android de sherpa-onnx.
- Corrige `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics`.
- Primera versión de la serie 3.4.x confirmada funcional para transcripción local en dispositivo.

## 3.4.5 · versionCode 51

- Motor de transcripción aislado en proceso Android `:transcription`.
- Diagnóstico interno persistente por fases.
- Reproductor con posición y velocidades 1×/1,5×/2×.

## 3.4.4 · versionCode 50

- Reproductor mejorado con barra arrastrable y velocidad variable.
- Transcripción desactivada temporalmente durante diagnóstico de fallos nativos.

## 3.4.3 · versionCode 49

- Adaptación a la API Kotlin/JVM incluida realmente en el AAR Android de sherpa-onnx.

## 3.4.2 · versionCode 48

- Sustitución de reflexión por API Java directa de sherpa-onnx; descartada posteriormente por discrepancia con el AAR Android.

## 3.4.1 · versionCode 47

- Estado visual persistente de transcripción: preparando, transcribiendo, terminada o error.
- Acceso directo a ver/guardar TXT.
- Corrección del detalle de errores de transcripción.

## 3.4.0 · versionCode 46

- Transcripción 100 % local con Whisper Base multilingüe INT8 + sherpa-onnx.
- Descarga del modelo fuera del APK.
- Procesamiento por bloques y exportación de transcripciones.

## 3.3.2 · versionCode 45

- Base estable conservada: grabadora local, documentos, copias, agenda, medicación, notas, listas, lugares, calendario y resto de módulos previos.
