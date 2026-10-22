# Nexo Connect — contrato de autenticación y contactos (laboratorio)

**Estado:** SQL de referencia, validado en PostgreSQL aislado. NO hay un proyecto
Supabase Connect operativo ni correo, OTP o API reales desplegados.

## 1. Límites de confianza

El servidor Edge debe verificar un token Auth legítimo con la API de Supabase,
derivar `user_id` de esa verificación, aplicar rate limiting y consultar el
dispositivo autorizado. Los `user_id` y `device_id` del cuerpo de petición son
**no confiables**. Las funciones `api_*` solo están concedidas a `service_role`.
No exponer ninguna de ellas como RPC invocable desde un cliente con la clave
pública de Supabase.

**Peligro importante:** estas funciones reciben `p_user` como argumento y NO
demuestran por sí mismas que el usuario controla esa cuenta. Su autorización
depende del backend autenticado; todavía NO son un sistema de producción.

## 2. Flujo de inicio de sesión sin frase

1. Cliente solicita OTP para correo y verifica el código mediante Supabase Auth.
2. Para registrar el primer dispositivo, el backend valida JWT y ausencia de
   dispositivo anterior; comprueba huellas SHA-256 de las claves públicas.
3. Para reemplazar el teléfono, **exigir OTP reciente** comprobado por servidor
   en el propio flujo de sustitución (un JWT antiguo no es prueba suficiente).
   Se incrementa generación y se revoca la anterior.
4. El nuevo dispositivo publica nuevas claves, pero los contactos existentes
   **no deben aceptarlas automáticamente**: queda pendiente reverificación
   local de huella/QR y se impiden nuevos envíos E2EE hasta entonces.
5. Los envíos anteriores con claves perdidas exigen reenvío.

## 3. Protección de operaciones

- Cada operación no inicial (invitaciones, contactos, envíos) deberá validar
  una firma Ed25519 **del dispositivo principal**, sobre un desafío aleatorio
  de un solo uso y un cuerpo canónico que incluya método, ruta, hash del cuerpo,
  ID usuario, ID dispositivo, generación y fecha de caducidad.
- El servidor debe registrar y consumir atómicamente IDs de desafíos para
  rechazar repetición; **todavía no está implementado**, bloqueante para el API.
- Nunca transmitir claves privadas. Se compara el hash público con SHA-256.
- `api_create_invite`: solo el hash SHA-256 del token; el QR enlaza el secreto
  aleatorio de alta entropía, creado en el cliente y de un solo uso.
- `api_accept_invite`: bloqueo de fila evita que dos destinatarios acepten
  el mismo token. Aprobar una invitación y escanearla es consentimiento mutuo,
  pero NO prueba por sí mismo identidad criptográfica: intercambiar/verificar
  fingerprints sin sustitución silenciosa.
- `api_set_receive_permission`: modifica solo el permiso de RECEPCIÓN del
  usuario que hizo la petición, previa verificación de sesión/firma por Edge.
- `api_unlink_contact`: se debe invocar solo para un actor autenticado.
- La revinculación mediante otra invitación válida restablece permisos mutuos;
  se trata de una acción explícita, nunca por caducidad o reintentos.

## 4. Pruebas de laboratorio

`test-contact-api.sql` simula que el gateway ha comprobado OTP/sesión y
llama usando `service_role`; **NO simula JWT auténtico, correo ni verificación
Ed25519 de la solicitud**. Valida transacciones, permisos a nivel de base de
datos, reemplazo de generaciones, huellas, token usado, recepción direccional,
y aislamiento tras desvincular.

## 5. Antes de implementar el backend real

Crear el proyecto Supabase **independiente** y de pruebas; incorporar validación
de JWT, OTP reciente, desafío firmado, antiabuso, pruebas de suplantación,
controles de coste y revocación de sesiones. Sin esas capas, estas funciones
NO deben estar expuestas a Internet.

Prohibido modificar el proyecto Supabase de Nexo Familia.
