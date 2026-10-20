-- Laboratorio aislado de Nexo Connect: NO ejecutar en Nexo Familia.
-- Funciones privilegiadas destinadas UNICAMENTE a backend/Edge Functions.
-- Las funciones NO validan JWT, prueba de posesion, reautenticacion ni antiabuso
-- externo: el gateway DEBE realizar tales verificaciones ANTES de llamarlas.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
GRANT USAGE ON SCHEMA connect TO service_role;
-- Supabase service_role is a privileged backend role, not a client-facing key.
GRANT SELECT ON ALL TABLES IN SCHEMA connect TO service_role;

-- Registro inicial o sustitucion por correo nuevamente verificado.
-- Solo el gateway puede invocar; p_replacement=true NO es una autorizacion.
CREATE FUNCTION connect.api_register_device(
    p_user uuid, p_device uuid, p_signing_key bytea,
    p_encryption_key bytea, p_signing_fingerprint bytea,
    p_encryption_fingerprint bytea, p_replacement boolean DEFAULT false
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $fn$
DECLARE
    old_generation bigint;
    next_generation bigint;
BEGIN
    IF p_user IS NULL OR p_device IS NULL
       OR octet_length(p_signing_key) NOT BETWEEN 32 AND 8192
       OR octet_length(p_encryption_key) NOT BETWEEN 32 AND 8192
       OR p_signing_fingerprint IS DISTINCT FROM public.digest(p_signing_key,'sha256')
       OR p_encryption_fingerprint IS DISTINCT FROM public.digest(p_encryption_key,'sha256') THEN
       RAISE EXCEPTION 'Invalid device key material' USING ERRCODE='22023';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('nexo-device:'||p_user::text, 0));
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id=p_user) THEN
       RAISE EXCEPTION 'Unknown account' USING ERRCODE='22023';
    END IF;
    SELECT generation INTO old_generation FROM connect.devices
    WHERE user_id=p_user FOR UPDATE;
    IF old_generation IS NULL THEN
        IF p_replacement THEN
            RAISE EXCEPTION 'No device to replace' USING ERRCODE='22023';
        END IF;
        next_generation := 1;
        INSERT INTO connect.devices(user_id,device_id,generation,signing_public_key,
            encryption_public_key,signing_fingerprint,encryption_fingerprint)
        VALUES(p_user,p_device,next_generation,p_signing_key,p_encryption_key,
            p_signing_fingerprint,p_encryption_fingerprint);
    ELSE
        IF NOT p_replacement THEN
            RAISE EXCEPTION 'Device exists; explicit verified replacement required' USING ERRCODE='23505';
        END IF;
        next_generation := old_generation+1;
        UPDATE connect.key_history SET revoked_at=pg_catalog.now()
        WHERE user_id=p_user AND revoked_at IS NULL;
        UPDATE connect.devices SET device_id=p_device,generation=next_generation,
            signing_public_key=p_signing_key,encryption_public_key=p_encryption_key,
            signing_fingerprint=p_signing_fingerprint,
            encryption_fingerprint=p_encryption_fingerprint,updated_at=pg_catalog.now()
        WHERE user_id=p_user;
    END IF;
    INSERT INTO connect.key_history(user_id,generation,signing_fingerprint,encryption_fingerprint)
    VALUES(p_user,next_generation,p_signing_fingerprint,p_encryption_fingerprint);
    RETURN next_generation;
END
$fn$;

-- El codigo QR contiene un token generado criptograficamente en el cliente,
-- se sube SOLO su SHA-256. El token en claro NUNCA se guarda en PostgreSQL.
CREATE FUNCTION connect.api_create_invite(
    p_creator uuid, p_token_hash bytea, p_seconds integer DEFAULT 3600
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $fn$
DECLARE invite_id uuid;
BEGIN
    IF p_creator IS NULL OR octet_length(p_token_hash) != 32
       OR p_seconds NOT BETWEEN 300 AND 86400 THEN
        RAISE EXCEPTION 'Invalid invitation' USING ERRCODE='22023';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('nexo-invite:'||p_creator::text, 0));
    IF NOT EXISTS (SELECT 1 FROM connect.devices WHERE user_id=p_creator) THEN
        RAISE EXCEPTION 'Active registered device required' USING ERRCODE='22023';
    END IF;
    IF (SELECT count(*) FROM connect.contact_invites WHERE creator_id=p_creator
        AND consumed_at IS NULL AND expires_at>pg_catalog.now()) >= 5 THEN
        RAISE EXCEPTION 'Too many invitations' USING ERRCODE='22023';
    END IF;
    invite_id := public.gen_random_uuid();
    INSERT INTO connect.contact_invites(id,creator_id,token_sha256,expires_at)
    VALUES(invite_id,p_creator,p_token_hash,pg_catalog.now()+pg_catalog.make_interval(secs=>p_seconds));
    RETURN invite_id;
END
$fn$;

