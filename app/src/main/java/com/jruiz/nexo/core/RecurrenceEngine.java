package com.jruiz.nexo.core;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor determinista de recurrencias de Nexo.
 *
 * No genera filas hasta 2040. Una serie se guarda una sola vez y las apariciones
 * se calculan cuando una pantalla las necesita.
 */
public final class RecurrenceEngine {
    public static final String NONE = "none";
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";
    public static final String BIMONTHLY = "bimonthly";
    public static final String QUARTERLY = "quarterly";
    public static final String SEMIANNUAL = "semiannual";
    public static final String ANNUAL = "annual";

    private RecurrenceEngine() {}

    public static boolean isRecurring(String value) {
        return value != null && !NONE.equals(value) && isValid(value);
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        return switch (value) {
            case NONE, DAILY, WEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL -> true;
            default -> false;
        };
    }

    public static String normalize(String value) {
        if (value == null) return NONE;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return isValid(v) ? v : NONE;
    }

    public static int monthStep(String recurrence) {
        return switch (normalize(recurrence)) {
            case MONTHLY -> 1;
            case BIMONTHLY -> 2;
            case QUARTERLY -> 3;
            case SEMIANNUAL -> 6;
            case ANNUAL -> 12;
            default -> 0;
        };
    }

    /** Devuelve las apariciones de una serie dentro de un rango cerrado. */
    public static List<Long> occurrencesBetween(long baseDueAt, String recurrence, long recurrenceUntil,
                                                 long rangeStart, long rangeEnd, ZoneId zone, int max) {
        ArrayList<Long> out = new ArrayList<>();
        if (baseDueAt <= 0 || rangeEnd < rangeStart || max <= 0) return out;
        String r = normalize(recurrence);
        if (NONE.equals(r)) {
            if (baseDueAt >= rangeStart && baseDueAt <= rangeEnd) out.add(baseDueAt);
            return out;
        }

        long hardEnd = recurrenceUntil > 0 ? Math.min(rangeEnd, recurrenceUntil) : rangeEnd;
        if (hardEnd < baseDueAt || hardEnd < rangeStart) return out;

        ZonedDateTime base = Instant.ofEpochMilli(baseDueAt).atZone(zone);
        LocalDate baseDate = base.toLocalDate();
        LocalTime baseTime = base.toLocalTime();
        LocalDate targetDate = Instant.ofEpochMilli(Math.max(rangeStart, baseDueAt)).atZone(zone).toLocalDate();

        long index = firstIndexOnOrAfter(baseDate, targetDate, r);
        while (out.size() < max) {
            LocalDate date = dateAt(baseDate, r, index);
            long at = date.atTime(baseTime).atZone(zone).toInstant().toEpochMilli();
            if (at < baseDueAt) { index++; continue; }
            if (at < rangeStart) { index++; continue; }
            if (at > hardEnd || at > rangeEnd) break;
            out.add(at);
            index++;
        }
        return out;
    }

    /** Próxima aparición estrictamente posterior a afterMillis. */
    public static long nextOccurrence(long baseDueAt, String recurrence, long recurrenceUntil,
                                      long afterMillis, ZoneId zone) {
        if (baseDueAt <= 0) return 0;
        String r = normalize(recurrence);
        if (NONE.equals(r)) return baseDueAt > afterMillis ? baseDueAt : 0;
        long end = recurrenceUntil > 0 ? recurrenceUntil : LocalDate.of(2100, 12, 31)
                .atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
        List<Long> xs = occurrencesBetween(baseDueAt, r, end, Math.max(baseDueAt, afterMillis + 1), end, zone, 1);
        return xs.isEmpty() ? 0 : xs.get(0);
    }

