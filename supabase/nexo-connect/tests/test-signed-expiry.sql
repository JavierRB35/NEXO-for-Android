\set ON_ERROR_STOP on
DO $test$
DECLARE
 sender constant uuid := '11111111-1111-4111-8111-111111111111';
 receiver constant uuid := '22222222-2222-4222-8222-222222222222';
 delivery constant uuid := 'f5555555-5555-4555-8555-555555555555';
 retry constant uuid := 'f5656565-5656-4656-8656-565656565656';
 ctx bytea;
 sign_fp bytea;
 enc_fp bytea;
 issued bigint;
 expiry bigint;
 persisted timestamptz;
 actual uuid;
BEGIN
 SELECT signing_fingerprint INTO sign_fp FROM connect.devices WHERE user_id=sender;
 SELECT encryption_fingerprint INTO enc_fp FROM connect.devices WHERE user_id=receiver;
 issued := pg_catalog.floor(pg_catalog.extract(epoch FROM pg_catalog.now())*1000)::bigint;
 expiry := issued + 3600000;
 ctx := pg_catalog.decode('4e58433101'||replace(delivery::text,'-','')||
  replace(sender::text,'-','')||replace(receiver::text,'-','')||'02'||
  '00000001'||'00000001'||
  pg_catalog.lpad(pg_catalog.to_hex(issued),16,'0')||
  pg_catalog.lpad(pg_catalog.to_hex(expiry),16,'0')||
  pg_catalog.encode(sign_fp,'hex')||pg_catalog.encode(enc_fp,'hex'),'hex');
 PERFORM connect.api_approve_peer_key(sender,receiver,1,decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex'));
 actual:=connect.api_send_delivery(delivery,retry,sender,receiver,1,1,
   'note',ctx,decode(repeat('ab',80),'hex'),decode(repeat('cd',64),'hex'));
 IF actual<>delivery THEN RAISE EXCEPTION 'Delivery ID mismatch'; END IF;
 SELECT expires_at INTO persisted FROM connect.deliveries WHERE id=delivery;
 IF pg_catalog.abs(pg_catalog.extract(epoch FROM persisted)*1000-expiry)>2 THEN
  RAISE EXCEPTION 'Signed one-hour expiry ignored'; END IF;
 IF persisted>pg_catalog.now()+interval '2 hours' THEN
  RAISE EXCEPTION 'Fixed 30-day expiry was retained'; END IF;

 -- Distinct id and request, expiry already passed: reject without INSERT.
 -- Construct the past-dated context with a distinct delivery id.
 ctx := pg_catalog.decode('4e58433101'||
  replace('f6666666-6666-4666-8666-666666666666','-','')||
  replace(sender::text,'-','')||replace(receiver::text,'-','')||
  '02'||'00000001'||'00000001'||
  pg_catalog.lpad(pg_catalog.to_hex(issued-86400000),16,'0')||
  pg_catalog.lpad(pg_catalog.to_hex(issued-1000),16,'0')||
  pg_catalog.encode(sign_fp,'hex')||pg_catalog.encode(enc_fp,'hex'),'hex');
 BEGIN
  PERFORM connect.api_send_delivery(
  'f6666666-6666-4666-8666-666666666666',
  'f6767676-6767-4676-8676-676767676767',
  sender,receiver,1,1,'note',ctx,
  decode(repeat('ab',80),'hex'),decode(repeat('cd',64),'hex'));
  RAISE EXCEPTION 'Expired signed content was stored';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 IF EXISTS(SELECT 1 FROM connect.deliveries WHERE
    id='f6666666-6666-4666-8666-666666666666') THEN
  RAISE EXCEPTION 'Expired ciphertext row remains'; END IF;
END $test$;
\echo NEXO CONNECT: SIGNED EXPIRES_AT MATCHES NXC1 / EXPIRED REJECTED PASS
