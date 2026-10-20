-- Nexo Connect / PROTOTIPO de buzón E2EE.
-- NO aplicar en Nexo Familia. No sustituye Auth/Edge y NO verifica firmas.
-- El Gateway debe validar JWT, dispositivo, desafío firmado y contexto NXC1.
BEGIN;

CREATE TABLE connect.peer_key_approvals (
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  peer_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  peer_generation bigint NOT NULL CHECK (peer_generation > 0),
  verified_signing_fp bytea NOT NULL CHECK (octet_length(verified_signing_fp)=32),
  verified_encryption_fp bytea NOT NULL CHECK (octet_length(verified_encryption_fp)=32),
  verified_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_id,peer_id),
  CHECK(owner_id<>peer_id)
);
CREATE TABLE connect.deliveries (
  id uuid PRIMARY KEY,
  client_request_id uuid NOT NULL,
  sender_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  recipient_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  sender_generation bigint NOT NULL CHECK(sender_generation>0),
  recipient_generation bigint NOT NULL CHECK(recipient_generation>0),
  entity_type text NOT NULL CHECK(entity_type IN
    ('appointment','note','list','journal','book','movie','document')),
  ciphertext bytea,
  ciphertext_sha256 bytea NOT NULL CHECK(octet_length(ciphertext_sha256)=32),
  signature bytea NOT NULL CHECK(octet_length(signature) BETWEEN 64 AND 128),
  status text NOT NULL DEFAULT 'pending'
    CHECK(status IN ('pending','accepted','rejected','cancelled','expired')),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  resolved_at timestamptz,
  UNIQUE(sender_id,recipient_id,client_request_id),
  CHECK(sender_id<>recipient_id),
  CHECK(ciphertext IS NULL OR octet_length(ciphertext) BETWEEN 1 AND 524288),
  CHECK(expires_at > created_at AND expires_at <= created_at + interval '30 days'),
  CHECK((status='pending' AND resolved_at IS NULL AND ciphertext IS NOT NULL)
    OR (status<>'pending' AND resolved_at IS NOT NULL))
);
CREATE INDEX connect_deliveries_inbox_idx
  ON connect.deliveries(recipient_id,status,created_at DESC);
CREATE INDEX connect_deliveries_sender_idx
  ON connect.deliveries(sender_id,created_at DESC);
CREATE INDEX connect_deliveries_expiry_idx
  ON connect.deliveries(expires_at) WHERE status='pending';
CREATE TABLE connect.delivery_events (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  delivery_id uuid NOT NULL REFERENCES connect.deliveries(id) ON DELETE CASCADE,
  actor_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  new_status text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  CHECK(new_status IN ('pending','accepted','rejected','cancelled','expired'))
);
CREATE INDEX connect_delivery_events_delivery_idx
  ON connect.delivery_events(delivery_id,occurred_at);

REVOKE ALL ON connect.peer_key_approvals,connect.deliveries,connect.delivery_events
  FROM PUBLIC,anon,authenticated;
GRANT SELECT ON connect.peer_key_approvals,connect.deliveries,connect.delivery_events
  TO authenticated;
ALTER TABLE connect.peer_key_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.delivery_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY connect_approved_keys_read ON connect.peer_key_approvals
  FOR SELECT TO authenticated USING(owner_id=(select auth.uid()));
CREATE POLICY connect_deliveries_read ON connect.deliveries
  FOR SELECT TO authenticated USING
    (sender_id=(select auth.uid()) OR recipient_id=(select auth.uid()));
CREATE POLICY connect_events_read ON connect.delivery_events
  FOR SELECT TO authenticated USING(EXISTS (
    SELECT 1 FROM connect.deliveries d
    WHERE d.id=delivery_events.delivery_id AND
       (d.sender_id=(select auth.uid()) OR d.recipient_id=(select auth.uid()))
  ));

