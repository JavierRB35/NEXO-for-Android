package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.UUID;

import static org.junit.Assert.*;

/** Instrumented security regressions; run with a real AndroidKeyStore. */
@RunWith(AndroidJUnit4.class)
public class NexoConnectSealedNoteStoreTest {
    private static final String OWNER="11111111-1111-4111-8111-111111111111";
    private static final String SENDER="22222222-2222-4222-8222-222222222222";
    private static final String FIRST="33333333-3333-4333-8333-333333333333";
    private static final String SECOND="44444444-4444-4444-8444-444444444444";

    private static SQLiteDatabase database() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "type TEXT NOT NULL DEFAULT 'note',title TEXT NOT NULL,"+
            "body TEXT NOT NULL DEFAULT '',original_text TEXT NOT NULL DEFAULT '',"+
            "category TEXT NOT NULL DEFAULT 'Otros',tags TEXT NOT NULL DEFAULT '',"+
            "favorite INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'manual',"+
            "note_title_manual INTEGER NOT NULL DEFAULT 0,"+
            "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }

    private static long importNote(SQLiteDatabase db,String receipt,String title,String body)
            throws Exception {
        byte[] clear=NexoConnectPayloadCodec.encode("note",
            new JSONObject().put("title",title).put("body",body),null);
        NexoConnectAtomicImporter.Result saved=NexoConnectAtomicImporter.importVerified(
            db,receipt,SENDER,"note",clear,
            new NexoConnectSealedNoteStore(UUID.fromString(OWNER),
                                            UUID.fromString(receipt)));
        return saved.localId;
    }

    private static String storedBody(SQLiteDatabase db,long id) {
        try(Cursor c=db.rawQuery("SELECT body FROM items WHERE id=?",
            new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }

    private static String storedTitle(SQLiteDatabase db,long id) {
        try(Cursor c=db.rawQuery("SELECT title FROM items WHERE id=?",
            new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }

    @Test public void storesNoPlaintextAndReadsOnlyWithMatchingIdentity()
            throws Exception {
        try(SQLiteDatabase db=database()) {
            long localId=importNote(db,FIRST,"Confidencial unico","Mensaje privado unico");
            String blob=storedBody(db,localId);
            assertTrue(NexoConnectSealedNoteStore.isSealed(blob));
            assertFalse(blob.contains("Mensaje privado unico"));
            assertFalse(blob.contains("Confidencial unico"));
            assertEquals("Nota cifrada",storedTitle(db,localId));
            JSONObject recovered=new NexoConnectSealedNoteStore(
                UUID.fromString(OWNER),UUID.fromString(FIRST)).read(blob);
            assertEquals("Confidencial unico",recovered.getString("title"));
            assertEquals("Mensaje privado unico",recovered.getString("body"));
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(db,20).size());
            assertTrue(NexoConnectAtomicImporter.importVerified(
                db,FIRST,SENDER,"note",NexoConnectPayloadCodec.encode("note",
                    new JSONObject().put("title","Confidencial unico")
                                    .put("body","Mensaje privado unico"),null),
                new NexoConnectSealedNoteStore(UUID.fromString(OWNER),
                                               UUID.fromString(FIRST))).alreadyImported);
            try(Cursor c=db.rawQuery("SELECT count(*) FROM items",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
        }
    }

    @Test public void ciphertextCannotBeMovedToAnotherAccountOrReceipt()
            throws Exception {
        try(SQLiteDatabase db=database()){
            long local=importNote(db,FIRST,"Uno","Solo A");
            String blob=storedBody(db,local);
            assertReject(()->new NexoConnectSealedNoteStore(
                UUID.fromString(SENDER),UUID.fromString(FIRST)).read(blob));
            assertReject(()->new NexoConnectSealedNoteStore(
                UUID.fromString(OWNER),UUID.fromString(SECOND)).read(blob));
            assertEquals("Solo A",new NexoConnectSealedNoteStore(
                UUID.fromString(OWNER),UUID.fromString(FIRST)).read(blob).getString("body"));
        }
    }

    @Test public void corruptedStorageFailsClosedAndRejectsUnsupportedEntities()
            throws Exception {
        try(SQLiteDatabase db=database()){
            long local=importNote(db,FIRST,"Integridad","Texto intacto");
            String blob=storedBody(db,local);
            String bad=blob.substring(0,blob.length()-2)+"xx";
            assertReject(()->new NexoConnectSealedNoteStore(
                UUID.fromString(OWNER),UUID.fromString(FIRST)).read(bad));
            try {
                db.beginTransaction();
                new NexoConnectSealedNoteStore(UUID.fromString(OWNER),
                    UUID.fromString(SECOND)).insertNew(db,"list",
                    new JSONObject().put("title","Prohibida"));
                fail("Unprotected list accepted");
            } catch(GeneralSecurityException expected) {
            } finally {
                if(db.inTransaction())db.endTransaction();
            }
        }
    }
    private interface Operation {void run()throws Exception;}
    private static void assertReject(Operation op)throws Exception {
        try {op.run();fail("Tampered note was readable");}
        catch(GeneralSecurityException expected){}
    }
}
