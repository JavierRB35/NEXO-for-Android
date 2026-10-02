package com.jruiz.nexo.communications;

import com.jruiz.nexo.core.TemporalParser;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtros deterministas del Inbox.
 *
 * SMS/notificaciones conservan una sensibilidad alta. Gmail automático usa un
 * criterio contextual más exigente: una fecha u hora aislada no basta para
 * convertir un correo en una captura.
 */
public final class NotificationCandidateFilter {
    public static final String GMAIL_FILTER_RULE = "gmail-v7";
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b");

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
     * Señales que justifican por sí mismas revisar un correo. Deliberadamente no
     * incluye palabras genéricas como "factura", "seguro", "fecha", "run" o
     * "failed": necesitan contexto adicional para no llenar el Inbox.
     */
    private static final String[] GMAIL_STRONG_TERMS = {
            "recuerda","recordatorio","acuerdate","cita","consulta","reunion","reunión",
            "llama","llamar","reserva","reservado","confirmacion de reserva","confirmación de reserva",
            "vuelo","embarque","tren","vence","vencimiento","caduca","caducidad","renovar",
            "pagar","pago pendiente","pedido","recoger","recogida","entrega prevista","entrega programada",
            "visita","doctor","medico","médico","hospital","itv","cumpleanos","cumpleaños",
            "invitacion","invitación","invitation","evento","te esperamos","appointment","meeting",
            "save the date","asistencia","convocatoria","sesion","sesión","clase","examen",
            "analitica","analítica","dentista","vacuna","tratamiento","taller","mantenimiento",
            "comprar","trae","lleva"
    };

    private static final String[] TECHNICAL_MAIL_TERMS = {
            "workflow run","all jobs have failed","job annotations","view workflow run",
            "run failed","github actions","continuous integration","ci / validate","build failed",
            "commit ","branch ","pull request","package-beta","gradle"
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

    /** Filtro general de alta sensibilidad para SMS y notificaciones. */
    public static boolean shouldCapture(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (!basicSafety(raw)) return false;
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

            if (dueAt > 0L || !dueDate.isBlank()) return accept(classification, "Señal temporal detectada", temporalEvidence(classification), "inbox-general-v6");
            if (hasTime) return accept(classification, "Hora detectada", temporalEvidence(classification), "inbox-general-v6");
            if (!recurrence.isBlank() && !"none".equals(recurrence)) return accept(classification, "Recurrencia detectada", recurrence, "inbox-general-v6");
            if (!location.isBlank()) return accept(classification, "Ubicación detectada", location, "inbox-general-v6");
            if (reminderRequested) return accept(classification, "Petición de recordatorio", evidence(raw, "record"), "inbox-general-v6");
            if (!type.isBlank() && !"note".equals(type) && confidence >= 0.45) {
                return accept(classification, "Contenido accionable clasificado", type, "inbox-general-v6");
            }
        }

        for (String term : ACTION_TERMS) {
            if (n.contains(norm(term))) return accept(classification, "Término accionable", evidence(raw, term), "inbox-general-v6");
        }

        // Fallback temporal compartido con RuleClassifier: ya no existe una regex
        // independiente capaz de interpretar v3.7.4 como una fecha.
        TemporalParser.Result temporal = TemporalParser.parse(raw);
        if (temporal.hasDate) return accept(classification, "Fecha detectada", temporal.dateIso(), "inbox-general-v6");
        if (temporal.hasTime) return accept(classification, "Hora detectada", temporal.timeIso(), "inbox-general-v6");
        return false;
    }

    /**
     * Filtro Gmail v7. Una fecha/hora aislada solo describe el correo; no implica
     * que el usuario tenga que hacer algo. Se exige contexto accionable.
     */
    public static boolean shouldCaptureGmail(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (!basicSafety(raw)) return false;
        String n = norm(raw);

        String strongTerm = firstMatching(n, GMAIL_STRONG_TERMS);
        boolean technical = containsAny(n, TECHNICAL_MAIL_TERMS);
        String type = classification == null ? "" : classification.optString("type", "");
        double confidence = classification == null ? 0.0 : classification.optDouble("confidence", 0.0);
        boolean reminder = classification != null && classification.optBoolean("reminderRequested", false);
        String recurrence = classification == null ? "" : classification.optString("recurrence", "");

        if (technical && strongTerm.isBlank() &&
                (type.isBlank() || "note".equals(type) || "document".equals(type))) {
            return false;
        }

        if (classification != null) {
            if ("medication".equals(type)) {
                return accept(classification, "Contenido médico accionable", evidence(raw, "medic"), GMAIL_FILTER_RULE);
            }
            if (reminder) {
                return accept(classification, "Petición de recordatorio", evidence(raw, "record"), GMAIL_FILTER_RULE);
            }
            if (!recurrence.isBlank() && !"none".equals(recurrence) &&
                    (!strongTerm.isBlank() || !"note".equals(type))) {
                return accept(classification, "Evento recurrente relevante", recurrence, GMAIL_FILTER_RULE);
            }
            if (isActionableType(type) && confidence >= 0.45) {
                String ev = !strongTerm.isBlank() ? evidence(raw, strongTerm) : type;
                return accept(classification, "Contenido accionable para el usuario", ev, GMAIL_FILTER_RULE);
            }
        }

        if (!strongTerm.isBlank()) {
            return accept(classification, "Término accionable en Gmail", evidence(raw, strongTerm), GMAIL_FILTER_RULE);
        }

        // Una fecha/hora real sin contexto accionable no es suficiente en Gmail.
        return false;
    }

    private static boolean basicSafety(String raw) {
        if (raw == null || raw.trim().length() < 5) return false;
        String n = norm(raw);
        for (String noise : NOISE_TERMS) if (n.contains(norm(noise))) return false;
        for (String sensitive : SENSITIVE_TERMS) {
            if (n.contains(norm(sensitive)) && OTP_PATTERN.matcher(n).find()) return false;
        }
        return true;
    }

    private static boolean isActionableType(String type) {
        return "event".equals(type) || "task".equals(type) || "reminder".equals(type) ||
                "expiry".equals(type) || "restaurant".equals(type) || "place".equals(type);
    }

    private static String firstMatching(String normalized, String[] terms) {
        if (normalized == null || normalized.isBlank()) return "";
        for (String term : terms) if (normalized.contains(norm(term))) return term;
        return "";
    }

    private static boolean containsAny(String normalized, String[] terms) {
        return !firstMatching(normalized, terms).isBlank();
    }

    private static String temporalEvidence(JSONObject classification) {
        if (classification == null) return "";
        String date = classification.optString("dueDate", "");
        String time = classification.optString("dueTime", "");
        if (!date.isBlank() && !time.isBlank()) return date + " " + time;
        if (!date.isBlank()) return date;
        return time;
    }

    private static String evidence(String raw, String needle) {
        if (raw == null || raw.isBlank() || needle == null || needle.isBlank()) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        String wanted = needle.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(wanted);
        if (at < 0) return needle;
        int from = Math.max(0, at - 34), to = Math.min(raw.length(), at + wanted.length() + 58);
        String out = raw.substring(from, to).replaceAll("\\s+", " ").trim();
        if (from > 0) out = "…" + out;
        if (to < raw.length()) out = out + "…";
        return out;
    }

    private static boolean accept(JSONObject classification, String reason, String evidence, String rule) {
        if (classification != null) {
            try {
                classification.put("candidateReason", reason);
                classification.put("candidateEvidence", evidence == null ? "" : evidence);
                classification.put("filterRevision", rule == null ? "" : rule);
            } catch (Exception ignored) {}
        }
        return true;
    }

    private static String norm(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
