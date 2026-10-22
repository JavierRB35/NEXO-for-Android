\set ON_ERROR_STOP on
-- Tests autoejecutables de entrega E2EE en PostgreSQL (sin datos personales).
-- La prueba anterior revocó A-B; reactivamos la relación como admin de fixture.
UPDATE connect.contacts SET status='active'
 WHERE user_a='11111111-1111-4111-8111-111111111111'
   AND user_b='22222222-2222-4222-8222-222222222222';
UPDATE connect.receive_permissions SET allow_receive=true
 WHERE receiver_id='22222222-2222-4222-8222-222222222222'
 AND sender_id='11111111-1111-4111-8111-111111111111';

-- CI-only fixture: assembles exactly the NXC1 context that the sending
-- Android client will sign. This helper is NOT part of production migrations.
CREATE FUNCTION connect.test_send_delivery(
  p_id uuid,p_request uuid,p_sender uuid,p_recipient uuid,
  p_sender_generation bigint,p_recipient_generation bigint,
  p_type text,p_cipher bytea,p_signature bytea
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
DECLARE ctx bytea;
DECLARE code integer;
DECLARE sender_fp bytea;
DECLARE recipient_fp bytea;
DECLARE issued bigint;
BEGIN
 code:=CASE p_type
   WHEN 'appointment' THEN 1 WHEN 'note' THEN 2 WHEN 'list' THEN 3
   WHEN 'journal' THEN 4 WHEN 'book' THEN 5 WHEN 'movie' THEN 6
   WHEN 'document' THEN 7 ELSE 0 END;
 SELECT signing_fingerprint INTO sender_fp FROM connect.devices WHERE user_id=p_sender;
 SELECT encryption_fingerprint INTO recipient_fp FROM connect.devices WHERE user_id=p_recipient;
 -- Same logical request must preserve identical signed context for retries.
 issued:=1780000000000;
 ctx:=decode('4e58433101'||
    replace(p_id::text,'-','')||
    replace(p_sender::text,'-','')||
    replace(p_recipient::text,'-','')||
    lpad(to_hex(code),2,'0')||
    lpad(to_hex(p_sender_generation),8,'0')||
    lpad(to_hex(p_recipient_generation),8,'0')||
    lpad(to_hex(issued),16,'0')||
    lpad(to_hex(issued+86400000),16,'0')||
    encode(sender_fp,'hex')||encode(recipient_fp,'hex'),'hex');
 RETURN connect.api_send_delivery(
    p_id,p_request,p_sender,p_recipient,
    p_sender_generation,p_recipient_generation,p_type,
    ctx,p_cipher,p_signature);
END $f$;

SET ROLE service_role;
SELECT connect.api_approve_peer_key(
  '11111111-1111-4111-8111-111111111111',
  '22222222-2222-4222-8222-222222222222',
  1,decode(repeat('ee',32),'hex'),decode(repeat('ff',32),'hex'));

DO $cannot_send_unverified$
BEGIN
  BEGIN
    PERFORM connect.test_send_delivery(
      '91111111-1111-4111-8111-111111111111',
      '91111111-1111-4111-8111-222222222222',
      '22222222-2222-4222-8222-222222222222',
      '11111111-1111-4111-8111-111111111111',
      1,1,'note',decode(repeat('ab',200),'hex'),decode(repeat('bb',64),'hex'));
    RAISE EXCEPTION 'Unverified reverse recipient accepted';
  EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $cannot_send_unverified$;

-- Tampering with canonical context must fail even if other fields look valid.
DO $badctx$
BEGIN
 BEGIN
  PERFORM connect.api_send_delivery(
   'd1111111-1111-4111-8111-111111111111',
   'd2222222-2222-4222-8222-222222222222',
   '11111111-1111-4111-8111-111111111111',
   '22222222-2222-4222-8222-222222222222',
   1,1,'note',decode(repeat('00',142),'hex'),
   decode('ffff','hex'),decode(repeat('bc',64),'hex'));
  RAISE EXCEPTION 'Context mutation accepted';
 EXCEPTION WHEN invalid_parameter_value THEN NULL;
 END;
END $badctx$;

-- A->B permitido, with fingerprint verified by A.
SELECT connect.test_send_delivery(
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
 'abababab-abab-4bab-8bab-abababababab',
 '11111111-1111-4111-8111-111111111111',
 '22222222-2222-4222-8222-222222222222',
 1,1,'note',decode(repeat('ab',200),'hex'),decode(repeat('bc',64),'hex')) AS sent \gset

-- Same request is idempotent and produces no second record.
SELECT connect.test_send_delivery(
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
 'abababab-abab-4bab-8bab-abababababab',
 '11111111-1111-4111-8111-111111111111',
 '22222222-2222-4222-8222-222222222222',
 1,1,'note',decode(repeat('ab',200),'hex'),decode(repeat('bc',64),'hex')) AS resent \gset

DO $only_one$
BEGIN
 IF (SELECT count(*) FROM connect.deliveries WHERE sender_id='11111111-1111-4111-8111-111111111111'
    AND recipient_id='22222222-2222-4222-8222-222222222222')<>1
 OR (SELECT count(*) FROM connect.delivery_events WHERE delivery_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')<>1
 THEN RAISE EXCEPTION 'Idempotency failed'; END IF;
 BEGIN
  PERFORM connect.test_send_delivery(
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','abababab-abab-4bab-8bab-abababababab',
   '11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222',
   1,1,'note',decode(repeat('ac',200),'hex'),decode(repeat('bc',64),'hex'));
  RAISE EXCEPTION 'Idempotency accepts ciphertext substitution';
 EXCEPTION WHEN unique_violation THEN NULL; END;
END $only_one$;

-- Prevent sender from marking received; recipient can acknowledge only once.
DO $not_sender$
BEGIN
 BEGIN
 PERFORM connect.api_resolve_delivery('11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','accepted');
 RAISE EXCEPTION 'Sender accepted for recipient';
 EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $not_sender$;
SELECT connect.api_resolve_delivery('22222222-2222-4222-8222-222222222222',
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','accepted');
SELECT connect.api_resolve_delivery('22222222-2222-4222-8222-222222222222',
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','accepted');
DO $terminal$
BEGIN
 IF (SELECT status FROM connect.deliveries WHERE id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')<>'accepted'
 OR (SELECT ciphertext FROM connect.deliveries WHERE id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') IS NOT NULL
 OR (SELECT count(*) FROM connect.delivery_events WHERE delivery_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')<>2
 THEN RAISE EXCEPTION 'Receipt finalization failed'; END IF;
 BEGIN
  PERFORM connect.api_resolve_delivery('22222222-2222-4222-8222-222222222222',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','rejected');
  RAISE EXCEPTION 'Accepted message changed status';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $terminal$;

-- Pausing reception prevents NEW messages, not retroactive accepted status.
SELECT connect.api_set_receive_permission(
 '22222222-2222-4222-8222-222222222222',
 '11111111-1111-4111-8111-111111111111', false);
DO $pause$
BEGIN
 BEGIN
 PERFORM connect.test_send_delivery(
 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','bcbcbcbc-bcbc-4cbc-8cbc-bcbcbcbcbcbc',
 '11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222',
 1,1,'document',decode(repeat('ff',40),'hex'),decode(repeat('ba',64),'hex'));
 RAISE EXCEPTION 'Paused recipient accepted new send';
 EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $pause$;

-- A delivery with elapsed TTL is finalized as expired atomically without
-- rolling back the UPDATE through an exception.
SELECT connect.api_set_receive_permission(
 '22222222-2222-4222-8222-222222222222',
 '11111111-1111-4111-8111-111111111111', true);
SELECT connect.test_send_delivery(
 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
 'cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd',
 '11111111-1111-4111-8111-111111111111',
 '22222222-2222-4222-8222-222222222222',
 1,1,'list',decode(repeat('ee',80),'hex'),decode(repeat('df',64),'hex'));
RESET ROLE;
UPDATE connect.deliveries SET created_at=now()-interval '31 days',
 expires_at=now()-interval '1 day'
WHERE id='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
SET ROLE service_role;
SELECT connect.api_resolve_delivery(
 '22222222-2222-4222-8222-222222222222',
 'cccccccc-cccc-4ccc-8ccc-cccccccccccc','accepted');
DO $expiry$
BEGIN
 IF (SELECT status FROM connect.deliveries
     WHERE id='cccccccc-cccc-4ccc-8ccc-cccccccccccc')<>'expired'
 OR (SELECT ciphertext FROM connect.deliveries
     WHERE id='cccccccc-cccc-4ccc-8ccc-cccccccccccc') IS NOT NULL
 THEN RAISE EXCEPTION 'Expiry not committed'; END IF;
END $expiry$;

-- Deleting contact revokes visibility as defined in contact RLS (message history
-- between parties remains visible to parties for tracking; not to other users).
RESET ROLE;
SET ROLE authenticated;
SET request.jwt.claim.sub='33333333-3333-4333-8333-333333333333';
DO $isolate$
BEGIN
 IF (SELECT count(*) FROM connect.deliveries)<>0 OR
    (SELECT count(*) FROM connect.delivery_events)<>0 OR
    (SELECT count(*) FROM connect.peer_key_approvals)<>0
 THEN RAISE EXCEPTION 'Third-party delivery visibility breach'; END IF;
END $isolate$;
RESET ROLE;

SET ROLE authenticated;
SET request.jwt.claim.sub='22222222-2222-4222-8222-222222222222';
DO $recipient$
BEGIN
 IF (SELECT count(*) FROM connect.deliveries)<>2 THEN
 RAISE EXCEPTION 'Recipient cannot see own delivery'; END IF;
END $recipient$;
RESET ROLE;
\echo NEXO CONNECT: DELIVERY, IDEMPOTENCY, RLS, STATUS TESTS OK
