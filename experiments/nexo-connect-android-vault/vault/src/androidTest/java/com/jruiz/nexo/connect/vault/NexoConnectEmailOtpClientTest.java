package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectEmailOtpClientTest {
    private static final String EMAIL="person@example.org";
    private static final String ID="11111111-1111-4111-8111-111111111111";
    private static final long NOW=1800000000000L;
    private static class Stub implements NexoConnectEmailOtpClient.Transport {
        ArrayList<String> operations=new ArrayList<>();
        int fail=0;
        boolean wrongEmail;
        @Override public NexoConnectEmailOtpClient.Result call(String method,
                String path,byte[] body,Map<String,String> headers)throws Exception{
            operations.add(method+" "+path);
            assertEquals("public-test",headers.get("apikey"));
            if(path.equals("/auth/v1/otp")){
                JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));
                assertEquals(EMAIL,request.getString("email"));
                assertFalse(request.getBoolean("create_user"));
                return fail==429?new NexoConnectEmailOtpClient.Result(429,null):
                        NexoConnectEmailOtpClient.Result.json(200,new JSONObject());
            }
            if(path.equals("/auth/v1/verify")){
                JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));
                assertEquals("123456",request.getString("token"));
                assertEquals("email",request.getString("type"));
                return NexoConnectEmailOtpClient.Result.json(200,new JSONObject()
                        .put("access_token","verified.jwt.mock")
                        .put("token_type","bearer").put("expires_in",3600)
                        .put("user",new JSONObject().put("id",
                                "99999999-9999-4999-8999-999999999999")));
            }
            if(path.equals("/auth/v1/user")){
                assertEquals("GET",method);
                assertNull(body);
                assertEquals("Bearer verified.jwt.mock",headers.get("Authorization"));
                if(fail==401)return new NexoConnectEmailOtpClient.Result(401,null);
                return NexoConnectEmailOtpClient.Result.json(200,new JSONObject()
                        .put("id",ID).put("email",wrongEmail?"other@example.org":EMAIL));
            }
            throw new AssertionError("Unexpected endpoint");
        }
    }
    @Test public void verifiesJwtAccountViaUserNotTokenJson() throws Exception {
        Stub stub=new Stub();
        NexoConnectEmailOtpClient client=new NexoConnectEmailOtpClient(
                stub,"public-test",()->NOW);
        client.sendEmailCode("PERSON@EXAMPLE.ORG",false);
        NexoConnectEmailOtpClient.VerifiedSession s=
                client.verifyEmailCode(EMAIL,"123456");
        assertEquals(UUID.fromString(ID),s.accountId);
        assertEquals(NOW+3600000L,s.expiresAtMillis);
        assertEquals("verified.jwt.mock",s.accessTokenFor(s.accountId,NOW));
        assertEquals(3,stub.operations.size());
    }
    @Test public void incorrectOtpNeverContactsServer() throws Exception {
        Stub stub=new Stub();
        NexoConnectEmailOtpClient client=new NexoConnectEmailOtpClient(
                stub,"public-test",()->NOW);
        try{client.verifyEmailCode(EMAIL,"1234");fail("Short OTP");}
        catch(GeneralSecurityException expected){}
        assertTrue(stub.operations.isEmpty());
    }
    @Test public void switchedAccountAndExpiredBearerRejected() throws Exception {
        NexoConnectEmailOtpClient client=new NexoConnectEmailOtpClient(
                new Stub(),"public-test",()->NOW);
        NexoConnectEmailOtpClient.VerifiedSession s=client.verifyEmailCode(EMAIL,"123456");
        try{s.accessTokenFor(UUID.randomUUID(),NOW);fail("Account swap");}
        catch(GeneralSecurityException expected){}
        try{s.accessTokenFor(s.accountId,NOW+3600000L);fail("Expired");}
        catch(GeneralSecurityException expected){}
    }
    @Test public void emailMismatchAndBadAuthTokenRejected() throws Exception {
        Stub mismatch=new Stub();mismatch.wrongEmail=true;
        try{new NexoConnectEmailOtpClient(mismatch,"public-test",()->NOW)
            .verifyEmailCode(EMAIL,"123456");fail("Wrong email");}
        catch(GeneralSecurityException expected){}
        Stub revoked=new Stub();revoked.fail=401;
        try{new NexoConnectEmailOtpClient(revoked,"public-test",()->NOW)
            .verifyEmailCode(EMAIL,"123456");fail("Bad JWT");}
        catch(NexoConnectEmailOtpClient.AuthException expected){
            assertEquals(401,expected.httpStatus);
        }
        assertEquals(2,revoked.operations.size());
    }
    @Test public void rateLimitedOtpIsNotAutomaticallyRetried()throws Exception{
        Stub blocked=new Stub();blocked.fail=429;
        try{new NexoConnectEmailOtpClient(blocked,"public-test",()->NOW)
            .sendEmailCode(EMAIL,false);fail("Retry rate limit");}
        catch(NexoConnectEmailOtpClient.AuthException expected){
            assertEquals(429,expected.httpStatus);
        }
        assertEquals(1,blocked.operations.size());
    }
    @Test public void untrustedHostsAndHttpRejected()throws Exception{
        for(String url:new String[]{"http://a.supabase.co",
                "https://evil.example.org","https://a.supabase.co.bad.org",
                "https://a.supabase.co:9999","https://a.supabase.co/path"}){
            try{new NexoConnectEmailOtpClient(url,"public-test");
                fail("Host allowed");}
            catch(GeneralSecurityException expected){}
        }
    }
}
