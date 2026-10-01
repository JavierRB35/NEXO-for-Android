package com.jruiz.nexo.core;

import org.json.JSONObject;
import org.json.JSONArray;

import java.time.ZoneId;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clasificador local determinista. Toda la interpretación se realiza en el dispositivo. */
public final class RuleClassifier {
    private RuleClassifier() {}

    public static final class Result {
        public String title = "Nota rápida";
        public String type = "note";
        public String category = "Otro";
        public String subcategory = "";
        public String tags = "";
        public long dueAt = 0L;
        public boolean hasTime = false;
        public String dueDate = "";
        public String dueTime = "";
        public String dueEndTime = "";
        public int durationMinutes = 0;
        public int priority = 1;
        public double confidence = 0.45;
        public boolean needsReview = true;
        public int suggestedLeadDays = 0;
        public boolean reminderRequested = false;
        public boolean voiceRequested = false;
        public final List<Integer> reminderLeadMinutes = new ArrayList<>();
        public String reason = "Clasificación básica";
        public String temporalSource = "";
        public String recurrence = RecurrenceEngine.NONE;
        public String recurrenceRule = "";
        public String recurrenceUntilDate = "";
        public String locationText = "";
        public String description = "";
        public String parserVersion = "event-parser-v2";
        public String professionalText = "";
        public String titleEvidence = "";
        public boolean invalidDate = false;
        public boolean invalidTime = false;
        public boolean ambiguousTime = false;
        public final List<String> warnings = new ArrayList<>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("title", title); o.put("type", type); o.put("category", category); o.put("subcategory", subcategory); o.put("tags", tags);
                o.put("dueAt", dueAt); o.put("hasTime", hasTime); o.put("dueDate", dueDate); o.put("dueTime", dueTime); o.put("dueEndTime", dueEndTime); o.put("durationMinutes", durationMinutes);
                o.put("priority", priority); o.put("confidence", confidence); o.put("needsReview", needsReview);
                o.put("suggestedLeadDays", suggestedLeadDays); o.put("reminderRequested", reminderRequested); o.put("voiceRequested", voiceRequested); o.put("reminderLeadMinutes", new JSONArray(reminderLeadMinutes)); o.put("reason", reason); o.put("temporalSource", temporalSource);
                o.put("recurrence", recurrence); o.put("recurrenceRule", recurrenceRule); o.put("recurrenceUntilDate", recurrenceUntilDate); o.put("locationText", locationText); o.put("description", description);
                o.put("invalidDate", invalidDate); o.put("invalidTime", invalidTime); o.put("ambiguousTime", ambiguousTime); o.put("warnings", new JSONArray(warnings));
                o.put("parserVersion", parserVersion); o.put("professionalText", professionalText); o.put("titleEvidence", titleEvidence);
            } catch (Exception ignored) {}
            return o;
        }
    }

    public static Result classify(String raw) {
        Result r = new Result();
        String text = raw == null ? "" : raw.trim();
        String n = norm(text);
        EventInterpretation semantic = EventSemanticParser.parse(text);
        r.professionalText = semantic.firstProfessional();
        if (semantic.purpose.hasValue()) r.titleEvidence = semantic.purpose.evidence;
        r.title = "Nota rápida";

        boolean medicationLike = MedicationParser.looksLikeMedication(text);
        if (medicationLike) {
            r.category = "Salud / Médico"; r.type = "medication"; r.confidence = .94; r.reason = "Detectada pauta de medicamento · requiere confirmación";
        } else if (containsAny(n, "itv", "seguro", "neumatic", "cambio de aceite", "aceite motor", "revision coche", "revision del coche", "revisar el coche", "matricula", "repost", "taller")) {
            r.category = "Vehículos / ITV"; r.confidence = .92; r.reason = "Detectado contenido del coche";
        } else if (containsAny(n, "restaurante", "cenar", "comer en ", "bar ", "cafeteria")) {
            r.category = "Ocio / Social"; r.type = "restaurant"; r.confidence = .88; r.reason = "Detectado restaurante";
        } else if (containsAny(n, "hotel", "vuelo", "reserva", "viaje", "aeropuerto", "ryanair", "booking")) {
            r.category = "Viaje"; r.confidence = .87; r.reason = "Detectado viaje o reserva";
        } else if (containsAny(n, "factura", "manual", "contrato", "garantia", "pdf", "documento")) {
            r.category = "Trámites"; r.type = "document"; r.confidence = .87; r.reason = "Detectado documento";
        } else if (containsAny(n, "comprar", "compra", "supermercado", "mercadona", "carrefour")) {
            r.category = "Compras"; r.confidence = .90; r.reason = "Detectada compra";
        } else if (containsAny(n, "cumpleanos", "cumple de ", "birthday")) {
            r.category = "Cumpleaños"; r.type = "event"; r.recurrence = RecurrenceEngine.ANNUAL; r.confidence = .93; r.reason = "Detectado cumpleaños · periodicidad anual";
        } else if (containsAny(n, "medico", "hospital", "salud", "farmacia", "medicacion", "dentista", "cardiologo", "cardiologia", "traumatologo", "oftalmologo", "analitica", "centro de salud", "ambulatorio", "pet-tac", "pet tac", "medicina nuclear", "radiodiagnostico", "tarjeta aseguradora")) {
            r.category = "Salud / Médico"; r.confidence = .96; r.reason = "Detectado contenido de salud";
        } else if (containsAny(n, "hipoteca", "banco", "pago", "pagar", "recibo", "finanza", "ingreso", "impuesto")) {
            r.category = "Trámites"; r.confidence = .80; r.reason = "Detectado contenido financiero";
        } else if (containsAny(n, "casa", "hogar", "caldera", "aire acondicionado", "frigorifico", "lavadora")) {
            r.category = "Casa"; r.confidence = .78; r.reason = "Detectado contenido del hogar";
        } else if (containsAny(n, "idea", "se me ocurre", "podria hacer")) {
            r.category = "Otro"; r.confidence = .82; r.reason = "Detectada idea";
        } else if (containsAny(n, "maps.app", "google.com/maps", "goo.gl/maps")) {
            r.category = "Ocio / Social"; r.type = "place"; r.confidence = .90; r.reason = "Detectado enlace de mapas";
        }

        boolean ideaExpression = n.startsWith("idea ") || n.startsWith("se me ocurre") || n.startsWith("podria hacer");
        boolean taskVerb = containsAny(n, "comprar", "llamar", "hacer ", "enviar", "pedir ", "llevar ", "recoger ", "reservar ", "solicitar ", "tengo que ", "hay que ");
        if (ideaExpression && !containsAny(n,"tengo que ","hay que ","debo ","necesito ")) taskVerb = false;
        boolean reminderVerb = containsAny(n, "recuerd", "recordar", "avisame", "avisa me", "avisar", "acuerdate");
        boolean voiceRequest = !medicationLike && containsAny(n, "por voz", "con voz", "mediante voz", "en voz alta", "aviso hablado", "recordatorio hablado", "dimelo en voz", "dime en voz alta", "que me lo diga en voz", "que me lo leas", "avisame hablando");
        boolean explicitSpokenReminder = containsAny(n,"recordatorio hablado","aviso hablado","avisame hablando");
        r.voiceRequested = voiceRequest;
        boolean expiry = containsAny(n, "vence", "vencimiento", "caduca", "caducidad", "renovar", "renovacion");
        boolean negatedEvent = Pattern.compile("\\b(?:no\\s+tengo|no\\s+hay|no\\s+quiero|cancelar|cancela|anula|anular)\\s+(?:la\\s+|una\\s+)?(?:cita|reunion|consulta|evento)\\b").matcher(n).find();
        boolean eventLike = !negatedEvent && (Pattern.compile("\\b(?:cita|reunion|consulta|evento|dentista|analitica)\\b").matcher(n).find() ||
                containsAny(n, "medico a las", "hospital a las", "analisis de sangre", "centro de salud", "pet-tac", "pet tac", "medicina nuclear", "radiodiagnostico"));

        if (eventLike && !medicationLike) {
            r.type = "event"; r.confidence = Math.max(r.confidence, .84); r.reason = "Detectada cita o evento";
        }
        if (taskVerb && !eventLike && !medicationLike) {
            r.type = "task"; if (r.category.equals("Otro")) r.category = "Tarea / Recado";
            r.confidence = Math.max(r.confidence, .84); r.reason = "Detectada acción pendiente";
        }
        if ((reminderVerb || explicitSpokenReminder) && !medicationLike) {
            if (!eventLike) {
                r.type = "reminder";
                if (r.category.equals("Otro") || r.category.equals("Tarea / Recado")) r.category = "Recordatorio";
                r.reason = "Detectada petición de recordatorio";
            }
            r.reminderRequested = true;
            r.confidence = Math.max(r.confidence, .90);
        }
        if (expiry && !medicationLike) {
            r.type = "expiry"; r.suggestedLeadDays = 30; r.confidence = Math.max(r.confidence, .90); r.reason = "Detectado vencimiento";
        }

        List<Integer> explicitLeads = parseLeadMinutes(n);
        if (reminderVerb && !explicitLeads.isEmpty()) {
            r.reminderLeadMinutes.addAll(explicitLeads);
            int first = explicitLeads.get(0);
            r.suggestedLeadDays = first % 1440 == 0 ? first / 1440 : 0;
        } else if (reminderVerb) {
            // "Recuérdame..." sin anticipación concreta significa aviso en el momento.
            r.reminderLeadMinutes.add(0);
        }
        if (voiceRequest) {
            r.reminderRequested = true;
            if (r.reminderLeadMinutes.isEmpty()) r.reminderLeadMinutes.add(0);
            r.confidence = Math.max(r.confidence, .90);
            if ("Clasificación básica".equals(r.reason)) r.reason = "Detectado aviso por voz";
        }

        RecurrenceEngine.Detection recurrence = RecurrenceEngine.detect(text);
        if (RecurrenceEngine.isRecurring(recurrence.recurrence)) {
            r.recurrence = recurrence.recurrence;
            r.recurrenceRule = recurrence.rule;
            r.recurrenceUntilDate = recurrence.untilDate;
            r.confidence = Math.max(r.confidence, .86);
            if ("Clasificación básica".equals(r.reason)) r.reason = "Detectada periodicidad";
        }

        r.locationText = LocationParser.extract(text);
        if (!r.locationText.isBlank()) {
            r.confidence = Math.max(r.confidence, .84);
            if ("Clasificación básica".equals(r.reason)) r.reason = "Detectada ubicación";
        }

        TemporalParser.Result tr = TemporalParser.parse(text);
        r.invalidDate = tr.invalidDate;
        r.invalidTime = tr.invalidTime;
        r.ambiguousTime = tr.ambiguousTime;
        if (tr.invalidDate) r.warnings.add("La fecha indicada no existe o no es válida.");
        if (tr.invalidTime) r.warnings.add("La hora indicada no es válida.");
        if (tr.ambiguousTime) r.warnings.add("La hora puede ser de la mañana o de la tarde.");
        if (tr.hasDate) {
            r.dueAt = tr.toEpochMillis(ZoneId.systemDefault()); r.hasTime = tr.hasTime; r.dueDate = tr.dateIso(); r.dueTime = tr.timeIso();
            r.dueEndTime = tr.endTimeIso(); r.durationMinutes = tr.durationMinutes();
            r.temporalSource = (tr.dateSource + (tr.hasTime ? " · " + tr.timeSource : "") + (tr.hasEndTime ? " · " + tr.endTimeSource : "")).trim();
            r.confidence = Math.max(r.confidence, .82);
        }

        // La descripción local conserva solo lo que no está ya representado en campos estructurados.
        r.description = DescriptionExtractor.extract(text, r.locationText);

        // Título local breve y semántico. Se calcula al final, cuando ya conocemos tipo/categoría/ubicación.
        r.title = LocalTitleEngine.derive(text, r.type, r.category, r.locationText, r.description);
        if (medicationLike) {
            JSONObject med = MedicationParser.parse(text);
            String medName = med.optString("name", "").trim();
            if (!medName.isBlank()) r.title = medName;
            String startDate = med.optString("startDate", "").trim();
            String startTime = med.optString("startTime", "").trim();
            if (!startDate.isBlank()) {
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(startDate);
                    java.time.LocalTime t = startTime.isBlank() ? java.time.LocalTime.of(9,0) : java.time.LocalTime.parse(startTime);
                    r.dueAt = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    r.dueDate = startDate; r.dueTime = startTime; r.hasTime = !startTime.isBlank();
                    r.temporalSource = "pauta de medicamento";
                } catch(Exception ignored) {}
            }
            r.description = ""; // La pauta se confirma y estructura en el módulo Medicamentos.
        }

        if (containsAny(n, "urgente", "muy importante", "prioridad alta")) r.priority = 3;
        else if (containsAny(n, "importante", "prioridad media")) r.priority = 2;

        r.needsReview = medicationLike || r.confidence < .72 || (r.category.equals("Otros") && r.type.equals("note"));
        if (negatedEvent) {
            r.dueAt = 0L; r.hasTime = false; r.dueDate = ""; r.dueTime = ""; r.dueEndTime = ""; r.durationMinutes = 0;
            r.needsReview = true;
            r.reason = "La frase niega o cancela una cita; confirma qué quieres hacer";
            r.warnings.add("Se ha detectado una negación o cancelación.");
        } else if (r.invalidDate) {
            r.needsReview = true; r.reason = "La fecha indicada no es válida";
        } else if (r.invalidTime) {
            r.needsReview = true; r.reason = "La hora indicada no es válida";
        } else if (r.ambiguousTime) {
            r.needsReview = true; r.reason = "Confirma si la hora es de la mañana o de la tarde";
        }
        if (eventLike && r.dueAt <= 0) {
            r.needsReview = true;
            if (r.reason == null || r.reason.isBlank() || "Detectada cita o evento".equals(r.reason)) r.reason = "Falta la fecha de la cita";
        }
        if ((r.type.equals("reminder") || r.type.equals("expiry")) && r.dueAt <= 0) {
            r.needsReview = true; r.reason = "Falta una fecha para poder avisarte";
        }
        return r;
    }


    /** Extrae uno o varios avisos expresados en lenguaje natural. */
    private static List<Integer> parseLeadMinutes(String n) {
        Set<Integer> values = new LinkedHashSet<>();
        if (n == null || n.isBlank()) return new ArrayList<>();

        if (n.contains("en el momento") || n.contains("a la hora") || n.contains("mismo dia")) values.add(0);
        if (n.contains("media hora antes")) values.add(30);
        if (n.contains("un cuarto de hora antes") || n.contains("cuarto de hora antes")) values.add(15);
        if (n.contains("dia anterior")) values.add(1440);

        Matcher compoundHours = Pattern.compile("\\b((?:(?:[a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?)\\s+)?horas?(?:\\s+y\\s+(?:media|cuarto|[a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?\\s+minutos?)))\\s+antes\\b").matcher(n);
        while (compoundHours.find()) {
            int minutes = TemporalParser.parseSpanMinutes(compoundHours.group(1));
            if (minutes >= 0 && minutes <= 5_256_000) values.add(minutes);
        }

        Matcher m = Pattern.compile("([a-z0-9]+(?:\\s+y\\s+[a-z0-9]+)?)\\s+(minuto|minutos|hora|horas|dia|dias|semana|semanas|mes|meses)\\s+antes").matcher(n);
        while (m.find()) {
            int v = TemporalParser.parseNumberPhrase(m.group(1));
            if (v < 0) continue;
            String unit = m.group(2);
            long minutes;
            if (unit.startsWith("minuto")) minutes = v;
            else if (unit.startsWith("hora")) minutes = v * 60L;
            else if (unit.startsWith("dia")) minutes = v * 1440L;
            else if (unit.startsWith("semana")) minutes = v * 10080L;
            else minutes = v * 43200L;
            if (minutes >= 0 && minutes <= 5_256_000L) values.add((int)minutes);
        }
        return new ArrayList<>(values);
    }


    private static boolean containsAny(String s, String... needles) { for (String x : needles) if (s.contains(x)) return true; return false; }
    public static String norm(String s) { return TemporalParser.norm(s); }
}
