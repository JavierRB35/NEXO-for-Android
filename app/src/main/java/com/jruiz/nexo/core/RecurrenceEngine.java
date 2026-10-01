package com.jruiz.nexo.core;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor determinista de recurrencias de Nexo.
 *
 * No genera filas a futuro. Una serie se guarda una sola vez y las apariciones
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

    private static final LinkedHashMap<String,DayOfWeek> SPANISH_DAYS = new LinkedHashMap<>();
    private static final EnumMap<DayOfWeek,String> RRULE_DAYS = new EnumMap<>(DayOfWeek.class);
    static {
        SPANISH_DAYS.put("lunes", DayOfWeek.MONDAY);
        SPANISH_DAYS.put("martes", DayOfWeek.TUESDAY);
        SPANISH_DAYS.put("miercoles", DayOfWeek.WEDNESDAY);
        SPANISH_DAYS.put("jueves", DayOfWeek.THURSDAY);
        SPANISH_DAYS.put("viernes", DayOfWeek.FRIDAY);
        SPANISH_DAYS.put("sabado", DayOfWeek.SATURDAY);
        SPANISH_DAYS.put("domingo", DayOfWeek.SUNDAY);

        RRULE_DAYS.put(DayOfWeek.MONDAY, "MO");
        RRULE_DAYS.put(DayOfWeek.TUESDAY, "TU");
        RRULE_DAYS.put(DayOfWeek.WEDNESDAY, "WE");
        RRULE_DAYS.put(DayOfWeek.THURSDAY, "TH");
        RRULE_DAYS.put(DayOfWeek.FRIDAY, "FR");
        RRULE_DAYS.put(DayOfWeek.SATURDAY, "SA");
        RRULE_DAYS.put(DayOfWeek.SUNDAY, "SU");
    }

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

    /** Compatibilidad con llamadas anteriores que no almacenaban RRULE. */
    public static List<Long> occurrencesBetween(long baseDueAt, String recurrence, long recurrenceUntil,
                                                 long rangeStart, long rangeEnd, ZoneId zone, int max) {
        return occurrencesBetween(baseDueAt, recurrence, "", recurrenceUntil, rangeStart, rangeEnd, zone, max);
    }

    /** Devuelve las apariciones de una serie dentro de un rango cerrado. */
    public static List<Long> occurrencesBetween(long baseDueAt, String recurrence, String recurrenceRule,
                                                 long recurrenceUntil, long rangeStart, long rangeEnd,
                                                 ZoneId zone, int max) {
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

        EnumSet<DayOfWeek> byDays = WEEKLY.equals(r) ? byDays(recurrenceRule) : EnumSet.noneOf(DayOfWeek.class);
        if (!byDays.isEmpty()) {
            LocalDate first = Instant.ofEpochMilli(Math.max(rangeStart, baseDueAt)).atZone(zone).toLocalDate();
            LocalDate last = Instant.ofEpochMilli(hardEnd).atZone(zone).toLocalDate();
            for (LocalDate date=first; !date.isAfter(last) && out.size()<max; date=date.plusDays(1)) {
                if (!byDays.contains(date.getDayOfWeek())) continue;
                long at = date.atTime(baseTime).atZone(zone).toInstant().toEpochMilli();
                if (at < baseDueAt || at < rangeStart || at > hardEnd || at > rangeEnd) continue;
                out.add(at);
            }
            return out;
        }

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
        return nextOccurrence(baseDueAt, recurrence, "", recurrenceUntil, afterMillis, zone);
    }

    public static long nextOccurrence(long baseDueAt, String recurrence, String recurrenceRule,
                                      long recurrenceUntil, long afterMillis, ZoneId zone) {
        if (baseDueAt <= 0) return 0;
        String r = normalize(recurrence);
        if (NONE.equals(r)) return baseDueAt > afterMillis ? baseDueAt : 0;
        long end = recurrenceUntil > 0 ? recurrenceUntil : LocalDate.of(2100, 12, 31)
                .atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
        List<Long> xs = occurrencesBetween(baseDueAt, r, recurrenceRule, end,
                Math.max(baseDueAt, afterMillis + 1), end, zone, 1);
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

        EnumSet<DayOfWeek> mentioned = mentionedWeekdays(n);
        boolean weeklyDaysCue = !mentioned.isEmpty() && (
                Pattern.compile("\\b(?:cada|todos\\s+los)\\s+(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b").matcher(n).find() ||
                containsAny(n, "todas las semanas", "cada semana", "semanalmente", "periodicidad semanal") ||
                (mentioned.size() > 1 && Pattern.compile("\\b(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)(?:\\s*[,y]\\s*|\\s+y\\s+)(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b").matcher(n).find())
        );

        if (weeklyDaysCue) {
            d.recurrence = WEEKLY;
            d.recurrenceRule = weeklyRule(mentioned);
        } else if (containsAny(n, "cada dia", "todos los dias", "diariamente", "periodicidad diaria")) d.recurrence = DAILY;
        else if (containsAny(n, "cada semana", "todas las semanas", "semanalmente", "periodicidad semanal")) d.recurrence = WEEKLY;
        else if (containsAny(n, "cada dos meses", "cada 2 meses", "bimestral", "bimensual")) d.recurrence = BIMONTHLY;
        else if (containsAny(n, "cada tres meses", "cada 3 meses", "trimestral")) d.recurrence = QUARTERLY;
        else if (containsAny(n, "cada seis meses", "cada 6 meses", "semestral")) d.recurrence = SEMIANNUAL;
        else if (containsAny(n, "cada ano", "todos los anos", "anualmente", "periodicidad anual")) d.recurrence = ANNUAL;
        else if (containsAny(n, "cada mes", "todos los meses", "mensualmente", "periodicidad mensual")) d.recurrence = MONTHLY;

        if (!NONE.equals(d.recurrence)) {
            if (d.recurrenceRule.isBlank()) d.recurrenceRule = ruleFor(d.recurrence);

            Matcher y = Pattern.compile("(?:hasta|hasta el|hasta el ano|hasta ano)\\s+(20\\d{2})\\b").matcher(n);
            if (y.find()) d.untilDate = y.group(1) + "-12-31";

            if (d.untilDate.isBlank()) {
                Matcher tail = Pattern.compile("\\bhasta(?:\\s+el)?\\s+(.+)$").matcher(n);
                if (tail.find()) {
                    TemporalParser.Result end = TemporalParser.parse(tail.group(1));
                    if (end.hasDate) d.untilDate = end.dateIso();
                }
            }

            // "todos los lunes, miércoles y viernes del 1 de mayo al 30 de junio"
            if (d.untilDate.isBlank() && WEEKLY.equals(d.recurrence) && !mentioned.isEmpty()) {
                Matcher range = Pattern.compile("\\bdel\\s+.+?\\s+al\\s+(.+)$").matcher(n);
                if (range.find()) {
                    TemporalParser.Result end = TemporalParser.parse(range.group(1));
                    if (end.hasDate) d.untilDate = end.dateIso();
                }
            }
        }
        return d;
    }

    public static String ruleFor(String recurrence) {
        String r=normalize(recurrence);
        if (DAILY.equals(r)) return "FREQ=DAILY";
        if (WEEKLY.equals(r)) return "FREQ=WEEKLY";
        if (MONTHLY.equals(r)) return "FREQ=MONTHLY";
        if (BIMONTHLY.equals(r)) return "FREQ=MONTHLY;INTERVAL=2";
        if (QUARTERLY.equals(r)) return "FREQ=MONTHLY;INTERVAL=3";
        if (SEMIANNUAL.equals(r)) return "FREQ=MONTHLY;INTERVAL=6";
        if (ANNUAL.equals(r)) return "FREQ=YEARLY";
        return "";
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

    private static EnumSet<DayOfWeek> mentionedWeekdays(String n) {
        EnumSet<DayOfWeek> out=EnumSet.noneOf(DayOfWeek.class);
        for (Map.Entry<String,DayOfWeek> e : SPANISH_DAYS.entrySet()) {
            if (Pattern.compile("\\b"+e.getKey()+"\\b").matcher(n).find()) out.add(e.getValue());
        }
        return out;
    }

    public static EnumSet<DayOfWeek> byDays(String recurrenceRule) {
        EnumSet<DayOfWeek> out=EnumSet.noneOf(DayOfWeek.class);
        if (recurrenceRule==null || recurrenceRule.isBlank()) return out;
        Matcher m=Pattern.compile("(?:^|;)BYDAY=([^;]+)",Pattern.CASE_INSENSITIVE).matcher(recurrenceRule);
        if(!m.find()) return out;
        for(String token:m.group(1).toUpperCase(Locale.ROOT).split(",")) {
            for(Map.Entry<DayOfWeek,String> e:RRULE_DAYS.entrySet()) {
                if(e.getValue().equals(token.trim())) out.add(e.getKey());
            }
        }
        return out;
    }

    private static String weeklyRule(Set<DayOfWeek> days) {
        if(days==null || days.isEmpty()) return "FREQ=WEEKLY";
        ArrayList<String> codes=new ArrayList<>();
        for(DayOfWeek day:DayOfWeek.values()) if(days.contains(day)) codes.add(RRULE_DAYS.get(day));
        return "FREQ=WEEKLY;BYDAY="+String.join(",",codes);
    }

    private static boolean containsAny(String s, String... needles) {
        for (String x : needles) if (s.contains(x)) return true;
        return false;
    }

    public static final class Detection {
        public String recurrence = NONE;
        public String recurrenceRule = "";
        public String untilDate = "";
    }
}
