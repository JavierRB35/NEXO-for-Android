package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jruiz.nexo.connect.NexoConnectNativeWriter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * LAB ONLY: explicitly copies an ALREADY RECEIVED and sealed list from the
 * private Connect inbox to the native Nexo Lists and list_entries tables.
 *
 * The source database is read-only to this operation; its cryptographic receipt
 * was committed by the signed, QR-pinned HPKE inbox processor beforehand.
 * Target list + item rows + durable promotion receipt are one SQLite transaction.
 * No network activity, identity migration, implicit ACK or filesystem export.
 *
 * Native lists are intentionally plaintext like other Nexo lists. The UI MUST
 * ask for separate informed consent before creating this copy.
 */
public final class NexoConnectLocalListPromotion {
    private static final String SOURCE_LEDGER="nexo_connect_import_receipts";
    private static final String PROMOTION_LEDGER="nexo_connect_native_list_promotions";

    private NexoConnectLocalListPromotion(){}

    public static final class Received {
        public final UUID deliveryId,senderId;
        public final boolean serverAcknowledged;
        private Received(UUID deliveryId,UUID senderId,boolean serverAcknowledged){
            this.deliveryId=deliveryId;
            this.senderId=senderId;
            this.serverAcknowledged=serverAcknowledged;
        }
    }

    public static final class VerifiedList {
        public final Received source;
        public final String title,kind;
        public final JSONArray entries;
        private final JSONObject safeData;
        // Only the pilot's authenticated, signature-protected service may
        // expose this previously unsealed copy to the trusted native host.
        JSONObject dataCopy() throws org.json.JSONException {
            return new JSONObject(safeData.toString());
        }
        private VerifiedList(Received source,JSONObject safeData){
            this.source=source;
            this.safeData=safeData;
            this.title=safeData.optString("title","");
            this.kind=safeData.optString("kind","General");
            this.entries=safeData.optJSONArray("entries");
        }
    }

    public static final class Result {
        public final long nativeListId;
        public final boolean alreadyCopied;
        private Result(long id,boolean alreadyCopied){
            nativeListId=id;
            this.alreadyCopied=alreadyCopied;
        }
    }

    /** Lists only receipt metadata without decrypting private contents. */
    public static List<Received> list(SQLiteDatabase inbox,int limit)throws Exception{
        checkDb(inbox);
        if(limit<1||limit>100)throw new IllegalArgumentException("Invalid limit");
        if(!tablesExist(inbox))return Collections.emptyList();
        List<Received> output=new ArrayList<>();
        try(Cursor c=inbox.rawQuery(
                "SELECT r.delivery_id,r.sender_id,r.acked FROM "+SOURCE_LEDGER+" r "+
                "JOIN items i ON i.id=r.local_id "+
                "WHERE r.entity_type='list' AND i.type='note' "+
                "AND i.source='nexo-connect' "+
                "AND i.body LIKE 'nxc2:%' "+
                "ORDER BY r.imported_at DESC,r.delivery_id LIMIT "+limit,null)){
            while(c.moveToNext()){
                output.add(new Received(uuid(c.getString(0)),
                        uuid(c.getString(1)),c.getInt(2)==1));
            }
        }
        return Collections.unmodifiableList(output);
    }

    /**
     * A source receipt alone is not enough: verify current QR trust, Keystore
     * GCM account+delivery binding and the typed protected payload.
     */
    public static VerifiedList read(SQLiteDatabase inbox,UUID account,UUID delivery,
                                    AndroidPeerTrustStore trust)throws Exception{
        checkDb(inbox);
        if(account==null||delivery==null||trust==null||
                !account.equals(trust.ownerAccountId()))
            throw new SecurityException("Account and QR trust store mismatch");
        if(!tablesExist(inbox))throw new SecurityException("No received list ledger");
        try(Cursor c=inbox.rawQuery(
                "SELECT r.sender_id,r.acked,i.body FROM "+SOURCE_LEDGER+" r "+
                "JOIN items i ON i.id=r.local_id WHERE r.delivery_id=? "+
                "AND r.entity_type='list' AND i.type='note' "+
                "AND i.source='nexo-connect'",
                new String[]{delivery.toString()})){
            if(!c.moveToFirst())throw new SecurityException("List receipt not found");
            UUID sender=uuid(c.getString(0));
            if(trust.findActive(sender)==null)
                throw new SecurityException("Sender no longer verified");
            String ciphertext=c.getString(2);
            if(!NexoConnectSealedNoteStore.isSealed(ciphertext))
                throw new SecurityException("Unprotected legacy list rejected");
            JSONObject sealed=new NexoConnectSealedNoteStore(account,delivery)
                    .read(ciphertext);
            if(!"list".equals(sealed.optString(NexoConnectSealedNoteStore.PACKED_ENTITY,"")))
                throw new SecurityException("Sealed entity and receipt differ");
            JSONObject data=sealed.optJSONObject(NexoConnectSealedNoteStore.PACKED_DATA);
            if(data==null)throw new SecurityException("Missing list data");
            JSONObject safe=NexoConnectPayloadCodec.sanitize("list",data,null);
            if(safe.optString("title","").trim().isEmpty()||
                    safe.optJSONArray("entries")==null)
                throw new SecurityException("Incomplete list");
            return new VerifiedList(new Received(delivery,sender,c.getInt(1)==1),safe);
        }
    }

