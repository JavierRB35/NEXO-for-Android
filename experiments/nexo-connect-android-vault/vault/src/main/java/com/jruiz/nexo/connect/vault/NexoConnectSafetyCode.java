package com.jruiz.nexo.connect.vault;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * One 64-bit human-verifiable code for a full PUBLIC account+device identity.
 * Two different channels MUST be used: the QR/invitation and a direct call or
 * in-person comparison with the person who owns the second device.
 *
 * Both full public keys still remain pinned and verified server-side.
 * Never derive the independent input from the scanned QR or the server.
 */
public final class NexoConnectSafetyCode {
    private static final byte[] DOMAIN =
            "NEXO-CONNECT-IDENTITY-SAFETY-CODE-V1".getBytes(StandardCharsets.US_ASCII);

    private NexoConnectSafetyCode(){}

    public static String forPeer(AndroidPeerTrustStore.PeerPin peer)
            throws GeneralSecurityException {
        if(peer==null || !peer.active || peer.generation<=0)
            throw new GeneralSecurityException("Invalid peer identity");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        digest.update(DOMAIN);
        digest.update((byte)0);
        digest.update(peer.peerId.toString().getBytes(StandardCharsets.US_ASCII));
        digest.update((byte)0);
        digest.update(ByteBuffer.allocate(4).putInt(peer.generation).array());
        digest.update(peer.signingPublicRaw());
        digest.update(peer.encryptionPublicRaw());
        byte[] hash=digest.digest();
        char[] result=new char[16];
        final char[] alphabet="0123456789ABCDEF".toCharArray();
        for(int i=0;i<8;i++){
            result[2*i]=alphabet[(hash[i]>>>4)&0xf];
            result[2*i+1]=alphabet[hash[i]&0xf];
        }
        java.util.Arrays.fill(hash,(byte)0);
        return new String(result);
    }

    public static String normalize(String input)throws GeneralSecurityException {
        if(input==null||input.length()>40)
            throw new GeneralSecurityException("Invalid safety code");
        String compact=input.toUpperCase(Locale.ROOT)
                .replace(" ","").replace("-","").replace(":","");
        if(!compact.matches("[0-9A-F]{16}"))
            throw new GeneralSecurityException("Invalid safety code");
        return compact;
    }

    public static void verify(AndroidPeerTrustStore.PeerPin peer,
            String independentCode,boolean independentChannel,boolean consent)
            throws GeneralSecurityException{
        if(!independentChannel || !consent)
            throw new GeneralSecurityException("Independent verification and consent required");
        String checked=normalize(independentCode);
        String expected=forPeer(peer);
        if(!MessageDigest.isEqual(
                checked.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII)))
            throw new GeneralSecurityException("Safety code mismatch");
    }

    public static String grouped(String compact) {
        if(compact==null||compact.length()!=16)
            throw new IllegalArgumentException("Invalid safety code");
        return compact.substring(0,4)+"  "+compact.substring(4,8)+"  "
                +compact.substring(8,12)+"  "+compact.substring(12,16);
    }
}
