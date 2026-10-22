package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Laboratorio Fase 0/1: almacén local privado de DOS keysets serializados.
 *
 * Las claves públicas/privadas HPKE y Ed25519 se generan fuera, con Tink.
 * Sus bytes privados solo se persisten dentro de este blob cifrado con
 * una clave AES-256 no exportable del Android Keystore.
 *
 * NO integrar aún en Nexo principal. No implementa revocación remota,
 * cuentas, protocolo de recuperación, ni protección contra malware que
 * ejecute código dentro del proceso desbloqueado.
 */
public final class AndroidIdentityVault {
    private static final int MAGIC = 0x4e43564c; // NCVL
    private static final int VERSION = 1;
    private static final int MAX_KEYSET_BYTES = 32768;
    private static final int MAX_SEALED_BYTES = 131072;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final Context applicationContext;
    private final String alias;
    private final AtomicFile stored;
    private final byte[] aad;

    public AndroidIdentityVault(Context context, UUID accountId) throws GeneralSecurityException {
        if (context == null || accountId == null) throw new IllegalArgumentException("Parámetros nulos");
        applicationContext = context.getApplicationContext();
        byte[] hash = sha256(accountId.toString().getBytes(StandardCharsets.UTF_8));
        String tag = toHex(hash).substring(0, 32);
        alias = "nexo_connect_poc_1_" + tag;
        stored = new AtomicFile(new File(applicationContext.getNoBackupFilesDir(),
                "nexo-connect-identity-" + tag + ".sealed"));
        aad = ("nexo-connect-vault/v1/" + applicationContext.getPackageName() + "/" + accountId)
                .getBytes(StandardCharsets.UTF_8);
    }

    public boolean exists() {
        return stored.getBaseFile().exists();
    }

    /** Fallar si ya existe un almacén para impedir cambios silenciosos de identidad. */
    public synchronized void saveNew(byte[] hpkePrivate, byte[] signingPrivate)
            throws GeneralSecurityException, IOException {
        requireKeyset(hpkePrivate);
        requireKeyset(signingPrivate);
        if (exists()) throw new GeneralSecurityException("Identidad existente: reemplazo explícito necesario");
        SecretKey key = createOrGetMasterKey();
        byte[] raw = encodeKeysets(hpkePrivate, signingPrivate);
        byte[] ciphertext;
        byte[] iv;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            iv = cipher.getIV();
            if (iv == null || iv.length != IV_LENGTH)
                throw new GeneralSecurityException("IV de Keystore incorrecto");
            cipher.updateAAD(aad);
            ciphertext = cipher.doFinal(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
        byte[] blob = sealEnvelope(iv, ciphertext);
        if (blob.length > MAX_SEALED_BYTES)
            throw new GeneralSecurityException("Almacén demasiado grande");
        FileOutputStream output = null;
        try {
            output = stored.startWrite();
            output.write(blob);
            stored.finishWrite(output);
        } catch (IOException | RuntimeException failure) {
            if (output != null) stored.failWrite(output);
            throw failure;
        } finally {
            Arrays.fill(blob, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    /** Si desapareció la clave Keystore, NUNCA regenerarla en la lectura. */
    public synchronized IdentityBytes restore() throws GeneralSecurityException, IOException {
        if (!exists()) throw new GeneralSecurityException("Identidad no existente");
        SecretKey key = existingMasterKey();
        if (key == null) throw new GeneralSecurityException("Clave Keystore perdida: reenviar pendientes");
        byte[] blob = readSealed();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob));
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION)
                throw new GeneralSecurityException("Formato de almacén no compatible");
            int ivSize = in.readUnsignedByte();
            int cipherSize = in.readInt();
            if (ivSize != IV_LENGTH || cipherSize < TAG_BITS / 8
                    || cipherSize > MAX_SEALED_BYTES || in.available() != ivSize + cipherSize)
                throw new GeneralSecurityException("Longitudes inválidas");
            byte[] iv = new byte[ivSize];
            in.readFully(iv);
            byte[] ciphertext = new byte[cipherSize];
            in.readFully(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] plain = cipher.doFinal(ciphertext);
            try {
                return decodeKeysets(plain);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } finally {
            Arrays.fill(blob, (byte) 0);
        }
    }

    /** Eliminación local explícita después de desvinculación. No borra datos remotos. */
    public synchronized void destroy() throws GeneralSecurityException {
        // La eliminación de material cifrado precede a la revocación de la clave.
        stored.delete();
        if (exists()) {
            throw new GeneralSecurityException("No se pudo eliminar almacén cifrado");
        }
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        try { keys.load(null); } catch (IOException e) {
            throw new GeneralSecurityException("No se pudo acceder al Keystore", e);
        } catch (java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Keystore no válido", e);
        }
        if (keys.containsAlias(alias)) keys.deleteEntry(alias);
    }

    // Solo pruebas Android instrumentadas. No devolver esta ruta mediante WebView.
    File localSealedFileForTest() { return stored.getBaseFile(); }

    private static void requireKeyset(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_KEYSET_BYTES)
            throw new GeneralSecurityException("Keyset ausente o demasiado grande");
    }