CREATE FUNCTION connect.api_approve_peer_key(
  p_owner uuid,p_peer uuid,p_generation bigint,
  p_signing_fp bytea,p_encryption_fp bytea
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE dev record;
BEGIN
  IF p_owner IS NULL OR p_peer IS NULL OR p_owner=p_peer OR p_generation IS NULL
    THEN RAISE EXCEPTION 'Invalid identity' USING ERRCODE='22023'; END IF;
  IF NOT EXISTS(SELECT 1 FROM connect.contacts
     WHERE user_a=LEAST(p_owner,p_peer) AND user_b=GREATEST(p_owner,p_peer)
       AND status='active') THEN
    RAISE EXCEPTION 'Peer not linked' USING ERRCODE='22023';
  END IF;
  SELECT generation,signing_fingerprint,encryption_fingerprint INTO dev
     FROM connect.devices WHERE user_id=p_peer;
  IF NOT FOUND OR dev.generation<>p_generation OR
     dev.signing_fingerprint IS DISTINCT FROM p_signing_fp OR
     dev.encryption_fingerprint IS DISTINCT FROM p_encryption_fp THEN
    RAISE EXCEPTION 'Unverified peer key' USING ERRCODE='22023';
  END IF;
  INSERT INTO connect.peer_key_approvals(
      owner_id,peer_id,peer_generation,verified_signing_fp,verified_encryption_fp)
    VALUES(p_owner,p_peer,p_generation,p_signing_fp,p_encryption_fp)
    ON CONFLICT(owner_id,peer_id) DO UPDATE SET
       peer_generation=excluded.peer_generation,
       verified_signing_fp=excluded.verified_signing_fp,
       verified_encryption_fp=excluded.verified_encryption_fp,
       verified_at=now();
END
$fn$;

-- El Gateway verifica previamente el contexto canónico, firma Ed25519 del
-- dispositivo principal y existencia de consentimiento explícito por QR.
-- Una confirmación de la app puede ser falsa con un dispositivo comprometido;
-- el servidor no puede comprobar por sí solo una inserción SQLite local.
CREATE FUNCTION connect.api_send_delivery(
  p_id uuid,p_client_request_id uuid,p_sender uuid,p_recipient uuid,
  p_sender_generation bigint,p_recipient_generation bigint,
  p_entity_type text,p_ciphertext bytea,p_signature bytea
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE old_rec record;
DECLARE hash bytea;
BEGIN
  IF p_id IS NULL OR p_client_request_id IS NULL
    OR p_sender IS NULL OR p_recipient IS NULL OR p_sender=p_recipient
    OR p_ciphertext IS NULL OR octet_length(p_ciphertext) NOT BETWEEN 1 AND 524288
    OR p_signature IS NULL OR octet_length(p_signature) NOT BETWEEN 64 AND 128
    OR p_entity_type NOT IN ('appointment','note','list','journal','book','movie','document')
  THEN RAISE EXCEPTION 'Invalid delivery payload' USING ERRCODE='22023'; END IF;
  hash := public.digest(p_ciphertext,'sha256');
  PERFORM pg_catalog.pg_advisory_xact_lock(
      pg_catalog.hashtextextended('nexo-delivery:'||p_sender::text||':'||p_recipient::text||':'||p_client_request_id::text,0));
  SELECT * INTO old_rec FROM connect.deliveries
    WHERE sender_id=p_sender AND recipient_id=p_recipient AND client_request_id=p_client_request_id;
  IF FOUND THEN
    IF old_rec.id IS DISTINCT FROM p_id
      OR old_rec.ciphertext_sha256 IS DISTINCT FROM hash
      OR old_rec.signature IS DISTINCT FROM p_signature
      OR old_rec.entity_type IS DISTINCT FROM p_entity_type
    THEN RAISE EXCEPTION 'Idempotency key used with different payload'
      USING ERRCODE='23505'; END IF;
    RETURN old_rec.id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM connect.contacts
      WHERE user_a=LEAST(p_sender,p_recipient) AND user_b=GREATEST(p_sender,p_recipient)
      AND status='active') THEN
    RAISE EXCEPTION 'Sender not linked to recipient' USING ERRCODE='22023';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM connect.receive_permissions
      WHERE receiver_id=p_recipient AND sender_id=p_sender AND allow_receive) THEN
    RAISE EXCEPTION 'Reception paused' USING ERRCODE='22023';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM connect.devices
      WHERE user_id=p_sender AND generation=p_sender_generation)
    OR NOT EXISTS (SELECT 1 FROM connect.devices
      WHERE user_id=p_recipient AND generation=p_recipient_generation) THEN
    RAISE EXCEPTION 'Device generation revoked' USING ERRCODE='22023';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM connect.peer_key_approvals k
    JOIN connect.devices d ON d.user_id=k.peer_id
      AND d.generation=k.peer_generation
      AND d.signing_fingerprint=k.verified_signing_fp
      AND d.encryption_fingerprint=k.verified_encryption_fp
    WHERE k.owner_id=p_sender AND k.peer_id=p_recipient
      AND k.peer_generation=p_recipient_generation) THEN
    RAISE EXCEPTION 'Recipient key not verified' USING ERRCODE='22023';
  END IF;
  INSERT INTO connect.deliveries(id,client_request_id,sender_id,recipient_id,
    sender_generation,recipient_generation,entity_type,
    ciphertext,ciphertext_sha256,signature,expires_at)
   VALUES(p_id,p_client_request_id,p_sender,p_recipient,
    p_sender_generation,p_recipient_generation,p_entity_type,
    p_ciphertext,hash,p_signature,now()+interval '30 days');
  INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
   VALUES(p_id,p_sender,'pending');
  RETURN p_id;
