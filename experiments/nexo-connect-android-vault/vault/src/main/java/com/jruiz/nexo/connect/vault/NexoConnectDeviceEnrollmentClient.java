package com.jruiz.nexo.connect.vault;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.Ed25519PublicKey;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

/**
 * Experimental enrollment with a SECOND, fresh email OTP consumed ON SERVER.
 * The OTP used to obtain the JWT session is already consumed and CANNOT be
 * reused to enroll. First registration and replacement both require a newly
 * requested email code.
 *
 * The server validates Auth JWT user and email, live Auth verifyOtp, signed
 * proof of new Ed25519 private key ownership, then service-only SQL RPC.
 *
 * No automatic retries: after network uncertainty user must inspect /status
 * and request another code; never resend the same OTP on a loop.
 */
public final class NexoConnectDeviceEnrollmentClient {
    private static final String PATH="/functions/v1/nexo-connect-gateway/device/enroll";
    private static final String CANONICAL_PATH="/nexo-connect/device/enroll";
    private static final int MAX_BYTES=2048;
    private final Transport transport;
    private final String publishableKey;
    private final NexoConnectEmailOtpClient.VerifiedSession session;
    private final UUID account;
    private final UUID newDevice;
    private final PublicKeySign signer;
    private final byte[] signingRaw,encryptionRaw;
    private final Clock clock;

    public NexoConnectDeviceEnrollmentClient(String projectUrl,String publishableKey,
            NexoConnectEmailOtpClient.VerifiedSession session,UUID account,
            UUID newDevice,KeysetHandle privateSigner,KeysetHandle privateHpke)
            throws Exception {
        this(new HttpsTransport(projectUrl),publishableKey,session,account,
                newDevice,privateSigner,privateHpke,System::currentTimeMillis);
    }

