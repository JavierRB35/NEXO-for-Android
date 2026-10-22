package com.jruiz.nexo.connect.vault;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Messages use the existing authenticated note envelope; the category and
 * versioned body distinguish them before import. All message text is sealed at rest. */
public final class NexoConnectChatStore {
    public static final String CATEGORY="nexo.chat.v1";
    public static String validateName(String name){
        if(name==null)throw new IllegalArgumentException("Missing name");name=name.trim();
        if(name.isEmpty()||name.length()>50)throw new IllegalArgumentException("Name length");
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new IllegalArgumentException("Invalid name");
        return name;
    }
    public static JSONObject payload(String name,String message,boolean profile)throws Exception {
        name=validateName(name);
        if(message==null||message.trim().isEmpty()&&!profile||message.length()>4000)throw new IllegalArgumentException("Message length");
        return new JSONObject().put("title",profile?"Perfil":"Mensaje").put("category",CATEGORY)
            .put("body",new JSONObject().put("version",1).put("kind",profile?"profile":"message")
                .put("name",name).put("text",message).put("sentAt",System.currentTimeMillis()).toString());
    }
    public static JSONObject parse(JSONObject payload)throws Exception {
        if(!CATEGORY.equals(payload.optString("category")))throw new SecurityException("Not a chat");
        JSONObject body=new JSONObject(payload.getString("body"));
        if(body.length()!=5||body.getInt("version")!=1||!("message".equals(body.getString("kind"))||"profile".equals(body.getString("kind"))))throw new SecurityException("Invalid chat");
        validateName(body.getString("name"));String text=body.getString("text");
        if(text.length()>4000||("message".equals(body.getString("kind"))&&text.trim().isEmpty())||body.getLong("sentAt")<=0)throw new SecurityException("Invalid message");return body;
    }
    public static String ownName(Context c,UUID account){return c.getSharedPreferences("nexo-connect-profiles",0).getString("own:"+account,"");}
    public static void setOwnName(Context c,UUID account,String name){if(!c.getSharedPreferences("nexo-connect-profiles",0).edit().putString("own:"+account,validateName(name)).commit())throw new IllegalStateException();}
    public static String peerName(Context c,UUID account,UUID peer){
        String name=c.getSharedPreferences("nexo-connect-profiles",0).getString(account+":"+peer,"");
        if(name.isEmpty())name=c.getSharedPreferences("nexo-connect-local-names",0).getString(account+"_"+peer,"");
        return name.isEmpty()?"Persona sin nombre":name;
    }
    public static void rememberProfile(Context c,UUID owner,UUID peer,JSONObject body){
        String key=owner+":"+peer;android.content.SharedPreferences prefs=c.getSharedPreferences("nexo-connect-profiles",0);
        long at=body.optLong("sentAt",0);if(at<=0||at>System.currentTimeMillis()+300000)return;
        if(at>=prefs.getLong("time:"+key,0))prefs.edit().putString(key,validateName(body.optString("name"))).putLong("time:"+key,at).commit();
    }
    public static void schema(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_outgoing(delivery TEXT PRIMARY KEY,account TEXT NOT NULL,peer TEXT NOT NULL,body TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL)");}
    public static void pending(SQLiteDatabase db,UUID account,UUID peer,UUID delivery,JSONObject data)throws Exception {
        schema(db);String sealed=new NexoConnectSealedNoteStore(account,delivery).sealForChat(data);
        db.execSQL("INSERT INTO nexo_chat_outgoing VALUES(?,?,?,?,?,?)",new Object[]{delivery.toString(),account.toString(),peer.toString(),sealed,"sending",System.currentTimeMillis()});
    }
    public static void status(SQLiteDatabase db,UUID delivery,String status){db.execSQL("UPDATE nexo_chat_outgoing SET status=? WHERE delivery=?",new Object[]{status,delivery.toString()});}
    public static final class Message {
        public final String text,status;public final boolean outgoing;public final long time;
        Message(String text,String status,boolean out,long time){this.text=text;this.status=status;outgoing=out;this.time=time;}
    }
    public static List<Message> messages(SQLiteDatabase db,UUID account,UUID peer)throws Exception {
        schema(db);NexoConnectChatBackup.schema(db);List<Message> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        try(Cursor c=db.rawQuery("SELECT delivery,body,status,created_at FROM nexo_chat_outgoing WHERE account=? AND peer=? ORDER BY created_at DESC LIMIT 200",new String[]{account.toString(),peer.toString()})){
            while(c.moveToNext()){
                seen.add(c.getString(0));
                JSONObject body=parse(new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1)));
                if("message".equals(body.getString("kind")))result.add(new Message(body.getString("text"),c.getString(2),true,c.getLong(3)));
            }
        }
        try(Cursor c=db.rawQuery("SELECT r.delivery_id,i.body,r.imported_at FROM nexo_connect_import_receipts r JOIN items i ON i.id=r.local_id WHERE r.sender_id=? AND r.entity_type='note' ORDER BY r.imported_at DESC LIMIT 200",new String[]{peer.toString()})){
            while(c.moveToNext()){
                seen.add(c.getString(0));
                JSONObject data=new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1));
                if(!CATEGORY.equals(data.optString("category")))continue;JSONObject body=parse(data);
                if("message".equals(body.getString("kind")))result.add(new Message(body.getString("text"),"received",false,c.getLong(2)));
            }
        }
        try(Cursor c=db.rawQuery("SELECT delivery,body,status,created_at,outgoing FROM nexo_chat_restored WHERE account=? AND peer=?",new String[]{account.toString(),peer.toString()})){
            while(c.moveToNext()){if(!seen.add(c.getString(0)))continue;JSONObject body=parse(new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1)));result.add(new Message(body.getString("text"),c.getString(2),c.getInt(4)==1,c.getLong(3)));}
        }
        result.sort(java.util.Comparator.comparingLong(m->m.time));return result;
    }
}
