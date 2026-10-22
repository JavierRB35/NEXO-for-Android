package com.jruiz.nexo.connect.vault;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import org.json.*;
import java.util.UUID;

public final class NexoConnectChatTransport {
    public static void send(Context c,NexoConnectSignedGatewayClient client,NexoConnectLocalPhoneIdentity identity,
            AndroidPeerTrustStore trust,UUID peer,String text,boolean profile)throws Exception {
        if(NexoConnectMigration.blocked(c))throw new SecurityException("Migration in progress");
        AndroidPeerTrustStore.PeerPin pin=trust.findActive(peer);if(pin==null)throw new SecurityException("Contact unavailable");
        JSONObject payload=NexoConnectChatStore.payload(NexoConnectChatStore.ownName(c,identity.accountId()),text,profile);
        UUID delivery=UUID.randomUUID();
        try(SQLiteDatabase db=NexoConnectMigration.inbox(c)) {
            NexoConnectChatStore.pending(db,identity.accountId(),peer,delivery,payload);
            try{
                client.sendApproved(identity,trust,peer,NexoConnectPinnedKeysets.recipientEncryption(pin),delivery,UUID.randomUUID(),"note",payload,null,System.currentTimeMillis()+24L*60*60*1000,true);
                NexoConnectChatStore.status(db,delivery,"sent");
            }catch(Exception uncertain){NexoConnectChatStore.status(db,delivery,"unknown");throw uncertain;}
        }
    }
    /** Opening Chat opts into receiving messages from already verified people.
     * Non-chat notes are authenticated but rejected BEFORE any local import or ACK. */
    public static int sync(Context c,NexoConnectSignedGatewayClient client,NexoConnectLocalPhoneIdentity identity,
            AndroidPeerTrustStore trust,int generation)throws Exception {
        if(NexoConnectMigration.blocked(c))throw new SecurityException("Migration in progress");
        int received=0;UUID after=null;
        try(SQLiteDatabase db=NexoConnectMigration.inbox(c)){
            for(int page=0;page<5;page++){
                JSONArray pending=client.inbox(20,after);if(pending.length()==0)break;
                for(int i=0;i<pending.length();i++){
                    JSONObject item=pending.getJSONObject(i);UUID delivery=UUID.fromString(item.getString("deliveryId"));after=delivery;
                    if(!"note".equals(item.optString("entityType")))continue;
                    UUID sender=UUID.fromString(item.getString("senderId"));AndroidPeerTrustStore.PeerPin pin=trust.findActive(sender);if(pin==null)continue;
                    JSONObject envelope=client.open(delivery);
                    if(!delivery.toString().equals(envelope.optString("deliveryId")))throw new SecurityException("Wrong delivery");
                    AndroidPeerTrustStore.PeerPin latest=trust.findActive(sender);
                    if(latest==null||latest.generation!=pin.generation||!java.security.MessageDigest.isEqual(latest.signingPublicRaw(),pin.signingPublicRaw()))throw new SecurityException("Contact changed");
                    try{
                        var result=NexoConnectInboxProcessor.receiveChat(db,envelope,identity.hpkePrivate(),NexoConnectPinnedKeysets.senderVerification(latest),sender,latest.generation,identity.accountId(),generation,System.currentTimeMillis());
                        try(Cursor row=db.rawQuery("SELECT body FROM items WHERE id=?",new String[]{Long.toString(result.localId)})){
                            if(!row.moveToFirst())throw new SecurityException();
                            JSONObject body=NexoConnectChatStore.parse(new NexoConnectSealedNoteStore(identity.accountId(),delivery).read(row.getString(0)));
                            NexoConnectChatStore.rememberProfile(c,identity.accountId(),sender,body);
                        }
                        if("accepted".equals(client.resolve(delivery,"accepted")))com.jruiz.nexo.connect.NexoConnectAtomicImporter.markServerAccepted(db,delivery.toString());
                        received++;
                    }catch(NexoConnectInboxProcessor.NotChat ordinaryNote){/* remains in the normal inbox */}
                }
                if(pending.length()<20)break;
            }
        }
        return received;
    }
}
