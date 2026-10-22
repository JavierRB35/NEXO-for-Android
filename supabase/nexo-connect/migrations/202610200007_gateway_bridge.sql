-- Authenticated gateway bridge. No client-side RPC access.
-- Only Edge Function with service role can call these wrappers.
BEGIN;
CREATE FUNCTION public.nexo_connect_device_status(p_user uuid)
RETURNS TABLE(user_id uuid,device_id uuid,generation bigint)
LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $fn$
 SELECT d.user_id,d.device_id,d.generation
 FROM connect.devices d WHERE d.user_id=p_user
$fn$;

CREATE FUNCTION public.nexo_connect_issue_challenge(
 p_user uuid,p_device uuid,p_generation bigint,p_action text
) RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $fn$
 SELECT connect.api_issue_challenge(p_user,p_device,p_generation,p_action)
$fn$;

REVOKE ALL ON FUNCTION public.nexo_connect_device_status(uuid)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.nexo_connect_issue_challenge(uuid,uuid,bigint,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_device_status(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.nexo_connect_issue_challenge(uuid,uuid,bigint,text)
 TO service_role;
COMMIT;
