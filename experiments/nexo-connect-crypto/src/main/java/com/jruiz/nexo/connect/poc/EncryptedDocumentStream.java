package com.jruiz.nexo.connect.poc;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/**
 * Proof of concept: bounded, authenticated, 64-KiB streaming AES-256-GCM.
 * A random AES key must be transported INSIDE a recipient-specific signed
 * HPKE envelope. Never upload original plaintext to Supabase Storage.
 *
 * Caller must use private temp output and delete it on any failure;
 * successful decryption must not be exposed until verification finishes.
 */
public final class EncryptedDocumentStream {
    public static final int CHUNK = 64 * 1024;
    public static final long MAX_PLAIN = 10L * 1024L * 1024L;
    private static final int TAG_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int HEADER_BYTES = 37;
    private static final int MAGIC = 0x4e434154; // NCAT
    private static final int VERSION = 1;
    private static final SecureRandom RNG = new SecureRandom();

    private EncryptedDocumentStream() {}

    public static byte[] generateKey() {
        byte[] key = new byte[32];
        RNG.nextBytes(key);
        return key;
    }

    public static byte[] sha256(byte[] input) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    public static byte[] encrypt(InputStream input, OutputStream output,
                                 byte[] contentKey, UUID deliveryId,
                                 long declaredPlainSize)
            throws GeneralSecurityException, IOException {
        checkKey(contentKey);
        if (input == null || output == null || deliveryId == null ||
                declaredPlainSize < 1 || declaredPlainSize > MAX_PLAIN)
            throw new GeneralSecurityException("Invalid document or size");

        int count = (int) ((declaredPlainSize + CHUNK - 1) / CHUNK);
        byte[] header = header(deliveryId, declaredPlainSize, count);
        output.write(header);
        byte[] noncePrefix = new byte[8];
        RNG.nextBytes(noncePrefix);
        byte[] plain = new byte[CHUNK];
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(noncePrefix, 0, iv, 0, noncePrefix.length);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long remaining = declaredPlainSize;
        try {
            for (int i = 0; i < count; ++i) {
                int size = (int) Math.min(CHUNK, remaining);
                readExactly(input, plain, size);
                digest.update(plain, 0, size);
                writeInt(iv, 8, i);
                Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, contentKey, iv, header, i);
                byte[] sealed = cipher.doFinal(plain, 0, size);
                output.write(iv);
                output.write(sealed);
                Arrays.fill(sealed, (byte) 0);
                remaining -= size;
            }
            if (input.read() != -1)
                throw new GeneralSecurityException("Declared file size does not match input");
            output.flush();
            return digest.digest();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** Returns the verified plaintext SHA-256; output must be private/temp. */
    public static long decrypt(InputStream encrypted, OutputStream privateTemp,
                               byte[] contentKey, UUID expectedDeliveryId,
                               byte[] expectedPlainSha256)
            throws GeneralSecurityException, IOException {
        checkKey(contentKey);
        if (encrypted == null || privateTemp == null || expectedDeliveryId == null ||
                expectedPlainSha256 == null || expectedPlainSha256.length != 32)
            throw new GeneralSecurityException("Incomplete attachment metadata");
        byte[] header = new byte[HEADER_BYTES];
        readExactly(encrypted, header, header.length);
        DataInputStream structure = new DataInputStream(new java.io.ByteArrayInputStream(header));
        if (structure.readInt() != MAGIC || structure.readUnsignedByte() != VERSION)
            throw new GeneralSecurityException("Unrecognized attachment format");
        UUID actual = new UUID(structure.readLong(), structure.readLong());
        long plainLength = structure.readLong();
        int chunkSize = structure.readInt();
        int count = structure.readInt();
        if (!expectedDeliveryId.equals(actual) || plainLength < 1 ||
                plainLength > MAX_PLAIN || chunkSize != CHUNK ||
                count != (plainLength + CHUNK - 1) / CHUNK)
            throw new GeneralSecurityException("Invalid attachment header");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long remaining = plainLength;
        byte[] firstPrefix = null;
        for (int i = 0; i < count; ++i) {
            int size = (int) Math.min(CHUNK, remaining);
            byte[] iv = new byte[IV_BYTES];
            readExactly(encrypted, iv, iv.length);
            if (i == 0) firstPrefix = Arrays.copyOf(iv, 8);
            if (!MessageDigest.isEqual(firstPrefix, Arrays.copyOf(iv, 8)) ||
                    intFrom(iv, 8) != i)
                throw new GeneralSecurityException("Out-of-sequence attachment chunk");
            byte[] cipherText = new byte[size + TAG_BYTES];
            readExactly(encrypted, cipherText, cipherText.length);
            Cipher cipher = newCipher(Cipher.DECRYPT_MODE, contentKey, iv, header, i);
            byte[] clear = cipher.doFinal(cipherText);
            if (clear.length != size)
                throw new GeneralSecurityException("Decrypted chunk length mismatch");
            privateTemp.write(clear);
            digest.update(clear);
            Arrays.fill(clear, (byte) 0);
            Arrays.fill(cipherText, (byte) 0);
            remaining -= size;
        }
        if (encrypted.read() != -1)
            throw new GeneralSecurityException("Unexpected trailing ciphertext");
        if (!MessageDigest.isEqual(expectedPlainSha256, digest.digest()))
            throw new GeneralSecurityException("Attachment digest mismatch");
        privateTemp.flush();
        return plainLength;
    }

    private static void checkKey(byte[] key) throws GeneralSecurityException {
        if (key == null || key.length != 32)
            throw new GeneralSecurityException("AES-256 document key missing");
    }

    private static byte[] header(UUID id, long size, int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES);
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(MAGIC);
        data.writeByte(VERSION);
        data.writeLong(id.getMostSignificantBits());
        data.writeLong(id.getLeastSignificantBits());
        data.writeLong(size);
        data.writeInt(CHUNK);
        data.writeInt(count);
        data.flush();
        return bytes.toByteArray();
    }

    private static Cipher newCipher(int mode, byte[] key, byte[] iv,
                                    byte[] header, int index)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BYTES * 8, iv));
        cipher.updateAAD(header);
        byte[] counter = new byte[4];
        writeInt(counter, 0, index);
        cipher.updateAAD(counter);
        return cipher;
    }

    private static void writeInt(byte[] bytes, int start, int value) {
        bytes[start] = (byte) (value >>> 24);
        bytes[start + 1] = (byte) (value >>> 16);
        bytes[start + 2] = (byte) (value >>> 8);
        bytes[start + 3] = (byte) value;
    }

    private static int intFrom(byte[] bytes, int start) {
        return ((bytes[start] & 255) << 24) |
                ((bytes[start + 1] & 255) << 16) |
                ((bytes[start + 2] & 255) << 8) |
                (bytes[start + 3] & 255);
    }

    private static void readExactly(InputStream stream, byte[] dest, int size)
            throws IOException {
        int offset = 0;
        while (offset < size) {
            int read = stream.read(dest, offset, size - offset);
            if (read < 0) throw new EOFException("Attachment is truncated");
            if (read > 0) offset += read;
        }
    }
}
