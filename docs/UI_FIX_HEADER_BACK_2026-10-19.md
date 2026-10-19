# Corrección visual · Nexo Android 3.7.42 · 2026-10-19

## Incidencia observada
Capturas aportadas del Samsung con pantalla «Nueva cita»: cabecera superior demasiado alta respecto a versiones anteriores y acción «Volver» con un chevrón insuficientemente reconocible.

## Causa confirmada en el código
- `index.html` incluía `headerTodayDate` **dentro** de `header.topbar` para **todas las pantallas**.
- `styles.css` (3.7.42) forzaba `.topbar:has(.header-today-date){flex-wrap:wrap}`, provocando una segunda fila global y aumentando su altura.
- `nx-icon-left` consistía en tres puntos de trazo formando un chevrón sin cuerpo de flecha; `captureBackBtn` lo reutilizaba, pero el resultado no transmitía claramente «volver».

## Decisión aplicada en rama de corrección
1. La cabecera global recupera **una sola fila** (marca, versión, tiempo y ajustes), sin tocar sus botones ni su lógica de meteorología.
2. La fecha local se conserva en **Inicio**, inmediatamente debajo de la cabecera, fuera de `topbar`, sin añadir altura a Nueva cita ni a otras pantallas. `refreshHeaderDate()` sigue actualizando el mismo ID y contenido.
3. El símbolo SVG compartido `nx-icon-left` se sustituye por la flecha completa ← (asta y punta). `captureBackBtn` conserva el mismo ID y el listener `navigateBack()`, pero ahora tiene icono y texto claramente legibles y etiqueta accesible.
4. El micrófono que antes se superponía a Descripción se reubica **dentro de la cabecera de Captura rápida**. Se mueve el mismo elemento DOM, manteniendo el ID y el listener; al abandonar Nueva cita, recupera su ubicación original para las demás pantallas.
5. CSS táctil de 44px como mínimo y foco visible. Se actualiza el test de regresión sobre la estructura.
5. No se modifican `nexo.db`, nombres de versión, permisos, rutas ni lógica de guardado.

## Criterios de verificación
- Cabecera de Inicio, Nueva cita, paneles y Agenda de una sola fila a anchuras 320, 360, 390 y 412 px.
- Texto meteorológico largo truncado sin cubrir Nexo ni Ajustes.
- Fecha visible solo en Inicio, legible, sin empujar el formulario Nueva cita.
- Flecha y «Volver» visibles, contraste correcto y `navigateBack()` operativo, incluidas citas con vínculos preparados.
- Micrófono no flotante en Nueva cita, pulsable en Captura rápida, dictado conserva contenido y vuelve a su posición contextual en otras pantallas.
- Compilación Release firmada en GitHub Actions y validación del dispositivo pendientes. **No etiquetar esta corrección como publicada antes de la prueba real**.

## Fuera de alcance
Las demás botoneras CRUD flotantes y las barras inferiores de editores se auditan en una tarea separada. No se cambian aquí para evitar una regresión de captura mientras se corrige el encabezado.
