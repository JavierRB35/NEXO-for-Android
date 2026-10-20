-- 0015: the signed NXC1 expiry, not a fixed 30-day retention, governs
-- ciphertext availability. Existing 0003 send API supplies now()+30 days;
-- this BEFORE INSERT trigger sets the exact expiry signed by the sender.
-- The Edge verifier already requires signed issued time near server clock.
BEGIN;
CREATE FUNCTION connect.guard_delivery_signed_expiry()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $fn$
DECLARE
 signed_issued_ms numeric := 0;
 signed_expires_ms numeric := 0;
 i integer;
BEGIN
 IF NEW.context IS NULL OR pg_catalog.octet_length(NEW.context)<>142
  OR pg_catalog.substring(NEW.context FROM 1 FOR 5)
     <>pg_catalog.decode('4e58433101','hex') THEN
  RAISE EXCEPTION 'Invalid signed delivery context' USING ERRCODE='22023';
 END IF;
 -- NXC1 has issuedAt at zero-based offsets 62..69 and expiresAt 70..77,
 -- big-endian UNSIGNED 64-bit. Numeric avoids bigint wraparound.
 FOR i IN 62..69 LOOP
  signed_issued_ms := signed_issued_ms * 256 + pg_catalog.get_byte(NEW.context,i);
 END LOOP;
 FOR i IN 70..77 LOOP
  signed_expires_ms := signed_expires_ms * 256 + pg_catalog.get_byte(NEW.context,i);
 END LOOP;
 IF signed_issued_ms NOT BETWEEN 1 AND 253402300799999
  OR signed_expires_ms NOT BETWEEN 1 AND 253402300799999
  OR signed_expires_ms <= signed_issued_ms
  OR signed_expires_ms-signed_issued_ms > 2592000000
  OR signed_expires_ms <= pg_catalog.extract(epoch FROM pg_catalog.now())*1000
  OR pg_catalog.abs(signed_issued_ms - pg_catalog.extract(epoch FROM pg_catalog.now())*1000)
    > 300000
 THEN
  RAISE EXCEPTION 'Invalid or expired signed TTL' USING ERRCODE='22023';
 END IF;
 NEW.expires_at := pg_catalog.to_timestamp(
    (signed_expires_ms/1000)::double precision);
 RETURN NEW;
END $fn$;

REVOKE ALL ON FUNCTION connect.guard_delivery_signed_expiry()
 FROM PUBLIC,anon,authenticated;
CREATE TRIGGER nexo_connect_signed_expiry_before_insert
 BEFORE INSERT ON connect.deliveries FOR EACH ROW
 EXECUTE FUNCTION connect.guard_delivery_signed_expiry();
COMMIT;
