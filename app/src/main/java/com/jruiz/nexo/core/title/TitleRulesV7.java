package com.jruiz.nexo.core.title;

import java.util.*;
import java.util.regex.*;

/**
 * Reglas V7: preservar la acción principal, el objeto y la finalidad.
 * Motor local determinista, sin IA, red ni entrenamiento. Solo devuelve un título;
 * no altera fechas, horas, aviso, categoría ni ningún objeto de AppointmentParser.
 */
final class TitleRulesV7 {
    private TitleRulesV7() {}
    public static final class Selection {
        public final String title, rule, action, object, purpose;
        public final int confidence;
        Selection(String t,String r,String a,String o,String p,int c){title=t;rule=r;action=a;object=o;purpose=p;confidence=c;}
    }
    private static final String MONTHS="enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
    private static final String DAYS="lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo";
    // Lista limitada a verbos cuyo objeto se puede reconocer sin inferir información externa.
    private static final String ACT="actualizar|apuntarme|arreglar|buscar|cambiar|comparar|comprobar|comprar|configurar|curarme|dejar|desparasitar|donar|entregar|enviar|firmar|hacer|inscribirme|instalar|montar|organizar|pagar|pedir|preparar|presentar|recoger|renovar|reparar|reservar|revisar|reunirme|solicitar|sustituir|tramitar|tratar|visitar|hablar|facturar|ensamblar|abonar|conocer|copiar|escribir";
    private static final Pattern MAIN=Pattern.compile("(?iu)^(?:(?:tengo|tenemos|necesito|necesitamos|debo|debemos|hay)\\s+(?:que\\s+)?|(?:voy|vamos)\\s+a\\s+|quiero\\s+)("+ACT+")\\b(?:\\s+(.+))?$");
    private static final Pattern VERB=Pattern.compile("(?iu)^("+ACT+")\\b(?:\\s+(.+))?$");
    private static final Pattern VENUE_PURPOSE=Pattern.compile("(?iu)^(?:voy|vamos|acudo|acudimos)\\s+(?:a|al|a\\s+la|en)\\s+.+?\\s+para\\s+(.+)$");
    private static final Pattern APPOINTMENT_PURPOSE=Pattern.compile("(?iu)^(?:(?:tengo|tenemos)\\s+)?(?:una?\\s+)?cita\\s+(?:(?:con|en|de)\\s+(.+?)\\s+)?para\\s+(.+)$");
    private static final Pattern VISITOR_PURPOSE=Pattern.compile("(?iu)^(?:viene|vendr[aá]|vendr[aá]n|vendr[aá]\\s+el|va\\s+a\\s+venir)\\s+.+?\\s+a\\s+("+ACT+")\\b\\s+(.+)$");
    private static final Pattern META=Pattern.compile("(?iu)^\\s*(?:recu[eé]rdame|recuerda|acu[eé]rdate|anota|apunta)(?:\\s+que)?\\s*[:,-]?\\s*");
    private static final Pattern NUM_DATE=Pattern.compile("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s*[/.-]\\s*\\d{1,2}(?:\\s*[/.-]\\s*\\d{2,4})?\\b");
    private static final Pattern WORD_DATE=Pattern.compile("(?iu)\\b(?:el\\s+)?(?:(?:"+DAYS+")\\s+)?(?:\\d{1,2}|primero)\\s+de\\s+(?:"+MONTHS+")(?:\\s+de\\s+\\d{2,4})?\\b");
    private static final Pattern TIME=Pattern.compile("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:cuarto|media|\\d{1,2}))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b");
    private static final Pattern SUBORDINATE=Pattern.compile("(?iu)^que\\s+(?:me|te|le|nos|lo|la|los|las)?\\s*(?:van\\s+a\\s+)?(curen|desparasiten|revisen|reparen|instalen|arreglen|saquen|entreguen|monten|cambien|vacunen)\\s+(.+)$");

