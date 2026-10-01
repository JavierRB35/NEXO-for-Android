package com.jruiz.nexo.recordings;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;

import org.json.JSONObject;

import java.io.File;

/** Servicio en primer plano que mantiene la grabación activa con pantalla apagada o Nexo en segundo plano. */
public final class RecordingService extends Service {
    public static final String ACTION_START = "com.jruiz.nexo.recordings.START";
    public static final String ACTION_PAUSE = "com.jruiz.nexo.recordings.PAUSE";
    public static final String ACTION_RESUME = "com.jruiz.nexo.recordings.RESUME";
    public static final String ACTION_STOP = "com.jruiz.nexo.recordings.STOP";

    private static final String CHANNEL_ID = "nexo_recording";
    private static final int NOTIFICATION_ID = 4091;
    private static final Object STATE_LOCK = new Object();

    private static boolean active = false;
    private static boolean paused = false;
    private static long startedAt = 0L;
    private static long pausedAt = 0L;
    private static long pausedAccumulatedMs = 0L;
    private static String activeId = "";
    private static String activeTitle = "";

    private MediaRecorder recorder;

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        try {
            if (ACTION_START.equals(action)) startRecording();
            else if (ACTION_PAUSE.equals(action)) pauseRecording();
            else if (ACTION_RESUME.equals(action)) resumeRecording();
            else if (ACTION_STOP.equals(action)) stopRecording(true);
        } catch (Exception e) {
            stopRecording(false);
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (isRecordingActive()) stopRecording(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    public static boolean isRecordingActive() {
        synchronized (STATE_LOCK) { return active; }
    }

    public static JSONObject status() {
        JSONObject o = new JSONObject();
        synchronized (STATE_LOCK) {
            try {
                o.put("active", active);
                o.put("paused", paused);
                o.put("id", activeId);
                o.put("title", activeTitle);
                o.put("startedAt", startedAt);
                long now = System.currentTimeMillis();
                long elapsed = active ? Math.max(0L, now - startedAt - pausedAccumulatedMs - (paused && pausedAt > 0 ? now - pausedAt : 0L)) : 0L;
                o.put("elapsedMs", elapsed);
            } catch (Exception ignored) {}
        }
        return o;
    }

    private void startRecording() throws Exception {
        synchronized (STATE_LOCK) { if (active) return; }

        long now = System.currentTimeMillis();
        JSONObject meta = RecordingStore.createMetadata(this, now);
        String id = meta.optString("id", RecordingStore.idFor(now));
        String title = meta.optString("title", RecordingStore.defaultTitle(now));
        File output = RecordingStore.audioFile(this, id);
        synchronized (STATE_LOCK) { activeId = id; activeTitle = title; }

        startForeground(NOTIFICATION_ID, buildNotification("Preparando grabación…", false));

        MediaRecorder r;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) r = new MediaRecorder(this);
        else {
            //noinspection deprecation
            r = new MediaRecorder();
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC);
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        r.setAudioChannels(1);
        r.setAudioSamplingRate(44100);
        r.setAudioEncodingBitRate(64000);
        r.setOutputFile(output.getAbsolutePath());
        r.prepare();
        r.start();
        recorder = r;

        synchronized (STATE_LOCK) {
            active = true;
            paused = false;
            startedAt = now;
            pausedAt = 0L;
            pausedAccumulatedMs = 0L;
            activeId = id;
            activeTitle = title;
        }
        updateNotification();
    }

    private void pauseRecording() {
        synchronized (STATE_LOCK) { if (!active || paused || recorder == null) return; }
        try {
            recorder.pause();
            synchronized (STATE_LOCK) { paused = true; pausedAt = System.currentTimeMillis(); }
            updateNotification();
        } catch (Exception ignored) {}
    }

    private void resumeRecording() {
        synchronized (STATE_LOCK) { if (!active || !paused || recorder == null) return; }
        try {
            recorder.resume();
            synchronized (STATE_LOCK) {
                if (pausedAt > 0) pausedAccumulatedMs += Math.max(0L, System.currentTimeMillis() - pausedAt);
                pausedAt = 0L;
                paused = false;
            }
            updateNotification();
        } catch (Exception ignored) {}
    }

    private void stopRecording(boolean keepIfValid) {
        MediaRecorder r = recorder;
        recorder = null;
        String id;
        synchronized (STATE_LOCK) { id = activeId; }
        boolean valid = false;
        if (r != null) {
            try { r.stop(); valid = true; } catch (RuntimeException ignored) {}
            try { r.reset(); } catch (Exception ignored) {}
            try { r.release(); } catch (Exception ignored) {}
        }
        if ((!keepIfValid || !valid) && id != null && !id.isBlank()) RecordingStore.delete(this, id);
        synchronized (STATE_LOCK) {
            active = false;
            paused = false;
            startedAt = 0L;
            pausedAt = 0L;
            pausedAccumulatedMs = 0L;
            activeId = "";
            activeTitle = "";
        }
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
        stopSelf();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Grabaciones de Nexo", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Mantiene una grabación activa sin emitir sonidos");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        JSONObject s = status();
        boolean isPaused = s.optBoolean("paused", false);
        String text = isPaused ? "Grabación en pausa" : "Grabando · toca para volver a Nexo";
        nm.notify(NOTIFICATION_ID, buildNotification(text, isPaused));
    }

    private Notification buildNotification(String text, boolean isPaused) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 701, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String toggleAction = isPaused ? ACTION_RESUME : ACTION_PAUSE;
        Intent toggle = new Intent(this, RecordingService.class).setAction(toggleAction);
        PendingIntent togglePi = PendingIntent.getService(this, 702, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 703, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification_nexo)
                .setContentTitle("Nexo · Grabación")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        // Icono 0 en las acciones: Android muestra el texto y evita recursos adicionales.
        b.addAction(0, isPaused ? "Continuar" : "Pausar", togglePi);
        b.addAction(0, "Terminar", stopPi);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(Notification.PRIORITY_LOW);
            b.setSound(null);
            b.setVibrate(new long[]{0L});
        }
        return b.build();
    }
}
