# Nexo con Chat integrado — 22 de octubre de 2026

Archivo de entrega: **Nexo-unificada-chat.apk**. Es la APK de pruebas compilada localmente e instalada en Pixel_6, NexoConnect_LAB_B y NexoConnect_LAB_C. No se han utilizado GitHub Actions.

- Nexo abre sin registro. La cuenta se pide al activar Chat o compartir mediante Nexo.
- Chat está en Herramientas → Aplicaciones. Las personas aparecen con su nombre elegido.
- Se pueden compartir invitaciones como archivos `.nexo-invite`. El destinatario necesita instalar Nexo; al abrir la invitación puede crear su cuenta o entrar. Ambas personas deben confirmar la conexión y sus códigos de seguridad antes de compartir. La invitación caduca en una hora.
- Las conversaciones y nombres se incluyen en la copia de seguridad. El chat se protege con una contraseña de al menos ocho caracteres, necesaria para restaurarlo. La copia no transporta las credenciales de acceso ni las claves de identidad. El historial se recupera sin duplicar mensajes; las conversaciones guardadas pueden consultarse sin iniciar sesión.
- El icono fijo de Inbox muestra el total de elementos pendientes de organizar. La insignia desaparece cuando el total es cero.
- Las fichas recibidas pueden copiarse directamente a su sección en Nexo.

## Comprobaciones realizadas

- 147 pruebas instrumentadas en cada emulador: **441 correctas**. Incluyen cifrado, confianza, recepción, importación y recuperación del chat; contraseña incorrecta, manipulación y restauración repetida.
- Traslado real de las tres cuentas existentes, conservando identidad y personas. Connect queda desactivada en los emuladores, con sus datos conservados.
- Mensajes reales identificados como «Prueba de integracion» en ambos sentidos entre Javi y Ana LAB, y entre Javi y Luis LAB.
- Copia `.nexo` creada y restaurada realmente en Pixel_6. Se verificó la presencia del historial cifrado y la persistencia de los mensajes.
- Contador de Inbox comprobado con 0, 1, 12 y 237 elementos, incluido el texto accesible. El contador usa el total completo, no el límite de filas mostrado.
- Compilación final local correcta, instalación de la misma APK en los tres emuladores. Se mantienen JDK 17, SDK 34, AGP 8.7.3 y Gradle 8.9, además del script de compilación para AndroidIDE.

## Alcance de esta entrega

Esta APK utiliza la firma de desarrollo de las pruebas. La clave de publicación no está disponible en este ordenador; no se ha generado ni publicado una versión firmada de distribución. Una instalación existente con otra firma no se puede actualizar con esta APK: no debe desinstalarse para forzarlo.

Los avisos en segundo plano dependen de las comprobaciones periódicas de Android; no son notificaciones instantáneas. Con la conversación abierta se actualiza el chat periódicamente.

La validación de invitaciones cubre el código y su compilación. El intercambio real se comprobó con las tres cuentas ya existentes; no se creó una cuarta cuenta por correo para ensayar un alta nueva completa.

La copia usada en la comprobación está en Descargas de Pixel_6, con nombre `Nexo_Backup_2026-10-22_1827.nexo.zip`. Su contraseña temporal de comprobación es `PruebaNexo2026`. Cada copia nueva permite elegir su propia contraseña.
