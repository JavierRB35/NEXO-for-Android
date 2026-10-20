# Nexo Connect — prueba de posesión del dispositivo (laboratorio)

Objetivo: ningún token de sesión de correo por sí solo autoriza a otro dispositivo
a enviar datos. Se exige **sesión Supabase verificada + desafío de un solo uso
firmado por clave privada del teléfono principal**.

La API de laboratorio `proof.mjs` calcula un contexto binario/textual canónico
que incluye usuario validado por el servidor, teléfono registrado, generación de
clave, nonce, instante, ruta HTTP, método y SHA-256 del cuerpo.

El servidor debe:

1. Validar JWT usando `supabase.auth.getUser(token)` (no sólo decodificar).
2. Derivar el UUID del usuario del resultado de Auth, no de la petición.
3. Comprobar dispositivo principal actual en base de datos.
4. Emitir nonce vinculado a usuario/dispositivo/generación/acción con TTL 2 min.
5. Verificar firma Ed25519 con clave pública **del dispositivo registrada**.
6. Consumir nonce atómicamente mediante `api_consume_challenge`.
7. Ejecutar operación privilegiada; rechazar cualquier paso fallido.

ATENCIÓN: La comprobación de firma y consumo del nonce son dos operaciones:
un error de red entre ellas puede exigir un nuevo desafío. La firma recibida
no significa que la acción se haya ejecutado; las operaciones deben mantener
idempotencia propia (por ejemplo client_request_id en entregas).

## Recuperación por correo

**Caso separado**: el móvil nuevo no dispone de clave anterior, por tanto
no puede responder al desafío del dispositivo perdido. El reemplazo exige un
flujo OTP *reciente* y firmado por el servidor Auth (con prueba de sesión
recientemente establecida), y después revoca la generación anterior. No
considerar suficiente un token JWT antiguo ni una contraseña del usuario.

## Restricciones

Este código es prototipo verificable con Node WebCrypto y PostgreSQL CI.
**No existe Endpoint Edge desplegado**; faltan Auth real, protección antiabuso,
manejo de sesión actualizada, gestión de secretos y pruebas adversariales
de la implementación final.

Todas las funciones con privilegios `service_role` deben ser invocadas
solo por backend, nunca por un cliente con clave pública.
