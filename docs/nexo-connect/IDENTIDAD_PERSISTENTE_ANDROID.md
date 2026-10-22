# Identidad del teléfono persistente y estable en Android Keystore

Nuevo NexoConnectLocalPhoneIdentity.openOrCreate(context, verifiedAccount).
Se invoca EXCLUSIVAMENTE tras validar la cuenta del usuario por JWT con
Supabase Auth /user. Crea Ed25519 y HPKE X25519+AES256 con Tink 1.18.0
solo la primera vez, serializa los keysets privados TEMPORALMENTE en
memoria y los guarda en AndroidIdentityVault cifrado AES-GCM con clave
no exportable Android Keystore. Borra los buffers temporales serializados.

En un reinicio de Android o un resultado de red incierto, se restauran
exactamente los mismos keysets desde el almacén cifrado. La UUID del
dispositivo deriva de SHA-256(domain + UUID cuenta + clave publica Ed25519),
con formato de UUID v4 y sin ser prueba de autenticación. La posesión
de la clave privada Ed25519 se verifica además en el servidor.

El nuevo liveBackend(context, origin, publicKey) de NexoConnectOnboardingFlow
ya no necesita conocer de antemano la cuenta o generar claves fuera.
Tras login, restaura/crea la identidad. Envía el segundo OTP de inscripción
con esa misma identidad y consulta el estado con la MISMA clave, incluso
si la primera solicitud quedó sin respuesta. No rota claves silenciosamente
si faltan permisos, se corrompe el almacén o cambia la cuenta.

El método liveBackend con claves explícitas sigue solo para pruebas
aisladas y migraciones. La UI no está conectada al launcher Nexo estable.
La protección Keystore no evita malware con permisos dentro del proceso
ni restauración maliciosa de ciphertext antiguo por root.

Pruebas instrumentadas con Android Keystore para restauración, continuidad
de UUID, aislamiento entre cuentas y rechazo a alteración del ciphertext.
Sin despliegue Supabase ni merge a main.