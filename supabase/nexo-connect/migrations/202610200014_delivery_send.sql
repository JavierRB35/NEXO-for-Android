-- 0014: server-only bridge for verified signed NXC1 ciphertext deliveries.
-- Do NOT deploy before testing the Edge signature verifier with actual Tink
-- output and testing a two-phone end-to-end import/ack cycle.
BEGIN;
CREATE FUNCTION public.nexo_connect_send_delivery(
 p_sender uuid,p_device uuid,p_generation bigint,
 p_id uuid,p_request uuid,p_recipient uuid,p_recipient_generation bigint,
 p_entity_type text,p_context bytea,p_ciphertext bytea,p_signature bytea
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE sent uuid;
BEGIN
 IF p_sender IS NULL OR p_device IS NULL OR p_generation IS NULL OR p_generation<=0
 OR p_id IS NULL OR p_request IS NULL OR p_recipient IS NULL
 OR p_recipient_generation IS NULL OR p_recipient_generation<=0
 OR p_sender=p_recipient OR p_entity_type IS NULL OR p_context IS NULL
 OR p_ciphertext IS NULL OR p_signature IS NULL
 THEN RAISE EXCEPTION 'Invalid delivery' USING ERRCODE='22023'; END IF;
 -- Hold device rows while sending, preventing replacement racing with send.
 PERFORM 1 FROM connect.devices WHERE user_id=p_sender
   AND device_id=p_device AND generation=p_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Sender device replaced' USING ERRCODE='22023'; END IF;
 PERFORM 1 FROM connect.devices WHERE user_id=p_recipient
   AND generation=p_recipient_generation FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Recipient device replaced' USING ERRCODE='22023'; END IF;
 -- Serialize with unlink and permission pause; guard also rechecks contact
 -- inside delivery INSERT. No recipient key pinning is ever silently changed.
 PERFORM 1 FROM connect.contacts WHERE
   user_a=LEAST(p_sender,p_recipient) AND user_b=GREATEST(p_sender,p_recipient)
   AND status='active' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Contact revoked' USING ERRCODE='22023'; END IF;
 PERFORM 1 FROM connect.receive_permissions
 WHERE receiver_id=p_recipient AND sender_id=p_sender AND allow_receive FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Reception paused' USING ERRCODE='22023'; END IF;
 sent:=connect.api_send_delivery(p_id,p_request,p_sender,p_recipient,
     p_generation,p_recipient_generation,p_entity_type,p_context,p_ciphertext,p_signature);
 RETURN sent;
END $fn$;
REVOKE ALL ON FUNCTION public.nexo_connect_send_delivery(
 uuid,uuid,bigint,uuid,uuid,uuid,bigint,text,bytea,bytea,bytea)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.nexo_connect_send_delivery(
 uuid,uuid,bigint,uuid,uuid,uuid,bigint,text,bytea,bytea,bytea)
 TO service_role;
COMMIT;
