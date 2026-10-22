# Primera operación firmada: invitación segura

**Estado:** avance limitado, no sistema de intercambio completo.

Endpoints detrás de `verify_jwt=true` y comprobación `Auth.getUser(token)`:

- `POST .../nexo-connect-gateway/status` devuelve solo dispositivo/generación.
- `POST .../nexo-connect-gateway/challenge` concede desafío de 120 segundos vinculado a la acción.
- `POST .../nexo-connect-gateway/contact/invite` crea invitación privada **solo si**:
  - JWT corresponde a usuario real;
  - teléfono principal sigue autorizado y registrado;
  - clave pública Ed25519 registrada tiene 32 bytes;
  - firma Ed25519 verifica cuerpo exacto, usuario, dispositivo, generación,
    método, ruta, nonce e instante de solicitud;
  - el desafío es consumido de forma atómica y solo una vez;
  - token aleatorio de QR es secreto del móvil, servidor almacena solo SHA256;
  - caducidad entre 300 y 86400 segundos; máximo cinco invitaciones abiertas.

El cliente debe generar **token aleatorio de al menos 256 bits** en el teléfono
y enviar solo SHA-256 para no revelar QR al servidor. El usuario debe verificar
independientemente identidad/huellas de contacto.

El servidor NO permite aceptar invitaciones, registrar dispositivos, reemplazar
identidad, enviar documentos ni compartir datos todavía. No usar con usuarios
reales antes de pruebas integrales y manejo de OTP reciente.

No se filtra ninguna clave `service_role` a la app; funciones SQL del puente
solo `service_role` y no `authenticated` ni `anon`.
