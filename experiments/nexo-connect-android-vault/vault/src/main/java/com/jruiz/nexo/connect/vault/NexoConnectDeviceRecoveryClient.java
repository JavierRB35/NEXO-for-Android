package com.jruiz.nexo.connect.vault;

import com.google.crypto.tink.KeysetHandle;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;

/**
 * LAB ONLY. Reconcile an uncertain phone registration without OTP replay.
 * Matching the phone UUID on /status alone is NOT proof of ownership:
 * /delivery/inbox requires a NEW challenge and a matching Ed25519 signature.
 */
public final class NexoConnectDeviceRecoveryClient {
    public enum State { OWN_DEVICE_CONFIRMED, NO_REGISTERED_DEVICE, ANOTHER_DEVICE, INCONCLUSIVE }
    public static final class Outcome {
        public final State state;
        public final int generation;
        private Outcome(State state,int generation){
            this.state=state;this.generation=generation;
        }
    }
    private final UUID account, device;
    private final NexoConnectEmailOtpClient.VerifiedSession session;
    private final StatusTransport status;
    private final PhoneProof proof;
    private final Clock clock;

    /** Real implementation: Auth JWT + HTTPS /status + signed inbox challenge. */
    public NexoConnectDeviceRecoveryClient(String origin,String apiKey,
            NexoConnectEmailOtpClient.VerifiedSession verified,UUID account,
            UUID device,KeysetHandle signer) throws Exception {
        this(new StatusHttpsTransport(origin,apiKey),
            generation -> {
                String jwt=verified.accessTokenFor(account,System.currentTimeMillis());
                new NexoConnectSignedGatewayClient(origin,apiKey,jwt,account,
                        device,generation,signer).inbox(1,null);
                return true;
            },verified,account,device,System::currentTimeMillis);
    }

    /** Test seam does not authorize a real device. */
    NexoConnectDeviceRecoveryClient(StatusTransport status,PhoneProof proof,
            NexoConnectEmailOtpClient.VerifiedSession session,UUID account,
            UUID device,Clock clock) throws GeneralSecurityException {
        if(status==null||proof==null||session==null||
                account==null||device==null||clock==null)
            throw new GeneralSecurityException("Missing recovery identity");
        this.status=status;this.proof=proof;this.session=session;
        this.account=account;this.device=device;this.clock=clock;
    }

    public Outcome checkOnce() throws Exception {
        String jwt=session.accessTokenFor(account,clock.millis());
        JSONObject remote=status.fetch(jwt);
        if(remote==null||remote.length()!=3||
                !(remote.opt("registered") instanceof Boolean))
            throw new GeneralSecurityException("Malformed device status");
        if(!remote.getBoolean("registered")){
            if(!remote.isNull("deviceId")||!remote.isNull("generation"))
                throw new GeneralSecurityException("Contradictory device status");
            return new Outcome(State.NO_REGISTERED_DEVICE,0);
        }
        String id=remote.optString("deviceId","");
        Object raw=remote.opt("generation");
        if(!uuid(id)||!(raw instanceof Number))
            throw new GeneralSecurityException("Invalid remote device");
        long generation=((Number)raw).longValue();
        if(((Number)raw).doubleValue()!=generation||generation<=0||
                generation>Integer.MAX_VALUE)
            throw new GeneralSecurityException("Invalid device generation");
        if(!device.toString().equals(id))
            return new Outcome(State.ANOTHER_DEVICE,(int)generation);
        try{
            if(proof.prove((int)generation))
                return new Outcome(State.OWN_DEVICE_CONFIRMED,(int)generation);
        }catch(Exception unavailable){
            // A valid UUID but failed proof MUST NOT mark this device trusted.
        }
        return new Outcome(State.INCONCLUSIVE,0);
    }
    static Outcome testOutcome(State state,int generation){
        return new Outcome(state,generation);
    }
    private static boolean uuid(String s){
        return s!=null&&s.matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    @FunctionalInterface interface Clock {long millis();}
    @FunctionalInterface interface PhoneProof {boolean prove(int generation)throws Exception;}
    interface StatusTransport {JSONObject fetch(String bearer)throws Exception;}

    private static final class StatusHttpsTransport implements StatusTransport {
        private final String origin, key;
        StatusHttpsTransport(String address,String apiKey)throws GeneralSecurityException{
            try{
                URL u=new URL(address);String host=u.getHost();
                if(!"https".equals(u.getProtocol())||host==null||
                        !host.matches("[a-z0-9-]+\\.supabase\\.co")||
                        u.getPort()!=-1||u.getUserInfo()!=null||
                        u.getQuery()!=null||u.getRef()!=null||
                        !(u.getPath().isEmpty()||u.getPath().equals("/"))||
                        apiKey==null||apiKey.length()<4||apiKey.length()>4096||
                        apiKey.matches(".*\\s.*"))
                    throw new GeneralSecurityException("Untrusted Supabase origin");
                origin="https://"+host;key=apiKey;
            }catch(Exception invalid){
                throw new GeneralSecurityException("Untrusted Supabase origin",invalid);
            }
        }
        @Override public JSONObject fetch(String jwt)throws Exception{
            if(jwt==null||jwt.length()<8||jwt.length()>16384||
                    jwt.matches(".*\\s.*"))
                throw new GeneralSecurityException("Invalid Auth session");
            HttpsURLConnection conn=null;
            try{
                conn=(HttpsURLConnection)new URL(origin+
                    "/functions/v1/nexo-connect-gateway/status").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);conn.setReadTimeout(15000);
                conn.setUseCaches(false);conn.setInstanceFollowRedirects(false);
                conn.setDoOutput(true);conn.setFixedLengthStreamingMode(2);
                conn.setRequestProperty("apikey",key);
                conn.setRequestProperty("Authorization","Bearer "+jwt);
                conn.setRequestProperty("Content-Type","application/json");
                conn.setRequestProperty("Cache-Control","no-store");
                try(OutputStream out=conn.getOutputStream()){
                    out.write(new byte[]{'{','}'});
                }
                if(conn.getResponseCode()!=200)
                    throw new GeneralSecurityException("Status check refused");
                try(InputStream input=conn.getInputStream();
                    ByteArrayOutputStream sink=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[1024];int n;
                    while((n=input.read(bytes))!=-1){
                        if(n==0)continue;
                        if(sink.size()+n>2048)
                            throw new GeneralSecurityException("Status too large");
                        sink.write(bytes,0,n);
                    }
                    return new JSONObject(sink.toString(StandardCharsets.UTF_8.name()));
                }
            }finally{if(conn!=null)conn.disconnect();}
        }
    }
}
