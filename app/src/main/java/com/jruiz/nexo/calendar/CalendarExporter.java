package com.jruiz.nexo.calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Genera eventos iCalendar (.ics) portables para Google Calendar y otras apps. */
public final class CalendarExporter {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private CalendarExporter() {}

    /**
     * Crea un .ics en MediaStore/Download/Nexo y devuelve un content:// Uri compartible.
     * Se usa MediaStore en lugar de un ContentProvider propio para evitar dependencias de
     * recursos/manifest y mejorar la robustez de compilación en AndroidIDE.
     */
    public static Uri export(Context context, JSONObject item, JSONArray leadMinutesList, boolean includeRecurrence) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new UnsupportedOperationException("Compartir .ics requiere Android 10 o superior");
        }

        long dueAt = item.optLong("dueAt", 0);
        if (dueAt <= 0) throw new IllegalArgumentException("El elemento no tiene fecha");

        boolean hasTime = item.optBoolean("hasTime", false);
        int durationMinutes = Math.max(0, item.optInt("durationMinutes", 0));
        if (hasTime && durationMinutes <= 0) durationMinutes = 60;

        String recurrence = includeRecurrence ? item.optString("recurrence", "none") : "none";
        long recurrenceUntil = includeRecurrence ? item.optLong("recurrenceUntil", 0) : 0;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime startLocal = Instant.ofEpochMilli(dueAt).atZone(zone);

        StringBuilder ics = new StringBuilder(1024);
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Nexo//Personal Control Center//ES");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");

        long id = item.optLong("seriesId", item.optLong("id", System.currentTimeMillis()));
        long occurrence = item.optLong("occurrenceAt", dueAt);
        String uidSuffix = includeRecurrence && !"none".equals(recurrence) ? "series" : String.valueOf(occurrence);
        line(ics, "UID:nexo-" + id + "-" + uidSuffix + "@nexo.local");
        line(ics, "DTSTAMP:" + UTC.format(Instant.now()));
        line(ics, "SUMMARY:" + escape(item.optString("title", "Evento de Nexo")));

        String description = item.optString("body", "").trim();
        if (!description.isEmpty()) line(ics, "DESCRIPTION:" + escape(description));
        String location = item.optString("locationText", "").trim();
        if (!location.isEmpty()) line(ics, "LOCATION:" + escape(location));

        if (hasTime) {
            Instant start = Instant.ofEpochMilli(dueAt);
            Instant end = start.plusSeconds(durationMinutes * 60L);
            line(ics, "DTSTART:" + UTC.format(start));
            line(ics, "DTEND:" + UTC.format(end));
        } else {
            LocalDate date = startLocal.toLocalDate();
            line(ics, "DTSTART;VALUE=DATE:" + DATE.format(date));
            // En iCalendar el final de un evento de día completo es exclusivo.
            line(ics, "DTEND;VALUE=DATE:" + DATE.format(date.plusDays(1)));
        }

        String rrule = recurrenceRule(recurrence, recurrenceUntil, hasTime, zone);
        if (!rrule.isEmpty()) line(ics, "RRULE:" + rrule);

        if (leadMinutesList != null) {
            java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>();
            for (int i=0;i<leadMinutesList.length();i++) {
                int minutes=leadMinutesList.optInt(i,-1); if(minutes>=0) unique.add(minutes);
            }
            for (Integer minutes:unique) {
                line(ics, "BEGIN:VALARM");
                line(ics, "ACTION:DISPLAY");
                line(ics, "DESCRIPTION:" + escape(item.optString("title", "Recordatorio de Nexo")));
                line(ics, "TRIGGER:" + alarmTrigger(minutes));
                line(ics, "END:VALARM");
            }
        }

        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");

        String safeTitle = sanitize(item.optString("title", "evento"));
        String fileName = safeTitle + "_" + System.currentTimeMillis() + ".ics";

        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Nexo");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("No se pudo crear el archivo de calendario");

        boolean ok = false;
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("No se pudo abrir el archivo de calendario");
            out.write(ics.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            ok = true;
        } finally {
            if (!ok) {
                try { resolver.delete(uri, null, null); } catch (Exception ignored) {}
            }
        }

        ContentValues ready = new ContentValues();
        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, ready, null, null);
        return uri;
    }


    private static String alarmTrigger(int minutes) {
        if (minutes <= 0) return "PT0M";
        if (minutes % 1440 == 0) return "-P" + (minutes / 1440) + "D";
        if (minutes % 60 == 0) return "-PT" + (minutes / 60) + "H";
        return "-PT" + minutes + "M";
    }

    private static String recurrenceRule(String recurrence, long untilMs, boolean hasTime, ZoneId zone) {
        if (recurrence == null || recurrence.isBlank() || "none".equals(recurrence)) return "";
        String core;
        switch (recurrence) {
            case "daily": core = "FREQ=DAILY"; break;
            case "weekly": core = "FREQ=WEEKLY"; break;
            case "monthly": core = "FREQ=MONTHLY"; break;
            case "bimonthly": core = "FREQ=MONTHLY;INTERVAL=2"; break;
            case "quarterly": core = "FREQ=MONTHLY;INTERVAL=3"; break;
            case "semiannual": core = "FREQ=MONTHLY;INTERVAL=6"; break;
            case "annual": core = "FREQ=YEARLY"; break;
            default: return "";
        }
        if (untilMs <= 0) return core;
        if (hasTime) return core + ";UNTIL=" + UTC.format(Instant.ofEpochMilli(untilMs));
        LocalDate until = Instant.ofEpochMilli(untilMs).atZone(zone).toLocalDate();
        return core + ";UNTIL=" + DATE.format(until);
    }

    private static void line(StringBuilder sb, String value) {
        int bytesOnLine = 0;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            String chunk = new String(Character.toChars(cp));
            int chunkBytes = chunk.getBytes(StandardCharsets.UTF_8).length;
            if (bytesOnLine > 0 && bytesOnLine + chunkBytes > 75) {
                sb.append("\r\n ");
                bytesOnLine = 1;
            }
            sb.append(chunk);
            bytesOnLine += chunkBytes;
            offset += Character.charCount(cp);
        }
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
                .replace(",", "\\,").replace(";", "\\;");
    }

    private static String sanitize(String title) {
        String raw = title == null ? "evento" : title.trim();
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String safe = normalized.replaceAll("[^a-zA-Z0-9._ -]", "_")
                .replaceAll("\\s+", "_");
        if (safe.isBlank()) safe = "evento";
        return safe.length() > 50 ? safe.substring(0, 50) : safe;
    }
}
