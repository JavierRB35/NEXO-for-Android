# Verificación visual de contactos QR — componente Android aislado

Se crea `QrTrustConfirmationView` como una vista nativa Java/Android de laboratorio, **no enlazada a las pantallas de la APK Nexo estable**. El QR se obtiene externamente de un escáner todavía pendiente; la vista **no solicita permisos de cámara** y **no trata el QR como prueba de identidad**.

**Interfaz:** muestra UUID de cuenta y generación del otro teléfono, SHA-256 completa de su clave Ed25519 y SHA-256 completa de su clave pública HPKE, agrupadas para lectura. Dos campos de huellas **comienzan vacíos**: el usuario debe escribir las huellas completas que haya obtenido independientemente en pantalla del teléfono del contacto o en un canal fiable que no dependa del QR/Supabase. No se autocompletan con los valores del QR. Requiere marcar por separado la comprobación independiente y el consentimiento expreso. La comparación constante se hace con los 64 caracteres completos de cada SHA-256, sin abreviar. Los errores nunca almacenan confianza.

Al confirmar, ejecuta el almacenamiento AES-256-GCM/Android Keystore en un hilo separado, no bloquea la UI y notifica a la vista si se registró o fue rechazado. Un contacto previamente activo **no puede cambiar claves ni generación** sin revocación expresa y nueva verificación. Se añadieron pruebas de normalización, pantalla deshabilitada inicialmente, guardado tras autorización y rechazo por huella incorrecta.

**Corrección crítica del contrato QR:** `AndroidPeerTrustStore.encodeQr` emitía seis campos (format, version, peerId, generation, signingKey, encryptionKey) pero `parseQr` comprobaba cinco. Se corrige a **seis campos exactos** y se añade prueba positiva de ida y vuelta. No se altera la criptografía ni el baseline JDK17/SDK34/Gradle8.9/AGP8.7.3.

**Pendiente para una aplicación utilizable:** integración real del escáner/cámara, mostrar la huella del propio móvil al interlocutor por un canal confiable, conectar el consentimiento con la doble autorización de `contact/accept` y `key/verify` en Supabase, pantallas de lista/desvincular, protección de la sesión y pruebas con dos móviles. Una persona podría copiar las huellas desde el QR sin verificar independientemente; la UI y el cifrado no pueden impedir ese engaño. Se requiere prueba presencial/identidad humana, no basta con marcar las casillas. Tampoco existe garantía anti-rollback contra restauración maliciosa de blobs cifrados en un dispositivo rooteado.

**Estado:** PR experimental sin fusionar; no desplegar migraciones ni instalar APK parcial.
