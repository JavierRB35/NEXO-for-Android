\set ON_ERROR_STOP on
DO $policy$
BEGIN
 IF has_function_privilege('authenticated',
    'public.nexo_connect_inbox(uuid,uuid,bigint,integer,uuid)','EXECUTE')
 OR has_function_privilege('anon',
    'public.nexo_connect_open_delivery(uuid,uuid,bigint,uuid)','EXECUTE')
 THEN RAISE EXCEPTION 'Inbox RPC leaked to client roles'; END IF;
END $policy$;
SET ROLE service_role;
DO $inbox$
DECLARE items jsonb;
DECLARE opened jsonb;
DECLARE message_id constant uuid := 'f5555555-5555-4555-8555-555555555555';
DECLARE sender constant uuid := '11111111-1111-4111-8111-111111111111';
DECLARE recipient constant uuid := '22222222-2222-4222-8222-222222222222';
BEGIN
 items:=public.nexo_connect_inbox(recipient,recipient,1,20,NULL);
 IF jsonb_typeof(items)<>'array' OR
  NOT EXISTS(SELECT 1 FROM jsonb_array_elements(items) AS x
    WHERE x->>'deliveryId'=message_id::text) THEN
  RAISE EXCEPTION 'Inbox excluded active pending delivery'; END IF;
 opened:=public.nexo_connect_open_delivery(recipient,recipient,1,message_id);
 IF opened->>'deliveryId'<>message_id::text OR
    opened->>'senderId'<>sender::text OR
    opened->>'recipientId'<>recipient::text OR
    length(opened->>'contextHex')<>284 OR
    length(opened->>'signatureHex')<>128 OR
    length(opened->>'ciphertextHex')<>160
 THEN RAISE EXCEPTION 'Invalid encrypted envelope contents'; END IF;
 BEGIN
  PERFORM public.nexo_connect_open_delivery(sender,sender,1,message_id);
  RAISE EXCEPTION 'Sender fetched recipient ciphertext';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_open_delivery(
   '33333333-3333-4333-8333-333333333333',
   '33333333-3333-4333-8333-333333333333',1,message_id);
  RAISE EXCEPTION 'Unrelated third party fetched ciphertext';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_open_delivery(recipient,recipient,2,message_id);
  RAISE EXCEPTION 'Old phone generation bypassed inbox';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_inbox(recipient,recipient,1,21,NULL);
  RAISE EXCEPTION 'Excessive inbox page accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM public.nexo_connect_inbox(recipient,recipient,1,10,
   '88888888-8888-4888-8888-888888888888');
  RAISE EXCEPTION 'Unowned cursor accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 IF public.nexo_connect_inbox(recipient,recipient,1,20,message_id)
   IS NULL THEN RAISE EXCEPTION 'Page cursor failed'; END IF;
 IF connect.api_resolve_delivery(recipient,message_id,'rejected')<>'rejected'
 THEN RAISE EXCEPTION 'Message was not rejected'; END IF;
 BEGIN
  PERFORM public.nexo_connect_open_delivery(recipient,recipient,1,message_id);
  RAISE EXCEPTION 'Resolved message was still downloadable';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 IF EXISTS(SELECT 1 FROM jsonb_array_elements(
   public.nexo_connect_inbox(recipient,recipient,1,20,NULL)) AS x
   WHERE x->>'deliveryId'=message_id::text) THEN
   RAISE EXCEPTION 'Resolved message remained listed'; END IF;
END $inbox$;
-- Two newly signed actions must be admitted, consumed only for corresponding action.
DO $challenge$
DECLARE challenge uuid;
DECLARE user1 constant uuid := '22222222-2222-4222-8222-222222222222';
BEGIN
 challenge:=connect.api_issue_challenge(user1,user1,1,'delivery/open');
 IF NOT connect.api_consume_challenge(user1,user1,1,'delivery/open',challenge)
 OR connect.api_consume_challenge(user1,user1,1,'delivery/open',challenge) THEN
  RAISE EXCEPTION 'Inbox proof nonce is not one-use'; END IF;
 challenge:=connect.api_issue_challenge(user1,user1,1,'delivery/inbox');
 IF connect.api_consume_challenge(user1,user1,1,'delivery/open',challenge)
 OR NOT connect.api_consume_challenge(user1,user1,1,'delivery/inbox',challenge) THEN
  RAISE EXCEPTION 'Inbox nonce is not action-bound'; END IF;
END $challenge$;
RESET ROLE;
\echo NEXO CONNECT: SIGNED INBOX / ONE ITEM / ISOLATION / NONCE PASS
