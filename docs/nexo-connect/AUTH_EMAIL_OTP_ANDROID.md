# Fase 3 — Cliente Android Supabase Email OTP (experimental)

Se incorpora NexoConnectEmailOtpClient en el módulo aislado Android.
POST /auth/v1/otp envía código al correo; POST /auth/v1/verify verifica el código,
y GET /auth/v1/user confirma que el JWT corresponde a la cuenta y correo previstos.
El usuario no se deduce del JSON de la respuesta OTP, sino de Auth /user por TLS.

Requisitos: plantilla de correo de Supabase con la variable Token que envía un
código de seis dígitos; configuración y límites del servidor en proyecto de
pruebas independiente. No ejecutar en Nexo Familia.

Los JWT se mantienen solo en memoria, no se guardan ni registran y se invalidan
para uso local si pasan su vencimiento o cambia de cuenta. Sin refresh automático
ni reintentos ciegos. Las URL se limitan a origen HTTPS *.supabase.co y Auth.

CRÍTICO: JWT válido tras login OTP NO demuestra al backend de Nexo Connect
que un OTP fue recientemente consumido para reemplazar un teléfono. La RPC
de enrollment sigue siendo restringida a service_role, y aún falta gateway
que verifique frescura OTP de forma independiente. No activar onboarding
real ni sustitución de dispositivo hasta cerrar esa frontera.

Pruebas instrumentadas con transporte falso: OTP, JWT + Auth /user,
sesión expirada o cruzada, correo falso, rechazo 401 y 429, orígenes inseguros.
Sin Supabase real ni SMS/correo de prueba. No hay merge ni deploy.