package com.jruiz.nexo.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The user's portable chat-backup passphrase, encrypted locally with Android Keystore. */
public final class NexoBackupPasswordStore {
    private static final String PREFS="nexo_backup_secret";
    private static final String ALIAS="nexo_backup_passphrase_aes_v1";
    private NexoBackupPasswordStore(){}

    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static boolean configured(Context c){
        return prefs(c).contains("sealed")&&prefs(c).contains("iv");
    }
    private static SecretKey key(boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing=(SecretKey)store.getKey(ALIAS,null);
        if(existing!=null||!create)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    public static void save(Context context,char[] password) throws Exception {
        if(password==null||password.length<8)
            throw new IOException("La contraseña debe tener al menos 8 caracteres.");
        byte[] plain=new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(true));
            byte[] sealed=cipher.doFinal(plain);
            if(!prefs(context).edit()
                .putString("sealed",Base64.encodeToString(sealed,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .commit())
                throw new IOException("No se pudo guardar la contraseña protegida.");
        } finally {Arrays.fill(plain,(byte)0);}
    }
    public static char[] load(Context context) throws Exception {
        if(!configured(context))return null;
        SecretKey secret=key(false);
        if(secret==null)throw new IOException("La clave segura de este móvil ya no está disponible. Configura de nuevo la contraseña.");
        byte[] plain=null;
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv=Base64.decode(prefs(context).getString("iv",""),Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,iv));
            plain=cipher.doFinal(Base64.decode(prefs(context).getString("sealed",""),Base64.NO_WRAP));
            return new String(plain,StandardCharsets.UTF_8).toCharArray();
        }catch(Exception error){
            throw new IOException("No se pudo recuperar la contraseña protegida. Configúrala de nuevo.",error);
        }finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
}
