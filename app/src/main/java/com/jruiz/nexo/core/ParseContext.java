package com.jruiz.nexo.core;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Contexto inmutable de una interpretación.
 *
 * Fija el reloj y la zona que deben usarse para expresiones relativas. Los
 * parsers antiguos siguen funcionando mediante ParseContext.now().
 */
public final class ParseContext {
    public static final String PARSER_VERSION = "event-parser-v2";

    public final long capturedAtMillis;
    public final String localeTag;
    public final ZoneId deviceZone;
    public final String origin;
    public final String pinnedDate;

    private ParseContext(long capturedAtMillis, String localeTag, ZoneId deviceZone,
                         String origin, String pinnedDate) {
        this.capturedAtMillis = capturedAtMillis > 0 ? capturedAtMillis : System.currentTimeMillis();
        this.localeTag = localeTag == null || localeTag.isBlank() ? "es-ES" : localeTag;
        this.deviceZone = deviceZone == null ? ZoneId.systemDefault() : deviceZone;
        this.origin = origin == null ? "" : origin;
        this.pinnedDate = pinnedDate == null ? "" : pinnedDate;
    }

    public static ParseContext now() {
        return new ParseContext(System.currentTimeMillis(), "es-ES", ZoneId.systemDefault(), "", "");
    }

    public static ParseContext at(long capturedAtMillis) {
        return new ParseContext(capturedAtMillis, "es-ES", ZoneId.systemDefault(), "", "");
    }

    public ParseContext withOrigin(String value) {
        return new ParseContext(capturedAtMillis, localeTag, deviceZone, value, pinnedDate);
    }

    public ParseContext withPinnedDate(String value) {
        return new ParseContext(capturedAtMillis, localeTag, deviceZone, origin, value);
    }

    public LocalDateTime localDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(capturedAtMillis), deviceZone);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("capturedAt", capturedAtMillis);
            o.put("locale", localeTag);
            o.put("deviceZone", deviceZone.getId());
            o.put("origin", origin);
            o.put("pinnedDate", pinnedDate);
            o.put("parserVersion", PARSER_VERSION);
        } catch (Exception ignored) {}
        return o;
    }
}
