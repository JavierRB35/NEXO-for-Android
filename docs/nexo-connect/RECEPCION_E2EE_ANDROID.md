# Nexo Connect — validación E2EE e importación Android (experimento aislado)

`NexoConnectInboxProcessor.receiveAndImport(...)` opera exclusivamente en el módulo de laboratorio `experiments/nexo-connect-android-vault/vault`, que ya depende de **Tink 1.18.0** y se compila con SDK34, Java17. **No está conectado a Nexo 3.7.48.**

## Frontera de confianza

- La cuenta destinataria y generación se reciben de la sesión Android previamente validada; la identidad y clave pública Ed25519 del remitente deben obtenerse del **almacén local de confianza derivado del QR**, nunca del mismo sobre de Supabase ni de una clave que el servidor sustituya silenciosamente.
- Comprueba los UUID, tipo, ID de entrega y generaciones comparándolos con los bytes canónicos NXC1; exige validez temporal firmada y coincidencia entre `expiresAtMs` y `context`.
- Obtiene la huella SHA-256 de la clave Ed25519 del contacto confiable y la de la clave pública HPKE derivada de la clave **privada local del receptor** (sin confiar en campos del servidor) y las compara con NXC1.
- Comprueba el hash del ciphertext y la firma Ed25519 `raw` sobre el dominio Tink `NEXO-CONNECT-CRYPTO-POC-1` con sus longitudes canónicas; solo entonces ejecuta HPKE X25519/HKDF-SHA256/AES-256-GCM con el contexto firmado como AAD.
- La salida descifrada se pasa directamente a `NexoConnectAtomicImporter` + `NexoConnectNativeWriter` para crear una nueva ficha con identificador local y recibo `deliveryId` **en la misma transacción SQLite**. Los buffers del mensaje claro se limpian después.
- Un error de verificación o de inserción **no genera una fila importada ni acuse de recepción**. `accepted` queda reservado a la llamada separada `/delivery/resolve` después del COMMIT, nunca durante la importación.

## Límites conocidos

- Solo procesa objetos ya descargados y no implementa su descarga HTTP, visualización Inbox/Outbox, contacto QR/pinning persistente o sesiones.
- La importación real sigue restringida a seis tipos sin recurrencias ni alarmas; `document` continúa bloqueado hasta recibir y verificar el archivo adjunto.
- Si el teléfono tiene reloj alterado, el servidor ya filtra la caducidad, pero la experiencia completa de conciliación de acuses queda pendiente.
- Estas pruebas son instrumentadas, con claves de laboratorio generadas en Android, y no equivalen a pruebas entre dos teléfonos con identidades reales.
- No activar ni fusionar a `main`, y no desplegar migraciones pendientes mientras falte integración y validación global.

## Vinculación de la clave confiable a identidad y generación

El API exige además `pinnedSenderAccount` y `pinnedSenderGeneration` procedentes de la misma entrada **local y comprobada por QR** que `pinnedSenderPublic`; compara ambos con el sobre firmado. Un antiguo móvil del contacto, aunque conservase su vieja clave Ed25519, no hereda silenciosamente la confianza de una generación más reciente. Prueba negativa instrumentada añadida.
