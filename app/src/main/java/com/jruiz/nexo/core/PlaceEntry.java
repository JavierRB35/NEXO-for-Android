package com.jruiz.nexo.core;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;
import org.json.JSONObject;

/** Saved places use existing item fields, without calendar or reminder semantics. */
public final class PlaceEntry {
    private PlaceEntry() {}

    public static boolean isPlace(String type) {
        return "place".equals(type) || "restaurant".equals(type);
    }

    public static String label(JSONObject item) {
        String value=item.optString("subcategory", "").trim();
        if (!value.isEmpty()) return value;
        return "restaurant".equals(item.optString("type")) || "Restaurantes".equals(item.optString("category")) ? "Restaurante" : "Otros";
    }

    public static boolean matchesSearchCategory(JSONObject item,String category) {
        return isPlace(item.optString("type")) && ("lugares".equals(category)
                || "restaurantes".equals(category) && "Restaurante".equalsIgnoreCase(label(item)));
    }

    public static JSONObject fields(JSONObject in) throws Exception {
        String title=in.optString("title", "").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Escribe un título para el lugar.");
        String location=in.optString("locationText", "").trim();
        if (!location.isEmpty()) mapsUrl(location);
        String label=in.optString("label", "Otros").trim();
        if (label.isEmpty()) label="Otros";
        return new JSONObject().put("title",title).put("body",in.optString("body", "").trim())
                .put("locationText",location).put("subcategory",label)
                .put("type","place").put("category","Lugares").put("status","active")
                .put("dueAt",0).put("hasTime",false).put("durationMinutes",0)
                .put("recurrence","none").put("recurrenceUntil",0)
                .put("voiceEnabled",false).put("needsReview",false);
    }

    /** Google Maps URLs, no API key or SDK. See developers.google.com/maps/documentation/urls/get-started. */
    public static String mapsUrl(String input) {
        String value=input==null?"":input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Añade un enlace de Google Maps o una dirección.");
        String lower=value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.google.") || lower.startsWith("maps.google.") || lower.startsWith("maps.app.goo.gl/") || lower.startsWith("goo.gl/maps/")) {
            value="https://"+value;lower=value.toLowerCase(Locale.ROOT);
        }
        String url;
        if (lower.matches("^[a-z][a-z0-9+.-]*:.*") || lower.startsWith("//")) {
            try {
                URI uri=URI.create(value);
                String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
                String path=uri.getPath()==null?"":uri.getPath();
                boolean google=host.matches("(?:www\\.|maps\\.)?google\\.(?:com|es|fr|it|de|pt|co\\.uk)");
                boolean mapsPath=host.startsWith("maps.google.") || path.equals("/maps") || path.startsWith("/maps/");
                boolean shortLink=host.equals("maps.app.goo.gl") && path.length()>1 || host.equals("goo.gl") && path.startsWith("/maps/");
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo()!=null || uri.getPort()!=-1 || !(shortLink || google&&mapsPath)) {
                    throw new IllegalArgumentException();
                }
                url=value;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Usa un enlace compartido desde Google Maps o escribe una dirección.");
            }
        } else {
            try { url="https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(value,"UTF-8"); }
            catch(java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
        }
        if (url.length()>2048) throw new IllegalArgumentException("La ubicación es demasiado larga. Copia el enlace corto de Compartir en Google Maps.");
        return url;
    }
}
