package com.jruiz.nexo.connect.vault;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectNativeWriter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Test database tables mirror native DatabaseHelper schema v32.
 * The importer is NOT yet connected to production screens.
 */
@RunWith(AndroidJUnit4.class)
public class NexoConnectNativeWriterTest {
    private static final String SENDER="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String D1="11111111-1111-4111-8111-111111111111";
    private static final String D2="22222222-2222-4222-8222-222222222222";

    private static SQLiteDatabase database() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "type TEXT NOT NULL DEFAULT 'note', title TEXT NOT NULL,"+
                "body TEXT NOT NULL DEFAULT '', original_text TEXT NOT NULL DEFAULT '',"+
                "category TEXT NOT NULL DEFAULT 'Otros',subcategory TEXT NOT NULL DEFAULT '',"+
                "tags TEXT NOT NULL DEFAULT '',due_at INTEGER NOT NULL DEFAULT 0,"+
                "end_at INTEGER NOT NULL DEFAULT 0,has_time INTEGER NOT NULL DEFAULT 0,"+
                "all_day INTEGER NOT NULL DEFAULT 0,location_text TEXT NOT NULL DEFAULT '',"+
                "maps_url TEXT NOT NULL DEFAULT '',priority INTEGER NOT NULL DEFAULT 1,"+
                "favorite INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'manual',"+
                "note_title_manual INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'active',"+
                "status_manual INTEGER NOT NULL DEFAULT 0,needs_review INTEGER NOT NULL DEFAULT 0,"+
                "recurrence TEXT NOT NULL DEFAULT 'none',recurrence_until INTEGER NOT NULL DEFAULT 0,"+
                "duration_minutes INTEGER NOT NULL DEFAULT 0,voice_enabled INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE lists(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,"+
                "kind TEXT NOT NULL DEFAULT 'General',reusable INTEGER NOT NULL DEFAULT 0,"+
                "favorite INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE list_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,list_id INTEGER NOT NULL,"+
                "text TEXT NOT NULL,checked INTEGER NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL DEFAULT 0,"+
                "category TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "kind TEXT NOT NULL DEFAULT 'thought',title TEXT NOT NULL,summary TEXT NOT NULL DEFAULT '',"+
                "body TEXT NOT NULL,tags TEXT NOT NULL DEFAULT '',favorite INTEGER NOT NULL DEFAULT 0,"+
                "source TEXT NOT NULL DEFAULT 'manual',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,"+
                "author TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'want',progress INTEGER NOT NULL DEFAULT 0,"+
                "finish_date TEXT NOT NULL DEFAULT '',abandoned_date TEXT NOT NULL DEFAULT '',"+
                "summary TEXT NOT NULL DEFAULT '',start_date TEXT NOT NULL DEFAULT '',"+
                "favorite INTEGER NOT NULL DEFAULT 0,rating INTEGER NOT NULL DEFAULT 0,"+
                "category TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE media_items(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,"+
                "creator TEXT NOT NULL DEFAULT '',media_type TEXT NOT NULL DEFAULT 'movie',"+
                "status TEXT NOT NULL DEFAULT 'want',progress INTEGER NOT NULL DEFAULT 0,"+
                "finish_date TEXT NOT NULL DEFAULT '',abandoned_date TEXT NOT NULL DEFAULT '',"+
                "summary TEXT NOT NULL DEFAULT '',start_date TEXT NOT NULL DEFAULT '',"+
                "favorite INTEGER NOT NULL DEFAULT 0,rating INTEGER NOT NULL DEFAULT 0,"+
                "category TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        return db;
    }
    private static long rows(SQLiteDatabase db,String table) {
        try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){
            c.moveToFirst();return c.getLong(0);
        }
    }
    private static NexoConnectAtomicImporter.Result load(SQLiteDatabase db,String delivery,
                        String kind, JSONObject data) throws Exception {
        byte[] bytes=NexoConnectPayloadCodec.encode(kind,data,null);
        return NexoConnectAtomicImporter.importVerified(
                db,delivery,SENDER,kind,bytes,NexoConnectNativeWriter.INSTANCE);
    }

    @Test public void importsSixNativeTypesWithFreshIds() throws Exception {
        try(SQLiteDatabase db=database()) {
            JSONObject note=new JSONObject().put("title","Recibida").put("body","Hola");
            NexoConnectAtomicImporter.Result a=load(db,D1,"note",note);
            assertEquals(1,rows(db,"items"));
            assertEquals("nexo-connect",readString(db,"SELECT source FROM items WHERE id=?",a.localId));
            JSONObject list=new JSONObject().put("title","Compra").put("kind","General");
            JSONArray entries=new JSONArray().put(new JSONObject().put("text","Pan").put("checked",true))
                                         .put(new JSONObject().put("text","Leche").put("sortOrder",1));
            list.put("entries",entries);
            NexoConnectAtomicImporter.Result b=load(db,D2,"list",list);
            assertEquals(1,rows(db,"lists"));
            assertEquals(2,rows(db,"list_entries"));
            assertTrue(b.localId>0);
            load(db,"33333333-3333-4333-8333-333333333333","journal",
                    new JSONObject().put("title","Reflexión").put("body","Algo importante").put("kind","thought"));
            assertEquals(1,rows(db,"journal_entries"));
            load(db,"44444444-4444-4444-8444-444444444444","book",
                    new JSONObject().put("title","Libro").put("author","Escritora").put("status","finished")
                                    .put("finishDate","2024-02-05").put("progress",100));
            assertEquals(1,rows(db,"books"));
            assertEquals("2024-02-05",readString(db,"SELECT finish_date FROM books WHERE id=?",1));
            load(db,"55555555-5555-4555-8555-555555555555","movie",
                    new JSONObject().put("title","Serie").put("mediaType","series").put("status","want"));
            assertEquals(1,rows(db,"media_items"));
            assertEquals("series",readString(db,"SELECT media_type FROM media_items WHERE id=?",1));
            load(db,"66666666-6666-4666-8666-666666666666","appointment",
                    new JSONObject().put("title","Reunión").put("type","event").put("dueAt",1810000000000L));
            assertEquals(2,rows(db,"items"));
            assertEquals("event",readString(db,"SELECT type FROM items WHERE id=?",2));
            assertEquals(6,NexoConnectAtomicImporter.pendingAcknowledgements(db,20).size());
            // Already delivered packet cannot insert a duplicate even if called again.
            assertTrue(load(db,D1,"note",note).alreadyImported);
            assertEquals(2,rows(db,"items"));
        }
    }

    private static String readString(SQLiteDatabase db,String sql,long id){
        try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());return c.getString(0);
        }
    }
    @Test public void invalidListRollsBackParentAndDeliveryReceipt() throws Exception {
        try(SQLiteDatabase db=database()){
            JSONArray entries=new JSONArray().put(new JSONObject().put("text","Primero"))
                    .put(new JSONObject().put("text","Segundo").put("sortOrder",-1));
            JSONObject bad=new JSONObject().put("title","Lista").put("entries",entries);
            try {load(db,D1,"list",bad);fail("Out-of-range sort order accepted");}
            catch(IllegalArgumentException expected){}
            assertEquals(0,rows(db,"lists"));
            assertEquals(0,rows(db,"list_entries"));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
        }
    }

    @Test public void unsupportedAttachedDocumentAndRemindersNeverAcknowledge() throws Exception {
        try(SQLiteDatabase db=database()){
            JSONObject attached=new JSONObject().put("title","PDF").put("fileName","example.pdf")
                .put("attachmentSize",23).put("attachmentSha256",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            try{load(db,D1,"document",attached);fail("Document without attachment accepted");}
            catch(UnsupportedOperationException expected){}
            JSONObject alarms=new JSONObject().put("title","Con avisos").put("type","event")
                .put("dueAt",1810000000000L).put("reminderLeadMinutes",new JSONArray().put(60));
            try{load(db,D2,"appointment",alarms);fail("Silent reminder loss accepted");}
            catch(UnsupportedOperationException expected){}
            assertEquals(0,rows(db,"items"));
            assertTrue(NexoConnectAtomicImporter.pendingAcknowledgements(db,10).isEmpty());
        }
    }

    @Test public void existingNativeRecordIsNeverOverwritten() throws Exception {
        try(SQLiteDatabase db=database()){
            db.execSQL("INSERT INTO items(title,body,created_at,updated_at) VALUES('Local','No modificar',1,1)");
            NexoConnectAtomicImporter.Result copy=load(db,D1,"note",
                    new JSONObject().put("title","Local").put("body","No modificar"));
            assertEquals(2,rows(db,"items"));
            assertEquals("manual",readString(db,"SELECT source FROM items WHERE id=?",1));
            assertTrue(copy.localId!=1);
        }
    }
}
