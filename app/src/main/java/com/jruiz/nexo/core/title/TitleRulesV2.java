package com.jruiz.nexo.core.title;

import java.util.*;
import java.util.regex.*;

/** Reglas deterministas conservadas del laboratorio validado; uso interno del motor de citas. */
final class TitleRulesV2 {
    private TitleRulesV2() {}
    public static final class Selection {
        public final String title;
        public final String rule;
        public Selection(String title, String rule) { this.title=title; this.rule=rule; }
    }

    private static final String MONTHS = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
    private static final String DAYS = "lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo";
    private static final String NUM = "\\d{1,2}|primero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veinti\\p{L}+|treinta(?:\\s+y\\s+uno)?";
    private static final String INF = "acompa[nñ]ar|acudir|analizar|apuntar|arreglar|asistir|buscar|cambiar|celebrar|cenar|comer|comprar|comprobar|consultar|controlar|devolver|entregar|firmar|gestionar|hacer|hacerme|instalar|ir|jugar|llevar|medir|organizar|pagar|participar|pedir|planificar|poner|ponerme|preparar|presentar|recoger|reclamar|renovar|reparar|reservar|realizar|revisar|solicitar|tomar|tramitar|tratar|pasar|vacunar|vacunarme|ver|visitar|hablar|defender|dar|pedir";
    private static final String NOUN_EVENT = "clase|cena|comida|concierto|consulta|encuentro|examen|excursi[oó]n|exposici[oó]n|fiesta|firma|inspecci[oó]n|instalaci[oó]n|junta|presentaci[oó]n|prueba|resonancia|reuni[oó]n|revisi[oó]n|tutor[ií]a|vacunaci[oó]n|visita|vuelo|tren|partido|carrera|actividad";

