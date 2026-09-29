package com.jruiz.nexo.communications;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtro de alta sensibilidad para Nexo Inbox.
 *
 * La decisión final pertenece al usuario: este filtro solo excluye ruido inequívoco
 * y credenciales/OTP. Si existe una señal temporal, de acción, recurrencia,
 * ubicación o una clasificación útil, el contenido debe llegar a Inbox.
 */
public final class NotificationCandidateFilter {
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(?:[0-3]?\\d[./-][01]?\\d(?:[./-]\\d{2,4})?|"+
            "[0-3]?\\d\\s+(?:de\\s+)?(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?:\\s+(?:de\\s+)?\\d{4})?|"+
            "(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)|"+
            "(?:hoy|manana|pasado manana)|"+
            "(?:proxim[oa]|siguiente)\\s+(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo|semana|mes)|"+
            "(?:dentro\\s+de|en)\\s+\\w+(?:\\s+y\\s+\\w+)?\\s+(?:dia|dias|semana|semanas|mes|meses))\\b"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:\\b(?:[01]?\\d|2[0-3])[:.]?[0-5]\\d\\s*h?\\b|"+
            "\\b(?:[01]?\\d|2[0-3])\\s*h(?:\\s*[0-5]\\d)?\\b|"+
            "\\ba\\s+las?\\s+(?:(?:[01]?\\d|2[0-3])(?:[:.]?[0-5]\\d)?|"+
            "una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\\b)"
    );
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

    public static boolean shouldCapture(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() < 5) return false;
        String n = norm(raw);

        for (String noise : NOISE_TERMS) {
            if (n.contains(norm(noise))) return false;
        }
        for (String sensitive : SENSITIVE_TERMS) {
            if (n.contains(norm(sensitive)) && OTP_PATTERN.matcher(n).find()) return false;
        }

        // Primero usamos la interpretación completa de Nexo: es más potente que
        // cualquier regex superficial y evita falsos negativos por formatos de fecha.
        if (classification != null) {
            long dueAt = classification.optLong("dueAt", 0L);
            boolean hasTime = classification.optBoolean("hasTime", false);
            String dueDate = classification.optString("dueDate", "");
            String recurrence = classification.optString("recurrence", "");
            String location = classification.optString("locationText", "");
            boolean reminderRequested = classification.optBoolean("reminderRequested", false);
            String type = classification.optString("type", "");
            double confidence = classification.optDouble("confidence", 0.0);

            if (dueAt > 0L || !dueDate.isBlank()) return accept(classification, "Señal temporal detectada");
            if (hasTime) return accept(classification, "Hora detectada");
            if (!recurrence.isBlank() && !"none".equals(recurrence)) return accept(classification, "Recurrencia detectada");
            if (!location.isBlank()) return accept(classification, "Ubicación detectada");
            if (reminderRequested) return accept(classification, "Petición de recordatorio");
            if (!type.isBlank() && !"note".equals(type) && confidence >= 0.45) {
                return accept(classification, "Contenido accionable clasificado");
            }
        }

        for (String term : ACTION_TERMS) {
            if (n.contains(norm(term))) return accept(classification, "Término accionable");
        }
        if (DATE_PATTERN.matcher(n).find()) return accept(classification, "Fecha aparente");
        if (TIME_PATTERN.matcher(n).find()) return accept(classification, "Hora aparente");

        return false;
    }

    private static boolean accept(JSONObject classification, String reason) {
        if (classification != null) {
            try { classification.put("candidateReason", reason); } catch (Exception ignored) {}
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
