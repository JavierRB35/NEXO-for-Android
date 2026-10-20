package com.jruiz.nexo.connect.vault;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.signature.Ed25519PublicKey;
import com.google.crypto.tink.signature.SignatureConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;

/**
 * Experimental isolated HTTPS client for Nexo Connect signed actions.
 *
 * Trust prerequisites:
 * - bearer belongs to the verified Supabase Auth session of accountId
 * - signer is the Ed25519 private key of that account's ACTIVE phone
 * - a successful OTP enrollment previously bound phoneId and generation
 *
 * No persistent tokens, private keys, plaintext, or packet logs. Run from a
 * worker thread; NO networking on UI. Nexo stable build does not use this.
 */
public final class NexoConnectSignedGatewayClient {
    private static final int CONTROL_BYTES = 2048;
    private static final int INLINE_BYTES = 768 * 1024;
    private static final int RESPONSE_BYTES = 1152 * 1024;
    private static final long CLOCK_SKEW_MS = 90_000L;
    private static final String PATH_PREFIX = "/functions/v1/nexo-connect-gateway/";
    private static final String CANON_PREFIX = "/nexo-connect/";
    private static final String DOMAIN = "NEXO-CONNECT-PROOF-V1";
    private static final String HEX = "0123456789abcdef";
    private static final String[] ALLOWED_ACTIONS = {
        "contact/invite","contact/accept","contact/permissions",
        "contact/unlink","key/verify","delivery/send",
        "delivery/resolve","delivery/inbox","delivery/open"
    };
    private final UUID accountId;
    private final UUID phoneId;
    private final int generation;
    private final String accessToken;
    private final String publishableKey;
    private final PublicKeySign signer;
    private final byte[] localSigningFingerprint;
    private final Transport transport;
    private final Clock clock;

    /** No field here grants backend authority; backend rechecks all claims. */
    public NexoConnectSignedGatewayClient(String supabaseProjectUrl,
            String publicApiKey, String verifiedAccessToken,
            UUID verifiedAccountId, UUID registeredPhoneId,
            int registeredGeneration, KeysetHandle localSigningPrivate)
            throws GeneralSecurityException {
        this(verifiedAccountId,registeredPhoneId,registeredGeneration,
                verifiedAccessToken,publicApiKey,localSigningPrivate,
                new HttpsTransport(supabaseProjectUrl),
                System::currentTimeMillis);
    }

    // Test seam: production callers cannot change the TLS/host enforcement.
    NexoConnectSignedGatewayClient(UUID accountId,UUID phoneId,int generation,
            String accessToken,String publishableKey,KeysetHandle localSigningPrivate,
            Transport transport,Clock clock) throws GeneralSecurityException {
        if(accountId==null||phoneId==null||generation<=0||
                transport==null||clock==null)
            throw new GeneralSecurityException("Missing verified phone identity");
        if(accessToken==null||accessToken.isEmpty()||accessToken.length()>16384||
                hasWhitespace(accessToken)||publishableKey==null||
                publishableKey.isEmpty()||publishableKey.length()>4096||
                hasWhitespace(publishableKey))
            throw new GeneralSecurityException("Invalid session configuration");
        if(localSigningPrivate==null || localSigningPrivate.size()!=1)
            throw new GeneralSecurityException("Missing unique signer");
        SignatureConfig.register();
        KeysetHandle publicHandle=localSigningPrivate.getPublicKeysetHandle();
        if(publicHandle.size()!=1||
                !(publicHandle.getAt(0).getKey() instanceof Ed25519PublicKey))
            throw new GeneralSecurityException("Expected Ed25519 signing identity");
        this.accountId=accountId;
        this.phoneId=phoneId;
        this.generation=generation;
        this.accessToken=accessToken;
        this.publishableKey=publishableKey;
        this.signer=localSigningPrivate.getPrimitive(
                RegistryConfiguration.get(),PublicKeySign.class);
        this.localSigningFingerprint=MessageDigest.getInstance("SHA-256").digest(
                ((Ed25519PublicKey)publicHandle.getAt(0).getKey())
                        .getPublicKeyBytes().toByteArray());
        this.transport=transport;
        this.clock=clock;
    }

    /** Only metadata, up to 20 pending deliveries. No plaintext. */
    public JSONArray inbox(int limit,UUID afterId) throws Exception {
        if(limit<1||limit>20)throw new IllegalArgumentException("Invalid inbox limit");
        JSONObject json=new JSONObject().put("limit",limit);
        if(afterId!=null)json.put("afterId",afterId.toString());
        JSONObject response=execute("delivery/inbox",json);
        JSONArray items=response.optJSONArray("items");
        if(items==null||items.length()>limit)
            throw new GeneralSecurityException("Invalid inbox page");
        return items;
    }

