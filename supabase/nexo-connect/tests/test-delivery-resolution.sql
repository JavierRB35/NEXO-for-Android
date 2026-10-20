\set ON_ERROR_STOP on
-- Follows the synthetic fixtures and 0012 contact-control tests.
DO $permission$
BEGIN
 IF has_function_privilege('anon',
  'public.nexo_connect_resolve_delivery(uuid,uuid,bigint,uuid,text)','EXECUTE')
 OR has_function_privilege('authenticated',
  'public.nexo_connect_resolve_delivery(uuid,uuid,bigint,uuid,text)','EXECUTE')
 THEN RAISE EXCEPTION 'Client can resolve delivery via privileged RPC'; END IF;
END $permission$;

SET ROLE service_role;
-- A -> B has an active link and an explicitly approved recipient key.
SELECT connect.test_send_delivery(
 'f1111111-1111-4111-8111-111111111111',
 'f1212121-1212-4121-8121-121212121212',
 '11111111-1111-4111-8111-111111111111',
 '22222222-2222-4222-8222-222222222222',
 1,1,'note',decode(repeat('ab',64),'hex'),decode(repeat('cd',64),'hex'));

DO $resolution$
DECLARE current_status text;
BEGIN
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
    '22222222-2222-4222-8222-222222222222',
    '22222222-2222-4222-8222-222222222222',2,
    'f1111111-1111-4111-8111-111111111111','accepted');
  RAISE EXCEPTION 'Stale phone generation accepted a delivery';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
    '22222222-2222-4222-8222-222222222222',
    '23232323-2323-4232-8232-232323232323',1,
    'f1111111-1111-4111-8111-111111111111','accepted');
  RAISE EXCEPTION 'Unknown phone accepted a delivery';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
    '11111111-1111-4111-8111-111111111111',
    '11111111-1111-4111-8111-111111111111',1,
    'f1111111-1111-4111-8111-111111111111','accepted');
  RAISE EXCEPTION 'Sender accepted on recipient behalf';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
    '33333333-3333-4333-8333-333333333333',
    '33333333-3333-4333-8333-333333333333',1,
    'f1111111-1111-4111-8111-111111111111','rejected');
  RAISE EXCEPTION 'Unrelated user resolved delivery';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 current_status:=public.nexo_connect_resolve_delivery(
    '22222222-2222-4222-8222-222222222222',
    '22222222-2222-4222-8222-222222222222',1,
    'f1111111-1111-4111-8111-111111111111','accepted');
 IF current_status<>'accepted' THEN
   RAISE EXCEPTION 'Valid recipient acceptance rejected'; END IF;
 current_status:=public.nexo_connect_resolve_delivery(
    '22222222-2222-4222-8222-222222222222',
    '22222222-2222-4222-8222-222222222222',1,
    'f1111111-1111-4111-8111-111111111111','accepted');
 IF current_status<>'accepted' THEN
   RAISE EXCEPTION 'Idempotent acceptance failed'; END IF;
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
    '22222222-2222-4222-8222-222222222222',
    '22222222-2222-4222-8222-222222222222',1,
    'f1111111-1111-4111-8111-111111111111','rejected');
  RAISE EXCEPTION 'Accepted delivery became rejected';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $resolution$;
DO $result$
BEGIN
 IF (SELECT status FROM connect.deliveries
   WHERE id='f1111111-1111-4111-8111-111111111111')<>'accepted'
 OR (SELECT ciphertext FROM connect.deliveries
   WHERE id='f1111111-1111-4111-8111-111111111111') IS NOT NULL
 OR (SELECT count(*) FROM connect.delivery_events
   WHERE delivery_id='f1111111-1111-4111-8111-111111111111')<>2
 THEN RAISE EXCEPTION 'Accepted delivery state or deletion incorrect'; END IF;
END $result$;

SELECT connect.test_send_delivery(
 'f2222222-2222-4222-8222-222222222222',
 'f2323232-2323-4232-8232-232323232323',
 '11111111-1111-4111-8111-111111111111',
 '22222222-2222-4222-8222-222222222222',
 1,1,'list',decode(repeat('bd',48),'hex'),decode(repeat('ef',64),'hex'));
DO $cancellation$
BEGIN
 BEGIN
  PERFORM public.nexo_connect_resolve_delivery(
   '22222222-2222-4222-8222-222222222222',
   '22222222-2222-4222-8222-222222222222',1,
   'f2222222-2222-4222-8222-222222222222','cancelled');
  RAISE EXCEPTION 'Recipient cancelled a sender delivery';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 IF public.nexo_connect_resolve_delivery(
    '11111111-1111-4111-8111-111111111111',
    '11111111-1111-4111-8111-111111111111',1,
    'f2222222-2222-4222-8222-222222222222','cancelled')<>'cancelled'
 THEN RAISE EXCEPTION 'Valid sender cancellation rejected'; END IF;
 IF (SELECT status FROM connect.deliveries
   WHERE id='f2222222-2222-4222-8222-222222222222')<>'cancelled'
 OR (SELECT ciphertext FROM connect.deliveries
   WHERE id='f2222222-2222-4222-8222-222222222222') IS NOT NULL
 OR (SELECT count(*) FROM connect.delivery_events
   WHERE delivery_id='f2222222-2222-4222-8222-222222222222')<>2
 THEN RAISE EXCEPTION 'Sender cancellation left pending ciphertext'; END IF;
END $cancellation$;
RESET ROLE;
\echo NEXO CONNECT: SIGNED DELIVERY RESOLUTION / REPLAY / ROLE / DEVICE TESTS OK
