package com.jruiz.nexo.connect.vault;

import android.util.Base64;

import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/**
 * Versioned one-use invitation QR = verified public peer keys + 256-bit secret.
 * Server stores ONLY SHA-256(secret), never the secret itself.
 *
 * The QR alone does not prove the named human/account. A separate human
 * verification of BOTH complete fingerprints is mandatory; a signed contact
 * acceptance then binds the verified locally pinned key to the server account.
 */
public final class NexoConnectInvitationQr {
    public static final String FORMAT="nexo-connect-invite";
    public static final int VERSION=1;
    private final AndroidPeerTrustStore.PeerPin creator;
    private final byte[] token;

    private NexoConnectInvitationQr(AndroidPeerTrustStore.PeerPin pin,byte[] secret)
            throws GeneralSecurityException {
        if(pin==null||!pin.active||secret==null||secret.length!=32)
            throw new GeneralSecurityException("Invalid invite QR contents");
        creator=pin;
        token=secret.clone();
    }
    /** Called with locally registered public keys and crypto-secure random 32 B. */
    public static NexoConnectInvitationQr create(
            AndroidPeerTrustStore.PeerPin pin,byte[] token)throws Exception{
        return new NexoConnectInvitationQr(pin,token);
    }
    public static NexoConnectInvitationQr parse(String encoded)throws Exception{
        if(encoded==null||encoded.length()>1024||encoded.length()<150)
            throw new GeneralSecurityException("Invalid invite QR size");
        JSONObject raw=new JSONObject(encoded);
        if(raw.length()!=7||!FORMAT.equals(raw.optString("format",""))||
                raw.optInt("version",-1)!=VERSION)
            throw new GeneralSecurityException("Not a Nexo Connect invitation");
        JSONObject peer=new JSONObject()
                .put("format","nexo-connect-peer")
                .put("version",1)
                .put("peerId",raw.optString("creatorId",""))
                .put("generation",raw.opt("creatorGeneration"))
                .put("signingKey",raw.optString("signingKey",""))
                .put("encryptionKey",raw.optString("encryptionKey",""));
        AndroidPeerTrustStore.PeerPin creator=AndroidPeerTrustStore.parseQr(
                peer.toString());
        String encodedToken=raw.optString("token","");
        if(!encodedToken.matches("[A-Za-z0-9_-]{43}"))
            throw new GeneralSecurityException("Invalid invite secret");
        byte[] secret;
        try{
            secret=Base64.decode(encodedToken,
                Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
        }catch(IllegalArgumentException ex){
            throw new GeneralSecurityException("Invalid invite encoding",ex);
        }
        if(secret.length!=32||!b64(secret).equals(encodedToken))
            throw new GeneralSecurityException("Noncanonical invite token");
        return new NexoConnectInvitationQr(creator,secret);
    }

    public String encode()throws Exception{
        return new JSONObject()
                .put("format",FORMAT).put("version",VERSION)
                .put("creatorId",creator.peerId.toString())
                .put("creatorGeneration",creator.generation)
                .put("signingKey",b64(creator.signingPublicRaw()))
                .put("encryptionKey",b64(creator.encryptionPublicRaw()))
                .put("token",b64(token)).toString();
    }
    public String publicPeerQr()throws Exception{
        return AndroidPeerTrustStore.encodeQr(creator);
    }
    public UUID creatorAccount(){return creator.peerId;}
    public int creatorGeneration(){return creator.generation;}
    public String signingFingerprint()throws Exception{
        return creator.signingFingerprint();
    }
    public String encryptionFingerprint()throws Exception{
        return creator.encryptionFingerprint();
    }
    public byte[] tokenSha256()throws GeneralSecurityException{
        return MessageDigest.getInstance("SHA-256").digest(token);
    }

    public boolean matchesPinnedPeer(AndroidPeerTrustStore.PeerPin saved)
            throws Exception {
        return saved!=null&&saved.active&&saved.peerId.equals(creator.peerId)&&
                saved.generation==creator.generation&&
                MessageDigest.isEqual(saved.signingPublicRaw(),
                    creator.signingPublicRaw())&&
                MessageDigest.isEqual(saved.encryptionPublicRaw(),
                    creator.encryptionPublicRaw());
    }
    private static String b64(byte[] bytes){
        return Base64.encodeToString(bytes,
                Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
    }
}
