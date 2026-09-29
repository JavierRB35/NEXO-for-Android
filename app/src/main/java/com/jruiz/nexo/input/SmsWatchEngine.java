package com.jruiz.nexo.input;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;

import com.jruiz.nexo.communications.NotificationCandidateFilter;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SmsWatchEngine {
    private static final long WINDOW_MS = 90L * 86400000L;
    private static final long RECOVERY_MS = 3L * 86400000L;
    private static final String FILTER_REVISION = "sms-filter-v5:";

    private SmsWatchEngine() {}

    public static void initializeBaseline(Context context) {
        if (!hasPermission(context) || !InputHubPrefs.smsEnabled(context)) return;
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            List<SmsSnapshot> messages = readInbox(context);
            // Se conserva el baseline para no importar meses de SMS históricos.
            for (SmsSnapshot sms : messages) {
                if (!db.hasExternalInput("sms", sms.id)) {
                    db.rememberExternalInput("sms", sms.id, "sms-baseline:" + sms.fingerprint(), 0L);
                }
            }
            InputHubPrefs.setSmsBaselined(context, true);
            // Pero los mensajes accionables muy recientes no deben perderse por haber
            // activado/reautorizado la fuente después de recibirlos.
            recoverRecent(context, db, messages);
            InputHubPrefs.setSmsLastSync(context, System.currentTimeMillis());
            InputHubPrefs.setSmsError(context, "");
        } catch (Exception e) {
            InputHubPrefs.setSmsError(context, safeMessage(e));
        } finally {
            db.close();
        }
        SmsWatchScheduler.schedule(context);
    }

    public static void scan(Context context) {
        if (!hasPermission(context) || !InputHubPrefs.smsEnabled(context)) return;
        if (!InputHubPrefs.smsBaselined(context)) {
            initializeBaseline(context);
            return;
        }

        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            List<SmsSnapshot> messages = readInbox(context);
            long recoveryFrom = System.currentTimeMillis() - RECOVERY_MS;
            for (SmsSnapshot sms : messages) {
                JSONObject previous = db.getExternalInputState("sms", sms.id);
                if (previous.length() > 0) {
                    long oldCapture = previous.optLong("captureId", 0L);
                    String oldFingerprint = previous.optString("fingerprint", "");
                    if (oldCapture > 0 || db.inboxCaptureExists("sms:" + sms.id) || oldFingerprint.startsWith(FILTER_REVISION)) continue;
                    if (sms.date < recoveryFrom) continue;
                }
                processSms(context, db, sms);
            }
            InputHubPrefs.setSmsLastSync(context, System.currentTimeMillis());
            InputHubPrefs.setSmsError(context, "");
        } catch (Exception e) {
            InputHubPrefs.setSmsError(context, safeMessage(e));
        } finally {
            db.close();
        }
    }

    private static void recoverRecent(Context context, DatabaseHelper db, List<SmsSnapshot> messages) {
        long from = System.currentTimeMillis() - RECOVERY_MS;
        for (SmsSnapshot sms : messages) {
            if (sms.date < from) continue;
            JSONObject previous = db.getExternalInputState("sms", sms.id);
            if (previous.optLong("captureId", 0L) > 0 || db.inboxCaptureExists("sms:" + sms.id)) continue;
            String fp = previous.optString("fingerprint", "");
            if (fp.startsWith(FILTER_REVISION)) continue;
            processSms(context, db, sms);
        }
    }

    private static void processSms(Context context, DatabaseHelper db, SmsSnapshot sms) {
        long captureId = 0L;
        try {
            JSONObject classification = RuleClassifier.classify(sms.body).toJson();
            if (NotificationCandidateFilter.shouldCapture(sms.body, classification)) {
                JSONObject in = new JSONObject();
                in.put("source", "sms-provider");
                in.put("mime", "text/plain");
                in.put("subject", sms.address);
                in.put("text", sms.body);
                in.put("sourcePackage", "android.sms");
                in.put("sender", sms.address);
                in.put("sourceKey", "sms:" + sms.id);
                captureId = db.insertInboxCapture(in, classification);
                if (captureId > 0) {
                    String preview = sms.body.length() > 140 ? sms.body.substring(0, 137) + "…" : sms.body;
                    InboxCandidateNotifier.show(context, captureId, "Nexo · SMS accionable", sms.address, preview);
                }
            }
        } catch (Exception ignored) {}
        db.rememberExternalInput("sms", sms.id, FILTER_REVISION + sms.fingerprint(), captureId);
    }

    private static List<SmsSnapshot> readInbox(Context context) {
        ArrayList<SmsSnapshot> out = new ArrayList<>();
        if (!hasPermission(context)) return out;
        long from = System.currentTimeMillis() - WINDOW_MS;
        String[] projection = new String[]{
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
        };
        try (Cursor c = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                Telephony.Sms.DATE + ">=?",
                new String[]{String.valueOf(from)},
                Telephony.Sms.DATE + " DESC"
        )) {
            if (c == null) return out;
            int limit = 500;
            while (c.moveToNext() && limit-- > 0) {
                String id = String.valueOf(c.getLong(0));
                String address = c.isNull(1) ? "" : c.getString(1);
                String body = c.isNull(2) ? "" : c.getString(2);
                long date = c.isNull(3) ? 0L : c.getLong(3);
                if (!id.isBlank() && !body.isBlank()) out.add(new SmsSnapshot(id, address, body, date));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String safeMessage(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage());
        return m == null || m.isBlank() ? "No se pudo consultar los SMS." : m;
    }

    private static final class SmsSnapshot {
        final String id, address, body;
        final long date;
        SmsSnapshot(String id, String address, String body, long date) {
            this.id=id;this.address=address==null?"":address;this.body=body==null?"":body;this.date=date;
        }
        String fingerprint() { return Integer.toHexString((id+"\n"+address+"\n"+body+"\n"+date).hashCode()); }
    }
}
