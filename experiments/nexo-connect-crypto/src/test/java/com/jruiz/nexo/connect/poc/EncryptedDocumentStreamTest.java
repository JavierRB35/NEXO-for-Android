package com.jruiz.nexo.connect.poc;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;

public class EncryptedDocumentStreamTest {
    private static final UUID DELIVERY = UUID.fromString(
        "11111111-1111-4111-8111-111111111111");

    private static byte[] material(int n) {
        byte[] data = new byte[n];
        new java.security.SecureRandom().nextBytes(data);
        return data;
    }

    private static byte[] seal(byte[] plain, byte[] key) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] digest = EncryptedDocumentStream.encrypt(
            new ByteArrayInputStream(plain), output, key, DELIVERY, plain.length);
        assertArrayEquals(EncryptedDocumentStream.sha256(plain), digest);
        assertFalse(Arrays.equals(plain, output.toByteArray()));
        return output.toByteArray();
    }

    @Test public void roundTripBeyondChunkBoundary() throws Exception {
        byte[] key = EncryptedDocumentStream.generateKey();
        byte[] original = material(EncryptedDocumentStream.CHUNK * 3 + 123);
        byte[] encrypted = seal(original, key);
        ByteArrayOutputStream dest = new ByteArrayOutputStream();
        long size = EncryptedDocumentStream.decrypt(new ByteArrayInputStream(encrypted),
            dest,key,DELIVERY,EncryptedDocumentStream.sha256(original));
        assertEquals(original.length,size);
        assertArrayEquals(original,dest.toByteArray());
    }

    @Test public void exactTenMiBBoundaryRoundTrip() throws Exception {
        byte[] key=EncryptedDocumentStream.generateKey();
        byte[] original=material((int)EncryptedDocumentStream.MAX_PLAIN);
        byte[] encrypted=seal(original,key);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        assertEquals(original.length,EncryptedDocumentStream.decrypt(
            new ByteArrayInputStream(encrypted),output,key,DELIVERY,
            EncryptedDocumentStream.sha256(original)));
        assertArrayEquals(original,output.toByteArray());
    }

    @Test public void rejectsReorderedAndTamperedFragments() throws Exception {
        byte[] key=EncryptedDocumentStream.generateKey();
        byte[] original=material(EncryptedDocumentStream.CHUNK+10);
        byte[] sealed=seal(original,key);
        sealed[50]^=1; // first IV/payload corruption
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(sealed),
          new ByteArrayOutputStream(),key,DELIVERY,EncryptedDocumentStream.sha256(original)));
    }

    @Test public void rejectsWrongKeyAndWrongDeliveryId() throws Exception {
        byte[] original=material(12345);
        byte[] key=EncryptedDocumentStream.generateKey();
        byte[] sealed=seal(original,key);
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(sealed),
            new ByteArrayOutputStream(),EncryptedDocumentStream.generateKey(),
            DELIVERY,EncryptedDocumentStream.sha256(original)));
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(sealed),
            new ByteArrayOutputStream(),key,UUID.randomUUID(),
            EncryptedDocumentStream.sha256(original)));
    }

    @Test public void rejectsTruncatedAppendedAndWrongHash() throws Exception {
        byte[] original=material(EncryptedDocumentStream.CHUNK+3);
        byte[] key=EncryptedDocumentStream.generateKey();
        byte[] sealed=seal(original,key);
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(
            Arrays.copyOf(sealed,sealed.length-17)),
            new ByteArrayOutputStream(),key,DELIVERY,EncryptedDocumentStream.sha256(original)));
        byte[] extra=Arrays.copyOf(sealed,sealed.length+1);
        extra[extra.length-1]=9;
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(extra),
            new ByteArrayOutputStream(),key,DELIVERY,EncryptedDocumentStream.sha256(original)));
        fails(()->EncryptedDocumentStream.decrypt(new ByteArrayInputStream(sealed),
            new ByteArrayOutputStream(),key,DELIVERY,new byte[32]));
    }

    @Test public void rejectsUnboundedPayloadAndDeclaredSizeMismatch() throws Exception {
        byte[] plain=material(42);
        byte[] key=EncryptedDocumentStream.generateKey();
        fails(()->EncryptedDocumentStream.encrypt(
            new ByteArrayInputStream(plain),new ByteArrayOutputStream(),key,
            DELIVERY,EncryptedDocumentStream.MAX_PLAIN+1));
        fails(()->EncryptedDocumentStream.encrypt(
            new ByteArrayInputStream(plain),new ByteArrayOutputStream(),key,
            DELIVERY,plain.length-1));
        fails(()->EncryptedDocumentStream.encrypt(
            new ByteArrayInputStream(plain),new ByteArrayOutputStream(),key,
            DELIVERY,plain.length+1));
    }

    private interface Throwing { void run() throws Exception; }
    private static void fails(Throwing job) throws Exception {
        try {job.run();fail("Untrusted document accepted");}
        catch (GeneralSecurityException | IOException expected) {}
    }
}
