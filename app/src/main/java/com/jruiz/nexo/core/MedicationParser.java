package com.jruiz.nexo.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intérprete local y conservador de pautas introducidas por el usuario.
 * NUNCA deduce una dosis o frecuencia a partir del nombre del medicamento.
 */
public final class MedicationParser {
    private MedicationParser() {}

    private static final Map<String,Integer> NUMBERS = new HashMap<>();
    static {
        String[] words = {"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte","veintiuno","veintidos","veintitres","veinticuatro","treinta","cuarenta","cincuenta","sesenta"};
        int[] vals = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,30,40,50,60};
        for (int i=0;i<words.length;i++) NUMBERS.put(words[i],vals[i]);
    }

    public static JSONObject parse(String raw) {
        JSONObject o = new JSONObject();
        JSONArray warnings = new JSONArray();
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+"," ");
        String n = RuleClassifier.norm(text);
        try {
            o.put("raw", text);
            o.put("name", extractName(text));
            o.put("dose", extractDose(text));
            o.put("reason", extractReason(text));
            o.put("instructions", extractInstructions(text));
            o.put("leadMinutes", 5);
            o.put("voiceEnabled", true);

            TemporalParser.Result tr = TemporalParser.parse(text);
            LocalDate today = LocalDate.now();
            boolean morningNight = n.contains("manana y noche") || n.contains("por la manana y por la noche");
            boolean inferredOnlyFromClock = "día inferido por hora".equals(tr.dateSource);
            boolean ignoreTomorrowAsDay = morningNight && "mañana".equals(tr.dateSource);
            LocalDate startDate = tr.hasDate && !ignoreTomorrowAsDay && !inferredOnlyFromClock ? tr.date : today;
            String startTime = tr.hasTime ? tr.time.format(DateTimeFormatter.ofPattern("HH:mm")) : "";
            o.put("startDate", startDate.toString());
            o.put("startTime", startTime);

            int intervalHours = extractIntervalHours(n);
            JSONArray times = extractTimes(text);
            String mode = "";
            if (intervalHours > 0) {
                mode = "interval";
                o.put("intervalMinutes", intervalHours * 60);
            } else if (times.length() > 0) {
                mode = "times";
                o.put("times", times);
            } else if (containsAny(n,"una sola toma","toma unica","una dosis","solo una vez")) {
                mode = "once";
            } else if (containsAny(n,"dos veces al dia","tres veces al dia","manana y noche","mañana y noche")) {
                mode = "times";
                warnings.put("Has indicado una frecuencia, pero faltan las horas concretas. Confírmalas antes de guardar.");
            }
            o.put("scheduleMode", mode);

            int durationDays = extractDurationDays(n);
            if (durationDays > 0) o.put("durationDays", durationDays);
            String until = extractUntilDate(text);
            if (!until.isBlank()) o.put("endDate", until);

            if (o.optString("name").isBlank()) warnings.put("Revisa el nombre del medicamento.");
            if (mode.isBlank()) warnings.put("Indica la pauta: cada cuántas horas, horas concretas o una sola toma.");
            if ("interval".equals(mode) && startTime.isBlank()) warnings.put("Indica la hora de la primera toma para calcular correctamente el intervalo.");
            if ("once".equals(mode) && startTime.isBlank()) warnings.put("Indica la hora de la toma.");
            if ("times".equals(mode) && times.length()==0) warnings.put("Indica las horas concretas de las tomas.");

            o.put("warnings", warnings);
            o.put("requiresConfirmation", true);
        } catch (Exception ignored) {}
        return o;
    }

    public static boolean looksLikeMedication(String raw) {
        String n = RuleClassifier.norm(raw == null ? "" : raw);
        boolean taking = containsAny(n,"tomar ","tomarme ","tomate ","toma ","debo tomar ","me toca tomar ");
        boolean intervalSchedule = Pattern.compile("\\bcada\\s+(?:\\d+|[a-z]+)\\s*(?:h|hora|horas)\\b").matcher(n).find();
        boolean explicitTimes = Pattern.compile("\\ba\\s+las?\\s+\\d{1,2}(?::\\d{2})?").matcher(n).find();
        boolean dayParts = n.contains("manana y noche") || n.contains("por la manana y por la noche");
        boolean schedule = intervalSchedule || explicitTimes || dayParts;
        boolean dose = Pattern.compile("\\b(?:\\d+(?:[.,]\\d+)?|un|una|dos|tres|cuatro|cinco|seis)\\s*(?:mg|g|ml|mcg|gramo|gramos|miligramos?|comprimidos?|capsulas?|pastillas?)\\b").matcher(n).find();
        boolean medicineCue = containsAny(n,"medicamento","medicacion","pastilla","comprimido","capsula","jarabe","dosis","antibiotico");
        if (containsAny(n,"comprar ","compra ","recoger ") && !taking && !schedule) return false;
        if (!medicineCue && !dose && taking && Pattern.compile("\\b(?:tomar|tomarme)\\s+(?:un\\s+|una\\s+)?(?:cafe|agua|te|zumo|desayuno|infusion)\\b").matcher(n).find()) return false;
        if (medicineCue) return true;
        return (taking && schedule) || (schedule && dose) || (taking && dose);
    }

    private static String extractName(String text) {
        if (text == null || text.isBlank()) return "";
        String s = text.trim();
        s = s.replaceFirst("(?i)^(?:recuerda(?:me)?\\s+|acu[eé]rdate\\s+de\\s+|(?:me\\s+)?tengo\\s+que\\s+|debo(?:\\s+de)?\\s+|me\\s+toca\\s+)?(?:tomar(?:me)?|toma)\\s+", "");
        s = s.replaceFirst("(?i)^(?:medicamento|medicaci[oó]n)\\s*[:\\-]?\\s*", "");
        s = s.replaceFirst("(?iu)^(?:un|una)?\\s*(?:pastilla|comprimido|c[aá]psula)\\s+(?:de\\s+)?", "");
        Pattern stop = Pattern.compile("(?i)\\s+(?=(?:\\d+(?:[.,]\\d+)?|un|una|dos|tres|cuatro|cinco|seis)\\s*(?:mg|g|ml|mcg|gramos?|miligramos?|comprimidos?|c[aá]psulas?|pastillas?)\\b|cada\\s+|a\\s+las?\\s+|durante\\s+|hasta\\s+|empezando\\s+|desde\\s+|con\\s+comida|en\\s+ayunas|despu[eé]s\\s+de|antes\\s+de|para\\s+|ma[nñ]ana\\s+y\\s+noche|por\\s+la\\s+ma[nñ]ana)");
        Matcher m = stop.matcher(s);
        if (m.find()) s = s.substring(0,m.start());
        s = s.replaceAll("^[,;:.\\- ]+|[,;:.\\- ]+$","").trim();
        if (s.length()>70) s=s.substring(0,70).trim();
        return titleCase(s);
    }

    private static String extractDose(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)?|un|una|dos|tres|cuatro|cinco|seis)\\s*(mg|mcg|g|ml|miligramos?|microgramos?|gramos?|mililitros?)\\b").matcher(text);
        if (!m.find()) {
            m = Pattern.compile("(?i)\\b(\\d+(?:[.,]\\d+)?|un|una|dos|tres|cuatro|cinco|seis)\\s*(comprimidos?|c[aá]psulas?|pastillas?)\\b").matcher(text);
            if (!m.find()) return "";
        }
        String amount=m.group(1).toLowerCase(Locale.ROOT);
        int word=parseNumber(amount); if(!Character.isDigit(amount.charAt(0)) && word>0) amount=String.valueOf(word);
        String unit=m.group(2).toLowerCase(Locale.ROOT);
        unit=unit.replace("miligramos","mg").replace("miligramo","mg").replace("microgramos","mcg").replace("microgramo","mcg").replace("mililitros","ml").replace("mililitro","ml");
        return amount.replace(',','.')+" "+unit;
    }

    private static int extractIntervalHours(String n) {
        Matcher m = Pattern.compile("\\bcada\\s+(\\d+|[a-z]+)\\s*(?:h|hora|horas)\\b").matcher(n);
        if (!m.find()) return 0;
        int v=parseNumber(m.group(1));
        return v>=1 && v<=48 ? v : 0;
    }

    private static int extractDurationDays(String n) {
        Matcher m=Pattern.compile("\\bdurante\\s+(\\d+|[a-z]+)\\s+dias?\\b").matcher(n);
        if(m.find()){int v=parseNumber(m.group(1));return v>0&&v<=3650?v:0;}
        Matcher w=Pattern.compile("\\bdurante\\s+(\\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho)\\s+semanas?\\b").matcher(n);
        if(w.find()){int v=parseNumber(w.group(1));return v>0&&v<=520?v*7:0;}
        return 0;
    }

    private static JSONArray extractTimes(String text) {
        JSONArray a=new JSONArray();
        if(text==null)return a;
        String n=RuleClassifier.norm(text);
        Matcher start=Pattern.compile("\\ba\\s+las?\\s+((?:\\d{1,2}(?::\\d{2})?)(?:\\s*[,y/]\\s*\\d{1,2}(?::\\d{2})?)*)").matcher(n);
        if(!start.find()) return a;
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        Matcher t=Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\b").matcher(start.group(1));
        while(t.find()){
            int h=Integer.parseInt(t.group(1)),min=t.group(2)==null?0:Integer.parseInt(t.group(2));
            if(h>=0&&h<=23&&min>=0&&min<=59) unique.add(String.format(Locale.ROOT,"%02d:%02d",h,min));
        }
        for(String x:unique)a.put(x);return a;
    }

    private static String extractUntilDate(String text) {
        if(text==null)return "";
        Matcher m=Pattern.compile("(?i)\\bhasta\\s+(?:el\\s+)?(.{2,50})$").matcher(text.trim());
        if(!m.find())return "";
        TemporalParser.Result tr=TemporalParser.parse(m.group(1));
        return tr.hasDate?tr.date.toString():"";
    }

    private static String extractInstructions(String text) {
        if(text==null)return "";
        String n=RuleClassifier.norm(text); List<String> p=new ArrayList<>();
        if(n.contains("en ayunas"))p.add("En ayunas");
        if(n.contains("con comida")||n.contains("con las comidas"))p.add("Con comida");
        if(n.contains("despues de comer")||n.contains("despues de las comidas"))p.add("Después de comer");
        if(n.contains("antes de comer")||n.contains("antes de las comidas"))p.add("Antes de comer");
        if(n.contains("con agua"))p.add("Con agua");
        Matcher m=Pattern.compile("(?i)\\bindicaciones?\\s*[:\\-]\\s*(.{2,120}?)(?=$|[.;])").matcher(text);
        if(m.find())p.add(m.group(1).trim());
        return String.join(" · ",new LinkedHashSet<>(p));
    }

    private static String extractReason(String text) {
        if(text==null)return "";
        Matcher m=Pattern.compile("(?i)\\bpara\\s+(?:tratar\\s+)?(.{2,90}?)(?=\\s+(?:cada|durante|hasta|a\\s+las?|con\\s+|en\\s+ayunas)|$|[.;])").matcher(text);
        if(m.find())return m.group(1).trim();
        return "";
    }

    private static int parseNumber(String s) {
        if(s==null)return 0;String n=RuleClassifier.norm(s.trim());
        try{return Integer.parseInt(n);}catch(Exception ignored){}
        if("un".equals(n)||"una".equals(n))return 1;Integer v=NUMBERS.get(n);if(v!=null)return v;
        if(n.contains(" y ")){String[] p=n.split(" y ");Integer a=NUMBERS.get(p[0]),b=p.length>1?NUMBERS.get(p[1]):null;if(a!=null&&b!=null)return a+b;}
        return 0;
    }

    private static boolean containsAny(String s,String...vals){for(String v:vals)if(s.contains(RuleClassifier.norm(v)))return true;return false;}
    private static String titleCase(String s){if(s==null||s.isBlank())return "";String[] p=s.toLowerCase(Locale.forLanguageTag("es-ES")).split(" ");StringBuilder b=new StringBuilder();for(String x:p){if(x.isBlank())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return b.toString();}
}
