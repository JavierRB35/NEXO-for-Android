package com.jruiz.nexo.core.title;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5 experimental. Reparaciones conservadoras del idioma y de referencias
 * explícitas al sujeto de la cita. Sin IA, red, aprendizaje ni cambios al parser.
 * No lee el corpus, la etiqueta esperada ni campos estructurados de Nexo.
 */
final class TitleRulesV5 {
    private TitleRulesV5() {}

    public static final class Selection {
        public final String title;
        public final String rule;
        public Selection(String title, String rule) {this.title=title; this.rule=rule;}
    }

    private static final Pattern VETERINARY_PATIENT = Pattern.compile(
            "(?iu)\\b(?:voy|vamos|acudo|acudimos)\\s+con\\s+(?:mi|mis|el|la|los|las|un|una)\\s+" +
            "(perro|perra|gato|gata|mascota|conejo|coneja|hur[oó]n|hurona)\\b[^,;.]*?\\b(?:una?\\s+)?revisi[oó]n\\s+veterinaria\\b");
    private static final Pattern VET_TITLE = Pattern.compile("(?iu)^Revisi[oó]n\\s+veterinaria$");
    private static final Pattern VISIT = Pattern.compile("(?iu)^Visitar\\s+(a\\s+.+)$");
    private static final Pattern BAD_DE_AL = Pattern.compile(
            "(?iu)^(Revisi[oó]n|Control|Inspecci[oó]n|Evaluaci[oó]n|Comprobaci[oó]n)\\s+de\\s+al\\s+(.+)$");
    private static final Pattern BAD_DE_A_LA = Pattern.compile(
            "(?iu)^(Revisi[oó]n|Control|Inspecci[oó]n|Evaluaci[oó]n|Comprobaci[oó]n)\\s+de\\s+a\\s+la\\s+(.+)$");
    private static final Pattern BAD_DE_A_MI = Pattern.compile(
            "(?iu)^(Revisi[oó]n|Control|Inspecci[oó]n|Evaluaci[oó]n|Comprobaci[oó]n)\\s+de\\s+a\\s+(mi|mis|tu|tus|su|sus)\\s+(.+)$");
    private static final Pattern BAD_DE_EL = Pattern.compile(
            "(?u)\\b([Dd]e)\\s+el\\s+(?=[\\p{Ll}0-9])");

    public static Selection derive(String raw,String baselineTitle,String category,String location) {
        TitleRulesV4.Selection previous =
                TitleRulesV4.derive(raw,baselineTitle,category,location);
        String current = previous.title == null ? "" : previous.title.trim();
        String candidate = current;
        String rule = "v4-conservado:" + previous.rule;
        Matcher m;

        // Concordancia de nominalizaciones: "revisar al gato" NO es
        // "Revisión de al gato"; "buscar el coche" NO es "Recogida de el coche".
        m=BAD_DE_AL.matcher(candidate);
        if(m.matches()) { candidate=m.group(1)+" del "+m.group(2); rule="concordancia-del"; }
        m=BAD_DE_A_LA.matcher(candidate);
        if(m.matches()) { candidate=m.group(1)+" de la "+m.group(2); rule="concordancia-la"; }
        m=BAD_DE_A_MI.matcher(candidate);
        if(m.matches()) { candidate=m.group(1)+" de "+m.group(2)+" "+m.group(3); rule="concordancia-posesivo"; }
        String contracted=BAD_DE_EL.matcher(candidate).replaceAll("$1l ");
        if(!contracted.equals(candidate)) {candidate=contracted;rule="contraccion-del";}

        // Visitar a alguien es una visita: normalización segura de una acción
        // que figura literalmente en el título, sin añadir participantes.
        m=VISIT.matcher(candidate);
        if(m.matches()) {candidate="Visita "+m.group(1);rule="visita-accion";}

        // En "voy con mi gato a una revisión veterinaria" el paciente está
        // dicho explícitamente; la V4 lo perdía. Solo nombres de animales
        // identificables y título genérico de revisión veterinaria.
        if(VET_TITLE.matcher(candidate).matches()) {
            m=VETERINARY_PATIENT.matcher(raw==null?"":raw);
            if(m.find()) {
                String animal=m.group(1).toLowerCase(Locale.ROOT);
                // Para masculino corresponde "del perro/gato"; para femenino "de la perra/gata".
                String article=(animal.equals("mascota") || animal.endsWith("a")) ? "de la " : "del ";
                candidate="Revisión veterinaria "+article+animal;
                rule="veterinaria-sujeto-explicito";
            }
        }

        candidate=candidate.replaceAll("\\s+"," ").trim();
        if(candidate.isBlank() || candidate.length()>72 ||
                candidate.matches("(?iu).*(?:\\b(?:a|de|del|para|por|con|en|sobre)\\s*)$")) {
            return new Selection(current,"v4-conservado:control-calidad");
        }
        if(candidate.equals(current))return new Selection(current,"v4-conservado:"+previous.rule);
        return new Selection(candidate.substring(0,1).toUpperCase(Locale.ROOT)+candidate.substring(1),rule);
    }
}
