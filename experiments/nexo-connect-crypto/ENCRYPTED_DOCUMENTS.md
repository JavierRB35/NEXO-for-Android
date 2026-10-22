# Nexo Connect — documentos cifrados en streaming (prueba aislada)

`EncryptedDocumentStream` cifra fragmentos de 64 KiB con AES-256-GCM y
una clave de contenido aleatoria de 256 bits, distinta por cada envío.
El encabezado versionado y el índice de fragmento quedan autenticados
como AAD. Cada fragmento contiene etiqueta de autenticación de 128 bits.

Se comprueba el tamaño total anunciado (máximo inicial 10 MiB), el ID
de entrega, la integridad AES-GCM de cada fragmento, el orden y la
huella SHA-256 **del archivo original** proporcionada dentro del sobre HPKE
firmado del destinatario.

La clave simétrica y la huella **no deben subirse en claro a Supabase**;
el destinatario las recuperará exclusivamente desde su sobre E2EE.

**Advertencia:** la salida descifrada puede contener fragmentos parciales
antes de que se valide la huella global final. Es obligatorio escribir
únicamente en un archivo temporal privado, eliminarlo si ocurre cualquier
error y NO mostrarlo al usuario hasta completar la verificación.

Por ahora no hay conexión de red ni políticas de carga/descarga por
destinatario. Se trata de pruebas Java 17 locales, no de una auditoría
independiente ni de un sistema con confidencialidad futura (ratchet).
