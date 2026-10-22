package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectLocalPhoneIdentityTest {
    private NexoConnectLocalPhoneIdentity open(UUID account)throws Exception{
        return NexoConnectLocalPhoneIdentity.openOrCreate(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),account);
    }
    @Test public void privateIdentityAndDeviceIdSurviveRestartWithoutRotation()throws Exception{
        UUID account=UUID.randomUUID();
        AndroidIdentityVault vault=new AndroidIdentityVault(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),account);
        try{
            NexoConnectLocalPhoneIdentity first=open(account);
            byte[] signing=raw(first.signingPrivate());
            assertEquals(account,first.accountId());
            assertTrue(vault.exists());
            NexoConnectLocalPhoneIdentity restart=open(account);
            assertEquals(first.deviceId(),restart.deviceId());
            assertArrayEquals(signing,raw(restart.signingPrivate()));
            assertFalse(Files.readAllBytes(vault.localSealedFileForTest().toPath())
                    .length==0);
            assertEquals(4,first.deviceId().version());
        }finally{vault.destroy();}
    }
    @Test public void separateAccountsCannotInheritSameIdentity()throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        AndroidIdentityVault av=new AndroidIdentityVault(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),a);
        AndroidIdentityVault bv=new AndroidIdentityVault(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),b);
        try{
            NexoConnectLocalPhoneIdentity first=open(a),second=open(b);
            assertNotEquals(first.deviceId(),second.deviceId());
            assertNotEquals(first.accountId(),second.accountId());
        }finally{av.destroy();bv.destroy();}
    }
    @Test public void lostKeystoreDoesNotSilentlyCreateReplacementIdentity()
            throws Exception{
        UUID account=UUID.randomUUID();
        AndroidIdentityVault vault=new AndroidIdentityVault(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),account);
        try{
            open(account);
            java.io.File file=vault.localSealedFileForTest();
            byte[] altered=Files.readAllBytes(file.toPath());
            altered[altered.length-1]^=0x11;
            Files.write(file.toPath(),altered);
            try{open(account);fail("Corrupt vault rotated keys silently");}
            catch(java.security.GeneralSecurityException expected){}
            assertTrue(file.exists());
        }finally{vault.destroy();}
    }
    private static byte[] raw(KeysetHandle handle)throws Exception{
        return ((Ed25519PublicKey)handle.getPublicKeysetHandle()
                .getAt(0).getKey()).getPublicKeyBytes().toByteArray();
    }
}