    /** Retrieves one opaque encrypted envelope; this method DOES NOT decrypt. */
    public JSONObject open(UUID deliveryId) throws Exception {
        if(deliveryId==null)throw new IllegalArgumentException("Missing delivery ID");
        JSONObject result=execute("delivery/open",new JSONObject()
                .put("deliveryId",deliveryId.toString()));
        if(!deliveryId.toString().equals(result.optString("deliveryId","")))
            throw new GeneralSecurityException("Unexpected delivery ID");
        // Always pass to NexoConnectInboxProcessor with a LOCALLY pinned key
        // before making any write to the native Nexo SQLite database.
        return result;
    }

    /**
     * Use target=accepted ONLY after NexoConnectAtomicImporter COMMIT.
     * This method performs NO local database write or implicit ACK retry.
     */
    public String resolve(UUID deliveryId,String target) throws Exception {
        if(deliveryId==null||target==null||!(target.equals("accepted")||
                target.equals("rejected")||target.equals("cancelled")))
            throw new IllegalArgumentException("Invalid resolution");
        JSONObject result=execute("delivery/resolve",new JSONObject()
                .put("deliveryId",deliveryId.toString()).put("target",target));
        if(!deliveryId.toString().equals(result.optString("deliveryId","")))
            throw new GeneralSecurityException("Wrong resolution ID");
        String status=result.optString("status","");
        if(!(status.equals("accepted")||status.equals("rejected")||
                status.equals("cancelled")||status.equals("expired")))
            throw new GeneralSecurityException("Invalid resolution status");
        return status;
    }

    /**
     * Low-level signed ciphertext send. Caller MUST first construct+sign an
     * authentic Tink NXC1 envelope for this sender and pinned recipient.
     * Backend rejects any invalid E2EE signature, context or peer trust.
     */
    public UUID send(JSONObject sealedEnvelope) throws Exception {
        validateLocalSealedEnvelope(sealedEnvelope);
        JSONObject answer=execute("delivery/send",sealedEnvelope);
        String expected=sealedEnvelope.optString("deliveryId","");
        if(!expected.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                ||!expected.equals(answer.optString("deliveryId",""))
                ||!"pending".equals(answer.optString("status","")))
            throw new GeneralSecurityException("Invalid send confirmation");
        return UUID.fromString(expected);
    }

    /**
     * Two POSTs: fetch action-specific one-use challenge, then sign bytes of
     * the exact JSON body sent. No automatic retry of an uncertain POST.
     */

    /**
     * Prevent accidentally posting plaintext or unstructured JSON as a
     * delivery. Server independently checks Ed25519 signature, QR approvals,
     * recipient encryption fingerprint, permissions and idempotency.
     */
    private void validateLocalSealedEnvelope(JSONObject data) throws Exception {
        if(data==null || data.length()!=8)
            throw new GeneralSecurityException("Only sealed delivery fields allowed");
        for(String field:new String[]{"deliveryId","clientRequestId","recipientId",
                "recipientGeneration","entityType","context","ciphertext","signature"})
            if(!data.has(field)||data.isNull(field))
                throw new GeneralSecurityException("Incomplete encrypted envelope");
        String delivery=data.optString("deliveryId","");
        String request=data.optString("clientRequestId","");
        String recipient=data.optString("recipientId","");
        String entity=data.optString("entityType","");
        if(!isUuid(delivery)||!isUuid(request)||!isUuid(recipient)||
                recipient.equals(accountId.toString()))
            throw new GeneralSecurityException("Invalid envelope routing");
        int code=0;
        String[] types={"appointment","note","list","journal","book","movie","document"};
        for(int i=0;i<types.length;i++)if(types[i].equals(entity))code=i+1;
        if(code==0)throw new GeneralSecurityException("Unknown encrypted entity");
        Object rawGeneration=data.opt("recipientGeneration");
        if(!(rawGeneration instanceof Number))
            throw new GeneralSecurityException("Invalid peer generation");
        long peerGen=((Number)rawGeneration).longValue();
        if(peerGen<=0||peerGen>Integer.MAX_VALUE||
                ((Number)rawGeneration).doubleValue()!=peerGen)
            throw new GeneralSecurityException("Invalid peer generation");

        byte[] context=base64Url(data.optString("context",""),142,142);
        byte[] cipher=base64Url(data.optString("ciphertext",""),1,512*1024);
        base64Url(data.optString("signature",""),64,64);
        ByteBuffer v=ByteBuffer.wrap(context);
        if(v.getInt(0)!=0x4e584331||context[4]!=1||
                !new UUID(v.getLong(5),v.getLong(13)).toString().equals(delivery)||
                !new UUID(v.getLong(21),v.getLong(29)).equals(accountId)||
                !new UUID(v.getLong(37),v.getLong(45)).toString().equals(recipient)||
                (context[53]&255)!=code || v.getInt(54)!=generation ||
                v.getInt(58)!=peerGen ||
                !MessageDigest.isEqual(localSigningFingerprint,
                        java.util.Arrays.copyOfRange(context,78,110)))
            throw new GeneralSecurityException("Unsigned or mismatched context");
        long issued=v.getLong(62),expires=v.getLong(70),now=clock.millis();
        if(issued<=0||expires<=now||expires<=issued||
                expires-issued>30L*24*60*60*1000||
                Math.abs(issued-now)>5L*60*1000)
            throw new GeneralSecurityException("Invalid or stale expiry");
        // Byte arrays above are opaque; ciphertext MUST remain encrypted.
        // Ed25519 over the full envelope is verified by the backend.
    }

