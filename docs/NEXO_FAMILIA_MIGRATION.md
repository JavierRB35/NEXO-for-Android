# Migración de eventos: Nexo Familia → Nexo Android

Nexo Android 3.7.0-beta1 incorpora una capa de compatibilidad local para recibir eventos exportados desde Nexo Familia.

## Principios

- La importación no borra los datos locales existentes.
- El UUID original del evento de Supabase se conserva.
- Reimportar el mismo UUID actualiza el registro local asociado en vez de duplicarlo.
- Se conserva el JSON de origen completo para no perder campos que Nexo Android todavía no representa visualmente.
- Las reglas de aviso de Nexo Familia se convierten en avisos locales cuando siguen pendientes.
- Los avisos locales creados manualmente en Nexo Android no se eliminan.

## Formato

```json
{
  "format": "nexo-familia-events-v1",
  "events": [],
  "reminderRules": []
}
```

Los eventos pueden contener el esquema completo de `public.events` de Nexo Familia. Se preservan, entre otros, identificadores, familia, creador, participantes, visibilidad, fechas, categoría, estado, recurrencia, zona horaria, metadatos sociales y el JSON original completo.

## Categorías canónicas

Familia; Cumpleaños; Santo / Onomástica; Salud / Médico; Colegio / Estudios; Trabajo; Viaje; Vacaciones; Casa; Vehículos / ITV; Seguros; Trámites; Tarea / Recado; Compras; Ocio / Social; Deporte; Recordatorio; Otro.

## Uso en el teléfono

Herramientas → Copia de seguridad → Importar Nexo Familia.

La importación es explícita: el usuario selecciona el archivo antes de incorporar los registros a la base local.
