# Nexo Connect — especificación técnica y plan de entrega

**Estado:** diseño aprobado en sus aspectos funcionales; implementación pendiente.
**Fase activa:** 0, laboratorio criptográfico aislado.
**Alcance:** no sustituye ni modifica el funcionamiento de Nexo 3.7.48.
**Política de pruebas:** las pruebas automatizadas las ejecuta GitHub Actions; el usuario únicamente hará pruebas manuales cuando exista una versión integrada y funcional.

## 1. Decisiones funcionales

- Cuenta Nexo **opcional**, independiente de Nexo Familia. Las funcionalidades locales siguen accesibles sin cuenta.
- Inicio de sesión y recuperación de la cuenta **solo mediante código al correo**; sin contraseña ni frase personal.
- **Un teléfono principal por cuenta**, con revocación de autorización del antiguo cuando se sustituye.
- Contactos exclusivamente **autorizados bilateralmente**, mutuos por defecto; permiso de recepción configurable por contacto sin eliminar el vínculo.
- Envíos **individuales o multidestinatario**, cada uno con copia y estado propio; nunca escritura directa sobre la base de datos del receptor.
- Contenidos: **citas/eventos, notas, listas, entradas de Cuaderno, fichas de libros, fichas de películas y documentos con archivos adjuntos**. Las fichas de libros/películas no incluyen el libro electrónico ni el archivo audiovisual.
- Inbox de recepción con aceptación/rechazo; historial de salida **pendiente/aceptado/rechazado/caducado**, más cancelación y errores técnicos.
- Advertir posibles duplicados por coincidencia de contenido; permitir importar otra copia; **no sobrescribir, actualizar ni fusionar** elementos existentes.
- Android push e Inbox; los avisos pueden silenciarse sin perder mensajes; mostrar **remitente y tipo, nunca título o cuerpo**. Respetar privacidad de pantalla de bloqueo.
- Contenido protegido por **cifrado de extremo a extremo desde la primera versión**.
- Datos personales permanecen locales. El servidor solo guarda metadatos, contactos y contenido cifrado transitorio.
- Si se pierde el móvil y no existe transferencia segura desde el anterior, tras la verificación de correo se regeneran las claves; **los pendientes antiguos requieren reenvío** y los contactos vuelven a verificar la identidad criptográfica.

## 2. Aislamiento y componentes

- Crear un proyecto **Supabase nuevo**, separado del proyecto y los privilegios de Nexo Familia.
- Auth OTP por correo; Edge Functions con sesión, control de dispositivo principal y límites antiabuso.
- PostgreSQL con RLS, escrituras atómicas y restricciones para contactos, autorizaciones y estados.
- Storage privado solo para ciphertexts grandes y archivos cifrados; las URLs no serán públicas ni permanentes.
- FCM enviará identificadores opacos. El cliente obtiene remitente/tipo tras autenticarse y construye notificación local.
- Android Java/JDK17, SDK34, AGP8.7.3, Gradle8.9, ARM64 con AndroidIDE-Rv2. Mantener configuración existente. No introducir secretos administrativos en cliente o repositorio.
- Compartir `.nexoshare` como archivo externo sigue existiendo. El nuevo módulo necesita **serialización en memoria privada antes de cifrar**: está prohibido generar un archivo plano en Descargas como paso intermedio.

## 3. Diseño criptográfico sujeto a auditoría

1. **Cifrado híbrido**: HPKE DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-256-GCM mediante biblioteca contrastada como Google Tink.
2. **Firma de remitente**: Ed25519 sobre sobre canónico versionado, con separación de dominio y longitud de campos.
3. **Binding**: asociar criptográficamente a versión, ID único, emisor, receptor, tipo, generación de clave, caducidad y huellas de identidad; rechazar cambios y usos fuera del contexto.
4. **Claves privadas**: material cifrado en el almacenamiento privado con claves de envoltura del Android Keystore. No exportar a la nube; excluir copias automáticas incompatibles del sistema.
5. **Contactos**: verificar huellas mediante QR/código o canal equivalente independiente de la respuesta de Supabase. Cambio de clave = aviso y bloqueo de envíos hasta reverificación; nunca aceptar sustitución silenciosa desde servidor.
6. **Ataques de repetición**: IDs únicos, contador/generación de dispositivo y registros de importación con transacciones idempotentes.
7. **Varios destinatarios**: sobre cifrado independiente y clave/contexto exclusivo por destinatario.
8. **Archivos**: cifrado autenticado por fragmentos o mecanismo auditado de streaming, tamaño/cantidad acotados, manifiesto cifrado, comprobación de integridad global, escritura privada temporal y limpieza tras cancelación/error.
9. **Limitación explícita**: HPKE por sí solo **no es un protocolo con ratchet** ni garantiza confidencialidad futura frente al robo posterior de claves. La amenaza y mitigaciones deben revisarse antes de producción.

## 4. Propuesta de tablas

| Tabla | Datos principales | Norma de acceso |
|---|---|---|
| `profiles` | uid, nombre visible | Cuenta propietaria, contactos con información mínima |
| `devices` | uid, device_uuid, generation, signing_pub, hpke_pub, active | Solo propietario; reemplazo vía función |
| `contact_invites` | token_hash, creador, expiry, use_count | Solo función autenticada |
| `contact_requests` | emisor, receptor, estado, TTL | Solo implicados |
| `contacts` | user_a, user_b, active | Solo implicados |
| `receive_permissions` | owner, sender, allow_receive | Controlado por propietario |
| `key_history` | uid, generación, huellas, revocación | Lectura limitada; actualizaciones firmadas y verificadas |
| `deliveries` | uid envío, lote, destinatario, remitente, tipo, ciphertext, estado, expires | Solo partes del envío, campos por rol |
| `delivery_attachments` | envío, objeto de Storage privado, tamaño ciphertext, hash | Solo destinatario/remitente permitido |
| `delivery_events` | envío, actor, estado, timestamps | Solo partes; transiciones atómicas |
| `push_devices` | uid, device_uuid, token_fcm | Solo funciones servidor y propietario |

