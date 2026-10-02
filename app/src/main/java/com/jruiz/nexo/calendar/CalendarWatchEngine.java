package com.jruiz.nexo.calendar;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CalendarContract;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;
import com.jruiz.nexo.communications.InboxNotificationReceiver;
import com.jruiz.nexo.communications.NexoNotificationListener;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CalendarWatchEngine {
    private static final String CHANNEL_ID = "nexo_inbox_candidates";
    private static final long PAST_WINDOW = 90L * 86400000L;
    private static final long FUTURE_WINDOW = 5L * 365L * 86400000L;

    private CalendarWatchEngine() {}

    public static void initializeBaseline(Context context) {
        if (!hasPermission(context)) return;
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            for (EventSnapshot event : readEvents(context)) {
                if (!event.deleted) db.rememberCalendarEvent(event.id, event.fingerprint, 0L, -1L);
            }
            CalendarCapturePrefs.setBaselined(context, true);
        } finally {
            db.close();
        }
        CalendarWatchScheduler.schedule(context);
    }

    public static void scan(Context context) {
        if (!hasPermission(context) || !CalendarCapturePrefs.isEnabled(context)) return;
        if (!CalendarCapturePrefs.isBaselined(context)) {
            initializeBaseline(context);
            return;
        }

        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            for (EventSnapshot event : readEvents(context)) {
                if (event.deleted) continue;
                JSONObject state = db.getCalendarWatchState(event.id);
                if (state.length() == 0) {
                    long linkedItem = db.findMatchingCalendarItem(event.title, normalizedDueAt(event), event.allDay);
                    long captureId = createOrUpdateCapture(context, db, event, 0L, linkedItem, "new");
                    if (captureId > 0L) {
                        db.rememberCalendarEvent(event.id, event.fingerprint, captureId, linkedItem);
                    }
                    continue;
                }

                String previous = state.optString("fingerprint", "");
                if (event.fingerprint.equals(previous)) {
                    long linkedItem = state.optLong("linkedItemId", 0L);
                    if (linkedItem <= 0L) {
                        linkedItem = db.findMatchingCalendarItem(event.title, normalizedDueAt(event), event.allDay);
                    }
                    if (linkedItem > 0L) {
                        db.rememberCalendarEvent(event.id, event.fingerprint, state.optLong("captureId", 0L), linkedItem);
                    } else {
                        db.touchCalendarEvent(event.id);
                    }
                    continue;
                }

                long pendingCapture = state.optLong("captureId", 0L);
                long linkedItem = state.optLong("linkedItemId", 0L);
                if (linkedItem <= 0L) {
                    linkedItem = db.findMatchingCalendarItem(event.title, normalizedDueAt(event), event.allDay);
                }
                long captureId = createOrUpdateCapture(context, db, event, pendingCapture, linkedItem, "updated");
                if (captureId > 0L) {
                    db.rememberCalendarEvent(event.id, event.fingerprint, captureId, linkedItem);
                }
            }
        } finally {
            db.close();
        }
    }

    private static long createOrUpdateCapture(
            Context context,
            DatabaseHelper db,
            EventSnapshot event,
            long existingCaptureId,
            long linkedItemId,
            String changeType
    ) {
        try {
            JSONObject classification = classification(event, linkedItemId, changeType);
            JSONObject in = new JSONObject();
            in.put("source", "calendar");
            in.put("mime", "text/calendar");
            in.put("subject", event.title);
            in.put("text", event.rawText());
            in.put("sourcePackage", "android.calendar");
            in.put("sender", "Calendar");
            in.put("sourceKey", "calendar:" + event.id + ":" + event.fingerprint);

            if (existingCaptureId > 0 && db.isInboxCapturePending(existingCaptureId)) {
                if (db.replaceInboxCapture(existingCaptureId, in, classification)) {
                    showCandidateNotification(context, existingCaptureId, event, changeType);
                    return existingCaptureId;
                }
            }

            long captureId = db.insertInboxCapture(in, classification);
            if (captureId > 0) showCandidateNotification(context, captureId, event, changeType);
            return captureId;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static long normalizedDueAt(EventSnapshot event) {
        if (event == null || event.start <= 0L) return 0L;
        if (!event.allDay) return event.start;
        LocalDate date = Instant.ofEpochMilli(event.start).atZone(ZoneOffset.UTC).toLocalDate();
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static JSONObject classification(EventSnapshot event, long linkedItemId, String changeType) {
        RuleClassifier.Result auto = RuleClassifier.classify(event.rawText());
        JSONObject o = auto.toJson();
        try {
            ZoneId zone = event.allDay ? ZoneOffset.UTC : ZoneId.systemDefault();
            LocalDate date = Instant.ofEpochMilli(event.start).atZone(zone).toLocalDate();
            String dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String startTime = event.allDay ? "" :
                    Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault()).toLocalTime()
                            .withSecond(0).withNano(0).toString();
            String endTime = (!event.allDay && event.end > event.start) ?
                    Instant.ofEpochMilli(event.end).atZone(ZoneId.systemDefault()).toLocalTime()
                            .withSecond(0).withNano(0).toString() : "";

            long dueAt = normalizedDueAt(event);

            o.put("type", "event");
            o.put("title", event.title.isBlank() ? "Evento de Calendar" : event.title);
            o.put("dueAt", dueAt);
            o.put("hasTime", !event.allDay);
            o.put("dueDate", dateText);
            o.put("dueTime", startTime);
            o.put("dueEndTime", endTime);
            o.put("durationMinutes", (!event.allDay && event.end > event.start)
                    ? Math.max(0, (event.end - event.start) / 60000L) : 0L);
            o.put("locationText", event.location);
            o.put("description", event.description);
            o.put("confidence", 0.99);
            o.put("needsReview", true);
            o.put("reason", "new".equals(changeType)
                    ? "Nuevo evento detectado en Calendar"
                    : "Cambio detectado en un evento de Calendar");
            o.put("calendarEventId", event.id);
            o.put("calendarChangeType", changeType);
            if (linkedItemId > 0) o.put("existingItemId", linkedItemId);
        } catch (Exception ignored) {}
        return o;
    }

    private static List<EventSnapshot> readEvents(Context context) {
        ArrayList<EventSnapshot> out = new ArrayList<>();
        if (!hasPermission(context)) return out;

        long now = System.currentTimeMillis();
        long from = now - PAST_WINDOW;
        long to = now + FUTURE_WINDOW;
        String[] projection = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.DELETED
        };
        String selection = "((" + CalendarContract.Events.DTSTART + ">=? AND " +
                CalendarContract.Events.DTSTART + "<=?) OR " +
                CalendarContract.Events.RRULE + " IS NOT NULL)";

        try (Cursor c = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                new String[]{String.valueOf(from), String.valueOf(to)},
                CalendarContract.Events.DTSTART + " ASC"
        )) {
            if (c == null) return out;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = value(c, 1);
                String description = value(c, 2);
                String location = value(c, 3);
                long start = c.isNull(4) ? 0L : c.getLong(4);
                long end = c.isNull(5) ? 0L : c.getLong(5);
                boolean allDay = !c.isNull(6) && c.getInt(6) != 0;
                String rrule = value(c, 7);
                boolean deleted = !c.isNull(8) && c.getInt(8) != 0;
                if (id <= 0 || start <= 0) continue;
                out.add(new EventSnapshot(id, title, description, location, start, end, allDay, rrule, deleted));
            }
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {}
        return out;
    }

    private static void showCandidateNotification(Context context, long captureId, EventSnapshot event, String changeType) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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

        String title = "new".equals(changeType)
                ? "Nuevo evento detectado en Calendar"
                : "Cambio detectado en Calendar";
        String preview = event.title;
        if (!event.location.isBlank()) preview += " · " + event.location;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(preview)
                .setStyle(new Notification.BigTextStyle().bigText(preview))
                .setAutoCancel(true)
                .setContentIntent(reviewPi)
                .addAction(new Notification.Action.Builder(null, "Revisar", reviewPi).build())
                .addAction(new Notification.Action.Builder(null, "Ignorar", discardPi).build());

        nm.notify(InboxNotificationReceiver.notificationId(captureId), b.build());
    }

    private static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static String value(Cursor c, int index) {
        return c.isNull(index) ? "" : String.valueOf(c.getString(index)).trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static final class EventSnapshot {
        final long id;
        final String title;
        final String description;
        final String location;
        final long start;
        final long end;
        final boolean allDay;
        final String rrule;
        final boolean deleted;
        final String fingerprint;

        EventSnapshot(long id, String title, String description, String location, long start, long end,
                      boolean allDay, String rrule, boolean deleted) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.location = location == null ? "" : location;
            this.start = start;
            this.end = end;
            this.allDay = allDay;
            this.rrule = rrule == null ? "" : rrule;
            this.deleted = deleted;
            this.fingerprint = sha256(
                    id + "\n" + this.title + "\n" + this.description + "\n" + this.location +
                            "\n" + start + "\n" + end + "\n" + allDay + "\n" + this.rrule
            );
        }

        String rawText() {
            StringBuilder b = new StringBuilder();
            if (!title.isBlank()) b.append(title);
            if (!description.isBlank()) {
                if (b.length() > 0) b.append("\n");
                b.append(description);
            }
            if (!location.isBlank()) {
                if (b.length() > 0) b.append("\n");
                b.append(location);
            }
            return b.length() == 0 ? "Evento de Calendar" : b.toString();
        }
    }
}