    public static Selection derive(String raw,String baselineTitle,String category,String location) {
        TitleRulesV6.Selection v6=TitleRulesV6.derive(raw,baselineTitle,category,location);
        String previous=clean(v6.title);
        String core=metadata(raw);
        if(location!=null && !location.isBlank())core=stripTrailingLocation(core,location);
        if(core.isBlank())return selected(previous,"v6-conservado:vacio","","","",0);
        Matcher m;
        String candidate="",rule="",verb="",object="",purpose="";
        int score=0;

        // A) Acciones propias: comparar X para cambiar Y significa COMPARAR X, no cambiar Y.
        m=MAIN.matcher(core);
        if(m.matches()) {
            verb=m.group(1).toLowerCase(Locale.ROOT);
            object=orEmpty(m.group(2));
            if(!object.isBlank()) {
                candidate=actionTitle(verb,object);
                rule="accion-principal";score=94;
            }
        }

        // B) Una cita es el marco; su motivo y acción específicos son el contenido del título.
        if(candidate.isBlank()) {
            m=APPOINTMENT_PURPOSE.matcher(core);
            if(m.matches()) {
                String specialty=orEmpty(m.group(1));
                purpose=m.group(2).trim();
                String proposed=clause(purpose);
                if(!proposed.isBlank()) {
                    candidate=proposed;
                    if(specialty.matches("(?iu).*\\bfisioterapia\\b.*"))
                        candidate="Fisioterapia para "+purpose;
                    rule=specialty.matches("(?iu).*\\bfisioterapia\\b.*")?"cita-finalidad-especialidad":"cita-finalidad";score=92;
                }
            }
        }

        // C) En desplazamientos el propósito sustantivo tiene prioridad frente al lugar.
        if(candidate.isBlank()) {
            m=VENUE_PURPOSE.matcher(core);
            if(m.matches()) {
                purpose=m.group(1).trim();
                candidate=clause(purpose);
                if(!candidate.isBlank()) {rule="desplazamiento-finalidad";score=90;}
            }
        }

        // D) «Viene el técnico a reparar X»: el evento es la reparación, no la visita del técnico.
        if(candidate.isBlank()) {
            m=VISITOR_PURPOSE.matcher(core);
            if(m.matches()) {
                verb=m.group(1);object=m.group(2);
                candidate=actionTitle(verb,object);
                rule="visita-tecnica-finalidad";score=86;
            }
        }

        candidate=finish(candidate);
        if(!quality(candidate))return selected(previous,"v6-conservado:sin-candidato","","","",0);
        if(previous.equalsIgnoreCase(candidate))return selected(previous,"v6-conservado:mismo-titulo","","","",0);
        // No tocar títulos ya informativos de la V6 salvo que omitan una acción explícita.
        boolean badLead=previous.matches("(?iu)^(?:tengo|tenemos|voy|vamos|acudo|vendr[aá]|viene|me\\s+he)\\b.*");
        boolean missingAction=score>=94 && !alreadyContainsAction(previous,verb);
        boolean missingSpecificPurpose=score>=90 && (badLead || (rule.equals("cita-finalidad-especialidad") && !previous.toLowerCase(Locale.ROOT).contains("fisioterapia")));
        if(!(badLead||missingAction||missingSpecificPurpose))
            return selected(previous,"v6-conservado:seleccion-segura","","","",0);
        // Las reglas nuevas no pueden introducir una ubicación o persona que no esté en el dictado.
        return selected(candidate,rule,verb,object,purpose,score);
    }

    private static String clause(String s) {
        String t=s.trim();
        Matcher sub=SUBORDINATE.matcher(t);
        if(sub.matches()) {
            String v=sub.group(1).toLowerCase(Locale.ROOT),o=sub.group(2).trim();
            String n;
            switch(v) {
                case "curen":n="Cura ";break;
                case "desparasiten":n="Desparasitación ";break;
                case "revisen":n="Revisión ";break;
                case "reparen":case "arreglen":n="Reparación ";break;
                case "instalen":n="Instalación ";break;
                case "saquen":n="Extracción ";break;
                case "entreguen":n="Entrega ";break;
                case "monten":n="Montaje ";break;
                case "cambien":n="Cambio ";break;
                default:n="Vacunación ";break;
            }
            return n+nominal(o);
        }
        Matcher act=VERB.matcher(t);
        if(act.matches())return actionTitle(act.group(1),orEmpty(act.group(2)));
        return "";
    }

