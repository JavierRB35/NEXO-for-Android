package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectDeviceEnrollmentClientTest {
    private static final UUID ACCOUNT=UUID.fromString(
            "11111111-1111-4111-8111-111111111111");
    private static final UUID DEVICE=UUID.fromString(
            "22222222-2222-4222-8222-222222222222");
    private static final long NOW=1810000000000L;
    private static NexoConnectEmailOtpClient.VerifiedSession session()throws Exception{
        NexoConnectEmailOtpClient.Transport fake=(method,path,body,headers)->{
            if(path.equals("/auth/v1/verify"))
                return NexoConnectEmailOtpClient.Result.json(200,new JSONObject()
                        .put("access_token","session.jwt.example")
                        .put("refresh_token","mock-rotating-refresh-credential")
                        .put("token_type","bearer").put("expires_in",3600));
            if(path.equals("/auth/v1/user"))
                return NexoConnectEmailOtpClient.Result.json(200,new JSONObject()
                        .put("id",ACCOUNT.toString()).put("email","user@example.org"));
            throw new AssertionError("Unexpected auth");
        };
        return new NexoConnectEmailOtpClient(fake,"public-demo",()->NOW)
                .verifyEmailCode("user@example.org","123456");
    }

    private static final class Fixture {
        final KeysetHandle signer,hpke;
        final FakeNetwork network;
        final NexoConnectDeviceEnrollmentClient client;
        Fixture()throws Exception{
            SignatureConfig.register();HybridConfig.register();
            signer=KeysetHandle.generateNew(SignatureKeyTemplates.ED25519WithRawOutput);
            hpke=KeysetHandle.generateNew(HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.TINK).build());
            network=new FakeNetwork(signer);
            client=new NexoConnectDeviceEnrollmentClient(network,"public-demo",
                    session(),ACCOUNT,DEVICE,signer,hpke,()->NOW);
        }
    }
    private static final class FakeNetwork implements NexoConnectDeviceEnrollmentClient.Transport{
        int calls;
        boolean reject;
        final PublicKeyVerify verify;
        FakeNetwork(KeysetHandle signer)throws Exception{
            verify=signer.getPublicKeysetHandle().getPrimitive(
                    RegistryConfiguration.get(),PublicKeyVerify.class);
        }
        @Override public NexoConnectDeviceEnrollmentClient.Response post(
                String path,byte[] body,Map<String,String> headers)throws Exception{
            calls++;
            assertEquals("/functions/v1/nexo-connect-gateway/device/enroll",path);
            assertEquals("Bearer session.jwt.example",headers.get("Authorization"));
            assertEquals("public-demo",headers.get("apikey"));
            JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));
            assertEquals(DEVICE.toString(),request.getString("deviceId"));
            assertEquals("654321",request.getString("otp"));
            String[] expectedFields={"deviceId","signingKey","encryptionKey","replace","otp"};
            assertEquals(expectedFields.length,request.length());
            assertEquals(43,request.getString("signingKey").length());
            assertEquals(43,request.getString("encryptionKey").length());
            String time=headers.get("x-nexo-issued-at");
            assertEquals(String.valueOf(NOW),time);
            byte[] sha=MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder hex=new StringBuilder();
            for(byte b:sha)hex.append(String.format("%02x",b&255));
            String canonical="NEXO-CONNECT-ENROLL-V1\nPOST\n"+
                "/nexo-connect/device/enroll\n"+ACCOUNT+"\n"+time+"\n"+hex+"\n";
            byte[] signature=android.util.Base64.decode(
                    headers.get("x-nexo-enroll-signature"),
                    android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|
                    android.util.Base64.NO_WRAP);
            verify.verify(signature,canonical.getBytes(StandardCharsets.UTF_8));
            if(reject)return new NexoConnectDeviceEnrollmentClient.Response(403,null);
            boolean replace=request.getBoolean("replace");
            return NexoConnectDeviceEnrollmentClient.Response.json(201,
                    new JSONObject().put("deviceId",DEVICE.toString())
                        .put("generation",replace?2:1)
                        .put("replacement",replace));
        }
    }
    @Test public void firstEnrollmentSignsExactBodyAndReturnsGeneration()throws Exception{
        Fixture f=new Fixture();
        assertEquals(1,f.client.enroll("654321",false,true));
        assertEquals(1,f.network.calls);
    }
    @Test public void replacementSignsExactBodyAndRequiresExplicitConsent()throws Exception{
        Fixture f=new Fixture();
        try{f.client.enroll("654321",true,false);fail("No consent allowed");}
        catch(SecurityException expected){}
        assertEquals(0,f.network.calls);
        assertEquals(2,f.client.enroll("654321",true,true));
        assertEquals(1,f.network.calls);
    }
    @Test public void rejectsInvalidOtpBeforeAnyNetworkCall()throws Exception{
        Fixture f=new Fixture();
        try{f.client.enroll("invalid",true,true);fail("Bad OTP accepted");}
        catch(GeneralSecurityException expected){}
        assertEquals(0,f.network.calls);
    }
    @Test public void failureDoesNotTriggerAnotherEnrollmentPost()throws Exception{
        Fixture f=new Fixture();f.network.reject=true;
        try{f.client.enroll("654321",true,true);fail("HTTP rejection accepted");}
        catch(NexoConnectDeviceEnrollmentClient.EnrollError expected){
            assertEquals(403,expected.httpStatus);
        }
        assertEquals(1,f.network.calls);
    }
    @Test public void untrustedOriginIsNotAccepted()throws Exception {
        Fixture f=new Fixture();
        try{new NexoConnectDeviceEnrollmentClient("http://bad.example.org",
                "public-demo",session(),ACCOUNT,DEVICE,f.signer,f.hpke);
            fail("Insecure HTTPS origin allowed");}
        catch(GeneralSecurityException expected){}
    }
}
