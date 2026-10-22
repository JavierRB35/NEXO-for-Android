# Nexo Connect: esquema de cuentas y contactos (Fase 1, pruebas offline)

**No existe todavía un servicio Nexo Connect desplegado en Supabase.**
Los archivos de esta carpeta son un diseño SQL listo para validar en una base
PostgreSQL de laboratorio, NO migraciones aplicadas a Nexo Familia.

## Qué cubre

- Perfiles privados y claves públicas del único dispositivo principal por cuenta.
- Relaciones bilaterales con pares ordenados, solicitudes e invitaciones de un solo uso.
- Permiso direccional para aceptar/rechazar nuevas entregas de un contacto.
- Historial de claves públicas y registros de generaciones.
- RLS para impedir que un tercero vea cuentas, claves o contactos de otros.
- Los clientes autenticados **solo tienen SELECT controlado**; Edge Functions
  deberán implementar escrituras con validaciones de identidad, firma, OTP,
  generación de dispositivo, límites de uso y permiso de recepción.
- **Las invitaciones ni siquiera exponen SELECT de hashes a clientes.**

## Ejecución segura de pruebas

GitHub Actions levanta PostgreSQL 16 aislado con tres identidades falsas.
La migración se aplica allí para probar selectividad de RLS y revocación.
La contraseña en YAML es solo de la base de datos temporal del job, no es real.

## Falta para activar un entorno Supabase

1. Crear un proyecto **distinto** de Nexo Familia, con región y cuotas definidas.
2. Configurar OTP por correo y límites antifraude en Auth.
3. Configurar el esquema `connect` en los esquemas expuestos por la API, si se
   requieren lecturas directas con JWT; de lo contrario servir todo mediante
   Edge Functions que gestionen lecturas y escrituras.
4. Diseñar Edge Functions y pruebas negativas de JWT falso, usuario C, replay,
   revocación de dispositivo y sustitución de claves.
5. Añadir entregas, Storage y políticas de retención **solo después** de que
   la identidad/autorización sean correctas.

**No aplicar a producción sin revisión de seguridad y sin aprobación explícita.**
