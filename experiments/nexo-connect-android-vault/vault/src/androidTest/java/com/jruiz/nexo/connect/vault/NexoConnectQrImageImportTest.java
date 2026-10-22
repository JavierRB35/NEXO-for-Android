package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Android 34 instrumented QR image tests WITHOUT camera, picker, Supabase,
 * OTP, microphone, browser, or any emulator 3D room interaction.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectQrImageImportTest {
    private static final Context APP=
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static byte[] png(Bitmap bitmap) {
        try {
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,b));
            return b.toByteArray();
        }finally{bitmap.recycle();}
    }

    private static AndroidPeerTrustStore.PeerPin freshPublic(UUID id)throws Exception {
        SignatureConfig.register();
        HybridConfig.register();
        KeysetHandle sign=KeysetHandle.generateNew(
                SignatureKeyTemplates.ED25519WithRawOutput);
        KeysetHandle hpke=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK)
                .build());
        return NexoConnectOwnQrView.publicIdentity(id,1,sign,hpke);
    }

    @Test public void importedPublicQrImageMatchesZxingAndDoesNotAutoTrust()
            throws Exception {
        UUID owner=UUID.randomUUID(),peer=UUID.randomUUID();
        AndroidPeerTrustStore.PeerPin publicPeer=freshPublic(peer);
        String qr=AndroidPeerTrustStore.encodeQr(publicPeer);
        byte[] bytes=png(NexoConnectOwnQrView.encodeQrBitmap(qr,900));
        String fromImage=NexoConnectQrImageDecoder.decodeImageBytes(bytes);
        assertEquals(qr,fromImage);
        AndroidPeerTrustStore.PeerPin parsed=
                NexoConnectQrScannerActivity.requireScannedPeerQr(fromImage,owner);
        assertEquals(peer,parsed.peerId);

        AndroidPeerTrustStore store=new AndroidPeerTrustStore(APP,owner);
        assertNull("Reading an image must never establish trust",store.findActive(peer));
        try {
            QrTrustConfirmationView.requireHumanVerification(parsed,
                    parsed.signingFingerprint(),parsed.encryptionFingerprint(),
                    false,true);
            fail("Independent-channel verification was not enforced");
        }catch(GeneralSecurityException expected){}
        try {
            QrTrustConfirmationView.requireHumanVerification(parsed,
                    "0".repeat(64),parsed.encryptionFingerprint(),true,true);
            fail("Wrong signing key was accepted");
        }catch(GeneralSecurityException expected){}
        assertNull(store.findActive(peer));
    }

    @Test public void importedInvitationImageRetainsServerOneUseTokenLocally()
            throws Exception {
        UUID owner=UUID.randomUUID(),peer=UUID.randomUUID();
        AndroidPeerTrustStore.PeerPin publicPeer=freshPublic(peer);
        byte[] secret=new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        NexoConnectInvitationQr invite=NexoConnectInvitationQr.create(publicPeer,secret);
        String serialized=invite.encode();
        String decoded=NexoConnectQrImageDecoder.decodeImageBytes(png(
                NexoConnectOwnQrView.encodeQrBitmap(serialized,1024)));
        assertEquals(serialized,decoded);
        NexoConnectInvitationQr found=NexoConnectInvitationQr.parse(decoded);
        assertArrayEquals(invite.tokenSha256(),found.tokenSha256());
        assertEquals(peer,found.creatorAccount());
        NexoConnectQrScannerActivity.requireScannedPeerQr(found.publicPeerQr(),owner);
        assertNull(new AndroidPeerTrustStore(APP,owner).findActive(peer));
    }

    @Test public void malformedOversizedOrNonQrImageIsRejected()throws Exception {
        for(byte[] invalid:new byte[][]{new byte[0],new byte[]{1,2,3},
                new byte[4*1024*1024+1]}) {
            try{
                NexoConnectQrImageDecoder.decodeImageBytes(invalid);
                fail("Invalid image accepted");
            }catch(Exception expected){}
        }
        Bitmap blank=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        blank.eraseColor(Color.WHITE);
        try{
            NexoConnectQrImageDecoder.decodeImageBytes(png(blank));
            fail("Blank image accepted as QR");
        }catch(Exception expected){}
    }

    @Test public void arbitraryQrDataCannotBecomeTrustedPeer()throws Exception{
        UUID owner=UUID.randomUUID();
        String arbitrary="https://example.invalid/attacker";
        String decoded=NexoConnectQrImageDecoder.decodeImageBytes(png(
                NexoConnectOwnQrView.encodeQrBitmap(arbitrary,512)));
        assertEquals(arbitrary,decoded);
        try{
            NexoConnectQrScannerActivity.requireScannedPeerQr(decoded,owner);
            fail("Non-Nexo QR passed strict peer parser");
        }catch(Exception expected){}
    }
}
