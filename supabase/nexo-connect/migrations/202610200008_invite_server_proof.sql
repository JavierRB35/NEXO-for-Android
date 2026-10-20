-- Service-only Edge adapters for phone-proof invite creation.
-- The user UUID is ONLY trustworthy after Edge validated Auth.getUser(token).
BEGIN;
CREATE FUNCTION public.nexo_connect_proof_device(p_user uuid)
RETURNS TABLE(user_id uuid, device_id uuid, generation bigint, signing_key_hex text)
LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $f$
 SELECT d.user_id,d.device_id,d.generation,
        pg_catalog.encode(d.signing_public_key,'hex') AS signing_key_hex
 FROM connect.devices d WHERE d.user_id=p_user
$f$;
CREATE FUNCTION public.nexo_connect_consume_proof(
 p_user uuid,p_device uuid,p_generation bigint,p_action text,p_challenge uuid
) RETURNS boolean
LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $f$
 SELECT connect.api_consume_challenge(p_user,p_device,p_generation,p_action,p_challenge)
$f$;
CREATE FUNCTION public.nexo_connect_create_invite(
 p_user uuid,p_token_sha256 bytea,p_seconds integer
) RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $f$
 SELECT connect.api_create_invite(p_user,p_token_sha256,p_seconds)
$f$;

REVOKE ALL ON FUNCTION public.nexo_connect_proof_device(uuid)
 FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_consume_proof(uuid,uuid,bigint,text,uuid)
 FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_create_invite(uuid,bytea,integer)
 FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_proof_device(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.nexo_connect_consume_proof(uuid,uuid,bigint,text,uuid)
 TO service_role;
GRANT EXECUTE ON FUNCTION public.nexo_connect_create_invite(uuid,bytea,integer)
 TO service_role;
COMMIT;
