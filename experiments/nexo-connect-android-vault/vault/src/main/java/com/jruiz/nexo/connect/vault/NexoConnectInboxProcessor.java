package com.jruiz.nexo.connect.vault;

import android.database.sqlite.SQLiteDatabase;

import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;

import com.jruiz.nexo.connect.NexoConnectAtomicImporter;
import com.jruiz.nexo.connect.NexoConnectNativeWriter;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/**
 * ISOLATED EXPERIMENT: an E2EE inbox envelope MUST be authenticated and
 * decrypted before invoking the native SQLite import ledger. It is NOT called
 * by the Nexo UI or production transport.
 *
 * TRUST BOUNDARY: pinnedSenderPublic must come from a local QR-verified trust
 * store, never from the server response. recipientPrivate comes from the
 * AndroidKeyStore-protected identity. Neither private keys nor plaintext leave
 * this method; plaintext buffers are zeroed in a finally block.
 *
 * This class is NOT a contact pin store, account recovery, network transport,
 * notification handler, or a substitute for user consent before import.
 */
public final class NexoConnectInboxProcessor {
    private static final int CONTEXT_BYTES = 142;
    private static final int SIGNATURE_BYTES = 64;
    private static final int SHA_BYTES = 32;
    private static final int MAX_CIPHER = 512 * 1024;
    private static final long MAX_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long FUTURE_SKEW_MS = 5 * 60 * 1000L;
    private static final String DOMAIN = "NEXO-CONNECT-CRYPTO-POC-1";
    private static final String[] ENTITY_NAMES = {
        "appointment","note","list","journal","book","movie","document"
    };

    private NexoConnectInboxProcessor() {}

    /**
     * Imports only after signature, pinned-key, context and HPKE checks.
     * Return means local SQLite has COMMITTED a copy (or recognized an
     * identical previously committed delivery); only THEN may the caller ask
     * the signed /delivery/resolve endpoint for accepted.
     *
     * @param recipientAccount the Auth UUID from the verified local session
     * @param recipientGeneration the registered generation of THIS phone
     * @param nowMillis current phone time; remote expiry must also be
     *                  checked by server to resist a manipulated phone clock
     */
    public static NexoConnectAtomicImporter.Result receiveAndImport(
            SQLiteDatabase db, JSONObject serverEnvelope,
            KeysetHandle recipientPrivate, KeysetHandle pinnedSenderPublic,
            UUID recipientAccount, int recipientGeneration, long nowMillis)
            throws Exception {
        if (db == null || serverEnvelope == null || recipientPrivate == null ||
                pinnedSenderPublic == null || recipientAccount == null ||
                recipientGeneration <= 0 || nowMillis <= 0)
            throw new GeneralSecurityException("Missing trusted inbox inputs");
        if (!db.isOpen()) throw new GeneralSecurityException("Database is closed");

        UUID deliveryId = strictUuid(serverEnvelope, "deliveryId");
        UUID senderId = strictUuid(serverEnvelope, "senderId");
        UUID recipientId = strictUuid(serverEnvelope, "recipientId");
        if (senderId.equals(recipientId) || !recipientId.equals(recipientAccount))
            throw new GeneralSecurityException("Wrong message recipient");

        int senderGen = strictGeneration(serverEnvelope,"senderGeneration");
        int recipientGen = strictGeneration(serverEnvelope,"recipientGeneration");
        if (recipientGen != recipientGeneration)
            throw new GeneralSecurityException("Recipient generation changed");
        String entity = serverEnvelope.optString("entityType","");
        byte entityCode = 0;
        for (int i=0; i<ENTITY_NAMES.length; i++)
            if (ENTITY_NAMES[i].equals(entity)) entityCode=(byte)(i+1);
        if (entityCode==0) throw new GeneralSecurityException("Unknown entity");

        // Exact hexadecimal lengths prevent unrestricted allocation from JSON.
        byte[] context = strictHex(serverEnvelope,"contextHex",CONTEXT_BYTES,CONTEXT_BYTES);
        byte[] ciphertext = strictHex(serverEnvelope,"ciphertextHex",1,MAX_CIPHER);
        byte[] signature = strictHex(serverEnvelope,"signatureHex",
                SIGNATURE_BYTES,SIGNATURE_BYTES);
        byte[] serverHash = strictHex(serverEnvelope,"ciphertextSha256Hex",
                SHA_BYTES,SHA_BYTES);

        if (!MessageDigest.isEqual(sha256(ciphertext),serverHash))
            throw new GeneralSecurityException("Ciphertext digest mismatch");
        ByteBuffer view=ByteBuffer.wrap(context).order(ByteOrder.BIG_ENDIAN);
        if (view.getInt(0)!=0x4e584331 || context[4]!=1 ||
                !uuidAt(view,5).equals(deliveryId) ||
                !uuidAt(view,21).equals(senderId) ||
                !uuidAt(view,37).equals(recipientId) ||
                context[53]!=entityCode ||
                view.getInt(54)!=senderGen ||
                view.getInt(58)!=recipientGen)
            throw new GeneralSecurityException("Authenticated routing mismatch");
        long issued=view.getLong(62), expires=view.getLong(70);
        if (issued<=0 || expires<=issued || expires-issued>MAX_TTL_MS ||
                issued>nowMillis+FUTURE_SKEW_MS || expires<=nowMillis)
            throw new GeneralSecurityException("Expired or invalid signed context");
        long serverExpiry=strictMillis(serverEnvelope,"expiresAtMs");
        if (serverExpiry!=expires)
            throw new GeneralSecurityException("Server expiry differs from signed expiry");

        // Check exactly ONE pinned Ed25519 raw public key; never trust a
        // server-supplied sender key, nor silently rotate the QR trust pin.
        SignatureConfig.register();
        if (pinnedSenderPublic.size()!=1 ||
                !(pinnedSenderPublic.getAt(0).getKey() instanceof Ed25519PublicKey))
            throw new GeneralSecurityException("Invalid pinned Ed25519 keyset");
        Ed25519PublicKey pinned = (Ed25519PublicKey)
                pinnedSenderPublic.getAt(0).getKey();
        byte[] senderRaw = pinned.getPublicKeyBytes().toByteArray();
        if (senderRaw.length!=32 ||
                !MessageDigest.isEqual(sha256(senderRaw),
                        Arrays.copyOfRange(context,78,110)))
            throw new GeneralSecurityException("Sender QR pin mismatch");

        HybridConfig.register();
        if (recipientPrivate.size()!=1) throw new GeneralSecurityException(
                "Ambiguous local HPKE keyset");
        KeysetHandle localPublic = recipientPrivate.getPublicKeysetHandle();
        if (!(localPublic.getAt(0).getKey() instanceof HpkePublicKey))
            throw new GeneralSecurityException("Unexpected recipient HPKE key");
        byte[] encryptionRaw = ((HpkePublicKey)
                localPublic.getAt(0).getKey()).getPublicKeyBytes().toByteArray();
        if (encryptionRaw.length!=32 ||
                !MessageDigest.isEqual(sha256(encryptionRaw),
                        Arrays.copyOfRange(context,110,142)))
            throw new GeneralSecurityException("Recipient encryption key mismatch");

        // Verify the exact Tink-v1 signing domain and length-prefixed
        // context+ciphertext representation BEFORE HPKE decryption.
        PublicKeyVerify verifier=pinnedSenderPublic.getPrimitive(
                RegistryConfiguration.get(),PublicKeyVerify.class);
        verifier.verify(signature, signedBytes(context,ciphertext));

        HybridDecrypt decrypt=recipientPrivate.getPrimitive(
                RegistryConfiguration.get(), HybridDecrypt.class);
        byte[] plaintext=decrypt.decrypt(ciphertext,context);
        try {
            return NexoConnectAtomicImporter.importVerified(
                    db,deliveryId.toString(),senderId.toString(),entity,
                    plaintext,NexoConnectNativeWriter.INSTANCE);
        } finally {
            Arrays.fill(plaintext,(byte)0);
        }
    }

