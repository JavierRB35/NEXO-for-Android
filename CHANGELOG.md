## Nexo 3.7.48 — Actualizaciones consultadas y ofrecidas al abrir
- Al terminar la pantalla inicial se consulta el canal de actualizaciones sin el límite diario de las verificaciones periódicas.
- Si se publica una versión superior, aparece un diálogo con dos opciones: Actualizar o Más tarde.
- Elegir Más tarde no desactiva avisos en aperturas posteriores; se evitan consultas y avisos duplicados por cambios breves de actividad.
- Se conservan la actualización manual, la notificación de segundo plano, la validación SHA-256, la firma oficial y la confirmación de instalación Android.
- Sin migración de datos, cambio de permisos ni de herramientas de compilación.

## Nexo 3.7.47 — Datos y seguridad se traslada a Ajustes
- Se retira el grupo «Datos y seguridad» de Herramientas/Aplicaciones.
- Se incorpora «Datos y seguridad» a Ajustes, desplegable desde la rueda dentada superior.
- Historial y Copia de seguridad conservan las mismas pantallas, acciones, almacenamiento y funcionamiento.
- La navegación Atrás devuelve a Ajustes; sin cambios de esquema de base de datos ni permisos.
- Publicación como Release firmada para probar la actualización integrada desde 3.7.46.

## Nexo 3.7.46 — Actualizaciones verificadas desde la aplicación
- Se consulta una versión oficial publicada en un canal de distribución separado del código privado.
- Aviso en primer plano y notificación diaria (sujeta a las restricciones de Android).
- Descarga de APK comprobando SHA-256, tamaño, paquete, versionCode y firma Release anclada.
- Instalación mediante instalador Android con confirmación; sin perder datos por reinstalación.
- Requiere la configuración inicial del repositorio público y credencial de publicación en GitHub Actions.

## Nexo 3.7.45 — Guardar solo como disquete en toda Nexo
- Se elimina el texto visible de todos los botones CRUD de guardado; se preservan el icono de disquete, el aria-label y el title para accesibilidad.
- Libros y Películas/series utilizan el mismo pie de acciones inferior fijo, con Cancelar y Guardar compactos; sin desbordes de texto.
- Los estilos globales evitan botones Guardar sobredimensionados en el resto de editores y mantienen las cuatro columnas del alta de citas.
- Se conserva «Revisar pauta» como acción de revisión (no de guardado), y las exportaciones no se alteran.
- Prueba de idempotencia: no aparecen iconos duplicados ni bucles del observador tras normalizar.
- Mantiene datos SQLite, clave Release y baseline AndroidIDE 34 / JDK17 / Gradle8.9 / AGP8.7.3. versionCode 138.

## Nexo 3.7.44 — Cabecera compacta y acciones despejadas
- Cabecera: fecha abreviada en español (por ejemplo, «mar, 20 oct 2026»), justo bajo Nexo y sin crear una segunda fila del encabezado.
- Nueva cita: Adjuntar, Vincular, Limpiar y Guardar tienen la misma altura. Guardar elimina el margen superior heredado que lo desalineaba.
- El micrófono flotante permanece disponible sobre la barra de acciones, a distancia calculada según el alto real, y nunca tapa Guardar.
- Conserva applicationId com.jruiz.nexo, firma Release, base de datos 32 y entorno JDK17/SDK34/Gradle8.9/AGP8.7.3; versionCode 137.

## Nexo 3.7.43 — Corrección de barras inferiores en alta y edición
- La barra de Nueva cita ya no desaparece porque un campo conserve el foco con el teclado cerrado.
- El teclado se detecta por la reducción real del área visible.
- En el editor normal y recurrente, la barra vive fuera del contenedor desplazable y se mantiene visible en el pie.
- Se elimina la barra persistente al cerrar o sustituir un modal.
- Conserva la firma, los datos y el entorno de compilación; versionCode 136.

## Nexo 3.7.43 — Acciones inferiores persistentes y fecha en cabecera

- Fecha completa en español bajo la cabecera, actualizada al reabrir la app y al cambiar de día.
- Alta de cita: comienza por texto libre; relaciones al final; barra inferior fija con Guardar, Adjuntar, Vincular y Limpiar, alineados con icono y texto.
- Edición de citas y apariciones recurrentes: controles disponibles en el pie de la ficha mientras se desplaza; se reutilizan acciones y manejadores ya existentes.
- Iconos semánticos y texto visibles para las acciones habituales; controles solo icónicos mantienen etiquetas accesibles.
- Se conservan la base de datos, los avisos, la recurrencia y el entorno AndroidIDE original. versionCode 136.

## Nexo 3.7.34 — Acciones superiores de Libros y puntuación visible

- Ficha completa: iconos Editar (lápiz), Compartir y Eliminar (papelera roja) arriba, con la iconografía de Notas. Ya no quedan ocultos al final de resúmenes largos.
- Editor de libros: disquete Guardar y botón Cancelar situados al principio del formulario, accesibles durante el desplazamiento. Una sola acción de guardado y misma protección ante cambios sin guardar.
- Listado: todas las tarjetas muestran cinco estrellas pequeñas, rellenas según la valoración (sin valorar = vacías); la puntuación se cambia únicamente desde la ficha completa, manteniendo la independencia de Favoritos.
- Sin modificación de base de datos ni de los datos personales. Versión 3.7.34 (versionCode 127), AndroidIDE SDK34/JDK17/Gradle8.9/AGP8.7.3.

## Nexo 3.7.33 — Lectura de notas sin entrar en edición

- Tarjetas de notas compactas con título, fecha, extracto de hasta tres líneas y acceso evidente a la lectura.
- Tocar la tarjeta abre una vista completa de solo lectura, desplazable y con saltos de línea conservados. El encabezado y la X permanecen accesibles mientras se desplaza el texto.
- Botones de tamaño de letra A− / A+, edición y compartir desde la ficha. Eliminar permanece bajo una acción roja diferenciada con confirmación y opción Deshacer.
- Las acciones de editar, compartir y borrar de las tarjetas siguen independientes: tocar el contenido no modifica la nota.
- También se abre en modo lectura una nota desde vínculos internos y el visor de elementos. Cerrar devuelve al listado y a su posición previa.
- Nexo 3.7.33, versionCode 126, SQLite 30, firma Release habitual, SDK 34, Gradle 8.9, AGP 8.7.3 y AndroidIDE ARM64.

## Nexo 3.7.32 — Listas de creación inmediata y presentación compacta

- Nueva lista: primero se elige Compra, Pendientes, Viaje, Libre u otros tipos; no se obliga a nombrarla. Se crea con un nombre automático y se abre para añadir el primer elemento.
- Tarjetas de listas más compactas, con icono, pendientes, progreso y un único menú de acciones. Renombrar, compartir, duplicar y eliminar accesibles desde el menú.
- Dentro de una lista: campo Añadir protagonista, progreso visible, renombrado a un toque, y sugerencias de voz plegadas. Se mantiene la navegación bidireccional con tareas y eventos.
- Nexo 3.7.32, versionCode 125, DB 30, misma firma Release y baseline AndroidIDE.

## Nexo 3.7.32 — Fichas compactas y relaciones entre contenidos

- Tareas y citas enlazan varias listas, notas y documentos sin duplicarlos. Selector con búsqueda y creación desde la ficha, con acceso de vuelta desde cada recurso.
- Botonera más pequeña: Editar/Guardar contextual y Compartir/Eliminar en menú Más; editor plegado inicialmente.
- Nueva tabla `record_links` con migración SQLite 29→30, validación de tipos y ausencia de duplicados. Las relaciones de citas recurrentes se asocian a la serie.
- versionCode 125, versionName 3.7.32; firma, SDK 34, Gradle 8.9 y AGP 8.7.3 intactos.

