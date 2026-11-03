package com.jruiz.nexo.core;

import com.jruiz.nexo.core.title.AppointmentTitleEngine;

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
        AppointmentTitleEngine.Selection title = AppointmentTitleEngine.derive(text, result.title, result.category, result.locationText);
        result.title = title.title();
        if (title.needsReview()) {
            result.needsReview = true;
            result.warnings.add(AppointmentTitleEngine.REVIEW_WARNING);
        }
        return result;
    }
}
