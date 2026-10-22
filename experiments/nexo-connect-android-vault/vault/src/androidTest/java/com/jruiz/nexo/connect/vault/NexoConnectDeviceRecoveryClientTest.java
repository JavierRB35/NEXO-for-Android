package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.security.GeneralSecurityException;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectDeviceRecoveryClientTest {
    private static final UUID ACCOUNT=UUID.fromString(
        "11111111-1111-4111-8111-111111111111");
    private static final UUID DEVICE=UUID.fromString(
        "22222222-2222-4222-8222-222222222222");
    private static final long NOW=1810000000000L;
    private static NexoConnectEmailOtpClient.VerifiedSession session()throws Exception{
        NexoConnectEmailOtpClient.Transport fake=(method,path,body,headers)->{
            if(path.equals("/auth/v1/verify"))
                return NexoConnectEmailOtpClient.Result.json(200,
                    new JSONObject().put("access_token","sample.jwt.token")
                        .put("refresh_token","mock-rotating-refresh-credential")
                        .put("token_type","bearer").put("expires_in",3600));
            if(path.equals("/auth/v1/user"))
                return NexoConnectEmailOtpClient.Result.json(200,
                    new JSONObject().put("id",ACCOUNT.toString())
                        .put("email","user@example.org"));
            throw new AssertionError("Unexpected Auth call");
        };
        return new NexoConnectEmailOtpClient(fake,"public-test",()->NOW)
                .verifyEmailCode("user@example.org","123456");
    }
    private static final class Fake implements
            NexoConnectDeviceRecoveryClient.StatusTransport,
            NexoConnectDeviceRecoveryClient.PhoneProof {
        JSONObject response;int reads,signatures;boolean signOk=true;
        Fake()throws Exception{
            response=new JSONObject().put("registered",true)
                .put("deviceId",DEVICE.toString()).put("generation",2);
        }
        @Override public JSONObject fetch(String bearer){
            assertEquals("sample.jwt.token",bearer);
            reads++;return response;
        }
        @Override public boolean prove(int generation){
            assertEquals(2,generation);
            signatures++;return signOk;
        }
    }
    @Test public void onlySignedPhoneProofConfirmsEnrollment()throws Exception{
        Fake f=new Fake();
        NexoConnectDeviceRecoveryClient check=new NexoConnectDeviceRecoveryClient(
            f,f,session(),ACCOUNT,DEVICE,()->NOW);
        assertEquals(NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED,
            check.checkOnce().state);
        assertEquals(2,check.checkOnce().generation);
        assertEquals(2,f.reads);assertEquals(2,f.signatures);
    }
    @Test public void matchingUuidWithBadSignatureRemainsUnknown()throws Exception{
        Fake f=new Fake();f.signOk=false;
        NexoConnectDeviceRecoveryClient check=new NexoConnectDeviceRecoveryClient(
            f,f,session(),ACCOUNT,DEVICE,()->NOW);
        assertEquals(NexoConnectDeviceRecoveryClient.State.INCONCLUSIVE,
            check.checkOnce().state);
        assertEquals(1,f.signatures);
    }
    @Test public void anotherPhoneAndNoDeviceDoNotAttemptSignature()throws Exception{
        Fake f=new Fake();
        NexoConnectDeviceRecoveryClient check=new NexoConnectDeviceRecoveryClient(
            f,f,session(),ACCOUNT,DEVICE,()->NOW);
        f.response.put("deviceId",UUID.randomUUID().toString());
        assertEquals(NexoConnectDeviceRecoveryClient.State.ANOTHER_DEVICE,
            check.checkOnce().state);
        f.response=new JSONObject().put("registered",false)
            .put("deviceId",JSONObject.NULL).put("generation",JSONObject.NULL);
        assertEquals(NexoConnectDeviceRecoveryClient.State.NO_REGISTERED_DEVICE,
            check.checkOnce().state);
        assertEquals(0,f.signatures);
    }
    @Test public void contradictoryStatusFailsClosed()throws Exception{
        Fake f=new Fake();f.response.put("registered",false);
        NexoConnectDeviceRecoveryClient check=new NexoConnectDeviceRecoveryClient(
            f,f,session(),ACCOUNT,DEVICE,()->NOW);
        try{check.checkOnce();fail("Status contradiction");}
        catch(GeneralSecurityException expected){}
        assertEquals(0,f.signatures);
    }
    @Test public void expiredOrOtherAccountNeverAccessStatus()throws Exception{
        Fake f=new Fake();
        NexoConnectDeviceRecoveryClient swapped=new NexoConnectDeviceRecoveryClient(
            f,f,session(),UUID.randomUUID(),DEVICE,()->NOW);
        try{swapped.checkOnce();fail("Account swapped");}
        catch(GeneralSecurityException expected){}
        NexoConnectDeviceRecoveryClient expired=new NexoConnectDeviceRecoveryClient(
            f,f,session(),ACCOUNT,DEVICE,()->NOW+3600000);
        try{expired.checkOnce();fail("Expired session");}
        catch(GeneralSecurityException expected){}
        assertEquals(0,f.reads);
    }
}