## Nexo 3.7.30 — Meteorología precargada durante la splashscreen

- Comienza a consultar Open-Meteo al arrancar Android, antes de que se termine de crear la WebView.
- Recupera la última medición local válida al entrar en splash (máximo 2 h), señalizando claramente si es anterior a 20 min. No presenta información antigua como temperatura actual.
- Coordina splash, Inbox y meteorología: mínimo 3,5 s y tope de seguridad 8,5 s. Cuando no hay conexión o GPS, Inicio no queda bloqueado; muestra estado explícito y actualiza cuando llegan nuevos datos.
- GPS reciente y suficientemente preciso permite consultar más rápido sin esperar el timeout completo.
- La temperatura se muestra tan pronto como responde Open-Meteo: el nombre exacto de la localidad se obtiene después de forma asíncrona, sin retrasar la temperatura.
- Sin cambios de base de datos (29), SDK 34, AGP 8.7.3, Gradle 8.9 ni clave de firma.
- Nexo 3.7.30 (versionCode 123).

## Nexo 3.7.29 — Lugares y GPS «Aquí»; volver al listado desde Libros

- Lugares tiene tarjetas compactas, búsqueda instantánea, tipos separados de Quiero ir / He estado y filtros rápidos con contadores y favoritos.
- En cada tarjeta, Maps utiliza la ubicación guardada (dirección/enlace/coordenadas) o busca por nombre si no existe.
- Ficha completa con corazón favorito independiente de valoración de 1 a 5 estrellas, fecha opcional de visita, compartir, editar y eliminar.
- Nuevo botón «Aquí» junto a la ubicación del editor: captura coordenadas GPS frescas, indica precisión y pide aceptación explícita si resulta impreciso.
- Guardar Libro o Lugar regresa al listado original conservando filtro, búsqueda y scroll. Editor de Lugares previene descartar cambios por error.
- Base de datos 28→29 migración aditiva y preservación de lugares existentes; etiquetas inequívocas Quiero ir / He estado pasan a estado, otras antiguas quedan sin clasificar hasta revisión.
- Nexo 3.7.29 (versionCode 122); misma firma Release y entorno AndroidIDE.

## Nexo 3.7.28 — Corazón y valoración únicamente en la ficha del libro

- Desaparecen el checkbox de Favoritos y su texto largo del formulario Nuevo libro / Editar libro, así como las estrellas dentro del editor.
- En la ficha completa aparece un único corazón pulsable junto al título: vacío si no es favorito, rojo relleno si lo es. No hay casilla, texto, confirmación ni paso de guardar adicional.
- Cinco estrellas pulsables de 1 a 5 dentro de la ficha completa, independientes del corazón; cada toque se guarda inmediatamente y pulsar de nuevo la puntuación actual permite dejarlo sin valorar.
- Tras guardar un libro nuevo o editar uno existente, se abre automáticamente su ficha completa. La edición conserva el favorito y puntuación anteriores, sin modificarlos accidentalmente.
- versionCode 121, versionName 3.7.28; misma DB 28, applicationId, firma Release y toolchain AndroidIDE.

## Nexo 3.7.27 — Biblioteca: fecha real, favoritos, estrellas y estadísticas

- Libros ordenados por fecha significativa descendente (terminados por finalización, abandonados por abandono, en lectura por inicio). Los libros sin fecha quedan al final y usan la última actualización como desempate. Editar una ficha antigua no cambia su orden cronológico.
- Fecha en las tarjetas compactas y contadores en Todos, Quiero leer, En lectura, Terminado, Abandonado y nuevo filtro Favoritos; los contadores responden también a la búsqueda.
- Corazón como favorito, independiente de la valoración de 0 a 5 estrellas. Cinco estrellas pulsables dentro de la ficha completa; pulsar de nuevo la estrella seleccionada deja Sin valorar. Favoritos y valoración guardados localmente.
- Fecha de inicio editable, con registro automático al comenzar desde Quiero leer. Resumen desplegable de estadísticas (terminados este año, total histórico, lectura, pendientes, abandonados y favoritos) sin aumentar las tarjetas.
- Búsqueda, filtro y scroll conservados al volver de una ficha o editar. Prevención de duplicados por título y autor (insensible a acentos, espacios y mayúsculas). Confirmación antes de reiniciar progreso, fechas y resumen al devolver un libro a pendientes, conservando corazón y estrellas.
- Migración incremental segura de base de datos 27→28: añade start_date, favorite y rating sin borrar libros. versionCode 120, versionName 3.7.27; firma Release, JDK17, SDK34, Gradle8.9 y AGP8.7.3 mantenidos.

## Nexo 3.7.26 — Libros: fichas compactas y filtros por estado

- Tarjetas de biblioteca en formato compacto, sin botones de editar, empezar o eliminar en el listado. Icono de estado a la izquierda y solo título, autor y porcentaje si procede.
- Iconos diferentes y codificación cromática clara: pendiente (marcapáginas ámbar), leyendo (libro abierto azul), terminado (libro con marca verde) y abandonado (libro con aspa roja).
- Selector bajo el buscador con cinco filtros: Todos, Quiero leer, En lectura, Terminado y Abandonado. La búsqueda incremental por título/autor continúa funcionando sin acentos y ahora respeta el filtro activo.
- Al tocar una tarjeta se abre la ficha con todos los datos, resumen completo, fechas y las acciones Editar / Eliminar / Empezar lectura según corresponda.
- El editor ofrece los cuatro estados para libros nuevos y existentes, incluyendo pasar de cualquier estado a Quiero leer. Confirma antes de perder información si ese cambio borrará progreso, fechas o resumen.
- versionCode 119, versionName 3.7.26; esquema de base de datos 27, firma Release y entorno AndroidIDE sin cambios.

## Nexo 3.7.25 — Ubicación del tiempo fiable y manual

- Cabecera: símbolo del tiempo y temperatura algo más grandes, nombre de la localidad debajo, separación mayor respecto al engranaje y áreas táctiles diferenciadas.
- Se corrige la preferencia anterior por NETWORK_PROVIDER y la reutilización de posiciones inexactas; el GPS preciso tiene prioridad, se descartan posiciones antiguas y se señala la ubicación aproximada.
- Geocodificación inversa del municipio mediante Android Geocoder, sin inferir nombres a partir de coordenadas redondeadas en Google.
- Ajustes → Tiempo y ubicación permite elegir «Ubicación actual del teléfono» o buscar y fijar una localidad con Open-Meteo Geocoding; se conserva la elección entre reinicios y se cambia sin desinstalar.
- Pronóstico detallado por nombre de localidad identificado; sin redondeo a dos decimales y sin permisos de ubicación en modo manual.
- versionCode 118, versionName 3.7.25; DB 27 sin migración, SDK 34/Gradle 8.9/AGP 8.7.3 y firma Release sin cambios.

## Nexo 3.7.24 — Biblioteca personal (esquema 27)

- Nueva aplicación «Libros» en Herramientas → Ideas y escritos, con las pestañas «Leídos y empezados» y «Quiero leer».
- Buscador incremental superior por título y autor, con coincidencias parciales, sin distinguir mayúsculas ni acentos; al escribir busca en ambas listas.
- Progreso del 0 al 100 %, estados En lectura, Terminado y Abandonado, fechas editables de finalización o abandono.
- Resumen personal dictado a texto mediante el reconocimiento de Android ya integrado, editable antes de guardar y sin archivos de audio adicionales.
- Botón «Empezar lectura» para mover pendientes conservando título y autor; edición y eliminación con confirmación.
- Tabla SQLite «books» añadida con migración aditiva 26→27. Las copias .nexo existentes incluyen la tabla al respaldar la base de datos.
- versionCode 117, versionName 3.7.24; JDK 17, SDK 34, Gradle 8.9 y firma Release sin modificaciones.

