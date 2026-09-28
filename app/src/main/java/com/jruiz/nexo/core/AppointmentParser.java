package com.jruiz.nexo.core;

/** The general capture always creates an appointment; parsers only suggest its fields. */
public final class AppointmentParser {
    private AppointmentParser() {}

    public static RuleClassifier.Result parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        RuleClassifier.Result result = RuleClassifier.classify(text);
        result.type = "event";
        if ("Tareas".equals(result.category) || "Recordatorios".equals(result.category)) result.category = "Personal";
        result.needsReview = false;
        result.reason = "";
        if (result.description.isBlank()) result.description = text;
        result.title = LocalTitleEngine.derive(text, "event", result.category, result.locationText, result.description);
        return result;
    }
}
