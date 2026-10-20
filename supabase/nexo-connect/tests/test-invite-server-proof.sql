\set ON_ERROR_STOP on
DO $acl$
BEGIN
 IF has_function_privilege('anon','public.nexo_connect_proof_device(uuid)','EXECUTE')
  OR has_function_privilege('authenticated','public.nexo_connect_proof_device(uuid)','EXECUTE')
  OR has_function_privilege('authenticated',
    'public.nexo_connect_consume_proof(uuid,uuid,bigint,text,uuid)','EXECUTE')
  OR has_function_privilege('authenticated',
    'public.nexo_connect_create_invite(uuid,bytea,integer)','EXECUTE') THEN
   RAISE EXCEPTION 'Public invite functions leaked to JWT clients';
 END IF;
END $acl$;
SET ROLE service_role;
DO $trusted$
DECLARE d record; challenge uuid; invite uuid;
BEGIN
 SELECT * INTO d FROM public.nexo_connect_proof_device(
  'dddddddd-dddd-4ddd-8ddd-dddddddddddd');
 IF d.device_id IS DISTINCT FROM 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'::uuid
   OR d.generation <> 2 OR length(d.signing_key_hex)<>64 THEN
   RAISE EXCEPTION 'Proof device lookup invalid'; END IF;
 challenge:=public.nexo_connect_issue_challenge(
  d.user_id,d.device_id,d.generation,'contact/invite');
 IF NOT public.nexo_connect_consume_proof(
  d.user_id,d.device_id,d.generation,'contact/invite',challenge) THEN
    RAISE EXCEPTION 'Valid challenge rejected'; END IF;
 IF public.nexo_connect_consume_proof(
  d.user_id,d.device_id,d.generation,'contact/invite',challenge) THEN
    RAISE EXCEPTION 'Consumed proof accepted again'; END IF;
 invite:=public.nexo_connect_create_invite(
  d.user_id,decode(repeat('78',32),'hex'),3600);
 IF invite IS NULL OR NOT EXISTS(SELECT 1 FROM connect.contact_invites
   WHERE id=invite AND creator_id=d.user_id) THEN
    RAISE EXCEPTION 'Service invite creation failed'; END IF;
END $trusted$;
RESET ROLE;
\echo NEXO CONNECT: INVITE PHONE PROOF SERVER RPC TESTS OK
