package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
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
    private static String imageQrRoundtrip(String value)throws Exception {
        // Same PNG producer as pilot, same pixel/Zxing reader as the new
        // ACTION_OPEN_DOCUMENT path. No camera, files, server or image logger.
        Bitmap image=NexoConnectOwnQrView.encodeQrBitmap(value,1024);
        byte[] png;
        try{
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));
            png=output.toByteArray();
        }finally{image.recycle();}
        String result=NexoConnectQrImageDecoder.decodeImageBytes(png);
        java.util.Arrays.fill(png,(byte)0);
        assertEquals(value,result);
        return result;
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
        Fixture()throws Exception{this(false);}
        Fixture(boolean useRealQrPng)throws Exception{
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
            if(useRealQrPng){
                byte[] secret=new byte[32];
                new SecureRandom().nextBytes(secret);
                // A's private INVITATION image is decoded and parsed, its
                // public identity must be independently verified before pin.
                final String invitation;
                try{
                    invitation=NexoConnectInvitationQr.create(publicA,secret).encode();
                }finally{java.util.Arrays.fill(secret,(byte)0);}
                String fromImage=imageQrRoundtrip(invitation);
                NexoConnectInvitationQr decoded=NexoConnectInvitationQr.parse(fromImage);
                assertEquals(a,decoded.creatorAccount());
                assertEquals(1,decoded.creatorGeneration());
                aQr=decoded.publicPeerQr();
                bQr=imageQrRoundtrip(bQr);
                AndroidPeerTrustStore.PeerPin parsedA=
                    NexoConnectQrScannerActivity.requireScannedPeerQr(aQr,b);
                AndroidPeerTrustStore.PeerPin parsedB=
                    NexoConnectQrScannerActivity.requireScannedPeerQr(bQr,a);
                assertNull(receivePins.findActive(a));
                assertNull(sendPins.findActive(b));
                try{
                    QrTrustConfirmationView.requireHumanVerification(parsedA,
                        publicA.signingFingerprint(),publicA.encryptionFingerprint(),
                        false,true);
                    fail("QR image alone must not establish identity");
                }catch(GeneralSecurityException expected){}
                QrTrustConfirmationView.requireHumanVerification(parsedA,
                    publicA.signingFingerprint(),publicA.encryptionFingerprint(),
                    true,true);
                QrTrustConfirmationView.requireHumanVerification(parsedB,
                    publicB.signingFingerprint(),publicB.encryptionFingerprint(),
                    true,true);
            }
            receivePins.saveQrConfirmed(aQr,publicA.signingFingerprint(),
                    publicA.encryptionFingerprint(),true);
            sendPins.saveQrConfirmed(bQr,publicB.signingFingerprint(),
                    publicB.encryptionFingerprint(),true);
            receiverPublic=NexoConnectPinnedKeysets.recipientEncryption(sendPins.findActive(b));
            senderPublic=NexoConnectPinnedKeysets.senderVerification(receivePins.findActive(a));
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


    /** Mock ONLY remote challenge and delivery persistence, not signatures. */
    private static final class FakeSignedServer implements
            NexoConnectSignedGatewayClient.Transport {
        final Fixture fixture;
        final PublicKeyVerify verify;
        final UUID challenge=UUID.randomUUID();
        boolean issued,spent;
        int challengeCalls,sendCalls;
        FakeSignedServer(Fixture fixture)throws Exception{
            this.fixture=fixture;
            this.verify=fixture.sender.signingPrivate().getPublicKeysetHandle()
                    .getPrimitive(RegistryConfiguration.get(),PublicKeyVerify.class);
        }
        @Override public NexoConnectSignedGatewayClient.TransportResponse post(
                String path,byte[] content,Map<String,String> headers)throws Exception{
            String action=path.replace("/functions/v1/nexo-connect-gateway/","");
            assertEquals("Bearer test.jwt",headers.get("Authorization"));
            assertEquals("publishable",headers.get("apikey"));
            if(action.equals("challenge")){
                challengeCalls++;
                JSONObject request=new JSONObject(new String(content,StandardCharsets.UTF_8));
                assertEquals("delivery/send",request.getString("action"));
                assertEquals(fixture.sender.deviceId().toString(),
                        request.getString("deviceId"));
                assertEquals(1,request.getInt("generation"));
                issued=true;spent=false;
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("challengeId",challenge.toString())
                                .put("expiresInSeconds",120));
            }
            assertEquals("delivery/send",action);
            assertTrue("Must first request one-use challenge",issued);
            assertFalse("Challenge replay",spent);
            spent=true;sendCalls++;
            assertEquals(challenge.toString(),headers.get("x-nexo-challenge"));
            assertEquals(fixture.sender.deviceId().toString(),
                    headers.get("x-nexo-device"));
            assertEquals("1",headers.get("x-nexo-generation"));
            String issuedTime=headers.get("x-nexo-issued-at");
            assertEquals(Long.toString(NOW),issuedTime);
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(content);
            String canonical="NEXO-CONNECT-PROOF-V1\nPOST\n"+
                    "/nexo-connect/delivery/send\n"+fixture.a+"\n"+
                    fixture.sender.deviceId()+"\n1\n"+challenge+"\n"+issuedTime+
                    "\n"+hex(digest)+"\n";
            byte[] signature=decode(headers.get("x-nexo-signature"));
            verify.verify(signature,canonical.getBytes(StandardCharsets.UTF_8));
            fixture.network.sealed=new JSONObject(
                    new String(content,StandardCharsets.UTF_8));
            return NexoConnectSignedGatewayClient.TransportResponse.json(201,
                    new JSONObject().put("deliveryId",
                            fixture.network.sealed.getString("deliveryId"))
                            .put("status","pending"));
        }
    }

    /**
     * One continuous Android-side flow: invitation PNG -> ZXing decode ->
     * strict QR parser -> explicit independent fingerprint approval ->
     * Keystore trust -> signed fake HTTP challenge/send -> genuine Tink
     * HPKE NXC1 payload -> pinned-key verification -> local SQLite COMMIT ->
     * acknowledged receipt. Server transport is the ONLY mock; no network,
     * camera, OTP or real user accounts are involved. This test must not be
     * mistaken for a human two-phone integration test.
     */
    @Test public void qrImageThroughE2eeReceiptRequiresNoCamera()
            throws Exception {
        try(Fixture f=new Fixture(true);SQLiteDatabase db=db()){
            assertNotNull(f.receivePins.findActive(f.a));
            assertNotNull(f.sendPins.findActive(f.b));
            FakeSignedServer transport=new FakeSignedServer(f);
            NexoConnectSignedGatewayClient signed=
                new NexoConnectSignedGatewayClient(f.a,f.sender.deviceId(),1,
                    "test.jwt","publishable",f.sender.signingPrivate(),
                    transport,()->NOW);
            UUID receipt=signed.sendApproved(f.sender,f.sendPins,f.b,
                    f.receiverPublic,f.id,UUID.randomUUID(),"note",
                    new JSONObject().put("title","Desde imagen QR")
                        .put("body","Cifrado real sin cámara ni navegador"),
                    null,NOW+60000,true);
            assertEquals(f.id,receipt);
            assertEquals(1,transport.challengeCalls);
            assertEquals(1,transport.sendCalls);
            assertFalse(f.network.sealed.toString().contains(
                "Cifrado real sin cámara ni navegador"));
            NexoConnectInboxCoordinator.ImportOutcome copied=f.importOnce(db,true);
            assertTrue(copied.acknowledged);
            assertEquals("Desde imagen QR",receivedTitle(db));
            assertEquals(1,rows(db));
            assertEquals(1,f.network.acks);
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
        }
    }

    @Test public void signedHttpSendFromAReachesBAndImportsRealEncryptedNote()
            throws Exception {
        try(Fixture f=new Fixture();SQLiteDatabase db=db()){
            FakeSignedServer transport=new FakeSignedServer(f);
            NexoConnectSignedGatewayClient client=
                    new NexoConnectSignedGatewayClient(f.a,f.sender.deviceId(),1,
                            "test.jwt","publishable",f.sender.signingPrivate(),
                            transport,()->NOW);
            assertEquals(f.id,client.sendApproved(f.sender,f.sendPins,f.b,
                    f.receiverPublic,f.id,UUID.randomUUID(),"note",
                    new JSONObject().put("title","Nota entregada A a B")
                            .put("body","Texto que no debe viajar en claro"),
                    null,NOW+60000,true));
            assertEquals(1,transport.challengeCalls);
            assertEquals(1,transport.sendCalls);
            assertFalse(f.network.sealed.toString().contains(
                    "Texto que no debe viajar en claro"));
            assertEquals(1,rowsAfterImport(f,db));
            assertEquals("Nota entregada A a B",receivedTitle(db));
            assertEquals(1,f.network.acks);
        }
    }

    private static int rowsAfterImport(Fixture f,SQLiteDatabase db)throws Exception {
        NexoConnectInboxCoordinator.ImportOutcome received=f.importOnce(db,true);
        assertTrue(received.acknowledged);
        assertFalse(received.alreadyImported);
        return rows(db);
    }

    @Test public void revokedReceiverBlocksSignedRequestBeforeChallenge()throws Exception{
        try(Fixture f=new Fixture()){
            FakeSignedServer transport=new FakeSignedServer(f);
            NexoConnectSignedGatewayClient client=
                    new NexoConnectSignedGatewayClient(f.a,f.sender.deviceId(),1,
                            "test.jwt","publishable",f.sender.signingPrivate(),
                            transport,()->NOW);
            assertTrue(f.sendPins.revoke(f.b));
            try{
                client.sendApproved(f.sender,f.sendPins,f.b,f.receiverPublic,
                        f.id,UUID.randomUUID(),"note",
                        new JSONObject().put("title","No enviar"),null,
                        NOW+60000,true);
                fail("Revoked peer reached gateway");
            }catch(SecurityException expected){}
            assertEquals(0,transport.challengeCalls);
            assertEquals(0,transport.sendCalls);
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
            UUID impostorId=UUID.randomUUID();
            KeysetHandle impostor=NexoConnectLocalPhoneIdentity.openOrCreate(
                    APP,impostorId).hpkePrivate().getPublicKeysetHandle();
            new AndroidIdentityVault(APP,impostorId).destroy();
            try{NexoConnectSealedDelivery.sealApproved(f.sender,1,f.sendPins,
                    f.b,impostor,f.id,UUID.randomUUID(),"note",
                    new JSONObject().put("title","No"),null,
                    NOW,NOW+60000,true);
                fail("Unverified receiver encryption key");}
            catch(GeneralSecurityException expected){}
            f.seal();
            String old=f.network.sealed.getString("signature");
            f.network.sealed.put("signature",
                    (old.startsWith("A")?"B":"A")+old.substring(1));
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
