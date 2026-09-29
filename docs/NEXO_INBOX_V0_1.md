# Nexo Inbox v0.1 — diseño de implementación

Fecha: 2026-09-29  
Rama: `feature/nexo-inbox-v0.1`  
Base analizada: Nexo 3.4.9 · DB schema 14 · Android SDK 34 · AGP 8.7.3 · Gradle 8.9

## 1. Objetivo

Convertir el flujo actual de captura de Nexo en una bandeja de entrada universal:

**capturar → conservar → interpretar → revisar → convertir en acción**

La v0.1 debe aprovechar la arquitectura existente y no reescribir Nexo.

## 2. Lo que Nexo ya tiene

La base actual ya contiene piezas importantes:

- Android Share Intent para texto, HTML, imágenes y PDF.
- Copia de adjuntos al almacenamiento interno.
- Clasificación local de texto.
- Extracción de fecha, hora, ubicación, recurrencia y categoría.
- Campo `needs_review` en `items`.
- Panel “Pendiente de organizar”.
- Documentos, lugares, notas, citas, listas, medicación y grabaciones.
- Transcripción local mediante Sherpa/Whisper.
- Búsqueda local.
- Copia de seguridad propia.
- WebView local con red bloqueada.

Por tanto, Nexo Inbox será una evolución del producto actual y no una aplicación separada.

## 3. Principios de v0.1

1. **Nada compartido debe perderse.**
2. Compartir a Nexo no debe crear automáticamente citas, recordatorios ni acciones irreversibles.
3. El contenido original debe conservarse íntegro.
4. La interpretación automática es una propuesta editable.
5. El procesamiento de v0.1 seguirá siendo local.
6. No se añade un LLM obligatorio.
7. No se introducen Accessibility, Shizuku ni automatización profunda.
8. No se modifica JDK, SDK, Gradle ni AGP.
9. No se migra toda la base de datos a Room en esta fase.

## 4. Flujo funcional

### 4.1 Entrada desde Android

El usuario comparte:

- texto,
- URL,
- imagen,
- PDF,
- HTML,
- otro documento compatible.

`MainActivity.handleIncomingIntent()` recibe el contenido.

En lugar de depender de que el usuario complete inmediatamente el formulario, Nexo debe:

1. copiar el adjunto al almacenamiento interno cuando exista;
2. guardar un registro de captura;
3. ejecutar la interpretación local;
4. mostrar una ficha de revisión;
5. permitir convertirla en un elemento existente de Nexo.

### 4.2 Estados de una captura

- `new`: recibida, todavía no revisada;
- `reviewing`: abierta en la ficha de revisión;
- `converted`: convertida a contenido definitivo de Nexo;
- `discarded`: descartada por el usuario.

No se elimina inmediatamente una captura convertida. Se conserva la trazabilidad mediante el identificador del elemento creado.

## 5. Modelo de datos

Se añadirá una tabla independiente para evitar mezclar entradas crudas con elementos confirmados.

### `inbox_captures`

| Campo | Tipo | Uso |
|---|---|---|
| id | INTEGER PK | Identificador |
| source | TEXT | android-share, voice, manual, file |
| mime | TEXT | MIME recibido |
| subject | TEXT | Asunto original |
| raw_text | TEXT | Texto original sin modificar |
| attachment_path | TEXT | Archivo interno |
| attachment_name | TEXT | Nombre original |
| attachment_mime | TEXT | MIME del adjunto |
| detected_type | TEXT | Tipo sugerido |
| detected_title | TEXT | Título sugerido |
| classification_json | TEXT | Resultado completo del clasificador |
| status | TEXT | new/reviewing/converted/discarded |
| converted_entity | TEXT | item/document/place/list/... |
| converted_id | INTEGER | ID del contenido definitivo |
| created_at | INTEGER | Creación |
| updated_at | INTEGER | Última modificación |

Índices:

- `status, created_at`
- `converted_entity, converted_id`

La versión de esquema pasará de 14 a 15 cuando se implemente.

## 6. Pantallas

### 6.1 Inicio

El acceso actual “Por organizar” se mantiene, pero pasa a mostrar el número de capturas pendientes.

Texto recomendado:

**Inbox**  
“Contenido recibido que todavía no has organizado.”

### 6.2 Inbox

Cada tarjeta mostrará:

- origen;
- fecha/hora de captura;
- título detectado;
- fragmento del contenido original;
- tipo sugerido;
- icono de adjunto cuando exista.

Acciones:

- **Revisar**
- **Descartar**

No habrá creación automática desde la lista.

### 6.3 Revisión de captura

La ficha mostrará dos bloques claramente separados.

**Original**

- texto recibido;
- archivo/imagen/PDF;
- origen;
- fecha de captura.

**Nexo ha entendido**

- tipo;
- título;
- descripción;
- fecha/hora;
- ubicación;
- categoría;
- recurrencia;
- posibles avisos.

El usuario podrá corregir todos los campos antes de confirmar.

Acción principal:

**Guardar en Nexo**

Tras guardar:

- se crea el elemento usando los métodos existentes;
- se marca la captura como `converted`;
- se almacena el tipo e ID de destino.

## 7. Tipos de conversión en v0.1

La primera versión debe poder convertir a:

- cita/evento;
- tarea;
- recordatorio;
- nota;
- documento;
- lugar/restaurante.

Medicación, listas inteligentes y otras conversiones se mantienen fuera de la primera iteración si requieren lógica especial.

## 8. Integración con código existente

### MainActivity

Se mantendrá el Share Intent actual.

Se añadirá una operación nativa equivalente a:

- `saveInboxCapture(payload)`
- `getInboxCaptures()`
- `getInboxCapture(id)`
- `updateInboxCapture(id,...)`
- `convertInboxCapture(id,payload)`
- `discardInboxCapture(id)`

### DatabaseHelper

Responsabilidades nuevas:

- crear/migrar `inbox_captures`;
- CRUD de capturas;
- conteo de pendientes;
- mantener trazabilidad de conversiones.

### NexoService

La clasificación existente seguirá siendo el motor inicial.

La capa futura de IA debe poder sustituir o complementar la clasificación sin modificar la UI.

Interfaz conceptual futura:

```
AIProvider
 ├─ LocalRulesProvider
 ├─ CloudProvider
 └─ LiteRTProvider
```

No se implementará todavía un proveedor cloud ni se almacenarán claves API.

## 9. Búsqueda

En v0.1:

- las capturas pendientes no se mezclarán con los resultados normales;
- serán accesibles desde Inbox;
- una vez convertidas, el contenido definitivo aparecerá en la búsqueda actual.

Una búsqueda global sobre capturas se evaluará después.

## 10. Adjuntos

Se mantiene el mecanismo actual de almacenamiento interno.

Reglas:

- una captura conserva su adjunto mientras esté pendiente;
- al convertirla, el contenido definitivo reutiliza el mismo archivo cuando sea posible;
- al descartar, solo se elimina el archivo si ninguna otra entidad lo referencia;
- la copia de seguridad deberá incluir adjuntos vinculados a capturas pendientes.

## 11. Seguridad y privacidad

La v0.1 no necesita enviar información fuera del dispositivo.

Se mantienen:

- `android:usesCleartextTraffic="false"`;
- WebView sin cargas de red;
- proveedores de archivos no exportados;
- almacenamiento interno;
- revisión humana antes de ejecutar acciones.

No se añadirán permisos nuevos en esta fase salvo que una funcionalidad concreta lo justifique.

## 12. Compatibilidad AndroidIDE

Se conserva el baseline validado:

- JDK 17
- compileSdk 34
- targetSdk 34
- Build Tools 34.0.4
- Gradle 8.9
- AGP 8.7.3
- ARM64
- `sh ./gradlew`
- AAPT2 de AndroidIDE
- `--no-daemon --max-workers=1`

No se introduce ninguna dependencia pesada en la primera fase.

## 13. Fases de implementación

### Fase A — persistencia

- [ ] DB v15.
- [ ] Tabla `inbox_captures`.
- [ ] CRUD nativo.
- [ ] Captura persistente desde Share Intent.
- [ ] Conteo de Inbox.

### Fase B — interfaz

- [ ] Panel Inbox con tarjetas.
- [ ] Ficha de revisión.
- [ ] Visualización del contenido original.
- [ ] Edición de propuesta interpretada.
- [ ] Descartar captura.

### Fase C — conversión

- [ ] Convertir a cita/tarea/recordatorio/nota.
- [ ] Convertir documentos.
- [ ] Convertir lugares.
- [ ] Mantener trazabilidad.

### Fase D — robustez

- [ ] Backup/restore de capturas.
- [ ] Limpieza segura de adjuntos.
- [ ] Pruebas de Share Intent.
- [ ] Pruebas tras cierre forzado.
- [ ] Pruebas tras reinicio.
- [ ] Pruebas de duplicados.

## 14. Criterios de aceptación del MVP

La v0.1 estará lista cuando:

1. compartir contenido hacia Nexo lo conserve aunque la app se cierre inmediatamente;
2. todas las capturas aparezcan en Inbox;
3. ninguna captura cree acciones sin confirmación;
4. el usuario pueda revisar y corregir la interpretación;
5. una captura pueda convertirse en un elemento normal de Nexo;
6. el contenido original permanezca trazable;
7. el proyecto siga compilando con el flujo AndroidIDE validado;
8. no haya regresiones en citas, recordatorios, documentos, grabaciones o búsqueda.

## 15. Fuera de alcance

Para versiones posteriores:

- embeddings;
- búsqueda semántica;
- memoria de entidades;
- LLM local;
- proveedor cloud;
- MCP;
- Accessibility;
- Shizuku;
- agente autónomo;
- OCR avanzado de documentos;
- extracción multimodal con VLM.

Estas funciones deben añadirse después de estabilizar el Inbox como capa de captura.