Antes de crear SQL real: definir índices y foreign keys, roles mínimos, reglas RLS y pruebas negativas de acceso con tercera cuenta no autorizada. La función de envío debe validar el vínculo y el permiso **en servidor**, no confiar en la interfaz.

## 5. Protocolo de envío e importación

1. Remitente elige elemento y destinatarios autorizados.
2. Se serializa solo la lista blanca de campos admitidos, **sin rutas locales, tokens, identificadores internos ni adjuntos no autorizados**.
3. Se cifra en memoria; cada receptor recibe sobre HPKE y firma verificada en receptor. Los documentos se cifran antes de Storage.
4. Función de envío valida sesión, dispositivo principal, relación, permiso, cuota, vigencia de clave y tamaño. Inserta una entrega independiente por receptor con idempotencia.
5. FCM dispara aviso opaco; Nexo consulta su Inbox al abrir o recuperar red.
6. Receptor recupera ciphertext, verifica firma de **identidad ya aprobada**, descifra, valida esquema y muestra vista previa.
7. Receptor detecta coincidencias localmente sin informar al emisor. El usuario acepta o rechaza.
8. Aceptar: transacción SQLite para insertar datos + registrar delivery ID; tras commit, emitir acuse al servidor. Si falla red, reintentar acuse sin volver a importar.
9. Cambios a estado terminal con CAS/operación atómica. Caducidad inicial propuesta: **30 días**; metadatos mínimos 90 días, pendientes de revisión legal.
10. El remitente solo ve estado; no puede modificar/eliminar contenido ya incorporado en el receptor.

## 6. Pantallas previstas

- Ajustes → Cuenta Nexo: OTP, sesión, identidad, dispositivo principal, cerrar sesión, sustituir.
- Ajustes → Contactos Nexo: invitaciones QR/enlace de uso limitado, aceptar, verificación de huellas, permisos de recepción y desvincular.
- Acción en fichas compatibles: «Enviar a Nexo» con selección única/múltiple y confirmación.
- Inbox → Recibidos Nexo: remitente, tipo, vista previa, duplicados, aceptar, rechazar, revisar después; descifrado solo local.
- Mis envíos: estado por receptor, cancelación de pendiente, caducados y solicitud de reenvío.
- Notificaciones: canal Android para intercambio, recepción silenciosa configurable, privacidad de pantalla bloqueada.

## 7. Fases y criterios de aceptación

### Fase 0 — Laboratorio criptográfico (actual)

Carpeta `experiments/nexo-connect-crypto/`, **sin integración con MainActivity, WebView, NexoNative ni servicios de nube**.

CI Java 17/Gradle 8.9 prueba:
- nota cifrada y descifrada;
- documento binario de 10 MiB;
- destinatario incorrecto;
- firma de remitente incorrecta;
- ciphertext y firma manipulados;
- contexto/identidad del destinatario cambiados;
- copias independientes para varios receptores;
- entradas fuera de límites.

**No cubre aún** Android Keystore real, dispositivos Android físicos, idempotencia SQLite, almacenamiento cifrado en disco, auditoría del protocolo, FCM, RLS, pérdida de móvil ni cifrado de documentos en streaming. No declarar la fase 0 completamente aprobada sin esas pruebas.

### Fase 1 — Identidad y contactos

Crear proyecto Supabase **de pruebas** independiente, configurar OTP, dispositivo autorizado, QR, políticas RLS y casos negativos de acceso.

### Fase 2 — Envíos E2EE

Construir adaptadores por tipo; serializar sin MediaStore; almacenamiento/descifrado seguro; aceptación local transaccional sin sobrescritura; adjuntos cifrados.

### Fase 3 — FCM y UX

Enviar notificaciones push mínimas, Inbox, salida y estados. No depender de entrega inmediata: el servidor es buzón y la app realiza comprobaciones al abrirse.

### Fase 4 — Endurecimiento y Release final

Revisión de amenazas, automatización CI/Android, pruebas de dos/tres cuentas de prueba y dos dispositivos físicos gestionados por el equipo, sin datos personales. Verificar cambios de dispositivo y recuperación, expiración, abuso y privacidad.

**El usuario no realizará pruebas manuales hasta que exista una versión integrada y terminada.** No publicar APK oficial ni promover estos cambios a `main` por el mero hecho de superar una prueba de laboratorio. Pedir revisión del diseño y publicar solo con criterios Go/No-Go cumplidos.

## 8. Bloqueadores de producción y siguientes pasos

- API estable de biblioteca criptográfica, evaluación de dependencias en el APK Android (SDK34, ARM64).
- Diseño formal y auditoría de firmas, contextos, huellas y actualización de claves.
- Cifrado de archivos en streaming (no utilizar memoria de 10 MiB por defecto).
- Manejo de almacenamiento persistente de claves y exclusión de Auto Backup.
- Test de mutaciones, corrupción y recuperación en Android real.
- Supabase + FCM separados con secretos de servidor; controles de coste y protección antifraude.
- Consentimiento del usuario y política de privacidad por alojamiento de ciphertexts/metadatos.

## Referencias técnicas

- https://developers.google.com/tink/hybrid
- https://developers.google.com/tink/digital-signature
- https://developers.google.com/tink/key-management-overview
- https://supabase.com/docs/guides/auth/auth-email-passwordless
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://firebase.google.com/docs/cloud-messaging
