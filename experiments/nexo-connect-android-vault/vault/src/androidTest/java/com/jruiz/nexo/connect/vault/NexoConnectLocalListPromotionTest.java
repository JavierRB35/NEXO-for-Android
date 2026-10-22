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

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Local-only Android test: the source simulates a SIGNED and VERIFIED
 * delivery already committed by the authenticated HPKE inbox.
 * No Supabase, camera, OTP, SMS, network, or user identity is touched.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectLocalListPromotionTest {
    private static final UUID SENDER=
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static SQLiteDatabase database() {
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
                // Match the production pilot inbox: it has no deleted_at column.

                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }

    private static void nativeLists(SQLiteDatabase db,boolean includeEntries){
        db.execSQL("CREATE TABLE lists("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "title TEXT NOT NULL,"+
                "kind TEXT NOT NULL DEFAULT 'General',"+
                "reusable INTEGER NOT NULL DEFAULT 0,"+
                "favorite INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        if(includeEntries)
            db.execSQL("CREATE TABLE list_entries("+
                    "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                    "list_id INTEGER NOT NULL,"+
                    "text TEXT NOT NULL,"+
                    "checked INTEGER NOT NULL DEFAULT 0,"+
                    "sort_order INTEGER NOT NULL DEFAULT 0,"+
                    "category TEXT NOT NULL DEFAULT '',"+
                    "created_at INTEGER NOT NULL)");
    }

    private static class Fixture implements AutoCloseable {
        final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final UUID owner=UUID.randomUUID();
        final UUID delivery=UUID.randomUUID();
        final AndroidPeerTrustStore trust;
        final SQLiteDatabase source=database(),target=database();
        Fixture()throws Exception {
            nativeLists(target,true);
            SignatureConfig.register();
            trust=new AndroidPeerTrustStore(context,owner);
            KeysetHandle key=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            byte[] sign=((Ed25519PublicKey)key.getPublicKeysetHandle()
                    .getAt(0).getKey()).getPublicKeyBytes().toByteArray();
            byte[] encryption=new byte[32];
            new SecureRandom().nextBytes(encryption);
            AndroidPeerTrustStore.PeerPin peer=
                    new AndroidPeerTrustStore.PeerPin(SENDER,1,sign,encryption,true);
            trust.saveQrConfirmed(AndroidPeerTrustStore.encodeQr(peer),
                    peer.signingFingerprint(),peer.encryptionFingerprint(),true);
        }
        JSONObject list()throws Exception {
            return new JSONObject()
                    .put("title","Compra de prueba")
                    .put("kind","Compra")
                    .put("reusable",true)
                    .put("favorite",true)
                    .put("entries",new JSONArray()
                        .put(new JSONObject().put("text","PAN").put("checked",true)
                            .put("sortOrder",2).put("category","Panadería"))
                        .put(new JSONObject().put("text","LECHE").put("checked",false)
                            .put("sortOrder",5).put("category","Lácteos")));
        }
        long storeList()throws Exception {
            return NexoConnectAtomicImporter.importVerified(
                    source,delivery.toString(),SENDER.toString(),"list",
                    NexoConnectPayloadCodec.encode("list",list(),null),
                    new NexoConnectSealedNoteStore(owner,delivery)).localId;
        }
        @Override public void close(){source.close();target.close();}
    }

    private static long count(SQLiteDatabase db,String table) {
        try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){
            assertTrue(c.moveToFirst());return c.getLong(0);
        }
    }

    private static String column(SQLiteDatabase db,String table,String name,long id) {
        try(Cursor c=db.rawQuery("SELECT "+name+" FROM "+table+" WHERE id=?",
                new String[]{Long.toString(id)})){
            assertTrue(c.moveToFirst());return c.getString(0);
        }
    }

    @Test public void emptyInboxIsNonMutating()throws Exception {
        try(Fixture f=new Fixture()){
            assertTrue(NexoConnectLocalListPromotion.list(f.source,20).isEmpty());
            assertEquals(0,count(f.target,"lists"));
        }
    }

    @Test public void explicitConsentPreservesElementsAndPreventsDuplicate()throws Exception {
        try(Fixture f=new Fixture()){
            long sealed=f.storeList();
            assertTrue(NexoConnectSealedNoteStore.isSealed(
                    column(f.source,"items","body",sealed)));
            assertEquals("Ficha cifrada",column(f.source,"items","title",sealed));
            assertEquals(1,NexoConnectLocalListPromotion.list(f.source,20).size());
            NexoConnectLocalListPromotion.VerifiedList safe=
                    NexoConnectLocalListPromotion.read(
                            f.source,f.owner,f.delivery,f.trust);
            assertEquals("Compra de prueba",safe.title);
            assertEquals(2,safe.entries.length());
            assertEquals("LECHE",safe.entries.getJSONObject(1).getString("text"));
            try{
                NexoConnectLocalListPromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,false);
                fail("Created list without explicit consent");
            }catch(SecurityException expected){}
            assertEquals(0,count(f.target,"lists"));
            NexoConnectLocalListPromotion.Result result=
                    NexoConnectLocalListPromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            assertFalse(result.alreadyCopied);
            assertEquals(1,count(f.target,"lists"));
            assertEquals(2,count(f.target,"list_entries"));
            assertEquals("Compra de prueba",
                    column(f.target,"lists","title",result.nativeListId));
            assertEquals("Compra",
                    column(f.target,"lists","kind",result.nativeListId));
            try(Cursor c=f.target.rawQuery(
                    "SELECT text,checked,sort_order,category FROM list_entries "+
                    "WHERE list_id=? ORDER BY sort_order",
                    new String[]{Long.toString(result.nativeListId)})){
                assertTrue(c.moveToFirst());
                assertEquals("PAN",c.getString(0));
                assertEquals(1,c.getInt(1));
                assertEquals(2,c.getInt(2));
                assertEquals("Panadería",c.getString(3));
                assertTrue(c.moveToNext());
                assertEquals("LECHE",c.getString(0));
                assertEquals(0,c.getInt(1));
                assertEquals(5,c.getInt(2));
                assertEquals("Lácteos",c.getString(3));
                assertFalse(c.moveToNext());
            }
            assertEquals(1,count(f.target,"nexo_connect_native_list_promotions"));
            NexoConnectLocalListPromotion.Result replay=
                    NexoConnectLocalListPromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            assertTrue(replay.alreadyCopied);
            assertEquals(result.nativeListId,replay.nativeListId);
            assertEquals(1,count(f.target,"lists"));
            assertEquals(2,count(f.target,"list_entries"));
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(
                    f.source,20).size());
        }
    }

    @Test public void revokedSenderCannotPreviewOrCopy()throws Exception {
        try(Fixture f=new Fixture()){
            f.storeList();
            assertTrue(f.trust.revoke(SENDER));
            try{
                NexoConnectLocalListPromotion.read(
                        f.source,f.owner,f.delivery,f.trust);
                fail("Read after revoked trust");
            }catch(SecurityException expected){}
            try{
                NexoConnectLocalListPromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Copied after revoked trust");
            }catch(SecurityException expected){}
            assertEquals(0,count(f.target,"lists"));
        }
    }

    @Test public void tamperingAccountAndCiphertextAreRejected()throws Exception {
        try(Fixture f=new Fixture()){
            long sealed=f.storeList();
            try{
                NexoConnectLocalListPromotion.read(
                        f.source,UUID.randomUUID(),f.delivery,f.trust);
                fail("Wrong account read");
            }catch(SecurityException expected){}
            f.source.execSQL("UPDATE items SET body=body||'changed' WHERE id=?",
                    new Object[]{sealed});
            try{
                NexoConnectLocalListPromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Ciphertext tampered");
            }catch(Exception expected){}
            assertEquals(0,count(f.target,"lists"));
        }
    }

    @Test public void nativeWriteFailureRollsBackParentAndReceipt()throws Exception {
        try(Fixture f=new Fixture();
            SQLiteDatabase broken=database()){
            f.storeList();
            nativeLists(broken,false);
            try{
                NexoConnectLocalListPromotion.copyApproved(
                        f.source,broken,f.owner,f.delivery,f.trust,true);
                fail("Accepted missing list_entries table");
            }catch(android.database.sqlite.SQLiteException expected){}
            assertEquals(0,count(broken,"lists"));
            try(Cursor c=broken.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' "+
                    "AND name='nexo_connect_native_list_promotions'",null)){
                assertFalse(c.moveToFirst());
            }
        }
    }

    @Test public void deletedNativeListIsNeverSilentlyRecreated()throws Exception {
        try(Fixture f=new Fixture()){
            f.storeList();
            NexoConnectLocalListPromotion.Result first=
                    NexoConnectLocalListPromotion.copyApproved(
                            f.source,f.target,f.owner,f.delivery,f.trust,true);
            f.target.delete("list_entries","list_id=?",
                    new String[]{Long.toString(first.nativeListId)});
            f.target.delete("lists","id=?",
                    new String[]{Long.toString(first.nativeListId)});
            try{
                NexoConnectLocalListPromotion.copyApproved(
                        f.source,f.target,f.owner,f.delivery,f.trust,true);
                fail("Resurrected deleted list");
            }catch(SecurityException expected){}
            assertEquals(0,count(f.target,"lists"));
            assertEquals(1,count(f.target,"nexo_connect_native_list_promotions"));
        }
    }
}
