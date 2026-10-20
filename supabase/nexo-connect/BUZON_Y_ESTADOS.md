# Nexo Connect — buzón E2EE (laboratorio)

Migración 0003: canal de entregas pendientes, aprobaciones criptográficas
locales, permisos por destino y estados **inmutables una vez terminales**.

**No existe un backend público desplegado.** La API Edge deberá verificar
autenticación JWT usando Supabase Auth, **prueba de posesión del teléfono** con
challenge y firma Ed25519, generación del dispositivo, fingerprint, caducidad
y firma de contexto NXC1 antes de llamar a las funciones `api_*`.

## Propiedades

- Un envío de contenido solo puede enviarse a un contacto vinculado y cuyo
  permiso direccional de recepción esté activo.
- El remitente debe haber **verificado la huella de cifrado** del receptor.
  Un teléfono sustituido tiene otra generación; bloquea envíos antiguos.
- Los reintentos usan triple `(sender,recipient,client_request_id)` y una
  huella SHA-256 de ciphertext; mutar el contenido con el mismo ID se rechaza.
- Confirmaciones, rechazos, cancelaciones solo admiten transiciones desde
  `pending`, y `accepted` únicamente puede confirmarlo el destinatario.
- `ciphertext` borrado tras resolución: permanece registro mínimo de estado.
- La expiración a 30 días está prevista; programar limpieza externa aparte.
- RLS aísla destinatario/emisor de terceros incluso en el entorno de pruebas.

## Importante

Los mecanismos criptográficos del frontend deben verificar la clave previamente
aprobada, la firma, la fecha y el identificador del mensaje. **La tabla
`peer_key_approvals` solo registra una afirmación de verificación**, por lo
que la seguridad depende de verificar el QR realmente en el dispositivo antes
de permitir su escritura. El servidor no puede demostrar ese gesto humano.

El acuse de recepción **no demuestra** al servidor que SQLite hizo commit;
el cliente debe aplicar una transacción local con delivery-ID único y solo
después solicitar `accepted` mediante solicitud firmada. El archivo de
documentos grandes se desarrollará con Storage privado y cifrado por fragmentos;
por ahora el límite del ciphertext inline es 512 KiB.

**Bloqueantes:** ejecución real en Supabase independiente, firmas/gateway,
almacenamiento de documentos, anti-replay, FCM, UI y pruebas end-to-end.

## Cierre de entregas firmado (migración 0013, experimental)

`POST /delivery/resolve` recibe exclusivamente `{"deliveryId":"<UUID>","target":"accepted|rejected|cancelled"}` mediante sesión JWT verificada, firma Ed25519 del teléfono registrado y desafío de acción `delivery/resolve` de un solo uso. El backend fija por sí mismo el usuario y el dispositivo, no acepta identidad del cliente. Solo destinatario puede aceptar/rechazar y solo emisor cancelar; el estado resultante puede ser `expired` si se superó el TTL. La actualización y eliminación de ciphertext son atómicas e idempotentes para un estado ya terminal idéntico.

**Invariante pendiente en Android:** responder `accepted` ÚNICAMENTE tras confirmar la importación SQLite local con identificador de entrega único; un error de red al emitir acuse no debe volver a importar. El endpoint aún no está conectado a la interfaz. Migración 0013 y Edge se mantienen sin desplegar.
