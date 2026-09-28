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
        String description=in.optString("description", "").trim();
        if (description.isEmpty()) throw new IllegalArgumentException("Escribe una descripción del documento.");
        String path=in.optString("attachmentPath",existing.optString("attachmentPath", ""));
        if (path.isBlank()) throw new IllegalArgumentException("Selecciona el archivo que quieres guardar.");
        String firstLine=description.split("\n",2)[0];
        int end=firstLine.offsetByCodePoints(0,Math.min(100,firstLine.codePointCount(0,firstLine.length())));
        return new JSONObject().put("type","document").put("category","Documentos")
                .put("title",firstLine.substring(0,end)).put("body",description)
                .put("subcategory",in.optString("label",existing.optString("subcategory", "")).trim())
                .put("attachmentPath",path)
                .put("attachmentName",in.optString("attachmentName",existing.optString("attachmentName", "")))
                .put("attachmentMime",in.optString("attachmentMime",existing.optString("attachmentMime", "")))
                .put("dueAt",0).put("hasTime",false).put("durationMinutes",0)
                .put("recurrence","none").put("recurrenceUntil",0)
                .put("voiceEnabled",false).put("needsReview",false).put("status","active");
    }
}
