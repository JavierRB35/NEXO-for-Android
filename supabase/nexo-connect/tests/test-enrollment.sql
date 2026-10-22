\set ON_ERROR_STOP on
INSERT INTO auth.users(id) VALUES ('99999999-9999-4999-8999-999999999999');
DO $permissions$
BEGIN
 IF has_function_privilege('authenticated',
   'public.nexo_connect_enroll_device(uuid,uuid,bytea,bytea,boolean)','EXECUTE')
   OR has_function_privilege('anon',
   'public.nexo_connect_enroll_device(uuid,uuid,bytea,bytea,boolean)','EXECUTE')
 THEN RAISE EXCEPTION 'Client can enroll without fresh OTP gateway'; END IF;
END $permissions$;
SET ROLE service_role;
DO $enrollment$
DECLARE g bigint;
BEGIN
 g:=public.nexo_connect_enroll_device(
  '99999999-9999-4999-8999-999999999999',
  '91919191-9191-4919-8919-919191919191',
  decode(repeat('a1',32),'hex'),decode(repeat('b1',32),'hex'),false);
 IF g<>1 THEN RAISE EXCEPTION 'Registration generation not 1'; END IF;
 BEGIN
  PERFORM public.nexo_connect_enroll_device(
   '99999999-9999-4999-8999-999999999999',
   '91919191-9191-4919-8919-919191919191',
   decode(repeat('a1',32),'hex'),decode(repeat('b1',32),'hex'),false);
  RAISE EXCEPTION 'Duplicate registration succeeded';
 EXCEPTION WHEN unique_violation THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_enroll_device(
   '99999999-9999-4999-8999-999999999999',
   '92929292-9292-4929-8929-929292929292',
   decode(repeat('a1',32),'hex'),decode(repeat('b2',32),'hex'),true);
  RAISE EXCEPTION 'Key reuse permitted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $enrollment$;
RESET ROLE;
-- establish a pinned trust from test account E to B and an old challenge
INSERT INTO connect.contacts(user_a,user_b,status) VALUES(
 '22222222-2222-4222-8222-222222222222',
 '99999999-9999-4999-8999-999999999999','active');
INSERT INTO connect.peer_key_approvals(
 owner_id,peer_id,peer_generation,verified_signing_fp,verified_encryption_fp)
SELECT '99999999-9999-4999-8999-999999999999',user_id,generation,
 signing_fingerprint,encryption_fingerprint FROM connect.devices
 WHERE user_id='22222222-2222-4222-8222-222222222222';
INSERT INTO connect.device_challenges(
 user_id,device_id,generation,action)
VALUES('99999999-9999-4999-8999-999999999999',
 '91919191-9191-4919-8919-919191919191',1,'contact/invite');
SET ROLE service_role;
DO $replace$
DECLARE g bigint;
BEGIN
 g:=public.nexo_connect_enroll_device(
 '99999999-9999-4999-8999-999999999999',
 '92929292-9292-4929-8929-929292929292',
 decode(repeat('a2',32),'hex'),decode(repeat('b2',32),'hex'),true);
 IF g<>2 OR EXISTS(SELECT 1 FROM connect.peer_key_approvals
   WHERE owner_id='99999999-9999-4999-8999-999999999999')
   OR EXISTS(SELECT 1 FROM connect.device_challenges
    WHERE user_id='99999999-9999-4999-8999-999999999999'
    AND consumed_at IS NULL) THEN
  RAISE EXCEPTION 'Replacement failed to clear inherited trust or nonces';
 END IF;
 IF (SELECT count(*) FROM connect.key_history
   WHERE user_id='99999999-9999-4999-8999-999999999999' AND revoked_at IS NOT NULL)<>1
 THEN RAISE EXCEPTION 'Old generation not revoked'; END IF;
END $replace$;
RESET ROLE;
\echo NEXO CONNECT: OTP-GATED ENROLLMENT SQL AND REVOCATION TESTS OK