    public static LocalDate dateAt(LocalDate baseDate, String recurrence, long index) {
        if (index <= 0) return baseDate;
        String r = normalize(recurrence);
        return switch (r) {
            case DAILY -> baseDate.plusDays(index);
            case WEEKLY -> baseDate.plusWeeks(index);
            case MONTHLY -> baseDate.plusMonths(index);
            case BIMONTHLY -> baseDate.plusMonths(index * 2);
            case QUARTERLY -> baseDate.plusMonths(index * 3);
            case SEMIANNUAL -> baseDate.plusMonths(index * 6);
            case ANNUAL -> baseDate.plusYears(index);
            default -> baseDate;
        };
    }

    private static long firstIndexOnOrAfter(LocalDate baseDate, LocalDate targetDate, String recurrence) {
        if (!targetDate.isAfter(baseDate)) return 0;
        String r = normalize(recurrence);
        if (DAILY.equals(r)) return Math.max(0, ChronoUnit.DAYS.between(baseDate, targetDate));
        if (WEEKLY.equals(r)) {
            long days = ChronoUnit.DAYS.between(baseDate, targetDate);
            long n = Math.max(0, days / 7);
            if (baseDate.plusWeeks(n).isBefore(targetDate)) n++;
            return n;
        }
        int step = monthStep(r);
        if (step > 0) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(baseDate), YearMonth.from(targetDate));
            long n = Math.max(0, months / step);
            while (dateAt(baseDate, r, n).isBefore(targetDate)) n++;
            while (n > 0 && !dateAt(baseDate, r, n - 1).isBefore(targetDate)) n--;
            return n;
        }
        return 0;
    }

    /** Detección local conservadora de expresiones de periodicidad en español. */
    public static Detection detect(String raw) {
        String n = TemporalParser.norm(raw == null ? "" : raw);
        Detection d = new Detection();
        if (containsAny(n, "cada dia", "todos los dias", "diariamente", "periodicidad diaria")) d.recurrence = DAILY;
        else if (containsAny(n, "cada semana", "todas las semanas", "semanalmente", "periodicidad semanal")) d.recurrence = WEEKLY;
        else if (containsAny(n, "cada dos meses", "cada 2 meses", "bimestral", "bimensual")) d.recurrence = BIMONTHLY;
        else if (containsAny(n, "cada tres meses", "cada 3 meses", "trimestral")) d.recurrence = QUARTERLY;
        else if (containsAny(n, "cada seis meses", "cada 6 meses", "semestral")) d.recurrence = SEMIANNUAL;
        else if (containsAny(n, "cada ano", "todos los anos", "anualmente", "periodicidad anual")) d.recurrence = ANNUAL;
        else if (containsAny(n, "cada mes", "todos los meses", "mensualmente", "periodicidad mensual")) d.recurrence = MONTHLY;

        if (!NONE.equals(d.recurrence)) {
            Matcher y = Pattern.compile("(?:hasta|hasta el|hasta el ano|hasta ano)\\s+(20\\d{2})\\b").matcher(n);
            if (y.find()) d.untilDate = y.group(1) + "-12-31";
            if (d.untilDate.isBlank()) {
                Matcher tail = Pattern.compile("\\bhasta(?:\\s+el)?\\s+(.+)$").matcher(n);
                if (tail.find()) {
                    TemporalParser.Result end = TemporalParser.parse(tail.group(1));
                    if (end.hasDate) d.untilDate = end.dateIso();
                }
            }
        }
        return d;
    }

    public static String label(String recurrence) {
        return switch (normalize(recurrence)) {
            case DAILY -> "Diaria";
            case WEEKLY -> "Semanal";
            case MONTHLY -> "Mensual";
            case BIMONTHLY -> "Bimestral";
            case QUARTERLY -> "Trimestral";
            case SEMIANNUAL -> "Semestral";
            case ANNUAL -> "Anual";
            default -> "No se repite";
        };
    }

    private static boolean containsAny(String s, String... needles) {
        for (String x : needles) if (s.contains(x)) return true;
        return false;
    }

    public static final class Detection {
        public String recurrence = NONE;
        public String untilDate = "";
    }
}
