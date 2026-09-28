package com.jruiz.nexo.core;

import java.util.*;

public final class QueryInterpreter {
    private QueryInterpreter() {}

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "que","cuando","donde","como","cual","cuales","tengo","tenia","fue","la","el","los","las","un","una","de","del","en","a","por","para","me","mi","mis","se","y","o","esta","este","estos","estas","enseñame","busca","buscar","relacionado","relacionada","cosa","cosas","guarde","guardado","guardada","guardados","guardadas","mostrar","muestra","gustaron","gusto","estuvimos","estuve","quiero","queria"
    ));

    public static String[] importantTerms(String q) {
        String n = RuleClassifier.norm(q).replaceAll("[^a-z0-9áéíóúñ ]", " ");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : n.split("\\s+")) {
            if (t.length() >= 3 && !STOP.contains(t)) out.add(t);
        }
        return out.toArray(new String[0]);
    }
}
