# Primera función Edge de Nexo Connect — acceso controlado

El módulo `nexo-connect-gateway` solo admite:

- `POST /functions/v1/nexo-connect-gateway/status` con cuerpo JSON `{}`, JWT Supabase válido; devuelve la identidad del dispositivo registrado, sin claves privadas.
- `POST /functions/v1/nexo-connect-gateway/challenge` con JSON `{"deviceId":"UUID","generation":2,"action":"delivery/send"}`, JWT válido; emite un desafío de 2 minutos ligado al dispositivo principal y a la acción.

El Gateway valida el token con `auth.getUser(token)` **antes** de usar el cliente de servicio. Sólo utiliza el `user.id` devuelto por Supabase, nunca el identificador enviado por el cliente. Las dos funciones RPC de `public` solo pueden ser invocadas por el rol `service_role`; ni `anon` ni `authenticated` tienen permiso para ejecutarlas.

**No habilita ningún envío, registro, sustitución de móvil, aceptación de contactos ni acceso a documentos.** El componente de firma Ed25519 existente sigue sin conectarse a operaciones remotas. No registrar un dispositivo real sin verificación OTP reciente de alta seguridad.

## Estado de pruebas

CI Node verifica autenticación antes de consultas, dispositivo principal, generación, acciones permitidas, rechazo de solicitudes manipuladas y límites de cuerpo. CI PostgreSQL comprueba la imposibilidad de invocar las funciones por roles de cliente.

Antes de habilitar acciones de escritura: convertir la prueba Ed25519 en pasarela transaccional contra PostgreSQL, verificar cambios de clave por QR independiente, rate limit por IP/cuenta, monitorización y pruebas adversariales con cuentas de ensayo.
