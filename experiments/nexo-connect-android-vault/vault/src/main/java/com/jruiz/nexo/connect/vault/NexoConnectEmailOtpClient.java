package com.jruiz.nexo.connect.vault;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;

/**
 * Isolated passwordless email OTP client. Access JWT stays memory-only.
 * Rotating refresh tokens may be saved via the Android Keystore session vault.
 * IMPORTANT: normal Supabase Auth OTP login is NOT enough to authorize a
 * replacement device. Server must separately demand RECENT OTP proof.
 */
public final class NexoConnectEmailOtpClient {
    private static final int MAX_RESPONSE=32768;
    private static final int MAX_REQUEST=8192;
    private final Transport transport;
    private final String publicKey;
    private final Clock clock;

    public NexoConnectEmailOtpClient(String supabaseOrigin,String publishableKey)
            throws GeneralSecurityException {
        this(new HttpsTransport(supabaseOrigin),publishableKey,System::currentTimeMillis);
    }

    NexoConnectEmailOtpClient(Transport transport,String publishableKey,Clock clock)
            throws GeneralSecurityException {
        if(transport==null||clock==null||publishableKey==null||
                publishableKey.length()<4||publishableKey.length()>4096||
                containsWhitespace(publishableKey))
            throw new GeneralSecurityException("Invalid Auth configuration");
        this.transport=transport;
        this.publicKey=publishableKey;
        this.clock=clock;
    }

    /** Requires an email template containing the actual OTP code. */
    public void sendEmailCode(String email,boolean createUser)throws Exception {
        String normalized=normalizeEmail(email);
        JSONObject request=new JSONObject().put("email",normalized)
                .put("create_user",createUser);
        expectSuccess(transport.call("POST","/auth/v1/otp",
                request.toString().getBytes(StandardCharsets.UTF_8),headers(null)),false);
    }

    /** A valid JWT must ALSO be checked through Auth /user, never self-parsed. */
    public VerifiedSession verifyEmailCode(String email,String code)throws Exception {
        String normalized=normalizeEmail(email);
        if(code==null||!code.matches("[0-9]{6,8}"))
            throw new GeneralSecurityException("Invalid OTP code");
        JSONObject request=new JSONObject().put("email",normalized)
                .put("token",code).put("type","email");
        JSONObject tokenResponse=expectSuccess(transport.call("POST","/auth/v1/verify",
                request.toString().getBytes(StandardCharsets.UTF_8),headers(null)),true);
        return validateSession(tokenResponse,normalized,null);
    }

    /**
     * Uses a previously OTP-verified refresh token. No new email is sent.
     * The calling activity must immediately persist the rotated credential.
     * /auth/v1/user, not JWT claims, binds the expected account identity.
     */
    public VerifiedSession refreshSession(String refreshToken,UUID expectedAccount,
            String expectedEmail)throws Exception {
        if(expectedAccount==null)
            throw new GeneralSecurityException("Missing saved account");
        String email=normalizeEmail(expectedEmail);
        requireRefresh(refreshToken);
        JSONObject request=new JSONObject().put("refresh_token",refreshToken);
        JSONObject response=expectSuccess(transport.call("POST",
                "/auth/v1/token?grant_type=refresh_token",
                request.toString().getBytes(StandardCharsets.UTF_8),headers(null)),true);
        return validateSession(response,email,expectedAccount);
    }

    private VerifiedSession validateSession(JSONObject tokenResponse,
            String normalized,UUID expectedAccount)throws Exception {
        String jwt=tokenResponse.optString("access_token","");
        String refresh=tokenResponse.optString("refresh_token","");
        String kind=tokenResponse.optString("token_type","");
        Object ttlValue=tokenResponse.opt("expires_in");
        if(jwt.length()<8||jwt.length()>16384||containsWhitespace(jwt)||
                !"bearer".equalsIgnoreCase(kind)||!(ttlValue instanceof Number))
            throw new GeneralSecurityException("Invalid session");
        requireRefresh(refresh);
        long ttl=((Number)ttlValue).longValue();
        if(ttl<30||ttl>172800||((Number)ttlValue).doubleValue()!=ttl)
            throw new GeneralSecurityException("Invalid expiry");
        JSONObject confirmed=expectSuccess(transport.call("GET","/auth/v1/user",
                null,headers(jwt)),true);
        String verifiedId=confirmed.optString("id","");
        if(!isUuid(verifiedId)||!normalized.equalsIgnoreCase(
                confirmed.optString("email",""))||
                (expectedAccount!=null&&!expectedAccount.toString().equals(verifiedId)))
            throw new GeneralSecurityException("Auth account not verified");
        long now=clock.millis();
        if(now<=0||now>Long.MAX_VALUE-ttl*1000)
            throw new GeneralSecurityException("Invalid clock");
        return new VerifiedSession(UUID.fromString(verifiedId),normalized,jwt,
                now+ttl*1000,refresh);
    }

    private static void requireRefresh(String refresh)throws GeneralSecurityException {
        if(refresh==null||refresh.length()<8||refresh.length()>4096||
                containsWhitespace(refresh))
            throw new GeneralSecurityException("Invalid refresh credential");
    }

