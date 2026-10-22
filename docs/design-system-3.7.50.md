# Nexo 3.7.50: sistema visual

La capa compartida `app/src/main/assets/design-system.css` se carga después del estilo histórico. No cambia los datos ni los contratos nativos.

Fondo #F7F9FC; superficie #FFFFFF; texto #142337; secundario #526177; primario #0F766E; selección #E6F5EF; separación #DCE3EC; eliminación #B42335. Espaciado 4/8, radios 12/16, iconos SVG existentes y tipografía del sistema. Foco visible, subrayado del destino activo, selección de fecha con borde y círculo y movimiento reducido.

La barra conserva seis destinos: Inicio, Calendario, Notas, Chat, Inbox y Tools. Tools solo abrevia el botón: la pantalla se llama Herramientas. Chat abre la actividad integrada; Atrás conserva la pantalla previa. Buscar permanece en Inicio y en el botón de cabecera.

Comprobado en WebView Android: cuatro pantallas, anchos 360/412/600 y fuentes 17/22 CSS px (24 combinaciones), sin desbordamiento de página. Los seis botones inferiores tienen áreas de al menos 48 × 48. La cuadrícula del calendario conserva siete columnas y permite desplazamiento propio en anchos que no admiten siete áreas de 48. Con fuente ampliada la barra admite desplazamiento horizontal si resulta necesario.

Verificación funcional: búsqueda global y retorno a Inicio; contador Inbox oculto con 0 y visible con 1/12/237; apertura de Chat y conservación del historial tras instalar encima. Pruebas de notas verifican lectura, edición, eliminación y las dos rutas de compartir.

La validación de documentos con enlaces simbólicos necesita permisos que Windows no proporciona en esta máquina; la suite íntegra permanece obligatoria en el trabajo Linux de publicación. Una suite opcional antigua (`test-ui-runtime.cjs`) conserva selectores de accesos de Inicio retirados antes de este rediseño; no se cuenta como aprobada. Las pruebas obligatorias y la comprobación real de WebView cubren los accesos actuales.


## Chat de Nexo Familia

Referencia: JavierRB35/nexo-familia, commit 1bd84f28ebaeb9d5f11cdf600132a3126d38ced9, MessagesPage.tsx y familia de estilos family-chat de App.css. No se ha usado nexo-family-saas. Se reproducen cabecera con avatar/nombre, burbujas alineadas, separadores de fecha, compositor fijo e inicial coloreada del lector. Los colores de persona son estables por identidad. El resto de Nexo mantiene la nueva paleta.

El acuse es un evento cifrado y autenticado que solo actualiza mensajes de la cuenta y del contacto firmante. Se encola cuando un mensaje recibido está visible con la actividad en primer plano y con foco; sincronizar o permanecer en la lista no marca lectura. La cola persiste y los eventos de control son idempotentes, no aparecen como mensajes ni producen notificaciones. Los estados leídos se incluyen en la copia protegida. La sincronización en primer plano conserva el intervalo de 10 segundos; no es un servicio de push instantáneo.

Validación: 151 pruebas Android por emulador (Pixel_6, LAB_B, LAB_C), 453 aprobadas. Prueba real: Javi envía a Luis; Luis sincroniza desde la lista y sigue Enviado; Luis abre la conversación y aparece L en el mensaje de Javi. Histórico conservado tras actualizar sobre la instalación existente. Contraste calculado de las combinaciones de texto de las cuatro pantallas: mínimo 5,19:1. Esto no sustituye una auditoría completa con lector de pantalla.