## Nexo 3.7.23 — Frases Radical Face (esquema 26)

- Biblioteca inicial ampliada con 40 reflexiones originales en castellano inspiradas en canciones de Radical Face, con canción, álbum y artista identificados.
- Conservadas las 40 frases previas y todas las entradas personales. Las nuevas frases aparecen en la sección «Frases» y en la selección aleatoria de Nexo.
- Migración 25→26 aditiva, identificadores estables y deduplicación por contenido y fuente; instalación nueva y actualización compatibles.
- Referencias indicadas como paráfrasis originales, no letras literales.
- APK 3.7.23 (versionCode 116), manteniendo la compilación AndroidIDE y la firma Release existente.

## 3.7.22 (2026-10-16)\n\n- Calendario: nuevo desplegable «Frase del día» que selecciona una frase guardada aleatoriamente en cada apertura, sin repetir consecutivamente si hay varias.\n- Cabecera: icono y temperatura aproximada actual del dispositivo obtenidos con Open-Meteo y permiso de ubicación; enlace a la información meteorológica ampliada de Google.\n- Consultas del tiempo fuera de WebView, con caché de 20 minutos, refresco periódico y tratamiento de errores de red/ubicación.\n- Referencias de versión y pruebas automatizadas actualizadas.\n\n# Nexo 3.7.21 · Jeringuilla reconocible para Analítica

- Rediseño exclusivo del icono de **Analítica**: jeringuilla en diagonal con aguja, cuerpo, émbolo, sangre en el cilindro y una gota roja, legible incluso en casillas pequeñas.
- Conserva categoría, color granate `#7F1D1D`, eventos existentes, avisos, Android ID y base de datos local.
- `versionCode 114`, `versionName 3.7.21`; misma firma Release.

---

# Nexo 3.7.20 · Categoría Analítica

- Nueva categoría **Analítica** entre Salud / Médico y Farmacia, con icono vectorial personalizado de jeringuilla gris y gota de sangre roja.
- Color exclusivo granate `#7F1D1D` en tarjetas, calendario, leyenda y selectores, conservando intactas las 19 categorías anteriores.
- Detección de «analítica», «análisis de sangre» y «extracción de sangre» en la clasificación de nuevas capturas, sin migrar ni recategorizar los eventos existentes.
- `versionCode 113`, `versionName 3.7.20`; firma Release y base de datos sin cambios.

---

# Nexo 3.7.19 · Calendario y tarjetas de eventos

- Iconos de las 19 categorías con colores fijos en los días del calendario, su leyenda y las tarjetas de eventos.
- Fondo de calendario gris azulado y casillas de días claras, con estados hoy/seleccionado diferenciados.
- Tarjetas sin botones en Agenda, vistas de 7 días y calendario, reutilizando el formato y la apertura de Inicio.
- Eventos pasados más discretos, tarjetas sin fecha correctas, indicador +N cuando hay demasiados eventos en un día.
- Se conservan acciones completas en el detalle del evento, avisos, recurrencias y gestión de datos.

---

# Changelog de Nexo

## 3.7.17 · versionCode 110 · Inicio con Mañana e Inbox inferior

- «Lo importante ahora» pasa a mostrar exactamente **Hoy, Mañana, 7 días y Agenda**.
- Se añade un contador real de eventos pendientes para mañana, calculado desde el resumen ligero de Inicio y protegido frente a series recurrentes.
- La tarjeta **Mañana** abre una vista de Agenda limitada al día siguiente; los botones de añadir y dictar respetan esa fecha contextual.
- La tarjeta **Agenda** deja de mostrar el texto «Completa».
- **Inbox** desaparece de «Lo importante ahora» y pasa a la barra de navegación inferior, colocado entre **Buscar** y **Herramientas**.
- La navegación inferior pasa de cinco a seis destinos y mantiene el estado activo correcto al entrar en Inbox.
- Se actualizan las regresiones de estructura, navegación contextual y paridad visual de Inicio.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.16 · versionCode 109 · Documentos alineados con Nexo Familia

- Documentos adopta la misma estructura de tarjetas que Nexo Familia: categoría, título, caducidad opcional, tamaño y descripción.
- Cada tarjeta muestra una fila inferior de cuatro acciones equivalentes: **Editar**, **Abrir**, **Descargar** y **Eliminar**.
- Pulsar el cuerpo de la tarjeta abre una ficha de detalle con metadatos del archivo y accesos a abrir y descargar.
- El editor incorpora **Título**, **Categoría**, **Caducidad opcional**, **Descripción opcional** y archivo, conservando los documentos existentes.
- La colección Documentos deja de mezclar adjuntos pertenecientes a otros tipos de registros.
- El tamaño se obtiene del archivo local real sin añadir columnas ni migraciones de base de datos.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.15 · versionCode 108 · Fichas de próximos eventos alineadas con Nexo Familia

- Inicio / **Próximamente** adopta la misma distribución acordada para Nexo Familia.
- La categoría se muestra bajo el contenido principal de la ficha.
- Los participantes pasan a la línea inferior, a la izquierda del bloque de aviso y horario.
- Los eventos con hora final muestran **hora de inicio – hora de fin**; si no existe una hora final explícita, se mantiene solo la hora de inicio.
- Los eventos de día completo no muestran horas artificiales; la campanilla puede permanecer si tienen avisos.
- Se añade la previsualización compacta de la nota/descripción para mantener la misma jerarquía visual.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.14 · versionCode 107 · Respetar eventos eliminados localmente

- Un evento procedente de Nexo Familia que se elimina en Nexo Android queda **suprimido solo en Android** y no vuelve a importarse al pulsar **Sincronizar eventos**.
- La decisión se conserva por UUID en la metadata local de compatibilidad, sin escribir ni borrar nada en Nexo Familia.
- Restaurar el evento desde la papelera de Nexo Android elimina la supresión y permite que vuelva a sincronizarse normalmente.
- Vaciar la papelera conserva la supresión para que el evento tampoco reaparezca después de un borrado definitivo local.
- Se mantiene compatibilidad con eventos eliminados en versiones anteriores: un mapping conocido con item local eliminado se interpreta como supresión intencional.
- Las supresiones locales dejan de contarse como mappings rotos, evitando falsos errores de integridad durante sincronizaciones posteriores.
- Se amplían las regresiones de sincronización incremental y el workflow de publicación las ejecuta antes de generar la Release.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.13 · versionCode 106 · Reparación automática de reservas de coche

- Al arrancar, Nexo repara automáticamente las reservas de vehículo ya importadas que todavía no tenían el subtipo local `vehicle_reservation`.
- La reparación se basa en la metadata original conservada de Nexo Familia (`event_kind`, `vehicle_id` o `reservation_user_id`) y no depende de una nueva sincronización.
- Es idempotente: solo corrige el marcador local y no modifica fechas, títulos, participantes, estados, recurrencias ni avisos.
- La reparación se ejecuta antes de inicializar la WebView, para que el Calendario pueda mostrar 🚗 desde el primer render tras actualizar.
- Se amplía la regresión de reservas de coche para proteger este caso en futuras versiones.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.12 · versionCode 105 · Reservas de coche en Calendario

- Nexo Android deja de excluir las reservas de vehículo de la sincronización con Nexo Familia.
- Se sincronizan `event_kind=event` y `event_kind=vehicle_reservation`, manteniendo el UUID y la metadata original de Nexo Familia.
- Las reservas se marcan localmente con el subtipo `vehicle_reservation` sin modificar el esquema SQLite (`nexo.db v25`).
- En la cuadrícula mensual del Calendario una reserva se representa con **🚗**, igual que en Nexo Familia.
- Si un día tiene más de tres elementos, la reserva de coche tiene prioridad para que el icono siga siendo visible.
- En el detalle diario del Calendario la categoría se identifica como **Reserva coche** con icono de coche.
- La detección de cambios remotos incluye `event_kind`, `vehicle_id`, `reservation_user_id` y `reservation_reason`.
- Se añaden regresiones específicas para proteger la sincronización y la representación visual de las reservas.
- Sin cambio de base de datos. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.11 · versionCode 104 · Acerca de Nexo y arranque del Calendario

