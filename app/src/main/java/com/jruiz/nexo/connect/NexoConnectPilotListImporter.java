package com.jruiz.nexo.connect;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * LAB ONLY: transactional copy of a received list already unsealed, verified
 * against QR-pinned trust, and supplied over the signature-gated pilot Binder.
 * Never called with untrusted plain Intent extras or arbitrary JSON.
 *
 * The caller MUST confirm consent, trusted caller UID, source identity, and
 * receipt authentication before calling. The source Connect inbox is untouched.
 * A native Nexo copy is not additionally protected by Connect's local cipher.
 */
public final class NexoConnectPilotListImporter {
    private static final String LEDGER="nexo_connect_pilot_list_promotions";
    private NexoConnectPilotListImporter(){}

    public static final class Result {
        public final long listId;
        public final boolean alreadyCopied;
        private Result(long listId,boolean alreadyCopied) {
            this.listId=listId;
            this.alreadyCopied=alreadyCopied;
        }
    }

    public static Result copyApproved(SQLiteDatabase target,UUID account,UUID delivery,
                                      UUID sender,JSONObject raw,boolean userApproved)
            throws Exception {
        if(!userApproved)throw new SecurityException("User must approve native copy");
        if(target==null || !target.isOpen() || account==null || delivery==null ||
                sender==null || raw==null)
            throw new IllegalArgumentException("Missing authenticated copy");
        if(target.inTransaction())
            throw new IllegalStateException("Nested transaction not allowed");
        JSONObject safe=NexoConnectPayloadCodec.sanitize(
                "list",raw,raw.optJSONArray("entries"));
        if(safe.optString("title","").trim().isEmpty() ||
                safe.optJSONArray("entries")==null)
            throw new SecurityException("Incomplete list");
        byte[] bytes=NexoConnectPayloadCodec.encode("list",safe,null);
        if(bytes.length>128*1024)
            throw new SecurityException("List exceeds authorized bridge size");
        String digest=sha256(bytes);
        java.util.Arrays.fill(bytes,(byte)0);
        target.beginTransactionNonExclusive();
        try {
            target.execSQL("CREATE TABLE IF NOT EXISTS "+LEDGER+"("+
                    "delivery_id TEXT PRIMARY KEY NOT NULL,"+
                    "account_id TEXT NOT NULL,"+
                    "sender_id TEXT NOT NULL,"+
                    "payload_sha256 TEXT NOT NULL,"+
                    "native_list_id INTEGER NOT NULL CHECK(native_list_id>0),"+
                    "promoted_at INTEGER NOT NULL)");
            try(Cursor c=target.rawQuery(
                    "SELECT account_id,sender_id,payload_sha256,native_list_id "+
                    "FROM "+LEDGER+" WHERE delivery_id=?",
                    new String[]{delivery.toString()})){
                if(c.moveToFirst()){
                    if(!account.toString().equals(c.getString(0)) ||
                            !sender.toString().equals(c.getString(1)) ||
                            !digest.equals(c.getString(2)))
                        throw new SecurityException("Conflicting delivery receipt");
                    long nativeId=c.getLong(3);
                    try(Cursor exists=target.rawQuery(
                            "SELECT 1 FROM lists WHERE id=?",
                            new String[]{Long.toString(nativeId)})){
                        if(!exists.moveToFirst())
                            throw new SecurityException("The imported list was deleted");
                    }
                    target.setTransactionSuccessful();
                    return new Result(nativeId,true);
                }
            }
            long id=NexoConnectNativeWriter.INSTANCE.insertNew(target,"list",safe);
            target.execSQL("INSERT INTO "+LEDGER+
                    "(delivery_id,account_id,sender_id,payload_sha256,native_list_id,promoted_at)"+
                    " VALUES(?,?,?,?,?,?)",
                    new Object[]{delivery.toString(),account.toString(),sender.toString(),
                            digest,id,System.currentTimeMillis()});
            target.setTransactionSuccessful();
            return new Result(id,false);
        }finally {
            target.endTransaction();
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
