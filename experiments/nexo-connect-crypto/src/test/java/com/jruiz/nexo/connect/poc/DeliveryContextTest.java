package com.jruiz.nexo.connect.poc;

import com.google.crypto.tink.KeysetHandle;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

public final class DeliveryContextTest {
    private static final UUID DELIVERY =
            UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID SENDER =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID RECIPIENT =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final long CREATED = 1780000000000L;

    private static byte[] digest(String text) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
    }

    private static DeliveryContext ctx(
            UUID delivery, UUID recipient, DeliveryContext.EntityType type,
            int senderGen, int recipientGen) throws Exception {
        return new DeliveryContext(
                delivery, SENDER, recipient, type,
                senderGen, recipientGen,
                CREATED, CREATED + 30L * 24 * 60 * 60 * 1000,
                digest("public-sender-key"), digest("public-recipient-key"));
    }

    @Test public void contextIsDeterministicAndBounded() throws Exception {
        byte[] a = ctx(DELIVERY, RECIPIENT, DeliveryContext.EntityType.NOTE,1,1).encode();
        byte[] b = ctx(DELIVERY, RECIPIENT, DeliveryContext.EntityType.NOTE,1,1).encode();
        assertEquals(DeliveryContext.ENCODED_SIZE,a.length);
        assertArrayEquals(a,b);
        assertArrayEquals(new byte[] {'N','X','C','1',1},Arrays.copyOfRange(a,0,5));
    }

    @Test public void allSevenEntityTypesHaveDifferentContexts() throws Exception {
        byte[][] all = new byte[7][];
        int i=0;
        for(DeliveryContext.EntityType type : DeliveryContext.EntityType.values()) {
            all[i++] = ctx(DELIVERY,RECIPIENT,type,1,1).encode();
        }
        assertEquals(7,i);
        for(int a=0;a<all.length;a++) for(int b=a+1;b<all.length;b++)
            assertFalse(Arrays.equals(all[a],all[b]));
    }

    @Test public void signingBindsMessageToVerifiedIdentitiesAndType() throws Exception {
        KeysetHandle signer = CryptoProof.newSenderSigningKeys();
        KeysetHandle recipient = CryptoProof.newRecipientKeys();
        byte[] original = ctx(DELIVERY,RECIPIENT,DeliveryContext.EntityType.DOCUMENT,1,3).encode();
        byte[] text = "Documento cifrado".getBytes(StandardCharsets.UTF_8);
        CryptoProof.SealedMessage msg=CryptoProof.seal(
                signer,CryptoProof.publicKeys(recipient),text,original);
        assertArrayEquals(text,CryptoProof.open(
                recipient,CryptoProof.publicKeys(signer),msg,original));

        assertRejected(recipient, signer, msg,
                ctx(DELIVERY,OTHER,DeliveryContext.EntityType.DOCUMENT,1,3).encode());
        assertRejected(recipient, signer, msg,
                ctx(DELIVERY,RECIPIENT,DeliveryContext.EntityType.BOOK,1,3).encode());
        assertRejected(recipient, signer, msg,
                ctx(UUID.randomUUID(),RECIPIENT,DeliveryContext.EntityType.DOCUMENT,1,3).encode());
        assertRejected(recipient, signer, msg,
                ctx(DELIVERY,RECIPIENT,DeliveryContext.EntityType.DOCUMENT,2,3).encode());
        assertRejected(recipient, signer, msg,
                ctx(DELIVERY,RECIPIENT,DeliveryContext.EntityType.DOCUMENT,1,4).encode());
    }

    @Test public void noSelfSendOrUnboundedExpirationOrWeakFingerprint() throws Exception {
        byte[] fp = digest("fingerprint");
        try {
            new DeliveryContext(DELIVERY,SENDER,SENDER,DeliveryContext.EntityType.NOTE,
                    1,1,CREATED,CREATED+100,fp,fp);
            fail("Self-send accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            new DeliveryContext(DELIVERY,SENDER,RECIPIENT,DeliveryContext.EntityType.NOTE,
                    1,1,CREATED,CREATED+31L*24*60*60*1000,fp,fp);
            fail("Oversized expiry accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            new DeliveryContext(DELIVERY,SENDER,RECIPIENT,DeliveryContext.EntityType.NOTE,
                    1,1,CREATED,CREATED+100,new byte[16],fp);
            fail("Invalid fingerprint accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static void assertRejected(KeysetHandle recipient,KeysetHandle sender,
            CryptoProof.SealedMessage message,byte[] other) throws Exception {
        try {
            CryptoProof.open(recipient,CryptoProof.publicKeys(sender),message,other);
            fail("Altered metadata unexpectedly accepted");
        } catch (GeneralSecurityException expected) {}
    }
}
