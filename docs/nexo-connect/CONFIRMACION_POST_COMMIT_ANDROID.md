# Coordinador de recepción confirmada (experimental)

`NexoConnectInboxCoordinator` une la descarga autenticada `NexoConnectSignedGatewayClient.open()`, el procesador Tink de identidad y HPKE `NexoConnectInboxProcessor.receiveAndImport()`, el registro SQLite `NexoConnectAtomicImporter` y el acuse firmado `/delivery/resolve`.

El método `importApproved(...,userApproved=true,...)` solo debe utilizarse después de consentimiento expreso del usuario para **esa entrega** y habiendo recuperado su clave privada de Android Keystore y la clave pública del contacto verificada localmente por QR. `false` bloquea toda descarga y escritura. El flujo es secuencial: GET cifrado vía POST firmado -> verificar criptografía -> importar en transacción -> finalizar COMMIT -> invocar `accepted` con nuevo challenge y firma -> marcar recibo `acked=1`.

Si falla la red tras el COMMIT, la ficha ya está guardada y queda un recibo `acked=0`; el coordinador entrega `ack-pending` y permite `retryAcknowledgements`, que **solo reintenta el acuse firmado**, sin descargar, descifrar ni insertar otra vez. Un `expired` o cualquier otro estado distinto de `accepted` permanece pendiente de revisión manual: nunca se marca falsamente como confirmado.

No hay lazo automático ni reintentos rápidos. El método de reintento opera sobre un máximo de 100 recibos y se interrumpe en el primer fallo de red. Las pruebas instrumentadas con dobles controlados comprueban orden COMMIT/ACK, denegación sin consentimiento, rollback/clave inválida, reintento offline sin duplicados y estado terminal conflictivo.

**Aún falta:** integrar la pantalla de consentimiento, persistir el PIN de contactos, configurar cuenta real con dos teléfonos de prueba, servicio de red activo y notificaciones, y resolver estados terminales en UI. No activar en Nexo estable ni desplegar a Supabase todavía.
