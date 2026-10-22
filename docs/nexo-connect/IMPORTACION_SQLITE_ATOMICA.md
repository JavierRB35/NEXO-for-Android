# Nexo Connect — importación SQLite atómica (laboratorio)

La clase `NexoConnectAtomicImporter` está en el módulo Android de Nexo, pero **no está conectada a la UI ni se usa en producción**. Permite aplicar una ficha nueva y registrar `deliveryId` en **una única transacción SQLite**. La primera importación crea una fila nueva con callback `EntityWriter` usando la MISMA conexión. Un reintento con el mismo ID y mismo payload devuelve el recibo anterior sin volver a llamar al escritor; IDs diferentes permiten copias independientes, incluso de contenido idéntico.

## Orden obligatorio

1. Verificar previamente la identidad de remitente fijada localmente (no creer una clave devuelta por servidor sin comparar huellas), firma Ed25519 y contexto canónico NXC1: destinatario, tipo, generaciones, caducidad e integridad, antes de descifrar mediante HPKE.
2. Decodificar campos permitidos con `NexoConnectPayloadCodec.decodeDecrypted`. El escritor adaptador debe mapear la ficha a una **fila nueva** de la tabla Nexo correspondiente; no escribir ficheros ni llamar a MediaStore desde la transacción. No se deben guardar rutas, tokens o claves privadas recibidas.
3. Ejecutar `importVerified()` y únicamente tras su retorno (COMMIT realizado) solicitar `/delivery/resolve` con `target=accepted` y desafío firmado.
4. Tras respuesta confirmada `status=accepted`, invocar `markServerAccepted`. Si falla la red, `pendingAcknowledgements` reintenta el acuse firmado, **sin reimportar**. Si la respuesta es `expired` o `cancelled`, no marcar como aceptado sin política explícita de conciliación.
5. Antes de integrar el módulo con las fichas, disponer de un `EntityWriter` auditado para los siete tipos y de una política de migración de versión de esquema en `DatabaseHelper`; la tabla de recibos actual se crea de forma aditiva y diferida para pruebas.

**Límites actuales:** no prueba por sí misma E2EE ni autentica contenido. No incluye importador concreto de las siete entidades, descargas cifradas, UI, documentos adjuntos, deduplicación semántica o sincronización automática. Tests instrumentados Android en `NexoConnectAtomicImporterTest` ejecutan escenarios de copia independiente, reintento, modificación del payload y rollback. No se debe describir como funcionalidad final del usuario.
