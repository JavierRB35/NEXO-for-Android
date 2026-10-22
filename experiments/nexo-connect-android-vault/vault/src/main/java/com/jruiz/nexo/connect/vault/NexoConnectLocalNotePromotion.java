package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jruiz.nexo.connect.NexoConnectNativeWriter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DEBUG/LAB: PROMOTES a manually selected, already authenticated and
 * AES-GCM-sealed received note from Connect's private inbox into the normal
 * Nexo notes database. It never downloads, uploads, sends or ACKs anything.
 *
 * A promotion writes a plaintext native Nexo note BY EXPLICIT CONSENT ONLY.
 * The encrypted source is retained. Native note + durable promotion receipt
 * commit atomically in the TARGET database: retry is idempotent. Two distinct
 * SQLite files cannot participate in the same transaction, so this operation
 * does NOT mutate the source file at all.
 *
 * SECURITY: the incoming sealed copy must have a real receipt, an active
 * QR-pinned sender, AES-GCM binding to the authenticated account+delivery,
 * and its contents must pass the Nexo exchange allowlist again. A nxc2:
 * prefix by itself never authenticates anything. Legacy plaintext pilot
 * records are intentionally excluded.
 */
public final class NexoConnectLocalNotePromotion {
    private static final String SOURCE_LEDGER="nexo_connect_import_receipts";
    private static final String PROMOTION_LEDGER="nexo_connect_native_note_promotions";
    private static final String[] REQUIRED_SOURCE_TABLES={SOURCE_LEDGER,"items"};

    private NexoConnectLocalNotePromotion(){}

    public static final class Received {
        public final UUID deliveryId, senderId;
        public final boolean serverAcknowledged;
        private Received(UUID delivery,UUID sender,boolean acknowledged){
            deliveryId=delivery;senderId=sender;serverAcknowledged=acknowledged;
        }
    }

    public static final class VerifiedNote {
        public final Received source;
        public final String title,body;
        private final JSONObject safeData;
        private VerifiedNote(Received source,JSONObject safeData){
            this.source=source;this.safeData=safeData;
            title=safeData.optString("title","");
            body=safeData.optString("body","");
        }
    }

    public static final class Result {
        public final long nativeNoteId;
        public final boolean alreadyCopied;
        private Result(long id,boolean repeat){nativeNoteId=id;alreadyCopied=repeat;}
    }

