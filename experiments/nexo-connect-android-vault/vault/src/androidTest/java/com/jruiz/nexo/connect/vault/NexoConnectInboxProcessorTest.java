package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectInboxProcessorTest {
    private static final UUID SENDER=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID RECIPIENT=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DELIVERY=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final long NOW=1780000000000L;
    private static final String DOMAIN="NEXO-CONNECT-CRYPTO-POC-1";

    private static SQLiteDatabase db() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE items ("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "type TEXT NOT NULL DEFAULT 'note',"+
                "title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',"+
                "original_text TEXT NOT NULL DEFAULT '',"+
                "category TEXT NOT NULL DEFAULT 'Otros',tags TEXT NOT NULL DEFAULT '',"+
                "favorite INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'manual',"+
                "note_title_manual INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }
    private static int rows(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT count(*) FROM items",null)){
            c.moveToFirst();return c.getInt(0);
        }
    }
    private static String hex(byte[] bytes){
        final char[] digits="0123456789abcdef".toCharArray();
        char[] chars=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){chars[i*2]=digits[(bytes[i]>>4)&15];chars[i*2+1]=digits[bytes[i]&15];}
        return new String(chars);
    }
    private static byte[] hash(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private static byte[] signedBytes(byte[] context,byte[] ciphertext)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DataOutputStream w=new DataOutputStream(out);
        w.writeUTF(DOMAIN);w.writeInt(context.length);w.write(context);
        w.writeInt(ciphertext.length);w.write(ciphertext);w.flush();
        return out.toByteArray();
    }
    private static class Fixture {
        final KeysetHandle receiver, signer;
        final JSONObject packet;
        Fixture()throws Exception {
            HybridConfig.register();SignatureConfig.register();
            receiver=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK).build());
            signer=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            byte[] signingRaw=((Ed25519PublicKey)signer.getPublicKeysetHandle()
                .getAt(0).getKey()).getPublicKeyBytes().toByteArray();
            byte[] encryptionRaw=((HpkePublicKey)receiver.getPublicKeysetHandle()
                .getAt(0).getKey()).getPublicKeyBytes().toByteArray();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            DataOutputStream w=new DataOutputStream(out);
            w.writeInt(0x4e584331);w.writeByte(1);
            w.writeLong(DELIVERY.getMostSignificantBits());w.writeLong(DELIVERY.getLeastSignificantBits());
            w.writeLong(SENDER.getMostSignificantBits());w.writeLong(SENDER.getLeastSignificantBits());
            w.writeLong(RECIPIENT.getMostSignificantBits());w.writeLong(RECIPIENT.getLeastSignificantBits());
            w.writeByte(2);w.writeInt(1);w.writeInt(1);
            w.writeLong(NOW-1000);w.writeLong(NOW+60000);
            w.write(hash(signingRaw));w.write(hash(encryptionRaw));w.flush();
            byte[] context=out.toByteArray();assertEquals(142,context.length);
            byte[] plaintext=NexoConnectPayloadCodec.encode("note",
                new JSONObject().put("title","Recibida").put("body","Mensaje privado"),null);
            byte[] ciphertext=receiver.getPublicKeysetHandle().getPrimitive(
                RegistryConfiguration.get(),HybridEncrypt.class).encrypt(plaintext,context);
            byte[] signature=signer.getPrimitive(RegistryConfiguration.get(),
                PublicKeySign.class).sign(signedBytes(context,ciphertext));
            packet=new JSONObject()
                .put("deliveryId",DELIVERY.toString())
                .put("senderId",SENDER.toString())
                .put("recipientId",RECIPIENT.toString())
                .put("senderGeneration",1).put("recipientGeneration",1)
                .put("entityType","note").put("expiresAtMs",NOW+60000)
                .put("contextHex",hex(context))
                .put("ciphertextHex",hex(ciphertext))
                .put("ciphertextSha256Hex",hex(hash(ciphertext)))
                .put("signatureHex",hex(signature));
        }
        NexoConnectAtomicImporter.Result receive(SQLiteDatabase database)throws Exception {
            return NexoConnectInboxProcessor.receiveAndImport(database,packet,
                receiver,signer.getPublicKeysetHandle(),RECIPIENT,1,NOW);
        }
    }
    @Test public void verifiesDecryptsAndImportsOnceOnlyAfterCommit()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            NexoConnectAtomicImporter.Result first=f.receive(database);
            assertEquals(1,rows(database));
            assertFalse(first.alreadyImported);
            assertTrue(first.acknowledgementPending);
            NexoConnectAtomicImporter.Result duplicate=f.receive(database);
            assertTrue(duplicate.alreadyImported);
            assertEquals(first.localId,duplicate.localId);
            assertEquals(1,rows(database));
        }
    }
    @Test public void mutatedCiphertextOrSignatureNeverWritesSQLite()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            String cipher=f.packet.getString("ciphertextHex");
            f.packet.put("ciphertextHex",cipher.substring(0,cipher.length()-2)+"00");
            expectReject(()->f.receive(database));
            assertEquals(0,rows(database));
        }
        Fixture g=new Fixture();
        try(SQLiteDatabase database=db()){
            g.packet.put("signatureHex","00".repeat(64));
            expectReject(()->g.receive(database));
            assertEquals(0,rows(database));
        }
    }
    @Test public void recipientOrGenerationSubstitutionNeverImports()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            f.packet.put("recipientId","44444444-4444-4444-8444-444444444444");
            expectReject(()->f.receive(database));
            assertEquals(0,rows(database));
        }
        Fixture g=new Fixture();
        try(SQLiteDatabase database=db()){
            g.packet.put("recipientGeneration",2);
            expectReject(()->g.receive(database));
            assertEquals(0,rows(database));
        }
    }
    @Test public void unknownSenderKeyAndWrongDecryptorFailClosed()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            KeysetHandle outsider=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            expectReject(()->NexoConnectInboxProcessor.receiveAndImport(database,f.packet,
                f.receiver,outsider.getPublicKeysetHandle(),RECIPIENT,1,NOW));
            assertEquals(0,rows(database));
        }
        Fixture g=new Fixture();
        try(SQLiteDatabase database=db()){
            KeysetHandle otherReceiver=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK).build());
            expectReject(()->NexoConnectInboxProcessor.receiveAndImport(database,g.packet,
                otherReceiver,g.signer.getPublicKeysetHandle(),RECIPIENT,1,NOW));
            assertEquals(0,rows(database));
        }
    }
    @Test public void expiredServerClaimOrExpiredSignedContextRejected()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            expectReject(()->NexoConnectInboxProcessor.receiveAndImport(database,
                f.packet,f.receiver,f.signer.getPublicKeysetHandle(),RECIPIENT,1,NOW+60001));
            assertEquals(0,rows(database));
            f.packet.put("expiresAtMs",NOW+300000);
            expectReject(()->f.receive(database));
            assertEquals(0,rows(database));
        }
    }
    @Test public void wrongCipherHashAndWrongEntityNeverImport()throws Exception {
        Fixture f=new Fixture();
        try(SQLiteDatabase database=db()){
            f.packet.put("ciphertextSha256Hex","ff".repeat(32));
            expectReject(()->f.receive(database));
            assertEquals(0,rows(database));
        }
        Fixture g=new Fixture();
        try(SQLiteDatabase database=db()){
            g.packet.put("entityType","list");
            expectReject(()->g.receive(database));
            assertEquals(0,rows(database));
        }
    }
    private interface Throwing {void run()throws Exception;}
    private static void expectReject(Throwing r)throws Exception{
        try{r.run();fail("Untrusted inbox item was imported");}
        catch(GeneralSecurityException expected){}
    }
}