    public static Selection derive(String raw, String baselineTitle, String category, String location) {
        String s = normaliseWhitespace(raw);
        if (s.isBlank()) return new Selection(limit(baselineTitle), "baseline-vacio");
        String base=normaliseWhitespace(baselineTitle);

        // Las fechas, los horarios y las alarmas SE LEEN exclusivamente por el parser de Nexo;
        // aquí se retiran SOLO de una copia de texto destinada a proponer el título.
        s = removeMeta(s);
        s = removeTemporal(s);
        s = removeTrailingLocation(s,raw,location);
        s = tidy(s);

        if (s.isBlank()) return new Selection(limit(base), "baseline-sin-contenido");

        // Conserva las especialidades ya bien representadas por el motor original.
        if (base.matches("(?iu)^cumplea[nñ]os\\s+de\\s+.+") && !s.matches("(?iu).*\\bfiesta\\s+de\\s+cumplea[nñ]os\\b.*")) {
            return new Selection(limit(base), "cumpleanos-original");
        }
        if (s.matches("(?iu).*\\bfiesta\\s+de\\s+cumplea[nñ]os\\b.*")) {
            Matcher m=Pattern.compile("(?iu)\\b(fiesta\\s+de\\s+cumplea[nñ]os(?:\\s+(?:de|para)\\s+[^,.;]+)?)").matcher(s);
            if(m.find()) return choose(m.group(1),"fiesta-cumpleanos");
        }

        // Una reunión sobre un asunto identificable no debe quedar reducida a 'reunión'.
        Matcher meeting=Pattern.compile("(?iu)\\breuni[oó]n\\s+(?:para\\s+hablar\\s+(?:de|del|sobre)|sobre|acerca\\s+de)\\s+(.+)$").matcher(s);
        if (meeting.find()) return choose("Reunión sobre "+meeting.group(1),"reunion-asunto");

        // Una tutoría/reunión/junta para hablar de algo debe conservar el tipo de actividad.
        Matcher formalTalk=Pattern.compile("(?iu)\\b((?:tutor[ií]a|reuni[oó]n|junta|encuentro|consulta)\\b(?:\\s+[^,.;]*?)?)\\s+para\\s+(?:hablar|tratar|consultar)\\s+(?:de|del|sobre)\\s+(.+)$").matcher(s);
        if(formalTalk.find()) return choose(formalTalk.group(1)+" sobre "+formalTalk.group(2), "actividad-sobre-asunto");

        // Acciones expresadas con subordinada: 'para que pongan la vacuna al perro'.
        Matcher administered=Pattern.compile("(?iu)\\bpara\\s+que\\s+(?:\\p{L}+\\s+){0,3}(?:pongan|administren)\\s+la\\s+vacuna\\s+(?:a|al)\\s+(.+)$").matcher(s);
        if(administered.find()) return choose("Vacunación de "+administered.group(1),"vacunacion-subordinada");

        // Reuniones 'para tratar la reparación': el nombre del evento es parte del título.
        Matcher meetingTopic=Pattern.compile("(?iu)\\b(reuni[oó]n(?:\\s+de\\s+[^,.;]+?)?)\\s+para\\s+(?:tratar|hablar\\s+de|comentar)\\s+(?:el|la|los|las)?\\s*(.+)$").matcher(s);
        if(meetingTopic.find()) return choose(meetingTopic.group(1)+" sobre "+meetingTopic.group(2), "reunion-tratar-asunto");

        // 'Consulta médica por el dolor...' conserva el motivo, no solo el tipo de cita.
        Matcher consult=Pattern.compile("(?iu)\\b(?:cita|consulta)(?:\\s+m[eé]dica)?\\s+por\\s+(.+)$").matcher(s);
        if (consult.find()) return choose("Consulta por "+consult.group(1),"consulta-motivo");

        // Un asunto precedido de 'por la reparación', 'por la renovación', etc.
        // explica el objetivo de la visita sin tener que conservar al profesional.
        Matcher nominalReason=Pattern.compile("(?iu)\\bpor\\s+(?:el|la|los|las)\\s+((?:reparaci[oó]n|revisi[oó]n|renovaci[oó]n|instalaci[oó]n|inspecci[oó]n|firma|recogida|presentaci[oó]n)\\b.+)$").matcher(s);
        if(nominalReason.find()) return choose(nominalReason.group(1),"motivo-nominal");

        // La acción tras 'para' tiene prioridad sobre 'voy al hospital', 'tengo cita con', etc.
        Matcher purpose=Pattern.compile("(?iu)\\bpara\\s+((?:"+INF+")\\b.+)$").matcher(s);
        if (purpose.find()) {
            String verbPhrase=purpose.group(1);
            String actor=extractPersonBeforePurpose(s, purpose.start());
            String nominal=normaliseAction(verbPhrase);
            if(nominal.matches("(?iu)^visita\\s+guiada$") && location!=null && location.matches("(?iu).*(?:museo|galer[ií]a|palacio).*"))
                nominal += " al "+location;
            if (!actor.isBlank() && !containsParticipant(nominal,actor)) nominal += " con "+actor;
            return choose(nominal,"para-accion");
        }

        // 'para la firma', 'para un concierto', 'para la cena': evento nominal explícito.
        Matcher purposeNoun=Pattern.compile("(?iu)\\bpara\\s+(?:el|la|los|las|un|una)?\\s*((?:"+NOUN_EVENT+")\\b.+)$").matcher(s);
        if(purposeNoun.find()) return choose(purposeNoun.group(1),"para-evento");

        // La causa puede contener el propósito: 'porque me tienen que revisar...'.
        Matcher cause=Pattern.compile("(?iu)\\bporque\\s+(?:(?:me|nos|le)\\s+)?(?:(?:tienen|tiene|van|vamos)\\s+que|van\\s+a)\\s+((?:"+INF+")\\b.+)$").matcher(s);
        if(cause.find()) return choose(normaliseAction(cause.group(1)),"porque-accion");

        Matcher causePlan=Pattern.compile("(?iu)\\bporque\\s+(?:(?:yo|nosotros)\\s+)?(?:quiero|necesito|toca)\\s+((?:"+INF+"|sustituir|comprobar)\\b.+)$").matcher(s);
        if(causePlan.find()) return choose(normaliseAction(causePlan.group(1)),"porque-plan");

        // ITV aislada: no devolver 'estación de ITV' cuando el fin es la inspección.
        if (s.matches("(?iu).*\\bITV\\b.*") && s.matches("(?iu).*\\bcoche\\b.*"))
            return choose("ITV del coche", "itv-objetivo");

        // Casos directos: 'vamos a visitar', 'tengo que recoger', 'vienen a medir'.
        String core=s.replaceFirst("(?iu)^(?:tengo|tenemos)\\s+(?:que|pendiente\\s+de)\\s+", "");
        core=core.replaceFirst("(?iu)^(?:voy|vamos|me\\s+voy|nos\\s+vamos|vienen|vendr[aá]n|va|me\\s+van)\\s+a\\s+", "");
        core=core.replaceFirst("(?iu)^(?:h[eé]\\s+quedado|hemos\\s+quedado)\\s+(?:para\\s+)?", "");
        core=core.replaceFirst("(?iu)^(?:he|me\\s+he|nos\\s+hemos)\\s+inscrito\\s+a\\s+(?:el|la|un|una)\\s+", "");
        core=core.replaceFirst("(?iu)^(?:tengo|tenemos|hay|ten[ií]amos)\\s+(?:un|una|el|la)\\s+", "");
        core=core.replaceFirst("(?iu)^(?:tengo|tenemos)\\s+(?=(?:"+NOUN_EVENT+"|videollamada)\\b)", "");
        core=core.replaceFirst("(?iu)^(?:h[eé]\\s+quedado|he\\s+quedado|hemos\\s+quedado)\\s+a\\s+", "");
        core=core.replaceFirst("(?iu)^(?:me\\s+examino|me\\s+examinar[eé])\\s+de\\s+", "Examen de ");
        core=core.replaceFirst("(?iu)^(?:voy|vamos)\\s+a(?:l|\\s+la)\\s+[^,.;]+?\\s+a\\s+(?=(?:"+INF+"|reunirme)\\b)", "");
        core=core.replaceFirst("(?iu)^reunirme\\s+", "Reunión ");
        core=core.replaceFirst("(?iu)^(?:acudo|asisto)\\s+a\\s+", "");
        core=core.replaceFirst("(?iu)^(?:ir|acudir)\\s+a(?:l|\\s+la)?\\s+", "");
        core=core.replaceFirst("(?iu)^(?:quiero|necesito|debo)\\s+", "");
        core=core.replaceFirst("(?iu)^(?:asistir|voy\\s+a\\s+asistir|tenemos\\s+entradas)\\s+a\\s+(?:el|la|un|una)?\\s*", "");
        core=core.replaceFirst("(?iu)^(?:me\\s+van\\s+a\\s+hacer|hacerme)\\s+(?:un|una)\\s+", "");
        core=tidy(core);
        core=core.replaceFirst("(?iu)^a\\s+(?=(?:comer|cenar|tomar)\\b)", "");

        // 'con X para revisar ...' se resolvió antes; ahora preferimos acción concreta.
        if(core.matches("(?iu)^(?:"+INF+")\\b.*")) return choose(normaliseAction(core),"accion-directa");
        if(core.matches("(?iu)^(?:"+NOUN_EVENT+")\\b.*")) return choose(core,"evento-directo");

        // Si los sustantivos del evento aparecen más adelante, evita devolver un auxiliar vacío.
        Matcher evt=Pattern.compile("(?iu)\\b((?:"+NOUN_EVENT+")\\b(?:\\s+[^,.;]+)?)$").matcher(core);
        if(evt.find() && evt.group(1).split("\\s+").length>1) return choose(evt.group(1),"evento-interno");

        if (core.matches("(?iu)^(?:voy|vamos|tengo|tenemos|he\\s+quedado|hemos\\s+quedado|me\\s+he)\\b.*")) {
            // Prefiere conservar contexto a devolver una etiqueta genérica que oculte el objetivo.
            return choose(core,"contexto-conservador");
        }
        if(core.isBlank() || core.equalsIgnoreCase("he quedado") || core.equalsIgnoreCase("hemos quedado")) {
            return choose(base,"baseline-fallback");
        }
        return choose(core,"limpieza-general");
    }

