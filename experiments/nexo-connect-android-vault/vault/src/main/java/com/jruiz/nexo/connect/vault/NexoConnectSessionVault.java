package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
 * EXPERIMENTAL. Stores only the Supabase refresh credential and its verified
 * account identity, encrypted with non-exportable Android Keystore AES-GCM.
 * Never persists the access JWT or an OTP. No cloud/Android Auto Backup.
 * Possession of this credential is not enough for Nexo device authorization:
 * the signed phone challenge is rechecked after EVERY session restoration.
 */
public final class NexoConnectSessionVault {
    // Foreground sign-in and background inbox checks share one refresh-token
    // rotation lock in this process; never replay an obsolete refresh token.
    private static final Object REFRESH_LOCK = new Object();

    public static NexoConnectEmailOtpClient.VerifiedSession refreshSavedSession(
            Context context,NexoConnectEmailOtpClient otp) throws Exception {
        if(context==null || otp==null)
            throw new IllegalArgumentException("Session recovery requires an Auth client");
        synchronized(REFRESH_LOCK) {
            NexoConnectSessionVault store=new NexoConnectSessionVault(context);
            Saved saved=store.restore();
            NexoConnectEmailOtpClient.VerifiedSession fresh=
                    otp.refreshSession(saved.refreshToken,saved.accountId,saved.email);
            store.save(fresh); // rotate token before any device/network operation
            return fresh;
        }
    }

    private static final int MAGIC = 0x4e435356; // NCSV
    private static final int VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int MAX_BLOB = 16384;
    private static final String ALIAS = "nexo_connect_pilot_auth_session_v1";
    private final AtomicFile file;
    private final byte[] aad;

    public NexoConnectSessionVault(Context context) {
        if(context == null) throw new IllegalArgumentException("Context required");
        Context app = context.getApplicationContext();
        file = new AtomicFile(new File(app.getNoBackupFilesDir(),
                "nexo-connect-auth-session.sealed"));
        aad = ("nexo-connect-auth/v1/" + app.getPackageName())
                .getBytes(StandardCharsets.UTF_8);
    }

    public synchronized boolean exists() { return file.getBaseFile().exists(); }

    public synchronized void save(NexoConnectEmailOtpClient.VerifiedSession session)
            throws Exception {
        synchronized(REFRESH_LOCK) {
        if(session == null) throw new GeneralSecurityException("Missing verified session");
        String refresh = session.refreshTokenFor(session.accountId);
        requireRefresh(refresh);
        JSONObject payload = new JSONObject()
                .put("account", session.accountId.toString())
                .put("email", session.email)
                .put("refresh", refresh);
        byte[] clear = payload.toString().getBytes(StandardCharsets.UTF_8);
        byte[] cipherText = null;
        byte[] blob = null;
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            if(iv == null || iv.length != IV_BYTES)
                throw new GeneralSecurityException("Invalid nonce");
            cipher.updateAAD(aad);
            cipherText = cipher.doFinal(clear);
            ByteBuffer buf = ByteBuffer.allocate(6 + IV_BYTES + cipherText.length);
            buf.putInt(MAGIC).put((byte) VERSION).put((byte) IV_BYTES)
                    .put(iv).put(cipherText);
            blob = buf.array();
            if(blob.length > MAX_BLOB)
                throw new GeneralSecurityException("Session storage too large");
            FileOutputStream out = null;
            try {
                out = file.startWrite();
                out.write(blob);
                file.finishWrite(out);
            } catch (Exception e) {
                if(out != null) file.failWrite(out);
                throw e;
            }
        } finally {
            Arrays.fill(clear,(byte)0);
            if(cipherText != null) Arrays.fill(cipherText,(byte)0);
            if(blob != null) Arrays.fill(blob,(byte)0);
        }
            }
}

    public synchronized Saved restore() throws Exception {
        synchronized(REFRESH_LOCK) {
        if(!exists()) throw new GeneralSecurityException("No stored session");
        SecretKey key = findKey();
        if(key == null) throw new GeneralSecurityException("Session Keystore key missing");
        File disk = file.getBaseFile();
        if(disk.length() < 6 + IV_BYTES + 16 || disk.length() > MAX_BLOB)
            throw new GeneralSecurityException("Invalid sealed session length");
        byte[] bytes = new byte[(int) disk.length()];
        try {
            try(InputStream in = file.openRead()) {
                int offset=0, n;
                while(offset < bytes.length) {
                    n = in.read(bytes,offset,bytes.length-offset);
                    if(n < 0) throw new GeneralSecurityException("Truncated session");
                    offset += n;
                }
                if(in.read() != -1) throw new GeneralSecurityException("Trailing session data");
            }
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            if(buf.getInt() != MAGIC || buf.get() != VERSION ||
                    buf.get() != IV_BYTES)
                throw new GeneralSecurityException("Incompatible session format");
            byte[] iv = new byte[IV_BYTES];buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];buf.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD(aad);
            byte[] clear = cipher.doFinal(ciphertext);
            try {
                JSONObject obj = new JSONObject(new String(clear,StandardCharsets.UTF_8));
                String id = obj.optString("account","");
                String email = obj.optString("email","");
                String refresh = obj.optString("refresh","");
                UUID account = UUID.fromString(id);
                if(!account.toString().equals(id) || email.length() > 254 ||
                        !email.matches("[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+"))
                    throw new GeneralSecurityException("Invalid session owner");
                requireRefresh(refresh);
                return new Saved(account,email,refresh);
            } finally {
                Arrays.fill(clear,(byte)0);
                Arrays.fill(ciphertext,(byte)0);
            }
        } finally { Arrays.fill(bytes,(byte)0); }
            }
}

    /** Local sign-out only; existing remote sessions are not revoked here. */
    public synchronized void clear() {
        synchronized(REFRESH_LOCK) {
        file.delete();
            }
}

    private static void requireRefresh(String value) throws GeneralSecurityException {
        if(value == null || value.length() < 8 || value.length() > 4096 ||
                value.matches(".*\\s.*"))
            throw new GeneralSecurityException("Invalid refresh credential");
    }

    private static SecretKey findKey() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            java.security.Key key = ks.getKey(ALIAS,null);
            return key instanceof SecretKey ? (SecretKey) key : null;
        } catch (java.io.IOException | java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Keystore unavailable",e);
        }
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        SecretKey key = findKey();
        if(key != null) return key;
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return kg.generateKey();
    }

    public static final class Saved {
        public final UUID accountId;
        public final String email, refreshToken;
        private Saved(UUID id,String email,String refresh) {
            accountId=id;this.email=email;refreshToken=refresh;
        }
    }
}
