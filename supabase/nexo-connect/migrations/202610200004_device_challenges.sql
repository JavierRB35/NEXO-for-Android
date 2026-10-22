-- ONLY deploy to new Nexo Connect project AFTER authentication gateway is tested.
-- These challenge RPCs are accessible exclusively by service_role.
BEGIN;
CREATE TABLE connect.device_challenges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  generation bigint NOT NULL CHECK (generation>0),
  action text NOT NULL CHECK(action IN
   ('contact/invite','contact/accept','contact/permissions','contact/unlink',
    'key/verify','delivery/send','delivery/resolve','delivery/request-resend')),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL DEFAULT (now()+interval '2 minutes'),
  consumed_at timestamptz,
  CHECK(expires_at>created_at AND expires_at<=created_at+interval '2 minutes')
);
CREATE INDEX device_challenges_cleanup ON connect.device_challenges(expires_at);
REVOKE ALL ON connect.device_challenges FROM PUBLIC, anon, authenticated;
ALTER TABLE connect.device_challenges ENABLE ROW LEVEL SECURITY;
-- Intentionally no RLS policies for client: challenge is an opaque proof.
CREATE FUNCTION connect.api_issue_challenge(
  p_user uuid, p_device uuid, p_generation bigint, p_action text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
DECLARE result uuid;
BEGIN
 IF p_user IS NULL OR p_device IS NULL OR p_generation IS NULL
    OR p_action NOT IN ('contact/invite','contact/accept','contact/permissions',
       'contact/unlink','key/verify','delivery/send','delivery/resolve',
       'delivery/request-resend') THEN
    RAISE EXCEPTION 'Invalid challenge' USING ERRCODE='22023';
 END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
       'nexo-challenge:'||p_user::text,0));
 IF NOT EXISTS(SELECT 1 FROM connect.devices
    WHERE user_id=p_user AND device_id=p_device AND generation=p_generation) THEN
    RAISE EXCEPTION 'Revoked or unknown device' USING ERRCODE='22023';
 END IF;
 IF (SELECT count(*) FROM connect.device_challenges
    WHERE user_id=p_user AND consumed_at IS NULL AND expires_at>now())>=5 THEN
    RAISE EXCEPTION 'Too many outstanding challenges' USING ERRCODE='22023';
 END IF;
 INSERT INTO connect.device_challenges(user_id,device_id,generation,action)
 VALUES(p_user,p_device,p_generation,p_action) RETURNING id INTO result;
 RETURN result;
END $f$;
CREATE FUNCTION connect.api_consume_challenge(
  p_user uuid,p_device uuid,p_generation bigint,p_action text,p_id uuid
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $f$
BEGIN
 UPDATE connect.device_challenges
 SET consumed_at=now()
 WHERE id=p_id AND user_id=p_user AND device_id=p_device
   AND generation=p_generation AND action=p_action
   AND consumed_at IS NULL AND expires_at>now()
   AND EXISTS(SELECT 1 FROM connect.devices
     WHERE user_id=p_user AND device_id=p_device
       AND generation=p_generation);
 RETURN FOUND;
END $f$;

REVOKE ALL ON FUNCTION connect.api_issue_challenge(uuid,uuid,bigint,text)
  FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION connect.api_consume_challenge(uuid,uuid,bigint,text,uuid)
  FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION connect.api_issue_challenge(uuid,uuid,bigint,text)
  TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_consume_challenge(uuid,uuid,bigint,text,uuid)
  TO service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON connect.device_challenges TO service_role;
COMMIT;
