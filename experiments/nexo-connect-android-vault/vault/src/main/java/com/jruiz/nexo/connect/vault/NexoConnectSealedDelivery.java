package com.jruiz.nexo.connect.vault;

import android.util.Base64;

import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;
import com.jruiz.nexo.connect.NexoConnectPayloadCodec;

import org.json.JSONArray;
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
 * Isolated, client-side E2EE sending boundary. Constructs the *actual*
 * NXC1 authenticated context required by Edge and Android InboxProcessor.
 *
 * The recipient's HPKE public key MUST match the locally QR-verified,
 * Keystore-protected pin. No public key received from a server is trusted.
 * The sender's private Ed25519/HPKE keysets stay in AndroidIdentityVault.
 *
 * Returns ONLY ciphertext and metadata suitable for signedGateway.send(...).
 * No network, automatic sending, file I/O, document attachments or consent
 * are performed here. Explicit UI consent is required for each delivery.
 */
public final class NexoConnectSealedDelivery {
    private static final String DOMAIN="NEXO-CONNECT-CRYPTO-POC-1";
    private static final long MAX_TTL=30L*24*60*60*1000;
    // Leave headroom for Tink HPKE's encapsulation and authenticated tag.
    private static final int MAX_PLAINTEXT=500*1024;
    private static final int MAX_CIPHER=512*1024;
    private static final String[] ENTITY_NAMES={
            "appointment","note","list","journal","book","movie","document"
    };

    private NexoConnectSealedDelivery(){}

    public static JSONObject sealApproved(
            NexoConnectLocalPhoneIdentity sender,int senderGeneration,
            AndroidPeerTrustStore trustedPeers,UUID recipientAccount,
            KeysetHandle candidateRecipientPublic,UUID deliveryId,
            UUID clientRequestId,String entityType,JSONObject source,
            JSONArray listEntries,long issuedAtMs,long expiresAtMs,
            boolean userApproved)throws Exception {
        if(!userApproved)throw new SecurityException("Explicit send consent required");
        if(sender==null||trustedPeers==null||recipientAccount==null||
                candidateRecipientPublic==null||deliveryId==null||
                clientRequestId==null||source==null||
                senderGeneration<=0||issuedAtMs<=0||
                expiresAtMs<=issuedAtMs||expiresAtMs-issuedAtMs>MAX_TTL)
            throw new GeneralSecurityException("Incomplete signed delivery");
        if(!sender.accountId().equals(trustedPeers.ownerAccountId())||
                sender.accountId().equals(recipientAccount))
            throw new SecurityException("Cross-account or self delivery forbidden");
        final int entityCode=entityCode(entityType);
        if(entityCode==0||entityCode==7)
            // Documents cannot be imported until encrypted attachment support.
            throw new GeneralSecurityException("Unsupported native recipient entity");
        final AndroidPeerTrustStore.PeerPin initial=
                trustedPeers.findActive(recipientAccount);
        if(initial==null||initial.generation<=0)
            throw new SecurityException("Recipient QR identity not verified");
        if(candidateRecipientPublic.size()!=1||
                !(candidateRecipientPublic.getAt(0).getKey() instanceof HpkePublicKey))
            throw new GeneralSecurityException("Unique HPKE public key required");
        byte[] recipientRaw=((HpkePublicKey)
                candidateRecipientPublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        if(recipientRaw.length!=32||
                !MessageDigest.isEqual(initial.encryptionPublicRaw(),recipientRaw))
            throw new GeneralSecurityException("Recipient encryption key not QR pinned");

        SignatureConfig.register();HybridConfig.register();
        KeysetHandle senderPublic=sender.signingPrivate().getPublicKeysetHandle();
        if(senderPublic.size()!=1||
                !(senderPublic.getAt(0).getKey() instanceof Ed25519PublicKey))
            throw new GeneralSecurityException("Sender identity unavailable");
        byte[] senderRaw=((Ed25519PublicKey)senderPublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        if(senderRaw.length!=32)
            throw new GeneralSecurityException("Invalid Ed25519 public key");

        ByteArrayOutputStream out=new ByteArrayOutputStream(142);
        DataOutputStream body=new DataOutputStream(out);
        body.writeInt(0x4e584331);body.writeByte(1);
        writeUuid(body,deliveryId);
        writeUuid(body,sender.accountId());
        writeUuid(body,recipientAccount);
        body.writeByte(entityCode);
        body.writeInt(senderGeneration);
        body.writeInt(initial.generation);
        body.writeLong(issuedAtMs);
        body.writeLong(expiresAtMs);
        body.write(sha256(senderRaw));
        body.write(sha256(recipientRaw));
        body.flush();
        byte[] context=out.toByteArray();
        if(context.length!=142)
            throw new GeneralSecurityException("Bad NXC1 context");

        byte[] plaintext=NexoConnectPayloadCodec.encode(entityType,source,listEntries);
        byte[] cipher;
        try{
            if(plaintext.length==0||plaintext.length>MAX_PLAINTEXT)
                throw new GeneralSecurityException("Metadata too large for E2EE envelope");
            HybridEncrypt encryptor=candidateRecipientPublic.getPrimitive(
                    RegistryConfiguration.get(),HybridEncrypt.class);
            cipher=encryptor.encrypt(plaintext,context);
        }finally{
            Arrays.fill(plaintext,(byte)0);
        }
        if(cipher.length<1||cipher.length>MAX_CIPHER)
            throw new GeneralSecurityException("Ciphertext exceeds delivery limit");
        PublicKeySign signing=sender.signingPrivate().getPrimitive(
                RegistryConfiguration.get(),PublicKeySign.class);
        byte[] signature=signing.sign(signedBytes(context,cipher));
        if(signature.length!=64)
            throw new GeneralSecurityException("Bad sealed Ed25519 signature");

        // QR revocation/rotation while encrypting must invalidate the result.
        AndroidPeerTrustStore.PeerPin current=trustedPeers.findActive(recipientAccount);
        if(current==null||current.generation!=initial.generation||
                !MessageDigest.isEqual(current.signingPublicRaw(),initial.signingPublicRaw())||
                !MessageDigest.isEqual(current.encryptionPublicRaw(),recipientRaw))
            throw new SecurityException("Recipient trust changed during encryption");

        return new JSONObject()
                .put("deliveryId",deliveryId.toString())
                .put("clientRequestId",clientRequestId.toString())
                .put("recipientId",recipientAccount.toString())
                .put("recipientGeneration",initial.generation)
                .put("entityType",entityType)
                .put("context",b64(context))
                .put("ciphertext",b64(cipher))
                .put("signature",b64(signature));
    }

    private static int entityCode(String value){
        for(int i=0;i<ENTITY_NAMES.length;i++)
            if(ENTITY_NAMES[i].equals(value))return i+1;
        return 0;
    }
    private static void writeUuid(DataOutputStream out,UUID id)throws Exception{
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }
    private static byte[] signedBytes(byte[] context,byte[] cipher)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DataOutputStream bytes=new DataOutputStream(out);
        bytes.writeUTF(DOMAIN);
        bytes.writeInt(context.length);bytes.write(context);
        bytes.writeInt(cipher.length);bytes.write(cipher);
        bytes.flush();
        return out.toByteArray();
    }
    private static String b64(byte[] binary){
        return Base64.encodeToString(binary,Base64.URL_SAFE|
                Base64.NO_PADDING|Base64.NO_WRAP);
    }
    private static byte[] sha256(byte[] src)throws Exception{
        return MessageDigest.getInstance("SHA-256").digest(src);
    }
}
