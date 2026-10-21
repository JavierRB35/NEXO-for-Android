package com.jruiz.nexo.connect.vault;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.protobuf.ByteString;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.Keyset;
import com.google.crypto.tink.proto.KeyStatusType;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.proto.HpkeParams;
import com.google.crypto.tink.proto.HpkeKem;
import com.google.crypto.tink.proto.HpkeKdf;
import com.google.crypto.tink.proto.HpkeAead;
import java.security.GeneralSecurityException;

/** Reconstruct PUBLIC Tink keysets only from locally QR-pinned raw 32-byte keys.
 * NO_PREFIX HPKE keys avoid unknowable Tink-internal key identifiers.
 * No private keysets and no server-provided key substitutions.
 */
public final class NexoConnectPinnedKeysets {
    private NexoConnectPinnedKeysets(){}
    public static KeysetHandle senderVerification(
            AndroidPeerTrustStore.PeerPin pin)throws Exception{
        if(pin==null||!pin.active)throw new GeneralSecurityException("Unverified peer");
        com.google.crypto.tink.proto.Ed25519PublicKey key=
                com.google.crypto.tink.proto.Ed25519PublicKey.newBuilder()
                .setVersion(0).setKeyValue(ByteString.copyFrom(pin.signingPublicRaw()))
                .build();
        return parse("type.googleapis.com/google.crypto.tink.Ed25519PublicKey",
                key.toByteString());
    }
    public static KeysetHandle recipientEncryption(
            AndroidPeerTrustStore.PeerPin pin)throws Exception{
        if(pin==null||!pin.active)throw new GeneralSecurityException("Unverified peer");
        HpkeParams params=HpkeParams.newBuilder()
                .setKem(HpkeKem.DHKEM_X25519_HKDF_SHA256)
                .setKdf(HpkeKdf.HKDF_SHA256)
                .setAead(HpkeAead.AES_256_GCM).build();
        com.google.crypto.tink.proto.HpkePublicKey key=
                com.google.crypto.tink.proto.HpkePublicKey.newBuilder()
                .setVersion(0).setParams(params)
                .setPublicKey(ByteString.copyFrom(pin.encryptionPublicRaw()))
                .build();
        return parse("type.googleapis.com/google.crypto.tink.HpkePublicKey",
                key.toByteString());
    }
    private static KeysetHandle parse(String typeUrl,ByteString publicProto)
            throws Exception{
        KeyData key=KeyData.newBuilder()
                .setTypeUrl(typeUrl).setValue(publicProto)
                .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PUBLIC)
                .build();
        Keyset.Key entry=Keyset.Key.newBuilder().setKeyData(key)
                .setStatus(KeyStatusType.ENABLED).setKeyId(1871341029)
                .setOutputPrefixType(OutputPrefixType.RAW).build();
        Keyset set=Keyset.newBuilder().addKey(entry)
                .setPrimaryKeyId(1871341029).build();
        return TinkProtoKeysetFormat.parseKeyset(
                set.toByteArray(), InsecureSecretKeyAccess.get());
    }
}
