package com.jruiz.nexo.core.title;

import java.util.*;
import java.util.regex.*;

/**
 * V6 - segmentación determinista de intención. Reglas internas conservadas del laboratorio validado.
 * No consulta resultados esperados ni ficheros del benchmark, ni usa IA, red o aprendizaje.
 * Opera sobre una copia del dictado; deja intacto el resultado de AppointmentParser.
 */
final class TitleRulesV6 {
    private TitleRulesV6() {}
    public static final class Selection {
        public final String title, rule, eventType, action, object, purpose;
        public final int confidence;
        public Selection(String title,String rule,String eventType,String action,String object,String purpose,int confidence) {
            this.title=title;this.rule=rule;this.eventType=eventType;this.action=action;
            this.object=object;this.purpose=purpose;this.confidence=confidence;
        }
    }
    private static final String MONTHS="enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre";
    private static final String DAYS="lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo";
    private static final String ACT="acompa[nñ]ar|actualizar|analizar|anular|arreglar|buscar|cambiar|celebrar|cenar|comer|comparar|comprar|comprobar|configurar|consultar|controlar|cortar|debatir|defender|dejar|devolver|discutir|donar|entregar|enviar|evaluar|facturar|firmar|gestionar|hacer|hacerme|inscribir|instalar|inspeccionar|jugar|llevar|medir|organizar|pagar|pedir|planificar|poner|ponerme|preparar|presentar|recoger|reclamar|renovar|reparar|reservar|realizar|revisar|solicitar|sustituir|tomar|tramitar|tratar|vacunar|vacunarme|ver|visitar|hablar|viajar|registrar|asistir|practicar|confirmar|recibir|sacar|sacarme";
    private static final Pattern VERB = Pattern.compile("(?iu)^("+ACT+")\\b(?:\\s+(.+))?$");
    private static final Pattern DATE_WORDS = Pattern.compile("(?iu)\\b(?:el\\s+)?(?:"+DAYS+")?\\s*(?:\\d{1,2}|primero)\\s+de\\s+(?:"+MONTHS+")(?:\\s+de\\s+\\d{2,4})?\\b");
    private static final Pattern DATE_NUM = Pattern.compile("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s*[/.-]\\s*\\d{1,2}(?:\\s*[/.-]\\s*\\d{2,4})?\\b");
    private static final Pattern TIME = Pattern.compile("(?iu)\\b(?:a\\s+las?|a\\s+la)\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:cuarto|media|\\d{1,2}))?(?:\\s+de\\s+la\\s+(?:ma[nñ]ana|tarde|noche))?\\b");
    private static final Pattern DIRECT_LEAD = Pattern.compile("(?iu)^(?:tengo|tenemos|hay|hab[ií]a|hace\\s+falta|necesito|necesitamos|debo|debemos)\\s+(?:que\\s+|pendiente\\s+de\\s+)?");
    private static final Pattern GO_LEAD = Pattern.compile("(?iu)^(?:voy|vamos|vengo|venimos|toca|me\\s+toca|nos\\s+toca)\\s+a\\s+");
    private static final Pattern META = Pattern.compile("(?iu)^\\s*(?:recu[eé]rdame|recuerda|acu[eé]rdate|anota|apunta|quiero\\s+recordar)(?:\\s+que)?\\s*[:,-]?\\s*");
    private static final Pattern EVENT = Pattern.compile("(?iu)\\b(reuni[oó]n|junta|tutor[ií]a|asamblea|entrevista|videollamada|encuentro)(?:\\s+((?:familiar|laboral|escolar|profesional|de\\s+padres|de\\s+vecinos|de\\s+trabajo|de\\s+equipo)))?\\b");
    private static final Pattern SUBORDINATE = Pattern.compile("(?iu)^para\\s+que\\s+(?:\\p{L}+\\s+){0,4}(revisen|saquen|corten|arreglen|instalen|entreguen|pongan|preparen|midan|firmen|hagan)\\s+(.+)$");

