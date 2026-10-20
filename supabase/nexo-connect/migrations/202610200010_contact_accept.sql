-- 0010: signed recipient acceptance; pin creator's QUOTED QR fingerprint.
-- Caller MUST be the Edge gateway after validated JWT, device Ed25519
-- signature and atomic one-time challenge for action "contact/accept".
BEGIN;
CREATE FUNCTION public.nexo_connect_accept_invite(
 p_recipient uuid,p_recipient_device uuid,p_recipient_generation bigint,
 p_creator uuid,p_creator_generation bigint,p_token_hash bytea,
 p_creator_signing_fp bytea,p_creator_encryption_fp bytea
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
DECLARE found_creator uuid; request_id uuid; creator_key record;
BEGIN
 IF p_recipient IS NULL OR p_creator IS NULL OR p_recipient=p_creator OR
    p_recipient_device IS NULL OR p_recipient_generation IS NULL OR
    p_creator_generation IS NULL OR octet_length(p_token_hash)<>32 OR
    octet_length(p_creator_signing_fp)<>32 OR
    octet_length(p_creator_encryption_fp)<>32 THEN
    RAISE EXCEPTION 'Invalid acceptance' USING ERRCODE='22023';
 END IF;
 -- Lock the consumed-once invitation so concurrent acceptors serialize.
 SELECT i.creator_id INTO found_creator FROM connect.contact_invites i
  WHERE i.token_sha256=p_token_hash AND i.consumed_at IS NULL
        AND i.expires_at>pg_catalog.now() FOR UPDATE;
 IF found_creator IS NULL OR found_creator<>p_creator THEN
    RAISE EXCEPTION 'Token creator mismatch or invite expired' USING ERRCODE='22023';
 END IF;
 -- Also hold row locks on both active devices throughout this transaction.
 IF NOT EXISTS(SELECT 1 FROM connect.devices d
   WHERE d.user_id=p_recipient AND d.device_id=p_recipient_device
     AND d.generation=p_recipient_generation FOR SHARE) THEN
   RAISE EXCEPTION 'Recipient phone revoked' USING ERRCODE='22023';
 END IF;
 SELECT d.generation,d.signing_fingerprint,d.encryption_fingerprint
   INTO creator_key FROM connect.devices d WHERE d.user_id=p_creator FOR SHARE;
 IF NOT FOUND OR creator_key.generation<>p_creator_generation OR
    creator_key.signing_fingerprint IS DISTINCT FROM p_creator_signing_fp OR
    creator_key.encryption_fingerprint IS DISTINCT FROM p_creator_encryption_fp THEN
    RAISE EXCEPTION 'Creator QR identity changed, reverify' USING ERRCODE='22023';
 END IF;
 request_id:=connect.api_accept_invite(p_recipient,p_token_hash);
 -- Only recipient pins creator from an authentically exchanged QR.
 -- Creator does not automatically trust recipient's unverified key.
 PERFORM connect.api_approve_peer_key(
   p_recipient,p_creator,p_creator_generation,
   p_creator_signing_fp,p_creator_encryption_fp);
 RETURN request_id;
END $f$;
REVOKE ALL ON FUNCTION public.nexo_connect_accept_invite(
 uuid,uuid,bigint,uuid,bigint,bytea,bytea,bytea)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_accept_invite(
 uuid,uuid,bigint,uuid,bigint,bytea,bytea,bytea) TO service_role;
COMMIT;
