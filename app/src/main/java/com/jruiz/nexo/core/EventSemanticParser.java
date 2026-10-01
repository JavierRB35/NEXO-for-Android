package com.jruiz.nexo.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primera capa semántica del parser V2.
 *
 * Es deliberadamente conservadora: solo devuelve entidades cuando existe una
 * evidencia textual clara. Los parsers históricos siguen siendo el respaldo.
 */
public final class EventSemanticParser {
    private EventSemanticParser() {}

    private static final Pattern ACTIVITY = Pattern.compile(
            "(?iu)\\b(cita|consulta|reuni[oó]n|evento|dentista|anal[ií]tica|entrenamiento|visita)\\b");

    private static final Pattern PURPOSE_AFTER_EVENT = Pattern.compile(
            "(?iu)\\b(?:cita|consulta|reuni[oó]n|evento)\\s+(?:para|de|sobre)\\s+" +
            "(?:(?:un|una|el|la)\\s+)?([^,.;]{3,100}?)(?=" +
            "\\s+(?:el\\s+(?:\\d{1,2}|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)|" +
            "a\\s+las?|en\\s+(?:(?:el|la)\\s+)?(?:cl[ií]nica|hospital|centro|consulta|oficina|restaurante)|" +
            "con\\s+(?:(?:el|la)\\s+)?(?:doctor|doctora|dr\\.?|dra\\.?)|" +
            "av[ií]same|recu[eé]rdame|durante)\\b|[,.;]|$)");

    private static final Pattern PURPOSE_AFTER_GENERIC_MEDICAL = Pattern.compile(
            "(?iu)^\\s*(?:dentista|cita|consulta)\\s*[,;:]\\s*([^,.;]{3,100})(?=[,.;]|$)");

    private static final Pattern PROFESSIONAL = Pattern.compile(
            "(?iu)\\b((?:doctor|doctora|dr\\.?|dra\\.?)\\s+" +
            "[\\p{L}][\\p{L}'’\\-]*(?:\\s+[\\p{L}][\\p{L}'’\\-]*){0,2})" +
            "(?=\\s*(?:[,.;]|$|\\b(?:el|a\\s+las?|hoy|ma[nñ]ana|durante|av[ií]same|recu[eé]rdame)\\b))");

    private static final String MONTH =
            "(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)";
    private static final String DATE =
            "(?:\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?|" +
            "(?:\\d{1,2}|[a-z]+(?:\\s+y\\s+[a-z]+)?)\\s+de\\s+" + MONTH +
            "(?:\\s+de\\s+(?:\\d{2,4}|[a-z]+(?:\\s+[a-z]+){0,5}))?)";
    private static final Pattern DOCUMENT_DATE = Pattern.compile(
            "\\b(?:informe|documento|resultado|radiografia|radiografia|analitica|factura|receta|justificante|certificado)" +
            "\\s+(?:de\\s+fecha\\s+|del\\s+)?(" + DATE + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static EventInterpretation parse(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        EventInterpretation out = new EventInterpretation(text);
        if (text.isBlank()) return out;

        Matcher activity = ACTIVITY.matcher(text);
        if (activity.find()) {
            out.activity = EventInterpretation.Field.explicit(
                    activity.group(1), activity.group(0), activity.start(1), activity.end(1));
        }

        Matcher purpose = PURPOSE_AFTER_EVENT.matcher(text);
        if (purpose.find()) {
            String value = cleanupPurpose(purpose.group(1));
            if (isUsefulPurpose(value)) {
                out.purpose = EventInterpretation.Field.explicit(
                        value, purpose.group(1), purpose.start(1), purpose.end(1));
            }
        }
        if (!out.purpose.hasValue()) {
            Matcher medical = PURPOSE_AFTER_GENERIC_MEDICAL.matcher(text);
            if (medical.find()) {
                String value = cleanupPurpose(medical.group(1));
                if (isUsefulPurpose(value)) {
                    out.purpose = EventInterpretation.Field.explicit(
                            value, medical.group(1), medical.start(1), medical.end(1));
                }
            }
        }

        Matcher professional = PROFESSIONAL.matcher(text);
        while (professional.find()) {
            String evidence = professional.group(1).trim();
            out.people.add(new EventInterpretation.Person(
                    evidence, "professional", evidence, professional.start(1), professional.end(1)));
        }

        Matcher doc = DOCUMENT_DATE.matcher(normalize(text));
        while (doc.find()) {
            out.documents.add(new EventInterpretation.DocumentReference(
                    documentKind(doc.group(0)), doc.group(0), doc.start(), doc.end()));
        }

        return out;
    }

    /**
     * Enmascara fechas ligadas inequívocamente a documentos para que no compitan
     * con la fecha principal del evento. Conserva longitud y posiciones.
     */
    public static String maskNonEventDateReferences(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return normalizedText == null ? "" : normalizedText;
        StringBuilder masked = new StringBuilder(normalizedText);
        Matcher m = DOCUMENT_DATE.matcher(normalizedText);
        while (m.find()) {
            int start = m.start(1), end = m.end(1);
            for (int i=start; i<end; i++) {
                char c = masked.charAt(i);
                if (c != '\n' && c != '\r') masked.setCharAt(i, ' ');
            }
        }
        return masked.toString();
    }

    private static String cleanupPurpose(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("(?iu)^(?:un|una|el|la)\\s+", "");
        s = s.replaceAll("(?iu)\\s+(?:y\\s+)?(?:av[ií]same|recu[eé]rdame|recordarme)\\b.*$", "");
        return s.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
    }

    private static boolean isUsefulPurpose(String value) {
        if (value == null || value.isBlank()) return false;
        String n = normalize(value);
        if (n.length() < 3) return false;
        return !n.matches("^(?:manana|hoy|lunes|martes|miercoles|jueves|viernes|sabado|domingo)(?:\\b.*)?$");
    }

    private static String documentKind(String evidence) {
        String n = normalize(evidence);
        for (String kind : new String[]{"informe","documento","resultado","radiografia","analitica","factura","receta","justificante","certificado"}) {
            if (n.startsWith(kind)) return kind;
        }
        return "documento";
    }

    private static String normalize(String value) {
        String s = value == null ? "" : Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "");
    }
}
