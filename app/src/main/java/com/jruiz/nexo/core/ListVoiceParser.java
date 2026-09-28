package com.jruiz.nexo.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intérprete local de órdenes de listas. Está optimizado para habla natural y para
 * transcripciones de Android sin puntuación. No depende de servicios externos.
 */
public final class ListVoiceParser {
    private ListVoiceParser() {}

    public static JSONObject parse(String raw) {
        JSONObject out = new JSONObject();
        String original = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        String n = RuleClassifier.norm(original);
        try {
            out.put("raw", original);
            out.put("action", "add");
            out.put("items", new JSONArray());
            if (original.isBlank()) return out;

            if (containsAny(n, "elimina lo comprado", "elimina los comprados", "borra lo comprado", "borra los comprados", "quita lo comprado", "quita los comprados", "vacia los comprados")) {
                out.put("action", "clear_checked");
                return out;
            }

            Matcher change = Pattern.compile("(?iu)\\b(?:cambia|sustituye|reemplaza)\\s+(.+?)\\s+por\\s+(.+?)(?:\\s+(?:en|de)\\s+(?:la\\s+)?lista(?:\\s+de\\s+la\\s+compra)?)?$", Pattern.CASE_INSENSITIVE).matcher(original);
            if (change.find()) {
                out.put("action", "replace");
                out.put("from", cleanOne(change.group(1)));
                out.put("to", cleanOne(change.group(2)));
                return out;
            }

            String action = "add";
            // Las negaciones deben evaluarse antes que "he comprado", ya que
            // "no he comprado" contiene literalmente esa secuencia.
            if (containsAny(n, "todavia no", "aun no", "desmarca", "no he comprado")) action = "uncheck";
            else if (containsAny(n, "ya he comprado", "he comprado", "marca como comprado", "marca como comprados", "marca comprado", "marca comprados")) action = "check";
            else if (startsAny(n, "quita ", "elimina ", "borra ", "saca ")) action = "remove";
            out.put("action", action);

            String body = stripCommand(original, action);
            JSONArray items = splitItems(body);
            out.put("cleanBody", body);
            out.put("items", items);
            out.put("count", items.length());
            out.put("confidence", items.length() > 0 ? (items.length() > 1 ? 0.96 : 0.82) : 0.25);
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean looksLikeShoppingCommand(String raw) {
        String n = RuleClassifier.norm(raw == null ? "" : raw);
        if (n.isBlank()) return false;
        if (containsAny(n, "lista de la compra", "lista de compra", "a la compra", "en la compra", "elimina lo comprado", "borra lo comprado", "quita lo comprado")) return true;
        boolean product = containsAny(n, "leche", "huevos", "pan", "agua", "azucar", "mantequilla", "cafe", "detergente", "jamon", "queso", "yogur", "papel", "arroz", "pasta", "macarrones", "espaguetis", "coca cola", "cocacola", "pollo", "carne", "pescado", "tomate", "lechuga", "patata", "cebolla", "manzana", "platano", "aceite", "harina", "galleta", "cereal", "sal", "atun", "lentejas", "garbanzos", "champu", "gel", "lejia", "suavizante");
        if (!product) return false;
        return startsAny(n, "comprar ", "compra ", "guardame ", "guarda ", "añade ", "anade ", "agrega ", "apunta ", "pon ", "mete ", "quita ", "elimina ", "borra ", "saca ", "cambia ", "sustituye ", "reemplaza ") ||
                containsAny(n, "ya he comprado", "he comprado", "todavia no", "aun no", "no he comprado");
    }

    private static String stripCommand(String raw, String action) {
        String s = raw.trim();
        if ("check".equals(action)) {
            s = s.replaceFirst("(?iu)^(?:ya\\s+)?he\\s+comprado\\s+", "");
            s = s.replaceFirst("(?iu)^marca(?:\\s+como)?\\s+comprad[oa]s?\\s+", "");
        } else if ("uncheck".equals(action)) {
            s = s.replaceFirst("(?iu)^(?:todav[ií]a|a[uú]n)\\s+no\\s+(?:he\\s+comprado\\s+)?", "");
            s = s.replaceFirst("(?iu)^no\\s+he\\s+comprado\\s+", "");
            s = s.replaceFirst("(?iu)^desmarca\\s+", "");
            s = s.replaceFirst("(?iu)\\s+(?:todav[ií]a|a[uú]n)\\s+no[.!?]?$", "");
        } else if ("remove".equals(action)) {
            s = s.replaceFirst("(?iu)^(?:quita|elimina|borra|saca)\\s+", "");
        } else {
            // Variantes habituales del habla: "guárdame en la lista...", "apúntame...", etc.
            s = s.replaceFirst("(?iu)^(?:por\\s+favor\\s+)?(?:(?:gu[aá]rda(?:me)?|a[nñ][aá]de(?:me)?|agr[eé]ga(?:me)?|ap[uú]nta(?:me)?|pon(?:me)?|m[eé]te(?:me)?)\\s+)+", "");
            s = s.replaceFirst("(?iu)^(?:quiero|necesito)\\s+(?:que\\s+)?(?:comprar|a[nñ]adir|agregar|apuntar)\\s+", "");
            s = s.replaceFirst("(?iu)^(?:necesito\\s+)?comprar\\s+", "");
            s = s.replaceFirst("(?iu)^compra\\s+", "");
        }
        // El destino de la orden puede ir antes o después de los productos.
        s = s.replaceFirst("(?iu)^(?:a|en)\\s+(?:la\\s+)?lista\\s+de\\s+(?:la\\s+)?compra\\s*[:,-]?\\s*", "");
        s = s.replaceFirst("(?iu)^(?:a|en)\\s+(?:la\\s+)?lista\\s*[:,-]?\\s*", "");
        s = s.replaceFirst("(?iu)^(?:gu[aá]rda(?:me)?|a[nñ][aá]de(?:me)?|agr[eé]ga(?:me)?|ap[uú]nta(?:me)?|pon(?:me)?|m[eé]te(?:me)?|comprar|compra)\\s+", "");
        // Muchas personas dicen: "... en la lista de la compra comprar macarrones..."
        s = s.replaceFirst("(?iu)^(?:comprar|compra)\\s+", "");
        s = s.replaceAll("(?iu)\\s+(?:a|en|de)\\s+(?:la\\s+)?lista(?:\\s+de\\s+(?:la\\s+)?compra)?[.!?]?$", "");
        s = s.replaceAll("(?iu)\\s+para\\s+la\\s+compra[.!?]?$", "");
        return s.trim();
    }

    private static final Pattern PRODUCT_ANCHOR = Pattern.compile(
            "(?iu)\\b(?:leche|huevos?|az[uú]car|mantequilla|agua|detergente|jam[oó]n|pan|caf[eé]|queso|yogur(?:es)?|papel\\s+higi[eé]nico|arroz|pasta|macarrones?|espaguetis?|fideos?|pollo|carne|pescado|tomates?|lechuga|patatas?|cebollas?|manzanas?|peras?|naranjas?|pl[aá]tanos?|cerveza|vino|refresco|zumo|coca[\\s-]?cola|pepsi|aceite|harina|galletas?|cereales?|sal|legumbres?|lentejas?|garbanzos?|jud[ií]as|at[uú]n|salm[oó]n|merluza|gel|champ[uú]|desodorante|lej[ií]a|suavizante|lavavajillas)\\b");

    private static final Pattern QUANTITY_TAIL = Pattern.compile(
            "(?iu)(?:^|\\s)(?:\\d+(?:[.,]\\d+)?|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio)" +
            "(?:\\s+(?:litros?|l|botellas?|paquetes?|cajas?|docenas?|kilos?|kg|gramos?|g|unidades?|botes?|latas?|bolsas?|packs?|bricks?)(?:\\s+de)?)?\\s*$");

    private static JSONArray splitItems(String body) {
        JSONArray out = new JSONArray();
        if (body == null || body.isBlank()) return out;
        String s = body.trim().replaceAll("[.!?]+$", "");
        s = s.replaceAll("(?iu)\\s+(?:y|e)\\s+", ",");
        String[] parts = s.split("[,;\\n]+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            for (String spokenItem : splitChunkByProducts(part)) {
                String x = cleanOne(spokenItem);
                if (x.isBlank()) continue;
                String key = RuleClassifier.norm(x);
                if (!key.isBlank() && unique.stream().noneMatch(v -> RuleClassifier.norm(v).equals(key))) unique.add(x);
            }
        }
        for (String x : unique) out.put(x);
        return out;
    }

    /**
     * Separa una transcripción sin comas usando productos frecuentes como anclas.
     * Conserva modificadores y cantidades: "leche desnatada huevos" -> "leche desnatada" + "huevos".
     */
    private static List<String> splitChunkByProducts(String chunk) {
        List<String> result = new ArrayList<>();
        if (chunk == null || chunk.isBlank()) return result;
        String source = chunk.trim();
        Matcher m = PRODUCT_ANCHOR.matcher(source);
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        while (m.find()) { starts.add(m.start()); ends.add(m.end()); }
        if (starts.size() < 2) { result.add(source); return result; }

        List<Integer> boundaries = new ArrayList<>(); boundaries.add(0);
        for (int i = 1; i < starts.size(); i++) {
            int boundary = starts.get(i), betweenStart = ends.get(i - 1);
            if (betweenStart < boundary) {
                String between = source.substring(betweenStart, boundary);
                Matcher q = QUANTITY_TAIL.matcher(between);
                if (q.find()) {
                    int candidate = betweenStart + q.start();
                    while (candidate < source.length() && Character.isWhitespace(source.charAt(candidate))) candidate++;
                    if (candidate > boundaries.get(boundaries.size() - 1)) boundary = candidate;
                }
            }
            if (boundary > boundaries.get(boundaries.size() - 1)) boundaries.add(boundary);
        }
        for (int i = 0; i < boundaries.size(); i++) {
            int pieceStart = boundaries.get(i), pieceEnd = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) : source.length();
            String piece = source.substring(pieceStart, pieceEnd).trim();
            if (!piece.isBlank()) result.add(piece);
        }
        return result;
    }

    private static String cleanOne(String value) {
        if (value == null) return "";
        String s = value.trim().replaceAll("^[,;:.\\- ]+|[,;:.\\- ]+$", "");
        s = s.replaceFirst("(?iu)^(?:el|la|los|las)\\s+", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 100) s = s.substring(0,100).trim();
        return s;
    }

    private static boolean containsAny(String s, String... values) { for (String v : values) if (s.contains(RuleClassifier.norm(v))) return true; return false; }
    private static boolean startsAny(String s, String... values) { for (String v : values) if (s.startsWith(RuleClassifier.norm(v))) return true; return false; }
}
