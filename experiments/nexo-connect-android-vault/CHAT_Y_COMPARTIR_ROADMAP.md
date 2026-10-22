# Nexo Connect · Enviar fichas, recibir avisos y conversar

## Objetivo de experiencia
- Pepito elige **Compartir** en una lista, cita, libro, nota u otro tipo compatible.
- Elige **una o varias personas** de su agenda, pulsa **Enviar** una sola vez.
- Cada destinatario recibe **su propia entrega cifrada e independiente**, sin claves privadas ni contenido en el enlace.
- Juanito recibe un aviso: **«Pepito te ha enviado una lista»**.
- Al abrirlo, después de verificar y descifrar, Nexo muestra la ficha y pregunta **«¿Añadir a tu Nexo? Sí / No»**. La decisión de Juanito no afecta a los demás destinatarios.
- En una fase posterior, esa misma conversación admite texto, respuestas, imágenes/adjuntos protegidos y tarjetas de fichas compartidas, con diseño tipo mensajería habitual.

## Qué funciona en la rama experimental
- Las identidades verificadas mediante QR, el envío cifrado a un destinatario, la recepción explícita y la importación de listas a Nexo mediante Binder están implementados y probados previamente en emuladores.
- Selección múltiple de contactos para compartir fichas, envío independiente con IDs de entrega y solicitud distintos por destinatario, nombres locales de contactos, y presentación de los elementos de lista por su orden original: implementación añadida, **pendiente de compilar y validar en ambos emuladores**.
- Si una entrega queda sin confirmar, detener el resto de envíos y mostrar el número confirmado. No reintentar silenciosamente ni anunciar como entregado lo que no fue confirmado.

## Avisos automáticos de laboratorio — implementados, no validados aún
- `NexoConnectInboxNoticeJob`: Android JobScheduler comprueba periódicamente (intervalo solicitado: 15 minutos) **solo metadatos** del buzón, bajo la misma identidad registrada y mediante prueba firmada del dispositivo.
- Notifica únicamente IDs nuevos, sin contenido, títulos, remitentes, claves ni códigos en la notificación; oculta el aviso en la pantalla bloqueada.
- La notificación abre Nexo Connect en su bandeja y, si la sesión aún no está activa, exige «Entrar». Nunca acepta ni incorpora fichas automáticamente.
- Solicita al usuario el permiso de notificaciones en Android 13+. El sistema puede retrasar trabajos en reposo/Doze: **no es push ni es tiempo real**.
- El acceso al almacén de sesiones rota el refresh token protegido bajo bloqueo compartido con el inicio de sesión.
- Queda pendiente **compilación y prueba funcional en los tres emuladores** antes de calificar como operativo.

## Pendiente antes de afirmar que el móvil avisa con la app cerrada
- **Futura fase de baja latencia**: diseñar y desplegar notificaciones push de tipo *wake-up* con infraestructura autorizada. El aviso no debe contener el texto de la ficha, claves ni material sensible; puede indicar que hay un mensaje pendiente y recuperar metadatos tras autenticar.
- Gestionar permisos Android 13+ (`POST_NOTIFICATIONS`), canales de notificación, usuario desconectado, reinicio, retrasos de red, batería, deduplicación del aviso y estados de notificación denegada.
- Un `inbox` consultado al abrir el piloto NO sustituye al push. No mostrar todavía «aviso instantáneo» o «recibido en tiempo real».
- Tras abrir un aviso, validar remitente/dispositivo/expiración, guardar la copia cifrada y presentar una única decisión clara «¿Añadir a tu Nexo? Sí / No»; el rechazo debe dejar intactos los datos originales y no copiar contenido a la base de datos nativa.

## Mensajería privada y grupos (futuras fases)
- Modelo de conversación: `conversationId`, participantes verificados, modalidad individual/grupo, estado de membresía y versión. Un grupo puede tener varios destinatarios y cambios de miembros.
- Modelo de mensaje: `messageId`, `conversationId`, autor, tipo (`text`, `nexo-card`, `attachment`), tiempos, estado por destinatario y recibos. Reutilizar el código E2EE existente, sin reutilizar ciphertext/nonce entre receptores.
- Para una conversación de grupo, especificar y revisar el protocolo de claves, incorporación/expulsión, rotación y verificación de miembros **antes** de lanzar el chat. La selección de varios destinatarios de una ficha NO constituye todavía un grupo de chat.
- Manejar mensajes fuera de orden, duplicados, revocaciones, reintentos ambiguos e idempotencia con identificadores durables. Evitar exponer contenido en notificaciones y copias de seguridad no autorizadas.
- El texto, la transferencia de imágenes/archivos, confirmaciones de lectura y sincronización multi-dispositivo están **pendientes**, no demostrados por el envío actual de fichas.

## Restricciones
- Solo la rama `experiment/nexo-connect-phase0-crypto` hasta auditoría y validación; jamás incorporar claves ni identidad del piloto al proceso de Nexo.
- Mantener los certificados, los registros y los contactos verificados existentes; no forzar creación ni sustitución de cuentas.
- No tocar la rama estable ni activar GitHub Actions para las pruebas locales.
- Cada receptor decide por separado si incorpora a su Nexo; nunca sobrescribir datos existentes ni generar duplicados.
