package com.jruiz.nexo.connect.vault;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.crypto.tink.signature.Ed25519PublicKey;
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
    private static final class Fixture{
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
    private static QrTrustConfirmationView view(Context ctx,Fixture f,
            QrTrustConfirmationView.Listener listener)throws Exception {
        return new QrTrustConfirmationView(ctx,f.qr,f.store,listener);
    }
    @Test public void singleSafetyCodeIsBoundToBothKeysAccountAndGeneration()
            throws Exception {
        Fixture f=new Fixture(context);
        String code=NexoConnectSafetyCode.forPeer(f.pin);
        assertEquals(16,code.length());
        assertEquals(code,NexoConnectSafetyCode.normalize(
                NexoConnectSafetyCode.grouped(code)));
        assertNotEquals(code,NexoConnectSafetyCode.forPeer(
                new AndroidPeerTrustStore.PeerPin(UUID.randomUUID(),1,
                f.pin.signingPublicRaw(),f.pin.encryptionPublicRaw(),true)));
        assertNotEquals(code,NexoConnectSafetyCode.forPeer(
                new AndroidPeerTrustStore.PeerPin(PEER,2,
                f.pin.signingPublicRaw(),f.pin.encryptionPublicRaw(),true)));
        byte[] changed=f.pin.encryptionPublicRaw();
        changed[0]^=1;
        assertNotEquals(code,NexoConnectSafetyCode.forPeer(
                new AndroidPeerTrustStore.PeerPin(PEER,1,
                f.pin.signingPublicRaw(),changed,true)));
        try{NexoConnectSafetyCode.verify(f.pin,code,false,true);
            fail("Independent channel requirement bypassed");
        }catch(GeneralSecurityException expected){}
        try{NexoConnectSafetyCode.verify(f.pin,code,true,false);
            fail("Consent requirement bypassed");
        }catch(GeneralSecurityException expected){}
        try{NexoConnectSafetyCode.verify(f.pin,"FFFFFFFFFFFFFFFF",true,true);
            fail("Incorrect code accepted");
        }catch(GeneralSecurityException expected){}
        NexoConnectSafetyCode.verify(f.pin,code,true,true);
    }

    @Test public void codeFieldIsEmptyAndCannotConfirmAutomatically()throws Exception{
        Fixture f=new Fixture(context);
        AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{holder.set(view(context,f,new QrTrustConfirmationView.Listener(){
                @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){}
                @Override public void onFailure(String reason){}
            }));}catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView ui=holder.get();
        assertEquals("",ui.safetyCodeInputForTest().getText().toString());
        assertFalse(ui.consentCheckForTest().isChecked());
        assertFalse(ui.buttonForTest().isEnabled());
        assertNull(f.store.findActive(PEER));
    }
    @Test public void manuallyObtainedSingleCodeAndConsentSaveFullKeys()
            throws Exception{
        Fixture f=new Fixture(context);
        String code=NexoConnectSafetyCode.forPeer(f.pin);
        CountDownLatch completed=new CountDownLatch(1);
        AtomicReference<Exception> failure=new AtomicReference<>();
        AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{holder.set(view(context,f,new QrTrustConfirmationView.Listener(){
                @Override public void onVerified(AndroidPeerTrustStore.PeerPin p){completed.countDown();}
                @Override public void onFailure(String reason){
                    failure.set(new SecurityException(reason));completed.countDown();
                }
            }));}catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView ui=holder.get();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ui.safetyCodeInputForTest().setText(NexoConnectSafetyCode.grouped(code));
            assertFalse(ui.buttonForTest().isEnabled());
            ui.consentCheckForTest().setChecked(true);
            assertTrue(ui.buttonForTest().isEnabled());
            ui.buttonForTest().performClick();
        });
        assertTrue("Keystore callback was not returned",completed.await(35,TimeUnit.SECONDS));
        if(failure.get()!=null)throw failure.get();
        AndroidPeerTrustStore.PeerPin stored=f.store.findActive(PEER);
        assertNotNull(stored);
        assertArrayEquals(f.pin.signingPublicRaw(),stored.signingPublicRaw());
        assertArrayEquals(f.pin.encryptionPublicRaw(),stored.encryptionPublicRaw());
    }

    @Test public void mismatchedCodeNeverWritesTrust()throws Exception{
        Fixture f=new Fixture(context);
        CountDownLatch failed=new CountDownLatch(1);
        AtomicReference<QrTrustConfirmationView> holder=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{holder.set(view(context,f,new QrTrustConfirmationView.Listener(){
                @Override public void onVerified(AndroidPeerTrustStore.PeerPin pin){
                    throw new AssertionError("Incorrect code accepted");
                }
                @Override public void onFailure(String reason){failed.countDown();}
            }));}catch(Exception ex){throw new RuntimeException(ex);}
        });
        QrTrustConfirmationView ui=holder.get();
        String incorrect=NexoConnectSafetyCode.forPeer(f.pin);
        incorrect=(incorrect.charAt(0)=='0'?"1":"0")+incorrect.substring(1);
        String finalIncorrect=incorrect;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ui.safetyCodeInputForTest().setText(finalIncorrect);
            ui.consentCheckForTest().setChecked(true);
            assertTrue(ui.buttonForTest().isEnabled());
            ui.buttonForTest().performClick();
        });
        assertTrue(failed.await(3,TimeUnit.SECONDS));
        assertNull(f.store.findActive(PEER));
    }
}
