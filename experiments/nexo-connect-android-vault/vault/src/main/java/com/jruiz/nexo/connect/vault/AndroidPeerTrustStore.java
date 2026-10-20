package com.jruiz.nexo.connect.vault;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.Ed25519PublicKey;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * EXPERIMENTAL: QR-pinned identities for Nexo Connect, separate from server DB.
 *
 * Never pin keys returned by Supabase /contact/key or /delivery/open.
 * The UI must verify the FULL signing and HPKE SHA-256 fingerprints over an
 * independent trusted channel and obtain explicit consent before saveQrConfirmed.
 * The API intentionally has no silent "update to server's current key" method.
 *
 * Integrity + confidentiality: AES-256-GCM, nonexportable Android Keystore key,
 * private NoBackup storage, AtomicFile writes, account-bound associated data.
 * Anti-rollback against a rooted attacker restoring older valid ciphertext
 * is NOT guaranteed (requires a separate monotonic trusted counter).
 */
public final class AndroidPeerTrustStore {
    private static final int FILE_MAGIC=0x4e435450; // NCTP
    private static final int RECORD_MAGIC=0x50494e31; // PIN1
    private static final int VERSION=1, IV_SIZE=12, TAG_BITS=128;
    private static final int KEY_BYTES=32, MAX_PEERS=200, MAX_FILE=65536;
    private static final Object LOCK=new Object();
    private static final String QR_FORMAT="nexo-connect-peer";
    private final UUID owner;
    private final String keyAlias;
    private final AtomicFile file;
    private final byte[] aad;

