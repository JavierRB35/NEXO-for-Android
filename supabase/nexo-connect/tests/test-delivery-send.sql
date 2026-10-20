\set ON_ERROR_STOP on
-- Synthetic NXC1 bytes; real Ed25519 signature checks belong to Edge.
CREATE FUNCTION connect.test_send_signed_bridge(
 p_id uuid,p_request uuid,p_sender uuid,p_device uuid,p_generation bigint,
 p_recipient uuid,p_recipient_generation bigint,p_type text
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
DECLARE ctx bytea;
DECLARE code integer;
DECLARE signer bytea;
DECLARE recipient_fp bytea;
DECLARE issued bigint:=1780000000000;
BEGIN
 code:=CASE p_type WHEN 'note' THEN 2 WHEN 'list' THEN 3 ELSE 0 END;
 SELECT signing_fingerprint INTO signer FROM connect.devices WHERE user_id=p_sender;
 SELECT encryption_fingerprint INTO recipient_fp FROM connect.devices WHERE user_id=p_recipient;
 ctx:=decode('4e58433101'||replace(p_id::text,'-','')||
  replace(p_sender::text,'-','')||replace(p_recipient::text,'-','')||
  lpad(to_hex(code),2,'0')||lpad(to_hex(p_generation),8,'0')||
  lpad(to_hex(p_recipient_generation),8,'0')||
  lpad(to_hex(issued),16,'0')||lpad(to_hex(issued+86400000),16,'0')||
  encode(signer,'hex')||encode(recipient_fp,'hex'),'hex');
 RETURN public.nexo_connect_send_delivery(p_sender,p_device,p_generation,
  p_id,p_request,p_recipient,p_recipient_generation,p_type,
  ctx,decode(repeat('ab',96),'hex'),decode(repeat('bc',64),'hex'));
END $f$;
DO $role$
BEGIN
 IF has_function_privilege('anon',
 'public.nexo_connect_send_delivery(uuid,uuid,bigint,uuid,uuid,uuid,bigint,text,bytea,bytea,bytea)','EXECUTE')
 OR has_function_privilege('authenticated',
 'public.nexo_connect_send_delivery(uuid,uuid,bigint,uuid,uuid,uuid,bigint,text,bytea,bytea,bytea)','EXECUTE')
 THEN RAISE EXCEPTION 'Client may directly send ciphertext as service role'; END IF;
END $role$;
SET ROLE service_role;
DO $signed_send$
DECLARE v_sent uuid;
BEGIN
 BEGIN
  PERFORM connect.test_send_signed_bridge(
   'f3333333-3333-4333-8333-333333333333',
   'f3434343-3434-4343-8343-343434343434',
   '11111111-1111-4111-8111-111111111111',
   '99999999-9999-4999-8999-999999999999',1,
   '22222222-2222-4222-8222-222222222222',1,'note');
  RAISE EXCEPTION 'Wrong sender device accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN
  PERFORM connect.test_send_signed_bridge(
   'f3333333-3333-4333-8333-333333333333',
   'f3434343-3434-4343-8343-343434343434',
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '22222222-2222-4222-8222-222222222222',2,'note');
  RAISE EXCEPTION 'Stale recipient generation accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 v_sent:=connect.test_send_signed_bridge(
   'f3333333-3333-4333-8333-333333333333',
   'f3434343-3434-4343-8343-343434343434',
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '22222222-2222-4222-8222-222222222222',1,'note');
 IF v_sent<>'f3333333-3333-4333-8333-333333333333' THEN
  RAISE EXCEPTION 'Wrong id returned'; END IF;
 IF connect.test_send_signed_bridge(
   'f3333333-3333-4333-8333-333333333333',
   'f3434343-3434-4343-8343-343434343434',
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '22222222-2222-4222-8222-222222222222',1,'note')<>v_sent THEN
   RAISE EXCEPTION 'Idempotent bridge retry failed'; END IF;
 IF (SELECT count(*) FROM connect.deliveries WHERE id=v_sent)<>1 THEN
  RAISE EXCEPTION 'Duplicate delivered'; END IF;
END $signed_send$;
SELECT connect.api_set_receive_permission(
 '22222222-2222-4222-8222-222222222222',
 '11111111-1111-4111-8111-111111111111',false);
DO $pause$
BEGIN
 BEGIN
  PERFORM connect.test_send_signed_bridge(
   'f4444444-4444-4444-8444-444444444444',
   'f4545454-4545-4454-8454-454545454545',
   '11111111-1111-4111-8111-111111111111',
   '11111111-1111-4111-8111-111111111111',1,
   '22222222-2222-4222-8222-222222222222',1,'list');
  RAISE EXCEPTION 'Paused reception accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $pause$;
SELECT connect.api_set_receive_permission(
 '22222222-2222-4222-8222-222222222222',
 '11111111-1111-4111-8111-111111111111',true);
RESET ROLE;
\echo NEXO CONNECT: SERVER-ONLY SIGNED SEND / PHONE / PERMISSIONS / IDEMPOTENCY PASS
