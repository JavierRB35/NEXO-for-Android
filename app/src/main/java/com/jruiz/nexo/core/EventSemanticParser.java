package com.jruiz.nexo.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Capa semántica pequeña y determinista para eventos.
 *
 * No sustituye a los parsers actuales. Identifica papeles que no deben competir
 * entre sí (propósito, profesional y referencias documentales) y entrega esa
 * evidencia a los motores existentes.
 */
public final class EventSemanticParser {
    private EventSemanticParser() {}

    private static final String MONTHS =
            "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";

    private static final String DOCUMENT =
            "informe|radiografia|radiografía|documento|resultado|receta|presupuesto|factura|justificante|analitica|analítica|prueba";

    private static final String PROFESSIONAL =
            "doctor|doctora|dr\\.?|dra\\.?|dentista|especialista";

    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?(?!\\d)");

    private static final Pattern WORD_DATE_NORMALIZED = Pattern.compile(
            "\\b(?:\\d{1,2}|[a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?)\\s+de\\s+(?:" + MONTHS +
                    ")(?:\\s+de\\s+(?:\\d{2,4}|[a-z0-9]+(?:\\s+[a-z0-9]+){0,7}))?\\b");

    public static String titleCandidate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String text = compact(raw);

        Matcher eventPurpose = Pattern.compile(
                "(?iu)\\b(?:cita|consulta|evento)\\s+(?:para|por|de)\\s+(?:una|un|la|el)?\\s*([^,.;]{3,90})")
                .matcher(text);
        if (eventPurpose.find()) {
            String value = cleanTitleCandidate(eventPurpose.group(1));
            if (!value.isBlank()) return capitalize(value);
        }

        Matcher review = Pattern.compile(
                "(?iu)\\b(revisi[oó]n\\s+(?:de|del)\\s+[^,.;]{2,70})")
                .matcher(text);
        if (review.find()) {
            String value = cleanTitleCandidate(review.group(1));
            if (!value.isBlank()) return capitalize(value);
        }

        Matcher meeting = Pattern.compile(
                "(?iu)\\breuni[oó]n\\s+(?:de|para|sobre)\\s+([^,.;]{3,70})")
                .matcher(text);
        if (meeting.find()) {
            String value = cleanTitleCandidate(meeting.group(1));
            if (!value.isBlank()) return "Reunión de " + lowerInitial(value);
        }
        return "";
    }

    public static String professional(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Matcher m = Pattern.compile(
                "(?iu)\\b(?:con\\s+)?(?:el\\s+|la\\s+)?((?:" + PROFESSIONAL +
                        ")\\s+[\\p{L}][\\p{L} .'-]{0,60}?)(?=\\s+(?:el|la|en|para|porque|a\\s+las?)\\b|[,.;]|$)")
                .matcher(compact(raw));
        if (!m.find()) return "";
        return cleanDetail(m.group(1));
    }

    public static String preservedDetails(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Set<String> details = new LinkedHashSet<>();

        String professional = professional(raw);
        if (!professional.isBlank()) details.add("Profesional: " + capitalize(professional));

        Matcher docs = Pattern.compile(
                "(?iu)\\b(?:" + DOCUMENT + ")\\s+(?:del|de\\s+fecha|de)\\s+" +
                        "(?:\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?" +
                        "|(?:\\d{1,2}|[a-záéíóúüñ]+(?:\\s+y\\s+[a-záéíóúüñ]+)?)\\s+de\\s+(?:" + MONTHS +
                        ")(?:\\s+de\\s+(?:\\d{2,4}|[a-záéíóúüñ]+(?:\\s+[a-záéíóúüñ]+){0,7}))?)")
                .matcher(compact(raw));
        while (docs.find()) details.add(capitalize(cleanDetail(docs.group())));

        return String.join(". ", details);
    }

    /**
     * Devuelve el texto normalizado que debe competir por la fecha principal.
     * Solo elimina fechas documentales cuando hay dos o más fechas explícitas.
     */
    public static String preferredDateText(String raw) {
        String n = TemporalParser.norm(raw == null ? "" : raw);
        if (n.isBlank() || countExplicitDates(n) < 2) return n;

        String numeric = "\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?";
        String word = "(?:\\d{1,2}|[a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?)\\s+de\\s+(?:" +
                MONTHS + ")(?:\\s+de\\s+(?:\\d{2,4}|[a-z0-9]+(?:\\s+[a-z0-9]+){0,7}))?";
        return n.replaceAll(
                "\\b(?:" + normalizeRegex(DOCUMENT) + ")\\s+(?:del|de\\s+fecha|de)\\s+(?:" + numeric + "|" + word + ")",
                " ");
    }

    private static int countExplicitDates(String n) {
        int count = 0;
        Matcher numeric = NUMERIC_DATE.matcher(n);
        while (numeric.find()) {
            if (!TemporalParser.isTechnicalNumericDate(n, numeric.start(), numeric.end(), numeric.group(0))) count++;
        }
        Matcher words = WORD_DATE_NORMALIZED.matcher(n);
        while (words.find()) count++;
        return count;
    }

    private static String cleanTitleCandidate(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", " ").trim();
        s = s.replaceAll(
                "(?iu)\\s+(?:el\\s+\\d|a\\s+las?|a\\s+la|ma[nñ]ana\\b|hoy\\b|en\\s+(?:el\\s+|la\\s+)?" +
                        "(?:hospital|cl[ií]nica|centro|consulta|oficina|sede)\\b|con\\s+(?:el\\s+|la\\s+)?(?:" +
                        PROFESSIONAL + ")\\b).*$",
                "").trim();
        s = s.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
        return s;
    }

    private static String cleanDetail(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ")
                .replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "")
                .trim();
    }

    private static String compact(String raw) {
        return raw.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalizeRegex(String value) {
        return TemporalParser.norm(value)
                .replace("radiografia", "radiografia")
                .replace("analitica", "analitica");
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lowerInitial(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
