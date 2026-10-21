package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Camera-free, OFFLINE Android instrumentation for private invitation QRs.
 * Uses real Tink public identities, ZXing's actual QR decoder and the Android
 * Keystore-backed peer trust store. No Supabase, email or camera is contacted.
 *
 * It does NOT pretend to test a real second account/server connection:
 * anti-replay and server-side invitation consumption have independent
 * gateway/SQL tests under supabase/nexo-connect.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectCameraFreePairingTest {
    private static final Context APP=
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static final class Identity {
        final AndroidPeerTrustStore.PeerPin publicPin;
        Identity(UUID account,int generation)throws Exception {
            SignatureConfig.register();
            HybridConfig.register();
            KeysetHandle signing=KeysetHandle.generateNew(
                    SignatureKeyTemplates.ED25519WithRawOutput);
            KeysetHandle hpke=KeysetHandle.generateNew(HpkeParameters.builder()
                    .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                    .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                    .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                    .setVariant(HpkeParameters.Variant.TINK).build());
            publicPin=NexoConnectOwnQrView.publicIdentity(
                    account,generation,signing,hpke);
        }

        NexoConnectInvitationQr invitation()throws Exception{
            byte[] secret=new byte[32];
            new SecureRandom().nextBytes(secret);
            try{
                return NexoConnectInvitationQr.create(publicPin,secret);
            }finally{
                java.util.Arrays.fill(secret,(byte)0);
            }
        }
    }

    private static String decodeWithZxing(Bitmap bitmap)throws Exception{
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        int[] pixels=new int[width*height];
        bitmap.getPixels(pixels,0,width,0,0,width,height);
        BinaryBitmap binary=new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(width,height,pixels)));
        return new MultiFormatReader().decode(binary).getText();
    }

    @Test public void invitationQrRoundTripsUsingZxingWithoutOpeningAnyCamera()
            throws Exception {
        UUID creator=UUID.randomUUID(),recipient=UUID.randomUUID();
        Identity a=new Identity(creator,1);
        NexoConnectInvitationQr invitation=a.invitation();
        String qr=invitation.encode();

        // Decode the exact image produced by the pilot, without a virtual room.
        String scanned=decodeWithZxing(
                NexoConnectOwnQrView.encodeQrBitmap(qr,1024));
        assertEquals(qr,scanned);
        JSONObject fields=new JSONObject(scanned);
        assertEquals(7,fields.length());
        assertFalse(fields.has("privateKey"));
        assertFalse(fields.has("refresh_token"));

        NexoConnectInvitationQr decoded=NexoConnectInvitationQr.parse(scanned);
        assertEquals(creator,decoded.creatorAccount());
        assertEquals(1,decoded.creatorGeneration());
        assertTrue(MessageDigest.isEqual(invitation.tokenSha256(),
                decoded.tokenSha256()));
        AndroidPeerTrustStore.PeerPin fromScanner=
                NexoConnectQrScannerActivity.requireScannedPeerQr(
                        decoded.publicPeerQr(),recipient);
        assertArrayEquals(a.publicPin.signingPublicRaw(),
                fromScanner.signingPublicRaw());
        assertArrayEquals(a.publicPin.encryptionPublicRaw(),
                fromScanner.encryptionPublicRaw());
    }

    @Test public void invitationCannotPinPeerWithoutIndependentFingerprintAndConsent()
            throws Exception {
        UUID creator=UUID.randomUUID(),recipient=UUID.randomUUID();
        Identity a=new Identity(creator,1);
        NexoConnectInvitationQr invite=a.invitation();
        AndroidPeerTrustStore store=new AndroidPeerTrustStore(APP,recipient);
        String publicQr=invite.publicPeerQr();

        try {
            store.saveQrConfirmed(publicQr,"00".repeat(32),
                    invite.encryptionFingerprint(),true);
            fail("Wrong independent Ed25519 fingerprint accepted");
        }catch(GeneralSecurityException expected){}
        try {
            store.saveQrConfirmed(publicQr,invite.signingFingerprint(),
                    "00".repeat(32),true);
            fail("Wrong independent HPKE fingerprint accepted");
        }catch(GeneralSecurityException expected){}
        try {
            store.saveQrConfirmed(publicQr,invite.signingFingerprint(),
                    invite.encryptionFingerprint(),false);
            fail("Missing explicit consent accepted");
        }catch(SecurityException expected){}
        assertNull(store.findActive(creator));
        assertFalse(invite.matchesPinnedPeer(store.findActive(creator)));

        // A human-approved pin is the only transition to local trust.
        store.saveQrConfirmed(publicQr,invite.signingFingerprint(),
                invite.encryptionFingerprint(),true);
        assertTrue(invite.matchesPinnedPeer(store.findActive(creator)));
        assertTrue(store.revoke(creator));
    }

    @Test public void oldInvitationCannotSilentlyChangeOrRestoreRevokedKeys()
            throws Exception {
        UUID creator=UUID.randomUUID(),recipient=UUID.randomUUID();
        Identity initial=new Identity(creator,1);
        Identity replacement=new Identity(creator,2);
        NexoConnectInvitationQr oldInvite=initial.invitation();
        NexoConnectInvitationQr newInvite=replacement.invitation();
        AndroidPeerTrustStore store=new AndroidPeerTrustStore(APP,recipient);
        store.saveQrConfirmed(oldInvite.publicPeerQr(),
                oldInvite.signingFingerprint(),oldInvite.encryptionFingerprint(),true);

        assertTrue(oldInvite.matchesPinnedPeer(store.findActive(creator)));
        assertFalse(newInvite.matchesPinnedPeer(store.findActive(creator)));
        try {
            store.saveQrConfirmed(newInvite.publicPeerQr(),
                    newInvite.signingFingerprint(),
                    newInvite.encryptionFingerprint(),true);
            fail("Silent generation/key replacement accepted");
        }catch(GeneralSecurityException expected){}
        assertTrue(oldInvite.matchesPinnedPeer(store.findActive(creator)));

        assertTrue(store.revoke(creator));
        assertNull(store.findActive(creator));
        assertFalse(oldInvite.matchesPinnedPeer(store.findActive(creator)));
        assertFalse(newInvite.matchesPinnedPeer(store.findActive(creator)));
        // A replaced phone would require a fresh, independently confirmed pin;
        // the old invitation cannot restore trust on its own.
    }
}
