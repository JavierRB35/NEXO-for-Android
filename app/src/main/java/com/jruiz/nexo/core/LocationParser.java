package com.jruiz.nexo.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor local y determinista de ubicaciones expresadas en lenguaje natural.
 *
 * Prioridad:
 * 1) campos estructurados (Lugar:, Ubicación:, Dirección: y citas médicas copiadas),
 * 2) lugares con cabecera conocida (hospital, restaurante, calle, oficina...),
 * 3) expresiones naturales del tipo "en Vithas Valencia" cuando el contexto indica
 *    claramente una cita, encuentro o acción que puede ocurrir en un lugar.
 *
 * El nivel 3 es deliberadamente conservador para no confundir expresiones temporales
 * ("en dos días", "en 30 minutos") o locuciones ("en voz alta") con ubicaciones.
 */
public final class LocationParser {
    private LocationParser() {}

    private static final String PLACE_HEAD =
            "(?:centro\\s+(?:de|del)\\s+salud|ambulatorio|hospital|cl[ií]nica|consultorio|consulta|" +
            "farmacia|restaurante|hotel|aeropuerto|estaci[oó]n|mercadona|carrefour|cafeter[ií]a|bar|" +
            "taller|colegio|universidad|oficina|oficinas|sede|calle|avenida|plaza|centro\\s+comercial|" +
            "polideportivo|estadio|cine|cines|teatro|museo|parking|aparcamiento)";

    private static final String STANDALONE_NAMED_PLACE_HEAD =
            "(?:centro\\s+(?:de|del)\\s+salud|ambulatorio|hospital|cl[ií]nica|consultorio|" +
            "farmacia|restaurante|hotel|mercadona|carrefour|cafeter[ií]a|bar|taller|colegio|" +
            "universidad|oficina|oficinas|sede|centro\\s+comercial|polideportivo|teatro|museo)";

    private static final String WEEKDAY =
            "(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)";

    private static final String MONTH =
            "(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)";

    private static final String TIME_WORD =
            "(?:una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+)";

    private static final String BOUNDARY =
            "(?:para\\s+|porque\\s+|a\\s+las?\\s+(?:\\d|" + TIME_WORD + ")|a\\s+mediod[ií]a\\b|a\\s+medianoche\\b|" +
            "el\\s+" + WEEKDAY + "\\b|este\\s+" + WEEKDAY + "\\b|" +
            "hoy\\b|ma[ñn]ana\\b|pasado\\s+ma[ñn]ana\\b|dentro\\s+de\\b|" +
            "av[ií]same\\b|avisa\\s+me\\b|recu[eé]rdame\\b|recordar\\b|" +
            "durante\\b|con\\s+(?:(?:el|la)\\s+)?(?:doctor|doctora|dr\\.?|dra\\.?)\\b|" +
            "con\\s+aviso\\b|con\\s+voz\\b|por\\s+voz\\b|" +
            "tengo\\s+que\\b|hay\\s+que\\b|debo\\b|necesito\\b)";

