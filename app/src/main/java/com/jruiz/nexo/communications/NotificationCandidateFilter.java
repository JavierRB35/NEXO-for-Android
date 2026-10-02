package com.jruiz.nexo.communications;

import com.jruiz.nexo.core.TemporalParser;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Filtro de candidatos para Nexo Inbox.
 *
 * SMS/notificaciones conservan la sensibilidad histórica. Gmail usa un filtro
 * contextual más estricto: una fecha u hora aisladas no bastan para crear Inbox.
 */
public final class NotificationCandidateFilter {
    public static final String SHARED_FILTER_REVISION = "shared-filter-v7";
    public static final String GMAIL_FILTER_REVISION = "gmail-filter-v7";

    private static final String[] ACTION_TERMS = {
            "recuerda","recordatorio","acuerdate","quedamos","quedar","cita","consulta","reunion","reunión",
            "llama","llamar","reserva","reservado","confirmacion","confirmación","vuelo","tren","embarque",
            "factura","presupuesto","poliza","póliza","vence","vencimiento","caduca","caducidad",
            "restaurante","ubicacion","ubicación","direccion","dirección","comprar","compra","trae","lleva",
            "entrega","pedido","recoger","recogida","visita","doctor","medico","médico","hospital","revision",
            "revisión","itv","seguro","renovar","pagar","pago","cumpleanos","cumpleaños","birthday",
            "calendario","calendar","invitacion","invitación","invitation","evento","te esperamos",
            "appointment","meeting","save the date","agenda","programado","programada","programación",
            "proxima cita","próxima cita","asistencia","convocatoria","sesion","sesión","clase","examen",
            "analitica","analítica","dentista","vacuna","tratamiento","taller","mantenimiento"
    };

    /**
     * Señales suficientemente personales/accionables para Gmail. Se evita incluir
     * palabras informativas aisladas como "factura", "calendar" o "fecha".
     */
    private static final String[] GMAIL_ACTION_TERMS = {
            "tu cita","su cita","cita confirmada","proxima cita","próxima cita","consulta el","consulta para",
            "appointment","meeting","convocatoria","save the date","te esperamos",
            "tu reserva","su reserva","reserva confirmada","reservado para",
            "tu vuelo","su vuelo","embarque","boarding","tren sale","salida del tren",
            "vence","vencimiento","caduca","caducidad","renovar antes","renovacion pendiente","renovación pendiente",
            "pago pendiente","debes pagar","debe pagar","pagar antes","recibo pendiente",
            "pedido listo","puedes recoger","puede recoger","recogida","entrega prevista","entrega programada",
            "llamame","llámame","llamar antes","comprar","recoger","tengo que","hay que",
            "itv","analitica","analítica","dentista","vacuna","tratamiento"
    };

    private static final String[] NOISE_TERMS = {
            "mensajes nuevos","mensaje nuevo","new messages","new message","esta escribiendo","está escribiendo",
            "typing","ha reaccionado","reacciono","reaccionó","reaccionado","notificacion silenciosa",
            "notificación silenciosa"
    };

    private static final String[] SENSITIVE_TERMS = {
            "codigo de verificacion","código de verificación","codigo verificacion","verification code","otp",
            "autenticacion","autenticación","authentication","inicio de sesion","inicio de sesión","login code",
            "contrasena","contraseña","password","clave de acceso","security code"
    };

    private NotificationCandidateFilter() {}

    /** Filtro general, usado por SMS y otras capturas automáticas. */
    public static boolean shouldCapture(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (!basicEligible(raw)) return false;
        String n = norm(raw);

        if (classification != null) {
            long dueAt = classification.optLong("dueAt", 0L);
            boolean hasTime = classification.optBoolean("hasTime", false);
            String dueDate = classification.optString("dueDate", "");
            String recurrence = classification.optString("recurrence", "");
            String location = classification.optString("locationText", "");
            boolean reminderRequested = classification.optBoolean("reminderRequested", false);
            String type = classification.optString("type", "");
            double confidence = classification.optDouble("confidence", 0.0);

            if (dueAt > 0L || !dueDate.isBlank()) return accept(classification, "Señal temporal detectada", temporalEvidence(raw, classification), SHARED_FILTER_REVISION);
            if (hasTime) return accept(classification, "Hora detectada", temporalEvidence(raw, classification), SHARED_FILTER_REVISION);
            if (!recurrence.isBlank() && !"none".equals(recurrence)) return accept(classification, "Recurrencia detectada", evidenceFor(raw, "cada"), SHARED_FILTER_REVISION);
            if (!location.isBlank()) return accept(classification, "Ubicación detectada", location, SHARED_FILTER_REVISION);
            if (reminderRequested) return accept(classification, "Petición de recordatorio", evidenceFor(raw, "recuerd"), SHARED_FILTER_REVISION);
            if (!type.isBlank() && !"note".equals(type) && confidence >= 0.45) {
                return accept(classification, "Contenido accionable clasificado", firstUsefulEvidence(raw), SHARED_FILTER_REVISION);
            }
        }

        for (String term : ACTION_TERMS) {
            if (n.contains(norm(term))) return accept(classification, "Término accionable", evidenceFor(raw, term), SHARED_FILTER_REVISION);
        }

        // El fallback temporal usa el mismo parser que RuleClassifier. Ya no hay
        // una segunda regex de fechas capaz de interpretar versiones como 3.7.4.
        TemporalParser.Result temporal = TemporalParser.parse(raw);
        if (temporal.hasDate) return accept(classification, "Fecha aparente", temporal.dateSource, SHARED_FILTER_REVISION);
        if (temporal.hasTime) return accept(classification, "Hora aparente", temporal.timeSource, SHARED_FILTER_REVISION);
        return false;
    }

