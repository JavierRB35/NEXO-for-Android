package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NexoConnectOnboardingFlowTest {
    private static final UUID ACCOUNT=UUID.fromString(
            "11111111-1111-4111-8111-111111111111");
    private static final class Fake implements NexoConnectOnboardingFlow.Backend {
        int mailCalls,verifyCalls,enrollCalls;
        boolean rejectEnrollment;
        boolean replacementObserved;
        NexoConnectDeviceRecoveryClient.State recoveryState=
                NexoConnectDeviceRecoveryClient.State.INCONCLUSIVE;
        int recoveryGeneration,recoveryCalls;
        @Override public void sendEmailOtp(String email,boolean createAccount){
            assertEquals("user@example.org",email);
            mailCalls++;
        }
        @Override public NexoConnectOnboardingFlow.VerifiedAccount verifyLoginOtp(
                String email,String otp)throws Exception {
            assertEquals("user@example.org",email);
            assertEquals("111111",otp);
            verifyCalls++;
            return new NexoConnectOnboardingFlow.VerifiedAccount(ACCOUNT,email);
        }
        @Override public NexoConnectDeviceRecoveryClient.Outcome reconcile(
                NexoConnectOnboardingFlow.VerifiedAccount user)throws Exception{
            assertEquals(ACCOUNT,user.accountId);
            recoveryCalls++;
            return NexoConnectDeviceRecoveryClient.testOutcome(
                    recoveryState,recoveryGeneration);
        }
        @Override public int enrollDevice(
                NexoConnectOnboardingFlow.VerifiedAccount user,String second,
                boolean replace,boolean approved)throws Exception {
            assertEquals(ACCOUNT,user.accountId);
            assertEquals("222222",second);
            assertTrue(approved);
            replacementObserved=replace;
            enrollCalls++;
            if(rejectEnrollment)throw new java.io.IOException("uncertain network");
            return replace?2:1;
        }
    }

    @Test public void firstPhoneRequiresTwoDifferentCodeStagesAndConsent()throws Exception{
        Fake fake=new Fake();
        NexoConnectOnboardingFlow flow=new NexoConnectOnboardingFlow(fake);
        assertEquals(NexoConnectOnboardingFlow.Stage.REQUEST_LOGIN_CODE,flow.stage());
        flow.requestLogin("user@example.org",false);
        assertEquals(1,fake.mailCalls);
        flow.verifyLogin("111111");
        assertEquals(ACCOUNT,flow.accountId());
        flow.requestDeviceCode(false);
        assertEquals(2,fake.mailCalls);
        try{flow.confirmDevice("222222",false);fail("No consent");}
        catch(SecurityException expected){}
        assertEquals(0,fake.enrollCalls);
        assertEquals(1,flow.confirmDevice("222222",true));
        assertEquals(NexoConnectOnboardingFlow.Stage.COMPLETE,flow.stage());
        assertEquals(1,fake.enrollCalls);
        assertFalse(fake.replacementObserved);
    }

    @Test public void replacementRequiresNewOtpAndCannotBeRetried()throws Exception{
        Fake fake=new Fake();fake.rejectEnrollment=true;
        NexoConnectOnboardingFlow flow=new NexoConnectOnboardingFlow(fake);
        flow.requestLogin("user@example.org",false);
        flow.verifyLogin("111111");
        flow.requestDeviceCode(true);
        try{flow.confirmDevice("222222",true);fail("Network uncertainty accepted");}
        catch(java.io.IOException expected){}
        assertEquals(NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT,
                flow.stage());
        try{flow.confirmDevice("222222",true);fail("Uncertain request replayed");}
        catch(IllegalStateException expected){}
        assertEquals(1,fake.enrollCalls);
    }

    @Test public void outOfOrderOperationsDoNotConsumeCodes()throws Exception{
        Fake fake=new Fake();
        NexoConnectOnboardingFlow flow=new NexoConnectOnboardingFlow(fake);
        try{flow.requestDeviceCode(false);fail("Bypassed login");}
        catch(IllegalStateException expected){}
        try{flow.confirmDevice("222222",true);fail("Bypassed OTP");}
        catch(IllegalStateException expected){}
        assertEquals(0,fake.mailCalls);
        assertEquals(0,fake.enrollCalls);
        flow.requestLogin("user@example.org",false);
        try{flow.requestLogin("user@example.org",false);fail("Repeated request");}
        catch(IllegalStateException expected){}
        assertEquals(1,fake.mailCalls);
    }

    @Test public void uncertainEnrollmentCanBeConfirmedOnlyByRemoteSignedProof()
            throws Exception{
        Fake fake=new Fake();fake.rejectEnrollment=true;
        NexoConnectOnboardingFlow flow=new NexoConnectOnboardingFlow(fake);
        flow.requestLogin("user@example.org",false);
        flow.verifyLogin("111111");flow.requestDeviceCode(true);
        try{flow.confirmDevice("222222",true);fail("Unexpected response");}
        catch(java.io.IOException expected){}
        assertEquals(NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT,
                flow.stage());
        assertEquals(NexoConnectDeviceRecoveryClient.State.INCONCLUSIVE,
                flow.reconcile());
        assertEquals(NexoConnectOnboardingFlow.Stage.RECOVER_FROM_UNKNOWN_RESULT,
                flow.stage());
        fake.recoveryState=NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED;
        fake.recoveryGeneration=2;
        assertEquals(fake.recoveryState,flow.reconcile());
        assertEquals(NexoConnectOnboardingFlow.Stage.COMPLETE,flow.stage());
        assertEquals(2,flow.generation());
        assertEquals(1,fake.enrollCalls);
        assertEquals(2,fake.recoveryCalls);
    }
    @Test public void absentOrReplacedPhoneNeedsFreshOtpNeverReplays()throws Exception{
        for(NexoConnectDeviceRecoveryClient.State state:new NexoConnectDeviceRecoveryClient.State[]{
                NexoConnectDeviceRecoveryClient.State.NO_REGISTERED_DEVICE,
                NexoConnectDeviceRecoveryClient.State.ANOTHER_DEVICE}){
            Fake fake=new Fake();fake.rejectEnrollment=true;
            NexoConnectOnboardingFlow flow=new NexoConnectOnboardingFlow(fake);
            flow.requestLogin("user@example.org",false);
            flow.verifyLogin("111111");flow.requestDeviceCode(false);
            try{flow.confirmDevice("222222",true);fail("Unexpected response");}
            catch(java.io.IOException expected){}
            fake.recoveryState=state;
            assertEquals(state,flow.reconcile());
            assertEquals(NexoConnectOnboardingFlow.Stage.REQUEST_DEVICE_CODE,flow.stage());
            assertEquals(1,fake.enrollCalls);
            flow.requestDeviceCode(true);
            assertEquals(3,fake.mailCalls);
            assertEquals(NexoConnectOnboardingFlow.Stage.ENROLL_DEVICE,flow.stage());
        }
    }

    @Test public void missingSecondCodeDoesNotContactEnrollment()throws Exception{
        Fake fake=new Fake();NexoConnectOnboardingFlow flow=
                new NexoConnectOnboardingFlow(fake);
        flow.requestLogin("user@example.org",false);
        flow.verifyLogin("111111");flow.requestDeviceCode(false);
        try{flow.confirmDevice("123",true);fail("Short OTP");}
        catch(java.security.GeneralSecurityException expected){}
        assertEquals(0,fake.enrollCalls);
        assertEquals(NexoConnectOnboardingFlow.Stage.ENROLL_DEVICE,flow.stage());
    }
}
