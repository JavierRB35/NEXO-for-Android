package com.jruiz.nexo.connect.vault;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AndroidIdentityVaultTest {
    private Context app() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private AndroidIdentityVault freshVault() throws Exception {
        return new AndroidIdentityVault(app(), UUID.randomUUID());
    }
    private byte[] randomKeyset() {
        byte[] bytes = new byte[96];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    @Test public void savedKeysSurviveObjectRecreationAndStayEncrypted() throws Exception {
        UUID account = UUID.randomUUID();
        AndroidIdentityVault vault = new AndroidIdentityVault(app(), account);
        byte[] hpke = randomKeyset();
        byte[] signer = randomKeyset();
        try {
            vault.saveNew(hpke, signer);
            byte[] disk = Files.readAllBytes(vault.localSealedFileForTest().toPath());
            assertFalse(contains(disk, hpke));
            assertFalse(contains(disk, signer));
            AndroidIdentityVault reloaded = new AndroidIdentityVault(app(), account);
            try (AndroidIdentityVault.IdentityBytes identity = reloaded.restore()) {
                assertArrayEquals(hpke, identity.hpkeKeyset());
                assertArrayEquals(signer, identity.signingKeyset());
            }
            assertTrue(reloaded.exists());
        } finally { vault.destroy(); }
    }

    @Test public void cannotSilentlyOverwriteExistingIdentity() throws Exception {
        AndroidIdentityVault vault = freshVault();
        try {
            vault.saveNew(randomKeyset(), randomKeyset());
            try {
                vault.saveNew(randomKeyset(), randomKeyset());
                fail("No debe sustituir identidad");
            } catch (GeneralSecurityException expected) {}
        } finally { vault.destroy(); }
    }

    @Test public void changedCiphertextIsRejected() throws Exception {
        AndroidIdentityVault vault = freshVault();
        try {
            vault.saveNew(randomKeyset(), randomKeyset());
            File file = vault.localSealedFileForTest();
            byte[] disk = Files.readAllBytes(file.toPath());
            disk[disk.length-1] ^= 1;
            Files.write(file.toPath(), disk);
            try {
                vault.restore();
                fail("No debe descifrar ciphertext manipulado");
            } catch (GeneralSecurityException expected) {}
        } finally { vault.destroy(); }
    }

    @Test public void copyingBlobAcrossAccountsIsRejected() throws Exception {
        AndroidIdentityVault a = freshVault();
        AndroidIdentityVault b = freshVault();
        try {
            a.saveNew(randomKeyset(), randomKeyset());
            b.saveNew(randomKeyset(), randomKeyset());
            Files.copy(a.localSealedFileForTest().toPath(), b.localSealedFileForTest().toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try {
                b.restore();
                fail("No debe restaurar la identidad de otra cuenta");
            } catch (GeneralSecurityException expected) {}
        } finally {
            a.destroy(); b.destroy();
        }
    }

    @Test public void missingIdentityFailsWithoutGeneratingNewKeys() throws Exception {
        AndroidIdentityVault vault = freshVault();
        try {
            try { vault.restore(); fail("No debe inventar una identidad"); }
            catch (GeneralSecurityException expected) {}
            assertFalse(vault.exists());
        } finally { vault.destroy(); }
    }

    @Test public void destroyingIdentityMakesItUnreadable() throws Exception {
        AndroidIdentityVault vault = freshVault();
        vault.saveNew(randomKeyset(), randomKeyset());
        vault.destroy();
        assertFalse(vault.exists());
        try { vault.restore(); fail("Las claves no deben ser recuperables"); }
        catch (GeneralSecurityException expected) {}
    }

    private boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (haystack[i+j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
}
