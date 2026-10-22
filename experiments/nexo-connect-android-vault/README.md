# Nexo Connect · laboratorio Android aislado

Prueba experimental de cifrado E2EE y emparejamiento entre **dos cuentas verificadas** con Supabase. No forma parte de la aplicación Nexo publicada ni altera el móvil real de nadie. La PR #65 sigue en borrador.

## Funciones del APK piloto

- Autenticación por email OTP, JWT validado en Supabase, segundo OTP para registrar/sustituir el dispositivo y sesión protegida con Android Keystore.
- Claves privadas Ed25519 (firma) y HPKE X25519 protegidas en almacenamiento privado con clave AES-256 no exportable del Keystore. No se restauran silenciosamente claves perdidas.
- Generación de identidad pública y de invitación de un solo uso en QR.
- Dos formas de leer el QR: cámara ZXing **o seleccionar una imagen con el gestor de archivos**. Importar imagen **no necesita cámara ni permisos de almacenamiento** y no envía el archivo a ningún servidor.
- **La importación del QR no establece confianza**: ambas huellas SHA-256 completas deben verificarse independientemente del QR y de Supabase, con confirmación expresa de la persona.
- Firmas Ed25519 con desafío de un solo uso; vínculo verificado, permisos, recepción y desvinculación.
- Envío experimental de notas Nexo con Google Tink HPKE y contexto NXC1, bandeja cifrada, importación privada y ACK solo tras guardar; sin bucles automáticos de SMS, OTP o reenvíos.

## Importar una imagen QR sin cámara

En el APK `Nexo Connect Pruebas`, una vez iniciada una sesión válida, selecciona **3a. Importar QR de imagen (sin cámara)**. El selector de Android permite escoger un PNG/JPEG con el QR de la otra persona. El archivo se descodifica **localmente** y pasa a la misma pantalla de comparación manual de las dos huellas de identidad que el escáner con cámara.

Por seguridad, el lector no acepta archivos de más de 4 MiB, imágenes con dimensiones superiores a 8192 píxeles por lado, ni QR que no sigan el protocolo Nexo. Las huellas de referencia deben proceder **del otro teléfono o de un canal confiable distinto**. Una imagen reenviada por sí sola no demuestra la identidad del contacto.

El emparejamiento por imagen sirve también para pruebas en emuladores **sin cámaras ni salas virtuales**, pero no sustituye la verificación humana en el uso real.

## Pruebas de consola y automatizadas

Windows desde `experiments/nexo-connect-android-vault`:

```powershell
..\..\gradlew.bat -p . :vault:connectedDebugAndroidTest --no-daemon --max-workers=1
```

La batería Android incluye pruebas del descodificador de imágenes, QR reales con ZXing, huellas, KeyStore, importación, mensajes y revocación. No abre cámaras en las pruebas nuevas de imagen.

La fase de laboratorio **HTTPS E2EE de extremo a extremo** con dos cuentas efímeras, Google Tink, notas en ambas direcciones, ACK y revocación finalizó correctamente y se documentó en la PR #65. Las dos cuentas de ese laboratorio fueron eliminadas tras autorización, sin afectar otros usuarios. No vuelvas a ejecutar el ensayo `e2eeLab` con aquellos UUID: las cuentas ya no existen.

## Límites antes de integrar en Nexo principal

- El intercambio HTTPS real se verificó desde una **consola JVM**, no como recorrido humano completo dentro del piloto Android.
- La comparación independiente de identidad, los estados de recuperación, accesibilidad y pruebas de usabilidad de Android deben validarse antes de conectar estas pantallas con la aplicación estable.
- Ninguna prueba de laboratorio implica certificación de seguridad, comercialización o despliegue de producción.
- No fusionar la PR #65 ni trasladar claves o credenciales del laboratorio a producción sin revisión separada.

Compatibilidad conocida: Java 17, SDK 34, Gradle 8.9 y AGP 8.7.3.

## Puerta de entrada en Nexo completo (experimental)