    /**
     * Gmail automático exige relevancia contextual. Una fecha/hora, ubicación o
     * recurrencia aisladas no justifican por sí solas una captura.
     */
    public static boolean shouldCaptureGmail(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (!basicEligible(raw)) return false;
        String n = norm(raw);

        String type = classification == null ? "" : classification.optString("type", "");
        double confidence = classification == null ? 0.0 : classification.optDouble("confidence", 0.0);
        boolean reminderRequested = classification != null && classification.optBoolean("reminderRequested", false);
        boolean hasTemporal = classification != null && (
                classification.optLong("dueAt", 0L) > 0L ||
                !classification.optString("dueDate", "").isBlank() ||
                classification.optBoolean("hasTime", false)
        );

        if (reminderRequested) {
            return accept(classification, "Recordatorio solicitado", evidenceFor(raw, "recuerd"), GMAIL_FILTER_REVISION);
        }

        if (confidence >= 0.72 && isStrongActionType(type)) {
            return accept(classification, actionTypeReason(type), firstUsefulEvidence(raw), GMAIL_FILTER_REVISION);
        }

        String gmailCue = firstContained(n, GMAIL_ACTION_TERMS);
        if (!gmailCue.isBlank()) {
            // Señales inequívocas como vencimiento, recogida o una reserva
            // confirmada son útiles incluso cuando el mensaje no expone una fecha.
            return accept(classification, "Acción relevante en Gmail", evidenceFor(raw, gmailCue), GMAIL_FILTER_REVISION);
        }

        // Fechas y horas solo refuerzan contexto; nunca crean Inbox por sí solas.
        if (hasTemporal) {
            String genericAction = firstContained(n, ACTION_TERMS);
            if (!genericAction.isBlank() && !isWeakInformationalTerm(genericAction)) {
                return accept(classification, "Fecha/hora vinculada a una acción", evidenceFor(raw, genericAction), GMAIL_FILTER_REVISION);
            }
        }

        return false;
    }

    private static boolean basicEligible(String raw) {
        if (raw == null || raw.trim().length() < 5) return false;
        String n = norm(raw);
        for (String noise : NOISE_TERMS) {
            if (n.contains(norm(noise))) return false;
        }
        for (String sensitive : SENSITIVE_TERMS) {
            if (n.contains(norm(sensitive)) && n.matches(".*\\b\\d{4,8}\\b.*")) return false;
        }
        return true;
    }

    private static boolean isStrongActionType(String type) {
        return "event".equals(type) || "task".equals(type) || "reminder".equals(type) ||
                "expiry".equals(type) || "medication".equals(type);
    }

    private static String actionTypeReason(String type) {
        if ("event".equals(type)) return "Cita o evento detectado";
        if ("task".equals(type)) return "Acción pendiente detectada";
        if ("reminder".equals(type)) return "Recordatorio detectado";
        if ("expiry".equals(type)) return "Vencimiento detectado";
        if ("medication".equals(type)) return "Información de medicación para revisar";
        return "Contenido accionable";
    }

    private static boolean isWeakInformationalTerm(String term) {
        String n = norm(term);
        return "factura".equals(n) || "presupuesto".equals(n) || "calendar".equals(n) ||
                "calendario".equals(n) || "agenda".equals(n) || "ubicacion".equals(n) ||
                "direccion".equals(n) || "restaurante".equals(n);
    }

    private static String firstContained(String normalizedText, String[] terms) {
        for (String term : terms) {
            if (normalizedText.contains(norm(term))) return term;
        }
        return "";
    }

    private static boolean accept(JSONObject classification, String reason, String evidence, String revision) {
        if (classification != null) {
            try {
                classification.put("candidateReason", reason == null ? "" : reason);
                classification.put("candidateEvidence", evidence == null ? "" : evidence);
                classification.put("filterRevision", revision == null ? "" : revision);
            } catch (Exception ignored) {}
        }
        return true;
    }

    private static String temporalEvidence(String raw, JSONObject classification) {
        if (classification == null) return firstUsefulEvidence(raw);
        String date = classification.optString("dueDate", "");
        String time = classification.optString("dueTime", "");
        String value = (date + (time.isBlank() ? "" : " " + time)).trim();
        return value.isBlank() ? firstUsefulEvidence(raw) : value;
    }

    private static String evidenceFor(String raw, String cue) {
        String compact = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) return "";
        String normalized = norm(compact);
        String needle = norm(cue);
        int at = needle.isBlank() ? -1 : normalized.indexOf(needle);
        if (at < 0) return firstUsefulEvidence(compact);
        int start = Math.max(0, at - 42);
        int end = Math.min(compact.length(), at + Math.max(needle.length(), 12) + 70);
        String piece = compact.substring(Math.min(start, compact.length()), Math.min(end, compact.length())).trim();
        return piece.length() > 150 ? piece.substring(0, 147) + "…" : piece;
    }

    private static String firstUsefulEvidence(String raw) {
        String compact = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 150) return compact;
        return compact.substring(0, 147) + "…";
    }

    private static String norm(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
