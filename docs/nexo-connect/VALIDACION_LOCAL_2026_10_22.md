# Nexo Connect: integración local verificada

Fecha: 22 de octubre de 2026.

## Implementación

- Recepción de notas, listas, citas sencillas, cuaderno, libros y películas desde Connect hacia sus tablas nativas de Nexo.
- Revalidación de cuenta, confianza QR, sobre AES-GCM y tipo de ficha antes de transferirla mediante el servicio protegido por firma.
- Tickets de un solo uso; ni claves ni contenido de fichas en los enlaces entre aplicaciones.
- Confirmación antes de crear la copia nativa, con aviso sobre su almacenamiento sin el cifrado adicional de Connect.
- Importación atómica e idempotente. Las listas conservan el registro de importaciones anterior. Las copias eliminadas no se recrean silenciosamente.
- Navegación desde el catálogo de Nexo al envío y acceso a la bandeja existente. Textos de Ajustes actualizados.
- Directorios de compilación separados para la biblioteca del piloto y la del host, evitando conflictos entre sus cachés incrementales.

## Comprobaciones realizadas

- Compilación local con JDK 17, SDK 34, Gradle 8.9 y AGP 8.7.3; sin GitHub Actions.
- 141 pruebas instrumentadas por emulador: Pixel_6, LAB_B y LAB_C. 423 ejecuciones sin fallos en la validación de la integración.
- Envío real de una nota sintética de Pixel_6 a LAB_B y LAB_C; confirmación del envío a dos personas.
- Notificaciones publicadas por Android en ambos receptores en segundo plano, tras forzar la ejecución puntual de la tarea 146801. Esto no verifica puntualidad del intervalo periódico.
- LAB_B recibe y confirma la nota cifrada, la copia a Notas de Nexo y muestra su título y contenido en la interfaz nativa.
- Segunda importación de la misma entrega: «La ficha ya estaba en Nexo».
- Desde Compartir en la nota nativa de LAB_B, envío a Pixel_6 mediante el puente, selección del contacto y confirmación. Pixel_6 recibe y confirma la ficha.
- Instalaciones mediante actualización, sin desinstalar, borrar datos ni sustituir identidades.

## Alcance y límites

Esta entrega sigue siendo DEBUG/LAB. Nexo Connect Pruebas debe permanecer instalado, con la misma firma que Nexo: las identidades y claves actuales continúan en su paquete. No constituye una migración a una única APK ni una publicación de producción.

Notas se verificó además con intercambio real e interfaz nativa. Los seis tipos se verifican mediante pruebas instrumentadas de escritura y deduplicación; no se afirma un intercambio real de cada uno de los seis tipos en esta sesión.

No se han incorporado adjuntos, chat, push instantáneo ni citas con características que el códec rechaza. Tampoco se ha fusionado o publicado la rama.

Repositorio de trabajo: `C:\Users\cjrui\StudioProjects\NEXO-Connect-Pruebas`, rama `experiment/nexo-connect-phase0-crypto`. La copia estable `NEXO-for-Android` no se ha modificado.