    private static byte[] base64Url(String text,int min,int max)
            throws GeneralSecurityException {
        if(text==null||text.isEmpty()||
                text.length()>(max*4L/3+4)||!text.matches("[A-Za-z0-9_-]+"))
            throw new GeneralSecurityException("Invalid canonical binary encoding");
        try{
            byte[] decoded=android.util.Base64.decode(text,
                    android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|
                    android.util.Base64.NO_PADDING);
            if(decoded.length<min||decoded.length>max||
                    !encodeBase64Url(decoded).equals(text))
                throw new GeneralSecurityException("Invalid binary length or encoding");
            return decoded;
        }catch(IllegalArgumentException ex){
            throw new GeneralSecurityException("Invalid binary encoding",ex);
        }
    }

    private JSONObject execute(String action,JSONObject payload) throws Exception {
        if(!allowed(action) || payload==null)
            throw new IllegalArgumentException("Unsupported signed action");
        String nonce=requestNonce(action);
        long issuedAt=clock.millis();
        if(issuedAt<=0)throw new GeneralSecurityException("Invalid phone time");
        byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
        int max=action.equals("delivery/send")?INLINE_BYTES:CONTROL_BYTES;
        if(body.length==0||body.length>max)
            throw new IllegalArgumentException("Oversize signed request");
        byte[] canonical=canonicalRequest(action,nonce,issuedAt,body);
        byte[] signature=signer.sign(canonical);
        if(signature==null||signature.length!=64)
            throw new GeneralSecurityException("Invalid Ed25519 signature format");
        Map<String,String> headers=baseHeaders();
        headers.put("x-nexo-device",phoneId.toString());
        headers.put("x-nexo-generation",Integer.toString(generation));
        headers.put("x-nexo-challenge",nonce);
        headers.put("x-nexo-issued-at",Long.toString(issuedAt));
        headers.put("x-nexo-signature",encodeBase64Url(signature));
        return success(transport.post(PATH_PREFIX+action,body,headers),RESPONSE_BYTES);
    }

    private String requestNonce(String action) throws Exception {
        JSONObject payload=new JSONObject()
                .put("action",action)
                .put("deviceId",phoneId.toString())
                .put("generation",generation);
        TransportResponse res=transport.post(PATH_PREFIX+"challenge",
                payload.toString().getBytes(StandardCharsets.UTF_8),baseHeaders());
        JSONObject challenge=success(res,CONTROL_BYTES);
        String nonce=challenge.optString("challengeId","");
        if(!isUuid(nonce)||challenge.optInt("expiresInSeconds",0)<1||
                challenge.optInt("expiresInSeconds",0)>120)
            throw new GeneralSecurityException("Invalid or expired challenge");
        return nonce;
    }

    private Map<String,String> baseHeaders() {
        HashMap<String,String> h=new HashMap<>();
        h.put("Content-Type","application/json; charset=utf-8");
        h.put("Accept","application/json");
        h.put("Cache-Control","no-store");
        h.put("Authorization","Bearer "+accessToken);
        h.put("apikey",publishableKey);
        return h;
    }

