package com.jruiz.nexo.connect.vault;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.crypto.tink.signature.Ed25519PublicKey;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AndroidPeerTrustStoreTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final UUID PEER=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER=UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final class PinFixture {
        final KeysetHandle signing;
        final AndroidPeerTrustStore.PeerPin pin;
        final String qr;
        PinFixture(UUID peer,int generation)throws Exception {
            SignatureConfig.register();
            signing=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            byte[] raw=((Ed25519PublicKey)signing.getPublicKeysetHandle()
                    .getAt(0).getKey()).getPublicKeyBytes().toByteArray();
            byte[] encryption=new byte[32];
            new java.security.SecureRandom().nextBytes(encryption);
            pin=new AndroidPeerTrustStore.PeerPin(peer,generation,raw,encryption,true);
            qr=AndroidPeerTrustStore.encodeQr(pin);
        }
    }
    private AndroidPeerTrustStore fresh(){
        try{return new AndroidPeerTrustStore(app,UUID.randomUUID());}
        catch(Exception ex){throw new RuntimeException(ex);}
    }
    @Test public void qrPinSurvivesRecreationAndPublicKeysStaySealed()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture f=new PinFixture(PEER,1);
        assertNull(store.findActive(PEER));
        store.saveQrConfirmed(f.qr,f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true);
        AndroidPeerTrustStore restored=new AndroidPeerTrustStore(app,store.ownerAccountId());
        AndroidPeerTrustStore.PeerPin saved=restored.findActive(PEER);
        assertNotNull(saved);
        assertEquals(1,saved.generation);
        assertArrayEquals(f.pin.signingPublicRaw(),saved.signingPublicRaw());
        assertArrayEquals(f.pin.encryptionPublicRaw(),saved.encryptionPublicRaw());
        saved.verifyTinkPublicKey(f.signing.getPublicKeysetHandle());
        byte[] onDisk=Files.readAllBytes(store.localFileForTest().toPath());
        assertFalse(contains(onDisk,f.pin.signingPublicRaw()));
        assertFalse(contains(onDisk,f.pin.encryptionPublicRaw()));
        assertFalse(new String(onDisk,java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains(PEER.toString()));
    }
    @Test public void requiresConsentAndIndependentFingerprints()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture f=new PinFixture(PEER,1);
        try{store.saveQrConfirmed(f.qr,f.pin.signingFingerprint(),
                    f.pin.encryptionFingerprint(),false);fail("No consent");}
        catch(SecurityException expected){}
        try{store.saveQrConfirmed(f.qr,"00".repeat(32),
                    f.pin.encryptionFingerprint(),true);fail("No independent check");}
        catch(GeneralSecurityException expected){}
        assertNull(store.findActive(PEER));
    }
    @Test public void changedGenerationOrKeysCannotReplaceExistingPin()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture before=new PinFixture(PEER,1);
        PinFixture rotated=new PinFixture(PEER,2);
        PinFixture substituted=new PinFixture(PEER,1);
        store.saveQrConfirmed(before.qr,before.pin.signingFingerprint(),
                before.pin.encryptionFingerprint(),true);
        for(PinFixture candidate:new PinFixture[]{rotated,substituted}){
            try{store.saveQrConfirmed(candidate.qr,
                candidate.pin.signingFingerprint(),candidate.pin.encryptionFingerprint(),true);
                fail("Silent peer key replacement");}
            catch(GeneralSecurityException expected){}
        }
        assertEquals(1,store.findActive(PEER).generation);
        assertArrayEquals(before.pin.signingPublicRaw(),store.findActive(PEER).signingPublicRaw());
    }
    @Test public void explicitRevokeThenNewQrCanReauthorize()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture before=new PinFixture(PEER,1);
        PinFixture renewed=new PinFixture(PEER,2);
        store.saveQrConfirmed(before.qr,before.pin.signingFingerprint(),
                before.pin.encryptionFingerprint(),true);
        assertTrue(store.revoke(PEER));
        assertNull(store.findActive(PEER));
        assertFalse(store.revoke(PEER));
        store.saveQrConfirmed(renewed.qr,renewed.pin.signingFingerprint(),
                renewed.pin.encryptionFingerprint(),true);
        assertEquals(2,store.findActive(PEER).generation);
    }
    @Test public void activeListingExcludesRevokedAndHasStableSnapshot() throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture a=new PinFixture(PEER,1);
        PinFixture b=new PinFixture(OTHER,2);
        store.saveQrConfirmed(a.qr,a.pin.signingFingerprint(),
                a.pin.encryptionFingerprint(),true);
        store.saveQrConfirmed(b.qr,b.pin.signingFingerprint(),
                b.pin.encryptionFingerprint(),true);
        java.util.List<AndroidPeerTrustStore.PeerPin> snapshot=store.listActive();
        assertEquals(2,snapshot.size());
        assertTrue(store.revoke(PEER));
        java.util.List<AndroidPeerTrustStore.PeerPin> after=store.listActive();
        assertEquals(1,after.size());
        assertEquals(OTHER,after.get(0).peerId);
        assertEquals(2,snapshot.size());
        try{after.clear();fail("Mutable trust snapshot");}
        catch(UnsupportedOperationException expected){}
    }
    @Test public void tamperedStorageFailsClosed()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture f=new PinFixture(PEER,1);
        store.saveQrConfirmed(f.qr,f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true);
        byte[] onDisk=Files.readAllBytes(store.localFileForTest().toPath());
        onDisk[onDisk.length-1]^=1;
        Files.write(store.localFileForTest().toPath(),onDisk);
        try{store.findActive(PEER);fail("Tampered trust decrypt");}
        catch(GeneralSecurityException expected){}
    }
    @Test public void differentAccountCannotReadPeerPin()throws Exception {
        AndroidPeerTrustStore original=fresh(), other=fresh();
        PinFixture f=new PinFixture(PEER,1);
        original.saveQrConfirmed(f.qr,f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true);
        assertNull(other.findActive(PEER));
        assertFalse(other.localFileForTest().equals(original.localFileForTest()));
    }
    @Test public void exportedQrRoundTripsExactlySixPublicFields() throws Exception {
        PinFixture f=new PinFixture(PEER,7);
        JSONObject json=new JSONObject(f.qr);
        assertEquals(6,json.length());
        assertFalse(json.has("privateKey"));
        AndroidPeerTrustStore.PeerPin decoded=AndroidPeerTrustStore.parseQr(f.qr);
        assertEquals(PEER,decoded.peerId);
        assertEquals(7,decoded.generation);
        assertArrayEquals(f.pin.signingPublicRaw(),decoded.signingPublicRaw());
        assertArrayEquals(f.pin.encryptionPublicRaw(),decoded.encryptionPublicRaw());
        assertEquals(f.pin.signingFingerprint(),decoded.signingFingerprint());
        assertEquals(f.pin.encryptionFingerprint(),decoded.encryptionFingerprint());
    }
    @Test public void malformedOrInjectedQrNeverStored()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture f=new PinFixture(PEER,1);
        JSONObject extra=new JSONObject(f.qr).put("privKey","secret");
        try{store.saveQrConfirmed(extra.toString(),f.pin.signingFingerprint(),
            f.pin.encryptionFingerprint(),true);fail("Injected QR");}
        catch(GeneralSecurityException expected){}
        String bad=new JSONObject(f.qr).put("signingKey","not-base64").toString();
        try{store.saveQrConfirmed(bad,f.pin.signingFingerprint(),
            f.pin.encryptionFingerprint(),true);fail("Invalid key");}
        catch(GeneralSecurityException expected){}
        assertNull(store.findActive(PEER));
    }
    @Test public void outdatedOrForgedEd25519KeysetFailsMatching()throws Exception {
        AndroidPeerTrustStore store=fresh();
        PinFixture f=new PinFixture(PEER,1);
        store.saveQrConfirmed(f.qr,f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true);
        PinFixture other=new PinFixture(OTHER,1);
        try{store.findActive(PEER).verifyTinkPublicKey(other.signing.getPublicKeysetHandle());
            fail("Untrusted keyset accepted");}
        catch(GeneralSecurityException expected){}
    }
    private static boolean contains(byte[] data,byte[] needle){
        outer:for(int i=0;i<=data.length-needle.length;i++){
            for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;
            return true;
        }
        return false;
    }
}
