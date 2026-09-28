package com.jruiz.nexo.recordings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Reglas puras y comprobables de identidad/título automático de grabaciones. */
public final class RecordingNaming {
    private static final String PREFIX = "rec_";
    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.forLanguageTag("es-ES"));

    private RecordingNaming() {}

    public static String idFor(long startedAt) {
        if (startedAt <= 0) throw new IllegalArgumentException("Fecha de inicio no válida");
        return PREFIX + startedAt;
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches("rec_[0-9]{10,20}");
    }

    public static void validateId(String id) {
        if (!isValidId(id)) throw new IllegalArgumentException("Grabación no válida");
    }

    public static long parseStartedAt(String id) {
        validateId(id);
        return Long.parseLong(id.substring(PREFIX.length()));
    }

    public static String defaultTitle(long startedAt, ZoneId zone) {
        if (startedAt <= 0) throw new IllegalArgumentException("Fecha de inicio no válida");
        ZoneId z = zone == null ? ZoneId.systemDefault() : zone;
        return TITLE_FMT.format(Instant.ofEpochMilli(startedAt).atZone(z));
    }

    public static String defaultTitle(long startedAt) {
        return defaultTitle(startedAt, ZoneId.systemDefault());
    }

    public static String exportFileName(String title, long startedAt) {
        String base = title == null ? "" : title.trim();
        if (base.isEmpty()) base = "Grabación " + defaultTitle(startedAt);
        String invalid = "\\/:*?\"<>|";
        StringBuilder safe = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            safe.append(c < 32 || c == 127 || invalid.indexOf(c) >= 0 ? ' ' : c);
        }
        base = safe.toString().replaceAll("\\s+", " ").trim().replaceAll("[. ]+$", "");
        if (base.isEmpty()) base = "Grabación " + startedAt;
        if (base.length() > 100) base = base.substring(0, 100).trim();
        return base + ".m4a";
    }
}