- El logo y la versión de la cabecera pasan a ser pulsables y abren una nueva pantalla **Acerca de Nexo**.
- La pantalla muestra identidad, versión instalada, tipo Release, `versionCode`, información de privacidad y desarrollo.
- Los detalles técnicos quedan plegados e incluyen `applicationId`, API mínima/objetivo, esquema de base de datos, arquitectura, dispositivo y API Android instalada.
- Se añade **Copiar diagnóstico**, limitado a información técnica y sin contraseñas, tokens, claves ni contenido personal.
- Corrige el primer arranque con Calendario como pantalla inicial: el estado se reinicia explícitamente a la fecha de hoy antes de navegar.
- El Calendario pinta primero el mes y la cuadrícula base y después solicita eventos y festivos, evitando la pantalla vacía si una consulta nativa falla durante la inicialización.
- La consulta de eventos y la carga de festivos son tolerantes a fallos; además se realiza un segundo render seguro en el siguiente frame cuando Calendario es la pantalla de apertura.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.10 · versionCode 103 · Sincronización incremental con Nexo Familia

- Corrige el caso en que Nexo Android descargaba correctamente Nexo Familia pero ignoraba cualquier cambio en eventos cuyo UUID ya había sido importado.
- La sincronización incorpora ahora eventos nuevos y vuelve a procesar eventos existentes únicamente cuando cambian sus datos fuente o sus reglas de aviso.
- Las actualizaciones usan una mezcla de tres vías: última versión remota conocida, estado local actual y nueva versión remota. Un campo modificado localmente se conserva en lugar de ser sobrescrito.
- Los campos exclusivamente locales de Nexo Android, como favorito, prioridad o aviso por voz, no se pisan durante la sincronización.
- Las reglas de aviso importadas se actualizan cuando cambian en Nexo Familia sin eliminar recordatorios creados manualmente en Nexo Android.
- Las nuevas reservas de vehículo de Nexo Familia quedan fuera de la agenda Android y sus reglas no contaminan el lote de importación.
- Se elimina una segunda escritura redundante del estado al actualizar un evento importado, evitando dos entradas de Historial para una sola sincronización.
- La interfaz pasa de «Importar eventos nuevos» a **«Sincronizar eventos»** y distingue eventos nuevos, actualizados y sin cambios.
- Se conserva la campanilla de avisos de 3.7.9 en la línea inferior de la tarjeta, inmediatamente a la izquierda del reloj.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.9 · versionCode 102 · Campanilla alineada con la hora

- En Inicio, la campanilla de aviso se mueve desde el título a la línea inferior, inmediatamente a la izquierda del icono del reloj.
- En Agenda y Calendario, la campanilla acompaña al bloque horario del evento.
- En tarjetas genéricas de eventos, la campanilla queda integrada en el metadato de fecha/hora.
- Las búsquedas dejan de mostrar la campanilla pegada al título cuando no existe una zona horaria equivalente.
- El icono se simplifica para funcionar como indicador, sin burbuja circular adicional.
- Sin cambios en la lógica de avisos, base de datos v25, sincronización, permisos ni baseline AndroidIDE.

## 3.7.8 · versionCode 101 · Campanilla de avisos en eventos

- Las tarjetas de eventos muestran una **campanilla** cuando el elemento tiene una o más reglas de aviso configuradas.
- El indicador aparece en **Inicio / Próximamente**, **Agenda**, el detalle diario del **Calendario**, tarjetas genéricas de eventos y resultados de búsqueda.
- En series recurrentes, la campanilla representa los avisos configurados para la serie.
- SQLite entrega el indicador junto con el evento mediante una consulta `EXISTS`, evitando consultas adicionales por cada tarjeta.
- La campanilla es informativa, no un botón, e incluye etiqueta accesible **“Aviso configurado”**.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene el baseline AndroidIDE/JDK17/SDK34/Gradle8.9/AGP8.7.3.

## 3.7.7 · versionCode 100 · Integridad al editar series recurrentes

- Corrige un fallo reproducido en cumpleaños anuales importados de Nexo Familia: al editar participantes desde una aparición futura y aplicar el cambio a **Toda la serie**, el `endAt` de esa aparición podía copiarse sobre el evento base sin desplazar su `dueAt`, creando duraciones de meses o un año.
- El backend deja de copiar `endAt` de una aparición de forma incondicional; los límites temporales de la serie solo se recalculan cuando realmente cambia fecha, hora, fin o modo Todo el día.
- La detección de cambio temporal incluye también la hora de fin y el cambio Todo el día.
- Editar participantes ya no provoca una segunda actualización redundante solo para volver a guardar `voiceEnabled` cuando ese valor no ha cambiado, evitando entradas dobles en Historial.
- Se añade una regresión específica para proteger duración e historial de las series recurrentes.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene el baseline AndroidIDE/JDK17/SDK34/Gradle8.9/AGP8.7.3.

## 3.7.6 · versionCode 99 · Reenvíos Gmail y mensajes a uno mismo

- Corrige un caso real en el que Gmail etiqueta simultáneamente un mensaje como `INBOX` y `SENT`, habitual en reenvíos o mensajes enviados a uno mismo.
- La consulta directa deja de usar `-in:sent` de forma absoluta y pasa a conservar mensajes recibidos aunque también tengan etiqueta de enviados.
- Los enviados normales continúan excluidos y los borradores siguen fuera del escaneo.
- Se añade una regresión con un recordatorio reenviado de cumpleaños para asegurar que llegue al filtro contextual y al Inbox.
- Sin cambios en el filtro Gmail v7, base de datos v25, permisos, OAuth ni baseline AndroidIDE.

## 3.7.5 · versionCode 98 · Gmail contextual e Inbox explicable

- Gmail automático deja de aceptar fechas u horas aisladas como motivo suficiente para crear una captura.
- El filtro Gmail v7 exige contexto accionable: cita, tarea, recordatorio, vencimiento, reserva, recogida, pago u otra acción concreta para el usuario.
- `TemporalParser` y el filtro comparten la misma interpretación temporal; versiones como `v3.7.4`, `fix/3.7.4-transcription-settings` o `1.13.7` dejan de confundirse con fechas, mientras `03.07.2027` sigue siendo válida.
- La primera conexión con Gmail crea únicamente el baseline. Los correos anteriores no se reevalúan automáticamente ni al cambiar la revisión del filtro; solo se reintentan errores reales.
- El respaldo mediante NotificationListener usa el mismo filtro contextual que Gmail API para evitar falsos positivos por una ruta alternativa.
- Inbox muestra `candidateReason`, `candidateEvidence` y `filterRevision` para explicar por qué entró una captura.
- Las capturas técnicas acumuladas de GitHub/CI pueden descartarse juntas, con Deshacer, sin borrar los correos originales ni permitir que reaparezcan en el siguiente escaneo.
- Se añaden regresiones ejecutables para CI, versiones técnicas, fechas legítimas, citas, pólizas, reservas, pedidos y facturas informativas.
- Sin cambio de base de datos: `nexo.db v25`. Se mantiene AndroidIDE-Rv2, JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.4 · versionCode 97 · Hotfix selector de transcripción

