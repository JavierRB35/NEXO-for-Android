package com.jruiz.nexo.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generador local y determinista de títulos breves para el Bloc de notas. */
public final class NoteTitleEngine {
    private NoteTitleEngine() {}

    private static final String ACTION_WORDS = "enviar|mandar|llevar|recoger|comprar|pedir|solicitar|renovar|llamar|revisar|mirar|comprobar|reservar|presentar|entregar|guardar|buscar|escanear";

    private static final Pattern TALKED_WITH = Pattern.compile(
            "(?iu)\\b(?:he|hemos|hab[ií]a|hab[ií]amos)\\s+hablado\\s+con\\s+([^,.;]{2,45}?)(?:\\s+y\\s+(?:me|nos)\\s+(?:dicen|dijeron|han\\s+dicho)|\\s+(?:sobre|acerca\\s+de|por|para)\\s+)([^.;]{3,180})");
    private static final Pattern TOLD_THAT = Pattern.compile(
            "(?iu)\\b(?:me|nos)\\s+(?:han\\s+dicho|ha\\s+dicho|dijeron|indicaron|indic[oó]|explicaron|explic[oó]|comentaron|coment[oó])\\s+que\\s+([^.;]{3,180})");
    private static final Pattern TOLD_BY = Pattern.compile(
            "(?iu)\\b(?:me|nos)\\s+(?:ha\\s+dicho|dijo|indic[oó]|explic[oó]|coment[oó])\\s+(?:el|la)?\\s*[^,.;]{2,45}?\\s+que\\s+([^.;]{3,180})");
    private static final Pattern IDEAS_FOR = Pattern.compile(
            "(?iu)\\b(ideas?|cosas)\\s+(?:para|sobre|de)\\s+([^:.;]{2,90})");
    private static final Pattern MEASURES_OF = Pattern.compile(
            "(?iu)\\b(medidas?|dimensiones?)\\s+(del|de\\s+la|de\\s+los|de\\s+las|de)\\s+([^:.;]{2,90})");
    private static final Pattern DATA_OF = Pattern.compile(
            "(?iu)\\b(tel[eé]fono|m[oó]vil|direcci[oó]n|contrase[nñ]a|clave|c[oó]digo|referencia|matr[ií]cula|horario|correo|email|iban|cuenta|nombre)\\s+(del|de\\s+la|de\\s+los|de\\s+las|de)\\s+([^:.;,]{2,70}?)(?=\\s+(?:es|son)\\b|$|[:.;,])");
    private static final Pattern SUBJECT_LOCATION = Pattern.compile(
            "(?iu)^(?:el|la|los|las)?\\s*([^,.;]{2,80}?)\\s+(?:est[aá]|se\\s+encuentra|queda)\\s+(?:en|detr[aá]s|debajo|encima|junto|al\\s+lado)\\b");
    private static final Pattern SUBJECT_NOISE = Pattern.compile(
            "(?iu)^(?:el|la)\\s+([^,.;]{2,55}?)\\s+(?:hace|tiene|produce)\\s+(?:un\\s+)?ruido\\b");
    private static final Pattern PURPOSE = Pattern.compile(
            "(?iu)\\bpara\\s+((?:" + ACTION_WORDS + ")\\b[^.;]{0,120})");
    private static final Pattern ACTION = Pattern.compile(
            "(?iu)\\b(?:(?:tengo|tenemos|hay)\\s+que|(?:debo|debemos|necesito|necesitamos)(?:\\s+que)?)\\s+((?:" + ACTION_WORDS + ")\\b[^.;]{0,120})");
    private static final Pattern TRIP = Pattern.compile(
            "(?iu)\\b(?:viaje|escapada|vacaciones)\\s+(?:a|para|en)\\s+([^,.;:]{2,55})");

    private static final Pattern LIST_FOR = Pattern.compile(
            "(?iu)\\blista\\s+de\\s+([^.;]{2,60}?)\\s+que\\s+(?:hace|hacen)\\s+falta\\s+para\\s+([^.;]{2,60})");

