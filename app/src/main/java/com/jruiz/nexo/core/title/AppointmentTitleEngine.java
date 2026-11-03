package com.jruiz.nexo.core.title;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Preserves explicit main actions and polarity; never mutates parser data. Provenance: validated V8.1 laboratory. */
public final class AppointmentTitleEngine {
    private AppointmentTitleEngine() {}
    public static final String REVIEW_WARNING = "Revisa el título: conserva una instrucción larga sin recortarla.";
    public record Selection(String title, String rule, boolean needsReview) {}
    private static final String INF = "[\\p{L}]+(?:ar|er|ir)(?:me|te|se|nos|lo|la|los|las)?";
    private static final Pattern FRAME = Pattern.compile("(?iu)^(?:(?:tengo|tenemos|hay)\\s+que|necesito|necesitamos|debo|debemos|quiero|queremos|voy\\s+a|vamos\\s+a)\\s+((?:no\\s+)?("+INF+")\\s+.+)$");
    private static final Pattern NEGATIVE = Pattern.compile("(?iu)^no\\s+("+INF+")\\s+.+$");
    private static final Pattern POLARITY = Pattern.compile("(?iu)\\b(?:no|sin|ni|sino)\\b");
    private static final String DAYS="lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo";
    private static final String MONTHS="enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
    private static final String DATE="(?:el\\s+)?(?:\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?|(?:(?:"+DAYS+")\\s+)?\\d{1,2}\\s+de\\s+(?:"+MONTHS+")(?:\\s+de\\s+\\d{4})?)";
    private static final String TIME="a\\s+las?\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+(?:y\\s+(?:media|cuarto)|menos\\s+cuarto))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?";
    private static final String DAYPART="(?:por|de)\\s+la\\s+(?:ma[nñ]ana|tarde|noche)";
    private static final String REL="(?:pasado\\s+ma[nñ]ana|ma[nñ]ana|hoy|(?:el\\s+)?(?:pr[oó]ximo\\s+)?(?:"+DAYS+"))";
    private static final Pattern SUFFIX=Pattern.compile("(?iu)\\s+(?:"+DATE+"|"+TIME+"|"+REL+")(?:\\s+"+DAYPART+")?(?:\\s+"+TIME+")?(?:\\s+en\\s+[^;.!?]+)?[.!?]*$");

    public static Selection derive(String raw, String base, String category, String location) {
        String previous=TitleRulesV7.derive(raw,base,category,location).title;
        String core=core(raw);
        Matcher framed=FRAME.matcher(core);
        Matcher negative=NEGATIVE.matcher(core);
        String action="", candidate="";
        if(framed.matches()) { candidate=framed.group(1); action=framed.group(2); }
        else if(negative.matches()) { candidate=core; action=negative.group(1); }
        else return new Selection(previous,"v7-conservado",false);

        // Same polarity words alone do not prove scope. Preserve the complete source clause.
        boolean polarity=POLARITY.matcher(candidate).find();
        boolean missingAction=!containsAction(previous,action);
        // Attendance/transport/light verbs may already have a faithful nominal title.
        // Only protect explicit management actions or substantive actions with a purpose.
        String infinitive=norm(action);
        boolean management=infinitive.matches("cancelar|anular|aplazar|posponer|confirmar|rechazar|autorizar");
        boolean logistics=infinitive.matches("ver|hacer|llevar|pasar|ir|reunir(?:me|nos)?|celebrar|asistir|participar|coger|buscar");
        boolean hasPurpose=Pattern.compile("(?iu)\\bpara\\b").matcher(candidate).find();
        if(!polarity && !(management || (hasPurpose && !logistics)))
            return new Selection(previous,"v7-conservado",false);
        if(!polarity && !missingAction) return new Selection(previous,"v7-conservado",false);
        candidate=capitalize(candidate);
        // Never silently truncate an object or a negation to satisfy a length limit.
        boolean review=candidate.length()>92 || candidate.split("\\s+").length>17;
        return new Selection(candidate,polarity?"polaridad-explicita":"accion-principal-explicita",review);
    }
    private static boolean containsAction(String title,String action) {
        String a=norm(action).replaceFirst("(?:me|te|se|nos|lo|la|los|las)$","");
        String stem=a.length()>3?a.substring(0,a.length()-2):a;
        String alternatives=switch(a) {
            case "hacer" -> "hacer|realiz|revision|consulta|analitica|prueba";
            case "ir" -> "ir|visita";
            case "arreglar" -> "arregl|repar";
            case "ensamblar" -> "ensambl|mont";
            case "abonar" -> "abon|pag";
            case "apuntar", "inscribir" -> "apunt|inscri";
            case "reunir" -> "reun";
            case "ver" -> "ver|revision|consulta";
            default -> Pattern.quote(stem);
        };
        return Pattern.compile("\\b(?:"+alternatives+")\\p{L}*\\b").matcher(norm(title)).find();
    }
    private static String core(String raw) {
        String s=raw==null?"":raw.replaceAll("\\s+"," ").trim();
        s=s.replaceFirst("(?iu)^(?:recu[eé]rdame|recuerda|apunta|anota)(?:\\s+que)?\\s*[:,]?\\s*","");
        s=s.replaceFirst("(?iu)[;,]\\s*(?:av[ií]same|recu[eé]rdame)\\b.*$","");
        s=SUFFIX.matcher(s).replaceFirst("");
        return s.replaceAll("[.!?]+$","").trim();
    }
    private static String norm(String s) { return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}",""); }
    private static String capitalize(String s) { return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1); }
}