- Corrige el caso en que **Grabadora / Transcripción** se abría pero las opciones Rápida, Alta precisión y Máxima precisión no se mostraban.
- El estado nativo de modelos ya no puede dejar la interfaz vacía: cada perfil conserva una estructura válida aunque falle una consulta concreta de RAM, almacenamiento o archivos.
- La interfaz incorpora un fallback seguro y visible para seguir mostrando los tres perfiles cuando el estado nativo llegue incompleto.
- En modo degradado, Nexo mantiene únicamente el perfil Rápida disponible por seguridad y deshabilita los perfiles pesados hasta poder comprobar recursos.
- Se añade una regresión de runtime que simula una respuesta nativa incompleta y exige que aparezcan las tres tarjetas.
- Sin cambios en el motor sherpa-onnx, los modelos, las grabaciones, las transcripciones existentes, la base de datos v25 ni el baseline AndroidIDE.

## 3.7.3 · versionCode 96 · Notas y calidad de transcripción

- El micrófono vuelve a estar disponible al editar una nota existente y permite continuar dictando sobre esa misma nota.
- El texto dictado queda en el editor como cambio pendiente; la nota real sigue guardándose únicamente al pulsar el disquete.
- La X, el botón Atrás y el cierre del editor ya no pueden descartar cambios de una nota sin mostrar “Cambios sin guardar”.
- Grabadora / Transcripción incorpora tres perfiles locales: **Rápida** (Whisper Base INT8), **Alta precisión** (Whisper Small INT8) y **Máxima precisión** (Whisper Medium INT8), siempre multilingües.
- Los modelos se descargan bajo demanda fuera del APK, se verifican antes de utilizarse, se conservan localmente y pueden eliminarse desde Ajustes.
- Nexo muestra RAM y almacenamiento disponibles, recomienda un perfil según el dispositivo y bloquea la carga de modelos cuando no existe un margen razonable de memoria.
- Cada transcripción conserva la calidad con la que se inició, muestra nivel, estado, progreso y cancelación, y continúa ejecutándose en el proceso local aislado `:transcription`.
- Se mantienen sin cambios las grabaciones existentes, los TXT ya transcritos, la base de datos `nexo.db v25`, sherpa-onnx 1.13.7 y el baseline AndroidIDE/JDK 17/SDK 34/Gradle 8.9/AGP 8.7.3.

## 3.7.2 · versionCode 95 · Fiabilidad e integridad de datos

- Notas elimina el autoguardado de la nota real: el usuario guarda explícitamente con el botón de disquete.
- Los cambios de una nota se protegen al salir y un borrador temporal local permite recuperar una edición interrumpida sin modificar la nota guardada.
- Cuaderno conserva el editor abierto si falla una escritura al cerrar.
- El esquema local sube a **nexo.db v25** y repara también bases v24 defectuosas que no llegaron a incorporar `journal_entries.revisit`.
- La primera importación desde Nexo Familia pasa a ser transaccional; si falla, la agenda anterior permanece intacta y las alarmas del sistema solo cambian después del commit.
- Se retira de la APK la snapshot familiar histórica y sus rutas de compatibilidad.
- La conversión de una captura de Inbox en un elemento nuevo se realiza de forma atómica.
- Calendar y Gmail vuelven a intentar capturas cuya persistencia falló en lugar de consolidarlas como procesadas.
- La deduplicación de comunicaciones exige compatibilidad de remitente; un texto idéntico de dos personas distintas ya no basta para fusionarlas.
- Las advertencias del parser de citas dejan de silenciarse antes de la revisión.
- La creación fallida de una lista conserva el formulario en pantalla.


## 3.7.1 · versionCode 94 · Hotfix Calendar/Inbox

- Corrige el error `no column named revisit` al guardar elementos en instalaciones actualizadas desde esquemas anteriores.
- `revisit` queda limitado al Cuaderno (`journal_entries`); `items` deja de depender de una columna que no existía en todas las bases v24.
- Calendar busca una coincidencia inequívoca por título, fecha/hora y tipo de día completo antes de crear un elemento nuevo.
- Las capturas de Calendar ya existentes en Inbox también intentan ese enlace al abrirse, evitando duplicados cuando el evento ya está en Nexo.
- El esquema lógico continúa en **nexo.db v24** y no se aplica ninguna migración destructiva.

## 3.7.0 · versionCode 93 · Base estable

- Se consolida como versión estable la línea validada hasta **3.7.0-beta22.4**.
- Inicio usa un resumen ligero para evitar expansiones costosas del calendario y mantiene accesos directos operativos.
- Splash de Nexo con fondo pastel, logo estático y puntos animados; permanece visible un mínimo de 3,5 s y tiene salida de seguridad a los 5 s.
- Durante el splash se ejecuta un escaneo resiliente de Inbox y se precarga en paralelo la información necesaria de Inicio.
- Notas recupera la lectura completa y el desplazamiento vertical dentro de la ficha sin entrar en edición.
- Se mantienen la navegación contextual, voz por pantalla, Agenda/Calendario, Medicación, Listas, Documentos, Cuaderno, Frases, Grabaciones, Lugares, Aparcamiento, Inbox, Historial y Copia de seguridad.
- Se conserva la sincronización/importación de Nexo Familia y el esquema local **nexo.db v24**.
- La APK Release conserva la identidad de firma permanente de Nexo.
- GitHub Actions pasa a ser el flujo oficial de validación y compilación; AndroidIDE se mantiene como fallback.
- Baseline: JDK 17, SDK 34, targetSdk 34, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta21 · versionCode 86 · Formularios compactos y nuevo branding

- Fecha inicio + Fecha fin se muestran en la misma línea en móvil.
- Hora inicio + Hora fin se muestran en la misma línea en móvil.
- La misma composición compacta se aplica al alta, edición y apariciones recurrentes.
- Los selectores de hora usan el control nativo de Android.
- Los eventos nuevos arrancan con **Todo el día desactivado** y horario visible 09:00–10:00.
- Medicación sustituye las horas escritas a mano por selectores horarios nativos.
- Se integra el nuevo branding de Nexo en launcher, cabecera e iconos de notificación.
- La validación de release cubre también formularios compactos y branding.
- Se mantienen JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta18 · versionCode 83 · Sincronización directa con Nexo Familia

- Sustituye el panel antiguo de reparación/JSON por una vinculación directa con **Nexo Familia mediante Supabase Auth**.
- La vinculación usa el correo y contraseña del usuario de Nexo Familia; la contraseña no se guarda.
- Access/refresh token se almacenan cifrados con **Android Keystore + AES-GCM**.
- La primera importación descarga y valida los eventos antes de sustituir únicamente la agenda local; no toca notas, listas, medicación, documentos, lugares, Cuaderno ni grabaciones.
- Cada evento conserva el **UUID original de Supabase** y el estado fuente (`pending`, `completed`, `cancelled`).
- Tras la migración inicial, **solo se añaden UUID nuevos**. Un evento ya existente en Nexo Android no vuelve a modificarse aunque cambie después en Nexo Familia.
- Se importan eventos pasados, presentes y futuros, junto con las reglas de aviso de los eventos nuevos.
- La snapshot empaquetada deja de ejecutarse automáticamente al arrancar y queda solo como compatibilidad histórica.
- Rediseño responsive del panel para evitar desbordes laterales en móvil.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta17 · versionCode 82 · Estado, edición y acceso a Agenda

- Corrige la persistencia del estado manual de citas: **Completada**, **Pospuesta**, **Cancelada** y **Pendiente** ya no son sustituidos por la fecha durante el guardado.
- La reconciliación temporal conserva citas no recurrentes completadas manualmente.
- Los eventos importados de Nexo Familia respetan también un estado manual completado o pospuesto.
- **Guardar** pasa a la zona superior de acciones de la ficha, con distribución 2×2 en móvil y 4 columnas cuando hay espacio.
- Google Maps se integra en el mismo campo **Ubicación**; desaparece el campo visible «Enlace de Google Maps».
- La tarjeta **Calendario** de Inicio abre la Agenda; se elimina el enlace redundante «Ver agenda».
- El mes del botón Calendario usa capital inicial en lugar de mayúsculas completas.
- Sin cambios en el esquema de base de datos ni en el baseline AndroidIDE.