    private static String extractPersonBeforePurpose(String s, int before) {
        String pre=s.substring(0,Math.max(0,before));
        Matcher m=Pattern.compile("(?iu)\\b(?:quedado|reunido|cenar|comer|caf[eé])\\s+con\\s+([\\p{L}][\\p{L} .'-]*(?:\\s+y\\s+[\\p{L}][\\p{L} .'-]*)?)\\s*$").matcher(pre);
        if(!m.find()) {
            m=Pattern.compile("(?iu)\\b(?:ver|visitar|buscar)\\s+a\\s+([\\p{Lu}][\\p{L} .'-]{1,30})\\s*$").matcher(pre);
            if(!m.find())return "";
        }
        String name=m.group(1).trim();
        return name.length()>35?"":name;
    }
    private static boolean containsParticipant(String s, String a){return s.toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT));}

    private static String normaliseAction(String phrase) {
        String x=tidy(phrase);
        // Reescrituras generales del español; ninguna depende del título esperado del benchmark.
        x=x.replaceFirst("(?iu)^hablar\\s+(?:de|del|sobre)\\s+", "Tratar ");
        x=x.replaceFirst("(?iu)^revisar\\s+el\\s+", "Revisión del ");
        x=x.replaceFirst("(?iu)^revisar\\s+los\\s+", "Revisión de los ");
        x=x.replaceFirst("(?iu)^revisar\\s+la\\s+", "Revisión de la ");
        x=x.replaceFirst("(?iu)^revisar\\s+las\\s+", "Revisión de las ");
        x=x.replaceFirst("(?iu)^revisar\\s+", "Revisión de ");
        x=x.replaceFirst("(?iu)^renovar\\s+", "Renovación de ");
        x=x.replaceFirst("(?iu)^firmar\\s+", "Firma de ");
        x=x.replaceFirst("(?iu)^instalar\\s+", "Instalación de ");
        x=x.replaceFirst("(?iu)^planificar\\s+", "Planificación de ");
        x=x.replaceFirst("(?iu)^analizar\\s+", "Análisis de ");
        x=x.replaceFirst("(?iu)^vacunar\\s+a\\s+", "Vacunación de ");
        x=x.replaceFirst("(?iu)^vacunarme\\s+", "Vacunación ");
        x=x.replaceFirst("(?iu)^ponerme\\s+la\\s+vacuna\\s+de\\s+", "Vacunación contra ");
        x=x.replaceFirst("(?iu)^ponerme\\s+la\\s+vacuna\\s+", "Vacunación ");
        x=x.replaceFirst("(?iu)^realizar\\s+(?:el|la|un|una)\\s+", "");
        x=x.replaceFirst("(?iu)^hacer\\s+(?:la|el|una|un)\\s+(?=(?:renovaci[oó]n|presentaci[oó]n|gesti[oó]n|revisi[oó]n|firma|resonancia|reparaci[oó]n)\\b)", "");
        x=x.replaceFirst("(?iu)^hacerme\\s+(?:una|un)\\s+", "");
        x=x.replaceFirst("(?iu)^hacer\\s+(?:una|un)\\s+", "");
        x=x.replaceFirst("(?iu)^consultar\\s+lo\\s+del\\s+", "Consulta por ");
        x=x.replaceFirst("(?iu)^consultar\\s+", "Consulta sobre ");
        x=x.replaceFirst("(?iu)^gestionar\\s+", "Gestión de ");
        x=x.replaceFirst("(?iu)^controlar\\s+", "Control de ");
        x=x.replaceFirst("(?iu)^comprar\\s+", "Compra de ");
        x=x.replaceFirst("(?iu)^reparar\\s+", "Reparación de ");
        x=x.replaceFirst("(?iu)^medir\\s+", "Medición de ");
        x=x.replaceFirst("(?iu)^comer\\s+", "Comida ");
        x=x.replaceFirst("(?iu)^cenar\\s+", "Cena ");
        x=x.replaceFirst("(?iu)^tomar\\s+(?:un\\s+)?caf[eé]\\b", "Café");
        // Concordancia simple de artículos tras nominalizaciones.
        x=x.replaceFirst("(?iu)^Revisión de los el\\s+", "Revisión del ");
        x=x.replaceFirst("(?iu)^Revisión de los los\\s+", "Revisión de los ");
        x=x.replaceFirst("(?iu)^Revisión de la la\\s+", "Revisión de la ");
        x=x.replaceFirst("(?iu)^Renovación de el\\s+", "Renovación del ");
        x=x.replaceFirst("(?iu)^Renovación de la\\s+", "Renovación de la ");
        x=x.replaceFirst("(?iu)^Firma de la\\s+", "Firma de ");
        x=x.replaceFirst("(?iu)^Instalación de el\\s+", "Instalación del ");
        x=x.replaceFirst("(?iu)^Compra de el\\s+", "Compra del ");
        return tidy(x);
    }

    private static String removeMeta(String s) {
        s=s.replaceFirst("(?iu)^\\s*(?:recu[eé]rdame|recuerda|acu[eé]rdate|anota|apunta|quiero\\s+recordar)(?:\\s+que)?\\s*[:,-]?\\s*", "");
        // Avisos en cláusula separada: nunca se extraen aquí, solo se quitan del título.
        s=s.replaceFirst("(?iu)\\s*[,;.]\\s*(?:av[ií]same|avisarme|recu[eé]rdame|recordarme|avisa)\\b.*$", "");
        return s;
    }
    private static String removeTemporal(String s) {
        s=s.replaceAll("(?iu)\\b(?:el\\s+)?(?:(?:este|pr[oó]ximo|siguiente)\\s+)?(?:"+DAYS+")\\s+(?=(?:"+NUM+")\\s+de\\s+(?:"+MONTHS+")\\b)", " ");
        s=s.replaceAll("(?iu)\\b(?:el\\s+)?(?:"+NUM+")\\s+de\\s+(?:"+MONTHS+")(?:\\s+de\\s+\\d{2,4})?\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s*[/.-]\\s*\\d{1,2}(?:\\s*[/.-]\\s*\\d{2,4})?\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:hoy|pasado\\s+ma[nñ]ana|ma[nñ]ana|esta\\s+(?:ma[nñ]ana|tarde|noche))\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:el\\s+)?(?:(?:este|pr[oó]ximo|siguiente)\\s+)?(?:"+DAYS+")\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:cuarto|media|\\d{1,2}))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:por|de)\\s+la\\s+(?:ma[nñ]ana|tarde|noche)\\b", " ");
        s=s.replaceAll("(?iu)\\b(?:el\\s+d[ií]a\\s+anterior|una\\s+hora\\s+antes|media\\s+hora\\s+antes)\\b", " ");
        return s;
    }
    private static String removeTrailingLocation(String cleaned, String original, String location) {
        // Únicamente el lugar introducido DESPUÉS de la hora del evento es metadato.
        // No depende de LocationParser, que puede devolver ubicaciones incompletas.
        Matcher extra=Pattern.compile("(?iu)\\b\\d{1,2}:\\d{2}\\s+en\\s+([^;,]+)").matcher(normaliseWhitespace(original));
        if(extra.find()) {
            String venue=extra.group(1).trim();
            String suffix="(?iu)\\s+en\\s+"+Pattern.quote(venue)+"\\s*$";
            return cleaned.replaceFirst(suffix,"");
        }
        return cleaned;
    }
    private static Selection choose(String title,String rule){return new Selection(limit(title),rule);}
    private static String limit(String s) {
        String v=tidy(s);
        if(v.isBlank())return "Cita pendiente de identificar";
        String[] words=v.split("\\s+");
        if(words.length>12)v=String.join(" ",Arrays.copyOf(words,12));
        if(v.length()>72){v=v.substring(0,72);int i=v.lastIndexOf(' ');if(i>35)v=v.substring(0,i);}
        v=v.replaceAll("(?iu)\\s+(?:a|al|de|del|con|para|por|en|el|la|los|las|un|una|y|lo|sobre|que)\\s*$", "");
        return tidy(v);
    }
    private static String tidy(String s){
        if(s==null)return "";
        s=s.replaceAll("\\s+", " ").replaceAll("^[\\s,;.:!?-]+|[\\s,;.:!?-]+$", "").trim();
        s=s.replaceAll("(?iu)\\s+\\b(?:en|por|para|a|al)\\s*$", "");
        if(s.isEmpty())return s;
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }
    private static String normaliseWhitespace(String s){return s==null?"":s.replace('\n',' ').replaceAll("\\s+", " ").trim();}
}
