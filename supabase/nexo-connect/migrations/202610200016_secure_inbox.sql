-- 0016: authenticated/signed inbox listing and single-item encrypted retrieval.
-- Only service_role may call these bridge functions AFTER the Edge Function
-- has verified JWT, active phone, Ed25519 signature and one-use challenge.
BEGIN;
ALTER TABLE connect.device_challenges DROP CONSTRAINT device_challenges_action_check;
ALTER TABLE connect.device_challenges ADD CONSTRAINT device_challenges_action_check
 CHECK(action IN
   ('contact/invite','contact/accept','contact/permissions','contact/unlink',
    'key/verify','delivery/send','delivery/resolve','delivery/request-resend',
    'delivery/inbox','delivery/open'));

CREATE OR REPLACE FUNCTION connect.api_issue_challenge(
 p_user uuid,p_device uuid,p_generation bigint,p_action text
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE result uuid;
BEGIN
 IF p_user IS NULL OR p_device IS NULL OR p_generation IS NULL OR
    p_action IS NULL OR p_action NOT IN (
      'contact/invite','contact/accept','contact/permissions','contact/unlink',
      'key/verify','delivery/send','delivery/resolve','delivery/request-resend',
      'delivery/inbox','delivery/open')
 THEN RAISE EXCEPTION 'Invalid challenge' USING ERRCODE='22023'; END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
       'nexo-challenge:'||p_user::text,0));
 IF NOT EXISTS(SELECT 1 FROM connect.devices
    WHERE user_id=p_user AND device_id=p_device AND generation=p_generation) THEN
    RAISE EXCEPTION 'Revoked or unknown device' USING ERRCODE='22023'; END IF;
 IF (SELECT count(*) FROM connect.device_challenges
    WHERE user_id=p_user AND consumed_at IS NULL AND expires_at>pg_catalog.now())>=5 THEN
    RAISE EXCEPTION 'Too many outstanding challenges' USING ERRCODE='22023'; END IF;
 INSERT INTO connect.device_challenges(user_id,device_id,generation,action)
  VALUES(p_user,p_device,p_generation,p_action) RETURNING id INTO result;
 RETURN result;
END $fn$;
REVOKE ALL ON FUNCTION connect.api_issue_challenge(uuid,uuid,bigint,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION connect.api_issue_challenge(uuid,uuid,bigint,text)
 TO service_role;

CREATE FUNCTION public.nexo_connect_inbox(
 p_actor uuid,p_device uuid,p_generation bigint,p_limit integer,p_after uuid
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE anchor timestamptz;
DECLARE page jsonb;
BEGIN
 IF p_actor IS NULL OR p_device IS NULL OR p_generation IS NULL OR
    p_generation<=0 OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 20
 THEN RAISE EXCEPTION 'Invalid inbox request' USING ERRCODE='22023'; END IF;
 PERFORM 1 FROM connect.devices WHERE user_id=p_actor
    AND device_id=p_device AND generation=p_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Revoked inbox phone' USING ERRCODE='22023'; END IF;
 IF p_after IS NOT NULL THEN
  SELECT created_at INTO anchor FROM connect.deliveries
   WHERE id=p_after AND recipient_id=p_actor AND
     recipient_generation=p_generation;
  IF NOT FOUND THEN
   RAISE EXCEPTION 'Invalid inbox cursor' USING ERRCODE='22023'; END IF;
 END IF;
 SELECT COALESCE(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
   'deliveryId',d.id,'senderId',d.sender_id,'senderGeneration',d.sender_generation,
   'recipientGeneration',d.recipient_generation,
   'entityType',d.entity_type,
   'expiresAtMs',(pg_catalog.floor(pg_catalog.extract(epoch FROM d.expires_at)*1000))::bigint
 ) ORDER BY d.created_at,d.id),'[]'::jsonb) INTO page
 FROM (
  SELECT id,sender_id,sender_generation,recipient_generation,entity_type,
         expires_at,created_at
  FROM connect.deliveries
  WHERE recipient_id=p_actor AND recipient_generation=p_generation
    AND status='pending' AND expires_at>pg_catalog.now()
    AND (p_after IS NULL OR (created_at,id)>(anchor,p_after))
  ORDER BY created_at,id LIMIT p_limit
 ) d;
 RETURN page;
END $fn$;

CREATE FUNCTION public.nexo_connect_open_delivery(
 p_actor uuid,p_device uuid,p_generation bigint,p_delivery uuid
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE envelope connect.deliveries%ROWTYPE;
BEGIN
 IF p_actor IS NULL OR p_device IS NULL OR p_generation IS NULL
 OR p_generation<=0 OR p_delivery IS NULL THEN
   RAISE EXCEPTION 'Invalid delivery request' USING ERRCODE='22023'; END IF;
 PERFORM 1 FROM connect.devices WHERE user_id=p_actor
    AND device_id=p_device AND generation=p_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Revoked inbox phone' USING ERRCODE='22023'; END IF;
 SELECT * INTO envelope FROM connect.deliveries
 WHERE id=p_delivery AND recipient_id=p_actor
   AND recipient_generation=p_generation
   AND status='pending' AND expires_at>pg_catalog.now()
   AND ciphertext IS NOT NULL
 FOR SHARE;
 IF NOT FOUND THEN
   RAISE EXCEPTION 'Unavailable encrypted message' USING ERRCODE='22023'; END IF;
 -- NEVER return a claimed sender public key. Android must compare the
 -- context fingerprint to its own QR-pinned sender key before decryption.
 RETURN pg_catalog.jsonb_build_object(
   'deliveryId',envelope.id,'senderId',envelope.sender_id,
   'recipientId',envelope.recipient_id,
   'senderGeneration',envelope.sender_generation,
   'recipientGeneration',envelope.recipient_generation,
   'entityType',envelope.entity_type,
   'expiresAtMs',(pg_catalog.floor(pg_catalog.extract(epoch FROM envelope.expires_at)*1000))::bigint,
   'contextHex',pg_catalog.encode(envelope.context,'hex'),
   'ciphertextHex',pg_catalog.encode(envelope.ciphertext,'hex'),
   'ciphertextSha256Hex',pg_catalog.encode(envelope.ciphertext_sha256,'hex'),
   'signatureHex',pg_catalog.encode(envelope.signature,'hex')
 );
END $fn$;
REVOKE ALL ON FUNCTION public.nexo_connect_inbox(uuid,uuid,bigint,integer,uuid)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_open_delivery(uuid,uuid,bigint,uuid)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_inbox(uuid,uuid,bigint,integer,uuid)
 TO service_role;
GRANT EXECUTE ON FUNCTION public.nexo_connect_open_delivery(uuid,uuid,bigint,uuid)
 TO service_role;
COMMIT;
