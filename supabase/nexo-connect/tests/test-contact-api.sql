\set ON_ERROR_STOP on
-- La migracion base ha creado A, B y C; la anterior prueba desvinculó A-B.
INSERT INTO auth.users(id) VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
INSERT INTO connect.profiles(user_id,display_name)
VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd','D');

DO $acl$
BEGIN
 IF has_function_privilege('authenticated','connect.api_create_invite(uuid,bytea,integer)','EXECUTE')
 OR has_function_privilege('anon','connect.api_accept_invite(uuid,bytea)','EXECUTE')
 OR has_function_privilege('authenticated','connect.api_set_receive_permission(uuid,uuid,boolean)','EXECUTE')
 OR has_function_privilege('authenticated','connect.api_register_device(uuid,uuid,bytea,bytea,bytea,bytea,boolean)','EXECUTE')
 THEN RAISE EXCEPTION 'Functions are exposed to clients'; END IF;
END $acl$;

SET ROLE authenticated;
DO $blocked$
BEGIN
 PERFORM connect.api_create_invite('11111111-1111-4111-8111-111111111111',
    decode(repeat('23',32),'hex'),3600);
 RAISE EXCEPTION 'Client executed service-only function';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $blocked$;
RESET ROLE;

-- Edge Function simulado: rol servidor de laboratorio. NO es un JWT real.
SET ROLE service_role;
SELECT connect.api_register_device(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 decode(repeat('09',32),'hex'),decode(repeat('18',32),'hex'),
 digest(decode(repeat('09',32),'hex'),'sha256'),
 digest(decode(repeat('18',32),'hex'),'sha256'),false) AS device_generation \gset

DO $initial$
BEGIN
 IF (SELECT generation FROM connect.devices
     WHERE user_id='dddddddd-dddd-4ddd-8ddd-dddddddddddd')<>1 THEN
   RAISE EXCEPTION 'Initial registration did not yield generation 1';
 END IF;
 BEGIN
   PERFORM connect.api_register_device(
   'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
   'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
   decode(repeat('09',32),'hex'),decode(repeat('18',32),'hex'),
   digest(decode(repeat('09',32),'hex'),'sha256'),
   digest(decode(repeat('18',32),'hex'),'sha256'),false);
   RAISE EXCEPTION 'Silently replaced existing device';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
END $initial$;

SELECT connect.api_register_device(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
 decode(repeat('0a',32),'hex'),decode(repeat('19',32),'hex'),
 digest(decode(repeat('0a',32),'hex'),'sha256'),
 digest(decode(repeat('19',32),'hex'),'sha256'),true) AS next_generation \gset

DO $replaced$
BEGIN
 IF (SELECT generation FROM connect.devices
     WHERE user_id='dddddddd-dddd-4ddd-8ddd-dddddddddddd')<>2
 OR (SELECT count(*) FROM connect.key_history
     WHERE user_id='dddddddd-dddd-4ddd-8ddd-dddddddddddd' AND revoked_at IS NOT NULL)<>1
 OR (SELECT count(*) FROM connect.key_history
     WHERE user_id='dddddddd-dddd-4ddd-8ddd-dddddddddddd' AND revoked_at IS NULL)<>1 THEN
   RAISE EXCEPTION 'Device replacement did not revoke previous generation';
 END IF;
 BEGIN
   PERFORM connect.api_register_device(
   'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
   'ffffffff-ffff-4fff-8fff-ffffffffffff',
   decode(repeat('0a',32),'hex'),decode(repeat('19',32),'hex'),
   decode(repeat('00',32),'hex'),digest(decode(repeat('19',32),'hex'),'sha256'),true);
   RAISE EXCEPTION 'Invalid fingerprint accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
END $replaced$;

-- A invita a C, C aprueba mediante el token QR; B no queda vinculado.
SELECT connect.api_create_invite(
 '11111111-1111-4111-8111-111111111111',
 digest('nexo-connect-test-token-unique','sha256'),3600) AS invite_id \gset
SELECT connect.api_accept_invite(
 '33333333-3333-4333-8333-333333333333',
 digest('nexo-connect-test-token-unique','sha256')) AS request_id \gset

DO $linked$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM connect.contacts
    WHERE user_a='11111111-1111-4111-8111-111111111111'
    AND user_b='33333333-3333-4333-8333-333333333333'
    AND status='active') THEN RAISE EXCEPTION 'Contact not linked'; END IF;
 IF (SELECT count(*) FROM connect.receive_permissions
    WHERE receiver_id IN ('11111111-1111-4111-8111-111111111111',
                          '33333333-3333-4333-8333-333333333333')
      AND sender_id IN ('11111111-1111-4111-8111-111111111111',
                        '33333333-3333-4333-8333-333333333333')
      AND allow_receive)<>2 THEN RAISE EXCEPTION 'Bilateral default permission absent'; END IF;
 BEGIN
   PERFORM connect.api_accept_invite('22222222-2222-4222-8222-222222222222',
     digest('nexo-connect-test-token-unique','sha256'));
   RAISE EXCEPTION 'Consumed invitation reused';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
END $linked$;

SELECT connect.api_set_receive_permission(
 '33333333-3333-4333-8333-333333333333',
 '11111111-1111-4111-8111-111111111111',false);
DO $direction$
BEGIN
 IF (SELECT allow_receive FROM connect.receive_permissions
     WHERE receiver_id='33333333-3333-4333-8333-333333333333'
       AND sender_id='11111111-1111-4111-8111-111111111111') IS DISTINCT FROM false
 OR (SELECT allow_receive FROM connect.receive_permissions
     WHERE receiver_id='11111111-1111-4111-8111-111111111111'
       AND sender_id='33333333-3333-4333-8333-333333333333') IS DISTINCT FROM true
 THEN RAISE EXCEPTION 'Direction-specific receiving permissions failed'; END IF;
 BEGIN
  PERFORM connect.api_set_receive_permission(
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',false);
  RAISE EXCEPTION 'Permission changed without contact';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
END $direction$;

SELECT connect.api_unlink_contact('33333333-3333-4333-8333-333333333333',
                                  '11111111-1111-4111-8111-111111111111');
DO $unlink$
BEGIN
 IF (SELECT status FROM connect.contacts
    WHERE user_a='11111111-1111-4111-8111-111111111111'
    AND user_b='33333333-3333-4333-8333-333333333333') <> 'revoked'
 THEN RAISE EXCEPTION 'Contact not revoked'; END IF;
 BEGIN
   PERFORM connect.api_set_receive_permission(
   '33333333-3333-4333-8333-333333333333',
   '11111111-1111-4111-8111-111111111111',true);
   RAISE EXCEPTION 'Unlinked contact accepted permission change';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
END $unlink$;
RESET ROLE;

-- Tras desvincular A-C, C vuelve a estar aislado de los datos de A y B.
SET ROLE authenticated;
SET request.jwt.claim.sub='33333333-3333-4333-8333-333333333333';
DO $isolation$
BEGIN
 IF (SELECT count(*) FROM connect.profiles)<>1
 OR (SELECT count(*) FROM connect.devices)<>1
 OR (SELECT count(*) FROM connect.receive_permissions)<>0 THEN
   RAISE EXCEPTION 'Post-unlink RLS isolation failed';
 END IF;
END $isolation$;
RESET ROLE;
\echo NEXO CONNECT: OTP-GATEWAY-CONTRACT / DEVICES / INVITES / PERMISSIONS TESTS OK
