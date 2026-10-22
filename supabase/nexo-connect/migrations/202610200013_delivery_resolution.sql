-- 0013: privileged bridge from signed device gateway to atomic delivery status.
-- The Edge gateway MUST independently verify JWT, registered-phone Ed25519
-- signature and a single-use challenge for action delivery/resolve.
-- Only service_role may call this RPC; authenticated/anon must never call it.
BEGIN;
CREATE FUNCTION public.nexo_connect_resolve_delivery(
 p_actor uuid,p_device uuid,p_generation bigint,p_delivery uuid,p_target text
) RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE resolved text;
BEGIN
 IF p_actor IS NULL OR p_device IS NULL OR p_generation IS NULL OR
    p_generation<=0 OR p_delivery IS NULL OR p_target IS NULL OR
    p_target NOT IN ('accepted','rejected','cancelled') THEN
    RAISE EXCEPTION 'Invalid delivery resolution' USING ERRCODE='22023';
 END IF;
 -- Serialize a device generation replacement with this database operation;
 -- a proof for an obsolete phone is never enough to change delivery status.
 PERFORM 1 FROM connect.devices d WHERE d.user_id=p_actor
   AND d.device_id=p_device AND d.generation=p_generation FOR SHARE;
 IF NOT FOUND THEN
   RAISE EXCEPTION 'Revoked phone cannot resolve delivery' USING ERRCODE='22023';
 END IF;
 -- Existing resolver locks the delivery FOR UPDATE, enforces sender/recipient
 -- authorization, prevents cross-status changes, deletes ciphertext and
 -- returns 'expired' when the pending delivery already passed its TTL.
 resolved:=connect.api_resolve_delivery(p_actor,p_delivery,p_target);
 RETURN resolved;
END $fn$;
REVOKE ALL ON FUNCTION public.nexo_connect_resolve_delivery(
 uuid,uuid,bigint,uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_resolve_delivery(
 uuid,uuid,bigint,uuid,text) TO service_role;
COMMIT;
