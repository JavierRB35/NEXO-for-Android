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

import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class QrTrustConfirmationViewTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final UUID PEER=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final class Fixture {
        final AndroidPeerTrustStore store;
        final AndroidPeerTrustStore.PeerPin pin;
        final String qr;
        Fixture(Context ctx)throws Exception{
            SignatureConfig.register();
            store=new AndroidPeerTrustStore(ctx,UUID.randomUUID());
            KeysetHandle key=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            byte[] signing=((Ed25519PublicKey)key.getPublicKeysetHandle().getAt(0).getKey())
                    .getPublicKeyBytes().toByteArray();
            byte[] encryption=new byte[32];
            new java.security.SecureRandom().nextBytes(encryption);
            pin=new AndroidPeerTrustStore.PeerPin(PEER,1,signing,encryption,true);
            qr=AndroidPeerTrustStore.encodeQr(pin);
        }
    }
    @Test public void independentValidationRejectsMissingConsentOrMismatchedKeys()
            throws Exception{
        Fixture f=new Fixture(context);
        try{
            QrTrustConfirmationView.requireHumanVerification(f.pin,
                    f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),false,true);
            fail("UI without channel confirmation accepted");
        }catch(GeneralSecurityException expected){}
        try{
            QrTrustConfirmationView.requireHumanVerification(f.pin,
                    f.pin.signingFingerprint(),"00".repeat(32),true,true);
            fail("Wrong HPKE fingerprint was accepted");
        }catch(GeneralSecurityException expected){}
        try{
            QrTrustConfirmationView.requireHumanVerification(f.pin,
                    f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true,false);
            fail("UI without consent accepted");
        }catch(GeneralSecurityException expected){}
        QrTrustConfirmationView.requireHumanVerification(f.pin,
                f.pin.signingFingerprint(),f.pin.encryptionFingerprint(),true,true);
    }

    @Test public void verificationFieldsAreEmptyAndButtonStartsDisabled() throws Exception{
        Fixture f=new Fixture(context);
        final AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                holder.set(new QrTrustConfirmationView(context,f.qr,f.store,
                        new QrTrustConfirmationView.Listener(){
                            @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){}
                            @Override public void onFailure(String reason){}
                        }));
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView view=holder.get();
        assertNotNull(view);
        assertEquals("",view.signingInputForTest().getText().toString());
        assertEquals("",view.encryptionInputForTest().getText().toString());
        assertFalse(view.channelCheckForTest().isChecked());
        assertFalse(view.consentCheckForTest().isChecked());
        assertFalse(view.buttonForTest().isEnabled());
        assertNull(f.store.findActive(PEER));
    }

    @Test public void manuallyEnteredFullFingerprintsAndConsentSaveViaKeystore()
            throws Exception {
        Fixture f=new Fixture(context);
        final String signerFingerprint=f.pin.signingFingerprint();
        final String encryptionFingerprint=f.pin.encryptionFingerprint();
        final AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        final AtomicReference<Exception> error=new AtomicReference<>();
        final CountDownLatch saved=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                holder.set(new QrTrustConfirmationView(context,f.qr,f.store,
                        new QrTrustConfirmationView.Listener(){
                            @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){
                                saved.countDown();
                            }
                            @Override public void onFailure(String reason){
                                error.set(new SecurityException(reason));
                                saved.countDown();
                            }
                        }));
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView view=holder.get();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            view.signingInputForTest().setText(signerFingerprint);
            view.encryptionInputForTest().setText(encryptionFingerprint);
            assertFalse(view.buttonForTest().isEnabled());
            view.channelCheckForTest().setChecked(true);
            view.consentCheckForTest().setChecked(true);
            assertTrue(view.buttonForTest().isEnabled());
            view.buttonForTest().performClick();
            assertFalse(view.buttonForTest().isEnabled());
        });
        assertTrue("Keystore UI callback never returned",saved.await(35,TimeUnit.SECONDS));
        if(error.get()!=null)throw error.get();
        AndroidPeerTrustStore.PeerPin stored=f.store.findActive(PEER);
        assertNotNull(stored);
        assertEquals(1,stored.generation);
        assertArrayEquals(f.pin.signingPublicRaw(),stored.signingPublicRaw());
    }

    @Test public void mismatchedManualFingerprintDoesNotWriteTrust() throws Exception{
        Fixture f=new Fixture(context);
        final String encryptionFingerprint=f.pin.encryptionFingerprint();
        final AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        final CountDownLatch failed=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                holder.set(new QrTrustConfirmationView(context,f.qr,f.store,
                        new QrTrustConfirmationView.Listener(){
                            @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){
                                throw new AssertionError("Accepted incorrect fingerpint");
                            }
                            @Override public void onFailure(String reason){
                                failed.countDown();
                            }
                        }));
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView view=holder.get();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            view.signingInputForTest().setText("00".repeat(32));
            view.encryptionInputForTest().setText(encryptionFingerprint);
            view.channelCheckForTest().setChecked(true);
            view.consentCheckForTest().setChecked(true);
            assertTrue(view.buttonForTest().isEnabled());
            view.buttonForTest().performClick();
        });
        assertTrue(failed.await(3,TimeUnit.SECONDS));
        assertNull(f.store.findActive(PEER));
    }
}
