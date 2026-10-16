# Nexo 3.7.24

Nexo es una aplicación Android **local-first** para organizar agenda, tareas, recordatorios, medicación, notas, documentos, listas, escritos, libros, grabaciones, lugares y capturas de Inbox desde una única interfaz.

Esta versión corresponde a **Nexo 3.7.24**, `versionCode 117`, `applicationId com.jruiz.nexo` y base de datos local `nexo.db` esquema **27**.

**Nuevo en 3.7.24:** biblioteca personal en Herramientas → Ideas y escritos, con «Leídos y empezados» y «Quiero leer», progreso de lectura (0–100 %), fecha de finalización o abandono, y resumen dictado a texto editable. El buscador superior filtra mientras escribes por título o autor, sin distinguir acentos, y consulta ambas listas. Puedes empezar un libro pendiente, editarlo o eliminarlo.

**Nuevo en 3.7.23:** el catálogo de Frases incluye 40 reflexiones originales inspiradas en Radical Face junto a las 40 frases previas. Se siembran en una instalación nueva y se incorporan de forma aditiva al actualizar al esquema 26, sin borrar ni duplicar las personales. «Frases» y «Frase del día» las seleccionan de la misma base de datos local. Cada reflexión identifica canción, álbum y artista y advierte que no es una cita literal.

## Base estable

Nexo 3.7.21 mantiene la base funcional de 3.7.16 y reorganiza el acceso rápido de Inicio: **Hoy, Mañana, 7 días y Agenda**. Inbox deja de ocupar una tarjeta de «Lo importante ahora» y pasa a la navegación inferior, entre **Buscar** y **Herramientas**. La tarjeta Agenda deja de mostrar el texto «Completa».

La versión 3.7.21 conserva las **20 categorías**, incluida Analítica entre Salud / Médico y Farmacia, y sustituye su icono por una jeringuilla diagonal inequívoca con cuerpo, émbolo, aguja y sangre roja. El resto de los iconos, los colores de categoría, el calendario y las tarjetas permanecen igual. La cuadrícula utiliza fondo gris azulado con días más claros. Calendario y Agenda reutilizan las tarjetas pulsables de Inicio: las acciones se muestran solo al abrir el detalle. Los eventos pasados son más discretos y los días con varios eventos muestran un indicador +N sin alterar los datos ni los avisos.

Entre los comportamientos ya integrados:

- Inicio ligero con **Hoy**, **Mañana**, **7 días**, **Agenda**, accesos directos y **Próximamente**; Inbox queda disponible directamente en la barra inferior.
- Splash de arranque con identidad Nexo, animación CSS de tres puntos, mínimo de 3,5 s y salida de seguridad a los 5 s.
- Durante el splash: escaneo resiliente de Inbox y precarga del resumen de Inicio en paralelo.
- Agenda y Calendario con edición de eventos, recurrencias, participantes, categorías, ubicaciones, festivos e indicador de campanilla cuando existen avisos configurados.
- Voz contextual según pantalla y captura universal **Habla con Nexo**.
- Notas con guardado explícito, aviso al salir con cambios y dictado contextual para continuar una nota abierta.
- Listas, Documentos, Cuaderno, Frases, Medicación, Grabaciones, Lugares y Aparcamiento.
- Input Hub para Calendar, SMS y Gmail, además de captura compatible mediante notificaciones.
- Integración con Nexo Familia mediante Supabase Auth y sincronización/importación controlada.
- Grabación local y transcripción 100 % local mediante sherpa-onnx 1.13.7, con perfiles Whisper Base/Small/Medium multilingües INT8 descargados bajo demanda según los recursos del dispositivo.

## Entorno Android validado

- AndroidIDE-Rv2 / ARM64
- OpenJDK 17
- Android SDK 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`

No actualizar estas versiones salvo necesidad justificada.

## Flujo oficial de compilación

El flujo normal es **GitHub Actions**:

- cada push a una rama que contenga el workflow oficial ejecuta validaciones y genera una APK Release firmada como artefacto;
- una ejecución manual desde Actions hace lo mismo;
- un tag `vX.Y.Z` que coincida con `versionName` puede crear la GitHub Release correspondiente;
- la firma Release permanente se mantiene fuera del repositorio mediante GitHub Secrets.

El procedimiento se documenta en [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md).

## AndroidIDE como fallback

Desde la raíz del proyecto en el teléfono:

```sh
cd "/storage/emulated/0/Download/Nexo_3_7_23"
sh build-on-phone.sh
```

No ejecutar `./gradlew` directamente desde `Download`.

El script conserva la firma Release permanente, usa el baseline de AndroidIDE y copia el APK final a:

```text
/storage/emulated/0/Download/Nexo_3_7_23.apk
```

## Validación

Validación base del proyecto:

```sh
sh validation/validate-project.sh
```

El workflow oficial añade además las regresiones de runtime WebView, rendimiento de Inicio, splash, navegación contextual y fichas de Notas antes de compilar la Release.

## Privacidad y seguridad

- WebView local con tráfico cleartext deshabilitado.
- Grabaciones, documentos y transcripciones se gestionan localmente.
- La transcripción se realiza de forma local.
- Las fuentes de Inbox requieren permisos/autorizaciones explícitas.
- La clave privada de firma nunca se almacena en el repositorio.
