\set ON_ERROR_STOP on
-- service_role simulates authenticated server calls, not unverified client RPCs
DO $access$
BEGIN
 IF has_table_privilege('authenticated','connect.device_challenges','SELECT')
 OR has_function_privilege('authenticated','connect.api_issue_challenge(uuid,uuid,bigint,text)','EXECUTE')
 OR has_function_privilege('authenticated','connect.api_consume_challenge(uuid,uuid,bigint,text,uuid)','EXECUTE')
 THEN RAISE EXCEPTION 'Unauthenticated challenge access'; END IF;
END $access$;

-- user D had his device replaced to generation 2 in previous contact tests
SET ROLE service_role;
SELECT connect.api_issue_challenge(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',2,'delivery/send') AS nonce \gset
SELECT set_config('nexo.test_challenge', :'nonce', false);
DO $check$
BEGIN
 IF connect.api_consume_challenge(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',2,'delivery/resolve',current_setting('nexo.test_challenge')::uuid)
 THEN RAISE EXCEPTION 'Wrong route consumed nonce'; END IF;
 IF connect.api_consume_challenge(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',1,'delivery/send',current_setting('nexo.test_challenge')::uuid)
 THEN RAISE EXCEPTION 'Revoked old device consumed nonce'; END IF;
 IF NOT connect.api_consume_challenge(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',2,'delivery/send',current_setting('nexo.test_challenge')::uuid)
 THEN RAISE EXCEPTION 'Valid nonce not consumed'; END IF;
 IF connect.api_consume_challenge(
 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',2,'delivery/send',current_setting('nexo.test_challenge')::uuid)
 THEN RAISE EXCEPTION 'Replay accepted'; END IF;
END $check$;
RESET ROLE;
\echo NEXO CONNECT: DEVICE CHALLENGE TESTS OK
