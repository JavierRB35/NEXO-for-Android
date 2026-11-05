# Nexo 3.7.85

Nexo es una aplicación Android **local-first** para organizar agenda, tareas, recordatorios, medicación, notas, documentos, listas, escritos, libros, grabaciones, lugares y capturas de Inbox desde una única interfaz.

Esta versión corresponde a **Nexo 3.7.85**, `versionCode 178`, `applicationId com.jruiz.nexo` y base de datos local `nexo.db` esquema **34**.

**Nuevo en 3.7.85:** El calendario mensual recupera la cuadrícula visible: cada día es una celda delimitada sobre un plato suave, de modo que los iconos de categorías se leen siempre dentro de su fecha. La rejilla se adapta automáticamente a los tres estilos (Vivo, Mediterráneo y Noche), con el día de hoy y el seleccionado destacados. Solo presentación: datos, avisos, gestos y copias de seguridad no cambian. Actualización opcional.

**Nuevo en 3.7.84:** Rediseño visual premium integral con un sistema de tokens por tema. Los tres estilos (Vivo, Mediterráneo y Noche) se refuerzan con tipografía y espaciado más claros, botones y campos unificados, calendario sin rejilla de bordes, barra inferior con indicador Material 3 y etiquetas en español, tarjetas de Inicio con jerarquía numérica y acento de categoría en las citas, y barra de estado del sistema alineada con el tema activo (incluida la barra de estado y navegación en Noche). Solo presentación: datos, avisos, gestos, copias de seguridad y navegación no cambian. Actualización opcional.

**Nuevo en 3.7.83:** El reproductor de grabaciones incorpora Play, Pausa y Detener, con reanudación desde la misma posición y control de velocidad durante la pausa. Los audios y transcripciones existentes no cambian. Actualización opcional.

**Nuevo en 3.7.82:** Calendario sin recorte lateral en móviles y motor local de títulos V8.1 en nuevas capturas de citas. Sin migraciones ni cambios en citas guardadas.

**Nuevo en 3.7.80:** Diferenciación de citas de salud propias y acompañamientos familiares con dos iconos y colores únicos en agenda, calendario y formularios. Se conservan los eventos históricos y el esquema de datos. Actualización opcional.

**Nuevo en 3.7.76:** Línea horizontal que marca la hora actual en la agenda diaria de hoy. Al abrir se desplaza hasta dejar unos 45 minutos anteriores visibles; se actualiza sin interrumpir el scroll manual. No afecta a otras fechas ni a los gestos de citas.

**Nuevo en 3.7.75:** Más personalidad cromática en los tres temas de Ajustes → Apariencia: cinco fondos suaves para accesos de Inicio y Herramientas, una fina línea multicolor y contraste corregido en la fecha de hoy, el buscador y textos de agenda del tema Noche. Solo presentación, sin cambios funcionales.

**Nuevo en 3.7.74:** Pulido exclusivamente visual de los tres temas: mejor contraste de acentos en Vivo y Mediterráneo, fondos oscuros coherentes en Noche y banda horaria correctamente alineada en móviles estrechos. Sin cambios funcionales; actualización opcional.

**Nuevo en 3.7.73:** Ajustes → Apariencia permite seleccionar tres temas visuales (Vivo, Mediterráneo o Noche). Solo cambia la presentación, con preferencia guardada localmente y animaciones respetuosas con los ajustes de accesibilidad. La lógica de citas, avisos, datos, navegación y gestos no cambia.

**Nuevo en 3.7.72:** Mantener pulsado un hueco vacío de la agenda horaria abre una nueva cita con el día y el intervalo horario de una hora ya rellenados. Se conservan los gestos de arrastrar y redimensionar citas existentes, sin guardar nada automáticamente. Actualización opcional, sin migraciones de datos.

**Nuevo en 3.7.71:** Al entrar en Calendario se muestra el mes actual y el día de hoy; el horario diario se desplaza de forma independiente, dejando fijos los eventos de todo el día. Al volver de una cita se conserva el desplazamiento anterior. Actualización opcional con la firma Release habitual y sin migraciones de datos.

**Nuevo en 3.7.70:** Compartir Nexo desde Ajustes → General permite enviar el enlace permanente a la última versión usando el menú de Android, sin compartir datos personales ni vincular dispositivos.

**Nuevo en 3.7.69:** El micrófono del alta de citas aparece junto a Captura rápida, siempre accesible al abrirse el teclado. En Notas, Cuaderno, Frases, Medicación, Lugares y Documentos se sitúa en el encabezado del editor. Conserva el texto anterior y permite ampliarlo por voz. Actualización opcional sin cambios de firma ni datos.

