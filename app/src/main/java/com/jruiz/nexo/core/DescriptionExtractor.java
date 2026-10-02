package com.jruiz.nexo.core;

import java.util.regex.Pattern;

/**
 * Extrae una descripción breve a partir de la captura original sin depender de IA.
 * El objetivo no es resumir creativamente, sino quitar datos que ya viven en campos
 * estructurados (fecha, hora, ubicación, avisos y periodicidad) y conservar lo útil.
 */
public final class DescriptionExtractor {
    private DescriptionExtractor() {}

    public static String extract(String raw, String locationText) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String preservedDetails = EventSemanticParser.preservedDetails(s);

        String structuredAppointment = extractStructuredAppointmentDetails(s);
        if (!structuredAppointment.isBlank()) return mergeDetails(structuredAppointment, preservedDetails);

        // Ubicación ya extraída a su propio campo.
        if (locationText != null && !locationText.isBlank()) {
            String q = Pattern.quote(locationText.trim());
            s = s.replaceAll("(?iu)\\b(?:en\\s+(?:el|la|los|las)\\s+|en\\s+|al\\s+|a\\s+la\\s+)?" + q, " ");
        }

        // Fechas numéricas: 23/11/2026, 23-11-26, 23.11.2026 y variantes sin año.
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?:\\s*[./-]\\s*\\d{2,4})?\\b", " ");

        // Fechas en español: 23 de enero (de 2027), veintitrés de enero de dos mil veintisiete.
        String months = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)?\\s*(?:\\d{1,2}|primero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti(?:uno|d[oó]s|tr[eé]s|cuatro|cinco|seis|siete|ocho|nueve)|treinta(?:\\s+y\\s+uno)?)\\s+de\\s+(?:"+months+")(?:\\s+de\\s+(?:\\d{2,4}|(?:mil|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+|treinta|cien|ciento|doscientos|trescientos|cuatrocientos|quinientos|seiscientos|setecientos|ochocientos|novecientos|y)\\s*){1,8})?", " ");

        // Fechas relativas y días de semana usados como fecha.
        s = s.replaceAll("(?iu)\\b(?:pasado\\s+ma[nñ]ana|ma[nñ]ana|hoy|esta\\s+ma[nñ]ana|esta\\s+tarde|esta\\s+noche)\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?(?:pr[oó]ximo|siguiente|este|esta)?\\s*(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b", " ");
        s = s.replaceAll("(?iu)\\bdentro\\s+de\\s+(?:\\d+|[a-záéíóúüñ]+(?:\\s+y\\s+[a-záéíóúüñ]+)?)\\s+(?:d[ií]as?|semanas?|meses?)\\b", " ");

        // Rango horario / hora de fin ya extraído a campos estructurados.
        // Ej.: "la cita durará desde las 18:30 hasta las 19:30" o
        // "y la cita durará hasta las 19:30" no debe contaminar la descripción.
        String hourWords = "(?:una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+)";
        String clock = "(?:\\d{1,2}(?:[:.]\\d{2})?|" + hourWords + "(?:\\s+y\\s+(?:cuarto|media|\\d+|[a-záéíóúüñ]+))?(?:\\s+menos\\s+cuarto)?)";
        String dayPart = "(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?";
        s = s.replaceAll("(?iu)\\b(?:y\\s+)?(?:la\\s+)?(?:cita|reuni[oó]n|consulta|evento)?\\s*(?:durar[aá]|dura|termina|finaliza)\\s+(?:desde\\s+(?:las?\\s+)?"+clock+dayPart+"\\s+)?hasta\\s+(?:las?\\s+)?"+clock+dayPart, " ");
        s = s.replaceAll("(?iu)\\b(?:desde|de)\\s+(?:las?\\s+)?"+clock+dayPart+"\\s+(?:hasta|a)\\s+(?:las?\\s+)?"+clock+dayPart, " ");
        s = s.replaceAll("(?iu)\\bhasta\\s+(?:las?\\s+)?"+clock+dayPart, " ");

        // Horas explícitas y franjas horarias, evitando que queden en la descripción.
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+\\d{1,2}(?:[:.]\\d{2})?(?:\\s*(?:h|horas?))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+(?:una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\w+)(?:\\s+y\\s+(?:cuarto|media|\\d+|[a-záéíóúüñ]+))?(?:\\s+menos\\s+cuarto)?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:por\\s+la\\s+ma[nñ]ana|por\\s+la\\s+tarde|por\\s+la\\s+noche|al\\s+mediod[ií]a|a\\s+medianoche)\\b", " ");
        s = s.replaceAll("(?iu)\\bde\\s+la\\s+(?:ma[nñ]ana|tarde|noche)\\b", " ");

        // Avisos: quita la instrucción de aviso, pero intenta conservar el motivo posterior.
        s = s.replaceAll("(?iu)\\b(?:(?:quiero\\s+que\\s+)?me\\s+avises|av[ií]same|avisarme|avisar|recu[eé]rdame|recordarme|recordar|acu[eé]rdate)(?:\\s+de\\s+que)?\\s*(?:\\d+|[a-záéíóúüñ]+(?:\\s+y\\s+[a-záéíóúüñ]+)?)?\\s*(?:minutos?|horas?|d[ií]as?|semanas?|meses?)?\\s*antes\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:av[ií]same|avisarme|recu[eé]rdame|recordarme)\\s+(?:en\\s+el\\s+momento|a\\s+la\\s+hora|el\\s+mismo\\s+d[ií]a)\\b", " ");

        // Periodicidad y final de serie ya viven en sus campos.
        s = s.replaceAll("(?iu)\\b(?:cada\\s+(?:d[ií]a|semana|mes|dos\\s+meses|tres\\s+meses|seis\\s+meses|a[nñ]o)|todos\\s+los\\s+(?:d[ií]as|meses|a[nñ]os)|todas\\s+las\\s+semanas|diariamente|semanalmente|mensualmente|bimestral(?:mente)?|bimensual(?:mente)?|trimestral(?:mente)?|semestral(?:mente)?|anualmente|periodicidad\\s+(?:diaria|semanal|mensual|bimestral|trimestral|semestral|anual))\\b", " ");
        s = s.replaceAll("(?iu)\\bhasta(?:\\s+el)?\\s+(?:31/12/)?20\\d{2}\\b", " ");

        // Si hay una cláusula explicativa, suele contener precisamente la descripción útil.
        String lower = s.toLowerCase();
        int because = lower.indexOf(" porque ");
        if (because >= 0 && because + 8 < s.length()) s = s.substring(because + 8).trim();
        else {
            int purpose = lower.indexOf(" para ");
            if (purpose >= 0) {
                String prefix = lower.substring(0, purpose);
                if (prefix.matches(".*\\b(?:ir|acudir|tengo|debo|necesito|cita|consulta|evento)\\b.*")) s = s.substring(purpose + 6).trim();
            }
        }

        // Frases de captura que no aportan contenido semántico.
        s = s.replaceAll("(?iu)^\\s*(?:recuerda\\s+que|apunta\\s+que|anota\\s+que|tengo\\s+que|hay\\s+que|debo|necesito)\\s+", "");
        s = s.replaceAll("(?iu)\\b(?:tengo\\s+que|hay\\s+que|debo|necesito)\\s+", " ");
        s = s.replaceAll("(?iu)^\\s*(?:ir|acudir)\\s+(?:a|al)\\s+", "");
        s = s.replaceAll("(?iu)^\\s*para\\s+", "");

        // Limpieza final: conectores huérfanos, espacios y puntuación.
        s = s.replaceAll("\\s+([,.;:])", "$1");
        s = s.replaceAll("([,;])\\s*(?:y|e)\\s*([,;])", "$1 $2");
        s = s.replaceAll("(?iu)^\\s*(?:y|e|,|;|:)\\s*", "");
        s = s.replaceAll("(?iu)\\s+(?:y|e)\\s*$", "");
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = s.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
        s = s.replaceAll("(?iu)^\\s*(?:hacerme|hacer|realizar|que\\s+me\\s+hagan)\\s+", "");
        s = s.replaceAll("(?iu)^\\s*(?:un|una|el|la)\\s+", "");
        s = s.replaceAll("\\s{2,}", " ").trim();

        if (s.length() > 420) s = s.substring(0, 417).trim() + "…";
        if (s.length() < 3) return preservedDetails;
        int words = s.split("\\s+").length;
        String n = TemporalParser.norm(s);
        boolean detailMarker = n.matches(".*\\b(?:ayunas|llevar|comparar|precio|nota|importante|documentacion|tarjeta|sin|con|preguntar|recordar)\\b.*");
        if (words <= 4 && !detailMarker) return preservedDetails;
        return mergeDetails(capitalize(s), preservedDetails);
    }

    private static String mergeDetails(String base, String preserved) {
        String result = base == null ? "" : base.trim();
        if (preserved == null || preserved.isBlank()) return result;
        for (String part : preserved.split("\\.\\s+")) {
            String detail = part.trim();
            if (detail.isBlank()) continue;
            String nr = TemporalParser.norm(result);
            String nd = TemporalParser.norm(detail);
            if (!nr.contains(nd)) {
                if (!result.isBlank() && !result.endsWith(".")) result += ".";
                if (!result.isBlank()) result += " ";
                result += detail;
            }
        }
        return result.trim();
    }

    /**
     * Limpia citas médicas copiadas desde mensajes o documentos con campos repetidos
     * (FECHA FECHA, CENTRO CENTRO, etc.) y conserva solo instrucciones útiles.
     */
    private static String extractStructuredAppointmentDetails(String raw) {
        String n = TemporalParser.norm(raw);
        boolean medical = n.contains("hospital") || n.contains("medicina nuclear") || n.contains("radiodiagnostico") || n.contains("pet-tac") || n.contains("pet tac");
        boolean structured = n.contains("especialista") || n.contains("centro centro") || n.contains("direccion") || n.contains("fecha fecha");
        if (!medical || !structured) return "";

        String instruction = "";
        java.util.regex.Matcher reminder = java.util.regex.Pattern.compile(
                "(?iu)\\b(?:no\\s+olvides|recuerda|acu[eé]rdate\\s+de)\\s+(.{3,180}?)(?=$|[.;]|\\s+(?:fecha|hora|especialista|centro|direcci[oó]n)\\b)")
                .matcher(raw);
        if (reminder.find()) instruction = cleanDetail(reminder.group(1));

        String specialist = "";
        java.util.regex.Matcher spec = java.util.regex.Pattern.compile(
                "(?iu)\\bespecialista(?:\\s+especialista)?\\s*:?\\s*(.{2,90}?)(?=\\s+(?:centro|direcci[oó]n|fecha|hora|descripci[oó]n)\\b|$|[.;])")
                .matcher(raw);
        if (spec.find()) {
            specialist = cleanDetail(spec.group(1));
            specialist = specialist.replaceFirst("(?iu)^p\\.?d\\.?\\s+(?=medicina\\s+nuclear\\b)", "");
        }

        StringBuilder out = new StringBuilder();
        if (!instruction.isBlank()) {
            String x = instruction.replaceFirst("(?iu)^que\\s+", "");
            if (x.matches("(?iu)^traer\\b.*")) out.append(capitalize(x));
            else out.append(capitalize(x));
        }
        if (!specialist.isBlank()) {
            if (out.length() > 0) out.append(". ");
            out.append("Especialista: ").append(capitalize(specialist));
        }
        String result = out.toString().replaceAll("\\.{2,}", ".").trim();
        if (!result.isBlank() && !result.endsWith(".")) result += ".";
        return result;
    }

    private static String cleanDetail(String value) {
        if (value == null) return "";
        String x = value.replaceAll("(?iu)\\b(fecha|hora|especialista|centro|direcci[oó]n)\\s+\\1\\b", "$1");
        x = x.replaceAll("\\s+", " ").trim();
        x = x.replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "").trim();
        return x;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
