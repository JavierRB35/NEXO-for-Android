# Nexo Connect: registro y sustitución de móvil con OTP fresco (experimental)

Endpoint POST /functions/v1/nexo-connect-gateway/device/enroll.
1. Edge valida el JWT con Supabase Auth getUser(token), recupera el UUID y correo
   verificado en Auth (no del JSON enviado). Se exige email_confirmed_at.
2. Android presenta deviceId, signingKey y encryptionKey (base64url público de 32 bytes),
   replace boolean y un código OTP recibido por correo. La solicitud contiene
   x-nexo-issued-at (milisegundos) y x-nexo-enroll-signature (Ed25519 raw).
3. Edge verifica la firma sobre dominio NEXO-CONNECT-ENROLL-V1, método, ruta,
   cuenta JWT, timestamp y SHA-256 de LOS BYTES EXACTOS del JSON de la petición.
   Acepta una diferencia de reloj máxima de 90 segundos. Una firma inválida
   NO consume el OTP. Ningún usuario/email se toma del cuerpo del cliente.
4. Edge crea un cliente de Auth NUEVO, ejecuta verifyOtp({email,token,type:'email'})
   y compara el ID/email devuelto con la cuenta del JWT validado. El OTP se
   CONSUME allí; una reutilización debe fallar. No se devuelve la nueva sesión.
5. Solo tras ello el servidor usa service_role para ejecutar
   public.nexo_connect_enroll_device(...). Su SQL exige reemplazo explícito,
   nuevas claves y nuevo deviceId; incrementa generación, revoca los permisos
   de confianza heredados y consume los challenges pendientes.

En reemplazo no basta poseer un JWT antiguo; se necesita correo + OTP reciente
y posesión de la nueva clave Ed25519. El OTP es el control de replay del
registro (no los challenges antiguos, que están ligados al teléfono previo).
Si falla la red después de verificar OTP y antes de la respuesta SQL, el cliente
NO reintenta automáticamente: solicitará nuevo OTP y conciliará estado.

Se implementaron tests Node con pares Ed25519 reales y simulación estricta
de verifyOtp y RPC. No hubo despliegue. Hay pendientes de producto:
límite de intentos OTP del proveedor Auth, auditoría de logs sin secretos,
pruebas reales de dos móviles, diseño UI de reemplazo y recuperación.
NO utilizar proyecto Supabase de Nexo Familia ni activar en producción.

Compatibilidad: se restaura el módulo antiguo `edge/enrollment.mjs` exclusivamente para sus pruebas históricas. El gateway activo importa `enrollment-proof.mjs` y mantiene el contrato OTP + firma sobre los bytes de la solicitud. Nunca desplegar el gateway histórico por separado.
