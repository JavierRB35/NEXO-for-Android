package com.jruiz.nexo.input;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;
import com.jruiz.nexo.communications.InboxNotificationReceiver;
import com.jruiz.nexo.communications.NexoNotificationListener;

public final class InboxCandidateNotifier {
    private static final String CHANNEL_ID = "nexo_inbox_candidates";

    private InboxCandidateNotifier() {}

    public static void show(Context context, long captureId, String title, String subText, String preview) {
        if (captureId <= 0) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID, "Nexo Inbox", NotificationManager.IMPORTANCE_DEFAULT
            );
            c.setDescription("Propuestas de Nexo para revisar información recibida");
            nm.createNotificationChannel(c);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent review = new Intent(context, MainActivity.class);
        review.setAction(NexoNotificationListener.ACTION_REVIEW);
        review.putExtra("captureId", captureId);
        review.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent reviewPi = PendingIntent.getActivity(
                context,
                InboxNotificationReceiver.notificationId(captureId),
                review,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent discard = new Intent(context, InboxNotificationReceiver.class);
        discard.setAction(InboxNotificationReceiver.ACTION_DISCARD);
        discard.putExtra("captureId", captureId);
        PendingIntent discardPi = PendingIntent.getBroadcast(
                context,
                InboxNotificationReceiver.notificationId(captureId) + 1,
                discard,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null || title.isBlank() ? "Nexo ha detectado algo útil" : title)
                .setContentText(preview == null ? "" : preview)
                .setSubText(subText == null ? "" : subText)
                .setStyle(new Notification.BigTextStyle().bigText(preview == null ? "" : preview))
                .setAutoCancel(true)
                .setContentIntent(reviewPi)
                .addAction(new Notification.Action.Builder(null, "Revisar", reviewPi).build())
                .addAction(new Notification.Action.Builder(null, "Ignorar", discardPi).build());

        nm.notify(InboxNotificationReceiver.notificationId(captureId), b.build());
    }
}
