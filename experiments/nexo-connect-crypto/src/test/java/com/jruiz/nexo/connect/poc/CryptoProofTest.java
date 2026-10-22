package com.jruiz.nexo.connect.poc;

import com.google.crypto.tink.KeysetHandle;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public class CryptoProofTest {
    private KeysetHandle recipient;
    private KeysetHandle sender;
    private byte[] context;

    @Before public void setup() throws Exception {
        recipient = CryptoProof.newRecipientKeys();
        sender = CryptoProof.newSenderSigningKeys();
        // Contexto de ejemplo. El definitivo se construirá con codificación canónica.
        context = "v1|delivery-123|sender-A|recipient-B|note|keygen-1"
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test public void signatureAndPublicKeyUseStandardRawEd25519() throws Exception {
        byte[] pub = CryptoProof.rawSigningPublicKey(sender);
        assertEquals(32, pub.length);
        CryptoProof.SealedMessage msg = CryptoProof.seal(sender,
            CryptoProof.publicKeys(recipient), new byte[]{1,2,3}, context);
        assertEquals(64, msg.signature().length);
    }

    @Test public void encryptedNoteRoundtrip() throws Exception {
        byte[] plain = "Nota confidencial: prueba Nexo Connect".getBytes(StandardCharsets.UTF_8);
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), plain, context);
        assertFalse(Arrays.equals(plain, sealed.ciphertext()));
        assertArrayEquals(plain, CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), sealed, context));
    }

    @Test public void documentTenMiBRoundtrip() throws Exception {
        byte[] document = new byte[10 * 1024 * 1024];
        new SecureRandom().nextBytes(document);
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), document, context);
        assertArrayEquals(document, CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), sealed, context));
    }

    @Test public void wrongRecipientCannotDecrypt() throws Exception {
        byte[] payload = "sensible".getBytes(StandardCharsets.UTF_8);
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), payload, context);
        KeysetHandle outsider = CryptoProof.newRecipientKeys();
        expectFailure(() -> CryptoProof.open(
                outsider, CryptoProof.publicKeys(sender), sealed, context));
    }

    @Test public void wrongSenderCannotAuthenticate() throws Exception {
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {1,2,3}, context);
        KeysetHandle imposter = CryptoProof.newSenderSigningKeys();
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(imposter), sealed, context));
    }

    @Test public void mutatedCiphertextRejected() throws Exception {
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {4,5,6}, context);
        byte[] changed = sealed.ciphertext();
        changed[changed.length - 1] ^= 1;
        CryptoProof.SealedMessage altered = new CryptoProof.SealedMessage(
                sealed.context(), changed, sealed.signature());
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), altered, context));
    }

    @Test public void mutatedSignatureRejected() throws Exception {
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {7,8}, context);
        byte[] changed = sealed.signature();
        changed[changed.length-1] ^= 1;
        CryptoProof.SealedMessage altered = new CryptoProof.SealedMessage(
                sealed.context(), sealed.ciphertext(), changed);
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), altered, context));
    }

    @Test public void changedRecipientContextRejected() throws Exception {
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {9}, context);
        byte[] wrong = "v1|delivery-123|sender-A|recipient-C|note|keygen-1"
                .getBytes(StandardCharsets.UTF_8);
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), sealed, wrong));
    }

    @Test public void multiRecipientCopiesAreIndependent() throws Exception {
        KeysetHandle second = CryptoProof.newRecipientKeys();
        byte[] plain = "Cita para dos usuarios".getBytes(StandardCharsets.UTF_8);
        byte[] context2 = "v1|delivery-124|sender-A|recipient-C|appointment|keygen-1"
                .getBytes(StandardCharsets.UTF_8);
        CryptoProof.SealedMessage first = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), plain, context);
        CryptoProof.SealedMessage other = CryptoProof.seal(
                sender, CryptoProof.publicKeys(second), plain, context2);
        assertFalse(Arrays.equals(first.ciphertext(), other.ciphertext()));
        assertArrayEquals(plain, CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), first, context));
        assertArrayEquals(plain, CryptoProof.open(
                second, CryptoProof.publicKeys(sender), other, context2));
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), other, context2));
    }

    @Test public void invalidInputsFailClosed() throws Exception {
        expectFailure(() -> CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {1}, new byte[0]));
        CryptoProof.SealedMessage sealed = CryptoProof.seal(
                sender, CryptoProof.publicKeys(recipient), new byte[] {1}, context);
        expectFailure(() -> CryptoProof.open(
                recipient, CryptoProof.publicKeys(sender), sealed, new byte[0]));
    }

    private interface ThrowingAction { void run() throws Exception; }
    private static void expectFailure(ThrowingAction action) throws Exception {
        try {
            action.run();
            fail("Debe rechazar este caso");
        } catch (GeneralSecurityException expected) {
            // Rechazo criptográfico correcto.
        }
    }
}
