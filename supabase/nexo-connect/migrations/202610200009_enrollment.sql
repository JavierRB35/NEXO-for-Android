-- 0009: OTP-gated device enrollment/replacement SQL backend adapter.
-- NOTE: only callable via service_role after the gateway verifies BOTH
-- Supabase JWT AND a just-consumed email OTP for the SAME Auth user.
BEGIN;
CREATE FUNCTION public.nexo_connect_enroll_device(
 p_user uuid,p_device uuid,p_signing_raw bytea,p_encryption_raw bytea,p_replace boolean
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
DECLARE old_device uuid; old_sign bytea; old_enc bytea; generation bigint;
BEGIN
 IF p_user IS NULL OR p_device IS NULL OR p_replace IS NULL OR
   octet_length(p_signing_raw)<>32 OR octet_length(p_encryption_raw)<>32
 THEN RAISE EXCEPTION 'Invalid enrollment material' USING ERRCODE='22023'; END IF;
 -- Same lock domain as connect.api_register_device; serialization protects
 -- against two concurrent OTP requests racing to replace the same device.
 PERFORM pg_catalog.pg_advisory_xact_lock(
   pg_catalog.hashtextextended('nexo-device:'||p_user::text,0));
 SELECT device_id, signing_public_key, encryption_public_key
  INTO old_device,old_sign,old_enc FROM connect.devices
  WHERE user_id=p_user FOR UPDATE;
 IF FOUND AND p_replace AND
   (old_device=p_device OR old_sign=p_signing_raw OR old_enc=p_encryption_raw)
 THEN RAISE EXCEPTION 'Replacement must use fresh phone and keys' USING ERRCODE='22023'; END IF;
 generation:=connect.api_register_device(
  p_user,p_device,p_signing_raw,p_encryption_raw,
  extensions.digest(p_signing_raw,'sha256'),
  extensions.digest(p_encryption_raw,'sha256'),p_replace
 );
 IF p_replace THEN
   -- Do not inherit trust decisions made by the lost phone.
   DELETE FROM connect.peer_key_approvals WHERE owner_id=p_user;
   UPDATE connect.device_challenges SET consumed_at=pg_catalog.now()
    WHERE user_id=p_user AND consumed_at IS NULL;
 END IF;
 RETURN generation;
END $f$;
REVOKE ALL ON FUNCTION public.nexo_connect_enroll_device(uuid,uuid,bytea,bytea,boolean)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_enroll_device(uuid,uuid,bytea,bytea,boolean)
 TO service_role;
COMMIT;
