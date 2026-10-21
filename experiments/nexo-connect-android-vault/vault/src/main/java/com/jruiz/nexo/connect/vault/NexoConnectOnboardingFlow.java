package com.jruiz.nexo.connect.vault;

import com.google.crypto.tink.KeysetHandle;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * LAB ONLY. User-visible two-code account setup, never phone enrollment by JWT
 * alone. All operations run on worker threads when used by the Android view.
 *
 * One OTP logs into Supabase Auth; a SECOND OTP must be issued and consumed
 * by the Edge gateway for secure device enrollment or replacement.
 */
public final class NexoConnectOnboardingFlow {
    public enum Stage {
        REQUEST_LOGIN_CODE, VERIFY_LOGIN_CODE, REQUEST_DEVICE_CODE,
        ENROLL_DEVICE, COMPLETE, RECOVER_FROM_UNKNOWN_RESULT
    }

    public interface Backend {
        void sendEmailOtp(String email,boolean createAccount)throws Exception;
        VerifiedAccount verifyLoginOtp(String email,String code)throws Exception;
        int enrollDevice(VerifiedAccount verified,String freshDeviceOtp,
                boolean replacement,boolean approved)throws Exception;
        /** Explicit, server-backed reconciliation; default FAILS CLOSED. */
        default NexoConnectDeviceRecoveryClient.Outcome reconcile(
                VerifiedAccount verified)throws Exception{
            throw new GeneralSecurityException("Device recovery unavailable");
        }
    }
    public static final class VerifiedAccount {
        public final UUID accountId;
        public final String email;
        public VerifiedAccount(UUID accountId,String email){
            if(accountId==null||email==null||email.isEmpty())
                throw new IllegalArgumentException("Unverified account");
            this.accountId=accountId;this.email=email;
        }
    }

    /**
     * Real backend. Keys must be loaded only from a previously persisted
     * AndroidIdentityVault. Caller MUST validate both keys match the active
     * local vault and must not switch them on an uncertain network response.
     */
    public static Backend liveBackend(String origin,String publicKey,
            UUID expectedAccount,UUID newDevice,
            KeysetHandle signingPrivate,KeysetHandle hpkePrivate)throws Exception {
        if(expectedAccount==null||newDevice==null||
                signingPrivate==null||hpkePrivate==null)
            throw new GeneralSecurityException("Enrollment keys/account missing");
        NexoConnectEmailOtpClient auth=new NexoConnectEmailOtpClient(
                origin,publicKey);
        return new Backend(){
            private NexoConnectEmailOtpClient.VerifiedSession verifiedSession;
            @Override public void sendEmailOtp(String email,boolean createAccount)
                    throws Exception {
                auth.sendEmailCode(email,createAccount);
            }
            @Override public VerifiedAccount verifyLoginOtp(String email,String code)
                    throws Exception {
                NexoConnectEmailOtpClient.VerifiedSession session=
                        auth.verifyEmailCode(email,code);
                if(!expectedAccount.equals(session.accountId))
                    throw new GeneralSecurityException("Unexpected session account");
                verifiedSession=session;
                return new VerifiedAccount(session.accountId,session.email);
            }
            @Override public NexoConnectDeviceRecoveryClient.Outcome reconcile(
                    VerifiedAccount verified)throws Exception {
                if(verifiedSession==null||
                        !verifiedSession.accountId.equals(verified.accountId))
                    throw new GeneralSecurityException("Recovery account mismatch");
                return new NexoConnectDeviceRecoveryClient(
                        origin,publicKey,verifiedSession,verified.accountId,
                        newDevice,signingPrivate).checkOnce();
            }
            @Override public int enrollDevice(VerifiedAccount verified,String deviceOtp,
                    boolean replacement,boolean approved)throws Exception {
                NexoConnectEmailOtpClient.VerifiedSession active=verifiedSession;
                if(active==null||!active.accountId.equals(verified.accountId))
                    throw new GeneralSecurityException("Session changed");
                NexoConnectDeviceEnrollmentClient client=
                        new NexoConnectDeviceEnrollmentClient(origin,publicKey,
                                active,verified.accountId,newDevice,
                                signingPrivate,hpkePrivate);
                return client.enroll(deviceOtp,replacement,approved);
            }
        };
    }

    private final Backend backend;
    private Stage stage=Stage.REQUEST_LOGIN_CODE;
    private String email;
    private VerifiedAccount account;
    private boolean replace;
    private int generation;