    public AndroidPeerTrustStore(Context context, UUID accountId)
            throws GeneralSecurityException {
        if(context==null||accountId==null)
            throw new IllegalArgumentException("Missing trust store account");
        Context app=context.getApplicationContext();
        owner=accountId;
        String label=hex(sha256((app.getPackageName()+"/"+owner)
                .getBytes(StandardCharsets.UTF_8))).substring(0,32);
        keyAlias="nexo_connect_qr_trust_v1_"+label;
        file=new AtomicFile(new File(app.getNoBackupFilesDir(),
                "nexo-connect-verified-peers-"+label+".sealed"));
        aad=("nexo-connect-trust/v1/"+app.getPackageName()+"/"+owner)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Must be invoked after HUMAN confirmation of fingerprints outside Supabase. */
    public PeerPin saveQrConfirmed(String scannedQr, String trustedSigningFingerprint,
            String trustedEncryptionFingerprint, boolean userApproved)
            throws Exception {
        if(!userApproved) throw new SecurityException("QR consent required");
        PeerPin scanned=parseQr(scannedQr);
        if(scanned.peerId.equals(owner))
            throw new GeneralSecurityException("Cannot trust own account");
        if(!constantHexEquals(scanned.signingFingerprint(),trustedSigningFingerprint)||
                !constantHexEquals(scanned.encryptionFingerprint(),trustedEncryptionFingerprint))
            throw new GeneralSecurityException("Trusted-channel fingerprint mismatch");
        synchronized(LOCK) {
            Map<UUID,PeerPin> peers=read();
            PeerPin old=peers.get(scanned.peerId);
            if(old!=null && old.active) {
                if(!old.sameKeysAndGeneration(scanned))
                    throw new GeneralSecurityException(
                            "Peer key/generation changed: revoke and reverify QR explicitly");
                return old; // repeated identical QR cannot change trust
            }
            if(old==null&&peers.size()>=MAX_PEERS)
                throw new GeneralSecurityException("Too many trusted contacts");
            peers.put(scanned.peerId,scanned);
            write(peers);
            return scanned;
        }
    }

    /** Read ONLY the pin recorded after out-of-band QR verification. */
    public PeerPin findActive(UUID peerId) throws Exception {
        if(peerId==null||peerId.equals(owner))return null;
        synchronized(LOCK) {
            PeerPin result=read().get(peerId);
            return result!=null&&result.active?result:null;
        }
    }

    /** Revocation is durable: old cached KeysetHandles must not authorize import. */
    public boolean revoke(UUID peerId) throws Exception {
        if(peerId==null||peerId.equals(owner))
            throw new IllegalArgumentException("Invalid peer");
        synchronized(LOCK) {
            Map<UUID,PeerPin> peers=read();
            PeerPin old=peers.get(peerId);
            if(old==null||!old.active)return false;
            peers.put(peerId,new PeerPin(old.peerId,old.generation,
                    old.signingRaw,old.encryptionRaw,false));
            write(peers);
            return true;
        }
    }

    public UUID ownerAccountId() { return owner; }

    /** Never return a file path to Android WebView or the server. */
    File localFileForTest() { return file.getBaseFile(); }

    private Map<UUID,PeerPin> read() throws Exception {
        Map<UUID,PeerPin> peers=new TreeMap<>();
        if(!file.getBaseFile().exists())return peers;
        SecretKey key=existingKey();
        if(key==null)throw new GeneralSecurityException("Trust Keystore key lost");
        File f=file.getBaseFile();
        if(f.length()<32||f.length()>MAX_FILE)
            throw new GeneralSecurityException("Corrupt trust file size");
        byte[] sealed;
        try(java.io.InputStream input=file.openRead();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] chunk=new byte[4096];
            for(int n;(n=input.read(chunk))!=-1;){
                if(bytes.size()+n>MAX_FILE)
                    throw new GeneralSecurityException("Trust file too large");
                bytes.write(chunk,0,n);
            }
            sealed=bytes.toByteArray();
        }
        byte[] plain=null;
        try {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(sealed));
            if(in.readInt()!=FILE_MAGIC||in.readUnsignedByte()!=VERSION)
                throw new GeneralSecurityException("Unknown trust format");
            int ivLength=in.readUnsignedByte();
            int cipherLength=in.readInt();
            if(ivLength!=IV_SIZE||cipherLength<16||
                    cipherLength>MAX_FILE||in.available()!=ivLength+cipherLength)
                throw new GeneralSecurityException("Invalid sealed trust lengths");
            byte[] iv=new byte[IV_SIZE],ciphertext=new byte[cipherLength];
            in.readFully(iv);
            in.readFully(ciphertext);
            Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(TAG_BITS,iv));
            aes.updateAAD(aad);
            plain=aes.doFinal(ciphertext);
            DataInputStream input=new DataInputStream(new ByteArrayInputStream(plain));
            if(input.readInt()!=RECORD_MAGIC||input.readUnsignedByte()!=VERSION)
                throw new GeneralSecurityException("Unknown trust records");
            int count=input.readUnsignedShort();
            if(count>MAX_PEERS)
                throw new GeneralSecurityException("Too many trust records");
            for(int i=0;i<count;i++){
                UUID id=new UUID(input.readLong(),input.readLong());
                int generation=input.readInt();
                boolean active=input.readUnsignedByte()==1;
                byte[] signing=new byte[KEY_BYTES],encryption=new byte[KEY_BYTES];
                input.readFully(signing);input.readFully(encryption);
                if(id.equals(owner)||generation<=0||
                        peers.put(id,new PeerPin(id,generation,signing,encryption,active))!=null)
                    throw new GeneralSecurityException("Invalid or duplicated peer record");
            }
            if(input.available()!=0)
                throw new GeneralSecurityException("Trailing peer records");
            return peers;
        } finally {
            Arrays.fill(sealed,(byte)0);
            if(plain!=null)Arrays.fill(plain,(byte)0);
        }
    }

    private void write(Map<UUID,PeerPin> peers) throws Exception {
        if(peers.size()>MAX_PEERS)throw new GeneralSecurityException("Too many peers");
        SecretKey key=createOrGetKey();
        ByteArrayOutputStream rawBytes=new ByteArrayOutputStream();
        DataOutputStream data=new DataOutputStream(rawBytes);
        data.writeInt(RECORD_MAGIC);data.writeByte(VERSION);
        data.writeShort(peers.size());
        for(PeerPin p:peers.values()){
            data.writeLong(p.peerId.getMostSignificantBits());
            data.writeLong(p.peerId.getLeastSignificantBits());
            data.writeInt(p.generation);
            data.writeByte(p.active?1:0);
            data.write(p.signingRaw);
            data.write(p.encryptionRaw);
        }
        data.flush();
        byte[] plain=rawBytes.toByteArray();
        byte[] encoded=null;
        try {
            Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE,key);
            byte[] iv=aes.getIV();
            if(iv==null||iv.length!=IV_SIZE)
                throw new GeneralSecurityException("Unexpected Keystore IV");
            aes.updateAAD(aad);
            byte[] ciphertext=aes.doFinal(plain);
            ByteArrayOutputStream outerBytes=new ByteArrayOutputStream();
            DataOutputStream output=new DataOutputStream(outerBytes);
            output.writeInt(FILE_MAGIC);
            output.writeByte(VERSION);
            output.writeByte(IV_SIZE);
            output.writeInt(ciphertext.length);
            output.write(iv);output.write(ciphertext);output.flush();
            encoded=outerBytes.toByteArray();
            Arrays.fill(ciphertext,(byte)0);
            if(encoded.length>MAX_FILE)
                throw new GeneralSecurityException("Excessive sealed trust store");
            FileOutputStream stream=null;
            try {
                stream=file.startWrite();
                stream.write(encoded);
                file.finishWrite(stream);
            }catch(IOException|RuntimeException ex){
                if(stream!=null)file.failWrite(stream);
                throw ex;
            }
        }finally {
            Arrays.fill(plain,(byte)0);
            if(encoded!=null)Arrays.fill(encoded,(byte)0);
        }
    }

    private SecretKey existingKey() throws GeneralSecurityException {
        try {
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            java.security.Key entry=store.getKey(keyAlias,null);
            return entry instanceof SecretKey?(SecretKey)entry:null;
        }catch(IOException|java.security.cert.CertificateException ex){
            throw new GeneralSecurityException("Cannot access trust Keystore",ex);
        }
    }

    private SecretKey createOrGetKey() throws GeneralSecurityException {
        SecretKey existing=existingKey();
        if(existing!=null)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }

    /** QR schema is strict; raw keys, NOT Base64 of serialized private keysets. */
    public static PeerPin parseQr(String qr) throws Exception {
        if(qr==null||qr.length()<25||qr.length()>1024)
            throw new GeneralSecurityException("Invalid QR length");
        JSONObject obj=new JSONObject(qr);
        if(obj.length()!=5||!"nexo-connect-peer".equals(obj.optString("format",""))||
                obj.optInt("version",-1)!=VERSION ||
                !obj.has("peerId")||!obj.has("generation")||
                !obj.has("signingKey")||!obj.has("encryptionKey"))
            throw new GeneralSecurityException("Unexpected QR fields");
        String id=obj.optString("peerId","");
        if(!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new GeneralSecurityException("Invalid peer identity");
        Object rawGeneration=obj.opt("generation");
        if(!(rawGeneration instanceof Number)||
                ((Number)rawGeneration).doubleValue()!=((Number)rawGeneration).longValue()||
                ((Number)rawGeneration).longValue()<=0||
                ((Number)rawGeneration).longValue()>Integer.MAX_VALUE)
            throw new GeneralSecurityException("Invalid QR generation");
        return new PeerPin(UUID.fromString(id),
                ((Number)rawGeneration).intValue(),
                decodeRaw(obj.optString("signingKey","")),
                decodeRaw(obj.optString("encryptionKey","")),true);
    }

    public static String encodeQr(PeerPin pinned) throws Exception {
        if(pinned==null||!pinned.active)
            throw new GeneralSecurityException("Invalid QR identity");
        return new JSONObject()
                .put("format",QR_FORMAT).put("version",VERSION)
                .put("peerId",pinned.peerId.toString())
                .put("generation",pinned.generation)
                .put("signingKey",encodeRaw(pinned.signingRaw))
                .put("encryptionKey",encodeRaw(pinned.encryptionRaw))
                .toString();
    }

    private static byte[] decodeRaw(String encoded) throws GeneralSecurityException {
        if(encoded==null||!encoded.matches("[A-Za-z0-9_-]{43}"))
            throw new GeneralSecurityException("Invalid QR key encoding");
        try{
            byte[] bytes=Base64.decode(encoded,
                    Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
            if(bytes.length!=KEY_BYTES||!encodeRaw(bytes).equals(encoded))
                throw new GeneralSecurityException("Noncanonical QR key");
            return bytes;
        }catch(IllegalArgumentException ex){
            throw new GeneralSecurityException("Invalid QR key",ex);
        }
    }
    private static String encodeRaw(byte[] bytes){
        return Base64.encodeToString(bytes,
                Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
    }
    private static boolean constantHexEquals(String computed,String supplied){
        if(supplied==null||!supplied.matches("[0-9a-f]{64}"))return false;
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] sha256(byte[] input) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
    private static String hex(byte[] bytes){
        char[] digits="0123456789abcdef".toCharArray();
        char[] chars=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){
            chars[i*2]=digits[(bytes[i]>>>4)&15];
            chars[i*2+1]=digits[bytes[i]&15];
        }
        return new String(chars);
    }

    public static final class PeerPin {
        public final UUID peerId;
        public final int generation;
        public final boolean active;
        private final byte[] signingRaw,encryptionRaw;
        public PeerPin(UUID peerId,int generation,byte[] signingRaw,byte[] encryptionRaw,
                boolean active) throws GeneralSecurityException {
            if(peerId==null||generation<=0||
                    signingRaw==null||signingRaw.length!=KEY_BYTES||
                    encryptionRaw==null||encryptionRaw.length!=KEY_BYTES)
                throw new GeneralSecurityException("Invalid pinned public keys");
            this.peerId=peerId;
            this.generation=generation;
            this.signingRaw=signingRaw.clone();
            this.encryptionRaw=encryptionRaw.clone();
            this.active=active;
        }
        public byte[] signingPublicRaw(){return signingRaw.clone();}
        public byte[] encryptionPublicRaw(){return encryptionRaw.clone();}
        public String signingFingerprint() throws GeneralSecurityException{
            return hex(sha256(signingRaw));
        }
        public String encryptionFingerprint() throws GeneralSecurityException{
            return hex(sha256(encryptionRaw));
        }
        private boolean sameKeysAndGeneration(PeerPin p){
            return generation==p.generation &&
                    MessageDigest.isEqual(signingRaw,p.signingRaw) &&
                    MessageDigest.isEqual(encryptionRaw,p.encryptionRaw);
        }
        /**
         * Bind the Tink public verifier supplied to E2EE inbox to this pin.
         * No key from network can bypass the locally saved QR fingerprint.
         */
        public void verifyTinkPublicKey(KeysetHandle claimed) throws Exception{
            if(!active||claimed==null||claimed.size()!=1||
                    !(claimed.getAt(0).getKey() instanceof Ed25519PublicKey))
                throw new GeneralSecurityException("Invalid QR-pinned verifier");
            byte[] raw=((Ed25519PublicKey)claimed.getAt(0).getKey())
                    .getPublicKeyBytes().toByteArray();
            if(!MessageDigest.isEqual(raw,signingRaw))
                throw new GeneralSecurityException("Untrusted sender signing key");
        }
    }
}