**Solo en el build DEBUG de la rama experimental** se añade `Ajustes → Nexo Connect · experimental → Abrir Nexo Connect Pruebas`. El enlace abre explícitamente el APK independiente `com.jruiz.nexo.connect.pilot`, si está instalado. En la APK RELEASE, la sección permanece oculta y el puente bloquea el lanzamiento. El control **no** copia ni expone contenidos, cuentas, claves o sesiones de Nexo principal al piloto.

Es una integración de navegación, **NO la incorporación de E2EE a la base de datos principal**, ni sustituye un ensayo HTTPS real entre dos instancias Android. El piloto sigue con claves y base de datos independientes.

Desde PowerShell en `experiments/nexo-connect-android-vault` se pueden pasar las pruebas y compilar las dos APK localmente:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -IncludeNexoMain
```
Omitir los tests del emulador: añadir `-SkipEmulator`. Ninguna variante usa GitHub Actions.

### Integración E2EE pendiente
- Sustituir configuración LAB por perfil de producción y diseño de autenticación, sin reutilizar claves entre paquetes.
- Integrar en la interfaz nativa de Nexo consentimiento por destinatario, envío de notas y listas, bandeja de entrada y recepción verificada con inserción SQLite atómica.
- Manejar revocaciones, reenvíos inciertos sin bucles, fallos de ACK, cambios de teléfono, recuperación y accesibilidad.
- Ensayo HTTPS real desde **dos Android** y revisión independiente de seguridad antes de unir PR #65. Los 102 tests por AVD prueban componentes locales, no este escenario completo.

## Windows: solución al error de biblioteca Sherpa ausente

La APK completa Nexo requiere la dependencia local `app/libs/sherpa-onnx-1.13.7.aar`, ignorada expresamente por Git (`.gitignore`). El instalador Linux `prepare-transcription-runtime.sh` no se ejecutaba al usar `run-local-tests.ps1` en Windows, lo que ocasionaba `checkDebugAarMetadata FAILED`.

El script PowerShell ahora ejecuta `Ensure-SherpaRuntime` **solamente si se compila Nexo principal**. Si falta, descarga una vez la versión v1.13.7 desde la release oficial `k2-fsa/sherpa-onnx`, verifica SHA-256 `c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8` (mismo pin que el script Linux), y solo entonces la instala en `app/libs`. Rechaza archivos con un hash erróneo. No cambia versiones de JDK, Gradle o Android y **no utiliza GitHub Actions**.

Para reintentar únicamente Nexo principal sin repetir el piloto ni los 102 tests por AVD, en PowerShell desde `experiments/nexo-connect-android-vault`:

```powershell
git pull --ff-only origin experiment/nexo-connect-phase0-crypto
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -MainOnly
```

Esta instrucción genera solo un APK de depuración de Nexo y no lo instala ni fusiona la PR. La primera descarga necesita conexión a GitHub Releases, no a Actions.

## Nexo integrado: primera ejecución en el mismo APK (solo DEBUG)

La aplicación principal incluye ahora `:connectVault` como dependencia **debug-only**, recuperando el código de cifrado E2EE original del experimento. El código fuente de las clases `com.jruiz.nexo.connect.*` se compila una sola vez dentro de la biblioteca para evitar clases dex duplicadas. Los archivos UI del piloto se reutilizan como `src/debug/java`, y un `app/src/debug/AndroidManifest.xml` registra una Activity **no exportada**. La configuración original Gradle 8.9/AGP 8.7.3/SDK34 y la aplicación estable permanecen intactas.

En la build de depuración: **Ajustes → Nexo Connect · experimental → Abrir Connect integrado (LAB)**. Esta opción abre una Activity **dentro de `com.jruiz.nexo`**, no en el paquete del piloto `com.jruiz.nexo.connect.pilot`. El botón anterior se conserva para abrir las dos cuentas experimentales ya verificadas en el piloto independiente, sin borrar su información.

**Advertencia de identidad:** claves privadas con Android Keystore, cuentas y sesión cifrada pertenecen a cada paquete. Nexo integrado **no** importa automáticamente ni puede copiar las claves de la app piloto. Iniciar el flujo integrado requiere autorización/verificación nueva del dispositivo para el mismo correo/cuenta; no genera una cuenta ni contactos en la apertura de la pantalla. No se debe tratar ese flujo como una migración ya terminada.

**Limitaciones:** piloto experimental con endpoint Supabase LAB. No enviar datos personales reales. No hay todavía integración entre notas/listas SQLite de Nexo y la entrada/salida del motor E2EE, ni prueba de tráfico HTTPS real entre dos instancias Android con esa APK. Compilar primero con `run-local-tests.ps1 -MainOnly` (biblioteca Sherpa verificada), instalar **solo en emulador**, comprobar que se abre la nueva pantalla y que el piloto externo continúa intacto. Mantener PR #65 draft, sin merge ni publicación.

## Enviar una nota de Nexo a un destinatario verificado (solo LAB DEBUG)

La pantalla **Connect integrado** añade `5a. Enviar nota guardada en Nexo`, implementado exclusivamente en `app/src/debug/java/com/jruiz/nexo/connect/host/NexoConnectEmbeddedActivity.java`. La clase extiende el piloto sin mezclar lógica de UI, sesión o de criptografía con `MainActivity`. En el piloto independiente no aparece esta opción.

Flujo seguro: el usuario abre un selector de hasta 100 notas **ya guardadas** de Nexo (consulta `DatabaseHelper.getNotes()`, solo lectura), escoge una, se vuelve a cargar esa nota por ID para evitar usar un registro eliminado o cambiado, se validan límites de texto, se elige expresamente un contacto previamente verificado por QR, se muestra el compositor con título y cuerpo editables y **solo después** aparece el diálogo de confirmación del envío único. No se envían ficheros adjuntos, rutas, IDs locales ni cuentas de Nexo; nunca se añaden datos de notas en Intent extras. No hay sincronización ni reenvío automático de solicitudes inciertas. El piloto conserva la misma política de cifrado/verificación que el flujo manual de nota. La nota fuente no se modifica.

**Limitaciones y revisión pendientes:** este primer envío usa solo título y cuerpo, por lo que no replica metadatos, enlaces, archivos ni bases de datos; requiere configurar la identidad de la instalación integrada y establecer confianza mutua. Las notas recibidas siguen en `nexo-connect-pilot.db` privado hasta validar la importación atómica en la base principal y los ACK tras COMMIT. Esta funcionalidad no está validada en un intercambio HTTPS Android↔Android ni está lista para producción.

Compilar en Windows: `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -MainOnly`. Una vez generado `app-debug.apk`, instalar únicamente en Pixel_6 con `adb -s emulator-5554 install -r`, preservando el piloto externo.

## Prueba de notas sin cuenta de Nexo Connect integrada

Como la Activity integrada tiene un almacenamiento Android Keystore distinto del APK piloto, la instalación integrada **no posee aún una identidad registrada en Supabase LAB**. Para no pedir al usuario otra cuenta, un nuevo enrolamiento que podría revocar el dispositivo anterior, ni trasladar secretos entre aplicaciones, existe ahora una prueba solo local.

Desde `Ajustes → Nexo Connect · experimental → Abrir Connect integrado (LAB)`, aun en `Inicio seguro`, aparece `Vista previa de notas de Nexo · sin cuenta y sin envío`. La opción usa el mismo selector de notas guardadas de `DatabaseHelper`, lee una nota bajo decisión explícita del usuario y muestra hasta 2000 caracteres de contenido en un cuadro de diálogo con **únicamente «Cerrar»**. No llama a `offerHostNote`, no busca destinatarios ni accede al transporte, autenticación o servidor; no guarda ni envía nada. El hook `addHostSetupActions()` por defecto es vacío y el piloto independiente no muestra este control.

En una sesión LAB ya autorizada sigue existiendo `5a. Enviar nota guardada en Nexo`: ese camino separado exige contacto verificado, revisión de mensaje y confirmación antes del único intento de envío cifrado. La prueba sin cuenta **no valida cifrado ni intercambio HTTPS**.

Compilar localmente con `run-local-tests.ps1 -MainOnly` y actualizar únicamente `emulator-5554`; no realizar enrolamientos para esta prueba. La PR permanece en borrador.

## Promoción manual segura de una nota recibida al bloc nativo (DEBUG, fase siguiente)

En **Connect integrado (LAB)**, tras iniciar sesión con un dispositivo autorizado, se muestra **6a. Copiar nota cifrada recibida a Notas de Nexo**. La nueva clase `NexoConnectLocalNotePromotion` no realiza solicitudes ni confirmaciones al servidor. Lee solamente recibos locales con `entity_type=note`, filas marcadas `source=nexo-connect` y cuerpo cifrado `nxc2:`; los elementos piloto históricos en texto claro quedan excluidos.

Al seleccionar la entrega se revalida que el remitente sigue fijado y activo en el almacén QR para la **cuenta actualmente autenticada**, se descifra el contenido exclusivamente usando AES-GCM Keystore ligado a esa cuenta+ID de entrega y se aplica de nuevo la allowlist de notas. El usuario ve un extracto y una advertencia importante: la copia al bloc de Nexo se guardará como una **nota local normal, sin el cifrado adicional de Connect**. Solo pulsando **Copiar a Notas de Nexo** se crea la copia.

La clase vuelve a verificar toda la entrada al copiar. En la base **destino** se insertan la nueva nota nativa y un registro exclusivo `nexo_connect_native_note_promotions` dentro de **una misma transacción SQLite**. El `delivery_id` es único; repetir la operación no crea duplicados y detecta cambios sospechosos en remitente, cuenta o contenido. Si la nota copiada fue eliminada, no se resucita automáticamente. La base privada de Connect queda intacta; no se genera ningún ACK nuevo ni reenvío.

**Precaución:** la transacción solo cubre la base destino; la base cifrada fuente se lee pero no se modifica. El usuario sigue sin necesitar nuevas cuentas ni verificaciones para la prueba local de lectura ya validada. Esta nueva acción 6a requiere una cuenta **ya autorizada** y una nota cifrada **ya recibida**; no está habilitada en `Inicio seguro` ni se ha probado aún el transporte Android↔Android real.


## 22/10/2026 — Catálogo de compartir desde Nexo, flujo sencillo (LAB DEBUG)

Se ha incorporado un **selector común** de `Compartir → Con un contacto de Nexo (pruebas)`, conservando el botón de compartir por Android en Notas, Listas, Cuaderno/Frases, Agenda, Lugares, Libros y Filmoteca. La función solo aparece en Nexo **DEBUG**. Los libros y películas reutilizan sus pantallas de detalle; también se puede explorar el catálogo nativo desde la actividad integrada, incluso sin iniciar sesión, en modo **vista previa local**.

La Activity `NexoConnectEmbeddedActivity` ahora muestra `Explorar fichas de Nexo · sin cuenta y sin envío` en Inicio seguro, y `Compartir ficha guardada en Nexo` tras verificar sesión. El puente `MainActivity.openNexoConnectShare(section,id)` solo transporta **sección de una lista blanca + ID local** por Intent interno, nunca contenido, tokens, rutas ni claves. La ficha se vuelve a leer exclusivamente dentro del proceso de Nexo tras seleccionarla. `NexoConnectHostShare` aplica **la misma lista blanca** `NexoConnectPayloadCodec` del protocolo. El usuario elige contacto previamente verificado y confirma **cada envío una sola vez**; no hay reintentos automáticos ambiguos.

### Cobertura real y límites

| Contenido Nexo | Preparar y enviar metadatos E2EE | Recepción cifrada privada del piloto | Importación final a la sección nativa |
| --- | --- | --- | --- |
| Notas sin adjuntos | Sí, LAB DEBUG con identidad propia verificada | Sí | Solo copia manual de nota, pendiente de validar con identidad integrada |
| Citas/eventos/tareas simples SIN recurrencia, alarmas ni asistentes | Sí, LAB DEBUG | Sí, bandeja cifrada privada | **Pendiente** de guardar como cita y programar alarmas |
| Listas con entradas y orden | Sí, LAB DEBUG | Sí, bandeja cifrada privada | **Pendiente** de copiar a Listas de Nexo |
| Cuaderno y frases | Sí, LAB DEBUG | Sí, bandeja cifrada privada | **Pendiente** de copiar a Cuaderno/Frases |
| Libros | Sí, LAB DEBUG | Sí, bandeja cifrada privada | **Pendiente** de copiar a Libros |
| Películas y series | Sí, LAB DEBUG | Sí, bandeja cifrada privada | **Pendiente** de copiar a Filmoteca |
| Documentos y archivos | **Bloqueado**: hay que cifrar y verificar el binario | No | Pendiente |
| Lugares/restaurantes y aparcamiento | **Bloqueado**: falta especificación de campos e importación fiel | No | Pendiente |
| Medicación, grabaciones y otras fichas | **Bloqueado**: sin exportador/importador específico | No | Pendiente |

**Importante:** que una ficha figure en la bandeja privada cifrada NO significa que esté ya incorporada a su sección nativa; no se disfraza de nota normal en Nexo. `NexoConnectSealedNoteStore` guarda actualmente un sobre `nxc2:` con tipo original ligado por AES-GCM/Keystore al par cuenta+recibo y un marcador sin datos en claro en la tabla interna de `items`. Notas recibidas anteriormente siguen teniendo su formato compatible. `showReceivedNotes` comprueba tipo contra `nexo_connect_import_receipts` antes de presentar el contenido. El destino final por tipo necesita una fase posterior de copia bajo consentimiento y recibo atómico.

**Identidades:** las dos identidades que ya han intercambiado notas E2EE por HTTPS siguen perteneciendo al paquete `com.jruiz.nexo.connect.pilot`; el `com.jruiz.nexo` integrado NO puede importar sus claves Keystore. No solicitar un segundo registro sustituyendo dispositivo ni confundir la vista previa del catálogo (sin cuenta) con un envío desde la aplicación integrada. Mantener PR #65 **draft**, sin merge ni publicar APK de producción. Se debe desarrollar una transición de identidad explícita, asistida y segura.

### Pruebas locales recomendadas

Desde `experiments/nexo-connect-android-vault` en el PC, con Pixel_6 y LAB_B arrancados:

```powershell
git pull --ff-only origin experiment/nexo-connect-phase0-crypto; if ($LASTEXITCODE -eq 0) { powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1 -IncludeNexoMain }
```

La prueba `NexoConnectSealedNoteStoreTest` amplía la validación de cifrado para cita, lista, Cuaderno, libro y película (incluyendo recibos/idempotencia, vinculación AES-GCM a cuenta/entrega y rechazo de documentos sin binario cifrado). **Esta nueva revisión aún no se ha compilado ni probado localmente**, por lo que no se da por validada hasta que el usuario comparta el resultado.

No invocar GitHub Actions automáticamente: preferimos el script local del PC; el usuario ha autorizado usarlas si agilizan claramente una futura fase.


## 22/10/2026 — Copia voluntaria de una lista cifrada recibida a Listas de Nexo (LAB)

Se añade `NexoConnectLocalListPromotion` al módulo `vault` y el botón de laboratorio `Añadir lista cifrada recibida a Listas de Nexo` en `NexoConnectEmbeddedActivity`. Se reutiliza el protocolo E2EE/HPKE y el recibo local verificado: esta nueva función no transmite, descarga ni acusa mensajes y no lleva datos privados en `Intent`.

Flujo esperado **solo para una cuenta Connect integrada y ya verificada**: abrir bandeja, recibir una lista con consentimiento, elegir `Añadir lista cifrada recibida a Listas de Nexo`, revisar título y elementos, confirmar `Añadir a mis listas`. El contenido se vuelve a autenticar desde el almacén AES-GCM de Android Keystore ligado a cuenta/entrega, y se exige que el remitente siga fijado por QR y que el sobre protegido indique `list`. `NexoConnectPayloadCodec` sanea los metadatos otra vez.

La copia se crea en `lists` + `list_entries` nativas usando `NexoConnectNativeWriter`. Las entradas conservan texto, `checked`, `sortOrder` y categoría, así como título, clase, `reusable` y favorito de la lista. **En una única transacción SQLite de destino**, se escribe también un recibo `nexo_connect_native_list_promotions` por UUID de entrega, destinatario, remitente y hash del contenido. Repetir la acción devuelve la lista ya existente sin duplicar filas. Si se eliminó, no se vuelve a crear silenciosamente. Un fallo a mitad de la creación revierte todas las filas y el recibo. La fuente cifrada sigue intacta.

La copia ordinaria en Listas **no usa el cifrado adicional de Connect**; el usuario recibe una explicación y debe consentir explícitamente antes de crearla. Esto es equivalente a la copia nativa de una nota y no significa sincronización bidireccional permanente.

Se añadieron **6 pruebas instrumentadas** en `NexoConnectLocalListPromotionTest`: buzón vacío; consentimiento/2 elementos y atributos/replay; revocación; vinculación a cuenta/manipulación; rollback si falta tabla de entradas; y no recuperación silenciosa de una lista borrada.

**Estado:** código en PR experimental, **aún no probado localmente**. La cuenta del piloto independiente sigue en otro paquete Android y no se ha migrado; no pedir enrolar otra cuenta ni reemplazar el móvil. Pendiente: migración segura de identidades, importación nativa por los demás tipos, cifrado de adjuntos, UX de nombres amistosos, invitación simple y pruebas E2EE reales de listas entre dos cuentas integradas.

Desde `experiments/nexo-connect-android-vault` en PowerShell, usar `git pull --ff-only origin experiment/nexo-connect-phase0-crypto` y luego `powershell -NoProfile -ExecutionPolicy Bypass -File .\\run-local-tests.ps1 -IncludeNexoMain`. Esto ejecutará todas las pruebas Android instrumentadas en ambos AVD y compilará Nexo DEBUG y piloto localmente, sin GitHub Actions.


## 22/10/2026 — Consola de contactos menos técnica y corrección de esquema real

Tras instalar Nexo DEBUG, el usuario comprobó que **la lista local PAN/LECHE se conservaba correctamente**. Esto valida la persistencia tras actualización, no aún un envío E2EE de listas desde Nexo integrado.

Se detectó una discrepancia entre la tabla `items` del inbox privado de Connect en `NexoConnectPilotActivity.openPrivateDatabase()` y el lector de importación de listas: **el inbox no tiene la columna `deleted_at`**. Se eliminaron los filtros contra esa columna inexistente en `NexoConnectLocalListPromotion.list/read`, que mantienen el requisito de recibo `entity_type='list'`, origen cifrado `source='nexo-connect'`, marcador `nxc2:`, AES-GCM ligado a cuenta/entrega, tipo del sobre, QR pin local activo y consentimiento explícito. El fixture `NexoConnectLocalListPromotionTest` replica ahora el esquema de `openPrivateDatabase()` para impedir una falsa cobertura. Esta revisión **aún requiere ejecutar las pruebas instrumentadas locales**, sin GitHub Actions.

Se simplificaron, manteniendo compatibilidad con identidad y criptografía, los textos de `NexoConnectPilotActivity`: navegación con `Compartir una nota`, `Bandeja de entrada`, `Fichas recibidas`, `Personas autorizadas` e `Invitar a una persona`. Las huellas completas SHA-256 de cada contacto ya no saturan la pantalla principal: se consultan desde `Detalles de seguridad` cuando hace falta verificarlas. La pantalla del QR propio ofrece `Ver códigos de seguridad`, que despliega identificador, generación y las **dos** huellas completas; la comprobación independiente sigue siendo obligatoria antes de fijar claves. No se han alterado claves, sesión OTP, trust store, RPC, endpoint ni validación de huellas y consentimiento. Los contactos siguen distinguiéndose por identificadores cortos hasta implementar alias amigables ligados a una identidad comprobada.

**Limitación no resuelta:** la APK de Nexo y el piloto tienen paquetes separados, por lo que no comparten Android Keystore ni sesión. El enlace de producción todavía necesita transición segura y explícita (o un mecanismo puente autenticado diseñado para ello). No sustituir el dispositivo existente ni pedir otro registro solo para probar una UI.
