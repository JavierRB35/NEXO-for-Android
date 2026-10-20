# Nexo Connect — Entorno Supabase independiente (20 octubre 2026)

## Estado real

- **Proyecto:** Nexo Connect, ref `fwvmoqrsqtpvpbyfclld`.
- **Organización:** la misma de la cuenta del desarrollador, separado de Nexo Familia.
- **Región:** `eu-central-1`.
- **Cuota de creación:** 0 USD/mes adicionales según cotización de la herramienta.
- **Nexo Familia** (`gimzsrreghmuuwmizojc`): pausado a petición expresa del propietario.
- **Nexo Family SaaS Staging** (`nkzqkcqfxelvnacitmxg`): sin cambios.

## Migraciones aplicadas al nuevo proyecto

| Migración | Nombre Supabase | Estado |
|---|---|---|
| `202610200001_identity_contacts.sql` | `nexo_connect_identity_contacts` | aplicada |
| `202610200002_contact_api.sql` | `nexo_connect_device_contact_api` | aplicada |
| `202610200003_deliveries.sql` | `nexo_connect_e2ee_deliveries` | aplicada |
| `202610200004_device_challenges.sql` | `nexo_connect_device_challenges` | aplicada |
| `202610200005_indexes.sql` | `nexo_connect_foreign_key_indexes` | aplicada |

**Todas las tablas del esquema `connect` están vacías y tienen RLS activo.**

## Informe del asesor de seguridad

- Solo dos avisos **INFO** de RLS sin políticas para `contact_invites` y `device_challenges`. **Intencional:** el cliente no debe leer estas tablas; solo se usan mediante backend autenticado.
- Cinco avisos iniciales de claves foráneas sin índices: corregidos con migración 0005.
- Índices no usados: normales en tablas vacías, no eliminarlos por ese aviso.

## Reglas de puesta en servicio

1. No reutilizar proyecto Nexo Familia ni exponer credenciales `service_role` en Android o GitHub.
2. No habilitar aún solicitudes de envío/recepción reales hasta completar backend OTP, challenge Ed25519 y antifraude.
3. No marcar como verificada una clave ajena solo porque el servidor devuelve una huella: hace falta verificación real QR por el contacto.
4. Exigir autorización del destinatario y recepción no pausada en cada envío.
5. Cifrar documentos antes de subirlos a Storage; las pruebas actuales con adjuntos se realizan únicamente en memoria.
6. Hacer pruebas end-to-end automatizadas con al menos dos identidades antes de publicar.
7. Mantener PR #65 en borrador hasta que exista implementación Android, revisión de seguridad y versión final funcional.

## Restricciones de la cuenta

La cuenta gratuita admite dos proyectos activos. Volver a activar Nexo Familia
sin cambiar de plan requerirá liberar otra plaza. La pausa actual no elimina
sus datos, pero afecta a la sincronización hasta su restauración.

**No guardar jamás claves administrativas, OTP, contraseñas ni tokens de acceso
en esta documentación.**
