package com.jruiz.nexo.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Conversión única de fecha/hora local del evento a instante UTC usando su ZoneId. */
public final class ZoneTimeResolver {
    private ZoneTimeResolver() {}

    public static long toEpochMillis(String date, String time, String zoneId) {
        if (date == null || date.isBlank()) return 0L;
        try {
            LocalDate localDate = LocalDate.parse(date.trim());
            String rawTime = time == null ? "" : time.trim();
            LocalTime localTime = rawTime.isBlank() ? LocalTime.of(9, 0) : LocalTime.parse(rawTime);
            ZoneId zone = zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneId.trim());
            LocalDateTime local = LocalDateTime.of(localDate, localTime);
            ZonedDateTime zoned = local.atZone(zone);
            return zoned.toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
