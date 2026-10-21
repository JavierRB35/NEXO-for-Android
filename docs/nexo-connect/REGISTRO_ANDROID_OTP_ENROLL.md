# Nexo Connect — Android dispositivo + segundo OTP

Se añade NexoConnectDeviceEnrollmentClient al módulo experimental.
Requiere una sesión Auth verificada en /auth/v1/user, cuenta idéntica,
deviceId nuevo y un par de claves nuevas Ed25519 + X25519 HPKE del móvil.
Un SEGUNDO código OTP, diferente del ya consumido para login, se transmite
en el JSON del registro junto a los datos públicos; la firma Ed25519 raw
se calcula sobre dominio/vía/cuenta/milisegundos/hash de los bytes EXACTOS.

No se envía secreto Keystore, ni claves privadas, ni service_role. El servidor
valida el OTP con Supabase Auth de forma independiente, compara user id y
correo con el JWT y consume el código antes de llamar al RPC restringido.
No hay reintentos automáticos, refresh ni guardado de JWT/OTP. Tras una
incertidumbre de red, la UI deberá consultar /status y pedir un nuevo OTP.

Las pruebas instrumentadas Android validan la firma criptográfica real de
los bytes enviados, consentimiento, primera generación, sustitución, OTP
inválido, 403 sin retry y bloqueo de origen HTTP.

Sigue pendiente conectar la pantalla de cuenta y la identidad persistente
de Android Keystore, configurar Supabase de prueba y probar con dos teléfonos.
NO desplegar en Nexo Familia; PR de laboratorio sin merge.