    public static String derive(String raw) {
        String text = clean(raw);
        if (text.isBlank()) return "";

        Matcher m = TALKED_WITH.matcher(text);
        if (m.find()) {
            String person = capitalize(compactEntity(m.group(1)));
            String subject = titleFromClause(m.group(2));
            if (!subject.isBlank()) return limit(subject + (person.isBlank() ? "" : " · " + person));
        }

        m = TOLD_THAT.matcher(text);
        if (m.find()) {
            String title = titleFromClause(m.group(1));
            if (!title.isBlank()) return limit(title);
        }
        m = TOLD_BY.matcher(text);
        if (m.find()) {
            String title = titleFromClause(m.group(1));
            if (!title.isBlank()) return limit(title);
        }

        // Una acción explícita expresa mejor el asunto que un sustantivo interno.
        m = PURPOSE.matcher(text);
        if (m.find()) return limit(capitalize(compactAction(m.group(1))));
        m = ACTION.matcher(text);
        if (m.find()) return limit(capitalize(compactAction(m.group(1))));

        m = MEASURES_OF.matcher(text);
        if (m.find()) return limit(capitalize(singularLabel(m.group(1))) + " " + normalizePrep(m.group(2)) + " " + compactEntity(m.group(3)));

        m = DATA_OF.matcher(text);
        if (m.find()) return limit(capitalize(m.group(1)) + " " + normalizePrep(m.group(2)) + " " + compactEntity(m.group(3)));

        m = IDEAS_FOR.matcher(text);
        if (m.find()) return limit(capitalize(m.group(1)) + " para " + compactTopic(m.group(2)));

        m = TRIP.matcher(text);
        if (m.find()) return limit("Viaje a " + compactTopic(m.group(1)));
        m = LIST_FOR.matcher(text);
        if (m.find()) return limit("Lista de " + compactTopic(m.group(1)) + " para " + compactTopic(m.group(2)));

        String stripped = stripIntro(text);
        m = SUBJECT_NOISE.matcher(stripped);
        if (m.find()) return limit("Ruido de " + cleanupEntity(m.group(1)));
        m = SUBJECT_LOCATION.matcher(stripped);
        if (m.find()) return limit(capitalize(cleanupEntity(m.group(1))));

        String title = LocalTitleEngine.derive(stripped, "note", "Notas", "", "");
        if (title == null || title.isBlank() || "Nota rápida".equalsIgnoreCase(title)) title = firstMeaningfulPhrase(stripped);
        return limit(title);
    }

    private static String titleFromClause(String clause) {
        String c = clean(clause);
        if (c.isBlank()) return "";

        Matcher p = PURPOSE.matcher(c);
        if (p.find()) return capitalize(compactAction(p.group(1)));
        Matcher a = ACTION.matcher(c);
        if (a.find()) return capitalize(compactAction(a.group(1)));
        Matcher measures = MEASURES_OF.matcher(c);
        if (measures.find()) return capitalize(singularLabel(measures.group(1))) + " " + normalizePrep(measures.group(2)) + " " + compactEntity(measures.group(3));
        Matcher data = DATA_OF.matcher(c);
        if (data.find()) return capitalize(data.group(1)) + " " + normalizePrep(data.group(2)) + " " + compactEntity(data.group(3));
        Matcher noise = SUBJECT_NOISE.matcher(stripIntro(c));
        if (noise.find()) return "Ruido de " + cleanupEntity(noise.group(1));
        Matcher loc = SUBJECT_LOCATION.matcher(stripIntro(c));
        if (loc.find()) return capitalize(cleanupEntity(loc.group(1)));
        return firstMeaningfulPhrase(stripIntro(c));
    }

    private static String compactAction(String s) {
        String x = clean(s);
        x = x.replaceAll("(?iu)\\s+(?:(?:tengo|tenemos|hay|debo|debemos)\\s+que|necesito|necesitamos)\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+(?:antes|despu[eé]s)\\s+(?:del|de\\s+la|de\\s+los|de\\s+las|de)?\\s*\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+(?:porque|ya\\s+que|puesto\\s+que|cuando|aunque)\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+y\\s+(?:me|nos)\\s+(?:dicen|dijeron|han\\s+dicho|indican|indicaron)\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+y\\s+(?:despu[eé]s|luego|adem[aá]s)\\b.*$", "");
        return cleanupEntity(x);
    }

