package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Two actual Tink identities; no mocked decryption, SQLite writer or QR pin.
 * The ONLY fake is the unavailable remote Supabase transport. This is NOT a
 * deployed two-account network test.
 */
@RunWith(AndroidJUnit4.class)
public class NexoConnectTwoAccountExchangeTest {
    private static final long NOW=1780000000000L;
    private static final Context APP=
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static byte[] decode(String value){
        return Base64.decode(value,Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
    }
    private static String hex(byte[] bytes){
        char[] d="0123456789abcdef".toCharArray(),out=new char[2*bytes.length];
        for(int i=0;i<bytes.length;i++){
            out[i*2]=d[(bytes[i]>>>4)&15];out[i*2+1]=d[bytes[i]&15];
        }
        return new String(out);
    }
    private static SQLiteDatabase db(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE items("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "type TEXT NOT NULL DEFAULT 'note',"+
                "title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',"+
                "original_text TEXT NOT NULL DEFAULT '',"+
                "category TEXT NOT NULL DEFAULT 'Otros',tags TEXT NOT NULL DEFAULT '',"+
                "favorite INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'manual',"+
                "note_title_manual INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }
    private static int rows(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM items",null)){
            c.moveToFirst();return c.getInt(0);
        }
    }
    private static String receivedTitle(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT title FROM items LIMIT 1",null)){
            assertTrue(c.moveToFirst());return c.getString(0);
        }
    }

    private static class FakeGateway implements NexoConnectInboxCoordinator.Gateway{
        JSONObject sealed;UUID id;int opens,acks;boolean failAck;
        @Override public JSONObject open(UUID deliveryId)throws Exception{
            opens++;
            if(sealed==null||!id.equals(deliveryId))
                throw new SecurityException("Missing or wrong server packet");
            byte[] cipher=decode(sealed.getString("ciphertext"));
            byte[] context=decode(sealed.getString("context"));
            return new JSONObject().put("deliveryId",id.toString())
                    .put("senderId",sender.accountId().toString())
                    .put("recipientId",recipient.accountId().toString())
                    .put("senderGeneration",1).put("recipientGeneration",1)
                    .put("entityType","note")
                    .put("expiresAtMs",ByteBuffer.wrap(context).getLong(70))
                    .put("contextHex",hex(context))
                    .put("ciphertextHex",hex(cipher))
                    .put("ciphertextSha256Hex",hex(MessageDigest
                            .getInstance("SHA-256").digest(cipher)))
                    .put("signatureHex",hex(decode(sealed.getString("signature"))));
        }
        @Override public String accept(UUID deliveryId)throws Exception{
            acks++;
            if(!id.equals(deliveryId))throw new SecurityException("Wrong delivery");
            if(failAck)throw new java.io.IOException("offline after commit");
            return "accepted";
        }
        NexoConnectLocalPhoneIdentity sender,recipient;
    }
    private static class Fixture implements AutoCloseable {
        final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
        final NexoConnectLocalPhoneIdentity sender,receiver;
        final AndroidPeerTrustStore sendPins,receivePins;
        final KeysetHandle receiverPublic,senderPublic;
        final FakeGateway network=new FakeGateway();
        final NexoConnectInboxCoordinator coordinator;
        Fixture()throws Exception{
            sender=NexoConnectLocalPhoneIdentity.openOrCreate(APP,a);
            receiver=NexoConnectLocalPhoneIdentity.openOrCreate(APP,b);
            sendPins=new AndroidPeerTrustStore(APP,a);
            receivePins=new AndroidPeerTrustStore(APP,b);
            AndroidPeerTrustStore.PeerPin publicA=
                NexoConnectOwnQrView.publicIdentity(a,1,
                    sender.signingPrivate(),sender.hpkePrivate());
            AndroidPeerTrustStore.PeerPin publicB=
                NexoConnectOwnQrView.publicIdentity(b,1,
                    receiver.signingPrivate(),receiver.hpkePrivate());
            // In a real app: fingerprints must be independently compared by
            // a person on the other device, not programmatically prefilled.
            String aQr=AndroidPeerTrustStore.encodeQr(publicA);
            String bQr=AndroidPeerTrustStore.encodeQr(publicB);
            receivePins.saveQrConfirmed(aQr,publicA.signingFingerprint(),
                    publicA.encryptionFingerprint(),true);
            sendPins.saveQrConfirmed(bQr,publicB.signingFingerprint(),
                    publicB.encryptionFingerprint(),true);
            receiverPublic=receiver.hpkePrivate().getPublicKeysetHandle();
            senderPublic=sender.signingPrivate().getPublicKeysetHandle();
            network.sender=sender;network.recipient=receiver;network.id=id;
            coordinator=new NexoConnectInboxCoordinator(
                    network,NexoConnectInboxProcessor::receiveAndImport);
        }
        JSONObject seal()throws Exception{
            JSONObject envelope=NexoConnectSealedDelivery.sealApproved(sender,1,
                    sendPins,b,receiverPublic,id,UUID.randomUUID(),
                    "note",new JSONObject().put("title","Privada entre cuentas")
                            .put("body","Mensaje cifrado entre A y B"),null,
                    NOW-1000,NOW+120000,true);
            network.sealed=envelope;
            return envelope;
        }
        NexoConnectInboxCoordinator.ImportOutcome importOnce(SQLiteDatabase db,
                boolean approved)throws Exception {
            return coordinator.importApprovedFromTrust(db,id,approved,
                    receiver.hpkePrivate(),senderPublic,receivePins,
                    a,b,1,NOW);
        }
        @Override public void close()throws Exception{
            new AndroidIdentityVault(APP,a).destroy();
            new AndroidIdentityVault(APP,b).destroy();
            sendPins.revoke(b);receivePins.revoke(a);
        }
    }

    @Test public void twoIndependentAccountsSealDecryptImportAndAckOnlyAfterCommit()
            throws Exception {
        try(Fixture f=new Fixture();SQLiteDatabase db=db()){
            JSONObject sealed=f.seal();
            assertEquals(8,sealed.length());
            assertFalse(sealed.toString().contains("Privada entre cuentas"));
            assertFalse(sealed.toString().contains("Mensaje cifrado"));
            assertEquals(f.id.toString(),sealed.getString("deliveryId"));
            NexoConnectInboxCoordinator.ImportOutcome first=f.importOnce(db,true);
            assertEquals(1,rows(db));
            assertEquals("Privada entre cuentas",receivedTitle(db));
            assertFalse(first.alreadyImported);
            assertTrue(first.acknowledged);
            assertEquals(1,f.network.acks);
            NexoConnectInboxCoordinator.ImportOutcome repeated=f.importOnce(db,true);
            assertTrue(repeated.alreadyImported);
            assertEquals(first.localId,repeated.localId);
            assertEquals(1,rows(db));
            assertEquals(2,f.network.acks);
        }
    }

    @Test public void networkAckFailureKeepsOneCopyAndAllowsAckOnlyRetry()throws Exception{
        try(Fixture f=new Fixture();SQLiteDatabase db=db()){
            f.seal();f.network.failAck=true;
            NexoConnectInboxCoordinator.ImportOutcome accepted=f.importOnce(db,true);
            assertEquals(1,rows(db));
            assertFalse(accepted.acknowledged);
            assertEquals("ack-pending",accepted.serverStatus);
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(db,10).size());
            f.network.failAck=false;
            NexoConnectInboxCoordinator.RetryOutcome retried=
                    f.coordinator.retryAcknowledgements(db,10);
            assertEquals(1,retried.acceptedCount);
            assertEquals(1,rows(db));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
            assertEquals(2,f.network.acks);
        }
    }
    @Test public void revokedRecipientOrUnapprovedSendNeverProducesEnvelope()
            throws Exception {
        try(Fixture f=new Fixture()){
            f.sendPins.revoke(f.b);
            try{f.seal();fail("Sent to revoked contact");}
            catch(SecurityException expected){}
            assertNull(f.network.sealed);
        }
        try(Fixture f=new Fixture()){
            try{NexoConnectSealedDelivery.sealApproved(f.sender,1,f.sendPins,
                    f.b,f.receiverPublic,f.id,UUID.randomUUID(),"note",
                    new JSONObject().put("title","No"),null,
                    NOW,NOW+60000,false);
                fail("Sent without UI consent");}
            catch(SecurityException expected){}
        }
    }
    @Test public void forgedRecipientPublicKeyAndMutatedCiphertextNeverImport()
            throws Exception {
        try(Fixture f=new Fixture();SQLiteDatabase db=db()){
            KeysetHandle impostor=NexoConnectLocalPhoneIdentity.openOrCreate(
                    APP,UUID.randomUUID()).hpkePrivate().getPublicKeysetHandle();
            try{NexoConnectSealedDelivery.sealApproved(f.sender,1,f.sendPins,
                    f.b,impostor,f.id,UUID.randomUUID(),"note",
                    new JSONObject().put("title","No"),null,
                    NOW,NOW+60000,true);
                fail("Unverified receiver encryption key");}
            catch(GeneralSecurityException expected){}
            f.seal();
            String old=f.network.sealed.getString("signature");
            f.network.sealed.put("signature",old.replace(old.substring(0,1),"A"));
            try{f.importOnce(db,true);fail("Forged signature imported");}
            catch(Exception expected){}
            assertEquals(0,rows(db));
            assertEquals(0,f.network.acks);
        }
    }
    @Test public void revokedSenderOrMissingConsentPreventsReceiverImport()
            throws Exception{
        try(Fixture f=new Fixture();SQLiteDatabase db=db()){
            f.seal();
            try{f.importOnce(db,false);fail("Unapproved download");}
            catch(SecurityException expected){}
            assertEquals(0,f.network.opens);
            f.receivePins.revoke(f.a);
            try{f.importOnce(db,true);fail("Revoked sender accepted");}
            catch(SecurityException expected){}
            assertEquals(0,f.network.opens);
            assertEquals(0,rows(db));
        }
    }
}
