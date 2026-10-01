package com.jruiz.nexo.core;

import java.text.Normalizer;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser temporal local, determinista y orientado a español (es-ES).
 * Su misión es extraer fechas/horas objetivas sin pedir a la IA que "adivine"
 * valores que pueden resolverse de forma exacta.
 */
public final class TemporalParser {
    private TemporalParser() {}

    public static final class Result {
        public LocalDate date;
        public LocalTime time;
        public LocalTime endTime;
        public boolean hasDate;
        public boolean hasTime;
        public boolean hasEndTime;
        public boolean invalidDate;
        public boolean invalidTime;
        public boolean ambiguousTime;
        public int explicitDurationMinutes;
        public String dateSource = "";
        public String timeSource = "";
        public String endTimeSource = "";

        public long toEpochMillis(ZoneId zone) {
            if (!hasDate || date == null) return 0L;
            LocalTime t = hasTime && time != null ? time : LocalTime.of(9, 0);
            return date.atTime(t).atZone(zone).toInstant().toEpochMilli();
        }

        public String dateIso() { return hasDate && date != null ? date.toString() : ""; }
        public String timeIso() { return hasTime && time != null ? String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute()) : ""; }
        public String endTimeIso() { return hasEndTime && endTime != null ? String.format(Locale.ROOT, "%02d:%02d", endTime.getHour(), endTime.getMinute()) : ""; }
        public int durationMinutes() {
            if (hasTime && time != null && hasEndTime && endTime != null) {
                int start = time.getHour()*60 + time.getMinute();
                int end = endTime.getHour()*60 + endTime.getMinute();
                int diff = end - start;
                if (diff <= 0) diff += 24*60;
                if (diff > 0 && diff <= 24*60) return diff;
            }
            return explicitDurationMinutes > 0 ? explicitDurationMinutes : 0;
        }
    }

    private static final String[] MONTHS = {
            "enero","febrero","marzo","abril","mayo","junio",
            "julio","agosto","septiembre","octubre","noviembre","diciembre"
    };

    private static final Map<String, DayOfWeek> WEEKDAYS = new LinkedHashMap<>();
    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final Map<String, Integer> HUNDREDS = new HashMap<>();
    static {
        WEEKDAYS.put("lunes", DayOfWeek.MONDAY); WEEKDAYS.put("martes", DayOfWeek.TUESDAY);
        WEEKDAYS.put("miercoles", DayOfWeek.WEDNESDAY); WEEKDAYS.put("jueves", DayOfWeek.THURSDAY);
        WEEKDAYS.put("viernes", DayOfWeek.FRIDAY); WEEKDAYS.put("sabado", DayOfWeek.SATURDAY);
        WEEKDAYS.put("domingo", DayOfWeek.SUNDAY);

        String[] ones = {"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte","veintiuno","veintidos","veintitres","veinticuatro","veinticinco","veintiseis","veintisiete","veintiocho","veintinueve"};
        for (int i=0;i<ones.length;i++) NUMBERS.put(ones[i], i);
        NUMBERS.put("un",1); NUMBERS.put("una",1); NUMBERS.put("veintiun",21); NUMBERS.put("primer",1); NUMBERS.put("primero",1); NUMBERS.put("primera",1);
        NUMBERS.put("treinta",30); NUMBERS.put("cuarenta",40); NUMBERS.put("cincuenta",50); NUMBERS.put("sesenta",60); NUMBERS.put("setenta",70); NUMBERS.put("ochenta",80); NUMBERS.put("noventa",90);
        HUNDREDS.put("cien",100); HUNDREDS.put("ciento",100); HUNDREDS.put("doscientos",200); HUNDREDS.put("trescientos",300); HUNDREDS.put("cuatrocientos",400); HUNDREDS.put("quinientos",500); HUNDREDS.put("seiscientos",600); HUNDREDS.put("setecientos",700); HUNDREDS.put("ochocientos",800); HUNDREDS.put("novecientos",900);
    }

    public static Result parse(String raw) {
        return parse(raw, LocalDateTime.now(ZoneId.systemDefault()));
    }

    public static Result parse(String raw, LocalDateTime now) {
        Result out = new Result();
        String n = norm(raw == null ? "" : raw);
        if (n.isBlank()) return out;
        String dateScope = EventSemanticParser.maskNonEventDateReferences(n);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = now.toLocalDate();

        parseExplicitTime(correctedTimeScope(n), out);
        parseExplicitEndTime(n, out);
        parseExplicitDuration(n, out);
        if (out.explicitDurationMinutes > 0 && out.hasTime && out.time != null && !out.hasEndTime) {
            out.endTime = out.time.plusMinutes(out.explicitDurationMinutes);
            out.hasEndTime = true;
            out.endTimeSource = "duración explícita";
        }

        Matcher numeric = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\s*[./-]\\s*(\\d{2,4}))?(?!\\d)").matcher(dateScope);
        while (numeric.find() && !out.hasDate && !out.invalidDate) {
            String token = numeric.group(0);
            String prefix = dateScope.substring(Math.max(0, numeric.start()-14), numeric.start());
            if (token.contains(".") && numeric.group(3) == null &&
                    (prefix.matches(".*\\ba\\s+las?\\s*$") || prefix.matches(".*\\bhora\\s*$"))) continue;
            try {
                int d = Integer.parseInt(numeric.group(1));
                int m = Integer.parseInt(numeric.group(2));
                Integer y = numeric.group(3) == null ? null : Integer.parseInt(numeric.group(3));
                if (y != null && y < 100) y += 2000;
                out.date = resolveYear(d, m, y, today);
                out.hasDate = true;
                out.dateSource = "fecha numérica";
            } catch (Exception invalid) {
                out.invalidDate = true;
                out.dateSource = "fecha explícita inválida";
            }
        }

        if (!out.hasDate && !out.invalidDate) parseMonthWords(dateScope, today, out);
        if (!out.hasDate && !out.invalidDate) parseWeekday(n, today, out);

        if (!out.hasDate && !out.invalidDate && n.contains("pasado manana")) {
            out.date = today.plusDays(2); out.hasDate = true; out.dateSource = "pasado mañana";
        }
        if (!out.hasDate && !out.invalidDate && Pattern.compile("\\besta\\s+(?:manana|tarde|noche|madrugada)\\b").matcher(n).find()) {
            out.date = today; out.hasDate = true; out.dateSource = "esta franja del día";
        }
        if (!out.hasDate && !out.invalidDate && Pattern.compile("\\bhoy\\b").matcher(n).find()) {
            out.date = today; out.hasDate = true; out.dateSource = "hoy";
        }
        if (!out.hasDate && !out.invalidDate && containsTomorrowReference(n)) {
            out.date = today.plusDays(1); out.hasDate = true; out.dateSource = "mañana";
        }

        if (!out.hasDate && !out.invalidDate) parseRelativeAmount(n, today, out);

        if (!out.hasDate && !out.invalidDate && !out.invalidTime && out.hasTime && out.time != null) {
            LocalDate candidate = today;
            if (out.time.isBefore(now.toLocalTime()) || out.time.equals(now.toLocalTime())) candidate = candidate.plusDays(1);
            out.date = candidate; out.hasDate = true; out.dateSource = "día inferido por hora";
        }

        if (out.hasDate && out.date != null) {
            try { out.date.atTime(out.hasTime && out.time != null ? out.time : LocalTime.of(9,0)).atZone(zone).toInstant(); }
            catch (Exception e) {
                out.date=null; out.hasDate=false; out.invalidDate=true; out.dateSource="fecha explícita inválida";
            }
        }
        return out;
    }


    /**
     * Detecta "mañana" como referencia al día siguiente, ignorando usos de
     * franja horaria: "de la mañana", "por la mañana", "esta mañana",
     * "turno de mañana".
     */
    private static boolean containsTomorrowReference(String n) {
        Matcher m = Pattern.compile("\\bmanana\\b").matcher(n);
        while (m.find()) {
            String before = n.substring(Math.max(0, m.start() - 18), m.start()).trim();
            if (before.endsWith("de la") || before.endsWith("por la") ||
                    before.endsWith("toda la") || before.endsWith("durante la") ||
                    before.endsWith("durante toda la") || before.endsWith("esta") ||
                    before.endsWith("turno de") || before.endsWith("horario de")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static LocalDate resolveYear(int d, int m, Integer y, LocalDate today) {
        int year = y == null ? today.getYear() : y;
        LocalDate date = LocalDate.of(year, m, d);
        if (y == null && date.isBefore(today)) date = date.plusYears(1);
        return date;
    }

    private static void parseRelativeAmount(String n, LocalDate today, Result out) {
        String numberWords = "([a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?)";
        Matcher m = Pattern.compile("(?:dentro\\s+de|en)\\s+" + numberWords + "\\s+(dia|dias|semana|semanas|mes|meses)\\b").matcher(n);
        if (!m.find()) return;
        int amount = parseNumberPhrase(m.group(1));
        if (amount <= 0) return;
        String unit = m.group(2);
        if (unit.startsWith("dia")) out.date = today.plusDays(amount);
        else if (unit.startsWith("semana")) out.date = today.plusWeeks(amount);
        else out.date = today.plusMonths(amount);
        out.hasDate = true; out.dateSource = "fecha relativa";
    }

    private static void parseWeekday(String n, LocalDate today, Result out) {
        String days = "(lunes|martes|miercoles|jueves|viernes|sabado|domingo)";
        Matcher correction = Pattern.compile("\\bno\\s+(?:es\\s+)?(?:el\\s+)?"+days+
                "\\b\\s*[,;]?\\s*(?:sino\\s+)?(?:es\\s+)?(?:el\\s+)?"+days+"\\b").matcher(n);
        if (correction.find()) {
            DayOfWeek day = WEEKDAYS.get(correction.group(2));
            if (day != null) {
                out.date = today.with(TemporalAdjusters.nextOrSame(day));
                out.hasDate = true;
                out.dateSource = "día de la semana corregido";
                return;
            }
        }
        for (Map.Entry<String,DayOfWeek> e : WEEKDAYS.entrySet()) {
            if (!Pattern.compile("\\b"+e.getKey()+"\\b").matcher(n).find()) continue;
            boolean next = n.contains("proximo " + e.getKey()) || n.contains("proxima " + e.getKey()) || n.contains("siguiente " + e.getKey());
            LocalDate d = next ? today.with(TemporalAdjusters.next(e.getValue())) : today.with(TemporalAdjusters.nextOrSame(e.getValue()));
            out.date = d; out.hasDate = true; out.dateSource = next ? "próximo día de la semana" : "día de la semana";
            return;
        }
    }

    private static void parseMonthWords(String n, LocalDate today, Result out) {
        String[] tokens = n.replaceAll("[^a-z0-9]+", " ").trim().split("\\s+");
        for (int i=0;i<tokens.length;i++) {
            int month = monthIndex(tokens[i]);
            if (month < 1) continue;

            int dayEnd = i;
            if (i>0 && tokens[i-1].equals("de")) dayEnd = i-1;
            int day = 0;
            for (int len=1; len<=4 && dayEnd-len>=0; len++) {
                int val = parseNumberTokens(tokens, dayEnd-len, dayEnd);
                if (val>=1 && val<=31) day = val;
            }
            if (day==0 && dayEnd-1>=0) {
                try { day=Integer.parseInt(tokens[dayEnd-1]); } catch(Exception ignored) {}
            }
            if (day<1 || day>31) continue;

            Integer year = null;
            int j=i+1;
            if (j<tokens.length && tokens[j].equals("de")) j++;
            for (int len=1;len<=5 && j+len<=tokens.length;len++) {
                int val=parseNumberTokens(tokens,j,j+len);
                if (val>=1900 && val<=2200) year=val;
                try {
                    if (len==1) {
                        int digit=Integer.parseInt(tokens[j]);
                        if (digit>=1900 && digit<=2200) year=digit;
                        else if (digit>=0 && digit<100) year=2000+digit;
                    }
                } catch(Exception ignored) {}
            }
            try {
                out.date=resolveYear(day,month,year,today); out.hasDate=true; out.dateSource="fecha escrita con palabras"; return;
            } catch(Exception invalid) {
                out.invalidDate=true; out.dateSource="fecha explícita inválida"; return;
            }
        }
    }

    private static int monthIndex(String token) {
        for (int i=0;i<MONTHS.length;i++) if (MONTHS[i].equals(token)) return i+1;
        return -1;
    }

    private static void parseExplicitTime(String n, Result out) {
        if (n.contains("medianoche")) { out.time=LocalTime.MIDNIGHT; out.hasTime=true; out.timeSource="medianoche"; return; }
        if (n.contains("mediodia")) { out.time=LocalTime.NOON; out.hasTime=true; out.timeSource="mediodía"; return; }

        Matcher m = Pattern.compile("(?:\\ba\\s+las?\\s+|\\blas?\\s+)?(?<![./\\d])(\\d{1,2})[:.](\\d{2})(?!\\d)").matcher(n);
        while (m.find()) {
            int k=m.end(); while(k<n.length() && Character.isWhitespace(n.charAt(k))) k++;
            if (k<n.length() && (n.charAt(k)=='.' || n.charAt(k)=='/' || n.charAt(k)=='-')) continue;
            int rawHour=Integer.parseInt(m.group(1)), min=Integer.parseInt(m.group(2));
            String matched = m.group(0);
            boolean explicitCue = matched.matches(".*\\ba\\s+las?.*") || matched.matches(".*\\blas?.*");
            if (matched.contains(".") && !explicitCue && rawHour>=1 && rawHour<=31 && min>=1 && min<=12) continue;
            int h=adjustDayPartFromContext(rawHour,n,m.start(),m.end());
            if (validTime(h,min)) {
                out.time=LocalTime.of(h,min); out.hasTime=true; out.timeSource="hora numérica";
                // HH:mm es una hora explícita de 24 horas; no necesita AM/PM.
                out.ambiguousTime=false;
                return;
            }
            out.invalidTime=true; out.timeSource="hora explícita inválida"; return;
        }

        Matcher marker = Pattern.compile("\\b(?:a|desde|de)\\s+las?\\s+").matcher(n);
        boolean markerFound = marker.find();
        if (!markerFound) { marker = Pattern.compile("\\b(?:a|desde|de)\\s+la\\s+").matcher(n); markerFound = marker.find(); }
        if (markerFound) {
            String tail=n.substring(marker.end()).trim();
            String[] tokens=tail.replaceAll("[^a-z0-9]+"," ").trim().split("\\s+");
            if (tokens.length>0) {
                int h=0, used=0;
                int hv=parseNumberTokens(tokens,0,1);
                if (hv>=0 && hv<=23) { h=hv; used=1; }
                else if (hv>23) { out.invalidTime=true; out.timeSource="hora explícita inválida"; return; }
                if (used>0) {
                    int rawHour=h;
                    int min=0;
                    if (used+1<tokens.length && tokens[used].equals("y") && tokens[used+1].equals("media")) { min=30; used+=2; }
                    else if (used+1<tokens.length && tokens[used].equals("y") && tokens[used+1].equals("cuarto")) { min=15; used+=2; }
                    else if (used+1<tokens.length && tokens[used].equals("menos") && tokens[used+1].equals("cuarto")) { h=(h+23)%24; min=45; used+=2; }
                    else if (used<tokens.length && tokens[used].equals("y")) {
                        int[] parsed=parseSmallNumber(tokens,used+1,59);
                        if (parsed[0]>=0) { min=parsed[0]; used=parsed[1]; }
                    } else {
                        int[] parsed=parseSmallNumber(tokens,used,59);
                        if (parsed[0]>=0) { min=parsed[0]; used=parsed[1]; }
                    }
                    h=adjustDayPartFromContext(h,n,marker.start(),marker.end());
                    if (validTime(h,min)) {
                        out.time=LocalTime.of(h,min); out.hasTime=true; out.timeSource="hora escrita con palabras";
                        out.ambiguousTime=rawHour>=1 && rawHour<=11 && !hasDayPartAround(n,marker.start(),marker.end());
                    } else {
                        out.invalidTime=true; out.timeSource="hora explícita inválida";
                    }
                }
            }
        }
    }

    private static boolean hasDayPartAround(String n, int start, int end) {
        String around=n.substring(Math.max(0,start-28),Math.min(n.length(),end+40));
        // "mañana a las cinco" fija el día, pero no distingue 05:00/17:00.
        // Solo una franja explícita ("de la tarde", "por la mañana", "esta noche"...)
        // desambigua una hora hablada de 1 a 11.
        return Pattern.compile("\\b(?:(?:de|por)\\s+la|esta)\\s+(?:manana|tarde|noche|madrugada)\\b|\\b(?:mediodia|medianoche)\\b")
                .matcher(around).find();
    }

    private static String correctedTimeScope(String n) {
        if (n==null || n.isBlank()) return n;
        Matcher cue=Pattern.compile("\\b(?:perdon|mejor|rectifico|corrijo|quiero\\s+decir)\\b").matcher(n);
        int last=-1; while(cue.find()) last=cue.start();
        if(last<0) return n;
        String tail=n.substring(last);
        if(Pattern.compile("\\b(?:a|desde|de)\\s+las?\\s+|\\b(?:a|desde|de)\\s+la\\s+|\\d{1,2}[:.]\\d{2}").matcher(tail).find()) return tail;
        return n;
    }

    private static void parseExplicitDuration(String n, Result out) {
        if(n==null || n.isBlank()) return;
        Matcher m=Pattern.compile("\\b(?:durante|duracion(?:\\s+de)?|dura|durara)\\s+([^,.;]{2,90})").matcher(n);
        while(m.find()){
            int minutes=parseSpanMinutes(m.group(1));
            if(minutes>0 && minutes<=10080){out.explicitDurationMinutes=minutes;return;}
        }
    }

    public static int parseSpanMinutes(String phrase) {
        if(phrase==null) return -1;
        String p=norm(phrase).trim();
        p=p.replaceFirst("^(?:durante|duracion(?:\\s+de)?|dura|durara)\\s+","").trim();
        p=p.replaceFirst("\\s+antes\\b.*$","").trim();
        if(p.matches("^media\\s+hora\\b.*")) return 30;
        if(p.matches("^(?:un\\s+)?cuarto\\s+de\\s+hora\\b.*")) return 15;

        Matcher hours=Pattern.compile("(?:(.{1,45}?)\\s+)?horas?\\b").matcher(p);
        if(hours.find()){
            String number=hours.group(1)==null?"":hours.group(1).trim();
            int h=number.isBlank()?1:parseNumberPhrase(number);
            if(h<0)return -1;
            int minutes=h*60;
            String tail=p.substring(hours.end()).trim();
            if(tail.matches("^y\\s+media\\b.*"))minutes+=30;
            else if(tail.matches("^y\\s+cuarto\\b.*"))minutes+=15;
            else{
                Matcher extra=Pattern.compile("^(?:y\\s+)?(.{1,35}?)\\s+minutos?\\b").matcher(tail);
                if(extra.find()){int m=parseNumberPhrase(extra.group(1));if(m>=0&&m<60)minutes+=m;}
            }
            return minutes;
        }

        Matcher minutes=Pattern.compile("(.{1,45}?)\\s+minutos?\\b").matcher(p);
        if(minutes.find()){int m=parseNumberPhrase(minutes.group(1));return m>=0?m:-1;}
        return -1;
    }

    /**
     * Extrae una hora final explícita. Se centra en construcciones de rango habituales:
     * "desde las 18:30 hasta las 19:30", "de 18:30 a 19:30" o
     * "hasta las siete y media de la tarde". La hora final nunca se infiere si
     * no aparece de forma explícita.
     */
    private static void parseExplicitEndTime(String n, Result out) {
        if (!out.hasTime || out.time == null || n == null || n.isBlank()) return;

        // 1) "... hasta las 19:30 / hasta las siete y media ...".
        Matcher until = Pattern.compile("\\bhasta\\s+").matcher(n);
        while (until.find()) {
            String fragment = n.substring(until.end()).trim();
            LocalTime parsed = parseTimeAtBeginning(fragment);
            if (parsed != null) {
                out.endTime = parsed;
                out.hasEndTime = true;
                out.endTimeSource = "hora final tras hasta";
                return;
            }
        }

        // 2) Rango hablado "de/desde las seis y media ... a las siete y media ...".
        Matcher spokenStart = Pattern.compile("\\b(?:desde|de)\\s+(?:las?|la)\\s+").matcher(n);
        if (spokenStart.find()) {
            Matcher spokenEnd = Pattern.compile("\\s+(?:hasta\\s+)?a?\\s*(?:las?|la)\\s+").matcher(n);
            spokenEnd.region(spokenStart.end(), n.length());
            while (spokenEnd.find()) {
                LocalTime parsed = parseTimeAtBeginning(n.substring(spokenEnd.end()).trim());
                if (parsed != null) {
                    out.endTime = parsed;
                    out.hasEndTime = true;
                    out.endTimeSource = "rango horario hablado";
                    return;
                }
            }
        }

        // 3) Rangos compactos "de/desde 18:30 a 19:30". Evita usar una fecha
        // numérica como si fuese una hora porque exige dos horas con ':' o '.'.
        Matcher range = Pattern.compile("\\b(?:desde|de)\\s+(?:las?\\s+)?(\\d{1,2})[:.](\\d{2})(?:\\s+de\\s+la\\s+(manana|tarde|noche))?\\s+(?:a|hasta)\\s+(?:las?\\s+)?(\\d{1,2})[:.](\\d{2})(?:\\s+de\\s+la\\s+(manana|tarde|noche))?").matcher(n);
        if (range.find()) {
            try {
                int h = Integer.parseInt(range.group(4)), m = Integer.parseInt(range.group(5));
                String part = range.group(6) == null ? "" : range.group(6);
                h = adjustDayPart(h, part);
                if (validTime(h,m)) {
                    out.endTime = LocalTime.of(h,m);
                    out.hasEndTime = true;
                    out.endTimeSource = "rango horario";
                }
            } catch (Exception ignored) {}
        }
    }

    /** Parsea una hora solo si aparece al principio del fragmento. */
    private static LocalTime parseTimeAtBeginning(String raw) {
        if (raw == null) return null;
        String f = norm(raw).trim();
        f = f.replaceFirst("^(?:a\\s+)?(?:las?|la)\\s+", "");
        if (f.isBlank()) return null;

        Matcher numeric = Pattern.compile("^(\\d{1,2})[:.](\\d{2})(?!\\d)").matcher(f);
        if (numeric.find()) {
            int h = Integer.parseInt(numeric.group(1)), m = Integer.parseInt(numeric.group(2));
            String tail = f.substring(numeric.end(), Math.min(f.length(), numeric.end()+36));
            h = adjustDayPart(h, tail);
            return validTime(h,m) ? LocalTime.of(h,m) : null;
        }

        Matcher hourOnly = Pattern.compile("^(\\d{1,2})(?:\\s*(?:h|horas?))?\\b").matcher(f);
        if (hourOnly.find()) {
            int h = Integer.parseInt(hourOnly.group(1));
            String tail = f.substring(hourOnly.end(), Math.min(f.length(), hourOnly.end()+36));
            // Una hora sin minutos se acepta aquí porque el marcador "hasta" ya
            // proporciona el contexto temporal inequívoco.
            h = adjustDayPart(h, tail);
            return validTime(h,0) ? LocalTime.of(h,0) : null;
        }

        String[] tokens=f.replaceAll("[^a-z0-9]+"," ").trim().split("\\s+");
        if (tokens.length==0) return null;
        int h=parseNumberTokens(tokens,0,1), used=1;
        if (h<0 || h>23) return null;
        int min=0;
        if (used+1<tokens.length && tokens[used].equals("y") && tokens[used+1].equals("media")) { min=30; used+=2; }
        else if (used+1<tokens.length && tokens[used].equals("y") && tokens[used+1].equals("cuarto")) { min=15; used+=2; }
        else if (used+1<tokens.length && tokens[used].equals("menos") && tokens[used+1].equals("cuarto")) { h=(h+23)%24; min=45; used+=2; }
        else if (used<tokens.length && tokens[used].equals("y")) {
            int[] parsed=parseSmallNumber(tokens,used+1,59);
            if (parsed[0]>=0) { min=parsed[0]; used=parsed[1]; }
        }
        String remainder=String.join(" ",Arrays.copyOfRange(tokens,Math.min(used,tokens.length),tokens.length));
        h=adjustDayPart(h,remainder);
        return validTime(h,min) ? LocalTime.of(h,min) : null;
    }

    /** Devuelve [valor, índice final exclusivo] para un número pequeño escrito con hasta tres tokens. */
    private static int[] parseSmallNumber(String[] tokens, int start, int max) {
        if (start < 0 || start >= tokens.length) return new int[]{-1,start};
        int best=-1,bestEnd=start;
        for (int len=1; len<=3 && start+len<=tokens.length; len++) {
            int v=parseNumberTokens(tokens,start,start+len);
            if (v>=0 && v<=max) { best=v; bestEnd=start+len; }
        }
        return new int[]{best,bestEnd};
    }

    /**
     * Ajusta una hora de 12 h usando la franja horaria más cercana al marcador.
     * Esto cubre tanto "a las cinco de la tarde" como "esta tarde a las cinco"
     * y "mañana por la tarde a las cinco y media".
     */
    private static int adjustDayPartFromContext(int hour, String full, int anchorStart, int anchorEnd) {
        if (full == null || full.isBlank()) return hour;
        Matcher cue = Pattern.compile("\\b(?:esta|por\\s+la|de\\s+la|durante\\s+(?:toda\\s+)?la)\\s+(manana|tarde|noche|madrugada)\\b").matcher(full);
        String best = "";
        int bestDistance = Integer.MAX_VALUE;
        while (cue.find()) {
            int distance;
            if (cue.end() <= anchorStart) distance = anchorStart - cue.end();
            else if (cue.start() >= anchorEnd) distance = cue.start() - anchorEnd;
            else distance = 0;
            if (distance <= 80 && distance < bestDistance) {
                bestDistance = distance;
                best = cue.group(1);
            }
        }
        return best.isBlank() ? hour : adjustDayPart(hour, best);
    }

    private static int adjustDayPart(int hour, String tail) {
        String t=norm(tail);
        if (t.contains("noche") && hour==12) return 0;
        if ((t.contains("tarde") || t.contains("noche")) && hour>=1 && hour<12) return hour+12;
        if (t.contains("madrugada") && hour==12) return 0;
        return hour;
    }

    private static boolean validTime(int h,int m){return h>=0&&h<=23&&m>=0&&m<=59;}

    /** Convierte números españoles escritos con palabras (hasta miles). */
    public static int parseNumberPhrase(String phrase) {
        if (phrase==null) return -1;
        String p=norm(phrase).trim();
        try { return Integer.parseInt(p); } catch(Exception ignored) {}
        if (p.isBlank()) return -1;
        return parseNumberTokens(p.replaceAll("[^a-z0-9]+"," ").trim().split("\\s+"),0,p.replaceAll("[^a-z0-9]+"," ").trim().split("\\s+").length);
    }

    private static int parseNumberTokens(String[] tokens,int from,int to) {
        if (from<0||to>tokens.length||from>=to) return -1;
        int total=0,current=0; boolean any=false;
        for(int i=from;i<to;i++) {
            String t=tokens[i];
            if (t.equals("y")) continue;
            try { int d=Integer.parseInt(t); current+=d; any=true; continue; } catch(Exception ignored) {}
            Integer n=NUMBERS.get(t); if(n!=null){current+=n;any=true;continue;}
            Integer h=HUNDREDS.get(t); if(h!=null){current+=h;any=true;continue;}
            if(t.equals("mil")){if(current==0)current=1;total+=current*1000;current=0;any=true;continue;}
            return -1;
        }
        return any?total+current:-1;
    }

    public static String norm(String s) {
        String t=s==null?"":Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD);
        return t.replaceAll("\\p{M}","");
    }
}