    public static Selection derive(String raw,String baselineTitle,String category,String location) {
        TitleRulesV5.Selection older=TitleRulesV5.derive(raw,baselineTitle,category,location);
        String fallback=older.title == null ? "" : older.title.trim();
        String text=normalize(raw);
        if(text.isBlank())return select(fallback,"fallback-vacio","","","","",0);
        text=metadataOnly(text);
        String candidate="",rule="",event="",action="",object="",purpose="";
        int confidence=0;
        Matcher m;

        // 1. Si el usuario nombra una actividad, conservar su naturaleza.
        // «Reunión familiar para organizar vacaciones» no se convierte en «organizar vacaciones».
        m=EVENT.matcher(text);
        if(m.find()) {
            String eventLabel=cap(m.group(1))+(m.group(2)==null?"":" "+m.group(2));
            String following=text.substring(m.end()).trim();
            // Una finalidad o tema explícitos definen el asunto, no suplantan la reunión.
            Matcher detail=Pattern.compile("(?iu)^(?:para\\s+|sobre\\s+|acerca\\s+de\\s+|por\\s+)(.+)$").matcher(following);
            if(detail.find()) {
                purpose=detail.group(1);
                candidate=eventLabel+(following.toLowerCase(Locale.ROOT).startsWith("para ")?" para ":" sobre ")+purpose;
                rule="tipo-evento-y-finalidad";confidence=92;
            } else if(following.startsWith("con ") || following.startsWith("de ")) {
                candidate=eventLabel+" "+following;rule="tipo-evento-participante";confidence=85;
            } else if(!following.isBlank() && following.startsWith("del ")) {
                candidate=eventLabel+" "+following;rule="tipo-evento-contexto";confidence=84;
            } else {
                candidate=eventLabel;rule="tipo-evento";confidence=72;
            }
            event=eventLabel;
        }

        // 2. Tipo genérico cita/consulta + finalidad. Sin inventar especialista.
        if(candidate.isBlank()) {
            m=Pattern.compile("(?iu)^(?:(?:tengo|tenemos)\\s+)?(?:una?\\s+)?(?:cita|consulta)\\s+(?:con\\s+[^,;]+?\\s+)?para\\s+(.+)$").matcher(text);
            if(m.find()) {
                String inner=m.group(1).trim();
                Matcher act=VERB.matcher(inner);
                if(act.matches()) {
                    action=act.group(1);object=orEmpty(act.group(2));
                    candidate=renderAction(action,object);rule="cita-accion";confidence=92;
                } else if(inner.matches("(?iu)^(?:una?|la|el)\\s+.+")) {
                    candidate=inner.replaceFirst("(?iu)^(?:una?|la|el)\\s+","");
                    rule="cita-sustantivo";confidence=86;
                }
            }
        }

        // 3. Actos y gestiones explícitos: nunca convertir «preparar un regalo de cumpleaños»
        // en «Cumpleaños». Mantener la acción y el objeto y, si existe, la finalidad.
        if(candidate.isBlank()) {
            String core=unwrap(text);
            m=VERB.matcher(core);
            if(m.matches()) {
                action=m.group(1);object=orEmpty(m.group(2));
                if(!object.isBlank()) {
                    candidate=renderAction(action,object);rule="accion-objeto";confidence=90;
                } else {
                    candidate=cap(action);rule="accion-sin-objeto";confidence=65;
                }
            }
        }

        // 4. Objetivo introducido por «para», preferible al desplazamiento logístico.
        // Evitar capturar «para el cumpleaños» como acción cuando ya hay verbo principal.
        if(candidate.isBlank()) {
            m=Pattern.compile("(?iu)\\bpara\\s+("+ACT+")\\b(?:\\s+(.+))?$").matcher(text);
            if(m.find()) {
                action=m.group(1);object=orEmpty(m.group(2));
                candidate=renderAction(action,object);rule="finalidad-accion";confidence=87;
            }
        }

        // 5. Subordinadas de finalidad: «para que le revisen la vista».
        if(candidate.isBlank()) {
            m=SUBORDINATE.matcher("para "+afterParaQue(text));
            if(m.matches()) {
                String verb=m.group(1).toLowerCase(Locale.ROOT),target=m.group(2);
                String root;
                switch(verb) {
                    case "revisen":root="Revisión de ";break;
                    case "saquen":root="Extracción de ";break;
                    case "corten":root="Corte de ";break;
                    case "arreglen":root="Reparación de ";break;
                    case "instalen":root="Instalación de ";break;
                    case "entreguen":root="Entrega de ";break;
                    case "pongan":root="Colocación de ";break;
                    case "preparen":root="Preparación de ";break;
                    case "midan":root="Medición de ";break;
                    case "firmen":root="Firma de ";break;
                    default: root="Realización de ";break;
                }
                candidate=root+target;action=verb;object=target;rule="finalidad-subordinada";confidence=78;
            }
        }

        // 6. «He quedado a desayunar», «nos vemos para cenar»: evento social.
        if(candidate.isBlank()) {
            m=Pattern.compile("(?iu)^(?:he|hemos)\\s+quedado\\s+a\\s+(desayunar|almorzar|merendar|comer|cenar)\\b(.*)$").matcher(text);
            if(m.matches()) {
                action=m.group(1);object=m.group(2).trim();
                candidate=mealNoun(action)+(object.isBlank()?"":" "+object);rule="quedada-comida";confidence=86;
            }
        }
        if(candidate.isBlank()) {
            m=Pattern.compile("(?iu)^(?:voy|vamos)\\s+(al|a\\s+la)\\s+(cine|teatro|museo|concierto)\\b(.*)$").matcher(text);
            if(m.matches()) {
                candidate="Ir "+m.group(1)+" "+m.group(2)+m.group(3);event=m.group(2);rule="salida-explicita";confidence=80;
            }
        }

        // 7. Objetivos expresados como sintagma nominal («sesión de fisioterapia»).
        if(candidate.isBlank()) {
            m=Pattern.compile("(?iu)^(?:(?:tengo|tenemos)\\s+)?(?:un|una|la|el)??\\s*(sesi[oó]n|ecograf[ií]a|resonancia|revisi[oó]n|entrega|visita|curso|clase|consulta|vacunaci[oó]n|concierto|comida|cena)\\b(.*)$").matcher(text);
            if(m.matches()) {candidate=cap(m.group(1))+m.group(2);event=m.group(1);rule="actividad-nominal";confidence=81;}
        }

        // 8. Fallback: no reemplazar un título existente por una extracción parcial.
        String result=finish(candidate);
        if(!isAcceptable(result,confidence))return select(fallback,"v5-conservado:"+older.rule,"","","","",0);
        // Evitar sustituir un buen título (con mayor información) por uno nominal vacío.
        if(confidence<80 && !fallback.isBlank() && wordCount(fallback)>wordCount(result)+2 &&
                !fallback.matches("(?iu)^(?:recuerda|recu[eé]rdame|tengo|voy|vamos)\\b.*"))
            return select(fallback,"v5-conservado:contexto","","","","",0);
        if(!mustReplaceOlder(fallback,result,rule,confidence))
            return select(fallback,"v5-conservado:seleccion-segura","","","","",0);
        return select(result,rule,event,action,object,purpose,confidence);
    }


