package com.jruiz.nexo.connect.vault;

import android.content.Context;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/**
 * LAB ONLY. Generates one Ed25519 + X25519-HPKE identity per verified account
 * and stores the keysets BEFORE any network enrollment operation. The private
 * keysets are never written unencrypted to disk: TinkProtoKeysetFormat is used
 * only to pass transient byte[] into the AES-GCM AndroidIdentityVault.
 *
 * Device ID is deterministically derived from unpredictable PUBLIC Ed25519
 * bytes and domain separation, so a restarted app has the same device UUID.
 * It is an identifier, NOT authentication; server still verifies signatures.
 *
 * WARNING: same-phone key rotation is not supported. Existing identities
 * are never overwritten silently or replaced on a network failure.
 */
public final class NexoConnectLocalPhoneIdentity {
    private static final Object LOCK=new Object();
    private static final byte[] DOMAIN=
            "NEXO-CONNECT-DEVICE-ID-V1".getBytes(StandardCharsets.US_ASCII);
    private final UUID accountId;
    private final UUID deviceId;
    private final KeysetHandle signer,hpke;

    private NexoConnectLocalPhoneIdentity(UUID account,KeysetHandle signer,
            KeysetHandle hpke) throws Exception {
        if(account==null||signer==null||hpke==null||
                signer.size()!=1||hpke.size()!=1)
            throw new GeneralSecurityException("Incomplete device identity");
        KeysetHandle signingPublic=signer.getPublicKeysetHandle();
        KeysetHandle encryptionPublic=hpke.getPublicKeysetHandle();
        if(signingPublic.size()!=1||encryptionPublic.size()!=1||
                !(signingPublic.getAt(0).getKey() instanceof Ed25519PublicKey)||
                !(encryptionPublic.getAt(0).getKey() instanceof HpkePublicKey))
            throw new GeneralSecurityException("Incompatible persisted device keys");
        byte[] raw=((Ed25519PublicKey)signingPublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        byte[] enc=((HpkePublicKey)encryptionPublic.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        if(raw.length!=32||enc.length!=32)
            throw new GeneralSecurityException("Invalid device public key lengths");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        digest.update(DOMAIN);
        digest.update(account.toString().getBytes(StandardCharsets.US_ASCII));
        byte[] hash=digest.digest(raw);
        // Deterministic RFC-4122-shaped identifier; entropy comes from the
        // randomly generated Ed25519 public key. Not a security credential.
        hash[6]=(byte)((hash[6]&0x0f)|0x40);
        hash[8]=(byte)((hash[8]&0x3f)|0x80);
        ByteBuffer id=ByteBuffer.wrap(hash);
        this.deviceId=new UUID(id.getLong(),id.getLong());
        this.accountId=account;this.signer=signer;this.hpke=hpke;
        Arrays.fill(hash,(byte)0);
    }

    public static NexoConnectLocalPhoneIdentity openOrCreate(
            Context androidContext,UUID verifiedAccount)throws Exception {
        if(androidContext==null||verifiedAccount==null)
            throw new GeneralSecurityException("Verified account required");
        synchronized(LOCK){
            SignatureConfig.register();HybridConfig.register();
            AndroidIdentityVault vault=new AndroidIdentityVault(
                    androidContext,verifiedAccount);
            if(vault.exists()){
                try(AndroidIdentityVault.IdentityBytes identity=vault.restore()){
                    byte[] signing=identity.signingKeyset();
                    byte[] hpkeBytes=identity.hpkeKeyset();
                    try {
                        return new NexoConnectLocalPhoneIdentity(verifiedAccount,
                            TinkProtoKeysetFormat.parseKeyset(
                                signing,InsecureSecretKeyAccess.get()),
                            TinkProtoKeysetFormat.parseKeyset(
                                hpkeBytes,InsecureSecretKeyAccess.get()));
                    } finally {
                        Arrays.fill(signing,(byte)0);
                        Arrays.fill(hpkeBytes,(byte)0);
                    }
                }
            }
            // Both keysets must be safely durable BEFORE first enrollment POST.
            KeysetHandle signing=KeysetHandle.generateNew(
                    SignatureKeyTemplates.ED25519WithRawOutput);
            KeysetHandle encryption=KeysetHandle.generateNew(
                    HpkeParameters.builder()
                        .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                        .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                        .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                        .setVariant(HpkeParameters.Variant.NO_PREFIX).build());
            byte[] privateSign=TinkProtoKeysetFormat.serializeKeyset(
                    signing,InsecureSecretKeyAccess.get());
            byte[] privateHpke=TinkProtoKeysetFormat.serializeKeyset(
                    encryption,InsecureSecretKeyAccess.get());
            try {
                vault.saveNew(privateHpke,privateSign);
            } finally {
                Arrays.fill(privateSign,(byte)0);
                Arrays.fill(privateHpke,(byte)0);
            }
            return new NexoConnectLocalPhoneIdentity(
                    verifiedAccount,signing,encryption);
        }
    }

    public UUID accountId(){return accountId;}
    public UUID deviceId(){return deviceId;}
    /** Private material remains in Tink KeysetHandle, not UI text/QR. */
    KeysetHandle signingPrivate(){return signer;}
    KeysetHandle hpkePrivate(){return hpke;}
}
