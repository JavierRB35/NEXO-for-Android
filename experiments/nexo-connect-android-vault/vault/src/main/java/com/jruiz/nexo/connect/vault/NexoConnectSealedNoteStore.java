package com.jruiz.nexo.connect.vault;

import android.database.sqlite.SQLiteDatabase;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectNativeWriter;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * EXPERIMENTAL PILOT ONLY. Encrypts the entire verified note (including title)
 * BEFORE the transactional SQLite INSERT. Never commits plaintext note text.
 *
 * Android Keystore AES-256-GCM key is non-exportable; authenticated associated
 * data binds ciphertext to this account and this unique delivery receipt.
 * The prefix is a storage FORMAT MARKER, not proof of authentication: callers
 * must decrypt and authenticate before displaying any contents.
 *
 * Legacy pilot notes remain readable but are NOT retroactively encrypted;
 * migrating pre-existing plaintext SQLite/WAL securely needs a separate plan.
 * Fail closed on non-note entities until protected writers exist for them.
 */
public final class NexoConnectSealedNoteStore implements NexoConnectAtomicImporter.EntityWriter {
    private static final String ALIAS = "nexo_connect_pilot_notes_aes256gcm_v1";
    private static final String PREFIX = "nxc2:";
    /** Format within the existing AES-GCM envelope, for non-note entities. */
    public static final String PACKED_ENTITY = "_nxcEntity";
    public static final String PACKED_DATA = "_nxcData";
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int MAX_BLOB_BYTES = 512 * 1024;

    private final UUID accountId;
    private final UUID deliveryId;

    public NexoConnectSealedNoteStore(UUID accountId, UUID deliveryId) {
        if(accountId == null || deliveryId == null)
            throw new IllegalArgumentException("Authenticated note owner/receipt required");
        this.accountId = accountId;
        this.deliveryId = deliveryId;
    }

    public static boolean isSealed(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    @Override public long insertNew(SQLiteDatabase db, String entity, JSONObject note)
            throws Exception {
        if(db == null || !db.isOpen() || !db.inTransaction())
            throw new GeneralSecurityException("Atomic receipt transaction required");
        if(note==null||!"note".equals(entity)&&
                !"appointment".equals(entity)&&!"list".equals(entity)&&
                !"journal".equals(entity)&&!"book".equals(entity)&&
                !"movie".equals(entity))
            throw new GeneralSecurityException(
                    "Encrypted import unsupported without safe native type");
        // The decoded data was sanitized by AtomicImporter, but explicitly
        // revalidate types before binding this copy to the Keystore.
        JSONObject sanitized=NexoConnectPayloadCodec.sanitize(entity,note,null);
        String title=sanitized.optString("title","").trim();
        if(title.isEmpty()||title.length()>250)
            throw new GeneralSecurityException("Invalid protected title");
        if("note".equals(entity)&&
                sanitized.optString("body","").length()>240000)
            throw new GeneralSecurityException("Invalid protected note");

        // Backwards compatible: existing note ciphertext keeps its original
        // inner JSON structure. Non-note entities get a typed inner envelope.
        JSONObject inner="note".equals(entity)?sanitized:
                new JSONObject().put(PACKED_ENTITY,entity)
                                .put(PACKED_DATA,sanitized);
        String encrypted=seal(inner.toString());
        JSONObject surrogate=new JSONObject()
                .put("title","note".equals(entity)?"Nota cifrada":"Ficha cifrada")
                .put("body",encrypted)
                .put("category","Notas");
        // This storage is a SEALED INBOX, not the final native destination.
        // We intentionally reuse the proven protected 'items' SQLite schema
        // until a per-type native promotion is available.
        return NexoConnectNativeWriter.INSTANCE.insertNew(db,"note",surrogate);
    }

    String sealForChat(JSONObject payload)throws Exception {
        NexoConnectChatStore.parse(payload);
        return seal(NexoConnectPayloadCodec.sanitize("note",payload,null).toString());
    }

    public JSONObject read(String value) throws Exception {
        if(!isSealed(value) || value.length() > MAX_BLOB_BYTES * 2)
            throw new GeneralSecurityException("Not a sealed Nexo note");
        byte[] sealed;
        String encoded = value.substring(PREFIX.length());
        try {
            sealed = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch(IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid sealed note encoding", error);
        }
        if(sealed.length < IV_BYTES + TAG_BYTES + 1 ||
                sealed.length > MAX_BLOB_BYTES ||
                !encode(sealed).equals(encoded)) {
            Arrays.fill(sealed, (byte) 0);
            throw new GeneralSecurityException("Invalid sealed note length or encoding");
        }
        byte[] iv = Arrays.copyOfRange(sealed, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(sealed, IV_BYTES, sealed.length);
        byte[] clear = null;
        try {
            SecretKey key = findKey();
            if(key == null)
                throw new GeneralSecurityException("Note key missing from Android Keystore");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(aad());
            clear = c.doFinal(ciphertext);
            JSONObject result=new JSONObject(new String(clear,StandardCharsets.UTF_8));
            if(result.has(PACKED_ENTITY)||result.has(PACKED_DATA)){
                if(result.length()!=2||!result.has(PACKED_ENTITY)||
                        !result.has(PACKED_DATA))
                    throw new GeneralSecurityException("Invalid typed sealed envelope");
                String type=result.optString(PACKED_ENTITY,"");
                JSONObject data=result.optJSONObject(PACKED_DATA);
                if(data==null||!("appointment".equals(type)||
                        "list".equals(type)||"journal".equals(type)||
                        "book".equals(type)||"movie".equals(type)))
                    throw new GeneralSecurityException("Invalid protected entity");
                JSONObject normalized=NexoConnectPayloadCodec.sanitize(type,data,null);
                if(normalized.optString("title","").trim().isEmpty())
                    throw new GeneralSecurityException("Protected title missing");
            }else{
                String title=result.optString("title","").trim();
                if(title.isEmpty()||title.length()>250||
                        result.optString("body","").length()>240000)
                    throw new GeneralSecurityException("Invalid decrypted note");
            }
            return result;
        } finally {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(sealed, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            if(clear != null) Arrays.fill(clear, (byte) 0);
        }
    }

    private String seal(String plain) throws Exception {
        byte[] clear = plain.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = null;
        byte[] combined = null;
        byte[] iv = null;
        try {
            if(clear.length == 0 || clear.length > MAX_BLOB_BYTES - 64)
                throw new GeneralSecurityException("Note exceeds sealed storage limit");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            // AndroidKeyStore requires a provider-generated random IV when
            // randomized encryption is enabled. NEVER supply a caller IV.
            c.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            iv = c.getIV();
            if(iv == null || iv.length != IV_BYTES)
                throw new GeneralSecurityException("Unexpected Keystore GCM nonce");
            c.updateAAD(aad());
            ciphertext = c.doFinal(clear);
            combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + encode(combined);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if(iv != null) Arrays.fill(iv, (byte) 0);
            if(ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
            if(combined != null) Arrays.fill(combined, (byte) 0);
        }
    }

    private byte[] aad() {
        return ("nexo-connect/pilot-note/v1/" + accountId + "/" + deliveryId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static SecretKey findKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key raw = ks.getKey(ALIAS, null);
        return raw instanceof SecretKey ? (SecretKey) raw : null;
    }

    private static synchronized SecretKey getOrCreateKey() throws Exception {
        SecretKey existing = findKey();
        if(existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }
}
