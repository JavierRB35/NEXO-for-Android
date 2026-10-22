package com.jruiz.nexo.connect.vault;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.jruiz.nexo.connect.NexoConnectPilotEntityImporter;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectPilotEntityImporterTest {
    private final UUID account=UUID.randomUUID(), sender=UUID.randomUUID(), delivery=UUID.randomUUID();
    private JSONObject data(String type)throws Exception {
        JSONObject d=new JSONObject().put("title","Prueba de integración");
        switch(type) {
            case "note":d.put("body","Texto de prueba");break;
            case "list":d.put("entries",new JSONArray().put(new JSONObject().put("text","A")));break;
            case "journal":d.put("body","Reflexión").put("kind","thought");break;
            case "book":d.put("author","Autora");break;
            case "appointment":d.put("type","event").put("dueAt",1810000000000L);break;
        }
        return d;
    }
    @Test public void sixTypesCopyOnceAndKeepOriginalId()throws Exception {
        for(String type:new String[]{"note","list","appointment","journal","book","movie"}) {
            try(SQLiteDatabase db=NexoConnectNativeWriterTest.database()) {
                var a=NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,type,data(type),true);
                var b=NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,type,data(type),true);
                assertTrue(a.nativeId>0);assertFalse(a.alreadyCopied);
                assertEquals(a.nativeId,b.nativeId);assertTrue(b.alreadyCopied);
            }
        }
    }
    @Test public void rejectsChangedContentAndIdentity()throws Exception {
        try(SQLiteDatabase db=NexoConnectNativeWriterTest.database()) {
            NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"note",data("note"),true);
            try { NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"note",
                    data("note").put("body","Alterado"),true);fail(); } catch(SecurityException expected){}
            try { NexoConnectPilotEntityImporter.copyApproved(db,UUID.randomUUID(),delivery,sender,"note",
                    data("note"),true);fail(); } catch(SecurityException expected){}
            try(Cursor c=db.rawQuery("SELECT count(*) FROM items",null)){c.moveToFirst();assertEquals(1,c.getInt(0));}
        }
    }
    @Test public void rejectsNoConsentAndDoesNotRecreateDeletedCopy()throws Exception {
        try(SQLiteDatabase db=NexoConnectNativeWriterTest.database()) {
            try {NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"note",data("note"),false);
                fail();}catch(SecurityException expected){}
            NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"note",data("note"),true);
            db.delete("items",null,null);
            try {NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"note",data("note"),true);
                fail();}catch(SecurityException expected){}
        }
    }
    @Test public void failedNativeWriteRollsBackReceiptAndAllowsCorrectRetry()throws Exception {
        try(SQLiteDatabase db=NexoConnectNativeWriterTest.database()) {
            db.execSQL("CREATE TRIGGER reject_book BEFORE INSERT ON books BEGIN SELECT RAISE(ABORT,'test'); END");
            try {NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"book",data("book"),true);
                fail();}catch(android.database.SQLException expected){}
            db.execSQL("DROP TRIGGER reject_book");
            assertFalse(NexoConnectPilotEntityImporter.copyApproved(db,account,delivery,sender,"book",data("book"),true).alreadyCopied);
        }
    }
}