## 3.7.0-beta16 · versionCode 81 · Franjas horarias y voz en tareas

- Corrige «esta tarde a las cinco y media»: ahora se interpreta como **17:30**, no 05:30.
- La franja horaria puede aparecer antes o después de la hora: «mañana por la tarde a las 5:30» y «a las 5:30 de la tarde» producen 17:30.
- «mañana por la mañana» mantiene la hora AM y la fecha del día siguiente.
- «esta tarde», «esta noche» y «esta madrugada» se consideran referencias explícitas al día de hoy.
- El botón flotante **Habla con Nexo** se muestra también en la pantalla de creación de tareas/citas.
- El dictado dentro de una captura existente conserva el contexto de esa captura y añade el texto sin reiniciar el formulario.
- Se añaden casos de regresión específicos para las franjas mañana/tarde/noche.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

# Changelog de Nexo

## 3.7.0-beta15 · versionCode 80 · Voz inteligente y festivos

- El micrófono flotante general deja de iniciar la grabadora y abre **Habla con Nexo**.
- La voz universal vuelve a usar el enrutador local de intención y ya no fuerza todas las frases al flujo de cita.
- Se conservan los dictados especializados de listas, Cuaderno/Frases y Medicación.
- “Lo importante ahora” sustituye **Grabar audio / REC** por **Calendario**; Grabaciones sigue disponible en Herramientas.
- El calendario incorpora festivos de La Pobla de Vallbona con las capas nacional, autonómica, provincial y local.
- Se usa el mismo proveedor de Nexo Familia, caché local y fallback nacional sin conexión.
- Los festivos se muestran con el mismo círculo granate y con nombre, nivel y fuente en el día seleccionado.
- Se corrige el texto de Captura rápida para no afirmar que existe un micrófono flotante dentro del formulario.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

## 3.7.0-beta14 · versionCode 79 · Gmail sencillo

- Gmail pasa a un flujo directo de usuario: **Conectar Gmail → Escanear Gmail → Desconectar**.
- Se elimina el interruptor técnico de “Gmail directo” de la interfaz.
- La pantalla normal muestra solo el estado de conexión, la cuenta conectada y el último escaneo.
- El escaneo de Gmail puede lanzarse de forma independiente, sin escanear SMS ni Calendar.
- El modo **Manual** sigue siendo el predeterminado; Gmail no programa consultas periódicas salvo que el usuario active el escaneo automático.
- Los errores de Google se traducen a mensajes comprensibles. El detalle técnico y el código real quedan ocultos en **Ver diagnóstico**.
- El error 10 / DEVELOPER_ERROR se presenta como “Google todavía no reconoce esta versión de Nexo”, sin exponer OAuth al usuario normal.
- Desconectar Gmail cancela su scheduler, limpia el estado local y cierra la sesión Google de Nexo.
- Conectar Gmail ya no solicita permisos de notificaciones como efecto secundario.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

## 3.7.0-beta13 · versionCode 78 · Reparación global de interacción

