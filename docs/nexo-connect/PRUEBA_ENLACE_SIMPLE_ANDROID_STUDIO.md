# Nexo Connect — vinculación sencilla (candidata, sin publicar)

## Problema observado el 23/10/2026
El servidor respondió 503 a `contact/accept`, provocado por errores SQL HTTP 400.
Los mensajes de PostgreSQL distinguían `Already linked` y
`Token creator mismatch or invite expired`.
En el mismo intervalo `key/verify` y `contact/permissions` devolvieron 200:
es posible que exista una vinculación activa aunque una aceptación repetida falle.
No borrar cuentas, sesiones ni claves para resolverlo.

## Flujo previsto, explicado a personas no técnicas
1. En **Chat → Personas**, A toca **Invitar a alguien** y envía la invitación privada (WhatsApp).
2. B abre la invitación con Nexo. Llama a A o la comprueba en persona.
3. A abre **Personas → Mi código QR y de seguridad** y lee a B el código de
   **16 caracteres en cuatro grupos**. El código no procede de la invitación que
   B recibió. B lo introduce en su móvil y acepta la conexión.
4. B toca **Ver mi código**. A abre **Personas → Abrir invitación / escanear QR**,
   captura el QR de B y coteja su código (por llamada/en persona).
   Nexo comprueba automáticamente la relación con el servidor; si aún no consta,
   permite revalidar desde **Personas → Comprobar conexión** más adelante.
5. Una vez ambos teléfonos confirman el contacto, pueden compartir y chatear.

## Invariantes de seguridad
- El código humano se calcula mediante SHA-256 con dominio separado, UUID de
  cuenta, generación y ambas claves públicas. Son 64 bits de comparación humana,
  **no** una sustitución de los dos SHA-256 completos de 256 bits.
- Nunca se prellena el campo desde el QR recibido. Se necesita canal independiente
  más consentimiento explícito.
- El almacén de confianza mantiene las dos claves públicas completas y valida
  íntegramente sus huellas. La verificación remota emplea la firma del teléfono,
  un desafío de un solo uso y `key/verify` del servidor.
- Si `contact/accept` falla con 503, Nexo solo continúa si `key/verify`
  demuestra que la relación ya estaba autorizada. Si falla la prueba firmada,
  no concede permisos; no se reutiliza ni reenvía automáticamente el token.
- Ninguna pantalla muestra secretos de invitación, JWT ni claves privadas.

## Pruebas requeridas antes de Release
- Compilación Debug en PC con JDK 17, Gradle 8.9, SDK 34, AGP 8.7.3.
- Compilación y ejecución de `:connectVault:connectedDebugAndroidTest` en emulador.
- Dos cuentas de prueba en dos teléfonos: invitación nueva, previamente aceptada,
  caducada, QR falso, código incorrecto y coincidencia de código verdadero.
- Chat y envío de una ficha en ambas direcciones.
- En una APK Release de versión superior: instalar **encima** de 3.7.54 con
  el mismo certificado oficial, comprobar que sobreviven notas, sesión y chats.
- No publicar en Nexo Updates antes de completar la validación.
