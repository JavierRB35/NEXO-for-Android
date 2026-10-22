package com.jruiz.nexo.connect.vault;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class NexoConnectChatBackupTest {
    private JSONObject fixture()throws Exception{return new JSONObject().put("version",1).put("owner",UUID.randomUUID().toString()).put("name","Javi").put("names",new JSONObject()).put("messages",new JSONArray().put(new JSONObject().put("id",UUID.randomUUID().toString()).put("peer",UUID.randomUUID().toString()).put("data",NexoConnectChatStore.payload("Javi","Prueba de integración",false)).put("status","sent").put("at",123456).put("outgoing",true)));}
    @Test public void passwordRoundTripKeepsNamesAndMessages()throws Exception{JSONObject original=fixture();byte[] encrypted=NexoConnectChatBackup.encrypt(original,"prueba-segura".toCharArray());assertFalse(new String(encrypted,java.nio.charset.StandardCharsets.UTF_8).contains("Prueba de integración"));JSONObject restored=NexoConnectChatBackup.decrypt(encrypted,"prueba-segura".toCharArray());assertEquals(original.toString(),restored.toString());}
    @Test public void wrongPasswordRejected()throws Exception{byte[] encrypted=NexoConnectChatBackup.encrypt(fixture(),"prueba-segura".toCharArray());try{NexoConnectChatBackup.decrypt(encrypted,"otra-clave".toCharArray());fail();}catch(java.io.IOException expected){}}
    @Test public void tamperingRejected()throws Exception{byte[] encrypted=NexoConnectChatBackup.encrypt(fixture(),"prueba-segura".toCharArray());encrypted[encrypted.length-1]^=1;try{NexoConnectChatBackup.decrypt(encrypted,"prueba-segura".toCharArray());fail();}catch(java.io.IOException expected){}}
    @Test public void emptyNameAndMessageRejected()throws Exception{try{NexoConnectChatStore.payload("","Hola",false);fail();}catch(IllegalArgumentException expected){}try{NexoConnectChatStore.payload("Javi"," ",false);fail();}catch(IllegalArgumentException expected){}}
    @Test public void duplicateMessagesRejected()throws Exception{JSONObject data=fixture();JSONArray rows=data.getJSONArray("messages");rows.put(rows.getJSONObject(0));try{NexoConnectChatBackup.validate(data);fail();}catch(SecurityException expected){}}
    private android.content.Context isolated(String prefix){
        android.content.Context base=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new android.content.ContextWrapper(base){
            @Override public android.content.Context getApplicationContext(){return this;}
            @Override public java.io.File getNoBackupFilesDir(){java.io.File dir=new java.io.File(base.getCacheDir(),prefix);dir.mkdirs();return dir;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(prefix+name,mode);}
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name,int mode,android.database.sqlite.SQLiteDatabase.CursorFactory factory){return base.openOrCreateDatabase(prefix+name,mode,factory);}
        };
    }
    @Test public void portableRestoreWorksWithoutSessionAndIsIdempotent()throws Exception{
        String prefix="backup-test-"+UUID.randomUUID();android.content.Context c=isolated(prefix);JSONObject data=fixture();
        UUID owner=UUID.fromString(data.getString("owner")),peer=UUID.fromString(data.getJSONArray("messages").getJSONObject(0).getString("peer"));
        try{
            byte[] encrypted=NexoConnectChatBackup.encrypt(data,"copia-segura".toCharArray());
            NexoConnectChatBackup.restore(c,NexoConnectChatBackup.decrypt(encrypted,"copia-segura".toCharArray()));
            NexoConnectChatBackup.restore(c,data);
            assertFalse(new NexoConnectSessionVault(c).exists());assertEquals("Javi",NexoConnectChatStore.ownName(c,owner));
            try(android.database.sqlite.SQLiteDatabase db=NexoConnectMigration.inbox(c)){
                var messages=NexoConnectChatStore.messages(db,owner,peer);assertEquals(1,messages.size());assertEquals("Prueba de integración",messages.get(0).text);
            }
            assertEquals(1,NexoConnectChatBackup.snapshot(c).getJSONArray("messages").length());
        }finally{c.getSharedPreferences("nexo-connect-profiles",0).edit().clear().commit();androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(prefix+"nexo-connect-pilot.db");}
    }
}
