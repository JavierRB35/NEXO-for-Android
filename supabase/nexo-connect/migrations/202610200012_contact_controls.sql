-- Nexo Connect 0012: two secure contact-management RPC bridges.
-- A contact unlink MUST revoke both directions of key pinning and pending
-- deliveries. A locked active-contact check on delivery insert serializes
-- sending with unlinking, preventing messages from racing past revocation.
BEGIN;

-- Replace the earlier unlocked approval path. The active contact row must
-- stay locked until a peer fingerprint approval commits. Otherwise a concurrent
-- unlink can delete approvals and a late verifier can reinsert one afterwards.
CREATE OR REPLACE FUNCTION connect.api_approve_peer_key(
 p_owner uuid,p_peer uuid,p_generation bigint,
 p_signing_fp bytea,p_encryption_fp bytea
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE dev record;
BEGIN
 IF p_owner IS NULL OR p_peer IS NULL OR p_owner=p_peer OR
    p_generation IS NULL OR p_generation<=0 OR
    octet_length(p_signing_fp)<>32 OR
    octet_length(p_encryption_fp)<>32 THEN
   RAISE EXCEPTION 'Invalid identity' USING ERRCODE='22023';
 END IF;
 -- FOR SHARE conflicts with unlink's UPDATE of this same row; whichever
 -- operation runs last observes the committed state of the other.
 PERFORM 1 FROM connect.contacts
 WHERE user_a=LEAST(p_owner,p_peer)
   AND user_b=GREATEST(p_owner,p_peer)
   AND status='active'
 FOR SHARE;
 IF NOT FOUND THEN
   RAISE EXCEPTION 'Peer not linked' USING ERRCODE='22023';
 END IF;
 -- Also serialize with replacement of the peer's phone/keys.
 SELECT generation,signing_fingerprint,encryption_fingerprint
 INTO dev FROM connect.devices WHERE user_id=p_peer FOR SHARE;
 IF NOT FOUND OR dev.generation<>p_generation OR
    dev.signing_fingerprint IS DISTINCT FROM p_signing_fp OR
    dev.encryption_fingerprint IS DISTINCT FROM p_encryption_fp THEN
   RAISE EXCEPTION 'Unverified peer key' USING ERRCODE='22023';
 END IF;
 INSERT INTO connect.peer_key_approvals(
   owner_id,peer_id,peer_generation,
   verified_signing_fp,verified_encryption_fp)
 VALUES(p_owner,p_peer,p_generation,p_signing_fp,p_encryption_fp)
 ON CONFLICT(owner_id,peer_id) DO UPDATE SET
   peer_generation=excluded.peer_generation,
   verified_signing_fp=excluded.verified_signing_fp,
   verified_encryption_fp=excluded.verified_encryption_fp,
   verified_at=pg_catalog.now();
END
$fn$;
REVOKE ALL ON FUNCTION connect.api_approve_peer_key(
 uuid,uuid,bigint,bytea,bytea) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION connect.api_approve_peer_key(
 uuid,uuid,bigint,bytea,bytea) TO service_role;

CREATE OR REPLACE FUNCTION connect.api_unlink_contact(p_actor uuid, p_peer uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
BEGIN
 IF p_actor IS NULL OR p_peer IS NULL OR p_actor=p_peer THEN
   RAISE EXCEPTION 'Invalid contact' USING ERRCODE='22023';
 END IF;
 -- UPDATE acquires row lock and is serialized with the delivery guard below.
 UPDATE connect.contacts SET status='revoked'
 WHERE user_a=LEAST(p_actor,p_peer) AND user_b=GREATEST(p_actor,p_peer)
   AND status='active';
 IF NOT FOUND THEN
   RAISE EXCEPTION 'Contact is not active' USING ERRCODE='22023';
 END IF;
 DELETE FROM connect.peer_key_approvals k
 WHERE (k.owner_id=p_actor AND k.peer_id=p_peer)
    OR (k.owner_id=p_peer AND k.peer_id=p_actor);
 WITH cancelled AS (
   UPDATE connect.deliveries d
   SET status='cancelled',resolved_at=pg_catalog.now(),ciphertext=NULL
   WHERE d.status='pending' AND (
     (d.sender_id=p_actor AND d.recipient_id=p_peer)
     OR (d.sender_id=p_peer AND d.recipient_id=p_actor)
   )
   RETURNING d.id
 )
 INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
 SELECT id,p_actor,'cancelled' FROM cancelled;
END
$fn$;

CREATE FUNCTION connect.guard_delivery_active_contact()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
BEGIN
 -- FOR SHARE holds lock until sender's INSERT commits; unlink's UPDATE
 -- obtains conflicting row lock. No pending insert survives an unlink race.
 PERFORM 1 FROM connect.contacts c
 WHERE c.user_a=LEAST(NEW.sender_id,NEW.recipient_id)
   AND c.user_b=GREATEST(NEW.sender_id,NEW.recipient_id)
   AND c.status='active'
 FOR SHARE;
 IF NOT FOUND THEN
  RAISE EXCEPTION 'Recipient contact revoked' USING ERRCODE='22023';
 END IF;
 RETURN NEW;
END
$fn$;
CREATE TRIGGER nexo_connect_guard_delivery_before_insert
 BEFORE INSERT ON connect.deliveries FOR EACH ROW
 EXECUTE FUNCTION connect.guard_delivery_active_contact();

CREATE FUNCTION public.nexo_connect_receive_permission(
 p_actor uuid,p_device uuid,p_generation bigint,p_peer uuid,p_allow boolean
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
BEGIN
 IF p_actor IS NULL OR p_device IS NULL OR p_generation IS NULL
   OR p_peer IS NULL OR p_allow IS NULL THEN
   RAISE EXCEPTION 'Invalid contact permission' USING ERRCODE='22023';
 END IF;
 PERFORM 1 FROM connect.devices d WHERE d.user_id=p_actor
    AND d.device_id=p_device AND d.generation=p_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Old phone rejected' USING ERRCODE='22023'; END IF;
 PERFORM connect.api_set_receive_permission(p_actor,p_peer,p_allow);
END
$fn$;

CREATE FUNCTION public.nexo_connect_unlink_contact(
 p_actor uuid,p_device uuid,p_generation bigint,p_peer uuid
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
BEGIN
 IF p_actor IS NULL OR p_device IS NULL OR p_generation IS NULL
   OR p_peer IS NULL THEN
   RAISE EXCEPTION 'Invalid unlink request' USING ERRCODE='22023';
 END IF;
 PERFORM 1 FROM connect.devices d WHERE d.user_id=p_actor
   AND d.device_id=p_device AND d.generation=p_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Old phone rejected' USING ERRCODE='22023'; END IF;
 PERFORM connect.api_unlink_contact(p_actor,p_peer);
END
$fn$;

REVOKE ALL ON FUNCTION connect.guard_delivery_active_contact()
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_receive_permission(uuid,uuid,bigint,uuid,boolean)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_unlink_contact(uuid,uuid,bigint,uuid)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_receive_permission(uuid,uuid,bigint,uuid,boolean)
 TO service_role;
GRANT EXECUTE ON FUNCTION public.nexo_connect_unlink_contact(uuid,uuid,bigint,uuid)
 TO service_role;
COMMIT;
