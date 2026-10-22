package com.jruiz.nexo.connect.vault;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.security.GeneralSecurityException;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectCameraQrFlowTest {
    private static final UUID OWNER=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PEER=UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test public void generatedQrIsDecodableByRealZxingAndContainsOnlyPublicKeys()
            throws Exception{
        // Make this test independent of other instrumentation tests.
        SignatureConfig.register();
        HybridConfig.register();
        KeysetHandle signer=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
        KeysetHandle hpke=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK).build());
        AndroidPeerTrustStore.PeerPin own=
                NexoConnectOwnQrView.publicIdentity(OWNER,1,signer,hpke);
        String qr=AndroidPeerTrustStore.encodeQr(own);
        Bitmap image=NexoConnectOwnQrView.encodeQrBitmap(qr,720);
        assertNotNull(image);
        assertEquals(720,image.getWidth());
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        image.getPixels(pixels,0,image.getWidth(),0,0,
                image.getWidth(),image.getHeight());
        BinaryBitmap binary=new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels)));
        String parsed=new MultiFormatReader().decode(binary).getText();
        assertEquals(qr,parsed);
        AndroidPeerTrustStore.PeerPin result=
                NexoConnectQrScannerActivity.requireScannedPeerQr(parsed,PEER);
        assertEquals(OWNER,result.peerId);
        assertArrayEquals(own.signingPublicRaw(),result.signingPublicRaw());
        assertArrayEquals(own.encryptionPublicRaw(),result.encryptionPublicRaw());
        assertFalse(parsed.contains("privateKey"));
        assertFalse(parsed.contains("secret"));
        assertEquals(6,new org.json.JSONObject(parsed).length());
    }
    @Test public void scannerRejectsSelfPairingOversizedAndNonNexoQr()throws Exception{
        // Make this test independent of other instrumentation tests.
        SignatureConfig.register();
        HybridConfig.register();
        KeysetHandle signer=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
        KeysetHandle hpke=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK).build());
        AndroidPeerTrustStore.PeerPin own=
                NexoConnectOwnQrView.publicIdentity(OWNER,1,signer,hpke);
        String qr=AndroidPeerTrustStore.encodeQr(own);
        try{
            NexoConnectQrScannerActivity.requireScannedPeerQr(qr,OWNER);
            fail("Self pairing accepted");
        }catch(GeneralSecurityException expected){}
        try{
            NexoConnectQrScannerActivity.requireScannedPeerQr(
                    "A".repeat(1025),PEER);
            fail("Excessive camera payload accepted");
        }catch(GeneralSecurityException expected){}
        try{
            NexoConnectQrScannerActivity.requireScannedPeerQr(
                    "{\"format\":\"untrusted\"}",PEER);
            fail("External format accepted");
        }catch(Exception expected){}
    }
}
