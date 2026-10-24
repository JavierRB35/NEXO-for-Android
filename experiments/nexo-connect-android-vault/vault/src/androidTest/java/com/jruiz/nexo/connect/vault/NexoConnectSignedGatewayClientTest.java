package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class NexoConnectSignedGatewayClientTest {
    private static final UUID ACCOUNT=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PHONE=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DELIVERY=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID NONCE=UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final long NOW=1780000000000L;

    private static final class FakeTransport implements NexoConnectSignedGatewayClient.Transport {
        final PublicKeyVerify verifier;
        final UUID account;
        final ArrayList<String> actions=new ArrayList<>();
        String grantedAction;
        boolean spent;
        boolean failSigned;
        int statusOnAction=200;
        FakeTransport(KeysetHandle handle) throws Exception {
            this(handle,ACCOUNT);
        }
        FakeTransport(KeysetHandle handle,UUID account) throws Exception {
            this.account=account;
            SignatureConfig.register();
            verifier=handle.getPublicKeysetHandle().getPrimitive(
                    RegistryConfiguration.get(),PublicKeyVerify.class);
        }
        @Override public NexoConnectSignedGatewayClient.TransportResponse post(
                String path,byte[] bytes,Map<String,String> headers) throws Exception {
            assertEquals("Bearer secret.jwt",headers.get("Authorization"));
            assertEquals("publishable",headers.get("apikey"));
            assertTrue(path.startsWith("/functions/v1/nexo-connect-gateway/"));
            String action=path.substring("/functions/v1/nexo-connect-gateway/".length());
            actions.add(action);
            if(action.equals("challenge")){
                JSONObject requested=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                assertEquals(PHONE.toString(),requested.getString("deviceId"));
                assertEquals(1,requested.getInt("generation"));
                grantedAction=requested.getString("action");
                spent=false;
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("challengeId",NONCE.toString())
                                .put("expiresInSeconds",120));
            }
            assertEquals(grantedAction,action);
            assertFalse("Challenge was replayed",spent);
            spent=true;
            assertEquals(PHONE.toString(),headers.get("x-nexo-device"));
            assertEquals("1",headers.get("x-nexo-generation"));
            assertEquals(NONCE.toString(),headers.get("x-nexo-challenge"));
            String time=headers.get("x-nexo-issued-at");
            assertEquals(Long.toString(NOW),time);
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex=new StringBuilder();
            for(byte b:hash)hex.append(String.format("%02x",b&255));
            String canonical="NEXO-CONNECT-PROOF-V1\nPOST\n/nexo-connect/"+action+"\n"+
                    account+"\n"+PHONE+"\n1\n"+NONCE+"\n"+time+"\n"+hex+"\n";
            byte[] signature=android.util.Base64.decode(headers.get("x-nexo-signature"),
                    android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);
            verifier.verify(signature,canonical.getBytes(StandardCharsets.UTF_8));
            if(failSigned)return new NexoConnectSignedGatewayClient.TransportResponse(503,null);
            if(statusOnAction==403)return new NexoConnectSignedGatewayClient.TransportResponse(403,null);
            JSONObject request=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(action.equals("contact/invite"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(201,
                        new JSONObject().put("inviteId",
                                "77777777-7777-4777-8777-777777777777")
                            .put("expiresInSeconds",request.getInt("expiresInSeconds")));
            if(action.equals("contact/accept"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("linked",true)
                                .put("requestId","88888888-8888-4888-8888-888888888888")
                                .put("creatorId",request.getString("creatorId"))
                                .put("reciprocalKeyVerificationRequired",true));
            if(action.equals("key/verify"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("verified",true)
                                .put("peerId",request.getString("peerId"))
                                .put("generation",request.getInt("peerGeneration")));
            if(action.equals("contact/permissions"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("peerId",request.getString("peerId"))
                                .put("receivingAllowed",request.getBoolean("allowReceive")));
            if(action.equals("contact/unlink"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("unlinked",true)
                                .put("peerId",request.getString("peerId")));
            if(action.equals("delivery/inbox"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("items",new JSONArray()
                                .put(new JSONObject().put("deliveryId",DELIVERY))));
            if(action.equals("delivery/open"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("deliveryId",request.getString("deliveryId"))
                                .put("contextHex","aa"));
            if(action.equals("delivery/resolve"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(200,
                        new JSONObject().put("deliveryId",request.getString("deliveryId"))
                                .put("status",request.getString("target")));
            if(action.equals("delivery/send"))
                return NexoConnectSignedGatewayClient.TransportResponse.json(201,
                        new JSONObject().put("deliveryId",request.getString("deliveryId"))
                                .put("status","pending"));
            throw new AssertionError("Unknown action");
        }
    }

    private static final class Fixture {
        final KeysetHandle identity;
        final FakeTransport fake;
        final NexoConnectSignedGatewayClient client;
        Fixture()throws Exception{
            SignatureConfig.register();
            identity=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            fake=new FakeTransport(identity);
            client=new NexoConnectSignedGatewayClient(ACCOUNT,PHONE,1,"secret.jwt",
                    "publishable",identity,fake,()->NOW);
        }
    }

    @Test public void inboxPageAndOpenAreSignedUsingActualBytes() throws Exception {
        Fixture f=new Fixture();
        JSONArray items=f.client.inbox(10,null);
        assertEquals(1,items.length());
        JSONObject message=f.client.open(DELIVERY);
        assertEquals(DELIVERY.toString(),message.getString("deliveryId"));
        assertEquals(4,f.fake.actions.size());
        assertEquals("challenge",f.fake.actions.get(0));
        assertEquals("delivery/inbox",f.fake.actions.get(1));
        assertEquals("challenge",f.fake.actions.get(2));
        assertEquals("delivery/open",f.fake.actions.get(3));
    }
    @Test public void resolutionRequiresExplicitActionAndSignedReceipt()throws Exception {
        Fixture f=new Fixture();
        assertEquals("accepted",f.client.resolve(DELIVERY,"accepted"));
        try{f.client.resolve(DELIVERY,"invalid");fail("Invalid resolution");}
        catch(IllegalArgumentException expected){}
        assertEquals(2,f.fake.actions.size());
    }
    @Test public void noAutomaticRetryOfUncertainSignedPost()throws Exception {
        Fixture f=new Fixture();
        f.fake.failSigned=true;
        try{f.client.open(DELIVERY);fail("Network uncertainty should propagate");}
        catch(NexoConnectSignedGatewayClient.TransportException expected){
            assertEquals(503,expected.httpStatus);
        }
        assertEquals(2,f.fake.actions.size()); // challenge and ONE signed attempt
    }
    @Test public void rejectsOversizePageAndInvalidResponseWithoutImport()throws Exception {
        Fixture f=new Fixture();
        try{f.client.inbox(21,null);fail("Unlimited listing");}
        catch(IllegalArgumentException expected){}
        assertTrue(f.fake.actions.isEmpty());
    }
    private static JSONObject sealed(Fixture f)throws Exception {
        UUID receiver=UUID.fromString("55555555-5555-4555-8555-555555555555");
        byte[] senderRaw=((com.google.crypto.tink.signature.Ed25519PublicKey)
                f.identity.getPublicKeysetHandle().getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out=new java.io.DataOutputStream(buffer);
        out.writeInt(0x4e584331);out.writeByte(1);
        out.writeLong(DELIVERY.getMostSignificantBits());
        out.writeLong(DELIVERY.getLeastSignificantBits());
        out.writeLong(ACCOUNT.getMostSignificantBits());
        out.writeLong(ACCOUNT.getLeastSignificantBits());
        out.writeLong(receiver.getMostSignificantBits());
        out.writeLong(receiver.getLeastSignificantBits());
        out.writeByte(2);out.writeInt(1);out.writeInt(1);
        out.writeLong(NOW);out.writeLong(NOW+60000);
        out.write(MessageDigest.getInstance("SHA-256").digest(senderRaw));
        out.write(new byte[32]);
        out.flush();
        byte[] context=buffer.toByteArray();assertEquals(142,context.length);
        return new JSONObject()
                .put("deliveryId",DELIVERY.toString())
                .put("clientRequestId","66666666-6666-4666-8666-666666666666")
                .put("recipientId",receiver.toString())
                .put("recipientGeneration",1)
                .put("entityType","note")
                .put("context",android.util.Base64.encodeToString(context,
                        android.util.Base64.URL_SAFE|
                        android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING))
                .put("ciphertext",android.util.Base64.encodeToString(
                        new byte[]{4,5,6,7},android.util.Base64.URL_SAFE|
                        android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING))
                .put("signature",android.util.Base64.encodeToString(
                        new byte[64],android.util.Base64.URL_SAFE|
                        android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING));
    }

    @Test public void sendRejectsRawJsonBeforeAnyNetworkCall()throws Exception {
        Fixture f=new Fixture();
        try{f.client.send(new JSONObject().put("deliveryId",DELIVERY.toString()));
            fail("Incomplete payload was forwarded to Supabase");}
        catch(GeneralSecurityException expected){}
        assertTrue(f.fake.actions.isEmpty());
    }
    @Test public void sendRequiresSealedRoutingAndServerConfirmation()throws Exception {
        Fixture f=new Fixture();
        JSONObject sealed=sealed(f);
        assertEquals(DELIVERY,f.client.send(sealed));
        assertEquals(2,f.fake.actions.size());
    }
    @Test public void changedSenderOrContextDoesNotContactServer()throws Exception{
        Fixture f=new Fixture();
        JSONObject packet=sealed(f);
        packet.put("recipientId","77777777-7777-4777-8777-777777777777");
        try{f.client.send(packet);fail("Swapped recipient accepted");}
        catch(GeneralSecurityException expected){}
        assertTrue(f.fake.actions.isEmpty());
    }
    @Test public void rejectNonHttpsProjectOrigins()throws Exception {
        try{new NexoConnectSignedGatewayClient("http://example.org","public",
                "jwt",ACCOUNT,PHONE,1,new Fixture().identity);fail("HTTP allowed");}
        catch(IllegalArgumentException expected){}
        try{new NexoConnectSignedGatewayClient("https://example.org","public",
                "jwt",ACCOUNT,PHONE,1,new Fixture().identity);fail("Other host allowed");}
        catch(IllegalArgumentException expected){}
    }

    private static AndroidPeerTrustStore.PeerPin publicIdentity(
            Fixture fixture,UUID owner)throws Exception {
        byte[] signing=((com.google.crypto.tink.signature.Ed25519PublicKey)
                fixture.identity.getPublicKeysetHandle().getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        byte[] enc=new byte[32];
        new java.security.SecureRandom().nextBytes(enc);
        return new AndroidPeerTrustStore.PeerPin(owner,1,signing,enc,true);
    }
    @Test public void creatorSignsOnlyHashOfRandomInvitationSecret()throws Exception{
        Fixture creator=new Fixture();
        AndroidPeerTrustStore.PeerPin self=publicIdentity(creator,ACCOUNT);
        NexoConnectSignedGatewayClient.InvitationCreated voucher=
                creator.client.createInvitation(self,3600);
        assertEquals(3600,voucher.expiresInSeconds);
        assertEquals("77777777-7777-4777-8777-777777777777",
                voucher.inviteId.toString());
        NexoConnectInvitationQr decoded=NexoConnectInvitationQr.parse(voucher.qrPayload);
        assertEquals(ACCOUNT,decoded.creatorAccount());
        assertEquals(1,decoded.creatorGeneration());
        assertEquals(self.signingFingerprint(),decoded.signingFingerprint());
        assertEquals(2,creator.fake.actions.size());
        assertEquals("contact/invite",creator.fake.actions.get(1));
        assertNotEquals(voucher.qrPayload,creator.fake.actions.get(1));
        // A second issue is independent, and cannot reuse the first QR token.
        NexoConnectSignedGatewayClient.InvitationCreated second=
                creator.client.createInvitation(self,3600);
        assertNotEquals(voucher.qrPayload,second.qrPayload);
        assertFalse(second.qrPayload.contains("privateKey"));
    }
    @Test public void acceptRequiresPinnedPeerBeforeAnySignedNetworkCall()
            throws Exception{
        Fixture creator=new Fixture();
        AndroidPeerTrustStore.PeerPin identity=publicIdentity(creator,ACCOUNT);
        byte[] token=new byte[32];
        new java.security.SecureRandom().nextBytes(token);
        NexoConnectInvitationQr qr=NexoConnectInvitationQr.create(identity,token);
        // Simulate B's independent QR verification with a separate owner UUID.
        UUID b=UUID.fromString("99999999-9999-4999-8999-999999999999");
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AndroidPeerTrustStore bStore=new AndroidPeerTrustStore(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext(),b);
        KeysetHandle bKey=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
        FakeTransport bNet=new FakeTransport(bKey,b);
        NexoConnectSignedGatewayClient bClient=new NexoConnectSignedGatewayClient(
                b,PHONE,1,"secret.jwt","publishable",bKey,bNet,()->NOW);
        try{
            bClient.acceptVerifiedInvitation(qr.encode(),bStore,true);
            fail("Accepted without independent QR pin");
        }catch(GeneralSecurityException expected){}
        assertTrue(bNet.actions.isEmpty());
        bStore.saveQrConfirmed(qr.publicPeerQr(),identity.signingFingerprint(),
                identity.encryptionFingerprint(),true);
        assertEquals("88888888-8888-4888-8888-888888888888",
                bClient.acceptVerifiedInvitation(qr.encode(),bStore,true).toString());
        assertEquals("contact/accept",bNet.actions.get(1));
        try{
            bClient.acceptVerifiedInvitation(qr.encode(),bStore,false);
            fail("Accepted without permission");
        }catch(SecurityException expected){}
        assertEquals(2,bNet.actions.size());
    }
    @Test public void reciprocalQrVerificationAndRemoteUnlinkUseSignedProof()
            throws Exception{
        Fixture f=new Fixture();
        AndroidPeerTrustStore store=new AndroidPeerTrustStore(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getTargetContext(),ACCOUNT);
        UUID peer=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        byte[] sign=new byte[32],enc=new byte[32];
        new java.security.SecureRandom().nextBytes(sign);
        new java.security.SecureRandom().nextBytes(enc);
        AndroidPeerTrustStore.PeerPin peerPin=new AndroidPeerTrustStore.PeerPin(
                peer,1,sign,enc,true);
        store.saveQrConfirmed(AndroidPeerTrustStore.encodeQr(peerPin),
                peerPin.signingFingerprint(),peerPin.encryptionFingerprint(),true);
        f.client.verifyStoredPeer(store,peer);
        assertEquals("key/verify",f.fake.actions.get(1));
        f.client.setReceiving(store,peer,false);
        f.client.setReceiving(store,peer,true);
        f.client.unlinkRemotelyAndLocally(store,peer,true);
        assertNull(store.findActive(peer));
        assertEquals("contact/unlink",f.fake.actions.get(7));
        try{
            f.client.setReceiving(store,peer,true);
            fail("Enabled an unverified revoked contact");
        }catch(SecurityException expected){}
    }

    @Test public void unlinkRequiresConsentAndFailedServerCallKeepsPin() throws Exception {
        Fixture f=new Fixture();
        AndroidPeerTrustStore store=new AndroidPeerTrustStore(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getTargetContext(),ACCOUNT);
        UUID peer=UUID.randomUUID();
        byte[] signing=new byte[32],encryption=new byte[32];
        new java.security.SecureRandom().nextBytes(signing);
        new java.security.SecureRandom().nextBytes(encryption);
        AndroidPeerTrustStore.PeerPin pin=new AndroidPeerTrustStore.PeerPin(
                peer,1,signing,encryption,true);
        store.saveQrConfirmed(AndroidPeerTrustStore.encodeQr(pin),
                pin.signingFingerprint(),pin.encryptionFingerprint(),true);

        try{
            f.client.unlinkRemotelyAndLocally(store,peer,false);
            fail("Unlink without confirmation");
        }catch(SecurityException expected){}
        assertTrue(f.fake.actions.isEmpty());
        assertNotNull(store.findActive(peer));

        f.fake.failSigned=true;
        try{
            f.client.unlinkRemotelyAndLocally(store,peer,true);
            fail("Remote unlink was not confirmed");
        }catch(NexoConnectSignedGatewayClient.TransportException expected){
            assertEquals(503,expected.httpStatus);
        }
        assertEquals(2,f.fake.actions.size());
        assertEquals("challenge",f.fake.actions.get(0));
        assertEquals("contact/unlink",f.fake.actions.get(1));
        assertNotNull("Local trust cannot be dropped on uncertain server failure",
                store.findActive(peer));
    }

    @Test public void rejectsTamperedResponseId() throws Exception {
        Fixture f=new Fixture();
        f.fake.statusOnAction=403;
        try{f.client.open(DELIVERY);fail("Unauthorized envelope returned");}
        catch(NexoConnectSignedGatewayClient.TransportException expected){
            assertEquals(403,expected.httpStatus);
        }
    }
}
