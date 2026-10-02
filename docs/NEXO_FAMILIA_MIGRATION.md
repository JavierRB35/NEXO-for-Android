# Migración de eventos: Nexo Familia → Nexo Android

Nexo Android 3.7.0-beta2 incorpora compatibilidad real de eventos y una migración inicial automática del calendario familiar actual.

## Qué se migra

- 33 eventos actuales de la familia Ruiz-Villena.
- 46 reglas de aviso asociadas.
- Título, descripción y categoría.
- Fecha y hora de inicio.
- Fecha y hora de fin.
- Todo el día.
- Ubicación y enlace de Google Maps.
- Estado.
- Recurrencia y RRULE original.
- Zona horaria.
- Participantes, cuando aportan contexto local.

Los UUID originales de Supabase y el JSON completo de origen se conservan en las tablas de compatibilidad. Campos estrictamente familiares como permisos administrativos, visibilidad o metadatos sociales se preservan para futuras migraciones/sincronización, pero no recargan el formulario local si no aportan utilidad a Nexo Android.

## Lógica temporal

- Los eventos no recurrentes pasan a completados cuando su final queda en el pasado.
- Los recurrentes permanecen activos para sus siguientes apariciones.
- Los cancelados permanecen cancelados.
- Una recurrencia sin fecha final es indefinida.
- Los eventos de varios días aparecen en cada día cubierto por el intervalo.
- Los avisos pendientes se convierten en alarmas locales cuando siguen siendo futuros.

## Idempotencia

El UUID de Supabase identifica el registro. Reprocesar el mismo snapshot actualiza el evento local enlazado y no crea duplicados.

## Migración automática de esta beta

En el primer arranque de 3.7.0-beta2 se procesa el snapshot privado incluido para esta migración. Solo se marca como completado cuando se han enlazado todos los eventos previstos sin errores; si falla, se vuelve a intentar en el siguiente arranque.

El importador manual permanece disponible en Herramientas → Copia de seguridad → Importar Nexo Familia para futuras exportaciones.

## Privacidad

El snapshot incluido en esta beta contiene datos del calendario familiar y está destinado únicamente a la migración privada del propietario. Debe retirarse del paquete antes de distribuir públicamente Nexo.
