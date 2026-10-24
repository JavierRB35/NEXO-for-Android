# Ficha de producto Nexo y política de actualizaciones

## Dónde aparece
Al tocar el logotipo superior se abre **Acerca de Nexo**. El catálogo de módulos y funciones se encuentra en `app/src/main/assets/nexo-product-catalog.js`; la interfaz solo lo presenta y no consulta datos privados ni accede a la red. En móvil, los grupos son desplegables.

## Contrato de mantenimiento en **cada release**
1. Confirmar qué módulos o funcionalidades se añaden, retiran o cambian en el código y en Herramientas.
2. Editar nombres, descripciones y capacidades en `nexo-product-catalog.js`. Documentar cada módulo con su estado real; si requiere cuenta o permisos, indicarlo. Evitar prometer funcionalidades previstas pero no implementadas.
3. Cambiar `versionName` del catálogo para que coincida con `app/build.gradle` y `.github/nexo-update-summary.json`; incrementar también `versionCode` como siempre.
4. Preservar `developer: "Carlos Javier Ruiz Bermúdez"` y `copyrightYear: 2026`.
5. Ejecutar `python validation/test-product-catalog.py` o `sh validation/validate-project.sh`. Las comprobaciones impiden publicar una APK nueva con inventario desactualizado y detectan módulos de Herramientas sin descripción.
6. Validar visualmente desde el logotipo la expansión de grupos y que el catálogo no sea un menú de acciones. Comprobar después que el build firmado publica el APK y el manifiesto de Updates.

## Alcance actual
La ficha incluye el inventario de Nexo y Nexo Connect, opciones opcionales y condiciones de permisos. No sustituye ni altera la guía de privacidad, el proceso de verificación de Nexo Connect ni las condiciones de instalación de una actualización.

## Ampliaciones futuras
Cuando se añada una herramienta fuera de la pantalla Herramientas (por ejemplo, funciones nuevas de Chat, Inbox o Calendario), revisar igualmente el apartado correspondiente del catálogo y reforzar la prueba de cobertura si procede. El validador no descubre automáticamente todas las funciones de la app: exige una revisión humana del contenido de cada versión.
