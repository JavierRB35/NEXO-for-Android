package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectAtomicImporterTest {
    private static final String ID1 = "11111111-1111-4111-8111-111111111111";
    private static final String ID2 = "22222222-2222-4222-8222-222222222222";
    private static final String SENDER = "33333333-3333-4333-8333-333333333333";
    private static byte[] note(String title) throws Exception {
        return NexoConnectPayloadCodec.encode(
                "note",new JSONObject().put("title",title).put("body","demo"),null);
    }
    private static void prepare(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE demo_items(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL)");
    }
    private static long rows(SQLiteDatabase db) {
        try(Cursor c = db.rawQuery("SELECT count(*) FROM demo_items",null)) {
            c.moveToFirst();return c.getLong(0);
        }
    }
    private static NexoConnectAtomicImporter.EntityWriter writer() {
        return (db,entity,data)-> {
            db.execSQL("INSERT INTO demo_items(title) VALUES(?)",
                    new Object[]{data.getString("title")});
            try(Cursor c=db.rawQuery("SELECT last_insert_rowid()",null)) {
                c.moveToFirst();return c.getLong(0);
            }
        };
    }

    @Test public void duplicatePacketDoesNotInsertTwiceAndAckComesAfterCommit()
            throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            prepare(db);
            byte[] payload=note("Lista de compra");
            NexoConnectAtomicImporter.Result first=
                    NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",payload,writer());
            assertFalse(first.alreadyImported);
            assertTrue(first.acknowledgementPending);
            assertEquals(1,rows(db));
            assertEquals(1,NexoConnectAtomicImporter.pendingAcknowledgements(db,10).size());
            NexoConnectAtomicImporter.Result again=
                    NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",payload,
                            (database,entity,data)->{throw new AssertionError("Duplicate writer invoked");});
            assertTrue(again.alreadyImported);
            assertEquals(first.localId,again.localId);
            assertEquals(1,rows(db));
            assertTrue(NexoConnectAtomicImporter.markServerAccepted(db,ID1));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
            assertFalse(NexoConnectAtomicImporter.markServerAccepted(db,ID2));
        }
    }

    @Test public void failedWriteRollsBackEntityAndReceipt() throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            prepare(db);
            try {
                NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",note("Falla"),
                        (database,entity,data)->{
                            database.execSQL("INSERT INTO demo_items(title) VALUES('partial')");
                            throw new IllegalStateException("Simulated storage failure");
                        });
                fail("Expected transaction rollback");
            }catch(IllegalStateException expected){}
            assertEquals(0,rows(db));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
            NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",note("Falla"),writer());
            assertEquals(1,rows(db));
        }
    }

    @Test public void payloadMutationOrWrongEntityCannotOverwriteReceipt()
            throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            prepare(db);
            NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",note("Uno"),writer());
            try {
                NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",note("Dos"),writer());
                fail("Reused delivery ID with modified content");
            }catch(SecurityException expected){}
            try {
                NexoConnectAtomicImporter.importVerified(db,ID2,SENDER,"list",note("Uno"),writer());
                fail("Entity mismatch");
            }catch(SecurityException expected){}
            assertEquals(1,rows(db));
        }
    }

    @Test public void identicalContentsWithDifferentDeliveryIdRemainSeparate()
            throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            prepare(db);
            byte[] payload=note("Misma nota");
            NexoConnectAtomicImporter.importVerified(db,ID1,SENDER,"note",payload,writer());
            NexoConnectAtomicImporter.importVerified(db,ID2,SENDER,"note",payload,writer());
            assertEquals(2,rows(db));
            assertEquals(2,NexoConnectAtomicImporter.pendingAcknowledgements(db,10).size());
        }
    }
}
