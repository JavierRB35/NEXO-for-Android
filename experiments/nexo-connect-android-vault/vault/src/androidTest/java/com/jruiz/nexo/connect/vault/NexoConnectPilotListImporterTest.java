package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jruiz.nexo.connect.NexoConnectPilotListImporter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;

import static org.junit.Assert.*;

/** Native list import tests use disposable in-memory databases, never user data. */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectPilotListImporterTest {
    private static final UUID ACCOUNT=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DELIVERY=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SENDER=UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static SQLiteDatabase db() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE lists(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "title TEXT NOT NULL,kind TEXT NOT NULL DEFAULT 'General',"+
                "reusable INTEGER NOT NULL DEFAULT 0,"+
                "favorite INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE list_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "list_id INTEGER NOT NULL,text TEXT NOT NULL,"+
                "checked INTEGER NOT NULL DEFAULT 0,"+
                "sort_order INTEGER NOT NULL DEFAULT 0,"+
                "category TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
        return db;
    }
    private static JSONObject list() throws Exception {
        return new JSONObject().put("title","Compra · 22 oct")
            .put("kind","Compra")
            .put("entries",new JSONArray()
                .put(new JSONObject().put("text","PAN")
                    .put("category","Alimentos").put("sortOrder",0)
                    .put("checked",true))
                .put(new JSONObject().put("text","LECHE")
                    .put("sortOrder",1).put("checked",false)));
    }
    private static int count(SQLiteDatabase db,String table) {
        try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){
            assertTrue(c.moveToFirst());return c.getInt(0);
        }
    }
    @Test public void insertsNativeListAndPreservesEntries() throws Exception {
        try(SQLiteDatabase target=db()) {
            NexoConnectPilotListImporter.Result r=
                    NexoConnectPilotListImporter.copyApproved(
                            target,ACCOUNT,DELIVERY,SENDER,list(),true);
            assertFalse(r.alreadyCopied);
            assertEquals(1,count(target,"lists"));
            assertEquals(2,count(target,"list_entries"));
            try(Cursor c=target.rawQuery(
                    "SELECT text,checked,category FROM list_entries ORDER BY sort_order",null)){
                assertTrue(c.moveToFirst());
                assertEquals("PAN",c.getString(0));
                assertEquals(1,c.getInt(1));
                assertEquals("Alimentos",c.getString(2));
                assertTrue(c.moveToNext());
                assertEquals("LECHE",c.getString(0));
                assertEquals(0,c.getInt(1));
            }
        }
    }
    @Test public void sameDeliveryCannotCreateDuplicate() throws Exception {
        try(SQLiteDatabase target=db()) {
            NexoConnectPilotListImporter.Result first=
                    NexoConnectPilotListImporter.copyApproved(
                            target,ACCOUNT,DELIVERY,SENDER,list(),true);
            NexoConnectPilotListImporter.Result retry=
                    NexoConnectPilotListImporter.copyApproved(
                            target,ACCOUNT,DELIVERY,SENDER,list(),true);
            assertTrue(retry.alreadyCopied);
            assertEquals(first.listId,retry.listId);
            assertEquals(1,count(target,"lists"));
            assertEquals(2,count(target,"list_entries"));
        }
    }
    @Test public void deliveryReplayWithDifferentContentIsRejected() throws Exception {
        try(SQLiteDatabase target=db()) {
            NexoConnectPilotListImporter.copyApproved(
                    target,ACCOUNT,DELIVERY,SENDER,list(),true);
            try {
                NexoConnectPilotListImporter.copyApproved(
                        target,ACCOUNT,DELIVERY,SENDER,
                        list().put("title","Contenido alterado"),true);
                fail("Changed content must fail closed");
            }catch(SecurityException expected){}
            assertEquals(1,count(target,"lists"));
            assertEquals(2,count(target,"list_entries"));
        }
    }
    @Test public void missingConsentDoesNotCreateNativeCopy() throws Exception {
        try(SQLiteDatabase target=db()) {
            try {
                NexoConnectPilotListImporter.copyApproved(
                        target,ACCOUNT,DELIVERY,SENDER,list(),false);
                fail("Missing consent");
            }catch(SecurityException expected){}
            assertEquals(0,count(target,"lists"));
        }
    }
    @Test public void partialInvalidChildRollsBackEverything() throws Exception {
        try(SQLiteDatabase target=db()) {
            JSONObject broken=list();
            broken.getJSONArray("entries").getJSONObject(1).put("sortOrder",-1);
            try {
                NexoConnectPilotListImporter.copyApproved(
                        target,ACCOUNT,DELIVERY,SENDER,broken,true);
                fail("Invalid child");
            }catch(IllegalArgumentException expected){}
            assertEquals(0,count(target,"lists"));
            assertEquals(0,count(target,"list_entries"));
        }
    }
    @Test public void existingLocalListIsNeverOverwritten() throws Exception {
        try(SQLiteDatabase target=db()) {
            target.execSQL("INSERT INTO lists(title,created_at,updated_at)"+
                    " VALUES('Compra · 22 oct',1,1)");
            NexoConnectPilotListImporter.Result imported=
                    NexoConnectPilotListImporter.copyApproved(
                            target,ACCOUNT,DELIVERY,SENDER,list(),true);
            assertTrue(imported.listId>1);
            assertEquals(2,count(target,"lists"));
            try(Cursor c=target.rawQuery("SELECT title,updated_at FROM lists WHERE id=1",null)){
                assertTrue(c.moveToFirst());
                assertEquals("Compra · 22 oct",c.getString(0));
                assertEquals(1,c.getInt(1));
            }
        }
    }
    @Test public void deletedCopyIsNotSilentlyRecreated() throws Exception {
        try(SQLiteDatabase target=db()) {
            NexoConnectPilotListImporter.Result first=
                    NexoConnectPilotListImporter.copyApproved(
                            target,ACCOUNT,DELIVERY,SENDER,list(),true);
            target.execSQL("DELETE FROM lists WHERE id=?",
                    new Object[]{first.listId});
            try {
                NexoConnectPilotListImporter.copyApproved(
                        target,ACCOUNT,DELIVERY,SENDER,list(),true);
                fail("Deleted copy must not be silently recreated");
            }catch(SecurityException expected){}
            assertEquals(0,count(target,"lists"));
        }
    }
}
