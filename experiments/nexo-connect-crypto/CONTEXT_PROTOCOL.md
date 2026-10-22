# Sobre canónico experimental para Nexo Connect

`DeliveryContext` define una representación **binaria fija de 142 bytes** para
el contexto público que se autentica mediante HPKE y Ed25519.

Los campos enlazados son:

- Mágica NXC1 y versión 1.
- UUID único de entrega, UUID emisor y destinatario.
- Código del tipo (cita, nota, lista, Cuaderno, libro, película, documento).
- Generación de clave del emisor y receptor.
- Fechas de emisión y caducidad (máximo 30 días).
- SHA-256 de clave pública de firma del emisor y de cifrado del receptor.

Las huellas se computan sobre **bytes serializados públicos de representación
canónica**, que deberán fijarse formalmente con vectores entre Android y Edge.

La verificación de firma y el descifrado deben recibir el contexto esperado,
construido desde datos autenticados **del destinatario y del servidor**, nunca
confiar solo en un contexto remitido por el emisor.

## Límites

Este mecanismo no impide por sí mismo **replay** o aceptar un contexto caducado:
el cliente y el servidor necesitan reloj/TTL confiables, registro de delivery ID
en SQLite, idempotencia y rechazo de generaciones revocadas. Tampoco verifica
quién posee una clave pública, requiere una vinculación previa con QR y pin.
No proporciona ratchet ni PFS integral.

Se trata de pruebas offline Java 17, no de Nexo Connect en producción.
