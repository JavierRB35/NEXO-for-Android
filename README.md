# Nexo 3.7.39

Nexo es una aplicación Android **local-first** para organizar agenda, tareas, recordatorios, medicación, notas, documentos, listas, escritos, libros, grabaciones, lugares y capturas de Inbox desde una única interfaz.

Esta versión corresponde a **Nexo 3.7.39**, `versionCode 132`, `applicationId com.jruiz.nexo` y base de datos local `nexo.db` esquema **32**.

**Nuevo en 3.7.39:** coherencia iconográfica de vínculos en Eventos (cadena azul con contador en la ficha, botón de vincular con la misma cadena y navegación al apartado vinculado). Revisión transversal de acciones CRUD: guardar, editar, compartir y eliminar pasan a barras compactas superiores en Captura, Notas, Frases, Cuaderno, Personas, Documentos, Lugares y Medicación; Libros, Filmoteca y la ficha de Eventos ya conservaban la pauta superior. Los controles propios del contenido siguen en contexto.

**Nuevo en 3.7.38:** botón **Duplicar evento** en «Más» de las fichas de Agenda y en las opciones de los eventos del calendario. Abre una nueva captura prellenada con título, descripción, categoría, fechas, horas, ubicación, participantes, prioridad y avisos (incluida la voz si estaba activa). No guarda automáticamente, no modifica el original, no hereda su identidad ni sincronización; los eventos recurrentes se copian desde la aparición elegida como evento independiente sin repetición por defecto. Aviso visible para revisar fechas y avisos antes de guardar.

**Nuevo en 3.7.39:** categorías independientes para Libros y Películas y series, con propuestas propias de cada módulo, nuevas categorías personalizadas, selección al editar y filtro combinado con búsqueda, estado y tipo. La categoría se muestra en tarjetas, fichas y contenido compartido. Migración aditiva de SQLite 31→32, sin alterar los registros ni sus valoraciones.

**Nuevo en 3.7.35:** Herramientas → Ideas y escritos incorpora **Películas y series**: tipos separados, búsqueda, estados Quiero ver / Viendo / Vista / Abandonada, progreso, fechas, favoritos, valoración de 1 a 5 estrellas, notas con dictado, estadísticas y fichas con editar, compartir y eliminar. Usa tabla local `media_items`, independiente de `books`; migración aditiva a esquema 31.

**Nuevo en 3.7.34:** la ficha de cada libro sitúa los iconos de Editar, Compartir y Eliminar en una barra superior accesible antes del resumen, siguiendo el diseño de Notas. En Nuevo libro / Editar libro, el disquete Guardar y Cancelar se muestran arriba, incluso con resúmenes extensos. Cada tarjeta de libro muestra las cinco estrellas en pequeño, con las seleccionadas resaltadas; el cambio de valoración se mantiene en la ficha completa. Misma base de datos, misma firma Release y configuración AndroidIDE.

**Nuevo en 3.7.33:** Notas se organiza en tarjetas breves con título, fecha y resumen de tres líneas. Tocar cualquier parte de la tarjeta abre una ficha de solo lectura con el contenido íntegro, saltos de línea originales, desplazamiento táctil y ajuste del tamaño de letra. Editar, compartir y eliminar son acciones separadas y el regreso conserva la posición del listado. No cambia el esquema de la base de datos ni el sistema de guardado explícito.

**Nuevo en 3.7.32:** permite relacionar tareas y eventos con múltiples listas, notas y documentos, con creación desde la ficha, navegación bidireccional y un menú compacto de acciones. Migración aditiva a SQLite 30 sin pérdida de datos.

**Nuevo en 3.7.30:** la temperatura se solicita desde el inicio de Android, durante la splashscreen. Si hay una medición anterior válida se muestra indicando su antigüedad, mientras se actualiza en paralelo. El arranque espera el resultado meteorológico dentro de un límite de 8,5 segundos y nunca queda bloqueado por GPS o Internet; al llegar información nueva la cabecera se actualiza al instante.

**Nuevo en 3.7.29:** Lugares dispone de filtros con contadores (Todos, Quiero ir, He estado, Favoritos), búsqueda por nombre/tipo/dirección, fichas compactas, botón Maps con dirección o enlace preciso, corazón y cinco estrellas independientes en la ficha completa, fecha de visita, botón GPS «Aquí» con control de precisión. Al guardar en Libros o Lugares se vuelve al listado conservando filtros y posición. Migración 28→29 sin borrar lugares antiguos.

**Nuevo en 3.7.28:** el formulario Nuevo libro / Editar libro muestra solo los datos de lectura, sin casillas ni estrellas. Al guardar se abre la ficha completa. Allí, un único corazón junto al título se toca para añadir o quitar de favoritos, y las cinco estrellas permiten valorar al momento. Cada toque se guarda automáticamente y favorito y valoración siguen siendo independientes. Los favoritos conservan su corazón en el listado.

**Nuevo en 3.7.27:** Libros ahora ordena por fecha efectiva (finalización, abandono o inicio) con los recientes arriba; muestra fecha en tarjetas compactas, contadores por estado y estadísticas desplegables. El corazón rojo identifica favoritos y funciona de forma **totalmente independiente** de las cinco estrellas de valoración, ambas seleccionables en la ficha. Añade fecha de inicio y prevención de duplicados sin perder la biblioteca existente al migrar la base de datos a esquema 28.

**Nuevo en 3.7.26:** Libros tiene ahora tarjetas compactas con icono de estado a la izquierda y sin botones en el listado. Al tocar una tarjeta se abre su ficha completa, donde puedes editar o eliminar y, para pendientes, empezar la lectura. Debajo del buscador hay filtros de Todos, Quiero leer, En lectura, Terminado y Abandonado. Desde el editor puedes seleccionar cualquier estado, con confirmación si devolver un libro a pendientes borrará datos de lectura.

**Nuevo en 3.7.25:** tiempo en cabecera con GPS reciente prioritario, nombre de la localidad, mejor separación visual y modo manual en Ajustes → Tiempo y ubicación. La búsqueda manual permite elegir entre municipios identificados por provincia y país; no requiere permisos GPS.

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
