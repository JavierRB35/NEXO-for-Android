package com.jruiz.nexo.communications;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NotificationCandidateFilter {
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(?:[0-3]?\\d[/-][01]?\\d(?:[/-]\\d{2,4})?|[0-3]?\\d\\s+de\\s+(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)|(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)|(?:hoy|manana|pasado manana))\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:\\b(?:[01]?\\d|2[0-3])[:.]?[0-5]\\d\\s*h?\\b|\\b(?:[01]?\\d|2[0-3])\\s*h(?:\\s*[0-5]\\d)?\\b|\\ba\\s+las\\s+(?:[01]?\\d|2[0-3])(?:[:.]?[0-5]\\d)?\\b)");
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b");
    private static final String[] ACTION_TERMS = {
            "recuerda","acuerdate","quedamos","quedar","cita","reunion","llama","llamar",
            "reserva","reservado","vuelo","tren","factura","presupuesto","poliza","vence",
            "caduca","caducidad","restaurante","ubicacion","direccion","te recomiendo",
            "comprar","compra","trae","lleva","entrega","pedido","visita","doctor","medico",
            "hospital","revision","itv","seguro","renovar","pagar","pago",
            "cumpleanos","birthday","calendario","calendar","invitacion","invitation","evento de calendario",
            "te esperamos","confirmacion de cita","confirmación de cita","appointment","meeting","save the date","recordatorio de cita"
    };
    private static final String[] NOISE_TERMS = {
            "mensajes nuevos","mensaje nuevo","new messages","new message","esta escribiendo",
            "typing","ha reaccionado","reacciono","reaccionado","notificacion silenciosa"
    };
    private static final String[] SENSITIVE_TERMS = {
            "codigo de verificacion","codigo verificacion","verification code","otp",
            "autenticacion","authentication","inicio de sesion","login code","contrasena",
            "password","clave de acceso","security code"
    };

    private NotificationCandidateFilter() {}

    public static boolean shouldCapture(String text, JSONObject classification) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() < 5) return false;
        String n = norm(raw);

        for (String noise : NOISE_TERMS) if (n.contains(noise)) return false;
        for (String sensitive : SENSITIVE_TERMS) {
            if (n.contains(sensitive) && OTP_PATTERN.matcher(n).find()) return false;
        }

        for (String term : ACTION_TERMS) if (n.contains(term)) return true;
        if (DATE_PATTERN.matcher(n).find() || TIME_PATTERN.matcher(n).find()) return true;

        if (classification != null) {
            String type = classification.optString("type", "");
            double confidence = classification.optDouble("confidence", 0.0);
            if (!type.isBlank() && !"note".equals(type) && confidence >= 0.58) return true;
            if (("document".equals(type) || "place".equals(type) || "restaurant".equals(type)) && confidence >= 0.45) return true;
        }
        return false;
    }

    private static String norm(String value) {
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }
}
