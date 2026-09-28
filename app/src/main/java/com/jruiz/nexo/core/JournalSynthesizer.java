package com.jruiz.nexo.core;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Síntesis local para Cuaderno: título, resumen y temas sin red ni IA. */
public final class JournalSynthesizer {
    private JournalSynthesizer() {}

    public static final class Result {
        public String kind="thought";
        public String title="Pensamiento";
        public String summary="";
        public String tags="";
        public String body="";
    }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "el","la","los","las","un","una","unos","unas","de","del","al","a","en","por","para","que","y","e","o","con","sin","me","mi","mis","yo","tu","su","sus","es","son","ser","estar","esta","este","esto","esa","ese","se","lo","le","ya","muy","mas","más","cada","vez","tambien","también","pero","si","sí","no"
    ));

    public static Result synthesize(String raw) {
        Result r=new Result();
        String body=clean(raw); r.body=body;
        if(body.isBlank()) return r;
        String norm=norm(body);
        r.kind = looksLikeQuote(body,norm) ? "quote" : "thought";
        String core = "quote".equals(r.kind) ? extractQuote(body) : body;
        r.title = deriveTitle(core, r.kind);
        r.summary = deriveSummary(body, core, r.kind);
        r.tags = deriveTags(body);
        return r;
    }

    private static boolean looksLikeQuote(String body,String n){
        return n.contains("he leido esta frase")||n.contains("he leido una frase")||n.startsWith("frase ")||n.startsWith("cita ")||body.contains("\"")||body.contains("“")||body.contains("”");
    }

    private static String extractQuote(String body){
        Matcher q=Pattern.compile("[“\"]([^”\"]{8,400})[”\"]").matcher(body); if(q.find())return q.group(1).trim();
        Matcher m=Pattern.compile("(?iu)(?:frase|cita)(?:\\s+que)?(?:\\s+dice)?\\s*[:,-]?\\s*(.+)$").matcher(body);if(m.find())return m.group(1).trim();
        Matcher d=Pattern.compile("(?iu)\\bdice\\s+(?:que\\s+)?(.+)$").matcher(body);if(d.find())return d.group(1).trim();
        return body;
    }

    private static String deriveTitle(String core,String kind){
        String n=norm(core);
        if(n.contains("no puedes anadir dias a tu vida") && n.contains("vida a tus dias")) return "Vida a tus días";
        if(n.contains("tiempo") && (n.contains("llenar cada dia")||n.contains("no hacer nada")||n.contains("aprovechar el tiempo"))) return "Disfrutar del tiempo sin llenarlo";
        String best=bestSentence(core);
        best=best.replaceAll("(?iu)^\\s*(?:cada\\s+vez\\s+tengo\\s+m[aá]s\\s+claro\\s+que|me\\s+doy\\s+cuenta\\s+de\\s+que|creo\\s+que|pienso\\s+que|siento\\s+que|he\\s+le[ií]do\\s+(?:esta|una)\\s+frase(?:\\s+que\\s+dice)?|la\\s+idea\\s+es\\s+que)\\s+","");
        best=best.replaceAll("^[“\"']|[”\"']$","").trim();
        String[] words=best.split("\\s+");
        if(words.length>7)best=String.join(" ",Arrays.copyOf(words,7));
        if(best.length()>48){best=best.substring(0,48).trim();int p=best.lastIndexOf(' ');if(p>22)best=best.substring(0,p);}
        if(best.isBlank()) return "quote".equals(kind)?"Cita guardada":"Pensamiento";
        return capitalize(best.replaceAll("[,:;.]+$", ""));
    }

    private static String deriveSummary(String body,String core,String kind){
        if("quote".equals(kind)) return "";
        String x=bestSentence(body);
        x=x.replaceAll("(?iu)^\s*(?:cada\s+vez\s+tengo\s+m[aá]s\s+claro\s+que|me\s+doy\s+cuenta\s+de\s+que|creo\s+que|pienso\s+que|siento\s+que)\s+","").trim();
        if(x.length()>180){x=x.substring(0,177).trim()+"…";}
        return capitalize(x);
    }

    private static String deriveTags(String body){
        String n=norm(body);LinkedHashSet<String> t=new LinkedHashSet<>();
        add(t,n,"familia","Familia","hijos","padres","madre","padre","pareja","esposa","marido","nietos");
        add(t,n,"tiempo","Tiempo","tiempo","dias","día","vida cotidiana");
        add(t,n,"vida","Vida","vida","vivir","disfrutar");
        add(t,n,"viajes","Viajes","viaje","viajar","vacaciones","hotel","vuelo");
        add(t,n,"amistad","Amistad","amigo","amiga","amistad");
        add(t,n,"aprendizaje","Aprendizaje","aprender","aprendizaje","leer","libro");
        add(t,n,"salud","Salud","salud","medico","enfermedad","bienestar");
        add(t,n,"gratitud","Gratitud","agradecer","gratitud","afortunado","afortunada");
        add(t,n,"cambio","Cambio","cambio","cambiar","etapa","nuevo comienzo");
        if(t.isEmpty()){
            for(String w:n.split("\\s+")){if(w.length()>=6&&!STOP.contains(w)){t.add(capitalize(w));if(t.size()>=3)break;}}
        }
        return String.join(" · ",t);
    }
    private static void add(Set<String> out,String n,String key,String label,String... needles){for(String x:needles)if(n.contains(norm(x))){out.add(label);return;}}

    private static String bestSentence(String text){
        String[] s=text.split("(?<=[.!?])\\s+|[;]");String best=clean(text);int score=-1;
        for(String x:s){x=clean(x);int sc=0;for(String w:norm(x).split("\\s+"))if(w.length()>=4&&!STOP.contains(w))sc++;if(sc>score){score=sc;best=x;}}
        return best;
    }
    private static String norm(String s){String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('ñ','n');return n.replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();}
    private static String clean(String s){return s==null?"":s.replace('\n',' ').replaceAll("\\s+"," ").trim();}
    private static String capitalize(String s){if(s==null||s.isBlank())return "";return Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