    private static String firstMeaningfulPhrase(String s) {
        String x = stripIntro(clean(s));
        if (x.isBlank()) return "";
        x = x.split("[.!?;]", 2)[0].trim();
        x = x.replaceAll("(?iu)\\s+(?:porque|ya\\s+que|puesto\\s+que)\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+y\\s+(?:adem[aá]s|luego|despu[eé]s)\\b.*$", "");
        return cleanupEntity(x);
    }

    private static String stripIntro(String s) {
        String x = clean(s);
        x = x.replaceFirst("(?iu)^\\s*(?:nota|apunta|anota|ap[uú]ntame|an[oó]tame|quiero\\s+recordar|quiero\\s+anotar|deja\\s+anotado|guarda(?:\\s+que)?)\\s*(?:que\\s+)?[:,-]?\\s*", "");
        x = x.replaceFirst("(?iu)^\\s*(?:me|nos)\\s+(?:han\\s+dicho|ha\\s+dicho|dijeron|indicaron|indic[oó]|explicaron|explic[oó]|comentaron|coment[oó])\\s+que\\s+", "");
        return x.trim();
    }

    private static String compactEntity(String s) {
        String x = cleanupEntity(s);
        x = x.replaceAll("(?iu)\\s+(?:es|son)\\b.*$", "");
        x = x.replaceAll("(?iu)\\s+(?:y\\s+(?:tengo|tenemos|hay|debo|debemos|necesito|necesitamos)|porque|ya\\s+que|para\\s+que)\\b.*$", "");
        return cleanupEntity(x);
    }

    private static String compactTopic(String s) {
        String x = clean(s).replaceAll("^[,:;\\-–—\\s]+|[,:;\\-–—\\s]+$", "").trim();
        x = x.replaceFirst("(?iu)^(?:cuando\\s+(?:vayamos|vaya|vamos|viajemos|viaje)\\s+a|el\\s+viaje\\s+a)\\s+", "");
        x = x.replaceAll("(?iu)\\s+(?:mirar|buscar|reservar|revisar|comprobar|comprar|llevar|hacer|alquilar)\\b.*$", "");
        return x.replaceAll("\\s+", " ").trim();
    }

    private static String cleanupEntity(String s) {
        String x = clean(s);
        x = x.replaceAll("^[,:;\\-–—\\s]+|[,:;\\-–—\\s]+$", "").trim();
        x = x.replaceFirst("(?iu)^(?:el|la|los|las|un|una|unos|unas)\\s+", "");
        return x;
    }

    private static String normalizePrep(String s) {
        String n = TemporalParser.norm(s).replaceAll("\\s+", " ").trim();
        if ("del".equals(n)) return "del";
        if ("de la".equals(n)) return "de la";
        if ("de los".equals(n)) return "de los";
        if ("de las".equals(n)) return "de las";
        return "de";
    }

    private static String singularLabel(String s) {
        return TemporalParser.norm(s).startsWith("dimension") ? "Dimensiones" : "Medidas";
    }

    private static String limit(String s) {
        String x = clean(s);
        if (x.isBlank()) return "";
        x = x.replaceAll("^[,:;\\-–—\\s]+|[,:;\\-–—\\s]+$", "").trim();
        String[] words = x.split("\\s+");
        if (words.length > 9) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 9; i++) { if (i > 0) b.append(' '); b.append(words[i]); }
            x = b.toString();
        }
        if (x.length() > 72) {
            x = x.substring(0, 72).trim();
            int p = x.lastIndexOf(' ');
            if (p > 35) x = x.substring(0, p);
        }
        return capitalize(x);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String s) {
        String x = clean(s);
        if (x.isBlank()) return "";
        return x.substring(0,1).toUpperCase(Locale.ROOT) + x.substring(1);
    }
}
