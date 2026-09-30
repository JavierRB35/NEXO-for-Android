# Nexo 3.7.0-beta11 · AndroidIDE

Rama de trabajo: `feature/nexo-polish-v0.11`.

Identidad:
- Nexo **3.7.0-beta11**
- `versionCode 76`
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
/storage/emulated/0/Download/Nexo_3_7_0_beta11/
```

Después:

```sh
cd "/storage/emulated/0/Download/Nexo_3_7_0_beta11"
sh build-on-phone.sh
```

No uses `./gradlew` desde Download.

El script conserva la firma Release permanente, usa `JAVA_OPTS=-Xmx768m`, `--no-daemon`, `--max-workers=1` y el AAPT2 de AndroidIDE, verifica la firma y copia:

```text
/storage/emulated/0/Download/Nexo_3_7_0_beta11.apk
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


## Corrección calendario beta8

Android `rawQuery(..., String[] selectionArgs)` enlaza los parámetros como texto. Las comparaciones contra una expresión `CASE` no heredan afinidad numérica de columna, por lo que el calendario no recurrente podía devolver cero filas aunque los eventos existieran.

Beta8 convierte explícitamente los límites temporales con `CAST(? AS INTEGER)` tanto en `getCalendar()` como en las comparaciones temporales de conciliación de estado. La regresión automatizada reproduce 0 eventos con la consulta antigua y 18 con la corregida.


## Inicio con paridad Nexo Familia

- Inicio replica la estructura visual común de Nexo Familia: buscador superior, “Lo importante ahora”, accesos rápidos y “Próximamente”.
- “Lo importante ahora”: Hoy, 7 días, Inbox y Grabar audio.
- Accesos rápidos: Notas, Medicación, Listas y Documentos.
- “Próximamente” usa la próxima aparición vigente, orden ascendente y un máximo de 10 eventos.
- La paleta de categorías, iconos, fecha, categoría, ubicación y participantes sigue los valores canónicos de Nexo Familia.
- La búsqueda de Inicio y la pestaña Buscar comparten el algoritmo de Nexo Familia: normalización sin acentos, stopwords, exigencia de todos los términos, misma ponderación y prioridad temporal. Incluye además Notas, Medicación, Listas, Documentos, Cuaderno/Frases, Grabaciones e Inbox.
- Los eventos históricos quedan fuera por defecto y se pueden incluir explícitamente en la pestaña Buscar.
- Ciclo temporal de eventos alineado con Familia: no recurrente pasado = completado; hoy/futuro = pendiente; recurrente = pendiente; cancelado se conserva. Al reprogramar un evento completado a hoy/futuro vuelve a pendiente.


## Botón flotante de evento

- El botón flotante **+** de añadir evento aparece únicamente en contextos de agenda: **Inicio, Calendario y Agenda / 7 días**.
- En Calendario crea el evento sobre la fecha seleccionada.
- En Inicio y Agenda / 7 días crea el evento sobre la fecha de hoy, que el usuario puede cambiar en el formulario.
- Se mantiene separado del flotante de voz y no aparece en Buscar, Herramientas, Inbox ni módulos que no gestionan eventos.


## Pulido beta11

- Las tarjetas de eventos abren directamente su formulario, sin navegación intermedia.
- Flotantes de **Grabar audio** y **+ evento** restaurados de forma central al navegar/volver a la app.
- Input Hub con modo **Automático** o **Manual**. Manual cancela jobs directos y conserva “Escanear ahora”.
- Ajustes organizados en acordeones al estilo Nexo Familia.
- Cumpleaños usa el mismo tratamiento de icono que Familia: tarta en tarjetas y 🎂 en calendario.
- Inicio renombra “Información familiar” a **Accesos directos**.
- Fichas con acciones compactas de editar, compartir y eliminar.
- La cabecera muestra la versión real instalada.
- Formularios de ubicación incorporan acceso directo a Google Maps.
