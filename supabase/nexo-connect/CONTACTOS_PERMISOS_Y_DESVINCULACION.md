# Seguridad de desvinculación y permisos direccionales

El usuario puede permitir o pausar la recepción **por contacto** sin afectar la autorización de envío en la dirección contraria. Un cambio solo se acepta a través de `/contact/permissions` después de validar la cuenta JWT con Supabase, la firma del dispositivo registrado, el desafío no reutilizado y su generación.

La desvinculación (`/contact/unlink`) revoca el contacto, **borra la confianza de claves en ambas direcciones**, anula todas las entregas aún `pending` entre las partes y elimina su ciphertext de PostgreSQL. Los estados históricos mínimos `cancelled` permanecen para información/idempotencia. Las entregas ya importadas y guardadas en SQLite de un teléfono ajeno NO se pueden borrar remotamente.

Se añade un disparador de inserción sobre `connect.deliveries`: una fila de contacto activa se bloquea `FOR SHARE` antes de insertar, serializando con la revocación por `UPDATE` y evitando que aparezca una nueva entrega después de completar el unlink.

El SQL de cliente sin privilegios no puede llamar ninguna función administrativa. La UI debe avisar de que el destinatario podría haber importado ya copias locales y que, si se revinculan, ambas partes deben verificar de nuevo las claves. Este código no constituye aún una función completa de mensajería.