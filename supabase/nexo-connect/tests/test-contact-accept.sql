\set ON_ERROR_STOP on
INSERT INTO auth.users(id) VALUES('66666666-6666-4666-8666-666666666666');
SET ROLE service_role;
SELECT public.nexo_connect_enroll_device(
 '66666666-6666-4666-8666-666666666666',
 '67676767-6767-4767-8767-676767676767',
 decode(repeat('c6',32),'hex'),decode(repeat('d6',32),'hex'),false);
SELECT connect.api_create_invite(
 '11111111-1111-4111-8111-111111111111',
 decode(repeat('ad',32),'hex'),3600);
DO $verify_acceptance$
DECLARE id uuid;
BEGIN
 BEGIN
  PERFORM public.nexo_connect_accept_invite(
   '66666666-6666-4666-8666-666666666666',
   '67676767-6767-4767-8767-676767676767',1,
   '11111111-1111-4111-8111-111111111111',1,
   decode(repeat('ad',32),'hex'),
   decode(repeat('ff',32),'hex'),decode(repeat('ff',32),'hex'));
  RAISE EXCEPTION 'Incorrect QR signing fingerprint was accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 IF EXISTS(SELECT 1 FROM connect.contacts WHERE
     user_a='11111111-1111-4111-8111-111111111111' AND
     user_b='66666666-6666-4666-8666-666666666666')
 THEN RAISE EXCEPTION 'Rejected QR created contact'; END IF;
 BEGIN
  PERFORM public.nexo_connect_accept_invite(
   '66666666-6666-4666-8666-666666666666',
   '67676767-6767-4767-8767-676767676767',2,
   '11111111-1111-4111-8111-111111111111',1,
   decode(repeat('ad',32),'hex'),
   decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex'));
  RAISE EXCEPTION 'Revoked recipient generation accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 id:=public.nexo_connect_accept_invite(
   '66666666-6666-4666-8666-666666666666',
   '67676767-6767-4767-8767-676767676767',1,
   '11111111-1111-4111-8111-111111111111',1,
   decode(repeat('ad',32),'hex'),
   decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex'));
 IF id IS NULL OR NOT EXISTS(
    SELECT 1 FROM connect.peer_key_approvals
    WHERE owner_id='66666666-6666-4666-8666-666666666666'
      AND peer_id='11111111-1111-4111-8111-111111111111'
      AND peer_generation=1)
 THEN RAISE EXCEPTION 'Recipient key pin missing'; END IF;
 IF EXISTS (SELECT 1 FROM connect.peer_key_approvals
    WHERE owner_id='11111111-1111-4111-8111-111111111111'
      AND peer_id='66666666-6666-4666-8666-666666666666') THEN
  RAISE EXCEPTION 'Creator was made to trust unverified recipient'; END IF;
 BEGIN
  PERFORM public.nexo_connect_accept_invite(
   '66666666-6666-4666-8666-666666666666',
   '67676767-6767-4767-8767-676767676767',1,
   '11111111-1111-4111-8111-111111111111',1,
   decode(repeat('ad',32),'hex'),
   decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex'));
  RAISE EXCEPTION 'Invitation reuse allowed';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $verify_acceptance$;
RESET ROLE;
DO $privileges$
BEGIN
 IF has_function_privilege('anon',
    'public.nexo_connect_accept_invite(uuid,uuid,bigint,uuid,bigint,bytea,bytea,bytea)','EXECUTE')
 OR has_function_privilege('authenticated',
    'public.nexo_connect_accept_invite(uuid,uuid,bigint,uuid,bigint,bytea,bytea,bytea)','EXECUTE')
 THEN RAISE EXCEPTION 'Client can approve arbitrary QR fingerprints'; END IF;
END $privileges$;
\echo NEXO CONNECT: QR VERIFIED CONTACT ACCEPTANCE TESTS OK
