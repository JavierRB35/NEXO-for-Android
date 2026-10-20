# Cliente HTTPS firmado de Nexo Connect (prototipo Android)

Nueva clase `NexoConnectSignedGatewayClient` incorporada en `experiments/nexo-connect-android-vault/vault`. Es un cliente **no activado** en Nexo 3.7.48 que comunica el módulo de Android con el gateway Supabase existente.

- Solo admite una URL `https://<proyecto>.supabase.co` sin puertos, rutas ni redirecciones. Bloquea respuestas excesivas y evita registrar secretos.
- Requiere el JWT de una sesión Supabase Auth validada, el UUID de la cuenta, UUID del teléfono/generación y el keyset de firma Ed25519 del teléfono, protegidos por los componentes locales correspondientes. Nunca usa `service_role`.
- Ejecuta desafío vinculado a acción (`POST /challenge`) y firma **los bytes exactos** del cuerpo JSON sobre el dominio `NEXO-CONNECT-PROOF-V1`. El servidor consume el nonce una sola vez.
- Permite `inbox(limit,afterId)`, `open(deliveryId)`, `resolve(deliveryId,target)` y `send(sealedEnvelope)`. `open` solo devuelve bytes cifrados sin interpretar; la verificación Ed25519/HPKE está separada en `NexoConnectInboxProcessor`.
- No reintenta automáticamente operaciones firmadas con resultado incierto; las idempotencias y la conciliación del acuse deben estar diseñadas en el nivel de orquestación, **no** por reenvío incondicional.
- Las pruebas instrumentadas utilizan transporte falso y verifican las firmas Ed25519 de las peticiones, el orden challenge/operación, tamaño de página, redirecciones/orígenes, rechazo y ausencia de doble POST.
- Falta todavía integración con usuario OTP/enrollment, pinning persistente del contacto por QR, interfaz de Nexo, proveedor HTTP real de prueba con dos cuentas, y acuse después del COMMIT. Los ensayos de transporte falso no prueban Supabase desplegado.
- No modificar `main`, no desplegar Edge ni migraciones, no publicar APK parcial.

## Rechazo local antes de enviar

El método `send` comprueba que el JSON contiene **exactamente los ocho campos de un sobre cifrado**, que los binarios son base64url canónico con tamaño limitado, que la cabecera NXC1 vincula el envío con el UUID del remitente/recipiente, generaciones, tipo, fecha y **huella del firmante local**. Esto bloquea el envío involuntario de una ficha JSON en claro. El backend sigue siendo el responsable de verificar la firma Ed25519 E2EE completa y los permisos contra los contactos comprobados. Pruebas negativas añadidas que demuestran que el rechazo ocurre **antes de una llamada de red**.