    public static String extract(String text) {
        if (text == null || text.isBlank()) return "";
        String compact = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();

        String structured = extractStructuredMedicalLocation(compact);
        if (!structured.isBlank()) return structured;

        Matcher explicit = Pattern.compile(
                "(?iu)\\b(?:ubicaci[oó]n|lugar|direcci[oó]n)\\s*[:\\-]\\s*(.{2,120}?)(?=$|[.;])"
        ).matcher(compact);
        if (explicit.find()) return cleanLocation(explicit.group(1));

        // Centros nombrados directamente, sin exigir "en": "Clínica Roble, doctora Vega".
        Matcher standalone = Pattern.compile(
                "(?iu)\\b(" + STANDALONE_NAMED_PLACE_HEAD + "\\s+[^,.;]{1,80}?)(?=" +
                "\\s*(?:,|[.;]|$|\\s+con\\s+(?:(?:el|la)\\s+)?(?:doctor|doctora|dr\\.?|dra\\.?)\\b|" +
                "\\s+a\\s+las?\\b|\\s+el\\s+\\d{1,2}\\b|\\s+(?:hoy|ma[nñ]ana)\\b))"
        ).matcher(compact);
        if (standalone.find()) return cleanLocation(standalone.group(1));

        String knownStop =
                "(?=\\s+(?:a\\s+(?:hacer|realizar|recoger|comprar|ver|las?\\s+(?:\\d|" + TIME_WORD + "))|para\\s+|porque\\s+|" +
                "hay\\s+que|tengo\\s+que|debo\\s+|necesito\\s+|anal[ií]tica|an[aá]lisis|revisi[oó]n|" +
                "cita\\b|consulta\\b|con\\s+(?:(?:el|la)\\s+)?(?:doctor|doctora|dr\\.?|dra\\.?)\\b|" +
                "el\\s+" + WEEKDAY + "\\b|este\\s+" + WEEKDAY + "\\b|" +
                "hoy\\b|ma[ñn]ana\\b|pasado\\s+ma[ñn]ana\\b)|$|[.;])";
        Matcher known = Pattern.compile(
                "(?iu)\\b(?:en\\s+(?:el\\s+|la\\s+|los\\s+|las\\s+)?|al\\s+|a\\s+la\\s+)(" +
                        PLACE_HEAD + ".{0,100}?)" + knownStop
        ).matcher(compact);
        if (known.find()) return cleanLocation(known.group(1));

        if (!hasGenericLocationContext(compact)) return "";

        Matcher generic = Pattern.compile(
                "(?iu)\\b(?:en\\s+|al\\s+|a\\s+la\\s+)((?:el\\s+|la\\s+|los\\s+|las\\s+)?(?:.{2,100}?))(?=\\s+" +
                        BOUNDARY + "|$|[.;])"
        ).matcher(compact);
        while (generic.find()) {
            String candidate = cleanGenericLocation(generic.group(1));
            if (isPlausibleGenericLocation(candidate)) return candidate;
        }
        return "";
    }

    private static boolean hasGenericLocationContext(String text) {
        String n = norm(text);
        return containsAny(n,
                "cita", "reunion", "consulta", "quedado", "quedar", "quedamos", "nos vemos", "encuentro", "encontramos", "encontrar",
                "evento", "reserva", "reservar", "comer", "comida", "cenar", "cena", "desayunar", "desayuno", "almorzar", "visita", "visitar", "revision",
                "recoger", "llevar", "comprar", "concierto", "clase", "curso", "partido", "entrenamiento",
                "tengo hora", "tengo cita", "tengo que estar", "voy a estar", "estare", "estaré"
        );
    }

    private static boolean isPlausibleGenericLocation(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String n = norm(candidate);
        if (n.length() < 2 || n.length() > 100 || !n.matches(".*[a-z].*")) return false;
        String semantic = n.replaceFirst("^(?:el|la|los|las)\\s+", "");

        if (semantic.matches("^(?:\\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|cuarto)\\s+" +
                "(?:minuto|minutos|hora|horas|dia|dias|semana|semanas|mes|meses|ano|anos)\\b.*")) return false;
        if (semantic.matches("^(?:" + MONTH + "|" + normRegex(WEEKDAY) + ")\\b.*")) return false;

        return !startsWithAny(semantic,
                "momento", "voz alta", "caso de", "realidad", "general", "particular", "principio", "final",
                "funcion de", "relacion con", "base a", "cuenta", "nexo", "cuaderno", "calendario", "lista",
                "recordatorio", "tarea", "reunion", "cita", "consulta", "evento"
        );
    }