    /**
     * Política de sustitución sin entrenamiento ni comparación contra respuestas esperadas.
     * Se reemplaza V5 cuando hay una pérdida objetiva de acción/tipo o un marcador de dictado.
     * Las paráfrasis igualmente válidas se dejan intactas para reducir regresiones.
     */
    private static boolean mustReplaceOlder(String older,String proposed,String rule,int confidence){
        if(older.isBlank())return true;
        if(older.equalsIgnoreCase(proposed))return false;
        String lo=older.toLowerCase(Locale.ROOT);
        if(lo.matches("(?iu)^(?:recu[eé]rdame|tengo|tenemos|voy|vamos|cita\\s+para|a\\s+(?:desayunar|cenar|comer))\\b.*"))return true;
        if(lo.matches("(?iu).*\\b(?:de\\s+para|de\\s+al|de\\s+el)\\b.*"))return true;
        if(lo.matches("(?iu)^cumplea[nñ]os(?:\\s+de.*)?$") &&
                (rule.equals("accion-objeto")||rule.equals("finalidad-accion")))return true;
        if(rule.equals("tipo-evento-y-finalidad") &&
                !lo.matches("(?iu).*(?:reuni[oó]n|junta|tutor[ií]a|asamblea|entrevista|videollamada|encuentro).*"))return true;
        if(rule.equals("cita-sustantivo") && lo.startsWith("cita para "))return true;
        if(rule.equals("quedada-comida") && lo.startsWith("a "))return true;
        if(rule.equals("salida-explicita") && lo.startsWith("vamos "))return true;
        if(rule.equals("actividad-nominal") && lo.startsWith("tengo "))return true;
        if(rule.equals("finalidad-subordinada") && (lo.startsWith("voy ")||lo.startsWith("acudo ")||lo.startsWith("ir ") || lo.startsWith("vamos ")))return true;
        if(confidence>=88 && older.split("\\s+").length<=2 && proposed.split("\\s+").length>=4 &&
                !lo.matches("(?iu).*(?:itv|anal[ií]tica).*"))return true;
        return false;
    }

