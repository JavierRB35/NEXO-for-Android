package com.jruiz.nexo.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generador local y conservador de títulos breves para todo Nexo. */
public final class LocalTitleEngine {
    private LocalTitleEngine() {}

    private static final String MONTHS = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "el","la","los","las","un","una","unos","unas","de","del","al","a","en","por","para","que","y","e","o","con","sin","mi","mis","su","sus","me","se","lo","le","es","son","esta","este","esto","esa","ese","hoy","manana","mañana"
    ));

    public static String derive(String raw, String type, String category, String location, String description) {
        String text = clean(raw);
        if (text.isBlank()) return fallback(type);
        String n = TemporalParser.norm(text);

        String birthday = birthdayTitle(text);
        if (!birthday.isBlank()) return limit(birthday);

        // Antes de atajos genéricos como "Dentista", prioriza el propósito
        // explícito de la cita: "revisión del implante dental", "reunión de presupuesto", etc.
        String semanticTitle = EventSemanticParser.titleCandidate(text);
        if (!semanticTitle.isBlank()) return limit(semanticTitle);

        if (n.contains("itv")) return "ITV";
        if (n.contains("seguro") && (n.contains("coche") || "Coche".equals(category))) {
            if (n.contains("renov") || n.contains("vence") || n.contains("caduc")) return "Renovar seguro del coche";
            return "Seguro del coche";
        }
        if (n.contains("pet-tac") || n.contains("pet tac")) {
            String facility = primaryFacility(location);
            return limit(facility.isBlank() ? "PET-TAC" : "PET-TAC · " + facility);
        }
        if (n.contains("analitica") || n.contains("analisis de sangre")) return n.contains("sangre") ? "Analítica de sangre" : "Analítica";
        if (n.contains("medicina nuclear")) return "Medicina nuclear";
        if (n.contains("dentista")) return "Dentista";
        if (n.contains("cardiolog")) return "Revisión de cardiología";

        String s = text;
        // Quita una explicación posterior del título; su contenido puede vivir en descripción.
        s = s.split("(?iu)\\s+(?:porque|ya que|puesto que)\\s+",2)[0];
        s = s.replaceAll("(?iu)\\s+durante\\s+(?:toda\\s+)?(?:la\\s+)?(?:ma[nñ]ana|tarde|noche)\\b.*$", "");
        s = stripTemporal(s);
        s = stripReminderAndRecurrence(s);
        s = s.replaceAll("(?iu)^\\s*(?:recuerda(?:me)?(?:\\s+que)?|acu[eé]rdate(?:\\s+de)?|anota(?:\\s+que)?|apunta(?:\\s+que)?|quiero\\s+recordar(?:\\s+que)?|tengo\\s+que|hay\\s+que|debo|necesito)\\s+", "");
        s = s.replaceAll("(?iu)^\\s*(?:tengo|tenemos)\\s+(?:una|un)\\s+(?:cita|evento|reuni[oó]n)\\s+(?:para|de)?\\s*", "");
        s = s.replaceAll("(?iu)^\\s*(?:cita|evento|reuni[oó]n)\\s+(?:para|de)\\s+", "");

        // Si es desplazamiento y se conoce la ubicación, no dupliques detalles posteriores.
        if (location != null && !location.isBlank()) {
            String q = Pattern.quote(location.trim());
            Matcher m = Pattern.compile("(?iu)^(?:ir|acudir|estar|quedar)\\s+(?:en|a|al|a\\s+la)\\s+(?:el\\s+|la\\s+)?" + q).matcher(s.trim());
            if (m.find()) return limit(capitalize(m.group().replaceAll("\\s+", " ")));
        }

        // Para eventos, el propósito suele ser mejor título que la frase logística.
        Matcher purpose = Pattern.compile("(?iu)\\b(?:para|a)\\s+(?:hacerme|hacer|realizar)\\s+(?:una|un)?\\s*([^,.;]{3,70})").matcher(s);
        if (("event".equals(type) || "Salud".equals(category)) && purpose.find()) {
            String p = purpose.group(1).trim();
            p = p.replaceAll("(?iu)\\s+(?:en\\s+ayunas|porque|y\\s+tengo).*$", "").trim();
            if (!p.isBlank()) return limit(capitalize(p));
        }

        s = s.replaceAll("(?iu)\\s+(?:durante\\s+)?(?:toda\\s+)?(?:la\\s+)?(?:ma[nñ]ana|tarde|noche)\\b.*$", "");
        s = s.replaceAll("(?iu)\\s+para\\s+.*$", "");
        s = cleanup(s);

        if (s.isBlank() && description != null) s = cleanup(description);
        if (s.isBlank()) return fallback(type);

        return limit(capitalize(s));
    }

    private static String birthdayTitle(String text) {
        Matcher m = Pattern.compile("(?iu)\\bcumple(?:a[nñ]os)?\\s+(?:de\\s+)?([^,.;]+)").matcher(text);
        if (!m.find()) return "";
        String who = m.group(1).replaceAll("(?iu)\\s+(?:el|hoy|ma[nñ]ana|pasado\\s+ma[nñ]ana|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b.*$", "").trim();
        who = who.replaceAll("(?iu)\\s+\\d{1,2}.*$", "").trim();
        who = who.replaceAll("(?iu)\\s+(?:primero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+|treinta)\\s+de\\s+(?:"+MONTHS+").*$", "").trim();
        return who.isBlank() ? "Cumpleaños" : "Cumpleaños de " + who;
    }

    private static String stripTemporal(String s) {
        s = s.replaceAll("(?iu)\\b(?:hoy|pasado\\s+ma[nñ]ana|ma[nñ]ana|esta\\s+ma[nñ]ana|esta\\s+tarde|esta\\s+noche)\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?(?:pr[oó]ximo|siguiente|este|esta)?\\s*(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?(?:\\d{1,2}|primero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+|treinta(?:\\s+y\\s+uno)?)\\s+de\\s+(?:"+MONTHS+")(?:\\s+de\\s+[^,.;]+)?", " ");
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:cuarto|media|\\d+))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:por\\s+la\\s+ma[nñ]ana|por\\s+la\\s+tarde|por\\s+la\\s+noche|de\\s+la\\s+ma[nñ]ana|de\\s+la\\s+tarde|de\\s+la\\s+noche)\\b", " ");
        return s;
    }

    private static String stripReminderAndRecurrence(String s) {
        s = s.replaceAll("(?iu)\\b(?:av[ií]same|avisarme|recu[eé]rdame|recordarme|avisar)(?:\\s+[^,.;]{0,45})?\\s+antes\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:cada\\s+(?:d[ií]a|semana|mes|dos\\s+meses|tres\\s+meses|seis\\s+meses|a[nñ]o)|diariamente|semanalmente|mensualmente|bimestral(?:mente)?|trimestral(?:mente)?|semestral(?:mente)?|anualmente)\\b", " ");
        s = s.replaceAll("(?iu)\\bhasta\\s+[^,.;]+", " ");
        return s;
    }

    private static String primaryFacility(String location) {
        if (location == null || location.isBlank()) return "";
        String first = location.split(",",2)[0].trim();
        String n = TemporalParser.norm(first);
        if (n.contains("hospital") || n.contains("clinica") || n.contains("centro")) return first;
        return "";
    }

    private static String limit(String s) {
        // El título generado se conserva completo. La elipsis pertenece a la
        // presentación de la tarjeta, no al dato almacenado.
        s = cleanup(s);
        return s.isBlank() ? "Nota rápida" : s;
    }

    private static String cleanup(String s) {
        if (s==null) return "";
        s=s.replaceAll("\\s+", " ").trim();
        s=s.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
        s=s.replaceAll("(?iu)^\\s*(?:el|la|un|una)\\s+", "");
        return s;
    }
    private static String clean(String s){return s==null?"":s.replace('\n',' ').replaceAll("\\s+"," ").trim();}
    private static String capitalize(String s){if(s==null||s.isBlank())return "";return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String fallback(String type){if("task".equals(type))return "Nueva tarea";if("event".equals(type))return "Nueva cita";if("reminder".equals(type))return "Recordatorio";return "Nota rápida";}
}