    private static String extractStructuredMedicalLocation(String compact) {
        String n = norm(compact);
        if (!containsAny(n, "hospital", "clinica", "centro", "pet-tac", "pet tac", "medicina nuclear", "radiodiagnostico")) return "";
        // Solo activa el modo estructurado cuando hay etiquetas reales. De otro modo
        // "Centro de Salud Ruzafa" se confundiría con un campo llamado "Centro".
        boolean labeled = Pattern.compile("(?iu)\\b(?:centro|direcci[oó]n)\\s*:|\\bcentro\\s+centro\\b|\\bdirecci[oó]n\\s+direcci[oó]n\\b|\\bfecha\\s+fecha\\b|\\bhora\\s+hora\\b").matcher(compact).find();
        if (!labeled) return "";

        String center = extractLabeledField(compact, "centro", "direcci[oó]n|fecha|hora|especialista|descripci[oó]n|recordatorio");
        String address = extractLabeledField(compact, "direcci[oó]n", "fecha|hora|especialista|centro|descripci[oó]n|recordatorio");
        center = cleanMedicalLocationPart(center, false);
        address = cleanMedicalLocationPart(address, true);

        if (!center.isBlank() && !address.isBlank()) {
            if (norm(address).contains(norm(center))) return cleanLocation(address);
            return cleanLocation(center + ", " + address);
        }
        if (!center.isBlank()) return cleanLocation(center);
        if (!address.isBlank()) return cleanLocation(address);
        return "";
    }

    private static String extractLabeledField(String text, String label, String stopLabels) {
        Matcher m = Pattern.compile(
                "(?iu)\\b(?:" + label + ")(?:\\s+(?:" + label + "))?\\s*:?\\s*(.{2,120}?)(?=\\s+(?:" + stopLabels + ")\\b|$|[.;])"
        ).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String cleanMedicalLocationPart(String value, boolean address) {
        if (value == null) return "";
        String x = value.trim();
        x = x.replaceFirst("(?iu)^(?:centro|direcci[oó]n)\\s*:?\\s*", "");
        x = x.replaceAll("(?iu)[,\\-:]?\\s*(?:pet\\s*[- ]?tac|prueba\\s+pet(?:\\s*[- ]?tac)?)\\s*$", "");
        if (!address) x = x.replaceAll("(?iu)\\s+(?:fecha|hora|especialista)\\b.*$", "");
        return x.replaceAll("\\s+", " ").trim();
    }

    private static String cleanGenericLocation(String raw) {
        String x = cleanLocation(raw);
        if (x.isBlank()) return x;
        String n = norm(x);
        // Quita artículos gramaticales delante de tipos de lugar, pero conserva
        // nombres propios donde el artículo forma parte del nombre (p. ej. La Pepica).
        if (n.matches("^(?:el|la|los|las)\\s+(?:hospital|clinica|consultorio|farmacia|restaurante|hotel|aeropuerto|estacion|cafeteria|bar|taller|colegio|universidad|oficina|oficinas|sede|calle|avenida|plaza|centro comercial|polideportivo|estadio|cine|cines|teatro|museo|parking|aparcamiento)\\b.*")) {
            x = x.replaceFirst("(?iu)^(?:el|la|los|las)\\s+", "");
        }
        return x;
    }

    private static String cleanLocation(String raw) {
        if (raw == null) return "";
        String x = raw.trim().replaceAll("[,:;\\-]+$", "").trim();
        if (x.length() > 120) x = x.substring(0, 120).trim();
        if (x.isBlank()) return "";
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String x = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return x.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /* WEEKDAY ya contiene variantes acentuadas para regex sobre texto original; aquí solo se usa
       dentro de una regex aplicada a texto normalizado. */
    private static String normRegex(String regex) {
        return regex.replace("mi[eé]rcoles", "miercoles").replace("s[aá]bado", "sabado");
    }

    private static boolean containsAny(String s, String... values) {
        for (String value : values) if (s.contains(norm(value))) return true;
        return false;
    }

    private static boolean startsWithAny(String s, String... values) {
        for (String value : values) if (s.startsWith(norm(value))) return true;
        return false;
    }
}