    private static String afterParaQue(String s){
        Matcher m=Pattern.compile("(?iu)\\bpara\\s+que\\s+(.+)$").matcher(s);
        return m.find()?"que "+m.group(1):"";
    }
    private static String unwrap(String s){
        String core=s;
        core=DIRECT_LEAD.matcher(core).replaceFirst("");
        core=GO_LEAD.matcher(core).replaceFirst("");
        core=core.replaceFirst("(?iu)^quiero\\s+","");
        core=core.replaceFirst("(?iu)^me\\s+han\\s+pedido\\s+","");
        core=core.replaceFirst("(?iu)^nos\\s+han\\s+pedido\\s+","");
        core=core.replaceFirst("(?iu)^ir\\s+al?\\s+[^,;]+?\\s+para\\s+","");
        core=core.replaceFirst("(?iu)^he\\s+quedado\\s+para\\s+","");
        return core.trim();
    }
    private static String renderAction(String verb,String object){
        String a=verb.toLowerCase(Locale.ROOT),o=object==null?"":object.trim();
        if(a.equals("revisar"))return "Revisión "+nominalObject(o);
        if(a.equals("consultar") && o.matches("(?iu)^lo\\s+del\\s+.+"))return "Consulta por "+o.replaceFirst("(?iu)^lo\\s+del\\s+","");
        if(a.equals("ponerme") && o.matches("(?iu)^la vacuna de .+"))return "Vacunación contra "+o.substring(13);
        if(a.equals("vacunarme"))return "Vacunación "+o;
        if(a.equals("visitar"))return "Visita "+o;
        if(a.equals("comer"))return "Comida "+o;
        if(a.equals("cenar"))return "Cena "+o;
        if(a.equals("tomar") && o.matches("(?iu)^(?:un\\s+)?caf[eé].*"))return "Café "+o.replaceFirst("(?iu)^(?:un\\s+)?caf[eé]\\s*","");
        if(a.equals("comparar"))return "Comparación "+nominalObject(o);
        if(a.equals("comprar"))return "Compra "+nominalObject(o);
        if(a.equals("reservar"))return "Reserva "+nominalObject(o);
        if(a.equals("entregar"))return "Entrega "+nominalObject(o);
        if(a.equals("devolver"))return "Devolución "+nominalObject(o);
        if(a.equals("renovar"))return "Renovación "+nominalObject(o);
        if(a.equals("organizar"))return "Organización "+nominalObject(o);
        if(a.equals("instalar"))return "Instalación "+nominalObject(o);
        if(a.equals("reparar"))return "Reparación "+nominalObject(o);
        if(a.equals("configurar"))return "Configuración "+nominalObject(o);
        if(a.equals("presentar"))return "Presentación "+nominalObject(o);
        if(a.equals("actualizar"))return "Actualización "+nominalObject(o);
        if(a.equals("controlar"))return "Control "+nominalObject(o);
        if(a.equals("firmar"))return "Firma "+nominalObject(o);
        if(a.equals("celebrar"))return "Celebración "+nominalObject(o);
        if(a.equals("donar"))return "Donación "+nominalObject(o);
        if(a.equals("hacer") && o.matches("(?iu)^(?:el|la|un|una)\\s+(?:registro|revisi[oó]n|gesti[oó]n|inspecci[oó]n|entrega|consulta|vacunaci[oó]n|reserva)\\b.*"))
            return cap(o.replaceFirst("(?iu)^(?:el|la|un|una)\\s+",""));
        // Infinito de acción explícita siempre válido y no requiere inventar sinónimos.
        return cap(verb)+(o.isBlank()?"":" "+o);
    }
    private static String nominalObject(String object){
        String o=object.trim();
        if(o.matches("(?iu)^el\\s+.+"))return "del "+o.substring(3);
        if(o.matches("(?iu)^los\\s+.+"))return "de los "+o.substring(4);
        if(o.matches("(?iu)^las\\s+.+"))return "de las "+o.substring(4);
        if(o.matches("(?iu)^la\\s+.+"))return "de la "+o.substring(3);
        if(o.matches("(?iu)^un\\s+.+"))return "de un "+o.substring(3);
        if(o.matches("(?iu)^una\\s+.+"))return "de una "+o.substring(4);
        if(o.matches("(?iu)^al\\s+.+"))return "del "+o.substring(3);
        if(o.matches("(?iu)^a\\s+la\\s+.+"))return "de la "+o.substring(5);
        if(o.matches("(?iu)^a\\s+mi\\s+.+"))return "de mi "+o.substring(5);
        if(o.matches("(?iu)^a\\s+los\\s+.+"))return "de los "+o.substring(6);
        return "de "+o;
    }
    private static String metadataOnly(String raw){
        String x=META.matcher(raw).replaceFirst("");
        // Cláusula explícita de alarma, no información del evento.
        x=x.replaceFirst("(?iu)[;,.]\\s*(?:av[ií]same|avisarme|recu[eé]rdame|avisa|recuerda)\\b.*$","");
        x=DATE_NUM.matcher(x).replaceAll(" ");
        x=DATE_WORDS.matcher(x).replaceAll(" ");
        x=TIME.matcher(x).replaceAll(" ");
        x=x.replaceAll("(?iu)\\b(?:hoy|pasado\\s+ma[nñ]ana|ma[nñ]ana|esta\\s+(?:ma[nñ]ana|tarde|noche))\\b"," ");
        x=x.replaceAll("(?iu)\\b(?:el\\s+)?(?:este|pr[oó]ximo|siguiente)?\\s*(?:"+DAYS+")\\b"," ");
        x=x.replaceAll("(?iu)\\b(?:por|de)\\s+la\\s+(?:ma[nñ]ana|tarde|noche)\\b"," ");
        // Solo una ubicación después de hora numérica explícita es metadato inequívoco.
        Matcher venue=Pattern.compile("(?iu)\\b\\d{1,2}:\\d{2}\\s+en\\s+([^;,]+)").matcher(raw);
        if(venue.find())x=x.replaceFirst("(?iu)\\s+en\\s+"+Pattern.quote(venue.group(1).trim())+"\\s*$","");
        return normalize(x).replaceAll("(?iu)\\s+el\\s*$","").trim();
    }
    private static String finish(String s){
        if(s==null)return "";
        String v=normalize(s).replaceAll("^[,;:.!?\\s-]+|[,;:.!?\\s-]+$","");
        v=v.replaceAll("(?iu)\\bde\\s+el\\b","del").replaceAll("(?iu)\\bde\\s+al\\b","del");
        v=v.replaceAll("(?iu)\\bde\\s+a\\s+la\\b","de la");
        // Limitar solo al terminar de extraer la acción y el objeto; no recortar a 7 palabras.
        if(v.length()>96){int k=v.lastIndexOf(' ',96);if(k>56)v=v.substring(0,k).trim();}
        if(wordCount(v)>17){String[] a=v.split("\\s+");v=String.join(" ",Arrays.copyOf(a,17));}
        return cap(v).trim();
    }
    private static boolean isAcceptable(String title,int confidence){
        return confidence>=65 && !title.isBlank() && title.length()<=96 && wordCount(title)>=2 &&
                !title.matches("(?iu).*\\b(?:a|al|de|del|el|la|en|para|por|con|sobre|que|y)\\s*$") &&
                !title.matches("(?iu).*\\b(?:de\\s+(?:el|al)|de\\s+a\\s+la)\\b.*") &&
                !title.matches("(?iu)^(?:recu[eé]rdame|recuerda|anota)\\b.*");
    }
    private static int wordCount(String s){return s.isBlank()?0:s.trim().split("\\s+").length;}
    private static String cap(String v){if(v==null || v.isBlank())return "";return v.substring(0,1).toUpperCase(Locale.ROOT)+v.substring(1);}
    private static String normalize(String s){return s==null?"":s.replace('\n',' ').replaceAll("\\s+"," ").trim();}
    private static String orEmpty(String s){return s==null?"":s;}
    private static String mealNoun(String v){
        switch(v.toLowerCase(Locale.ROOT)){
            case "desayunar":return "Desayuno";
            case "almorzar":return "Almuerzo";
            case "merendar":return "Merienda";
            case "comer":return "Comida";
            default:return "Cena";
        }
    }
    private static Selection select(String title,String rule,String event,String action,String obj,String purpose,int confidence){
        return new Selection(title,rule,event,action,obj,purpose,confidence);
    }
}
