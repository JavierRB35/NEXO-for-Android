-- Nexo Connect: indices de claves ajenas, recomendados por asesor Supabase.
-- No se crean accesos públicos ni se modifica Nexo Familia.
BEGIN;
CREATE INDEX IF NOT EXISTS connect_contact_requests_requester_idx
  ON connect.contact_requests(requester_id,status);
CREATE INDEX IF NOT EXISTS connect_delivery_events_actor_idx
  ON connect.delivery_events(actor_id,occurred_at);
CREATE INDEX IF NOT EXISTS connect_device_challenges_user_idx
  ON connect.device_challenges(user_id,expires_at);
CREATE INDEX IF NOT EXISTS connect_peer_key_approvals_peer_idx
  ON connect.peer_key_approvals(peer_id);
CREATE INDEX IF NOT EXISTS connect_receive_permissions_sender_idx
  ON connect.receive_permissions(sender_id);
COMMIT;
