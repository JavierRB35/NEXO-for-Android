package com.jruiz.nexo.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Intérprete local y conservador de órdenes dirigidas al despertador del teléfono.
 * No crea la alarma: solo extrae intención, hora y repetición para que MainActivity
 * delegue la creación en la aplicación Reloj mediante AlarmClock.ACTION_SET_ALARM.
 */
public final class AlarmIntentParser {
    private AlarmIntentParser() {}

    public static boolean looksLikeAlarm(String raw) {
        String n = RuleClassifier.norm(raw == null ? "" : raw);
        if (n.isBlank()) return false;
        // "Recuérdame" es un recordatorio Nexo. Para usar Reloj pedimos una intención inequívoca.
        return Pattern.compile("\\b(?:despierta(?:me)?|despertador|alarma)\\b").matcher(n).find()
                || n.startsWith("ponme una alarma") || n.startsWith("pon una alarma")
                || n.startsWith("crea una alarma") || n.startsWith("programa una alarma");
    }

    public static JSONObject parse(String raw) {
        return parse(raw, LocalDateTime.now(ZoneId.systemDefault()));
    }

    public static JSONObject parse(String raw, LocalDateTime now) {
        JSONObject out = new JSONObject();
        JSONArray warnings = new JSONArray();
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        String n = RuleClassifier.norm(text);
        try {
            boolean alarm = looksLikeAlarm(text);
            out.put("isAlarm", alarm);
            out.put("raw", text);
            out.put("warnings", warnings);
            if (!alarm) return out;

            TemporalParser.Result tr = TemporalParser.parse(text, now);
            if (!tr.hasTime || tr.time == null) {
                out.put("valid", false);
                warnings.put("Falta la hora de la alarma.");
                return out;
            }

            LinkedHashSet<Integer> days = parseRepeatDays(n);
            boolean repeatWeekdayIsNotStartDate = !days.isEmpty() && tr.dateSource != null && tr.dateSource.contains("día de la semana") &&
                    containsAny(n,"repite","cada ","lunes a viernes","de lunes a viernes","entre semana","dias laborables","los lunes","los martes","los miercoles","los jueves","los viernes","los sabados","los domingos");
            boolean hasRequestedDate = tr.hasDate && tr.date != null && !repeatWeekdayIsNotStartDate;

            out.put("valid", true);
            out.put("hour", tr.time.getHour());
            out.put("minute", tr.time.getMinute());
            out.put("time", tr.time.format(DateTimeFormatter.ofPattern("HH:mm")));
            out.put("date", hasRequestedDate ? tr.date.toString() : "");
            out.put("targetAt", hasRequestedDate ? tr.toEpochMillis(ZoneId.systemDefault()) : 0L);
            out.put("message", "Nexo");

            JSONArray dayArray = new JSONArray();
            for (Integer d : days) dayArray.put(d);
            out.put("days", dayArray);
            out.put("repeating", !days.isEmpty());
            out.put("repeatLabel", repeatLabel(days, n));

            boolean safe = !hasRequestedDate || isSystemAlarmDateSafe(now, tr, days);
            out.put("systemDateSafe", safe);
            if (!safe) {
                warnings.put("La interfaz estándar del Reloj de Android no permite indicar una fecha exacta. Nexo abrirá el Reloj con la hora preparada para que revises el día antes de guardar.");
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Calendar.SUNDAY=1 ... Calendar.SATURDAY=7, que es lo que espera AlarmClock.EXTRA_DAYS. */
    private static LinkedHashSet<Integer> parseRepeatDays(String n) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        if (containsAny(n, "todos los dias", "cada dia", "diariamente", "todos los días")) {
            for (int d=Calendar.SUNDAY; d<=Calendar.SATURDAY; d++) out.add(d);
            return out;
        }
        if (containsAny(n, "lunes a viernes", "de lunes a viernes", "entre semana", "dias laborables", "días laborables")) {
            out.add(Calendar.MONDAY); out.add(Calendar.TUESDAY); out.add(Calendar.WEDNESDAY); out.add(Calendar.THURSDAY); out.add(Calendar.FRIDAY);
            return out;
        }
        if (containsAny(n, "fin de semana", "sabado y domingo", "sábado y domingo", "sabados y domingos", "sábados y domingos")) {
            out.add(Calendar.SATURDAY); out.add(Calendar.SUNDAY); return out;
        }
        if (!containsAny(n, "repite", "repetir", "cada ", "los lunes", "los martes", "los miercoles", "los miércoles", "los jueves", "los viernes", "los sabados", "los sábados", "los domingos")) return out;
        addIfDay(n,out,"lunes",Calendar.MONDAY);
        addIfDay(n,out,"martes",Calendar.TUESDAY);
        addIfDay(n,out,"miercoles",Calendar.WEDNESDAY);
        addIfDay(n,out,"jueves",Calendar.THURSDAY);
        addIfDay(n,out,"viernes",Calendar.FRIDAY);
        addIfDay(n,out,"sabado",Calendar.SATURDAY);
        addIfDay(n,out,"domingo",Calendar.SUNDAY);
        return out;
    }

    private static void addIfDay(String n, Set<Integer> out, String word, int day) {
        if (Pattern.compile("\\b"+word+"(?:s)?\\b").matcher(n).find()) out.add(day);
    }

    private static String repeatLabel(Set<Integer> days, String n) {
        if (days.isEmpty()) return "No se repite";
        if (days.size()==7) return "Todos los días";
        if (days.equals(new LinkedHashSet<>(Arrays.asList(Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY)))) return "De lunes a viernes";
        List<String> names=new ArrayList<>();
        int[] vals={Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY,Calendar.SUNDAY};
        String[] labels={"Lun","Mar","Mié","Jue","Vie","Sáb","Dom"};
        for(int i=0;i<vals.length;i++) if(days.contains(vals[i])) names.add(labels[i]);
        return String.join(" · ", names);
    }

    private static boolean isSystemAlarmDateSafe(LocalDateTime now, TemporalParser.Result tr, Set<Integer> repeatDays) {
        if (!tr.hasDate || tr.date == null) return true;
        LocalDate next;
        if (repeatDays.isEmpty()) {
            next = tr.time.isAfter(now.toLocalTime()) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
        } else {
            next = nextDayForAlarm(now, tr.time, repeatDays);
        }
        return next.equals(tr.date);
    }

    private static LocalDate nextDayForAlarm(LocalDateTime now, LocalTime time, Set<Integer> days) {
        LocalDate d=now.toLocalDate();
        for(int i=0;i<8;i++,d=d.plusDays(1)) {
            int cal=toCalendarDay(d.getDayOfWeek());
            if(!days.contains(cal)) continue;
            if(i==0 && !time.isAfter(now.toLocalTime())) continue;
            return d;
        }
        return now.toLocalDate();
    }

    private static int toCalendarDay(DayOfWeek d) {
        switch(d) {
            case MONDAY:return Calendar.MONDAY; case TUESDAY:return Calendar.TUESDAY; case WEDNESDAY:return Calendar.WEDNESDAY;
            case THURSDAY:return Calendar.THURSDAY; case FRIDAY:return Calendar.FRIDAY; case SATURDAY:return Calendar.SATURDAY;
            default:return Calendar.SUNDAY;
        }
    }

    private static boolean containsAny(String s,String... values){for(String v:values)if(s.contains(RuleClassifier.norm(v)))return true;return false;}
}