-- La invitacion creada por A + aceptacion consciente del token por B
-- constituye la aprobacion bilateral. Pin criptografico aun pendiente
-- en el cliente: vinculo activo NO equivale a identidad verificada E2EE.
CREATE FUNCTION connect.api_accept_invite(
    p_recipient uuid, p_token_hash bytea
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $fn$
DECLARE
    creator uuid;
    deadline timestamptz;
    used_at timestamptz;
    request_id uuid;
    a uuid;
    b uuid;
BEGIN
    IF p_recipient IS NULL OR octet_length(p_token_hash) != 32 THEN
        RAISE EXCEPTION 'Invalid invitation' USING ERRCODE='22023';
    END IF;
    SELECT creator_id, expires_at, consumed_at
       INTO creator, deadline, used_at
    FROM connect.contact_invites WHERE token_sha256=p_token_hash FOR UPDATE;
    IF creator IS NULL OR used_at IS NOT NULL OR deadline<=pg_catalog.now()
       OR creator=p_recipient THEN
        RAISE EXCEPTION 'Invalid or expired invitation' USING ERRCODE='22023';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM connect.devices WHERE user_id=creator)
       OR NOT EXISTS(SELECT 1 FROM connect.devices WHERE user_id=p_recipient) THEN
        RAISE EXCEPTION 'Both accounts need an active device' USING ERRCODE='22023';
    END IF;
    a := LEAST(creator,p_recipient);
    b := GREATEST(creator,p_recipient);
    IF EXISTS(SELECT 1 FROM connect.contacts WHERE user_a=a AND user_b=b
              AND status='active') THEN
        RAISE EXCEPTION 'Already linked' USING ERRCODE='22023';
    END IF;
    UPDATE connect.contact_invites SET consumed_at=pg_catalog.now() WHERE token_sha256=p_token_hash;
    request_id := public.gen_random_uuid();
    INSERT INTO connect.contact_requests(id,requester_id,recipient_id,status,expires_at)
    VALUES(request_id,creator,p_recipient,'accepted',pg_catalog.now()+interval '1 day');
    INSERT INTO connect.contacts(user_a,user_b,status)
      VALUES(a,b,'active')
      ON CONFLICT(user_a,user_b) DO UPDATE SET status='active',accepted_at=pg_catalog.now();
    -- Nueva vinculacion aprobada por ambas partes reinicia permisos mutuos.
    INSERT INTO connect.receive_permissions(receiver_id,sender_id,allow_receive)
      VALUES (a,b,true),(b,a,true)
      ON CONFLICT(receiver_id,sender_id) DO UPDATE
        SET allow_receive=true,updated_at=pg_catalog.now();
    RETURN request_id;
END
$fn$;

CREATE FUNCTION connect.api_set_receive_permission(
    p_receiver uuid, p_sender uuid, p_allow boolean
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $fn$
BEGIN
    IF p_receiver IS NULL OR p_sender IS NULL OR p_receiver=p_sender OR p_allow IS NULL
       OR NOT EXISTS(
         SELECT 1 FROM connect.contacts
         WHERE user_a=LEAST(p_receiver,p_sender)
           AND user_b=GREATEST(p_receiver,p_sender)
           AND status='active') THEN
        RAISE EXCEPTION 'Contact is not active' USING ERRCODE='22023';
    END IF;
    INSERT INTO connect.receive_permissions(receiver_id,sender_id,allow_receive)
    VALUES(p_receiver,p_sender,p_allow)
    ON CONFLICT(receiver_id,sender_id) DO UPDATE
    SET allow_receive=EXCLUDED.allow_receive,updated_at=pg_catalog.now();
END
$fn$;

CREATE FUNCTION connect.api_unlink_contact(p_actor uuid, p_peer uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $fn$
BEGIN
    IF p_actor IS NULL OR p_peer IS NULL OR p_actor=p_peer THEN
        RAISE EXCEPTION 'Invalid contact' USING ERRCODE='22023';
    END IF;
    UPDATE connect.contacts SET status='revoked'
    WHERE user_a=LEAST(p_actor,p_peer) AND user_b=GREATEST(p_actor,p_peer)
      AND status='active';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Contact is not active' USING ERRCODE='22023';
    END IF;
END
$fn$;

-- No se conceden funciones privilegiadas al cliente JWT ni a PUBLIC.
REVOKE ALL ON FUNCTION connect.api_register_device(uuid,uuid,bytea,bytea,bytea,bytea,boolean) FROM PUBLIC,authenticated,anon;
REVOKE ALL ON FUNCTION connect.api_create_invite(uuid,bytea,integer) FROM PUBLIC,authenticated,anon;
REVOKE ALL ON FUNCTION connect.api_accept_invite(uuid,bytea) FROM PUBLIC,authenticated,anon;
REVOKE ALL ON FUNCTION connect.api_set_receive_permission(uuid,uuid,boolean) FROM PUBLIC,authenticated,anon;
REVOKE ALL ON FUNCTION connect.api_unlink_contact(uuid,uuid) FROM PUBLIC,authenticated,anon;
GRANT EXECUTE ON FUNCTION connect.api_register_device(uuid,uuid,bytea,bytea,bytea,bytea,boolean) TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_create_invite(uuid,bytea,integer) TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_accept_invite(uuid,bytea) TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_set_receive_permission(uuid,uuid,boolean) TO service_role;
GRANT EXECUTE ON FUNCTION connect.api_unlink_contact(uuid,uuid) TO service_role;
COMMIT;
