package com.jruiz.nexo.connect;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transactional import ledger for an ALREADY authenticated, decrypted Nexo
 * Connect envelope. Experimental component: not invoked by current Nexo UI.
 *
 * SECURITY PRECONDITION: the caller MUST verify the sender's locally trusted
 * Ed25519 key, canonical NXC1 context (recipient, generation, expiry, entity),
 * ciphertext integrity and successful HPKE decryption BEFORE calling this.
 * JSON validity is not cryptographic verification. Never write plaintext to
 * Downloads/MediaStore or acknowledge the server before SQLite commits.
 *
 * The EntityWriter must only use the PROVIDED database handle, must create a
 * fresh record (never update a pre-existing record), and MUST NOT start another
 * transaction, write files or issue network requests. Import receipts and the
 * created native record are committed in ONE SQLite transaction.
 */
public final class NexoConnectAtomicImporter {
    private static final String TABLE = "nexo_connect_import_receipts";
    private static final String UUID_RE =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private NexoConnectAtomicImporter() {}

    public interface EntityWriter {
        /** Insert a NEW item into db; return its positive native local ID. */
        long insertNew(SQLiteDatabase db, String entity, JSONObject safeData) throws Exception;
    }

    public static final class Result {
        public final long localId;
        public final boolean alreadyImported;
        public final boolean acknowledgementPending;
        private Result(long localId, boolean alreadyImported, boolean acknowledgementPending) {
            this.localId = localId;
            this.alreadyImported = alreadyImported;
            this.acknowledgementPending = acknowledgementPending;
        }
    }

    /**
     * Returns only AFTER a successful commit. A duplicate delivery ID returns
     * the existing receipt without creating a second item; same ID with
     * different sender/type/payload fails closed. Two distinct delivery IDs
     * always insert two independent records (even if contents are identical).
     */
    public static Result importVerified(SQLiteDatabase db, String deliveryId,
                                        String senderId, String expectedEntity,
                                        byte[] verifiedPlaintext, EntityWriter writer)
            throws Exception {
        if (db == null || !db.isOpen() || writer == null)
            throw new IllegalArgumentException("SQLite or writer unavailable");
        uuid(deliveryId);
        uuid(senderId);
        if (expectedEntity == null || expectedEntity.isEmpty())
            throw new IllegalArgumentException("Missing expected entity");
        // Validate strictly before any database modification.
        JSONObject envelope = NexoConnectPayloadCodec.decodeDecrypted(verifiedPlaintext);
        if (!expectedEntity.equals(envelope.optString("entity", "")))
            throw new SecurityException("Signed context and decrypted entity differ");
        JSONObject safeData = envelope.optJSONObject("data");
        if (safeData == null) throw new SecurityException("Missing safe payload");
        String hash = sha256(verifiedPlaintext);
        if (db.inTransaction()) throw new IllegalStateException("Nested transaction forbidden");
        db.beginTransactionNonExclusive();
        try {
            createLedger(db);
            try (Cursor existing = db.rawQuery("SELECT sender_id, entity_type, payload_sha256, local_id, acked" +
                    " FROM " + TABLE + " WHERE delivery_id=?", new String[]{deliveryId})) {
                if (existing.moveToFirst()) {
                    if (!senderId.equals(existing.getString(0))
                            || !expectedEntity.equals(existing.getString(1))
                            || !hash.equals(existing.getString(2)))
                        throw new SecurityException("Delivery ID reused with different payload");
                    Result result = new Result(existing.getLong(3), true, existing.getInt(4) == 0);
                    db.setTransactionSuccessful();
                    return result;
                }
            }
            long localId = writer.insertNew(db, expectedEntity, safeData);
            if (localId <= 0) throw new IllegalStateException("Entity was not inserted");
            db.execSQL("INSERT INTO " + TABLE +
                    "(delivery_id,sender_id,entity_type,payload_sha256,local_id,acked,imported_at)" +
                    " VALUES(?,?,?,?,?,0,?)",
                    new Object[]{deliveryId,senderId,expectedEntity,hash,localId,System.currentTimeMillis()});
            db.setTransactionSuccessful();
            return new Result(localId, false, true);
        } finally {
            db.endTransaction();
        }
    }

    /** Returns TRUE only when an already-committed import receipt was found. */
    public static boolean markServerAccepted(SQLiteDatabase db, String deliveryId) {
        uuid(deliveryId);
        requireDatabase(db);
        if (db.inTransaction()) throw new IllegalStateException("Nested transaction forbidden");
        db.beginTransactionNonExclusive();
        try {
            createLedger(db);
            db.execSQL("UPDATE " + TABLE + " SET acked=1 WHERE delivery_id=?",
                    new Object[]{deliveryId});
            boolean found;
            try (Cursor c = db.rawQuery("SELECT 1 FROM " + TABLE + " WHERE delivery_id=?",
                    new String[]{deliveryId})) { found = c.moveToFirst(); }
            db.setTransactionSuccessful();
            return found;
        } finally {
            db.endTransaction();
        }
    }

    /** Re-acknowledge these delivery IDs after reconnection, NOT re-import. */
    public static List<String> pendingAcknowledgements(SQLiteDatabase db, int limit) {
        requireDatabase(db);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid limit");
        if (db.inTransaction()) throw new IllegalStateException("Nested transaction forbidden");
        db.beginTransactionNonExclusive();
        try {
            createLedger(db);
            ArrayList<String> result = new ArrayList<>();
            try (Cursor c = db.rawQuery(
                    "SELECT delivery_id FROM " + TABLE +
                    " WHERE acked=0 ORDER BY imported_at, delivery_id LIMIT " + limit, null)) {
                while (c.moveToNext()) result.add(c.getString(0));
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    // Lazy additive table. No DB_VERSION change until the module is connected;
    // used only after a verified payload is ready to be imported.
    private static void createLedger(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "delivery_id TEXT PRIMARY KEY NOT NULL," +
                "sender_id TEXT NOT NULL," +
                "entity_type TEXT NOT NULL," +
                "payload_sha256 TEXT NOT NULL," +
                "local_id INTEGER NOT NULL CHECK(local_id>0)," +
                "acked INTEGER NOT NULL DEFAULT 0 CHECK(acked IN (0,1))," +
                "imported_at INTEGER NOT NULL)");
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b: digest) hex.append(String.format(Locale.ROOT,"%02x",b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable",e);
        }
    }

    private static void uuid(String value) {
        if (value == null || !value.matches(UUID_RE))
            throw new IllegalArgumentException("Invalid delivery or sender ID");
    }

    private static void requireDatabase(SQLiteDatabase db) {
        if (db == null || !db.isOpen()) throw new IllegalArgumentException("Database unavailable");
    }
}
