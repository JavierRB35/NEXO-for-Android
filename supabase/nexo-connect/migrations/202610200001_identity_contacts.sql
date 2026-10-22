-- Nexo Connect | esquema PROTOTIPO offline, para desplegar ÚNICAMENTE
-- en un proyecto Supabase de prueba, independiente de Nexo Familia.
-- Roles auth.users, authenticated y auth.uid() proceden de Supabase.
-- No se configura una conexión real ni se permite escribir al cliente.
BEGIN;
CREATE SCHEMA IF NOT EXISTS connect;
REVOKE ALL ON SCHEMA connect FROM PUBLIC;
GRANT USAGE ON SCHEMA connect TO authenticated;

CREATE TABLE connect.profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE connect.devices (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id uuid NOT NULL,
    generation bigint NOT NULL CHECK (generation > 0),
    signing_public_key bytea NOT NULL CHECK (octet_length(signing_public_key) BETWEEN 32 AND 8192),
    encryption_public_key bytea NOT NULL CHECK (octet_length(encryption_public_key) BETWEEN 32 AND 8192),
    signing_fingerprint bytea NOT NULL CHECK (octet_length(signing_fingerprint) = 32),
    encryption_fingerprint bytea NOT NULL CHECK (octet_length(encryption_fingerprint) = 32),
    registered_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE connect.contacts (
    user_a uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    user_b uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status text NOT NULL CHECK (status IN ('active','revoked')),
    accepted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_a, user_b),
    CHECK (user_a < user_b)
);
CREATE INDEX connect_contacts_user_b_status_idx ON connect.contacts(user_b,status);
CREATE TABLE connect.contact_requests (
    id uuid PRIMARY KEY,
    requester_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status text NOT NULL CHECK (status IN ('pending','accepted','rejected','expired')),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    CHECK (requester_id <> recipient_id),
    CHECK (expires_at > created_at)
);
CREATE INDEX connect_contact_requests_recipient_idx ON connect.contact_requests(recipient_id,status);
CREATE TABLE connect.contact_invites (
    id uuid PRIMARY KEY,
    creator_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_sha256 bytea NOT NULL UNIQUE CHECK (octet_length(token_sha256) = 32),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);
CREATE INDEX connect_contact_invites_creator_idx ON connect.contact_invites(creator_id,expires_at);
CREATE TABLE connect.receive_permissions (
    receiver_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    allow_receive boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (receiver_id,sender_id),
    CHECK (receiver_id <> sender_id)
);
CREATE TABLE connect.key_history (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    generation bigint NOT NULL CHECK (generation > 0),
    signing_fingerprint bytea NOT NULL CHECK (octet_length(signing_fingerprint)=32),
    encryption_fingerprint bytea NOT NULL CHECK (octet_length(encryption_fingerprint)=32),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (user_id,generation)
);

-- El esquema de intercambio NO otorga SELECT libre ni permisos INSERT/UPDATE/DELETE.
REVOKE ALL ON ALL TABLES IN SCHEMA connect FROM PUBLIC, anon, authenticated;
GRANT SELECT ON
    connect.profiles, connect.devices, connect.contacts,
    connect.contact_requests, connect.receive_permissions, connect.key_history
    TO authenticated;

ALTER TABLE connect.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.contact_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.contact_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.receive_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE connect.key_history ENABLE ROW LEVEL SECURITY;

-- Esquema de confianza: un usuario accede a sus datos y los de un contacto ACTIVO.
-- Las claves nuevas obtenidas en este SELECT se deben contrastar contra un PIN
-- criptográfico local; nunca confiar solamente en un valor actualizado por servidor.
CREATE POLICY connect_contacts_read ON connect.contacts
FOR SELECT TO authenticated USING (
    user_a = (select auth.uid()) OR user_b = (select auth.uid())
);
CREATE POLICY connect_profiles_read ON connect.profiles
FOR SELECT TO authenticated USING (
    user_id = (select auth.uid()) OR EXISTS (
        SELECT 1 FROM connect.contacts c
        WHERE c.status = 'active'
          AND c.user_a = LEAST(profiles.user_id,(select auth.uid()))
          AND c.user_b = GREATEST(profiles.user_id,(select auth.uid()))
    )
);
CREATE POLICY connect_devices_read ON connect.devices
FOR SELECT TO authenticated USING (
    user_id = (select auth.uid()) OR EXISTS (
        SELECT 1 FROM connect.contacts c
        WHERE c.status = 'active'
          AND c.user_a = LEAST(devices.user_id,(select auth.uid()))
          AND c.user_b = GREATEST(devices.user_id,(select auth.uid()))
    )
);
CREATE POLICY connect_requests_read ON connect.contact_requests
FOR SELECT TO authenticated USING (
    requester_id = (select auth.uid()) OR recipient_id = (select auth.uid())
);
-- Sin política SELECT para contact_invites: el hash del token no se devuelve
-- nunca a clientes. La creación/consumo se hará exclusivamente vía API servidor.
CREATE POLICY connect_receive_permissions_read ON connect.receive_permissions
FOR SELECT TO authenticated USING (
    (receiver_id = (select auth.uid()) OR sender_id = (select auth.uid()))
    AND EXISTS (
        SELECT 1 FROM connect.contacts c
        WHERE c.status='active'
          AND c.user_a=LEAST(receive_permissions.receiver_id,receive_permissions.sender_id)
          AND c.user_b=GREATEST(receive_permissions.receiver_id,receive_permissions.sender_id)
    )
);
CREATE POLICY connect_key_history_read ON connect.key_history
FOR SELECT TO authenticated USING (
    user_id = (select auth.uid()) OR EXISTS (
        SELECT 1 FROM connect.contacts c
        WHERE c.status='active'
          AND c.user_a=LEAST(key_history.user_id,(select auth.uid()))
          AND c.user_b=GREATEST(key_history.user_id,(select auth.uid()))
    )
);

-- No se conceden INSERT/UPDATE/DELETE al rol authenticated.
-- La futura API server-side con rol privilegiado deberá verificar OTP,
-- dispositivo vigente, doble consentimiento, firma, cuotas y antifraude;
-- no confiar en RLS como sustituto de validaciones de negocio.
COMMIT;
