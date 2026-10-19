package com.jruiz.nexo.communications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class NexoNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) onNotificationPosted(sbn);
            }
        } catch (Exception ignored) {}
    }

    @Override public void onListenerDisconnected() {
        super.onListenerDisconnected();
        try {
            requestRebind(new ComponentName(this, NexoNotificationListener.class));
        } catch (Exception ignored) {}
    }

    public static final String ACTION_REVIEW = "com.jruiz.nexo.REVIEW_INBOX_CAPTURE";
    private static final String CHANNEL_ID = "nexo_inbox_candidates";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        if (!CommunicationCapturePrefs.isPackageEnabled(this, pkg)) return;

        NotificationPayload payload = extractPayload(sbn);
        if (payload.text.isBlank()) return;

        DatabaseHelper db = new DatabaseHelper(getApplicationContext());
        try {
            JSONObject classification = RuleClassifier.classify(payload.text).toJson();

            // Gmail mantiene NotificationListener como salvavidas, pero debe respetar
            // el mismo filtro contextual v7 que la API directa para no reintroducir
            // falsos positivos por la vía de notificaciones.
            String appKey = CommunicationCapturePrefs.keyForPackage(pkg);
            boolean candidate = "gmail".equals(appKey)
                    ? NotificationCandidateFilter.shouldCaptureGmail(payload.text, classification)
                    : NotificationCandidateFilter.shouldCapture(payload.text, classification);
            if (!candidate) return;

            // Gmail y SMS mantienen NotificationListener como vía de respaldo aunque
            // su proveedor directo esté activo. Evitamos duplicar la misma comunicación.
            String sourceFamily = "gmail".equals(appKey) ? "gmail" : ("sms".equals(appKey) ? "sms" : "");
            if (!sourceFamily.isBlank()) {
                long equivalent = db.findRecentEquivalentInboxCapture(
                        sourceFamily, payload.sender, payload.text, 45L * 60L * 1000L
                );
                if (equivalent > 0L) return;
            }

            // El grupo identifica la conversación, no al autor del mensaje.
            // Guardar en la clasificación deja intactos los datos históricos del Inbox.
            if (!payload.group.isBlank()) classification.put("sourceGroup", payload.group);
            String sourceKey = fingerprint(pkg, payload.sender, payload.text, sbn.getPostTime());
            if (db.inboxCaptureExists(sourceKey)) return;

            JSONObject in = new JSONObject();
            in.put("source", "notification");
            in.put("mime", "text/plain");
            in.put("subject", payload.sender);
            in.put("text", payload.text);
            in.put("sourcePackage", pkg);
            in.put("sender", payload.sender);
            in.put("sourceKey", sourceKey);

            long captureId = db.insertInboxCapture(in, classification);
            if (captureId > 0) showCandidateNotification(captureId, pkg, payload.sender, payload.text, classification);
        } catch (Exception ignored) {
        } finally {
            db.close();
        }
    }

    private NotificationPayload extractPayload(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        String title = text(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE));
        String group = text(extras == null ? null : extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        boolean messageSenderFound = false;
        String body = "";

        if (extras != null) {
            body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (body.isBlank()) body = text(extras.getCharSequence(Notification.EXTRA_TEXT));

            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if ((body.isBlank() || body.length() < 8) && lines != null && lines.length > 0) {
                StringBuilder b = new StringBuilder();
                for (CharSequence line : lines) {
                    String s = text(line);
                    if (s.isBlank()) continue;
                    if (b.length() > 0) b.append("\n");
                    b.append(s);
                }
                if (b.length() > 0) body = b.toString();
            }

            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                    if (bundles != null && bundles.length > 0) {
                        List<Notification.MessagingStyle.Message> messages =
                                Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
                        if (messages != null && !messages.isEmpty()) {
                            Notification.MessagingStyle.Message last = messages.get(messages.size() - 1);
                            if (last != null) {
                                String latest = text(last.getText());
                                if (!latest.isBlank()) body = latest;
                                String lastSender;
                                if (Build.VERSION.SDK_INT >= 28 && last.getSenderPerson() != null) {
                                    lastSender = text(last.getSenderPerson().getName());
                                } else {
                                    lastSender = text(last.getSender());
                                }
                                if (!lastSender.isBlank()) {
                                    title = lastSender;
                                    messageSenderFound = true;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        body = body.trim();
        // Algunos avisos usan el título para el grupo; no inventar que el grupo
        // es la persona remitente cuando MessagingStyle no aporta autor.
        if (!messageSenderFound && !group.isBlank() && group.equalsIgnoreCase(title)) title = "";
        if (body.equals(title)) title = "";
        return new NotificationPayload(title.trim(), body, group);
    }

    private void showCandidateNotification(long captureId, String pkg, String sender, String body, JSONObject classification) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID,
                    "Nexo Inbox",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            c.setDescription("Propuestas de Nexo para revisar información recibida");
            nm.createNotificationChannel(c);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent review = new Intent(this, MainActivity.class);
        review.setAction(ACTION_REVIEW);
        review.putExtra("captureId", captureId);
        review.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent reviewPi = PendingIntent.getActivity(
                this,
                InboxNotificationReceiver.notificationId(captureId),
                review,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent discard = new Intent(this, InboxNotificationReceiver.class);
        discard.setAction(InboxNotificationReceiver.ACTION_DISCARD);
        discard.putExtra("captureId", captureId);
        PendingIntent discardPi = PendingIntent.getBroadcast(
                this,
                InboxNotificationReceiver.notificationId(captureId) + 1,
                discard,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String appKey = CommunicationCapturePrefs.keyForPackage(pkg);
        String app = CommunicationCapturePrefs.labelForKey(appKey);
        String type = classification == null ? "" : classification.optString("type", "");
        String title = type.isBlank() || "note".equals(type)
                ? "Nexo ha detectado algo útil"
                : "Nexo · posible " + typeLabel(type);
        String who = sender == null || sender.isBlank() ? app : app + " · " + sender;
        String preview = body.length() > 120 ? body.substring(0, 117) + "…" : body;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setSubText(who)
                .setContentText(preview)
                .setStyle(new Notification.BigTextStyle().bigText(preview))
                .setAutoCancel(true)
                .setContentIntent(reviewPi)
                .addAction(new Notification.Action.Builder(null, "Revisar", reviewPi).build())
                .addAction(new Notification.Action.Builder(null, "Ignorar", discardPi).build());

        nm.notify(InboxNotificationReceiver.notificationId(captureId), b.build());
    }

    private static String typeLabel(String type) {
        if ("event".equals(type) || "task".equals(type) || "reminder".equals(type) || "expiry".equals(type)) return "cita";
        if ("place".equals(type)) return "lugar";
        if ("restaurant".equals(type)) return "restaurante";
        if ("document".equals(type)) return "documento";
        return "acción";
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String fingerprint(String pkg, String sender, String body, long postTime) {
        long bucket = Math.max(0L, postTime) / 300000L;
        String raw = pkg + "\n" + sender + "\n" + body + "\n" + bucket;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static final class NotificationPayload {
        final String sender;
        final String text;
        final String group;
        NotificationPayload(String sender, String text, String group) {
            this.sender = sender == null ? "" : sender;
            this.text = text == null ? "" : text;
            this.group = group == null ? "" : group;
        }
    }
}
