\set ON_ERROR_STOP on
DO $rights$
BEGIN
 IF has_function_privilege('anon','public.nexo_connect_device_status(uuid)','EXECUTE')
 OR has_function_privilege('authenticated','public.nexo_connect_device_status(uuid)','EXECUTE')
 OR has_function_privilege('authenticated',
   'public.nexo_connect_issue_challenge(uuid,uuid,bigint,text)','EXECUTE')
 THEN RAISE EXCEPTION 'Gateway RPC accidentally public'; END IF;
END $rights$;
SET ROLE authenticated;
DO $denied$
BEGIN
 BEGIN
  PERFORM * FROM public.nexo_connect_device_status(
     'dddddddd-dddd-4ddd-8ddd-dddddddddddd');
  RAISE EXCEPTION 'Unauthorized device status succeeded';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $denied$;
RESET ROLE;
SET ROLE service_role;
DO $allowed$
DECLARE d record; challenge uuid;
BEGIN
 SELECT * INTO d FROM public.nexo_connect_device_status(
   'dddddddd-dddd-4ddd-8ddd-dddddddddddd');
 IF d.device_id IS DISTINCT FROM 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'::uuid
   OR d.generation<>2 THEN
   RAISE EXCEPTION 'Wrong registered device'; END IF;
 challenge:=public.nexo_connect_issue_challenge(
   d.user_id,d.device_id,d.generation,'contact/invite');
 IF challenge IS NULL THEN
   RAISE EXCEPTION 'Challenge issuance failed'; END IF;
 IF NOT connect.api_consume_challenge(
   d.user_id,d.device_id,d.generation,'contact/invite',challenge)
 THEN RAISE EXCEPTION 'Challenge mismatch'; END IF;
END $allowed$;
RESET ROLE;
\echo NEXO CONNECT: GATEWAY RPC ACCESS TESTS OK