    // Package test seam. Never use a fake transport in production UI.
    NexoConnectDeviceEnrollmentClient(Transport transport,String publishableKey,
            NexoConnectEmailOtpClient.VerifiedSession session,UUID account,
            UUID newDevice,KeysetHandle privateSigner,KeysetHandle privateHpke,
            Clock clock)throws Exception {
        if(transport==null||publishableKey==null||publishableKey.length()<4||
                publishableKey.length()>4096||containsWhitespace(publishableKey)||
                session==null||account==null||newDevice==null||
                privateSigner==null||privateHpke==null||clock==null||
                privateSigner.size()!=1||privateHpke.size()!=1)
            throw new GeneralSecurityException("Missing verified enrollment identity");
        SignatureConfig.register();
        HybridConfig.register();
        KeysetHandle pubSign=privateSigner.getPublicKeysetHandle();
        KeysetHandle pubHpke=privateHpke.getPublicKeysetHandle();
        if(pubSign.size()!=1||pubHpke.size()!=1||
                !(pubSign.getAt(0).getKey() instanceof Ed25519PublicKey)||
                !(pubHpke.getAt(0).getKey() instanceof HpkePublicKey))
            throw new GeneralSecurityException("Unexpected enrollment key types");
        signingRaw=((Ed25519PublicKey)pubSign.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        encryptionRaw=((HpkePublicKey)pubHpke.getAt(0).getKey())
                .getPublicKeyBytes().toByteArray();
        if(signingRaw.length!=32||encryptionRaw.length!=32)
            throw new GeneralSecurityException("Enrollment public keys must be 32 bytes");
        this.signer=privateSigner.getPrimitive(
                RegistryConfiguration.get(),PublicKeySign.class);
        this.transport=transport;
        this.publishableKey=publishableKey;
        this.session=session;
        this.account=account;
        this.newDevice=newDevice;
        this.clock=clock;
    }

    /** Requires consent for explicit replacement; UI must show revocation warning. */
    public int enroll(String freshEmailOtp,boolean replace,boolean approved)
            throws Exception {
        if(!approved)throw new SecurityException("Explicit device enrollment consent needed");
        if(freshEmailOtp==null||!freshEmailOtp.matches("[0-9]{6,8}"))
            throw new GeneralSecurityException("Fresh OTP required");
        long now=clock.millis();
        String jwt=session.accessTokenFor(account,now);
        JSONObject payload=new JSONObject()
                .put("deviceId",newDevice.toString())
                .put("signingKey",b64(signingRaw))
                .put("encryptionKey",b64(encryptionRaw))
                .put("otp",freshEmailOtp)
                .put("replace",replace);
        byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
        if(body.length>MAX_BYTES)throw new GeneralSecurityException("Oversize enrollment");
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(body);
        String canonical="NEXO-CONNECT-ENROLL-V1\nPOST\n"+CANONICAL_PATH+"\n"+
                account+"\n"+now+"\n"+hex(sha)+"\n";
        byte[] signature=signer.sign(canonical.getBytes(StandardCharsets.UTF_8));
        if(signature==null||signature.length!=64)
            throw new GeneralSecurityException("Invalid new-device signature");
        Map<String,String> headers=new HashMap<>();
        headers.put("Authorization","Bearer "+jwt);
        headers.put("apikey",publishableKey);
        headers.put("Accept","application/json");
        headers.put("Content-Type","application/json; charset=utf-8");
        headers.put("Cache-Control","no-store");
        headers.put("x-nexo-issued-at",Long.toString(now));
        headers.put("x-nexo-enroll-signature",b64(signature));
        // This POST is intentionally never automatically retried.
        Response r=transport.post(PATH,body,Collections.unmodifiableMap(headers));
        if(r==null)throw new GeneralSecurityException("No enrollment response");
        if(r.status<200||r.status>=300)
            throw new EnrollError(r.status);
        if(r.body==null||r.body.length<2||r.body.length>MAX_BYTES)
            throw new GeneralSecurityException("Invalid enrollment response");
        JSONObject answer=new JSONObject(new String(r.body,StandardCharsets.UTF_8));
        Object generation=answer.opt("generation");
        if(!newDevice.toString().equals(answer.optString("deviceId",""))||
                !(generation instanceof Number)||
                ((Number)generation).doubleValue()!=((Number)generation).longValue()||
                ((Number)generation).longValue()<1||
                ((Number)generation).longValue()>Integer.MAX_VALUE||
                !answer.has("replacement")||answer.optBoolean("replacement",!replace)!=replace)
            throw new GeneralSecurityException("Unverified enrollment result");
        int g=((Number)generation).intValue();
        if((replace&&g<2)||(!replace&&g!=1))
            throw new GeneralSecurityException("Unexpected device generation");
        return g;
    }

    private static boolean containsWhitespace(String value){
        for(int i=0;i<value.length();i++)
            if(Character.isWhitespace(value.charAt(i)))return true;
        return false;
    }
    private static String b64(byte[] bytes){
        return android.util.Base64.encodeToString(bytes,android.util.Base64.URL_SAFE|
                android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
    }
    private static String hex(byte[] bytes){
        char[] chars=new char[bytes.length*2];
        char[] alphabet="0123456789abcdef".toCharArray();
        for(int i=0;i<bytes.length;i++){
            chars[2*i]=alphabet[(bytes[i]>>4)&15];
            chars[2*i+1]=alphabet[bytes[i]&15];
        }
        return new String(chars);
    }

    public static final class EnrollError extends Exception {
        public final int httpStatus;
        EnrollError(int status){
            super("Enrollment HTTP error ("+status+")");
            httpStatus=status;
        }
    }
    @FunctionalInterface interface Clock {long millis();}
    interface Transport {
        Response post(String path,byte[] body,Map<String,String> headers)
                throws Exception;
    }
    static final class Response {
        final int status;
        final byte[] body;
        Response(int status,byte[] body){
            this.status=status;this.body=body==null?null:body.clone();
        }
        static Response json(int code,JSONObject content){
            return new Response(code,content.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static final class HttpsTransport implements Transport{
        private final String origin;
        HttpsTransport(String projectUrl)throws GeneralSecurityException{
            try{
                URL url=new URL(projectUrl);String host=url.getHost();
                if(!"https".equals(url.getProtocol())||host==null||
                        !host.matches("[a-z0-9-]+\\.supabase\\.co")||
                        url.getPort()!=-1||url.getUserInfo()!=null||
                        url.getQuery()!=null||url.getRef()!=null||
                        !(url.getPath().isEmpty()||url.getPath().equals("/")))
                    throw new GeneralSecurityException("Invalid Supabase HTTPS origin");
                origin="https://"+host;
            }catch(Exception ex){
                throw new GeneralSecurityException("Invalid Supabase HTTPS origin",ex);
            }
        }
        @Override public Response post(String path,byte[] body,
                Map<String,String> headers)throws Exception{
            if(!PATH.equals(path)||body==null||body.length>MAX_BYTES)
                throw new GeneralSecurityException("Invalid enrollment network request");
            HttpsURLConnection conn=null;
            try{
                conn=(HttpsURLConnection)new URL(origin+path).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(false);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                for(Map.Entry<String,String> entry:headers.entrySet())
                    conn.setRequestProperty(entry.getKey(),entry.getValue());
                try(OutputStream out=conn.getOutputStream()){out.write(body);}
                int code=conn.getResponseCode();
                if(code<200||code>=300)return new Response(code,null);
                try(InputStream in=conn.getInputStream();
                    ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[4096];int n;
                    while((n=in.read(buf))!=-1){
                        if(n==0)continue;
                        if(out.size()+n>MAX_BYTES)
                            throw new GeneralSecurityException("Large enrollment response");
                        out.write(buf,0,n);
                    }
                    return new Response(code,out.toByteArray());
                }
            }finally{if(conn!=null)conn.disconnect();}
        }
    }
}