**Nuevo en 3.7.60:** Al pulsar un evento desde Inicio → HOY o Calendario se abre la ficha ya existente, con Editar plegado. Corrección de citas que terminan a medianoche y de la representación duplicada de la misma actividad; guardado y recurrencias conservan sus flujos habituales.

**Nuevo en 3.7.59:** Agenda completa de 00:00 a 24:00, sin limitar las horas extremas: se sitúa inicialmente cerca de los eventos para facilitar la navegación. Tarjetas compactas que priorizan hora, icono y título; al pulsar se abre la ficha completa. Pulsación larga en el centro para mover una cita o sobre sus bordes para ajustar por separado inicio o final en pasos de 15 minutos, con vista previa, guardado asíncrono y Deshacer. Las series recurrentes afectan solo a la aparición editada.

**Nuevo en 3.7.58:** Agenda diaria cronológica por horas en Inicio → HOY y Calendario → día seleccionado, con bloques de duración proporcional y columnas para eventos simultáneos. Pulsación larga y arrastre con ajuste de 15 minutos, respuesta háptica, actualización asíncrona y Deshacer. Las citas recurrentes cambian solo en la aparición seleccionada; no se modifican los permisos, esquema de datos ni la firma Release.

**Nuevo en 3.7.57:** Deslizamiento lateral y pulsación larga para eliminar conversaciones locales con confirmación; selección múltiple de mensajes, eliminación para mí y cancelación con la flecha atrás. Historial descartado oculto tras sincronizar o restaurar, manteniendo cuentas, claves, contactos y recibos de entrega. La operación no elimina mensajes del otro dispositivo.

**Nuevo en 3.7.56:** Nexo Connect incluye Mi perfil con fotografía local reducida, intercambio de avatar por el canal cifrado con contactos verificados, alias privados, dos accesos de vinculación desde Ajustes, y entrada directa a Chat sin pantalla intermedia fugaz. Se conservan claves, E2EE y esquema SQLite.

**Nuevo en 3.7.55:** Añadir personas a Connect con un único código de seguridad de 16 caracteres comprobado por teléfono o en persona, botones y pantallas de Nexo, recuperación segura de contactos ya vinculados y confirmación bilateral más clara. Se mantiene el anclaje a ambas claves completas, sin modificar el esquema SQLite ni la firma oficial.

**Nuevo en 3.7.54:** Connect y Chat permiten retomar una inscripción de teléfono pendiente con la sesión protegida, sin reintentar códigos automáticamente ni borrar las claves. Se añade acceso directo para volver a verificar el correo y se informa cuando el servicio limita los envíos. La cuenta y los datos locales permanecen conservados; Android exige la misma firma Release al actualizar.

**Nuevo en 3.7.53:** Las fichas de citas y eventos con ubicación incluyen un acceso destacado a Google Maps y un botón Maps en la barra inferior fija, sin tener que abrir Editar. Se corrige la apertura de direcciones, nombres de lugares y enlaces de Maps en formularios y fichas, con alternativa de navegador cuando no esté disponible la aplicación. No cambia la estructura de datos ni la firma de instalación.

**Nuevo en 3.7.52:** Inicio más despejado: desaparece el bloque «Accesos directos» y «Próximamente» aparece bajo «Lo importante ahora». Notas, Medicación, Listas y Documentos siguen accesibles en Herramientas; las notas también desde la barra inferior. Los mensajes de actualización son breves y explican las novedades sin tecnicismos. Esta versión es opcional y conserva la misma firma Release y los datos locales.

**Nuevo en 3.7.51:** Actualizaciones identificadas como **opcionales** o **críticas** en el canal oficial. Las opcionales se pueden posponer; las críticas incluyen el motivo y requieren actualizar antes de continuar usando Nexo cuando el cliente ya admite el protocolo. La instalación conserva la confirmación de Android, SHA-256 y la firma oficial. Esta primera entrega se clasifica como opcional.

**Nuevo en 3.7.50:** Connect y Chat dentro de Nexo, historial de chat incluido en copias protegidas, nombre elegido para las personas y un diseño visual común. La barra inferior incluye Chat y Tools; la búsqueda permanece en Inicio y cabecera. Inbox muestra sus elementos pendientes.