END
$fn$;

CREATE FUNCTION connect.api_resolve_delivery(
  p_actor uuid,p_id uuid,p_target text
) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE d record;
BEGIN
  IF p_actor IS NULL OR p_id IS NULL OR p_target NOT IN ('accepted','rejected','cancelled')
    THEN RAISE EXCEPTION 'Invalid delivery status' USING ERRCODE='22023'; END IF;
  SELECT * INTO d FROM connect.deliveries WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Unknown delivery' USING ERRCODE='22023'; END IF;
  IF (p_target IN ('accepted','rejected') AND p_actor<>d.recipient_id)
     OR (p_target='cancelled' AND p_actor<>d.sender_id) THEN
    RAISE EXCEPTION 'Not allowed to resolve delivery' USING ERRCODE='42501';
  END IF;
  IF d.status=p_target THEN RETURN d.status; END IF;
  IF d.status<>'pending' THEN
    RAISE EXCEPTION 'Delivery already resolved' USING ERRCODE='22023';
  END IF;
  IF d.expires_at<=now() THEN
    UPDATE connect.deliveries
      SET status='expired',resolved_at=now(),ciphertext=NULL
      WHERE id=p_id;
    INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
      VALUES(p_id,d.recipient_id,'expired');
    RAISE EXCEPTION 'Delivery expired; retry after refresh' USING ERRCODE='22023';
  END IF;
  UPDATE connect.deliveries
   SET status=p_target,resolved_at=now(),ciphertext=NULL
   WHERE id=p_id;
  INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
   VALUES(p_id,p_actor,p_target);
  RETURN p_target;
END
$fn$;

-- Job horario eventual, no instalar aun. Retira ciphertext de caducados.
CREATE FUNCTION connect.api_expire_deliveries(p_limit integer DEFAULT 1000)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE n integer;
BEGIN
  IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 1000 THEN
    RAISE EXCEPTION 'Invalid limit' USING ERRCODE='22023'; END IF;
  WITH expired AS (
    SELECT id FROM connect.deliveries
     WHERE status='pending' AND expires_at<=now()
     ORDER BY expires_at LIMIT p_limit FOR UPDATE SKIP LOCKED
  ),updated AS (
    UPDATE connect.deliveries d
    SET status='expired',resolved_at=now(),ciphertext=NULL
    FROM expired e WHERE d.id=e.id
    RETURNING d.id,d.recipient_id
  ),events AS (
    INSERT INTO connect.delivery_events(delivery_id,actor_id,new_status)
    SELECT id,recipient_id,'expired' FROM updated RETURNING delivery_id
  )
  SELECT count(*) INTO n FROM events;
  RETURN n;
END
$fn$;

-- Exponer SOLO al backend con clave service_role, NUNCA a cliente JWT.
REVOKE ALL ON ALL TABLES IN SCHEMA connect FROM PUBLIC,anon,authenticated;
GRANT SELECT ON connect.profiles,connect.devices,connect.contacts,
  connect.contact_requests,connect.receive_permissions,connect.key_history,
  connect.peer_key_approvals,connect.deliveries,connect.delivery_events
  TO authenticated;
REVOKE ALL ON FUNCTION connect.api_approve_peer_key(uuid,uuid,bigint,bytea,bytea)
  FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION connect.api_send_delivery(uuid,uuid,uuid,uuid,bigint,bigint,text,bytea,bytea)
  FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION connect.api_resolve_delivery(uuid,uuid,text)
  FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION connect.api_expire_deliveries(integer)
  FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION connect.api_approve_peer_key(uuid,uuid,bigint,bytea,bytea)
  TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_send_delivery(uuid,uuid,uuid,uuid,bigint,bigint,text,bytea,bytea)
  TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_resolve_delivery(uuid,uuid,text)
  TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_expire_deliveries(integer)
  TO service_role;
GRANT USAGE ON SCHEMA connect TO service_role;
GRANT SELECT ON connect.peer_key_approvals,connect.deliveries,connect.delivery_events
  TO service_role;
COMMIT;