    public NexoConnectOnboardingFlow(Backend backend){
        if(backend==null)throw new IllegalArgumentException("Backend missing");
        this.backend=backend;
    }
    public synchronized Stage stage(){return stage;}
    public synchronized UUID accountId(){
        return account==null?null:account.accountId;
    }
    public synchronized int generation(){return generation;}
    public synchronized boolean replacement(){return replace;}
    private NexoConnectDeviceRecoveryClient.State lastRecovery;
    public synchronized NexoConnectDeviceRecoveryClient.State lastRecovery(){
        return lastRecovery;
    }

    /**
     * After an uncertain device-enrollment POST: check server status and
     * Ed25519 proof, without sending an OTP again. User initiates manually.
     */
    public synchronized NexoConnectDeviceRecoveryClient.State reconcile()
            throws Exception {
        requireStage(Stage.RECOVER_FROM_UNKNOWN_RESULT);
        NexoConnectDeviceRecoveryClient.Outcome result=backend.reconcile(account);
        if(result==null||result.state==null)
            throw new GeneralSecurityException("Unknown remote status");
        lastRecovery=result.state;
        switch(result.state){
            case OWN_DEVICE_CONFIRMED:
                if(result.generation<=0||(replace&&result.generation<2)||
                        (!replace&&result.generation!=1))
                    throw new GeneralSecurityException("Recovery generation mismatch");
                generation=result.generation;
                stage=Stage.COMPLETE;
                break;
            case NO_REGISTERED_DEVICE:
            case ANOTHER_DEVICE:
                // Never retry the old second OTP. User must explicitly
                // request a NEW code and choose replacement again.
                generation=0;
                stage=Stage.REQUEST_DEVICE_CODE;
                break;
            default:
                // Inconclusive: stay blocked to avoid duplicate enrollment.
        }
        return result.state;
    }

    /** Auth sends first OTP; account creation must be an explicit user choice. */
    public synchronized void requestLogin(String email,boolean createAccount)
            throws Exception {
        requireStage(Stage.REQUEST_LOGIN_CODE);
        if(email==null||email.trim().isEmpty())
            throw new GeneralSecurityException("Email required");
        backend.sendEmailOtp(email,createAccount);
        this.email=email;
        stage=Stage.VERIFY_LOGIN_CODE;
    }

    public synchronized void verifyLogin(String code)throws Exception{
        requireStage(Stage.VERIFY_LOGIN_CODE);
        VerifiedAccount verified=backend.verifyLoginOtp(email,code);
        if(verified==null||verified.accountId==null)
            throw new GeneralSecurityException("Account not verified");
        account=verified;
        stage=Stage.REQUEST_DEVICE_CODE;
    }

    /** Fresh OTP, separate from the first consumed login OTP. */
    public synchronized void requestDeviceCode(boolean replacement)throws Exception{
        requireStage(Stage.REQUEST_DEVICE_CODE);
        if(account==null)throw new GeneralSecurityException("No verified Auth account");
        backend.sendEmailOtp(account.email,false);
        replace=replacement;
        stage=Stage.ENROLL_DEVICE;
    }

    /**
     * A request with an uncertain HTTP outcome is NEVER automatically retried
     * with the same OTP. Explicit recovery requires server status check and
     * fresh code. Mark that work as incomplete until reconciliation exists.
     */
    public synchronized int confirmDevice(String secondCode,boolean approved)
            throws Exception {
        requireStage(Stage.ENROLL_DEVICE);
        if(!approved)throw new SecurityException("Enrollment requires consent");
        if(secondCode==null||!secondCode.matches("[0-9]{6,8}"))
            throw new GeneralSecurityException("Invalid fresh OTP");
        try {
            int confirmed=backend.enrollDevice(account,secondCode,replace,true);
            if(confirmed<=0||(replace&&confirmed<2)||(!replace&&confirmed!=1))
                throw new GeneralSecurityException("Server generation invalid");
            generation=confirmed;
            stage=Stage.COMPLETE;
            return confirmed;
        } catch(Exception exception){
            // Even if server returns a 403, avoid cycling the same code. User
            // must start fresh after reconciliation, never loop or retry.
            stage=Stage.RECOVER_FROM_UNKNOWN_RESULT;
            throw exception;
        }
    }
    private void requireStage(Stage expected){
        if(stage!=expected)throw new IllegalStateException(
                "Operation forbidden in stage "+stage);
    }
}