    /** List metadata only: never decrypt private contents without selection. */
    public static List<Received> list(SQLiteDatabase source,int limit)throws Exception{
        checkDb(source);
        if(limit<1||limit>100)throw new IllegalArgumentException("Invalid list limit");
        if(!sourceTablesExist(source))return Collections.emptyList();
        List<Received> out=new ArrayList<>();
        try(Cursor c=source.rawQuery(
                "SELECT r.delivery_id,r.sender_id,r.acked FROM "+SOURCE_LEDGER+" r "+
                "JOIN items i ON i.id=r.local_id "+
                "WHERE r.entity_type='note' AND i.type='note' "+
                "AND i.source='nexo-connect' AND i.body LIKE 'nxc2:%' "+
                "ORDER BY r.imported_at DESC,r.delivery_id LIMIT "+limit,null)){
            while(c.moveToNext())
                out.add(new Received(canonical(c.getString(0)),canonical(c.getString(1)),
                        c.getInt(2)==1));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Requires current QR trust and successfully authenticated local AES-GCM.
     * The caller must also independently control whether plaintext is shown.
     */
    public static VerifiedNote read(SQLiteDatabase source,UUID authenticatedAccount,
            UUID delivery,AndroidPeerTrustStore trust)throws Exception{
        checkDb(source);
        if(authenticatedAccount==null||delivery==null||trust==null||
                !authenticatedAccount.equals(trust.ownerAccountId()))
            throw new SecurityException("Verified account/trust store mismatch");
        if(!sourceTablesExist(source))
            throw new SecurityException("No verified Connect receipts");
        try(Cursor c=source.rawQuery(
                "SELECT r.sender_id,r.acked,i.body FROM "+SOURCE_LEDGER+" r "+
                "JOIN items i ON i.id=r.local_id WHERE r.delivery_id=? "+
                "AND r.entity_type='note' AND i.type='note' "+
                "AND i.source='nexo-connect'",new String[]{delivery.toString()})){
            if(!c.moveToFirst())throw new SecurityException("Received note unavailable");
            UUID sender=canonical(c.getString(0));
            if(trust.findActive(sender)==null)
                throw new SecurityException("Sender QR trust missing or revoked");
            String sealed=c.getString(2);
            if(!NexoConnectSealedNoteStore.isSealed(sealed))
                throw new SecurityException("Legacy or unprotected note rejected");
            JSONObject recovered=new NexoConnectSealedNoteStore(
                    authenticatedAccount,delivery).read(sealed);
            // Strict allowlist strips any non-note fields or paths before a
            // normal Nexo note can be shown or copied.
            JSONObject safe=NexoConnectPayloadCodec.sanitize("note",recovered,null);
            if(safe.optString("title","").trim().isEmpty())
                throw new SecurityException("Received note title missing");
            return new VerifiedNote(new Received(delivery,sender,c.getInt(1)==1),safe);
        }
    }

    /**
     * Requires a second EXPLICIT user approval after preview.
     * Re-reads the source and current trust at the time of the action.
     * ACK status in source remains untouched; this is a local-only copy.
     */
    public static Result copyApproved(SQLiteDatabase source,SQLiteDatabase target,
            UUID authenticatedAccount,UUID delivery,AndroidPeerTrustStore trust,
            boolean userApproved)throws Exception {
        if(!userApproved)throw new SecurityException("Native note copy requires consent");
        checkDb(target);
        if(source==target)throw new IllegalArgumentException("Source and target must differ");
        VerifiedNote note=read(source,authenticatedAccount,delivery,trust);
        byte[] serialized=NexoConnectPayloadCodec.encode("note",note.safeData,null);
        String hash=sha256(serialized);
        if(target.inTransaction())
            throw new IllegalStateException("Nested target transaction forbidden");
        target.beginTransactionNonExclusive();
        try {
            target.execSQL("CREATE TABLE IF NOT EXISTS "+PROMOTION_LEDGER+"("+
                    "delivery_id TEXT PRIMARY KEY NOT NULL,"+
                    "recipient_id TEXT NOT NULL,"+
                    "sender_id TEXT NOT NULL,"+
                    "payload_sha256 TEXT NOT NULL,"+
                    "native_note_id INTEGER NOT NULL CHECK(native_note_id>0),"+
                    "promoted_at INTEGER NOT NULL)");
            try(Cursor existing=target.rawQuery(
                    "SELECT recipient_id,sender_id,payload_sha256,native_note_id FROM "+
                    PROMOTION_LEDGER+" WHERE delivery_id=?",
                    new String[]{delivery.toString()})){
                if(existing.moveToFirst()){
                    if(!authenticatedAccount.toString().equals(existing.getString(0))||
                            !note.source.senderId.toString().equals(existing.getString(1))||
                            !hash.equals(existing.getString(2)))
                        throw new SecurityException("Reused delivery with changed source");
                    long old=existing.getLong(3);
                    // Never silently resurrect a copy the user intentionally
                    // deleted or changed into another type.
                    try(Cursor local=target.rawQuery(
                            "SELECT 1 FROM items WHERE id=? AND type='note' "+
                            "AND source='nexo-connect' AND deleted_at=0",
                            new String[]{Long.toString(old)})){
                        if(!local.moveToFirst())
                            throw new SecurityException("Previously copied note was removed");
                    }
                    target.setTransactionSuccessful();
                    return new Result(old,true);
                }
            }
            JSONObject safe=NexoConnectPayloadCodec.decodeDecrypted(serialized)
                    .getJSONObject("data");
            long nativeId=NexoConnectNativeWriter.INSTANCE.insertNew(target,"note",safe);
            target.execSQL("INSERT INTO "+PROMOTION_LEDGER+
                    "(delivery_id,recipient_id,sender_id,payload_sha256,native_note_id,promoted_at) "+
                    "VALUES(?,?,?,?,?,?)",new Object[]{delivery.toString(),
                        authenticatedAccount.toString(),note.source.senderId.toString(),
                        hash,nativeId,System.currentTimeMillis()});
            target.setTransactionSuccessful();
            return new Result(nativeId,false);
        } finally {
            target.endTransaction();
        }
    }

    private static boolean sourceTablesExist(SQLiteDatabase db){
        for(String name:REQUIRED_SOURCE_TABLES){
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{name})){
                if(!c.moveToFirst())return false;
            }
        }
        return true;
    }
    private static UUID canonical(String id){
        if(id==null)throw new SecurityException("Missing receipt UUID");
        try{
            UUID value=UUID.fromString(id);
            if(!value.toString().equals(id))
                throw new SecurityException("Non-canonical receipt UUID");
            return value;
        }catch(IllegalArgumentException invalid){
            throw new SecurityException("Invalid receipt UUID",invalid);
        }
    }
    private static String sha256(byte[] input)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder out=new StringBuilder(64);
        for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b & 255));
        java.util.Arrays.fill(hash,(byte)0);
        return out.toString();
    }
    private static void checkDb(SQLiteDatabase db){
        if(db==null||!db.isOpen())throw new IllegalArgumentException("SQLite unavailable");
    }
}
