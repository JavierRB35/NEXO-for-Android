package com.jruiz.nexo.connect.vault;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import android.database.sqlite.SQLiteDatabase;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class NexoConnectChatReceiptsTest {
    private String status(SQLiteDatabase db,String id){try(var c=db.rawQuery("SELECT status FROM nexo_chat_outgoing WHERE delivery=?",new String[]{id})){assertTrue(c.moveToFirst());return c.getString(0);}}
    @Test public void readIsBoundToTheAuthenticatedPeerAndAccountAndNeverRegresses()throws Exception{
        UUID owner=UUID.randomUUID(),peer=UUID.randomUUID(),id=UUID.randomUUID();
        JSONObject body=NexoConnectChatStore.parse(NexoConnectChatStore.receipt("Ana",List.of(id.toString())));
        try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            NexoConnectChatStore.schema(db);
            db.execSQL("INSERT INTO nexo_chat_outgoing VALUES(?,?,?,?,?,?)",new Object[]{id.toString(),owner.toString(),peer.toString(),"sealed","sent",123});
            NexoConnectChatStore.applyRead(db,owner,UUID.randomUUID(),body);assertEquals("sent",status(db,id.toString()));
            NexoConnectChatStore.applyRead(db,UUID.randomUUID(),peer,body);assertEquals("sent",status(db,id.toString()));
            NexoConnectChatStore.applyRead(db,owner,peer,body);assertEquals("read",status(db,id.toString()));
            NexoConnectChatStore.applyRead(db,owner,peer,body);NexoConnectChatStore.status(db,id,"sent");assertEquals("read",status(db,id.toString()));
        }
    }
    @Test public void visibleReadQueueIsDurableAndIdempotent()throws Exception{
        UUID owner=UUID.randomUUID(),peer=UUID.randomUUID();String id=UUID.randomUUID().toString();
        try(SQLiteDatabase db=SQLiteDatabase.create(null)){assertTrue(NexoConnectChatStore.queueRead(db,owner,peer,id));assertFalse(NexoConnectChatStore.queueRead(db,owner,peer,id));try(var c=db.rawQuery("SELECT confirmed FROM nexo_chat_reads",null)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));assertFalse(c.moveToNext());}}
    }
    @Test public void malformedAndDuplicateReceiptIdsAreRejected()throws Exception{
        String id=UUID.randomUUID().toString();
        for(List<String> ids:List.of(List.<String>of(),List.of("not-an-id"),List.of(id,id))){try{NexoConnectChatStore.receipt("Ana",ids);fail();}catch(IllegalArgumentException|SecurityException expected){}}
    }
    @Test public void backupKeepsReadStatusWithoutAddingReceiptEventsToConversation()throws Exception{
        JSONObject row=new JSONObject().put("id",UUID.randomUUID().toString()).put("peer",UUID.randomUUID().toString()).put("data",NexoConnectChatStore.payload("Javi","Hola",false)).put("status","read").put("at",123).put("outgoing",true);
        JSONObject archive=new JSONObject().put("version",1).put("owner",UUID.randomUUID().toString()).put("name","Javi").put("names",new JSONObject()).put("messages",new JSONArray().put(row));
        byte[] encrypted=NexoConnectChatBackup.encrypt(archive,"copia-segura".toCharArray());assertEquals("read",NexoConnectChatBackup.decrypt(encrypted,"copia-segura".toCharArray()).getJSONArray("messages").getJSONObject(0).getString("status"));
        row.put("data",NexoConnectChatStore.receipt("Ana",List.of(UUID.randomUUID().toString())));try{NexoConnectChatBackup.validate(archive);fail();}catch(SecurityException expected){}
    }
}
