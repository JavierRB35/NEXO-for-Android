# Nexo Connect — UI y orquestación de dos códigos OTP (fase 3, experimental)

Este avance incorpora una máquina de estados secuencial `NexoConnectOnboardingFlow` y una vista nativa `NexoConnectOnboardingView` aisladas en el módulo Android de laboratorio.

Cuatro pasos obligatorios: (1) solicitar OTP de acceso, (2) verificar OTP mediante Supabase Auth y comprobar la cuenta JWT en `/auth/v1/user`, (3) solicitar un **segundo** OTP sin habilitar alta de nuevas cuentas y (4) firmar la solicitud con la clave Ed25519 del teléfono y enviar el segundo código al gateway para su consumo real antes de invocar SQL restringido. No admite saltarse pasos, llamadas repetidas ni registro sin consentimiento. Sustituir un móvil requiere la selección expresa del usuario y revoca el teléfono anterior en SQL.

`NexoConnectOnboardingFlow.liveBackend(...)` solo debe recibir pares de claves ya almacenados de manera segura en `AndroidIdentityVault`; comprueba la cuenta esperada y crea el cliente de registro usando la sesión temporal que todavía está en memoria. **No se implementa aquí la generación ni la recuperación de keysets:** es un punto de integración pendiente, por seguridad de crash-consistency. Si el POST de alta queda incierto, el flujo bloquea el reenvío del OTP y pasa a estado `RECOVER_FROM_UNKNOWN_RESULT`. Antes de reintentar deberá comprobar el estado del servidor y solicitar un nuevo OTP.

La UI muestra expresamente qué paso corresponde a cada código y una advertencia de reemplazo. Ejecuta las llamadas de red fuera del hilo principal y no persiste correo, OTP o JWT en SavedInstanceState, archivos ni logs. Se añaden pruebas instrumentadas de la máquina de estados: orden de códigos, consentimiento, sustitución, fallo de red sin repetición y operación fuera de secuencia.

**Límites:** no hay launcher ni integración con Ajustes/Cuenta de Nexo, no hay orquestación de persistencia de keysets y comprobación de estado remoto, ni Supabase de pruebas desplegado. No utilizar en producción, ni fusionar ni enviar APK a usuarios todavía.