    private Map<String,String> headers(String jwt){
        HashMap<String,String> map=new HashMap<>();
        map.put("apikey",publicKey);
        map.put("Content-Type","application/json; charset=utf-8");
        map.put("Cache-Control","no-store");
        map.put("Accept","application/json");
        if(jwt!=null)map.put("Authorization","Bearer "+jwt);
        return Collections.unmodifiableMap(map);
    }
    private static JSONObject expectSuccess(Result r,boolean json)throws Exception {
        if(r==null)throw new GeneralSecurityException("No Auth response");
        if(r.status<200||r.status>=300)throw new AuthException(r.status);
        if(r.body==null||r.body.length>MAX_RESPONSE||
                (json&&r.body.length<2))
            throw new GeneralSecurityException("Invalid Auth response");
        if(!json)return null;
        JSONObject value=new JSONObject(new String(r.body,StandardCharsets.UTF_8));
        if(value.has("error")||value.has("error_description"))
            throw new GeneralSecurityException("Auth denied");
        return value;
    }
    private static String normalizeEmail(String value)throws GeneralSecurityException {
        if(value==null||value.length()>254)
            throw new GeneralSecurityException("Invalid email");
        String email=value.trim().toLowerCase(Locale.ROOT);
        if(!email.matches("[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+")||
                email.contains(".."))
            throw new GeneralSecurityException("Invalid email");
        return email;
    }
    private static boolean containsWhitespace(String s){
        for(int i=0;i<s.length();i++)
            if(Character.isWhitespace(s.charAt(i)))return true;
        return false;
    }
    private static boolean isUuid(String s){
        return s!=null&&s.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    public static final class VerifiedSession {
        public final UUID accountId;
        public final String email;
        public final long expiresAtMillis;
        private final String bearer,refresh;
        private VerifiedSession(UUID accountId,String email,String bearer,long expires,
                String refresh){
            this.accountId=accountId;this.email=email;
            this.bearer=bearer;this.expiresAtMillis=expires;this.refresh=refresh;
        }
        public String refreshTokenFor(UUID expectedAccount)
                throws GeneralSecurityException {
            if(expectedAccount==null||!expectedAccount.equals(accountId))
                throw new GeneralSecurityException("Wrong session account");
            return refresh;
        }
        public String accessTokenFor(UUID expectedAccount,long now)
                throws GeneralSecurityException {
            if(expectedAccount==null||!expectedAccount.equals(accountId)||
                    now<=0||expiresAtMillis-now<=30000)
                throw new GeneralSecurityException("Session expired or switched");
            return bearer;
        }
    }
    public static final class AuthException extends Exception {
        public final int httpStatus;
        AuthException(int status){
            super("Auth HTTP error ("+status+")");httpStatus=status;
        }
    }
    @FunctionalInterface interface Clock {long millis();}
    interface Transport {
        Result call(String method,String path,byte[] body,Map<String,String> headers)
                throws Exception;
    }
    static final class Result {
        final int status;final byte[] body;
        Result(int status,byte[] body){
            this.status=status;this.body=body==null?null:body.clone();
        }
        static Result json(int status,JSONObject response){
            return new Result(status,
                    response.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static final class HttpsTransport implements Transport {
        private final String origin;
        HttpsTransport(String projectUrl)throws GeneralSecurityException {
            try{
                URL u=new URL(projectUrl);
                String host=u.getHost();
                if(!"https".equals(u.getProtocol())||host==null||
                        !host.matches("[a-z0-9-]+\\.supabase\\.co")||
                        u.getPort()!=-1||u.getUserInfo()!=null||
                        u.getQuery()!=null||u.getRef()!=null||
                        !(u.getPath().isEmpty()||u.getPath().equals("/")))
                    throw new GeneralSecurityException("HTTPS Supabase required");
                origin="https://"+host;
            }catch(Exception e){
                throw new GeneralSecurityException("HTTPS Supabase required",e);
            }
        }
        @Override public Result call(String method,String path,byte[] body,
                Map<String,String> headers)throws Exception{
            if(!("POST".equals(method)||"GET".equals(method))||
                !("/auth/v1/otp".equals(path)||
                  "/auth/v1/verify".equals(path)||"/auth/v1/user".equals(path)||
                  "/auth/v1/token?grant_type=refresh_token".equals(path))||
                ("GET".equals(method))!=(body==null)||
                (body!=null&&body.length>MAX_REQUEST))
                throw new GeneralSecurityException("Forbidden Auth request");
            HttpsURLConnection c=null;
            try{
                c=(HttpsURLConnection)new URL(origin+path).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(15000);c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.setUseCaches(false);
                if(body!=null){
                    c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);
                }
                for(Map.Entry<String,String> e:headers.entrySet())
                    c.setRequestProperty(e.getKey(),e.getValue());
                if(body!=null)try(OutputStream out=c.getOutputStream()){out.write(body);}
                int status=c.getResponseCode();
                if(status<200||status>=300)return new Result(status,null);
                try(InputStream input=c.getInputStream();
                    ByteArrayOutputStream sink=new ByteArrayOutputStream()){
                    byte[] part=new byte[4096];
                    for(int n;(n=input.read(part))!=-1;){
                        if(n==0)continue;
                        if(sink.size()+n>MAX_RESPONSE)
                            throw new GeneralSecurityException("Oversize Auth response");
                        sink.write(part,0,n);
                    }
                    return new Result(status,sink.toByteArray());
                }
            }finally{if(c!=null)c.disconnect();}
        }
    }
}
