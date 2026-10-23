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
 * Android periodically checks pending deliveries; execution is
 * deferred by Doze/battery scheduling and is NOT instant messaging.
 * No message body, keys, fingerprints, OTP, JWT, or document title in a
 * notification or local ledger. The integrated host receives authenticated chat
 * messages without marking them read; control receipts never produce notifications.
 */
public final class NexoConnectInboxNoticeJob extends JobService {
    private static final String TAG="NexoConnectNotice";
    private static final String CHANNEL="nexo_connect_chat_v2";
    private static final String PREFS="nexo_connect_notified_deliveries_v1";
    private static final int JOB_ID=146801;
    private static final int FOREGROUND_JOB_ID=146803;
    private static final int NOTIFICATION_ID=146802;
    private static final long INTERVAL_MS=15L*60*1000;
    private volatile boolean stopped;
    private static final java.util.concurrent.atomic.AtomicBoolean CHECKING=new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void schedule(Context context) {
        if(context==null)throw new IllegalArgumentException("Missing app");
        Context app=context.getApplicationContext();
        if(NexoConnectMigration.blocked(app)||(!"com.jruiz.nexo".equals(app.getPackageName())&&!"com.jruiz.nexo.connect.pilot".equals(app.getPackageName())))
            return; // Embedded LAB host uses the same Java sources, not this service.
        NotificationManager notifications=
                (NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(notifications!=null && Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(
                    CHANNEL,"Mensajes y fichas de Nexo Connect",NotificationManager.IMPORTANCE_DEFAULT);
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

    /** An app-visible refresh: the ordinary periodic 15-minute job remains
     * scheduled for the background, without a permanent foreground service. */
    public static void checkSoon(Context context){
        if(context==null||NexoConnectMigration.blocked(context)||
                !new NexoConnectSessionVault(context).exists())return;
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs==null||jobs.getPendingJob(FOREGROUND_JOB_ID)!=null)return;
        JobInfo request=new JobInfo.Builder(FOREGROUND_JOB_ID,
                new ComponentName(context,NexoConnectInboxNoticeJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(10000)
                .build();
        jobs.schedule(request);
    }

    /** Scheduling status, separate from notification permission state. */
    public static boolean isScheduled(Context context) {
        if(context==null ||
                (!"com.jruiz.nexo".equals(context.getPackageName())&&!"com.jruiz.nexo.connect.pilot".equals(context.getPackageName())))
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
                if(!stopped&&CHECKING.compareAndSet(false,true)){
                    try{checkMetadataAndNotify();}finally{CHECKING.set(false);}
                } // Unread sync is independent of notification consent.
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
        if(stopped)return;

        // Import encrypted chat messages before counting. Incoming message
        // deliveries leave the server inbox when accepted, so the notification
        // must use the durable local unread state, not pending.length().
        java.util.LinkedHashSet<String> candidates=new java.util.LinkedHashSet<>();
        int unreadChats=0;
        if("com.jruiz.nexo".equals(getPackageName())){
            AndroidPeerTrustStore trust=new AndroidPeerTrustStore(this,session.accountId);
            synchronized(NexoConnectSessionVault.REFRESH_LOCK){
                NexoConnectChatTransport.sync(this,client,local,trust,confirmed.generation);
            }
            try(android.database.sqlite.SQLiteDatabase db=NexoConnectMigration.inbox(this)){
                java.util.List<String> unread=NexoConnectChatStore.unreadIds(db,session.accountId,trust);
                unreadChats=unread.size();
                candidates.addAll(unread);
                for(int i=0;i<pending.length();i++){
                    org.json.JSONObject item=pending.getJSONObject(i);
                    UUID delivery=UUID.fromString(item.getString("deliveryId"));
                    boolean chat=false;
                    try(android.database.Cursor row=db.rawQuery(
                            "SELECT i.body FROM nexo_connect_import_receipts r "+
                            "JOIN items i ON i.id=r.local_id "+
                            "WHERE r.delivery_id=? AND r.entity_type='note'",
                            new String[]{delivery.toString()})){
                        if(row.moveToFirst()){
                            org.json.JSONObject data=new NexoConnectSealedNoteStore(
                                    session.accountId,delivery).read(row.getString(0));
                            chat=NexoConnectChatStore.CATEGORY.equals(data.optString("category"));
                        }
                    }
                    // Chat control/profile/read receipts must never alert; a
                    // chat message is counted only when it remains unread.
                    if(!chat)candidates.add(delivery.toString());
                }
            }
        }else{
            for(int i=0;i<pending.length();i++)
                candidates.add(pending.getJSONObject(i).getString("deliveryId"));
        }
        if(candidates.isEmpty()||stopped||!permitted(this))return;

        SharedPreferences preferences=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String accountKey="seen:"+session.accountId;
        Set<String> seen=new HashSet<>(
                preferences.getStringSet(accountKey,new HashSet<>()));
        java.util.List<String> unseen=new java.util.ArrayList<>();
        for(String id:candidates)if(!seen.contains(id))unseen.add(id);
        if(unseen.isEmpty())return;
        NotificationManager manager=
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        // Existing installations may already have a scheduled job from the
        // previous version; create the new channel before posting its first alert.
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(
                    CHANNEL,"Mensajes y fichas de Nexo Connect",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            manager.createNotificationChannel(channel);
        }
        boolean chatNotification=unreadChats>0;
        Intent open=new Intent().setClassName(getPackageName(),
                "com.jruiz.nexo".equals(getPackageName())?
                "com.jruiz.nexo.connectui.NexoConnectActivity":
                NexoConnectPilotActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                        Intent.FLAG_ACTIVITY_CLEAR_TOP|
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(chatNotification)open.putExtra("openChat",true);
        else open.putExtra(NexoConnectPilotActivity.EXTRA_OPEN_INBOX,true);
        PendingIntent tap=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String message=chatNotification?
                (unreadChats==1?"Tienes un mensaje de chat sin leer":
                        "Tienes "+unreadChats+" mensajes de chat sin leer"):
                (unseen.size()==1?"Tienes una ficha nueva":
                        "Tienes "+unseen.size()+" fichas nuevas");
        Notification notice=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Nexo Connect")
                .setContentText(message)
                .setContentIntent(tap)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setNumber(chatNotification?unreadChats:unseen.size())
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
        if(stopped)return;
        manager.notify(NOTIFICATION_ID,notice);
        seen.addAll(unseen);
        if(seen.size()>512) {
            seen.clear();
            seen.addAll(candidates);
        }
        preferences.edit().putStringSet(accountKey,seen).apply();
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped=true;
        return false; // Android schedules the next normal periodic check.
    }
}
