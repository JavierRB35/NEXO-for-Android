# Nexo Connect — confianza QR persistente (laboratorio)

**No activado ni integrado con Nexo 3.7.48.** `AndroidPeerTrustStore` almacena los contactos verificados en `Context.getNoBackupFilesDir()` mediante `AtomicFile`, cifrado AES-256-GCM y clave no exportable de Android Keystore **independiente para cada cuenta y aplicación**. Los bytes de ambas claves públicas de cada contacto quedan autenticados junto con la cuenta, generación y estado de revocación.

Formato QR de laboratorio: objeto JSON con cinco campos, `format=nexo-connect-peer`, `version=1`, `peerId`, `generation`, `signingKey` y `encryptionKey` en base64url canónico de 32 bytes. **No contiene claves privadas ni tokens**. Las huellas son SHA-256 sobre los bytes públicos reales, no sobre JSON.

## Confianza y cambios de móvil
- `parseQr` solo analiza datos no confiables. **Escanear no equivale a verificar.** La UI debe comparar las dos huellas SHA-256 completas en un canal independiente (presencial o equivalente) y solicitar autorización expresa. `saveQrConfirmed` exige ambas huellas verificadas y `userApproved=true` como precondición.
- Un contacto ya activo jamás cambia silenciosamente de clave o generación. Un QR distinto se rechaza aunque venga de Supabase. Una aprobación repetida e idéntica es idempotente.
- Para una nueva identidad/generación, primero debe ejecutarse `revoke(peerId)` de forma local y durable (normalmente tras desvinculación firmada en servidor o confirmación expresa); luego, una nueva verificación presencial de QR. El registro revocado se conserva como tombstone hasta reaprobación.
- `findActive` devuelve solo contactos activos, y `PeerPin.verifyTinkPublicKey` vincula el keyset Ed25519 público utilizado por `NexoConnectInboxProcessor` a los 32 bytes previamente guardados; así, una clave que llegase del servidor no se convierte en confiable automáticamente.

## Límites que quedan
- Se permiten hasta 200 contactos; se rechazan bases corruptas, manipuladas, dimensiones excesivas y cambios inesperados.
- La protección AES-GCM no evita **rollback** mediante restauración de una versión anterior del ciphertext por un atacante con control del almacenamiento. Tampoco demuestra por sí sola la presencialidad ni verifica que el QR procede de la persona prevista: eso depende de la interfaz, comprobación humana y canal de confianza.
- No es aún un escáner QR, ni implementa la UI o un vínculo real con `contact/accept`/`key/verify`. Los tests utilizan claves de laboratorio y Android Keystore real del emulador, cuando esté disponible.
- No se ha desplegado nada en Supabase ni se ha fusionado esta rama; antes de distribución exigir pruebas con dos teléfonos reales y revisión de seguridad.
