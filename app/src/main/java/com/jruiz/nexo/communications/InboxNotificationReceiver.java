package com.jruiz.nexo.communications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jruiz.nexo.data.DatabaseHelper;

public class InboxNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_DISCARD = "com.jruiz.nexo.DISCARD_INBOX_CAPTURE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DISCARD.equals(intent.getAction())) return;
        long captureId = intent.getLongExtra("captureId", 0L);
        if (captureId <= 0) return;
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            db.discardInboxCapture(captureId);
        } finally {
            db.close();
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(captureId));
    }

    public static int notificationId(long captureId) {
        return 42000 + (int)(Math.abs(captureId) % 20000);
    }
}
