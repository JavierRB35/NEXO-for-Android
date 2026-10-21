package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * No network or Supabase account is involved. Source notes simulate the
 * output of a completed signed+HPKE receive, already sealed via Keystore.
 * The tests assert local consent, cryptographic binding, rollback and replay.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectLocalNotePromotionTest {
    private static final UUID SENDER=
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static SQLiteDatabase database(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE items("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "type TEXT NOT NULL DEFAULT 'note',"+
                "title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',"+
                "original_text TEXT NOT NULL DEFAULT '',"+
                "category TEXT NOT NULL DEFAULT 'Otros',"+
                "tags TEXT NOT NULL DEFAULT '',favorite INTEGER NOT NULL DEFAULT 0,"+
                "source TEXT NOT NULL DEFAULT 'manual',"+
                "note_title_manual INTEGER NOT NULL DEFAULT 0,"+
                "status TEXT NOT NULL DEFAULT 'active',"+
                "deleted_at INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }

    private static final class Fixture implements AutoCloseable {
        final UUID owner=UUID.randomUUID();
        final UUID delivery=UUID.randomUUID();
        final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final AndroidPeerTrustStore trust;
        final SQLiteDatabase source=database(),target=database();

        Fixture()throws Exception{
            SignatureConfig.register();
            trust=new AndroidPeerTrustStore(context,owner);
            KeysetHandle key=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            byte[] sign=((Ed25519PublicKey)key.getPublicKeysetHandle().getAt(0).getKey())
                    .getPublicKeyBytes().toByteArray();
            byte[] encryption=new byte[32];
            new SecureRandom().nextBytes(encryption);
            AndroidPeerTrustStore.PeerPin peer=new AndroidPeerTrustStore.PeerPin(
                    SENDER,1,sign,encryption,true);
            trust.saveQrConfirmed(AndroidPeerTrustStore.encodeQr(peer),
                    peer.signingFingerprint(),peer.encryptionFingerprint(),true);
        }

        long store(String title,String body)throws Exception{
            return NexoConnectAtomicImporter.importVerified(
                    source,delivery.toString(),SENDER.toString(),"note",
                    NexoConnectPayloadCodec.encode("note",
                            new JSONObject().put("title",title).put("body",body),null),
                    new NexoConnectSealedNoteStore(owner,delivery)).localId;
        }

        @Override public void close(){source.close();target.close();}
    }

    private static long count(SQLiteDatabase db,String table){
        try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){
            assertTrue(c.moveToFirst());return c.getLong(0);
        }
    }

    private static String itemBody(SQLiteDatabase db,long id){
        try(Cursor c=db.rawQuery("SELECT body FROM items WHERE id=?",
                new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());return c.getString(0);
        }
    }

    @Test public void emptyInboxHasNoNotesAndNeverCreatesReceipt()throws Exception{
        try(Fixture f=new Fixture()){
            assertTrue(NexoConnectLocalNotePromotion.list(f.source,20).isEmpty());
            assertEquals(0,count(f.target,"items"));
        }
    }

    @Test public void consentThenPromotionCopiesOnceAndLeavesEncryptedSource()
            throws Exception{
        try(Fixture f=new Fixture()){
            long sealedId=f.store("Titulo protegido","Cuerpo que solo se lee con Keystore");
            assertEquals("Nota cifrada",readTitle(f.source,sealedId));
            assertEquals(1,NexoConnectLocalNotePromotion.list(f.source,20).size());
            NexoConnectLocalNotePromotion.VerifiedNote note=
                    NexoConnectLocalNotePromotion.read(f.source,f.owner,f.delivery,f.trust);
            assertEquals("Titulo protegido",note.title);
            assertEquals("Cuerpo que solo se lee con Keystore",note.body);
            try{
                NexoConnectLocalNotePromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,false);
                fail("Promoted without human consent");
            }catch(SecurityException expected){}
            assertEquals(0,count(f.target,"items"));
            NexoConnectLocalNotePromotion.Result first=
                    NexoConnectLocalNotePromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            assertFalse(first.alreadyCopied);
            assertEquals(1,count(f.target,"items"));
            assertEquals("Cuerpo que solo se lee con Keystore",
                    itemBody(f.target,first.nativeNoteId));
            assertEquals(1,count(f.target,"nexo_connect_native_note_promotions"));
            assertTrue(NexoConnectSealedNoteStore.isSealed(itemBody(f.source,sealedId)));
            NexoConnectLocalNotePromotion.Result duplicate=
                    NexoConnectLocalNotePromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            assertTrue(duplicate.alreadyCopied);
            assertEquals(first.nativeNoteId,duplicate.nativeNoteId);
            assertEquals(1,count(f.target,"items"));
            // No server ACK was performed as a side effect of the local copy.
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(f.source,20).size());
        }
    }

    @Test public void revokedSenderCannotBeReadOrPromoted()throws Exception{
        try(Fixture f=new Fixture()){
            f.store("Revocada","No copiar");
            assertTrue(f.trust.revoke(SENDER));
            try{
                NexoConnectLocalNotePromotion.read(f.source,f.owner,f.delivery,f.trust);
                fail("Read with revoked contact");
            }catch(SecurityException expected){}
            try{
                NexoConnectLocalNotePromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Copied after revocation");
            }catch(SecurityException expected){}
            assertEquals(0,count(f.target,"items"));
        }
    }

    @Test public void accountBindingAndTamperedEncryptedBodyFailClosed()throws Exception{
        try(Fixture f=new Fixture()){
            long local=f.store("Protegida","Sin modificaciones");
            try{
                NexoConnectLocalNotePromotion.read(
                        f.source,UUID.randomUUID(),f.delivery,f.trust);
                fail("Wrong authenticated account");
            }catch(SecurityException expected){}
            f.source.execSQL("UPDATE items SET body=body||'tamper' WHERE id=?",
                    new Object[]{local});
            try{
                NexoConnectLocalNotePromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Modified ciphertext accepted");
            }catch(Exception expected){}
            assertEquals(0,count(f.target,"items"));
        }
    }

    @Test public void removedCopyNeverComesBackAutomatically()throws Exception{
        try(Fixture f=new Fixture()){
            f.store("Importada","Texto");
            NexoConnectLocalNotePromotion.Result first=
                    NexoConnectLocalNotePromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            f.target.execSQL("UPDATE items SET deleted_at=123 WHERE id=?",
                    new Object[]{first.nativeNoteId});
            try{
                NexoConnectLocalNotePromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Previously deleted native note resurrected");
            }catch(SecurityException expected){}
            assertEquals(1,count(f.target,"items"));
            assertEquals(1,count(f.target,"nexo_connect_native_note_promotions"));
        }
    }

    private static String readTitle(SQLiteDatabase db,long id){
        try(Cursor c=db.rawQuery("SELECT title FROM items WHERE id=?",
                new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());return c.getString(0);
        }
    }
}
