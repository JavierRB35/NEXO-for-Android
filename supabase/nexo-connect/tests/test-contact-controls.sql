\set ON_ERROR_STOP on
-- Existing fixtures: A and F linked and mutually pinned in 0010/0011.
DO $no_client_rpc$
BEGIN
 IF has_function_privilege('anon',
  'public.nexo_connect_receive_permission(uuid,uuid,bigint,uuid,boolean)','EXECUTE')
 OR has_function_privilege('authenticated',
  'public.nexo_connect_receive_permission(uuid,uuid,bigint,uuid,boolean)','EXECUTE')
 OR has_function_privilege('authenticated',
  'public.nexo_connect_unlink_contact(uuid,uuid,bigint,uuid)','EXECUTE')
 THEN RAISE EXCEPTION 'Client can mutate contact via RPC'; END IF;
END $no_client_rpc$;
SET ROLE service_role;
SELECT public.nexo_connect_receive_permission(
 '66666666-6666-4666-8666-666666666666',
 '67676767-6767-4767-8767-676767676767',1,
 '11111111-1111-4111-8111-111111111111',false);
DO $direction$
BEGIN
 IF (SELECT allow_receive FROM connect.receive_permissions
   WHERE receiver_id='66666666-6666-4666-8666-666666666666'
   AND sender_id='11111111-1111-4111-8111-111111111111') IS DISTINCT FROM false
 OR (SELECT allow_receive FROM connect.receive_permissions
   WHERE receiver_id='11111111-1111-4111-8111-111111111111'
   AND sender_id='66666666-6666-4666-8666-666666666666') IS DISTINCT FROM true
 THEN RAISE EXCEPTION 'Permission not directional'; END IF;
 BEGIN
   PERFORM public.nexo_connect_receive_permission(
    '66666666-6666-4666-8666-666666666666',
    '67676767-6767-4767-8767-676767676767',2,
    '11111111-1111-4111-8111-111111111111',true);
   RAISE EXCEPTION 'Old phone allowed to change reception';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $direction$;
SELECT public.nexo_connect_receive_permission(
 '66666666-6666-4666-8666-666666666666',
 '67676767-6767-4767-8767-676767676767',1,
 '11111111-1111-4111-8111-111111111111',true);
-- Test-fixture INSERT is performed as the PostgreSQL migration owner.
-- service_role deliberately has NO direct INSERT privilege on connect.deliveries.
RESET ROLE;
-- Only synthetic ciphertext bytes are inserted; not a real delivery.
INSERT INTO connect.deliveries(
 id,client_request_id,sender_id,recipient_id,sender_generation,
 recipient_generation,entity_type,context,ciphertext,ciphertext_sha256,
 signature,expires_at)
VALUES(
 '70707070-7070-4070-8070-707070707070',
 '71717171-7171-4171-8171-717171717171',
 '11111111-1111-4111-8111-111111111111',
 '66666666-6666-4666-8666-666666666666',1,1,
 'note',decode(repeat('77',142),'hex'),
 decode('aabbccdd','hex'),
 extensions.digest(decode('aabbccdd','hex'),'sha256'),
 decode(repeat('cd',64),'hex'),now()+interval '1 day');
INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
VALUES('70707070-7070-4070-8070-707070707070',
       '11111111-1111-4111-8111-111111111111','pending');
SET ROLE service_role;
DO $unknown_phone$
BEGIN
 BEGIN
  PERFORM public.nexo_connect_unlink_contact(
    '66666666-6666-4666-8666-666666666666',
    '66666666-6666-4666-8666-666666666666',1,
    '11111111-1111-4111-8111-111111111111');
  RAISE EXCEPTION 'Old or unknown phone unlinked contact';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $unknown_phone$;
SELECT public.nexo_connect_unlink_contact(
 '66666666-6666-4666-8666-666666666666',
 '67676767-6767-4767-8767-676767676767',1,
 '11111111-1111-4111-8111-111111111111');
DO $revoked$
BEGIN
 IF (SELECT status FROM connect.contacts
   WHERE user_a='11111111-1111-4111-8111-111111111111'
     AND user_b='66666666-6666-4666-8666-666666666666')<>'revoked'
 OR EXISTS(SELECT 1 FROM connect.peer_key_approvals
    WHERE (owner_id='11111111-1111-4111-8111-111111111111'
       AND peer_id='66666666-6666-4666-8666-666666666666')
       OR (owner_id='66666666-6666-4666-8666-666666666666'
       AND peer_id='11111111-1111-4111-8111-111111111111'))
 OR (SELECT status FROM connect.deliveries
       WHERE id='70707070-7070-4070-8070-707070707070')<>'cancelled'
 OR (SELECT ciphertext FROM connect.deliveries
       WHERE id='70707070-7070-4070-8070-707070707070') IS NOT NULL
 OR (SELECT count(*) FROM connect.delivery_events
       WHERE delivery_id='70707070-7070-4070-8070-707070707070'
         AND new_status='cancelled')<>1
 THEN RAISE EXCEPTION 'Unlink failed to revoke all pending access and trust'; END IF;
 BEGIN
  INSERT INTO connect.deliveries(
   id,client_request_id,sender_id,recipient_id,sender_generation,
   recipient_generation,entity_type,context,ciphertext,ciphertext_sha256,
   signature,expires_at)
  VALUES(
   '72727272-7272-4272-8272-727272727272',
   '73737373-7373-4373-8373-737373737373',
   '11111111-1111-4111-8111-111111111111',
   '66666666-6666-4666-8666-666666666666',1,1,
   'note',decode(repeat('77',142),'hex'),decode('aabb','hex'),
   extensions.digest(decode('aabb','hex'),'sha256'),
   decode(repeat('cd',64),'hex'),now()+interval '1 day');
  RAISE EXCEPTION 'Revoked contact could still send';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_receive_permission(
   '66666666-6666-4666-8666-666666666666',
   '67676767-6767-4767-8767-676767676767',1,
   '11111111-1111-4111-8111-111111111111',true);
  RAISE EXCEPTION 'Unlinked permission enabled';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $revoked$;
RESET ROLE;
\echo NEXO CONNECT: SIGNED CONTACT PERMISSIONS, UNLINK, KEY WIPE, CIPHERTEXT CANCELLATION PASS
