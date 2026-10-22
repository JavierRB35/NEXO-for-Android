package com.jruiz.nexo.connect.poc;

import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureKeyTemplates;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Fase 0: demostración AISLADA de cifrado y autenticidad con Google Tink.
 *
 * NO es código de producción. No implementa verificación de claves de contactos,
 * Android Keystore, almacenamiento, antirrepetición, archivos por fragmentos,
 * identidad del servidor ni manejo de sesiones. Nunca persistir claves en claro.
 */
public final class CryptoProof {
    private static final String DOMAIN = "NEXO-CONNECT-CRYPTO-POC-1";
    private static final int MAX_CONTEXT = 4096;
    private static final int MAX_CIPHER = 12 * 1024 * 1024;

    private CryptoProof() {}

    public static KeysetHandle newRecipientKeys() throws GeneralSecurityException {
        HybridConfig.register();
        return KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK)
                .build());
    }

    public static KeysetHandle newSenderSigningKeys() throws GeneralSecurityException {
        SignatureConfig.register();
        return KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
    }

    /** Formato raw Ed25519 de 32 bytes para verificación interoperable en Edge. */
    public static byte[] rawSigningPublicKey(KeysetHandle signingPrivate)
            throws GeneralSecurityException {
        Object key = signingPrivate.getPublicKeysetHandle().getAt(0).getKey();
        if (!(key instanceof Ed25519PublicKey))
            throw new GeneralSecurityException("Clave de firma inesperada");
        byte[] raw = ((Ed25519PublicKey)key).getPublicKeyBytes().toByteArray();
        if (raw.length != 32) throw new GeneralSecurityException("Ed25519 public key length");
        return raw;
    }

    /** Se comparte solamente el handle público, nunca el privado. */
    public static KeysetHandle publicKeys(KeysetHandle privateKeyset)
            throws GeneralSecurityException {
        return privateKeyset.getPublicKeysetHandle();
    }

    /**
     * context debe codificar de forma canónica version, identificador del envío,
     * remitente, destinatario, tipo y generación de clave. Esta prueba NO construye
     * dicho contexto, tarea pendiente de la implementación final.
     */
    public static SealedMessage seal(
            KeysetHandle senderSigningPrivate, KeysetHandle recipientPublic,
            byte[] plain, byte[] context) throws GeneralSecurityException {
        checkInput(plain, context);
        HybridEncrypt encryptor = recipientPublic.getPrimitive(
                RegistryConfiguration.get(), HybridEncrypt.class);
        byte[] cipher = encryptor.encrypt(plain, context);
        if (cipher.length > MAX_CIPHER) throw new GeneralSecurityException("Cifrado excesivo");
        byte[] signedBytes = canonicalSignedBytes(context, cipher);
        PublicKeySign signer = senderSigningPrivate.getPrimitive(
                RegistryConfiguration.get(), PublicKeySign.class);
        return new SealedMessage(context, cipher, signer.sign(signedBytes));
    }

    /**
     * Verificar firma y contexto esperado ANTES de descifrar.
     * senderPublic debe estar previamente ligado por otro protocolo a un contacto fiable.
     */
    public static byte[] open(
            KeysetHandle recipientPrivate, KeysetHandle trustedSenderPublic,
            SealedMessage envelope, byte[] expectedContext)
            throws GeneralSecurityException {
        if (envelope == null || expectedContext == null) {
            throw new GeneralSecurityException("Sobre o contexto ausente");
        }
        byte[] context = envelope.context();
        byte[] cipher = envelope.ciphertext();
        if (context.length == 0 || context.length > MAX_CONTEXT
                || cipher.length == 0 || cipher.length > MAX_CIPHER
                || !MessageDigest.isEqual(expectedContext, context)) {
            throw new GeneralSecurityException("Contexto incompatible");
        }
        PublicKeyVerify verifier = trustedSenderPublic.getPrimitive(
                RegistryConfiguration.get(), PublicKeyVerify.class);
        verifier.verify(envelope.signature(), canonicalSignedBytes(context, cipher));
        HybridDecrypt decryptor = recipientPrivate.getPrimitive(
                RegistryConfiguration.get(), HybridDecrypt.class);
        return decryptor.decrypt(cipher, context);
    }

    private static void checkInput(byte[] plain, byte[] context)
            throws GeneralSecurityException {
        if (plain == null || context == null || plain.length > 10 * 1024 * 1024
                || context.length == 0 || context.length > MAX_CONTEXT) {
            throw new GeneralSecurityException("Datos o contexto fuera de límites");
        }
    }

    /** Representación binaria determinista y separada por dominio. */
    private static byte[] canonicalSignedBytes(byte[] context, byte[] cipher)
            throws GeneralSecurityException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(DOMAIN);
            data.writeInt(context.length);
            data.write(context);
            data.writeInt(cipher.length);
            data.write(cipher);
            data.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new GeneralSecurityException("No se pudo crear el mensaje firmado", e);
        }
    }

    /** Contenedor en memoria, sin serialización ni escritura a Descargas. */
    public static final class SealedMessage {
        private final byte[] context;
        private final byte[] ciphertext;
        private final byte[] signature;

        public SealedMessage(byte[] context, byte[] ciphertext, byte[] signature) {
            if (context == null || ciphertext == null || signature == null)
                throw new IllegalArgumentException("Campos obligatorios");
            this.context = Arrays.copyOf(context, context.length);
            this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            this.signature = Arrays.copyOf(signature, signature.length);
        }
        public byte[] context() { return Arrays.copyOf(context, context.length); }
        public byte[] ciphertext() { return Arrays.copyOf(ciphertext, ciphertext.length); }
        public byte[] signature() { return Arrays.copyOf(signature, signature.length); }
    }
}
