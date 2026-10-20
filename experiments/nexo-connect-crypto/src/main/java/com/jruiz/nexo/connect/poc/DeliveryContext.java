package com.jruiz.nexo.connect.poc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Canonical byte encoding of the PUBLIC, signed E2EE delivery context.
 * All integer fields big endian (DataOutputStream); fixed-length fields,
 * no JSON, optional fields, default charset, string delimiters or maps.
 * This is an offline protocol EXPERIMENT, not a production message parser.
 */
public final class DeliveryContext {
    private static final int MAGIC = 0x4E584331; // "NXC1"
    private static final byte VERSION = 1;
    private static final long MAX_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    public static final int FINGERPRINT_BYTES = 32;
    public static final int ENCODED_SIZE = 142;

    public enum EntityType {
        APPOINTMENT(1), NOTE(2), LIST(3), JOURNAL(4),
        BOOK(5), MOVIE(6), DOCUMENT(7);
        private final int code;
        EntityType(int code) { this.code = code; }
    }

    private final UUID deliveryId;
    private final UUID senderId;
    private final UUID recipientId;
    private final EntityType type;
    private final int senderGeneration;
    private final int recipientGeneration;
    private final long issuedAtEpochMillis;
    private final long expiresAtEpochMillis;
    private final byte[] senderSigningFingerprint;
    private final byte[] recipientEncryptionFingerprint;

    public DeliveryContext(
            UUID deliveryId, UUID senderId, UUID recipientId,
            EntityType type, int senderGeneration, int recipientGeneration,
            long issuedAtEpochMillis, long expiresAtEpochMillis,
            byte[] senderSigningFingerprint, byte[] recipientEncryptionFingerprint) {
        if (deliveryId == null || senderId == null || recipientId == null || type == null
                || senderId.equals(recipientId)) {
            throw new IllegalArgumentException("Missing IDs or invalid self-send");
        }
        if (senderGeneration <= 0 || recipientGeneration <= 0
                || issuedAtEpochMillis <= 0 || expiresAtEpochMillis <= issuedAtEpochMillis
                || expiresAtEpochMillis - issuedAtEpochMillis > MAX_TTL_MS) {
            throw new IllegalArgumentException("Invalid key generations or expiry");
        }
        this.senderSigningFingerprint = fingerprint(senderSigningFingerprint);
        this.recipientEncryptionFingerprint = fingerprint(recipientEncryptionFingerprint);
        this.deliveryId = deliveryId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.type = type;
        this.senderGeneration = senderGeneration;
        this.recipientGeneration = recipientGeneration;
        this.issuedAtEpochMillis = issuedAtEpochMillis;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
    }

    /** Serialize all fields deterministically; do not append user-supplied strings. */
    public byte[] encode() {
        try {
            ByteArrayOutputStream memory = new ByteArrayOutputStream(ENCODED_SIZE);
            DataOutputStream out = new DataOutputStream(memory);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            writeUuid(out, deliveryId);
            writeUuid(out, senderId);
            writeUuid(out, recipientId);
            out.writeByte(type.code);
            out.writeInt(senderGeneration);
            out.writeInt(recipientGeneration);
            out.writeLong(issuedAtEpochMillis);
            out.writeLong(expiresAtEpochMillis);
            out.write(senderSigningFingerprint);
            out.write(recipientEncryptionFingerprint);
            out.flush();
            byte[] encoded = memory.toByteArray();
            if (encoded.length != ENCODED_SIZE)
                throw new IllegalStateException("Unexpected context size");
            return encoded;
        } catch (IOException e) {
            // ByteArrayOutputStream cannot fail with an I/O error.
            throw new IllegalStateException("Failed to encode delivery context", e);
        }
    }

    private static byte[] fingerprint(byte[] data) {
        if (data == null || data.length != FINGERPRINT_BYTES)
            throw new IllegalArgumentException("Fingerprint must be SHA-256 bytes");
        return Arrays.copyOf(data, data.length);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }
}