    private static String actionTitle(String verb,String obj) {
        String v=verb.toLowerCase(Locale.ROOT),o=obj.trim();
        switch(v) {
            case "comparar":return "Comparación "+nominal(o);
            case "revisar":return "Revisión "+nominal(o);
            case "renovar":return "Renovación "+nominal(o);
            case "recoger":return "Recogida "+nominal(o);
            case "reparar":case "arreglar":return "Reparación "+nominal(o);
            case "desparasitar":return "Desparasitación "+nominal(o);
            case "curarme":return "Cura "+nominal(o);
            case "comprar":return "Compra "+nominal(o);
            case "entregar":return "Entrega "+nominal(o);
            case "reservar":return "Reserva "+nominal(o);
            case "inscribirme":case "apuntarme":return "Inscripción en "+o.replaceFirst("(?iu)^(?:a|en)\\s+","");
            case "reunirme":return "Reunión "+o;
            case "organizar":return "Organización "+nominal(o);
            case "montar":case "ensamblar":return "Montaje "+nominal(o);
            case "comprobar":return "Comprobación "+nominal(o);
            case "donar":return "Donación "+nominal(o);
            case "pagar":case "abonar":return "Pago "+nominal(o);
            case "preparar":return "Preparación "+nominal(o);
            case "instalar":return "Instalación "+nominal(o);
            case "copiar":return "Copia "+nominal(o);
            default:return cap(v)+(o.isBlank()?"":" "+o);
        }
    }
    private static String nominal(String s){
        String v=s.trim();
        if(v.matches("(?iu)^el\\s+.+"))return "del "+v.substring(3);
        if(v.matches("(?iu)^la\\s+.+"))return "de la "+v.substring(3);
        if(v.matches("(?iu)^los\\s+.+"))return "de los "+v.substring(4);
        if(v.matches("(?iu)^las\\s+.+"))return "de las "+v.substring(4);
        if(v.matches("(?iu)^un\\s+.+"))return "de un "+v.substring(3);
        if(v.matches("(?iu)^una\\s+.+"))return "de una "+v.substring(4);
        if(v.matches("(?iu)^al\\s+.+"))return "del "+v.substring(3);
        if(v.matches("(?iu)^a\\s+la\\s+.+"))return "de la "+v.substring(5);
        if(v.matches("(?iu)^a\\s+mi\\s+.+"))return "de mi "+v.substring(5);
        if(v.matches("(?iu)^a\\s+los\\s+.+"))return "de los "+v.substring(6);
        if(v.matches("(?iu)^a\\s+las\\s+.+"))return "de las "+v.substring(6);
        return "de "+v;
    }
    private static boolean alreadyContainsAction(String old,String action) {
        if(action.isBlank())return true;
        String a=action.toLowerCase(Locale.ROOT),n=unaccent(old.toLowerCase(Locale.ROOT));
        if(a.equals("inscribirme")||a.equals("apuntarme"))return n.contains("inscrip")||n.contains("apunt");
        if(a.equals("curarme"))return n.contains("cura")||n.contains("herida");
        if(a.equals("recoger"))return n.contains("recog")||n.contains("buscar");
        if(a.equals("buscar") && n.contains("recogida"))return true;
        if(a.equals("cambiar") && (n.contains("cambio")||n.contains("cambia")))return true;
        if(a.equals("donar") && (n.contains("donacion")||n.contains("donar")))return true;
        if(a.equals("reunirme"))return n.contains("reunion")||n.contains("reunir");
        if(a.equals("hacer"))return true;
        String stem=unaccent(a);stem=stem.substring(0,Math.min(6,stem.length()));
        return n.contains(stem);
    }
    private static boolean lostObject(String old,String candidate) {
        // No sustituir por una paráfrasis léxica: exigir evidencia de omisión clara.
        // El candidato recupera al menos dos palabras sustantivas ausentes en el título previo.
        Set<String> oldWords=new HashSet<>(Arrays.asList(unaccent(old.toLowerCase(Locale.ROOT)).split("[^a-z0-9]+")));
        int missing=0;
        for(String word:unaccent(candidate.toLowerCase(Locale.ROOT)).split("[^a-z0-9]+")) {
            if(word.length()>=5&&!oldWords.contains(word))missing++;
        }
        return missing>=2 && old.length()<candidate.length();
    }
    private static String unaccent(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");}
    private static String stripTrailingLocation(String core,String loc){
        // Solo la ubicación estructurada ya extraída por el parser, situada al final.
        String location=loc.trim();
        String pattern="(?iu)\\s+en\\s+"+Pattern.quote(location)+"\\s*$";
        String stripped=core.replaceFirst(pattern,"").trim();
        return stripped.isBlank()?core:stripped;
    }
    private static String metadata(String raw) {
        String s=raw==null?"":raw.replace('\n',' ').replaceAll("\\s+"," ").trim();
        s=META.matcher(s).replaceFirst("");
        // Ubicación separada tras una hora explícita: es metadato, no parte de la acción.
        s=s.replaceAll("(?iu)(\\d{1,2}:\\d{2})\\s+en\\s+[^,;.]+(?=\\s*(?:[;, .]|$))", "$1");
        s=s.replaceFirst("(?iu)[;,.]\\s*(?:av[ií]same|avisarme|recu[eé]rdame|avisa|recuerda)\\b.*$","");
        s=WORD_DATE.matcher(s).replaceAll(" ");
        s=NUM_DATE.matcher(s).replaceAll(" ");
        s=TIME.matcher(s).replaceAll(" ");
        s=s.replaceAll("(?iu)\\b(?:hoy|pasado\\s+ma[nñ]ana|ma[nñ]ana|esta\\s+(?:ma[nñ]ana|tarde|noche))\\b"," ");
        s=s.replaceAll("(?iu)\\b(?:el\\s+)?(?:este|pr[oó]ximo|siguiente)?\\s*(?:"+DAYS+")\\b"," ");
        // La hora de día «por la noche» NO se elimina aquí: puede ser parte del propósito.
        s=s.replaceAll("(?iu)\\b(?:por\\s+la\\s+tarde|por\\s+la\\s+ma[nñ]ana)\\b", " ");
        s=s.replaceAll("(?iu)\\s+en\\s+[^;,]+(?=\\s*[;,]\\s*av[ií]same\\b)"," ");
        return clean(s).replaceFirst("(?iu)\\s+el$","");
    }
    private static String finish(String s){
        String x=clean(s).replaceAll("^[,;:.!?\\s-]+|[,;:.!?\\s-]+$","");
        x=x.replaceAll("(?iu)\\bde\\s+el\\b","del").replaceAll("(?iu)\\bde\\s+al\\b","del");
        if(x.length()>92){int k=x.lastIndexOf(' ',92);if(k>40)x=x.substring(0,k);}
        if(x.split("\\s+").length>17)x=String.join(" ",Arrays.copyOf(x.split("\\s+"),17));
        return cap(x);
    }
    private static boolean quality(String s){return !s.isBlank()&&s.length()<=92&&s.split("\\s+").length>=2 &&
        !s.matches("(?iu).*(?:^|\\s)(?:a|de|del|el|la|para|por|con|sobre|en|que|y)\\s*$") &&
        !s.matches("(?iu)^(?:recu[eé]rdame|recuerda|anota)\\b.*") &&
        !s.matches("(?iu).*\\bde\\s+(?:al|el)\\b.*");}
    private static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
    private static String cap(String s){return s==null||s.isBlank()?"":s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
    private static String orEmpty(String s){return s==null?"":s;}
    private static Selection selected(String t,String r,String a,String o,String p,int c){return new Selection(t,r,a,o,p,c);}
}
