# Recuperación segura de inscripción de teléfono después de un corte de red

Componente experimental NexoConnectDeviceRecoveryClient en Android.
Cuando el POST de registro con segundo OTP queda sin respuesta, NO debe
reenviarse ese código. La nueva recuperación EXPLÍCITA solo usa un JWT de
sesión no vencido para POST /status {} y, si coincide el deviceId,
comprueba además la posesión de la clave Ed25519 con nuevo desafío y POST
firmado de solo lectura a /delivery/inbox. UUID coincidente SIN firma
NO significa teléfono autorizado. Los resultados son:
OWN_DEVICE_CONFIRMED, NO_REGISTERED_DEVICE, ANOTHER_DEVICE, INCONCLUSIVE.

Error de red, firma inválida, servidor contradictorio o sesión caducada
no activan el móvil, no cambian identidad y no reutilizan códigos OTP.
La vista y la máquina de estados de alta se conectarán a este componente
en un commit posterior. Quedan por conectar Tink keysets persistentes,
prueba de Supabase desplegado y validación con dos teléfonos físicos.
Rama experimental solamente, sin despliegue ni merge.

Integración completa: `NexoConnectOnboardingFlow.reconcile()` invoca el cliente HTTPS real exclusivamente tras `RECOVER_FROM_UNKNOWN_RESULT` y la vista ofrece «Comprobar estado sin reenviar». Resultado coincidente + firma de teléfono válida cierra el flujo; ausencia/otro teléfono permite solicitar un NUEVO segundo OTP; firma no confirmada conserva bloqueo. Pruebas instrumentadas de máquina de estados validan ambas rutas y que ninguna reimporta ni reenvía automáticamente el código.
