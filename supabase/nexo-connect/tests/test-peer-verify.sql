\set ON_ERROR_STOP on
DO $priv$
BEGIN
 IF has_function_privilege('authenticated',
   'public.nexo_connect_verify_peer(uuid,uuid,bigint,uuid,bigint,bytea,bytea)','EXECUTE')
  OR has_function_privilege('anon',
   'public.nexo_connect_verify_peer(uuid,uuid,bigint,uuid,bigint,bytea,bytea)','EXECUTE')
 THEN RAISE EXCEPTION 'Unauthorized key trust RPC'; END IF;
END $priv$;
SET ROLE service_role;
DO $test$
BEGIN
 -- Fixture from test-contact-accept: A and F linked; F verified A; A has not verified F.
 IF EXISTS(SELECT 1 FROM connect.peer_key_approvals WHERE
   owner_id='11111111-1111-4111-8111-111111111111' AND
   peer_id='66666666-6666-4666-8666-666666666666') THEN
   RAISE EXCEPTION 'Fixture already verified without QR'; END IF;
 BEGIN
  PERFORM public.nexo_connect_verify_peer(
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '66666666-6666-4666-8666-666666666666',1,
   decode(repeat('00',32),'hex'),decode(repeat('d6',32),'hex'));
  RAISE EXCEPTION 'Incorrect peer fingerprint accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_verify_peer(
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',2,
   '66666666-6666-4666-8666-666666666666',1,
   extensions.digest(decode(repeat('c6',32),'hex'),'sha256'),
   extensions.digest(decode(repeat('d6',32),'hex'),'sha256'));
  RAISE EXCEPTION 'Revoked owner phone approved keys';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 PERFORM public.nexo_connect_verify_peer(
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '66666666-6666-4666-8666-666666666666',1,
   extensions.digest(decode(repeat('c6',32),'hex'),'sha256'),
   extensions.digest(decode(repeat('d6',32),'hex'),'sha256'));
 IF NOT EXISTS (SELECT 1 FROM connect.peer_key_approvals
   WHERE owner_id='11111111-1111-4111-8111-111111111111'
     AND peer_id='66666666-6666-4666-8666-666666666666'
     AND peer_generation=1) THEN
   RAISE EXCEPTION 'Reciprocal key approval not persisted'; END IF;
 BEGIN
  PERFORM public.nexo_connect_verify_peer(
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   'dddddddd-dddd-4ddd-8ddd-dddddddddddd',2,
   extensions.digest(decode(repeat('0a',32),'hex'),'sha256'),
   extensions.digest(decode(repeat('19',32),'hex'),'sha256'));
  RAISE EXCEPTION 'Unlinked account approved';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $test$;
RESET ROLE;
\echo NEXO CONNECT: RECIPROCAL KEY APPROVAL / RLS TESTS OK
