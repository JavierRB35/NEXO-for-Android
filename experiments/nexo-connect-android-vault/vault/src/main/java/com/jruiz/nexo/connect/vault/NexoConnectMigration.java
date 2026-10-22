package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.util.Base64;
import android.util.AtomicFile;
import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;
import android.security.keystore.*;

/** One-time, same-signer migration. Only encrypted archives reach disk/Binder.
 * Source stays frozen until destination durably imports and acknowledges.
 * Destination cannot refresh a session before source acknowledges retirement.
 */
public final class NexoConnectMigration {
    public static final String HOST="com.jruiz.nexo", PILOT="com.jruiz.nexo.connect.pilot";
    public static final String PERMISSION="com.jruiz.nexo.permission.NEXO_CONNECT_MIGRATION";
    public static final String DESCRIPTOR="nexo.connect.migration.v1";
    public static final int MAX=500*1024;
    private static final String KEY="nexo_connect_migration_rsa_v1";
    private static final byte[] AAD="NEXO-MIGRATION-1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private NexoConnectMigration(){}
    public static SharedPreferences state(Context c){return c.getSharedPreferences("nexo-connect-migration",0);}
    public static boolean blocked(Context c){return state(c).getBoolean("sourceFrozen",false)||state(c).getBoolean("targetPending",false);}
    public static boolean moved(Context c){return state(c).getBoolean("sourceMoved",false);}
    public static String request(Context c){
        String id=state(c).getString("request",null);
        if(id==null){id=UUID.randomUUID().toString();if(!state(c).edit().putString("request",id).commit())throw new IllegalStateException();}
        return id;
    }
    public static byte[] publicKey(Context c)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(KEY)) {
            KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build());g.generateKeyPair();
        }
        return store.getCertificate(KEY).getPublicKey().getEncoded();
    }
    static byte[] encrypt(byte[] plain,byte[] publicKey)throws Exception {
        PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey));
        if(!(key instanceof java.security.interfaces.RSAPublicKey)||
            ((java.security.interfaces.RSAPublicKey)key).getModulus().bitLength()!=2048)throw new SecurityException("Invalid target key");
        KeyGenerator kg=KeyGenerator.getInstance("AES");kg.init(256);byte[] secret=kg.generateKey().getEncoded();
        try {
            Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE,key,new OAEPParameterSpec("SHA-256","MGF1",java.security.spec.MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
            byte[] wrapped=rsa.doFinal(secret);Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(secret,"AES"));aes.updateAAD(AAD);
            byte[] encrypted=aes.doFinal(plain);ByteArrayOutputStream out=new ByteArrayOutputStream();
            DataOutputStream d=new DataOutputStream(out);d.writeInt(1);d.writeInt(wrapped.length);d.write(wrapped);d.write(aes.getIV());d.write(encrypted);
            byte[] result=out.toByteArray();if(result.length>MAX)throw new SecurityException("Archive too large");return result;
        }finally{Arrays.fill(secret,(byte)0);}
    }
    static byte[] decrypt(byte[] archive,PrivateKey key)throws Exception {
        if(archive==null||archive.length>MAX||archive.length<300)throw new SecurityException("Invalid archive");
        DataInputStream d=new DataInputStream(new ByteArrayInputStream(archive));
        if(d.readInt()!=1||d.readInt()!=256)throw new SecurityException("Invalid archive header");
        byte[] wrapped=new byte[256],iv=new byte[12];d.readFully(wrapped);d.readFully(iv);
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE,key,new OAEPParameterSpec("SHA-256","MGF1",java.security.spec.MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
        byte[] secret=rsa.doFinal(wrapped);
        try{Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(secret,"AES"),new GCMParameterSpec(128,iv));aes.updateAAD(AAD);return aes.doFinal(d.readAllBytes());}
        finally{Arrays.fill(secret,(byte)0);}
    }
    private static AtomicFile archive(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"connect-transfer.encrypted"));}
    public static void storeArchive(Context c,byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>MAX)throw new SecurityException("Archive too large");
        AtomicFile file=archive(c);FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    public static byte[] loadArchive(Context c)throws Exception {return archive(c).readFully();}
    public static boolean hasArchive(Context c){return archive(c).getBaseFile().exists();}
    private static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}
    public static SQLiteDatabase inbox(Context c){
        SQLiteDatabase db=c.openOrCreateDatabase("nexo-connect-pilot.db",0,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL DEFAULT 'note',title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',original_text TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT 'Otros',tags TEXT NOT NULL DEFAULT '',favorite INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'manual',note_title_manual INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nexo_connect_import_receipts(delivery_id TEXT PRIMARY KEY NOT NULL,sender_id TEXT NOT NULL,entity_type TEXT NOT NULL,payload_sha256 TEXT NOT NULL,local_id INTEGER NOT NULL CHECK(local_id>0),acked INTEGER NOT NULL DEFAULT 0 CHECK(acked IN(0,1)),imported_at INTEGER NOT NULL)");
        return db;
    }
    public static String prepareSource(Context c,String request,byte[] publicKey)throws Exception {
        if(!PILOT.equals(c.getPackageName())||!UUID.fromString(request).toString().equals(request))throw new SecurityException();
        synchronized(NexoConnectSessionVault.REFRESH_LOCK){
            if(state(c).getBoolean("sourceFrozen",false)){
                if(!request.equals(state(c).getString("sourceRequest",""))||!b64(publicKey).equals(state(c).getString("targetKey","")))throw new SecurityException("Another transfer is pending");
                return state(c).getString("ticket",null);
            }
            NexoConnectSessionVault.Saved saved=new NexoConnectSessionVault(c).restore();
            JSONObject root=new JSONObject().put("request",request).put("account",saved.accountId.toString())
                .put("email",saved.email).put("refresh",saved.refreshToken);
            try(AndroidIdentityVault.IdentityBytes keys=new AndroidIdentityVault(c,saved.accountId).restore()){
                byte[] h=keys.hpkeKeyset(),s=keys.signingKeyset();
                try{root.put("hpke",b64(h)).put("signer",b64(s));}finally{Arrays.fill(h,(byte)0);Arrays.fill(s,(byte)0);}
            }
            root.put("peers",new AndroidPeerTrustStore(c,saved.accountId).migrationSnapshot());
            JSONObject names=new JSONObject();
            for(Map.Entry<String,?> entry:c.getSharedPreferences("nexo-connect-local-names",0).getAll().entrySet())
                if(entry.getValue() instanceof String)names.put(entry.getKey(),entry.getValue());
            root.put("names",names);JSONArray rows=new JSONArray();
            try(SQLiteDatabase db=inbox(c);Cursor row=db.rawQuery("SELECT r.delivery_id,r.sender_id,r.entity_type,r.payload_sha256,r.acked,r.imported_at,i.title,i.body FROM items i LEFT JOIN nexo_connect_import_receipts r ON r.local_id=i.id ORDER BY i.id",null)){
                while(row.moveToNext()){
                    String id=row.getString(0),type=row.getString(2),body=row.getString(7);
                    JSONObject data;
                    if(NexoConnectSealedNoteStore.isSealed(body)){
                        if(id==null)throw new SecurityException("Orphan sealed record");
                        JSONObject inner=new NexoConnectSealedNoteStore(saved.accountId,UUID.fromString(id)).read(body);
                        if(!type.equals(inner.optString(NexoConnectSealedNoteStore.PACKED_ENTITY,"note")))throw new SecurityException("Type mismatch");
                        data="note".equals(type)?inner:inner.getJSONObject(NexoConnectSealedNoteStore.PACKED_DATA);
                    }else{data=new JSONObject().put("title",row.getString(6)).put("body",body);type="note";}
                    boolean historical=id==null;if(historical)id=UUID.randomUUID().toString();
                    rows.put(new JSONObject().put("id",id).put("sender",historical?saved.accountId.toString():row.getString(1))
                        .put("type",type).put("hash",historical?"historical":row.getString(3)).put("acked",historical?1:row.getInt(4))
                        .put("at",historical?System.currentTimeMillis():row.getLong(5)).put("data",data));
                }
            }
            root.put("rows",rows);byte[] plain=root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encrypted;try{encrypted=encrypt(plain,publicKey);}finally{Arrays.fill(plain,(byte)0);}
            storeArchive(c,encrypted);Arrays.fill(encrypted,(byte)0);String ticket=UUID.randomUUID().toString();
            if(!state(c).edit().putBoolean("sourceFrozen",true).putString("sourceRequest",request)
                .putString("targetKey",b64(publicKey)).putString("ticket",ticket).commit())throw new IOException();
            android.app.job.JobScheduler jobs=c.getSystemService(android.app.job.JobScheduler.class);if(jobs!=null)jobs.cancel(146801);
            return ticket;
        }
    }
    public static void retireSource(Context c,String ticket)throws Exception {
        if(!PILOT.equals(c.getPackageName())||!ticket.equals(state(c).getString("ticket","")))throw new SecurityException();
        synchronized(NexoConnectSessionVault.REFRESH_LOCK){
            if(!state(c).edit().putBoolean("sourceMoved",true).commit())throw new IOException();
            new NexoConnectSessionVault(c).clear();
        }
    }
    public static void importTarget(Context c,String ticket,byte[] encrypted)throws Exception {
        if(!HOST.equals(c.getPackageName()))throw new SecurityException();
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        byte[] clear=decrypt(encrypted,(PrivateKey)store.getKey(KEY,null));
        try{
            JSONObject root=new JSONObject(new String(clear,java.nio.charset.StandardCharsets.UTF_8));
            String request=request(c);if(!request.equals(root.getString("request")))throw new SecurityException("Wrong request");
            UUID account=UUID.fromString(root.getString("account"));
            if(!state(c).getBoolean("targetPending",false)&&new NexoConnectSessionVault(c).exists())throw new SecurityException("Nexo already has an account");
            storeArchive(c,encrypted);
            if(!state(c).edit().putBoolean("targetPending",true).putString("ticket",ticket).commit())throw new IOException();
            byte[] hpke=Base64.decode(root.getString("hpke"),Base64.NO_WRAP),signer=Base64.decode(root.getString("signer"),Base64.NO_WRAP);
            try{
                AndroidIdentityVault keys=new AndroidIdentityVault(c,account);
                if(!keys.exists())keys.saveNew(hpke,signer);
                else try(AndroidIdentityVault.IdentityBytes old=keys.restore()){
                    byte[] h=old.hpkeKeyset(),s=old.signingKeyset();
                    try{if(!MessageDigest.isEqual(h,hpke)||!MessageDigest.isEqual(s,signer))throw new SecurityException("Different identity already present");}
                    finally{Arrays.fill(h,(byte)0);Arrays.fill(s,(byte)0);}
                }
                NexoConnectLocalPhoneIdentity.openOrCreate(c,account); // parse and validate transferred keysets
            }finally{Arrays.fill(hpke,(byte)0);Arrays.fill(signer,(byte)0);}
            new AndroidPeerTrustStore(c,account).restoreMigration(root.getJSONArray("peers"));
            try(SQLiteDatabase db=inbox(c)){
                db.beginTransaction();try{
                    JSONArray rows=root.getJSONArray("rows");
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
                        try(Cursor exists=db.rawQuery("SELECT sender_id,payload_sha256,entity_type FROM nexo_connect_import_receipts WHERE delivery_id=?",new String[]{id})){
                            if(exists.moveToFirst()){
                                if(!row.getString("sender").equals(exists.getString(0))||!row.getString("hash").equals(exists.getString(1))||!row.getString("type").equals(exists.getString(2)))throw new SecurityException("Existing receipt differs");
                                continue;
                            }
                        }
                        long local=new NexoConnectSealedNoteStore(account,UUID.fromString(id)).insertNew(db,row.getString("type"),row.getJSONObject("data"));
                        db.execSQL("INSERT INTO nexo_connect_import_receipts VALUES(?,?,?,?,?,?,?)",new Object[]{id,row.getString("sender"),row.getString("type"),row.getString("hash"),local,row.getInt("acked"),row.getLong("at")});
                    }
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            SharedPreferences.Editor names=c.getSharedPreferences("nexo-connect-local-names",0).edit();JSONObject n=root.getJSONObject("names");
            for(Iterator<String> it=n.keys();it.hasNext();){String name=it.next();names.putString(name,n.getString(name));}if(!names.commit())throw new IOException();
            new NexoConnectSessionVault(c).saveCredentials(account,root.getString("email"),root.getString("refresh"));
        }finally{Arrays.fill(clear,(byte)0);}
    }
    public static void finishTarget(Context c)throws Exception {
        if(!state(c).edit().putBoolean("targetPending",false).putBoolean("completed",true).commit())throw new IOException();
        archive(c).delete();
    }
}
