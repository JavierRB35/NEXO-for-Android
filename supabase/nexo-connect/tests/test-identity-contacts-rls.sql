\set ON_ERROR_STOP on
INSERT INTO auth.users(id) VALUES
 ('11111111-1111-4111-8111-111111111111'),
 ('22222222-2222-4222-8222-222222222222'),
 ('33333333-3333-4333-8333-333333333333');
INSERT INTO connect.profiles(user_id, display_name) VALUES
 ('11111111-1111-4111-8111-111111111111','A'),
 ('22222222-2222-4222-8222-222222222222','B'),
 ('33333333-3333-4333-8333-333333333333','C');
INSERT INTO connect.devices(user_id,device_id,generation,signing_public_key,encryption_public_key,signing_fingerprint,encryption_fingerprint)
SELECT id, id,1,decode(repeat('ab',32),'hex'),decode(repeat('cd',32),'hex'),
    decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex') FROM auth.users;
INSERT INTO connect.contacts(user_a,user_b,status) VALUES
 ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','active');
INSERT INTO connect.receive_permissions(receiver_id,sender_id,allow_receive) VALUES
 ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222',true),
 ('22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111',false);
INSERT INTO connect.key_history(user_id,generation,signing_fingerprint,encryption_fingerprint)
SELECT id,1,decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex') FROM auth.users;
INSERT INTO connect.contact_requests(id,requester_id,recipient_id,status,expires_at)
VALUES ('44444444-4444-4444-8444-444444444444','11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','pending',now()+interval '1 hour');
INSERT INTO connect.contact_invites(id,creator_id,token_sha256,expires_at)
VALUES ('55555555-5555-4555-8555-555555555555','11111111-1111-4111-8111-111111111111',decode(repeat('44',32),'hex'),now()+interval '1 hour');

DO $checks$
BEGIN
 IF has_table_privilege('anon','connect.profiles','SELECT')
   OR has_table_privilege('authenticated','connect.devices','INSERT')
   OR has_table_privilege('authenticated','connect.contacts','UPDATE')
   OR has_table_privilege('authenticated','connect.contact_invites','SELECT')
   OR has_table_privilege('authenticated','connect.receive_permissions','DELETE')
 THEN RAISE EXCEPTION 'Privilegios concedidos indebidamente'; END IF;
END $checks$;

SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
DO $a$
BEGIN
 IF (SELECT count(*) FROM connect.profiles)<>2 THEN RAISE EXCEPTION 'A: perfiles expuestos'; END IF;
 IF (SELECT count(*) FROM connect.devices)<>2 THEN RAISE EXCEPTION 'A: claves de ajenos expuestas'; END IF;
 IF (SELECT count(*) FROM connect.contacts)<>1 THEN RAISE EXCEPTION 'A: contactos incorrectos'; END IF;
 IF (SELECT count(*) FROM connect.receive_permissions)<>2 THEN RAISE EXCEPTION 'A: permisos incorrectos'; END IF;
 IF (SELECT count(*) FROM connect.key_history)<>2 THEN RAISE EXCEPTION 'A: historial incorrecto'; END IF;
 IF (SELECT count(*) FROM connect.contact_requests)<>1 THEN RAISE EXCEPTION 'A: solicitudes incorrectas'; END IF;
END $a$;
RESET ROLE;

SET ROLE authenticated;
SET request.jwt.claim.sub = '33333333-3333-4333-8333-333333333333';
DO $c$
BEGIN
 IF (SELECT count(*) FROM connect.profiles)<>1 THEN RAISE EXCEPTION 'C: perfiles ajenos'; END IF;
 IF (SELECT count(*) FROM connect.devices)<>1 THEN RAISE EXCEPTION 'C: claves ajenas'; END IF;
 IF (SELECT count(*) FROM connect.contacts)<>0 THEN RAISE EXCEPTION 'C: contactos ajenos'; END IF;
 IF (SELECT count(*) FROM connect.receive_permissions)<>0 THEN RAISE EXCEPTION 'C: permisos ajenos'; END IF;
 IF (SELECT count(*) FROM connect.key_history)<>1 THEN RAISE EXCEPTION 'C: historial ajeno'; END IF;
 IF (SELECT count(*) FROM connect.contact_requests)<>0 THEN RAISE EXCEPTION 'C: solicitudes ajenas'; END IF;
END $c$;
RESET ROLE;

-- Desvinculación revoca inmediatamente visibilidad mutua aunque no se eliminen filas.
UPDATE connect.contacts SET status='revoked';
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
DO $rev$
BEGIN
 IF (SELECT count(*) FROM connect.profiles)<>1 THEN RAISE EXCEPTION 'Revocación de perfil fallida'; END IF;
 IF (SELECT count(*) FROM connect.devices)<>1 THEN RAISE EXCEPTION 'Revocación de claves fallida'; END IF;
 IF (SELECT count(*) FROM connect.receive_permissions)<>0 THEN RAISE EXCEPTION 'Revocación de permisos fallida'; END IF;
END $rev$;
RESET ROLE;
\echo RLS NEXO CONNECT: TESTS OK
