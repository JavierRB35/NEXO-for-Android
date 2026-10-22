# Nexo Connect — escritor SQLite nativo (experimental, seis tipos)

Se añadió \`NexoConnectNativeWriter\` en la rama experimental. Es un adaptador para \`NexoConnectAtomicImporter.importVerified(db, deliveryId, senderId, entity, verifiedPlaintext, NexoConnectNativeWriter.INSTANCE)\`.

**No es una funcionalidad activada para usuarios ni prueba criptográfica por sí misma.** El método solo recibe contenidos YA autenticados, descifrados y comprobados frente a la huella de la clave verificada localmente, contexto NXC1 y caducidad. Nunca aceptar aquí JSON procedente directamente del servidor sin esa verificación.

## Entidades admitidas

- \`note\` — fila nueva en \`items\`, con \`type=note\` y \`source=nexo-connect\`.
- \`appointment\` — fila nueva en \`items\`, tipos \`event/task/reminder/expiry\`; pendiente/activo por defecto, sin importar estado de completado del remitente.
- \`list\` — \`lists\` + \`list_entries\` en **la misma transacción y conexión**.
- \`journal\` — \`journal_entries\`, sin sintetizar ni sobrescribir otras entradas.
- \`book\` — \`books\`, conserva fechas históricas, autor, progreso y estado sin inventar la fecha actual.
- \`movie\` — \`media_items\`, conserva película o serie, estado y datos recibidos.

Se generan nuevos IDs locales. Un \`deliveryId\` repetido no duplica la ficha. Dos entregas diferentes del mismo contenido crean copias distintas sin modificar lo que ya existía. Si falla cualquier inserción, se revierte toda la operación, incluido el recibo de deduplicación.

## Bloqueos deliberados

- \`document\`: **se rechaza** hasta que exista transporte cifrado del archivo, verificación de su SHA-256 y escritura segura del adjunto. No se simulan documentos vacíos.
- Citas con recordatorios: **se rechazan** hasta que el importador coordine \`reminders\` y programación real de alarmas Android. No se simula que funcionen alertas que no están programadas.
- Citas recurrentes: se rechazan hasta auditar el motor de recurrencia en el receptor.

Las pruebas instrumentadas \`NexoConnectNativeWriterTest\` cubren las seis tablas, nuevas IDs, duplicados, rollback de listas y rechazo de documentos o alarmas incompletas. Estas pruebas usan un esquema reducido que reproduce las columnas utilizadas; falta una prueba contra una base real de Nexo v32 y la integración de UI, sincronización y end-to-end de dos móviles.

**No hacer merge, deploy o release hasta validar el conjunto completo.**
