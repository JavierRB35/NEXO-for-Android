-- 0011: reciprocal QR key trust confirmation, service-only.
-- Edge MUST verify real JWT, correct device Ed25519 signature and consume
-- a challenge for action key/verify before calling this RPC.
BEGIN;
CREATE FUNCTION public.nexo_connect_verify_peer(
 p_owner uuid,p_owner_device uuid,p_owner_generation bigint,
 p_peer uuid,p_peer_generation bigint,p_signing_fp bytea,p_encryption_fp bytea
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
BEGIN
 IF p_owner IS NULL OR p_peer IS NULL OR p_owner=p_peer
   OR p_owner_device IS NULL OR p_owner_generation IS NULL
   OR p_peer_generation IS NULL OR octet_length(p_signing_fp)<>32
   OR octet_length(p_encryption_fp)<>32 THEN
   RAISE EXCEPTION 'Invalid key verification' USING ERRCODE='22023';
 END IF;
 IF NOT EXISTS(
   SELECT 1 FROM connect.devices d
   WHERE d.user_id=p_owner AND d.device_id=p_owner_device
     AND d.generation=p_owner_generation FOR SHARE
 ) THEN RAISE EXCEPTION 'Device revoked' USING ERRCODE='22023'; END IF;
 -- api_approve_peer_key additionally verifies active bilateral contact
 -- and fingerprints against currently registered peer device.
 PERFORM connect.api_approve_peer_key(
   p_owner,p_peer,p_peer_generation,p_signing_fp,p_encryption_fp);
END $f$;
REVOKE ALL ON FUNCTION public.nexo_connect_verify_peer(
 uuid,uuid,bigint,uuid,bigint,bytea,bytea)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_verify_peer(
 uuid,uuid,bigint,uuid,bigint,bytea,bytea) TO service_role;
COMMIT;
