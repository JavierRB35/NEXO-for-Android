package com.jruiz.nexo.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/** Biblioteca local independiente de películas y series (SQLite 31). */
public final class MediaRepository {
    private MediaRepository() {}

    private static final String FIELDS =
            "id,title,creator,status,progress,finish_date,abandoned_date,summary,created_at,updated_at,start_date,favorite,rating,media_type,category,synopsis";

    private static JSONObject result(boolean ok, String error, long id) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", ok);
            if (!ok) o.put("error", error);
            if (id > 0) o.put("id", id);
        } catch (Exception ignored) {}
        return o;
    }

    private static JSONObject fromCursor(Cursor c) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", c.getLong(0));
            o.put("title", c.getString(1));
            o.put("creator", c.getString(2));
            o.put("status", c.getString(3));
            o.put("progress", c.getInt(4));
            o.put("finishDate", c.getString(5));
            o.put("abandonedDate", c.getString(6));
            o.put("summary", c.getString(7));
            o.put("createdAt", c.getLong(8));
            o.put("updatedAt", c.getLong(9));
            o.put("startDate", c.getString(10));
            o.put("favorite", c.getInt(11) != 0);
            o.put("rating", c.getInt(12));
            o.put("mediaType", c.getString(13));
            o.put("category", c.getString(14));
            o.put("synopsis", c.getString(15));
        } catch (Exception ignored) {}
        return o;
    }

    public static JSONArray list(DatabaseHelper helper) {
        JSONArray out = new JSONArray();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + FIELDS + " FROM media_items ORDER BY updated_at DESC,id DESC", null)) {
            while (c.moveToNext()) out.put(fromCursor(c));
        } catch (Exception ignored) {}
        return out;
    }

    public static JSONObject get(DatabaseHelper helper, long id) {
        if (id <= 0) return new JSONObject();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + FIELDS + " FROM media_items WHERE id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? fromCursor(c) : new JSONObject();
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static boolean isValidDate(String s) {
        if (s.isEmpty()) return true;
        try { return LocalDate.parse(s).toString().equals(s); }
        catch (Exception e) { return false; }
    }

    private static String mediaKey(String value) {
        String clean = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return clean.replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isDuplicate(SQLiteDatabase db, long exceptId, String title, String creator, String mediaType) {
        final String normTitle = mediaKey(title);
        final String normCreator = mediaKey(creator);
        try (Cursor c = db.rawQuery("SELECT id,title,creator,media_type FROM media_items", null)) {
            while (c.moveToNext()) {
                if (c.getLong(0) == exceptId) continue;
                if (normTitle.equals(mediaKey(c.getString(1)))
                        && normCreator.equals(mediaKey(c.getString(2)))
                        && mediaType.equals(c.getString(3))) return true;
            }
        }
        return false;
    }

    public static JSONObject save(DatabaseHelper helper, JSONObject input) {
        if (input == null) return result(false, "Datos de película o serie no válidos.", 0);
        String title = input.optString("title", "").trim();
        String creator = input.optString("creator", "").trim();
        if (title.isEmpty())
            return result(false, "Indica el título.", 0);
        if (title.length() > 250 || creator.length() > 180)
            return result(false, "El título o el nombre del director/creador es demasiado largo.", 0);

        String mediaType = input.optString("mediaType", "movie");
        if (!("movie".equals(mediaType) || "series".equals(mediaType)))
            return result(false, "Selecciona Película o Serie.", 0);
        String status = input.optString("status", "want");
        if (!("want".equals(status) || "reading".equals(status) ||
                "finished".equals(status) || "abandoned".equals(status)))
            return result(false, "Estado de visualización no válido.", 0);

        int progress = Math.max(0, Math.min(100, input.optInt("progress", 0)));
        String startDate = input.optString("startDate", "").trim();
        String finishDate = input.optString("finishDate", "").trim();
        String abandonedDate = input.optString("abandonedDate", "").trim();
        String summary = input.optString("summary", "").trim();
        String synopsis = input.optString("synopsis", "").trim();
        String category = input.optString("category", "").trim();
        if (category.length() > 80) return result(false, "La categoría supera 80 caracteres.", 0);
        long id = input.optLong("id", 0);
        int rating = input.optInt("rating", 0);
        if (rating < 0 || rating > 5)
            return result(false, "La valoración debe estar entre 0 y 5 estrellas.", 0);
        boolean favorite = input.optBoolean("favorite", false);
        // No asignar la fecha de hoy a película o series históricos sin fecha al editar solo sus estrellas o corazón.
        String previousStatus = id > 0 ? get(helper, id).optString("status", "") : "";
        if (summary.length() > 20000)
            return result(false, "El resumen supera los 20.000 caracteres.", 0);
        if (synopsis.length() > 20000)
            return result(false, "El argumento supera los 20.000 caracteres.", 0);

        if ("want".equals(status)) {
            progress = 0;
            startDate = "";
            finishDate = "";
            abandonedDate = "";
            summary = "";
        } else if ("finished".equals(status) || (progress == 100 && !"abandoned".equals(status))) {
            status = "finished";
            progress = 100;
            abandonedDate = "";
            if (finishDate.isEmpty() && !"finished".equals(previousStatus)) finishDate = LocalDate.now().toString();
        } else if ("abandoned".equals(status)) {
            progress = Math.min(progress, 99);
            finishDate = "";
            if (abandonedDate.isEmpty() && !"abandoned".equals(previousStatus)) abandonedDate = LocalDate.now().toString();
        } else {
            finishDate = "";
            abandonedDate = "";
        }

        if (!isValidDate(startDate) || !isValidDate(finishDate) || !isValidDate(abandonedDate))
            return result(false, "Revisa las fechas.", 0);
        if (!startDate.isEmpty() && !finishDate.isEmpty() && startDate.compareTo(finishDate) > 0)
            return result(false, "El inicio no puede ser posterior a la finalización.", 0);
        if (!startDate.isEmpty() && !abandonedDate.isEmpty() && startDate.compareTo(abandonedDate) > 0)
            return result(false, "El inicio no puede ser posterior al abandono.", 0);

        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("media_type", mediaType);
        v.put("creator", creator);
        v.put("status", status);
        v.put("progress", progress);
        v.put("start_date", startDate);
        v.put("finish_date", finishDate);
        v.put("abandoned_date", abandonedDate);
        v.put("summary", summary);
        v.put("synopsis", synopsis);
        v.put("category", category);
        v.put("favorite", favorite ? 1 : 0);
        v.put("rating", rating);
        long now = System.currentTimeMillis();
        v.put("updated_at", now);
        try {
            SQLiteDatabase sql = helper.getWritableDatabase();
            if (isDuplicate(sql, id, title, creator, mediaType)) {
                return result(false, "Esta película o serie ya está en tu biblioteca.", 0);
            }
            if (id > 0) {
                int changed = sql.update("media_items", v, "id=?", new String[]{String.valueOf(id)});
                return changed > 0 ? result(true, "", id) : result(false, "Película o serie no encontrado.", 0);
            }
            v.put("created_at", now);
            long newId = sql.insertOrThrow("media_items", null, v);
            return result(newId > 0, "No se pudo guardar el película o serie.", newId);
        } catch (Exception e) {
            return result(false, "No se pudo guardar el película o serie en la base de datos.", 0);
        }
    }

    public static boolean delete(DatabaseHelper helper, long id) {
        if (id <= 0) return false;
        try {
            return helper.getWritableDatabase().delete(
                    "media_items", "id=?", new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
