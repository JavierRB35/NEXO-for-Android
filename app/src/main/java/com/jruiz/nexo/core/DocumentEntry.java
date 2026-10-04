package com.jruiz.nexo.core;

import org.json.JSONObject;

/** Description, optional label and one file, using the existing item schema. */
public final class DocumentEntry {
    private DocumentEntry() {}

    public static boolean isDocument(String type) { return "document".equals(type); }

    public static String description(JSONObject item) {
        String title=item.optString("title", "").trim(), body=item.optString("body", "").trim();
        if (body.isEmpty()) return title;
        if (title.isEmpty() || body.startsWith(title)) return body;
        return title + "\n\n" + body;
    }

    public static JSONObject fields(JSONObject in, JSONObject existing) throws Exception {
        String description=in.has("description")?in.optString("description", "").trim():existing.optString("body", "").trim();
        String path=in.optString("attachmentPath",existing.optString("attachmentPath", ""));
        if (path.isBlank()) throw new IllegalArgumentException("Selecciona el archivo que quieres guardar.");

        String title=in.has("title")?in.optString("title", "").trim():existing.optString("title", "").trim();
        if (title.isBlank() && !description.isBlank()) title=description.split("\\n",2)[0].trim();
        if (title.isBlank()) title=in.optString("attachmentName",existing.optString("attachmentName", "")).replaceFirst("\\.[^.]+$","").trim();
        if (title.isBlank()) throw new IllegalArgumentException("Escribe un título para el documento.");
        int titleEnd=title.offsetByCodePoints(0,Math.min(100,title.codePointCount(0,title.length())));
        title=title.substring(0,titleEnd);

        long expiryAt=in.has("expiryAt")?Math.max(0L,in.optLong("expiryAt",0L)):Math.max(0L,existing.optLong("dueAt",0L));
        return new JSONObject().put("type","document").put("category","Documentos")
                .put("title",title).put("body",description)
                .put("subcategory",in.optString("label",existing.optString("subcategory", "")).trim())
                .put("attachmentPath",path)
                .put("attachmentName",in.optString("attachmentName",existing.optString("attachmentName", "")))
                .put("attachmentMime",in.optString("attachmentMime",existing.optString("attachmentMime", "")))
                .put("dueAt",expiryAt).put("endAt",0).put("hasTime",false).put("allDay",false).put("durationMinutes",0)
                .put("recurrence","none").put("recurrenceUntil",0)
                .put("voiceEnabled",false).put("needsReview",false).put("status","active");
    }
}