    private byte[] canonicalRequest(String action,String nonce,long issued,byte[] body)
            throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(body);
        String canonical=DOMAIN+"\nPOST\n"+CANON_PREFIX+action+"\n"+
                accountId+"\n"+phoneId+"\n"+generation+"\n"+nonce+"\n"+
                issued+"\n"+hex(digest)+"\n";
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject success(TransportResponse response,int max)
            throws Exception {
        if(response==null)throw new GeneralSecurityException("Missing server response");
        if(response.status<200||response.status>299)
            throw new TransportException(response.status);
        if(response.body==null||response.body.length<2||response.body.length>max)
            throw new GeneralSecurityException("Unexpected response size");
        JSONObject result=new JSONObject(new String(response.body,StandardCharsets.UTF_8));
        if(result.has("error"))throw new GeneralSecurityException("Server rejected request");
        return result;
    }
    private static String hex(byte[] bytes) {
        char[] chars=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){
            chars[i*2]=HEX.charAt((bytes[i]>>>4)&15);
            chars[i*2+1]=HEX.charAt(bytes[i]&15);
        }
        return new String(chars);
    }
    private static String encodeBase64Url(byte[] data) {
        return android.util.Base64.encodeToString(data,android.util.Base64.URL_SAFE
                |android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);
    }
    private static boolean isUuid(String s) {
        return s!=null&&s.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
    private static boolean allowed(String a) {
        for(String value:ALLOWED_ACTIONS)if(value.equals(a))return true;
        return false;
    }
    private static boolean hasWhitespace(String s) {
        for(int i=0;i<s.length();i++)if(Character.isWhitespace(s.charAt(i)))return true;
        return false;
    }

    @FunctionalInterface interface Clock { long millis(); }
    interface Transport {
        TransportResponse post(String path,byte[] bytes,Map<String,String> headers)
                throws Exception;
    }
    static final class TransportResponse {
        final int status;
        final byte[] body;
        TransportResponse(int status,byte[] body) {
            this.status=status;
            this.body=body==null?null:body.clone();
        }
        static TransportResponse json(int status,JSONObject json) {
            return new TransportResponse(status,json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    public static final class TransportException extends Exception {
        public final int httpStatus;
        TransportException(int status){
            super("Nexo Connect HTTP error ("+status+")");
            this.httpStatus=status;
        }
    }
    private static final class HttpsTransport implements Transport {
        private static final int TIMEOUT = 15000;
        private final String origin;
        HttpsTransport(String projectUrl) {
            try{
                URL url=new URL(projectUrl);
                String host=url.getHost();
                if(!"https".equals(url.getProtocol())||host==null||
                        !host.matches("^[a-z0-9-]+\\.supabase\\.co$")||
                        url.getPort()!=-1||url.getUserInfo()!=null||
                        url.getQuery()!=null||url.getRef()!=null||
                        !(url.getPath().isEmpty()||url.getPath().equals("/")))
                    throw new IllegalArgumentException("Supabase HTTPS origin required");
                origin="https://"+host;
            }catch(Exception e){throw new IllegalArgumentException("Invalid Supabase endpoint",e);}
        }
        @Override public TransportResponse post(String path,byte[] bytes,
                Map<String,String> headers) throws Exception {
            if(!path.startsWith(PATH_PREFIX)||path.contains("..")||
                    path.indexOf('?')!=-1||path.indexOf('#')!=-1||
                    bytes==null||bytes.length>INLINE_BYTES)
                throw new GeneralSecurityException("Invalid network request");
            HttpsURLConnection conn=null;
            try{
                conn=(HttpsURLConnection)new URL(origin+path).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setInstanceFollowRedirects(false);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setFixedLengthStreamingMode(bytes.length);
                for(Map.Entry<String,String> entry:headers.entrySet())
                    conn.setRequestProperty(entry.getKey(),entry.getValue());
                try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
                int status=conn.getResponseCode();
                // HTTP 3xx never followed, and errors are opaque: no server
                // response, bearer or request body logged or returned.
                if(status<200||status>299)return new TransportResponse(status,null);
                try(InputStream in=conn.getInputStream();
                    ByteArrayOutputStream sink=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192];int n;
                    while((n=in.read(buf))!=-1){
                        if(n==0)continue;
                        if(sink.size()+n>RESPONSE_BYTES)
                            throw new GeneralSecurityException("Response too large");
                        sink.write(buf,0,n);
                    }
                    return new TransportResponse(status,sink.toByteArray());
                }
            }finally{if(conn!=null)conn.disconnect();}
        }
    }
}
