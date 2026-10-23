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
        if(body.length()!=5||body.getInt("version")!=1||!("message".equals(body.getString("kind"))||"profile".equals(body.getString("kind"))||"read".equals(body.getString("kind"))))throw new SecurityException("Invalid chat");
        validateName(body.getString("name"));String text=body.getString("text");
        if(text.length()>4000||("message".equals(body.getString("kind"))&&text.trim().isEmpty())||body.getLong("sentAt")<=0)throw new SecurityException("Invalid message");if("read".equals(body.getString("kind")))receiptIds(body);return body;
    }
    /** Read receipts travel in the same authenticated, encrypted envelope as messages. */
    public static JSONObject receipt(String name,java.util.List<String> deliveries)throws Exception {
        if(deliveries.isEmpty()||deliveries.size()>100)throw new SecurityException("Receipt limit");
        JSONObject data=payload(name,new JSONArray(deliveries).toString(),false);
        JSONObject body=new JSONObject(data.getString("body")).put("kind","read");
        receiptIds(body);data.put("body",body.toString());return data;
    }
    public static java.util.List<String> receiptIds(JSONObject body)throws Exception {
        JSONArray ids=new JSONArray(body.getString("text"));
        if(ids.length()==0||ids.length()>100)throw new SecurityException("Receipt limit");
        java.util.List<String> result=new ArrayList<>();Set<String> unique=new HashSet<>();
        for(int i=0;i<ids.length();i++){String id=ids.getString(i);if(!UUID.fromString(id).toString().equals(id)||!unique.add(id))throw new SecurityException("Invalid receipt");result.add(id);}
        return result;
    }
    /** Called only after the envelope's signature and recipient binding have been verified. */
    public static void applyRead(SQLiteDatabase db,UUID account,UUID sender,JSONObject body)throws Exception {
        if(!"read".equals(body.getString("kind")))throw new SecurityException("Not a receipt");
        schema(db);NexoConnectChatBackup.schema(db);db.beginTransaction();
        try{for(String id:receiptIds(body)){
            db.execSQL("UPDATE nexo_chat_outgoing SET status='read' WHERE delivery=? AND account=? AND peer=?",new Object[]{id,account.toString(),sender.toString()});
            db.execSQL("UPDATE nexo_chat_restored SET status='read' WHERE delivery=? AND account=? AND peer=? AND outgoing=1",new Object[]{id,account.toString(),sender.toString()});
        }db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public static boolean queueRead(SQLiteDatabase db,UUID account,UUID peer,String delivery){
        schema(db);android.content.ContentValues row=new android.content.ContentValues();row.put("account",account.toString());row.put("peer",peer.toString());row.put("delivery",UUID.fromString(delivery).toString());
        return db.insertWithOnConflict("nexo_chat_reads",null,row,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    private static android.content.SharedPreferences profiles(Context c){
        return c.getSharedPreferences("nexo-connect-profiles",0);
    }
    public static String ownName(Context c,UUID account){return profiles(c).getString("own:"+account,"");}
    public static void setOwnName(Context c,UUID account,String name){
        String safe=validateName(name);
        android.content.SharedPreferences p=profiles(c);
        if(!p.edit().putString("own:"+account,safe).putLong("revision:"+account,p.getLong("revision:"+account,0)+1).commit())
            throw new IllegalStateException("Could not save name");
    }
    public static String ownAvatar(Context c,UUID account){return profiles(c).getString("avatar:own:"+account,"");}
    public static void setOwnAvatar(Context c,UUID account,String avatar)throws java.security.GeneralSecurityException{
        String safe=NexoConnectProfileAvatar.validate(avatar);
        android.content.SharedPreferences p=profiles(c);
        if(!p.edit().putString("avatar:own:"+account,safe).putLong("revision:"+account,p.getLong("revision:"+account,0)+1).commit())
            throw new IllegalStateException("Could not save avatar");
    }
    public static String peerAvatar(Context c,UUID account,UUID peer){
        return profiles(c).getString("avatar:"+account+":"+peer,"");
    }
    public static String peerName(Context c,UUID account,UUID peer){
        String alias=c.getSharedPreferences("nexo-connect-local-names",0).getString(account+"_"+peer,"").trim();
        if(!alias.isEmpty())return alias;
        String name=profiles(c).getString(account+":"+peer,"");
        return name.isEmpty()?"Contacto "+peer.toString().substring(0,8):name;
    }
    public static void setLocalAlias(Context c,UUID account,UUID peer,String alias){
        String name=alias==null?"":alias.trim();
        if(!name.isEmpty())validateName(name);
        if(!c.getSharedPreferences("nexo-connect-local-names",0).edit()
                .putString(account+"_"+peer,name).commit())throw new IllegalStateException("Could not save alias");
    }
    public static long profileRevision(Context c,UUID owner){
        return Math.max(1,profiles(c).getLong("revision:"+owner,0));
    }
    public static boolean profileSent(Context c,UUID owner,UUID peer){
        return profiles(c).getLong("sent:"+owner+":"+peer,0)>=profileRevision(c,owner);
    }
    public static void markProfileSent(Context c,UUID owner,UUID peer,long revision){
        profiles(c).edit().putLong("sent:"+owner+":"+peer,revision).apply();
    }
    public static JSONObject profilePayload(Context c,UUID account)throws Exception {
        return payload(ownName(c,account),"avatar:"+ownAvatar(c,account),true);
    }
    /** A profile is accepted only inside an authenticated signed/decrypted chat envelope. */
    public static void rememberProfile(Context c,UUID owner,UUID peer,JSONObject body){
        String key=owner+":"+peer;
        android.content.SharedPreferences p=profiles(c);
        long at=body.optLong("sentAt",0);
        if(at<=0||at>System.currentTimeMillis()+300000||at<p.getLong("time:"+key,0))return;
        String name=validateName(body.optString("name"));
        android.content.SharedPreferences.Editor e=p.edit().putString(key,name).putLong("time:"+key,at);
        if("profile".equals(body.optString("kind"))){
            String text=body.optString("text","");
            if(text.startsWith("avatar:")){
                try{e.putString("avatar:"+key,NexoConnectProfileAvatar.validate(text.substring(7)));}
                catch(java.security.GeneralSecurityException invalid){return;}
            }
        }
        e.commit();
    }
    public static void schema(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_reads(account TEXT NOT NULL,peer TEXT NOT NULL,delivery TEXT NOT NULL,confirmed INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account,peer,delivery))");db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_outgoing(delivery TEXT PRIMARY KEY,account TEXT NOT NULL,peer TEXT NOT NULL,body TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL)");}
    public static void pending(SQLiteDatabase db,UUID account,UUID peer,UUID delivery,JSONObject data)throws Exception {
        schema(db);String sealed=new NexoConnectSealedNoteStore(account,delivery).sealForChat(data);
        db.execSQL("INSERT INTO nexo_chat_outgoing VALUES(?,?,?,?,?,?)",new Object[]{delivery.toString(),account.toString(),peer.toString(),sealed,"sending",System.currentTimeMillis()});
    }
    public static void status(SQLiteDatabase db,UUID delivery,String status){db.execSQL("UPDATE nexo_chat_outgoing SET status=CASE WHEN status='read' THEN status ELSE ? END WHERE delivery=?",new Object[]{status,delivery.toString()});}
    public static final class Message {
        public final String delivery,text,status;public final boolean outgoing;public final long time;
        Message(String delivery,String text,String status,boolean out,long time){this.delivery=delivery;this.text=text;this.status=status;outgoing=out;this.time=time;}
    }
    public static List<Message> messages(SQLiteDatabase db,UUID account,UUID peer)throws Exception {
        schema(db);NexoConnectChatBackup.schema(db);List<Message> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        try(Cursor c=db.rawQuery("SELECT delivery,body,status,created_at FROM nexo_chat_outgoing WHERE account=? AND peer=? ORDER BY created_at DESC LIMIT 200",new String[]{account.toString(),peer.toString()})){
            while(c.moveToNext()){
                seen.add(c.getString(0));
                JSONObject body=parse(new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1)));
                if("message".equals(body.getString("kind")))result.add(new Message(c.getString(0),body.getString("text"),c.getString(2),true,c.getLong(3)));
            }
        }
        try(Cursor c=db.rawQuery("SELECT r.delivery_id,i.body,r.imported_at FROM nexo_connect_import_receipts r JOIN items i ON i.id=r.local_id WHERE r.sender_id=? AND r.entity_type='note' ORDER BY r.imported_at DESC LIMIT 200",new String[]{peer.toString()})){
            while(c.moveToNext()){
                seen.add(c.getString(0));
                JSONObject data=new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1));
                if(!CATEGORY.equals(data.optString("category")))continue;JSONObject body=parse(data);
                if("message".equals(body.getString("kind")))result.add(new Message(c.getString(0),body.getString("text"),"received",false,c.getLong(2)));
            }
        }
        try(Cursor c=db.rawQuery("SELECT delivery,body,status,created_at,outgoing FROM nexo_chat_restored WHERE account=? AND peer=?",new String[]{account.toString(),peer.toString()})){
            while(c.moveToNext()){if(!seen.add(c.getString(0)))continue;JSONObject body=parse(new NexoConnectSealedNoteStore(account,UUID.fromString(c.getString(0))).read(c.getString(1)));result.add(new Message(c.getString(0),body.getString("text"),c.getString(2),c.getInt(4)==1,c.getLong(3)));}
        }
        result.sort(java.util.Comparator.comparingLong(m->m.time));return result;
    }
}
