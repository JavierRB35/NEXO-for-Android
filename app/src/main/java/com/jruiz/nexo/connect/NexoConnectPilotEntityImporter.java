package com.jruiz.nexo.connect;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * LAB ONLY: transactional copy of a received entity already unsealed, verified
 * against QR-pinned trust, and supplied over the signature-gated pilot Binder.
 * Never called with untrusted plain Intent extras or arbitrary JSON.
 *
 * The caller MUST confirm consent, trusted caller UID, source identity, and
 * receipt authentication before calling. The source Connect inbox is untouched.
 * A native Nexo copy is not additionally protected by Connect's local cipher.
 */
public final class NexoConnectPilotEntityImporter {
    private static final String LEDGER="nexo_connect_pilot_entity_promotions";
    private NexoConnectPilotEntityImporter(){}

    public static final class Result {
        public final long nativeId;
        public final boolean alreadyCopied;
        private Result(long nativeId,boolean alreadyCopied) {
            this.nativeId=nativeId;
            this.alreadyCopied=alreadyCopied;
        }
    }

    public static Result copyApproved(SQLiteDatabase target,UUID account,UUID delivery,
                                      UUID sender,String entity,JSONObject raw,boolean userApproved)
            throws Exception {
        if(!userApproved)throw new SecurityException("User must approve native copy");
        if(target==null || !target.isOpen() || account==null || delivery==null ||
                sender==null || raw==null)
            throw new IllegalArgumentException("Missing authenticated copy");
        if(target.inTransaction())
            throw new IllegalStateException("Nested transaction not allowed");
        String table=tableFor(entity);
        // Preserve existing list receipts, including copies made by previous APKs.
        if("list".equals(entity)) {
            NexoConnectPilotListImporter.Result old=NexoConnectPilotListImporter.copyApproved(
                    target,account,delivery,sender,raw,userApproved);
            return new Result(old.listId,old.alreadyCopied);
        }
        JSONObject safe=NexoConnectPayloadCodec.sanitize(entity,raw,null);
        if(safe.optString("title","").trim().isEmpty())
            throw new SecurityException("Incomplete entity");
        byte[] bytes=NexoConnectPayloadCodec.encode(entity,safe,null);
        if(bytes.length>128*1024)
            throw new SecurityException("Entity exceeds authorized bridge size");
        String digest=sha256(bytes);
        java.util.Arrays.fill(bytes,(byte)0);
        target.beginTransactionNonExclusive();
        try {
            target.execSQL("CREATE TABLE IF NOT EXISTS "+LEDGER+"("+
                    "delivery_id TEXT PRIMARY KEY NOT NULL,"+
                    "account_id TEXT NOT NULL,"+
                    "sender_id TEXT NOT NULL,"+
                    "entity_type TEXT NOT NULL,"+
                    "payload_sha256 TEXT NOT NULL,"+
                    "native_id INTEGER NOT NULL CHECK(native_id>0),"+
                    "promoted_at INTEGER NOT NULL)");
            try(Cursor c=target.rawQuery(
                    "SELECT account_id,sender_id,payload_sha256,native_id,entity_type "+
                    "FROM "+LEDGER+" WHERE delivery_id=?",
                    new String[]{delivery.toString()})){
                if(c.moveToFirst()){
                    if(!account.toString().equals(c.getString(0)) ||
                            !sender.toString().equals(c.getString(1)) ||
                            !digest.equals(c.getString(2)) || !entity.equals(c.getString(4)))
                        throw new SecurityException("Conflicting delivery receipt");
                    long nativeId=c.getLong(3);
                    try(Cursor exists=target.rawQuery(
                            "SELECT 1 FROM "+table+" WHERE id=?",
                            new String[]{Long.toString(nativeId)})){
                        if(!exists.moveToFirst())
                            throw new SecurityException("The imported entity was deleted");
                    }
                    target.setTransactionSuccessful();
                    return new Result(nativeId,true);
                }
            }
            long id=NexoConnectNativeWriter.INSTANCE.insertNew(target,entity,safe);
            target.execSQL("INSERT INTO "+LEDGER+
                    "(delivery_id,account_id,sender_id,payload_sha256,native_id,promoted_at,entity_type)"+
                    " VALUES(?,?,?,?,?,?,?)",
                    new Object[]{delivery.toString(),account.toString(),sender.toString(),
                            digest,id,System.currentTimeMillis(),entity});
            target.setTransactionSuccessful();
            return new Result(id,false);
        }finally {
            target.endTransaction();
        }
    }

    private static String tableFor(String entity) {
        switch(entity) {
            case "note": case "appointment": return "items";
            case "list": return "lists";
            case "journal": return "journal_entries";
            case "book": return "books";
            case "movie": return "media_items";
            default: throw new SecurityException("Unsupported native entity");
        }
    }

    private static String sha256(byte[] data)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
        java.util.Arrays.fill(digest,(byte)0);
        return hex.toString();
    }
}
