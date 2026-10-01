package com.jruiz.nexo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The general capture always creates an appointment; parsers only suggest its fields. */
public final class AppointmentParser {
    private AppointmentParser() {}

    private static final Pattern EVENT_START = Pattern.compile(
            "(?iu)^(?:tengo\\s+)?(?:otra?\\s+)?(?:cita|consulta|reuni[oó]n|evento|dentista|m[eé]dico|anal[ií]tica|revisi[oó]n)\\b");

    private static final Pattern EVENT_BOUNDARY = Pattern.compile(
            "(?iu)(?:[.;]\\s*|\\s+y\\s+)(?=(?:tengo\\s+)?(?:otra?\\s+)?(?:cita|consulta|reuni[oó]n|evento|dentista|m[eé]dico|anal[ií]tica|revisi[oó]n)\\b)");

    public static RuleClassifier.Result parse(String raw) {
        return parse(raw, ParseContext.now());
    }

    public static RuleClassifier.Result parse(String raw, ParseContext context) {
        ParseContext ctx = context == null ? ParseContext.now() : context;
        String text = raw == null ? "" : raw.trim();
        RuleClassifier.Result result = RuleClassifier.classify(text, ctx);
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
     * Separa únicamente citas que tienen un segundo comienzo de evento explícito.
     * No divide "lunes y miércoles", ni listas de personas, ni cláusulas descriptivas.
     */
    public static List<Proposal> parseProposals(String raw) {
        return parseProposals(raw, ParseContext.now());
    }

    public static List<Proposal> parseProposals(String raw, ParseContext context) {
        ParseContext ctx = context == null ? ParseContext.now() : context;
        String text = raw == null ? "" : raw.replace('\n',' ').replaceAll("\\s+"," ").trim();
        ArrayList<Proposal> out = new ArrayList<>();
        if (text.isBlank()) {
            out.add(new Proposal("", parse("", ctx)));
            return out;
        }

        ArrayList<String> clauses = new ArrayList<>();
        Matcher boundary = EVENT_BOUNDARY.matcher(text);
        int start = 0;
        while (boundary.find()) {
            String left = cleanClause(text.substring(start, boundary.start()));
            if (!left.isBlank()) clauses.add(left);
            start = boundary.end();
        }
        String tail = cleanClause(text.substring(start));
        if (!tail.isBlank()) clauses.add(tail);

        // Para evitar falsos positivos, solo aceptamos la separación si cada fragmento
        // parece una cita y al menos dos tienen una referencia temporal propia.
        if (clauses.size() >= 2) {
            int temporal = 0;
            boolean allEventLike = true;
            for (String clause : clauses) {
                String probe = clause.replaceFirst("(?iu)^\\s*(?:y|e)\\s+", "").trim();
                boolean eventLike = EVENT_START.matcher(probe).find() ||
                        Pattern.compile("(?iu)\\b(?:cita|consulta|reuni[oó]n|dentista|m[eé]dico|anal[ií]tica|revisi[oó]n)\\b").matcher(probe).find();
                if (!eventLike) { allEventLike = false; break; }
                TemporalParser.Result tr = TemporalParser.parse(probe, ctx.localDateTime());
                if (tr.hasDate || tr.hasTime || tr.invalidDate || tr.invalidTime) temporal++;
            }
            if (allEventLike && temporal >= 2) {
                for (String clause : clauses) out.add(new Proposal(clause, parse(clause, ctx)));
                return out;
            }
        }

        out.add(new Proposal(text, parse(text, ctx)));
        return out;
    }

    private static String cleanClause(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
    }

    public static final class Proposal {
        public final String text;
        public final RuleClassifier.Result result;
        public Proposal(String text, RuleClassifier.Result result) {
            this.text = text == null ? "" : text;
            this.result = result;
        }
    }
}