**Nuevo en 3.7.49:** Libros y películas/series incorporan «Argumento / sinopsis» como texto libre opcional desde el alta, independiente de los resúmenes y opiniones. Puede leerse, editarse y compartirse antes y después de leer/ver, y persiste al cambiar de estado. SQLite 33 amplía ambas tablas sin borrar registros. La actualización oficial se distribuye mediante el canal público de Nexo.

**Nuevo en 3.7.48:** Al abrir Nexo y terminar la pantalla inicial se buscan actualizaciones nuevas y, si las hay, aparece una pregunta «Actualizar / Más tarde». La apertura posterior puede volver a mostrarla; sin instalación silenciosa ni diálogos duplicados al cambiar de actividad.

**Nuevo en 3.7.47:** El apartado «Datos y seguridad» pasa de Herramientas a Ajustes (rueda dentada) e incluye Historial y Copia de seguridad, sin duplicar accesos ni cambiar su funcionamiento.

**Nuevo en 3.7.46:** Nexo incorpora búsqueda de nuevas versiones desde Ajustes/Acerca de, avisos diarios, descarga con SHA-256 y verificación de la firma oficial antes de abrir el instalador del sistema. El repositorio privado se mantiene aislado del canal público de APK.

**Nuevo en 3.7.45:** Guardar pasa a ser exclusivamente un icono de disquete en todos los editores, con etiqueta accesible; Libros y Películas comparten pie de acciones fijo y uniforme, sin textos cortados ni botones desproporcionados. Se mantienen los handlers originales, los datos y la firma Release.

**En 3.7.44:** botones inferiores uniformes, micrófono situado por encima de Guardar y fecha española compacta bajo la marca. 

**En 3.7.43:** corrección de la visibilidad persistente de las barras de acciones en alta y edición, incluso tras enfocar un campo. En 3.7.43: fecha local completa bajo la marca Nexo, captura libre prioritaria, vínculos al final y acciones fijas en el pie del alta y del editor de eventos normales/recurrentes, con iconos y etiquetas coherentes. Sin cambios en la base de datos ni en la lógica del guardado.

**Nuevo en 3.7.41:** vinculación y creación de listas, notas y documentos desde **Nueva cita** o **Editar cita**, también en apariciones recurrentes. Los elementos se crean desde el contexto del evento con «Guardar y volver» y una banda de origen. Las nuevas citas mantienen los vínculos como borradores hasta guardarse, evitan recursos huérfanos al cancelar y pueden reintentar una vinculación sin duplicar el registro ni el recurso. Se conservan los formularios anteriores, las fechas, los avisos, las personas, la repetición, la ubicación y los permisos. Disquete uniforme en acciones Guardar, sin cambiar la lógica de los botones.

**Nuevo en 3.7.40:** la cadena azul con contador aparece ahora en las tarjetas de Próximamente, Agenda y Calendario cuando el evento tiene vínculos; funciona también para apariciones recurrentes sin botones anidados. En la ficha desplegada, el número de vínculos se muestra **dentro del botón «Vincular»**, sin repetirlo en otra insignia. Los contadores se obtienen en la consulta SQLite original (sin consultas individuales por tarjeta), distinguen vínculos válidos y se actualizan al vincular/desvincular.

**Nuevo en 3.7.39:** coherencia iconográfica de vínculos en Eventos (cadena azul con contador en la ficha, botón de vincular con la misma cadena y navegación al apartado vinculado). Revisión transversal de acciones CRUD: guardar, editar, compartir y eliminar pasan a barras compactas superiores en Captura, Notas, Frases, Cuaderno, Personas, Documentos, Lugares y Medicación; Libros, Filmoteca y la ficha de Eventos ya conservaban la pauta superior. Los controles propios del contenido siguen en contexto.

**Nuevo en 3.7.38:** botón **Duplicar evento** en «Más» de las fichas de Agenda y en las opciones de los eventos del calendario. Abre una nueva captura prellenada con título, descripción, categoría, fechas, horas, ubicación, participantes, prioridad y avisos (incluida la voz si estaba activa). No guarda automáticamente, no modifica el original, no hereda su identidad ni sincronización; los eventos recurrentes se copian desde la aparición elegida como evento independiente sin repetición por defecto. Aviso visible para revisar fechas y avisos antes de guardar.

**Nuevo en 3.7.36:** categorías independientes para Libros y Películas y series, con propuestas propias de cada módulo, nuevas categorías personalizadas, selección al editar y filtro combinado con búsqueda, estado y tipo. La categoría se muestra en tarjetas, fichas y contenido compartido. Migración aditiva de SQLite 31→32, sin alterar los registros ni sus valoraciones.

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