    /**
     * Explicit second consent. The source is re-read and reauthenticated
     * immediately before native insertion to reject revoked or changed data.
     * Deleting a previously copied list never silently recreates it.
     */
    public static Result copyApproved(SQLiteDatabase inbox,SQLiteDatabase target,
            UUID account,UUID delivery,AndroidPeerTrustStore trust,
            boolean userApproved)throws Exception{
        if(!userApproved)throw new SecurityException("Explicit native list consent required");
        checkDb(target);
        if(inbox==target)throw new IllegalArgumentException("Source and destination are the same");
        VerifiedList safe=read(inbox,account,delivery,trust);
        byte[] encoded=NexoConnectPayloadCodec.encode("list",safe.safeData,null);
        String hash=sha256(encoded);
        if(target.inTransaction())
            throw new IllegalStateException("Nested native transaction rejected");
        target.beginTransactionNonExclusive();
        try {
            target.execSQL("CREATE TABLE IF NOT EXISTS "+PROMOTION_LEDGER+"("+
                    "delivery_id TEXT PRIMARY KEY NOT NULL,"+
                    "recipient_id TEXT NOT NULL,"+
                    "sender_id TEXT NOT NULL,"+
                    "payload_sha256 TEXT NOT NULL,"+
                    "native_list_id INTEGER NOT NULL CHECK(native_list_id>0),"+
                    "promoted_at INTEGER NOT NULL)");
            try(Cursor c=target.rawQuery(
                    "SELECT recipient_id,sender_id,payload_sha256,native_list_id "+
                    "FROM "+PROMOTION_LEDGER+" WHERE delivery_id=?",
                    new String[]{delivery.toString()})){
                if(c.moveToFirst()){
                    if(!account.toString().equals(c.getString(0))||
                            !safe.source.senderId.toString().equals(c.getString(1))||
                            !hash.equals(c.getString(2)))
                        throw new SecurityException("Receipt replay with changed list");
                    long id=c.getLong(3);
                    try(Cursor existing=target.rawQuery(
                            "SELECT 1 FROM lists WHERE id=?",
                            new String[]{Long.toString(id)})){
                        if(!existing.moveToFirst())
                            throw new SecurityException("Previously copied list was deleted");
                    }
                    target.setTransactionSuccessful();
                    return new Result(id,true);
                }
            }
            JSONObject data=NexoConnectPayloadCodec.decodeDecrypted(encoded)
                    .getJSONObject("data");
            long listId=NexoConnectNativeWriter.INSTANCE.insertNew(target,"list",data);
            target.execSQL("INSERT INTO "+PROMOTION_LEDGER+
                    "(delivery_id,recipient_id,sender_id,payload_sha256,native_list_id,promoted_at) "+
                    "VALUES(?,?,?,?,?,?)",new Object[]{delivery.toString(),
                        account.toString(),safe.source.senderId.toString(),
                        hash,listId,System.currentTimeMillis()});
            target.setTransactionSuccessful();
            return new Result(listId,false);
        }finally{
            target.endTransaction();
        }
    }

    private static boolean tablesExist(SQLiteDatabase db) {
        for(String name:new String[]{SOURCE_LEDGER,"items"}){
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{name})){
                if(!c.moveToFirst())return false;
            }
        }
        return true;
    }

    private static UUID uuid(String text){
        if(text==null)throw new SecurityException("Missing UUID");
        try{
            UUID value=UUID.fromString(text);
            if(!value.toString().equals(text))
                throw new SecurityException("Non-canonical UUID");
            return value;
        }catch(IllegalArgumentException ex){
            throw new SecurityException("Invalid UUID",ex);
        }
    }

    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest)result.append(String.format(Locale.ROOT,"%02x",b&255));
        java.util.Arrays.fill(digest,(byte)0);
        return result.toString();
    }

    private static void checkDb(SQLiteDatabase db){
        if(db==null||!db.isOpen())throw new IllegalArgumentException("Database unavailable");
    }
}
