package com.jruiz.nexo.connect.vault;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LAB background notifications without a Firebase/push provider.
 *
 * Android periodically checks encrypted-delivery METADATA; execution is
 * deferred by Doze/battery scheduling and is NOT instant messaging.
 * No message body, keys, fingerprints, OTP, JWT, or document title in a
 * notification or local ledger. Job NEVER decrypts or imports a delivery.
 */
public final class NexoConnectInboxNoticeJob extends JobService {
    private static final String TAG="NexoConnectNotice";
    private static final String CHANNEL="nexo_connect_fichas_lab_v1";
    private static final String PREFS="nexo_connect_notified_deliveries_v1";
    private static final int JOB_ID=146801;
    private static final int NOTIFICATION_ID=146802;
    private static final long INTERVAL_MS=15L*60*1000;
    private volatile boolean stopped;

    public static void schedule(Context context) {
        if(context==null)throw new IllegalArgumentException("Missing app");
        Context app=context.getApplicationContext();
        if(!"com.jruiz.nexo.connect.pilot".equals(app.getPackageName()))
            return; // Embedded LAB host uses the same Java sources, not this service.
        NotificationManager notifications=
                (NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(notifications!=null && Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(
                    CHANNEL,"Fichas de Nexo Connect",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            notifications.createNotificationChannel(channel);
        }
        JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs==null)return;
        JobInfo scheduled=new JobInfo.Builder(JOB_ID,
                new ComponentName(app,NexoConnectInboxNoticeJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build();
        if(jobs.getPendingJob(JOB_ID)==null &&
                jobs.schedule(scheduled)!=JobScheduler.RESULT_SUCCESS)
            throw new IllegalStateException("Android refused inbox notice job");
    }

    /** Scheduling status, separate from notification permission state. */
    public static boolean isScheduled(Context context) {
        if(context==null ||
                !"com.jruiz.nexo.connect.pilot".equals(context.getPackageName()))
            return false;
        JobScheduler jobs=(JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        return jobs!=null && jobs.getPendingJob(JOB_ID)!=null;
    }

    /** May be disabled for an emulator; never bypass user notification consent. */
    public static boolean permitted(Context context) {
        if(Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        !=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=(NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null || !manager.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
            if(channel!=null &&
                    channel.getImportance()==NotificationManager.IMPORTANCE_NONE)
                return false;
        }
        return true;
    }

    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        new Thread(()->{
            try {
                if(!stopped && permitted(this))checkMetadataAndNotify();
            }catch(Exception unavailable) {
                // Log class only: no credentials, recipient names or payloads.
                Log.w(TAG,"Background check unavailable: "+
                        unavailable.getClass().getSimpleName());
            }finally {
                jobFinished(params,false); // Never loop or retry implicitly.
            }
        },"nexo-connect-background-inbox").start();
        return true;
    }

    private void checkMetadataAndNotify() throws Exception {
        NexoConnectSessionVault vault=new NexoConnectSessionVault(this);
        if(!vault.exists())return;
        // The worker must NOT mint a second identity when the phone vault
        // cannot be restored: only a manually enrolled device is eligible.
        NexoConnectSessionVault.Saved saved=vault.restore();
        if(!new AndroidIdentityVault(this,saved.accountId).exists())return;
        NexoConnectEmailOtpClient auth=new NexoConnectEmailOtpClient(
                NexoConnectPilotActivity.ORIGIN,NexoConnectPilotActivity.PUBLIC_KEY);
        NexoConnectEmailOtpClient.VerifiedSession session=
                NexoConnectSessionVault.refreshSavedSession(this,auth);
        if(stopped)return;
        NexoConnectLocalPhoneIdentity local=
                NexoConnectLocalPhoneIdentity.openOrCreate(this,session.accountId);
        NexoConnectDeviceRecoveryClient.Outcome confirmed=
                new NexoConnectDeviceRecoveryClient(
                    NexoConnectPilotActivity.ORIGIN,NexoConnectPilotActivity.PUBLIC_KEY,
                    session,session.accountId,local.deviceId(),
                    local.signingPrivate()).checkOnce();
        if(stopped || confirmed.state!=
                NexoConnectDeviceRecoveryClient.State.OWN_DEVICE_CONFIRMED)return;
        NexoConnectSignedGatewayClient client=new NexoConnectSignedGatewayClient(
                NexoConnectPilotActivity.ORIGIN,NexoConnectPilotActivity.PUBLIC_KEY,
                session.accessTokenFor(session.accountId,System.currentTimeMillis()),
                session.accountId,local.deviceId(),confirmed.generation,
                local.signingPrivate());
        JSONArray pending=client.inbox(20,null);
        if(stopped || pending.length()==0)return;

        SharedPreferences preferences=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String accountKey="seen:"+session.accountId;
        Set<String> seen=new HashSet<>(
                preferences.getStringSet(accountKey,new HashSet<>()));
        List<String> unseen=NexoConnectNotificationLedger.unseenIds(pending,seen);
        if(unseen.isEmpty() || stopped || !permitted(this))return;
        NotificationManager manager=
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        Intent open=new Intent(this,NexoConnectPilotActivity.class)
                .putExtra(NexoConnectPilotActivity.EXTRA_OPEN_INBOX,true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                        Intent.FLAG_ACTIVITY_CLEAR_TOP|
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String message=unseen.size()==1?
                "Tienes una ficha pendiente de revisar":
                "Tienes "+unseen.size()+" fichas pendientes de revisar";
        Notification notice=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Nexo Connect")
                .setContentText(message)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
        if(stopped)return;
        manager.notify(NOTIFICATION_ID,notice);
        seen.addAll(unseen);
        // Bounded metadata memory. Current pending records always retained;
        // old IDs can be dropped after they leave the 20-entry inbox window.
        if(seen.size()>512) {
            seen.clear();
            for(int i=0;i<pending.length();i++)
                seen.add(pending.getJSONObject(i).getString("deliveryId"));
        }
        preferences.edit().putStringSet(accountKey,seen).apply();
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped=true;
        return false; // Android schedules the next normal periodic check.
    }
}