    private static byte[] signedBytes(byte[] context,byte[] ciphertext) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(
                DOMAIN.length()+2+4+context.length+4+ciphertext.length);
        DataOutputStream stream=new DataOutputStream(out);
        stream.writeUTF(DOMAIN);
        stream.writeInt(context.length);
        stream.write(context);
        stream.writeInt(ciphertext.length);
        stream.write(ciphertext);
        stream.flush();
        return out.toByteArray();
    }

    private static UUID strictUuid(JSONObject data,String key) throws GeneralSecurityException {
        String value=data.optString(key,"");
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new GeneralSecurityException("Invalid UUID: "+key);
        return UUID.fromString(value);
    }

    private static UUID uuidAt(ByteBuffer buffer,int index) {
        return new UUID(buffer.getLong(index),buffer.getLong(index+8));
    }

    private static int strictGeneration(JSONObject data,String name)
            throws GeneralSecurityException {
        Object raw=data.opt(name);
        if(!(raw instanceof Number))throw new GeneralSecurityException(
                "Invalid generation: "+name);
        long value=((Number)raw).longValue();
        if(((Number)raw).doubleValue()!=value || value<=0 || value>Integer.MAX_VALUE)
            throw new GeneralSecurityException("Invalid generation: "+name);
        return (int)value;
    }

    private static long strictMillis(JSONObject data,String name)
            throws GeneralSecurityException {
        Object raw=data.opt(name);
        if(!(raw instanceof Number))throw new GeneralSecurityException(
                "Invalid timestamp: "+name);
        long value=((Number)raw).longValue();
        if(((Number)raw).doubleValue()!=value || value<=0 || value>253402300799999L)
            throw new GeneralSecurityException("Invalid timestamp: "+name);
        return value;
    }

    private static byte[] strictHex(JSONObject data,String field,int min,int max)
            throws GeneralSecurityException {
        String hex=data.optString(field,"");
        if(hex.length()<min*2 || hex.length()>max*2 ||
                (hex.length()&1)!=0 || !hex.matches("[0-9a-f]+"))
            throw new GeneralSecurityException("Invalid binary field: "+field);
        byte[] result=new byte[hex.length()/2];
        for(int i=0;i<result.length;i++)
            result[i]=(byte)((Character.digit(hex.charAt(i*2),16)<<4) |
                              Character.digit(hex.charAt(i*2+1),16));
        return result;
    }

    private static byte[] sha256(byte[] bytes) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}