- Corrige un fallo de inicialización causado por usar un selector simple (`) donde se requería una lista (`$`). El error detenía el enlace de los controles posteriores.
- Repara los accesos de **Herramientas**, la **X de los formularios**, el **FAB +**, las tarjetas de Inicio y otras acciones que quedaban sin manejador.
- Corrige además los selectores de tarjetas de Inicio y acordeones de Ajustes para evitar el mismo fallo al renderizar.
- Los dos botones flotantes se muestran **en horizontal** para ocupar menos altura en orientación vertical.
- Las fichas de citas y apariciones recurrentes muestran arriba acciones visibles de **Editar, Compartir y Eliminar**.
- Se añade una regresión específica para impedir que vuelva a usarse `$().forEach` y para comprobar los controles críticos.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

## 3.7.0-beta12 · versionCode 77 · Escaneo manual por defecto

- Input Hub arranca en **modo Manual** en instalaciones nuevas.
- Calendar, SMS y Gmail no programan escaneos periódicos hasta que el usuario active expresamente **Escaneo automático**.
- El botón **Escanear ahora** sigue consultando manualmente las fuentes directas activadas.
- WhatsApp y Telegram mediante acceso a notificaciones siguen siendo independientes de este ajuste.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

## 3.7.0-beta11 · versionCode 76 · Navegación, batería y consistencia Nexo Familia

- Las tarjetas de eventos de Inicio, búsqueda, Agenda, Calendario y listados abren directamente el formulario del evento.
- Los flotantes **Grabar audio** y **+ evento** se gestionan desde un único controlador y se restauran al navegar, volver de segundo plano o regresar a Inicio.
- El flotante de micrófono inicia grabación de audio en pantallas generales; conserva dictado contextual en listas, Cuaderno/Frases y Medicación.
- Input Hub incorpora **Escaneo automático / manual**. Manual cancela jobs periódicos y observadores directos y evita escanear al volver a Nexo; “Escanear ahora” sigue disponible.
- La escucha de notificaciones de WhatsApp/Telegram permanece independiente del modo directo automático/manual.
- Ajustes pasa a acordeones por categorías siguiendo el patrón de Nexo Familia.
- Cumpleaños replica el icono de Familia, incluida la tarta 🎂 en el calendario.
- “Información familiar” pasa a llamarse **Accesos directos**.
- Eventos, notas, documentos, lugares, listas, Cuaderno/Frases, medicación y grabaciones muestran acciones compactas de editar, compartir y eliminar.
- La versión en ejecución aparece en la cabecera junto a Nexo.
- Formularios de evento, aparición recurrente, captura y lugares disponen de acceso directo a Google Maps.
- Sin cambios en esquema de base de datos ni baseline AndroidIDE.

## 3.7.0-beta10 · versionCode 75 · FAB contextual para crear eventos

- Añade un único botón flotante **+** de creación de eventos en las pantallas donde la acción corresponde: **Inicio, Calendario y Agenda / Próximos 7 días**.
- En Calendario respeta la fecha seleccionada; en Inicio y Agenda parte de hoy.
- El botón no aparece en Buscar, Herramientas, Inbox ni módulos no relacionados con eventos.
- Mantiene el flotante de voz como acción independiente.
- Sin cambios en base de datos, migraciones, Input Hub ni baseline AndroidIDE.

## 3.7.0-beta9 · versionCode 74 · Inicio y búsqueda con paridad Nexo Familia

- Pantalla Inicio reconstruida con la misma jerarquía visual de Nexo Familia.
- Buscador superior con el algoritmo exacto de relevancia de Familia y resultados en línea.
- La búsqueda global incluye Personas, Agenda, Notas, Medicación, Listas, Documentos, Lugares, Cuaderno/Frases, Grabaciones e Inbox.
- Los eventos históricos se excluyen por defecto; la pestaña Buscar permite incluirlos explícitamente.
- “Lo importante ahora” queda formado por **Hoy, 7 días, Inbox y Grabar audio**.
- Hoy navega al calendario en la fecha actual; 7 días abre la Agenda para hoy + 6 días; Inbox abre la bandeja; Grabar audio inicia la grabación local inmediatamente y abre Grabaciones.
- “Información familiar” ofrece accesos directos a **Notas, Medicación, Listas y Documentos**.
- “Próximamente” muestra hasta 10 próximas apariciones con fecha, ubicación, participantes, categoría e icono; las recurrencias usan su siguiente aparición.
- Paleta e iconos de categorías copiados de los valores canónicos de Nexo Familia.
- Ciclo temporal de eventos alineado con la función `sync_family_event_statuses`: pasado no recurrente = completado, hoy/futuro = pendiente, recurrente = pendiente y cancelado se conserva.
- Reprogramar un evento completado a hoy o futuro lo reactiva automáticamente como pendiente.
- Se mantiene la corrección SQLite beta8 con `CAST(? AS INTEGER)` para eventos no recurrentes.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta8 · versionCode 73 · Corrección SQL del calendario no recurrente

- Causa raíz reproducida sobre el patrón real de Android/SQLite: los límites de `rawQuery` llegan mediante `String[] selectionArgs`.
- Una expresión `CASE WHEN end_at>0 THEN end_at ELSE due_at END` no hereda afinidad numérica, de modo que compararla directamente con `?` podía evaluar contra TEXT y excluir todas las filas no recurrentes.
- `getCalendar()` usa ahora `CAST(? AS INTEGER)` en los límites temporales de la consulta no recurrente.
- La conciliación temporal aplica el mismo CAST en comparaciones `CASE` para evitar marcar futuros como finalizados.
- Prueba de regresión SQLite: consulta antigua = 0; consulta corregida = 18 eventos no recurrentes.
- Caso de control: “Medicina digestiva”, 30/10/2026 09:30, debe aparecer sin volver a importarse.
- Los 15 cumpleaños/aniversarios recurrentes no dependían de esta consulta, lo que explica que sí aparecieran.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta7 · versionCode 72 · Corrección de estados selected en Nexo Familia

- Diagnóstico realizado sobre una copia real `nexo.db` del teléfono: los 33 eventos estaban importados, pero los 16 con visibilidad `selected` habían quedado marcados como `done`.
- La vista Agenda abre por defecto en Pendientes, por lo que esos eventos desaparecían aunque existieran en SQLite.
- La conciliación temporal genérica ya no modifica los eventos cuyo origen es `nexo-familia`.
- Los estados de Nexo Familia se recalculan con el estado fuente de Supabase y `System.currentTimeMillis()`.
- `completed` y `cancelled` se respetan; los demás quedan `pending` mientras su fecha/hora final no haya pasado.
- Los cumpleaños locales mantienen su lógica propia y excluyen Nexo Familia para evitar dobles reglas de estado.
- Al abrir la app, incluso si la migración ya estaba marcada como completa, se concilian los estados antes de cargar la interfaz.
- En la copia real analizada, la regla corregida deja 14 eventos `selected` pendientes y 2 finalizados; “Medicina digestiva” pasa a `pending`.
- La pantalla de Nexo Familia muestra también pendientes/finalizados por tipo de visibilidad.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta6 · versionCode 71 · Reconstrucción limpia de Nexo Familia

- Sustituye la reparación incremental por una reconstrucción limpia de los datos importados desde Nexo Familia.
- Elimina únicamente las copias locales cuyo origen es `nexo-familia`, junto con sus avisos y mapeos; no toca eventos manuales ni otras fuentes.
- Reimporta los 33 eventos de la snapshot completa.
- Verifica por UUID, título y fecha/hora que cada evento existe realmente en la BD Android.
- Verifica por separado **17/17 eventos `family`** y **16/16 eventos `selected`**.
- Verifica las **46/46 reglas de aviso**.
- Si falta un solo evento o regla, la migración no se marca como completada.
- La pantalla de Nexo Familia muestra el desglose family/selected y enumera títulos faltantes.
- Caso de control: “Medicina digestiva” del 30/10/2026 debe quedar materializado como evento `selected`.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta5 · versionCode 70 · Reparación verificable de Nexo Familia

- Fuerza una nueva snapshot de reparación aunque una instalación anterior hubiera quedado marcada como migrada.
- Auditoría local por UUID: compara la BD Android contra los 33 eventos y 46 reglas empaquetados.
- La pantalla Copia de seguridad > Nexo Familia muestra eventos y avisos verificados y enumera títulos faltantes.
- Nuevo botón “Comprobar y reparar” que reimporta idempotentemente la snapshot incluida sin duplicar los eventos existentes.
- La migración ya no confía solo en la marca de SharedPreferences: si el snapshot coincide pero la auditoría local no está completa, vuelve a ejecutar la importación.
- Caso verificado: “Medicina digestiva”, 30/10/2026, 09:30 Europe/Madrid, está presente en Supabase y en la snapshot empaquetada y debe materializarse en Android tras la reparación.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta4 · versionCode 69 · Input Hub fiable y migración Nexo Familia verificada

- SMS y Calendar finalizan correctamente los jobs disparados por cambios de contenido y vuelven a armar sus observadores.
- El filtro de Inbox pasa a priorizar sensibilidad: fechas, horas, recurrencias, ubicaciones, recordatorios y señales accionables llegan a revisión salvo ruido inequívoco u OTP.
- SMS conserva reintentos ante errores y reevalúa siete días recientes al activar o recuperar la fuente.
- Gmail deja de limitarse a la etiqueta Inbox, revisa correo recibido reciente aunque una regla lo archive y mantiene fuera enviados y borradores.
- Gmail recupera texto MIME almacenado mediante attachmentId para no perder invitaciones o detalles que no estén en el snippet.
- SMS y Gmail mantienen simultáneamente la vía directa y el respaldo por notificación, con deduplicación por contenido/remitente/ventana temporal.
- Ajustes muestra vistos, candidatos, descartados y errores por cada fuente directa para diagnosticar falsos negativos.
- Snapshot de Nexo Familia regenerado directamente desde Supabase de producción: 33 eventos, 46 reglas de aviso y 4 miembros.
- La migración valida UUID, referencias, integridad y conteos antes de importar y verifica después 33/33 eventos y 46/46 reglas.
- Cada evento migrado conserva los campos de uso diario en el modelo Android y el JSON original completo de Nexo Familia, incluidas visibilidad, participantes, invitados, origen, UID externo y metadatos sociales.
- Los null legítimos de Supabase se conservan como ausencia de valor; no se convierten en la cadena "null".
- La nueva snapshotVersion fuerza una reimportación idempotente sobre instalaciones que ya ejecutaron una migración anterior.
- Baseline AndroidIDE sin cambios: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.

## 3.7.0-beta3 · versionCode 68 · Inbox fiable y migración Nexo Familia corregida

- Gmail reevalúa los correos recientes ya vistos pero no capturados; la revisión está versionada para no duplicar entradas.
- Gmail analiza ahora asunto, snippet y cuerpo completo del mensaje, mejorando citas, cumpleaños e invitaciones de calendario.
- SMS recupera mensajes accionables recientes incluso si quedaron marcados como referencia inicial o fueron descartados por un filtro anterior.
- Al abrir Inbox se fuerza una consulta inmediata de SMS, Gmail y Calendar, además de los trabajos en segundo plano.
- Inbox muestra si las fuentes directas SMS/Gmail están realmente activas y permite lanzar “Buscar ahora”.
- Se amplían las señales de cita: fechas con mes en texto, más formatos horarios, confirmaciones, invitaciones y recordatorios.
- La instantánea de Nexo Familia se actualiza y fuerza una reimportación idempotente de los 33 eventos y 46 reglas de aviso.
- Los valores JSON null de Supabase dejan de convertirse en texto “null”; los campos opcionales vacíos permanecen vacíos.
- La pantalla de copia de seguridad muestra cuántos eventos de Nexo Familia están vinculados respecto al total esperado.
- La compilación valida que el paquete contiene exactamente la instantánea esperada de migración.

## 3.7.0-beta2 · versionCode 67 · Eventos Nexo Familia y migración automática

- Migración automática inicial de los 33 eventos actuales de Nexo Familia (familia Ruiz-Villena) al almacenamiento local de Nexo Android.
- Importación idempotente por UUID: una segunda ejecución actualiza el registro asociado y no duplica eventos.
- Se migran también las 46 reglas de aviso existentes y se programan localmente cuando siguen pendientes.
- Modelo local de eventos ampliado a esquema 20 con fecha/hora de inicio, fecha/hora de fin, todo el día, zona horaria, enlace de Maps, RRULE original y participantes legibles.
- Los eventos de varios días aparecen en cada día que cubren dentro del calendario.
- Estado temporal alineado con Nexo Familia: los no recurrentes se completan al quedar su final en el pasado; los recurrentes continúan pendientes; los cancelados permanecen cancelados.
- Recurrencias sin fecha final pasan a ser realmente indefinidas, como en Nexo Familia.
- Formularios de creación y edición muestran inicio, fin y Todo el día de forma explícita.
- Se conservan en la capa de compatibilidad el UUID y el JSON original de Supabase, incluidos datos familiares que no tiene sentido mostrar en la interfaz local.
- Se mantienen intactos los datos locales existentes de Nexo Android.


## 3.7.0-beta1 · versionCode 66 · Navegación Nexo Familia y compatibilidad de migración

- Inicio simplificado con Inbox visible y resumen de lo importante.
- Nueva navegación inferior: Inicio, Calendario, Buscar y Herramientas.
- Herramientas agrupadas por función, eliminando accesos duplicados de Inicio.
- Se eliminan de Inicio los accesos redundantes Mi compra, Historial y Agenda/Citas.
- Hablar pasa a ser una acción flotante contextual; Añadir cita es flotante en Calendario.
- Paleta, densidad, jerarquía y categorías alineadas con Nexo Familia.
- Categorías de eventos unificadas con el catálogo canónico de Nexo Familia.
- Nueva capa local de compatibilidad para importar eventos de Nexo Familia sin perder UUID, familia, creador, participantes, visibilidad, metadatos sociales, zona horaria ni datos originales.
- Importación idempotente de eventos y reglas de aviso desde un JSON de migración de Nexo Familia.
- BD local actualizada a esquema 19.
- CI reforzada: valida estructura HTML, navegación, versión, sintaxis JavaScript y compilación Android antes de empaquetar.

## 3.6.0-beta3 · versionCode 65 · Corrección de navegación del rediseño

- Corrige un cierre HTML sobrante introducido en beta2 que desplazaba Inbox/Historial/Mi compra fuera de Inicio y rompía la presentación de Buscar y Calendario.
- Elimina un literal `\\n` visible en la parte superior de la interfaz.
- Restaura Inbox dentro de Inicio sin cambiar su lógica ni sus datos.
- Añade validación automática estricta de estructura HTML y presencia de pantallas/accesos antes de compilar.
- Mantiene el rediseño visual de beta2 y la invalidación de caché de WebView.


## 3.6.0-beta2 · versionCode 64 · Rediseño visual claramente perceptible

- Inicio reconstruido con una jerarquía nueva: título de contexto, bloque de captura rápida y herramientas separadas.
- Accesos principales en rejilla compacta de 3 columnas con iconos grandes y menor sensación de botones/cajas.
- Blanco y neutros claros dominan; verde, amarillo y rojo se usan como acentos de identidad.
- Formularios de Agenda divididos visualmente en bloques funcionales en lugar de una única tarjeta extensa.
- Cabeceras de panel más limpias y editoriales.
- Listas y tarjetas con mayor densidad y menos borde visual.
- Frases/Cuaderno con tratamiento visual más editorial.
- Navegación inferior refinada con indicador activo más claro.
- Se elimina la versión técnica de la cabecera principal; permanece disponible en Ajustes.
- La WebView invalida la caché de assets al cambiar de versión para garantizar que el CSS/JS empaquetado nuevo se muestre tras actualizar.
- Sin cambios en lógica de negocio ni baseline AndroidIDE.


## 3.6.0-beta1 · versionCode 63 · Rediseño visual global

- Nuevo sistema visual común documentado en DESIGN_SYSTEM.md.
- Blanco como superficie dominante; verde como primario; amarillo para atención moderada; rojo solo para error/destrucción.
- Rediseño coherente de cabecera, navegación inferior, módulos de Inicio, tarjetas, filas, botones, formularios, filtros, estados, ajustes, diálogos y feedback.
- Menos sombras y menos sensación de "cajas"; jerarquía basada en tipografía, espaciado y bordes suaves.
- Objetivos táctiles de 48 px o superiores en acciones principales.
- Formularios con foco verde, etiquetas consistentes y superficies neutras.
- Bottom navigation con indicador activo tipo Material 3.
- Inicio más compacto y legible, con acciones principales y módulos claramente diferenciados.
- Frase del día usa acento amarillo discreto; alertas y acciones destructivas usan rojo únicamente cuando corresponde.
- Calendario conserva colores de categoría diferenciados por utilidad funcional.
- Tema Android nativo alineado con la nueva identidad para status bar, navigation bar y colorAccent.
- Sin cambios en lógica de negocio, permisos, servicios, alarmas, notificaciones, almacenamiento ni integraciones.
- Baseline técnico intacto: JDK 17, SDK 34, Build Tools 34.0.4, Gradle 8.9 y AGP 8.7.3.


## 3.5.0-beta7 · versionCode 62 · Agenda, Frases y Cuaderno separados

- El módulo principal antes llamado "Citas" pasa a llamarse "Agenda".
- Se añade un acceso principal "Frases" para citas textuales, autores y referencias.
- "Cuaderno" queda orientado a pensamientos y reflexiones.
- Frases y Cuaderno usan vistas separadas aunque compartan almacenamiento interno.
- Cada sección conserva búsqueda, favoritas y Releer.
- El buscador general denomina "Agenda" a los compromisos y "Cuaderno / Frases" al contenido textual.
- No se migran ni duplican datos existentes.


## 3.5.0-beta6 · versionCode 61 · Citas/frases simplificadas

- Citas del Cuaderno separadas claramente de las citas de agenda.
- Nueva cita: solo texto, título, autor/referencia y favorito.
- Sin fecha, hora, recordatorios, recurrencia ni campos de agenda.
- El dictado dentro de Cuaderno > Citas mantiene siempre el tipo cita/frase.
- La referencia/autor de las citas manuales se muestra también en Inicio.
- Las citas siguen participando en la selección aleatoria de Inicio.
- El dictado general distingue frases/citas de autor de las citas de agenda cuando hay señales claras de contexto.


## 3.5.0-beta5 · versionCode 60 · Input Hub reliability audit

- Corrige Calendar y SMS: los trabajos con `TriggerContentUri` ya no usan `setPersisted(true)`, combinación incompatible en Android.
- Calendar y SMS rearman su observador de contenido después de cada ejecución.
- Calendar conserva escaneo periódico de respaldo cada 15 minutos.
- SMS añade escaneo periódico de respaldo cada 15 minutos.
- Al volver a Nexo, Calendar/SMS/Gmail realizan recuperación o escaneo según corresponda.
- WhatsApp y Telegram siguen dependiendo de NotificationListener, pero ahora solicitan rebind si Android desconecta el listener y reprocesan notificaciones activas al reconectar.
- Si se desactiva la fuente directa de Gmail o SMS, se reactiva su fallback por notificación.


## 3.5.0-beta4 · versionCode 59 · Calendar Inbox reliability

- Corrección de detección de cambios de Calendar en Inbox.
- Al volver a Nexo se ejecuta un escaneo de recuperación antes de rearmar el observador.
- Se mantiene la detección rápida mediante TriggerContentUri sobre Calendar Provider.
- Se añade un escaneo periódico de respaldo cada 15 minutos para evitar cambios perdidos por el proveedor/OEM.
- El botón "Buscar entradas ahora" sigue forzando un escaneo inmediato.


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
