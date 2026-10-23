package com.jruiz.nexo.connect.vault;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Portable history only. Never exports session credentials or identity keys. */
public final class NexoConnectChatBackup {
    private static final int MAX=32*1024*1024;
    public static void schema(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS nexo_chat_restored(delivery TEXT PRIMARY KEY,account TEXT NOT NULL,peer TEXT NOT NULL,body TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL,outgoing INTEGER NOT NULL)");}
    public static UUID owner(Context c)throws Exception{
        NexoConnectSessionVault session=new NexoConnectSessionVault(c);
        if(session.exists())return session.restore().accountId;
        String saved=c.getSharedPreferences("nexo-connect-profiles",0).getString("archiveOwner","");
        return saved.isEmpty()?null:UUID.fromString(saved);
    }
    public static boolean hasHistory(Context c)throws Exception{return owner(c)!=null;}
    public static JSONObject snapshot(Context c)throws Exception{
        UUID owner=owner(c);if(owner==null)return null;
        JSONArray rows=new JSONArray();Set<String> seen=new HashSet<>();Map<String,NexoConnectChatDeletion.Visibility> visibility=new HashMap<>();
        try(SQLiteDatabase db=NexoConnectMigration.inbox(c)){
            NexoConnectChatStore.schema(db);schema(db);db.beginTransaction();
            try{
                try(Cursor cur=db.rawQuery("SELECT delivery,peer,body,status,created_at,1 FROM nexo_chat_outgoing WHERE account=? UNION ALL SELECT delivery,peer,body,status,created_at,outgoing FROM nexo_chat_restored WHERE account=?",new String[]{owner.toString(),owner.toString()})){
                    while(cur.moveToNext())append(db,visibility,rows,seen,owner,cur.getString(0),cur.getString(1),cur.getString(2),cur.getString(3),cur.getLong(4),cur.getInt(5)==1);
                }
                try(Cursor cur=db.rawQuery("SELECT r.delivery_id,r.sender_id,i.body,r.imported_at FROM nexo_connect_import_receipts r JOIN items i ON i.id=r.local_id WHERE r.entity_type='note'",null)){
                    while(cur.moveToNext())append(db,visibility,rows,seen,owner,cur.getString(0),cur.getString(1),cur.getString(2),"received",cur.getLong(3),false);
                }
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        JSONObject names=new JSONObject();for(var e:c.getSharedPreferences("nexo-connect-profiles",0).getAll().entrySet())if(e.getKey().startsWith(owner+":"))names.put(e.getKey().substring(37),String.valueOf(e.getValue()));
        return new JSONObject().put("version",1).put("owner",owner.toString()).put("name",NexoConnectChatStore.ownName(c,owner)).put("names",names).put("messages",rows);
    }
    private static void append(SQLiteDatabase db,Map<String,NexoConnectChatDeletion.Visibility> visibility,JSONArray rows,Set<String> seen,UUID owner,String id,String peer,String sealed,String status,long at,boolean outgoing)throws Exception{
        if(!seen.add(id))return;
        JSONObject data=new NexoConnectSealedNoteStore(owner,UUID.fromString(id)).read(sealed);
        if(!NexoConnectChatStore.CATEGORY.equals(data.optString("category")))return;
        JSONObject body=NexoConnectChatStore.parse(data);if(!"message".equals(body.getString("kind")))return;
        NexoConnectChatDeletion.Visibility view=visibility.get(peer);
        if(view==null){view=NexoConnectChatDeletion.visibility(db,owner,UUID.fromString(peer));visibility.put(peer,view);}
        if(view.hidden(id,body.getLong("sentAt")))return;
        rows.put(new JSONObject().put("id",id).put("peer",peer).put("data",data).put("status",status).put("at",at).put("outgoing",outgoing));
    }
    public static byte[] encrypt(JSONObject data,char[] password)throws Exception{
        if(password==null||password.length<8)throw new java.io.IOException("La contraseña de la copia debe tener al menos 8 caracteres.");
        byte[] plain=data.toString().getBytes(StandardCharsets.UTF_8);if(plain.length>MAX)throw new java.io.IOException("El historial supera el tamaño permitido.");
        byte[] salt=new byte[16],iv=new byte[12];new SecureRandom().nextBytes(salt);new SecureRandom().nextBytes(iv);
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(new byte[]{1});
            byte[] encrypted=cipher.doFinal(plain);java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate(29+encrypted.length);out.put((byte)1).put(salt).put(iv).put(encrypted);return out.array();
        }finally{Arrays.fill(plain,(byte)0);}
    }
    private static SecretKeySpec key(char[] password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);byte[] bytes=null;
        try{bytes=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return new SecretKeySpec(bytes,"AES");}
        finally{spec.clearPassword();if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    public static JSONObject decrypt(byte[] encrypted,char[] password)throws Exception{
        if(password==null||encrypted.length<45||encrypted.length>MAX+45||encrypted[0]!=1)throw new java.io.IOException("No se puede abrir el historial de esta copia.");
        byte[] plain=null;
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,Arrays.copyOfRange(encrypted,1,17)),new GCMParameterSpec(128,Arrays.copyOfRange(encrypted,17,29)));cipher.updateAAD(new byte[]{1});plain=cipher.doFinal(encrypted,29,encrypted.length-29);JSONObject data=new JSONObject(new String(plain,StandardCharsets.UTF_8));validate(data);return data;
        }catch(javax.crypto.AEADBadTagException wrong){throw new java.io.IOException("Contraseña incorrecta o historial dañado.");}finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
    public static void validate(JSONObject data)throws Exception{
        if(data.getInt("version")!=1)throw new SecurityException();UUID.fromString(data.getString("owner"));
        if(!data.getString("name").isEmpty())NexoConnectChatStore.validateName(data.getString("name"));
        JSONObject names=data.getJSONObject("names");for(Iterator<String> it=names.keys();it.hasNext();){String id=it.next();UUID.fromString(id);NexoConnectChatStore.validateName(names.getString(id));}
        JSONArray rows=data.getJSONArray("messages");Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);UUID.fromString(row.getString("id"));UUID.fromString(row.getString("peer"));if(!ids.add(row.getString("id"))||row.getLong("at")<=0)throw new SecurityException();row.getBoolean("outgoing");if(!Arrays.asList("sending","unknown","sent","received","read").contains(row.getString("status")))throw new SecurityException();if(!"message".equals(NexoConnectChatStore.parse(row.getJSONObject("data")).getString("kind")))throw new SecurityException("Not a history message");}
    }
    public static void restore(Context c,JSONObject data)throws Exception{
        validate(data);UUID account=UUID.fromString(data.getString("owner"));UUID current=owner(c);if(current!=null&&!current.equals(account))throw new java.io.IOException("Esta copia de chat pertenece a otra cuenta.");
        try(SQLiteDatabase db=NexoConnectMigration.inbox(c)){
            schema(db);NexoConnectChatDeletion.schema(db);db.beginTransaction();try{JSONArray rows=data.getJSONArray("messages");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);UUID id=UUID.fromString(row.getString("id"));UUID peer=UUID.fromString(row.getString("peer"));JSONObject chat=NexoConnectChatStore.parse(row.getJSONObject("data"));if(NexoConnectChatDeletion.visibility(db,account,peer).hidden(id.toString(),chat.getLong("sentAt")))continue;String sealed=new NexoConnectSealedNoteStore(account,id).sealForChat(row.getJSONObject("data"));db.execSQL("INSERT OR IGNORE INTO nexo_chat_restored VALUES(?,?,?,?,?,?,?)",new Object[]{id.toString(),account.toString(),peer.toString(),sealed,row.getString("status"),row.getLong("at"),row.getBoolean("outgoing")?1:0});}db.setTransactionSuccessful();}finally{db.endTransaction();}
        }
        var edit=c.getSharedPreferences("nexo-connect-profiles",0).edit().putString("archiveOwner",account.toString()).putString("own:"+account,data.getString("name"));JSONObject names=data.getJSONObject("names");for(Iterator<String> it=names.keys();it.hasNext();){String id=it.next();edit.putString(account+":"+id,names.getString(id));}edit.commit();
    }
}