    private SecretKey existingMasterKey() throws GeneralSecurityException {
        try {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            java.security.Key key = keys.getKey(alias, null);
            return key instanceof SecretKey ? (SecretKey) key : null;
        } catch (IOException | java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Error de Android Keystore", e);
        }
    }

    private SecretKey createOrGetMasterKey() throws GeneralSecurityException {
        SecretKey existing = existingMasterKey();
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }

    private byte[] readSealed() throws GeneralSecurityException, IOException {
        File file = stored.getBaseFile();
        if (file.length() < 10 || file.length() > MAX_SEALED_BYTES)
            throw new GeneralSecurityException("Almacén dañado o demasiado grande");
        byte[] result = new byte[(int) file.length()];
        try (java.io.InputStream stream = stored.openRead()) {
            int offset = 0;
            while (offset < result.length) {
                int n = stream.read(result, offset, result.length - offset);
                if (n < 0) throw new GeneralSecurityException("Lectura truncada");
                offset += n;
            }
            if (stream.read() != -1) throw new GeneralSecurityException("Contenido sobrante");
        }
        return result;
    }

    private static byte[] encodeKeysets(byte[] first, byte[] second) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeInt(first.length);
        out.write(first);
        out.writeInt(second.length);
        out.write(second);
        out.flush();
        return buf.toByteArray();
    }

    private static byte[] sealEnvelope(byte[] iv, byte[] cipher) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(iv.length);
        out.writeInt(cipher.length);
        out.write(iv);
        out.write(cipher);
        out.flush();
        return buf.toByteArray();
    }

    private static IdentityBytes decodeKeysets(byte[] raw)
            throws GeneralSecurityException, IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        if (raw.length < 10) throw new GeneralSecurityException("Identidad truncada");
        int firstLen = in.readInt();
        if (firstLen <= 0 || firstLen > MAX_KEYSET_BYTES || firstLen > in.available())
            throw new GeneralSecurityException("Keyset HPKE inválido");
        byte[] first = new byte[firstLen];
        in.readFully(first);
        int secondLen = in.readInt();
        if (secondLen <= 0 || secondLen > MAX_KEYSET_BYTES || secondLen != in.available()) {
            Arrays.fill(first, (byte) 0);
            throw new GeneralSecurityException("Keyset de firma inválido");
        }
        byte[] second = new byte[secondLen];
        in.readFully(second);
        return new IdentityBytes(first, second);
    }

    private static byte[] sha256(byte[] bytes) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            hex[i * 2] = digits[(bytes[i] >>> 4) & 0xf];
            hex[i * 2 + 1] = digits[bytes[i] & 0xf];
        }
        return new String(hex);
    }

    public static final class IdentityBytes implements AutoCloseable {
        private final byte[] hpke;
        private final byte[] signer;
        private boolean closed;
        private IdentityBytes(byte[] hpke, byte[] signer) {
            this.hpke = hpke;
            this.signer = signer;
        }
        public synchronized byte[] hpkeKeyset() {
            if (closed) throw new IllegalStateException("Claves destruidas");
            return hpke.clone();
        }
        public synchronized byte[] signingKeyset() {
            if (closed) throw new IllegalStateException("Claves destruidas");
            return signer.clone();
        }
        @Override public synchronized void close() {
            Arrays.fill(hpke, (byte) 0);
            Arrays.fill(signer, (byte) 0);
            closed = true;
        }
    }
}
