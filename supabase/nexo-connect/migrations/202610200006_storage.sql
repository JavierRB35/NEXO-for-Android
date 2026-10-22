-- Nexo Connect: Storage privado. Solo se almacenaran archivos cifrados
-- con un protocolo validado en el cliente. NO subir archivos originales.
BEGIN;
INSERT INTO storage.buckets(
  id,name,public,file_size_limit,allowed_mime_types
) VALUES (
  'nexo-connect-ciphertext',
  'nexo-connect-ciphertext',
  false,
  12582912,
  ARRAY['application/octet-stream']::text[]
)
ON CONFLICT (id) DO NOTHING;

-- Fail closed if the bucket has been precreated with unsafe settings.
DO $check$
BEGIN
  IF NOT EXISTS(
    SELECT 1 FROM storage.buckets WHERE id='nexo-connect-ciphertext'
      AND public=false
      AND file_size_limit=12582912
      AND allowed_mime_types=ARRAY['application/octet-stream']::text[]
  ) THEN RAISE EXCEPTION 'Unsafe preexisting Nexo Connect bucket'; END IF;
END $check$;
-- No SELECT, INSERT, UPDATE or DELETE policies are added to storage.objects.
-- Backend service role must authorize each requested ciphertext operation.
COMMIT;
