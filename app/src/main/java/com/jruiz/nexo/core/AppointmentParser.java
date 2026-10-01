package com.jruiz.nexo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The general capture always creates an appointment; parsers only suggest its fields. */
public final class AppointmentParser {
    private AppointmentParser() {}

    public static RuleClassifier.Result parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        RuleClassifier.Result result = RuleClassifier.classify(text);
        result.type = "event";
        if ("Tareas".equals(result.category) || "Recordatorios".equals(result.category)) result.category = "Personal";

        // No se borra una advertencia generada por el parser. Una cita sin fecha,
        // con fecha/hora inválida o ambigua debe llegar al formulario como pendiente.
        if (result.dueAt <= 0 && !result.invalidDate && !result.invalidTime) {
            result.needsReview = true;
            if (result.reason == null || result.reason.isBlank()) result.reason = "Falta la fecha de la cita";
        }

        if (result.description.isBlank()) result.description = text;
        result.title = LocalTitleEngine.derive(text, "event", result.category, result.locationText, result.description);
        return result;
    }

    /**
     * Divide únicamente cuando aparece otra cabecera inequívoca de evento y cada
     * fragmento aporta su propia fecha. Nunca se parte por un "y" genérico.
     */
    public static List<String> splitAppointmentTexts(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        ArrayList<String> one = new ArrayList<>();
        if (text.isBlank()) return one;

        Pattern boundary = Pattern.compile(
                "(?iu)(?:[.;]\\s*|\\s+(?:y|adem[aá]s)\\s+)" +
                "(?=(?:otra\\s+|otro\\s+)?(?:cita|consulta|reuni[oó]n|evento)\\b)");
        Matcher m = boundary.matcher(text);
        ArrayList<String> parts = new ArrayList<>();
        int start = 0;
        while (m.find()) {
            String part = text.substring(start, m.start()).trim();
            if (!part.isBlank()) parts.add(part);
            start = m.end();
        }
        String tail = text.substring(start).trim();
        if (!tail.isBlank()) parts.add(tail);

        if (parts.size() < 2 || parts.size() > 5) {
            one.add(text);
            return one;
        }

        // Exigimos un ancla temporal propia en cada propuesta para no separar
        // participantes, detalles o una segunda actividad sin fecha independiente.
        for (String part : parts) {
            TemporalParser.Result temporal = TemporalParser.parse(part);
            if (!temporal.hasDate && !temporal.invalidDate) {
                one.add(text);
                return one;
            }
        }
        return parts;
    }

    public static List<RuleClassifier.Result> parseAll(String raw) {
        ArrayList<RuleClassifier.Result> out = new ArrayList<>();
        for (String part : splitAppointmentTexts(raw)) out.add(parse(part));
        return out;
    }
}
