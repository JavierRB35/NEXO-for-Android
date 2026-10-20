# Enrolamiento de teléfono único en Nexo Connect (código experimental)

`nexo-connect-enroll` exigirá simultáneamente un JWT actual **validado contra Supabase Auth**, un código OTP **nuevo** de seis cifras verificado en el servidor sobre el mismo usuario y una firma Ed25519 de la nueva clave, ligada a la identidad UUID y al cuerpo de alta o sustitución.

La firma de la clave nueva demuestra posesión de esa clave, pero **no demuestra por sí sola la legitimidad del dueño del correo**. Una cuenta de email comprometida puede permitir sustitución del dispositivo; para evitar suplantación entre contactos se mantiene fijada la identidad de clave anterior y la obligación de reverificar con QR. No se restauran los secretos de un dispositivo perdido.

## Flujo Android pendiente
1. Usuario se autentica por correo con OTP (sin contraseña ni frase).
2. Crea claves Ed25519/X25519 en teléfono, cifra su almacenamiento en `AndroidIdentityVault`.
3. Solicita **un segundo código recién emitido** y lo remite al gateway junto con prueba de posesión firmada para alta/sustitución.
4. Servidor coteja `Auth.getUser(token)` con `Auth.verifyOtp({email:user.email,token,type:"email"})`.
5. Al reemplazar, se incrementa generación; se invalidan nonces antiguos, se eliminan aprobaciones de claves de terceros heredadas del dispositivo perdido.
6. Contactos deben verificar la **nueva huella** antes de aceptar envíos posteriores. No se pretende borrar remotamente el dispositivo perdido.

## Bloqueos antes de desplegar función
- Configurar plantilla de email de Supabase para **`{{ .Token }}`**; la predeterminada puede enviar magic link.
- Comprobar el flujo OTP real de la pasarela y la política de limitación por IP/cuenta.
- Validar transporte Android con generación y firma interoperable de clave **raw** Ed25519.
- Revisar tasas/cuotas SMTP y condiciones de privacidad, errores, logs y sesiones efímeras.
- Mantener la función fuera de producción mientras esté sin cliente Android y pruebas completas.
