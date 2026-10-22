package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;
import org.json.JSONObject;
import java.util.UUID;

/** Reauthenticates a selected sealed receipt. No network, identity creation or writes. */
public final class NexoConnectReceivedEntity {
    public final UUID sender;
    public final String entity;
    private final JSONObject data;
    private NexoConnectReceivedEntity(UUID sender,String entity,JSONObject data) {
        this.sender=sender; this.entity=entity; this.data=data;
    }
    public JSONObject dataCopy() throws Exception { return new JSONObject(data.toString()); }
    public static NexoConnectReceivedEntity read(SQLiteDatabase db,UUID account,
            UUID delivery,AndroidPeerTrustStore trust) throws Exception {
        if(account==null || delivery==null || trust==null ||
                !account.equals(trust.ownerAccountId()))
            throw new SecurityException("Account/trust mismatch");
        try(Cursor c=db.rawQuery("SELECT r.sender_id,r.entity_type,i.body FROM "+
                "nexo_connect_import_receipts r JOIN items i ON i.id=r.local_id "+
                "WHERE r.delivery_id=? AND i.type='note' AND i.source='nexo-connect'",
                new String[]{delivery.toString()})) {
            if(!c.moveToFirst())throw new SecurityException("Missing receipt");
            UUID sender=UUID.fromString(c.getString(0));
            if(!sender.toString().equals(c.getString(0)) || trust.findActive(sender)==null)
                throw new SecurityException("Sender not verified");
            String entity=c.getString(1);
            JSONObject inner=new NexoConnectSealedNoteStore(account,delivery).read(c.getString(2));
            String protectedType=inner.optString(NexoConnectSealedNoteStore.PACKED_ENTITY,"note");
            if(!entity.equals(protectedType))throw new SecurityException("Receipt type mismatch");
            JSONObject raw="note".equals(entity)?inner:
                    inner.getJSONObject(NexoConnectSealedNoteStore.PACKED_DATA);
            JSONObject safe=NexoConnectPayloadCodec.sanitize(entity,raw,null);
            if(safe.optString("title","").trim().isEmpty())
                throw new SecurityException("Missing title");
            return new NexoConnectReceivedEntity(sender,entity,safe);
        }
    }
}
