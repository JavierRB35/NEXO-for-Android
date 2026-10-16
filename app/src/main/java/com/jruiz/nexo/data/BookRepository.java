package com.jruiz.nexo.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * Biblioteca personal local. Comparte nexo.db y su copia de seguridad.
 * Las fechas se guardan como ISO local (yyyy-MM-dd), sin conversión de zona.
 */
public final class BookRepository {
    private BookRepository() {}

    private static final String FIELDS =
            "id,title,author,status,progress,finish_date,abandoned_date,summary,created_at,updated_at";

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
            o.put("author", c.getString(2));
            o.put("status", c.getString(3));
            o.put("progress", c.getInt(4));
            o.put("finishDate", c.getString(5));
            o.put("abandonedDate", c.getString(6));
            o.put("summary", c.getString(7));
            o.put("createdAt", c.getLong(8));
            o.put("updatedAt", c.getLong(9));
        } catch (Exception ignored) {}
        return o;
    }

    public static JSONArray list(DatabaseHelper helper) {
        JSONArray out = new JSONArray();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + FIELDS + " FROM books ORDER BY updated_at DESC,id DESC", null)) {
            while (c.moveToNext()) out.put(fromCursor(c));
        } catch (Exception ignored) {}
        return out;
    }

    public static JSONObject get(DatabaseHelper helper, long id) {
        if (id <= 0) return new JSONObject();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + FIELDS + " FROM books WHERE id=?",
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

    public static JSONObject save(DatabaseHelper helper, JSONObject input) {
        if (input == null) return result(false, "Datos de libro no válidos.", 0);
        String title = input.optString("title", "").trim();
        String author = input.optString("author", "").trim();
        if (title.isEmpty() || author.isEmpty())
            return result(false, "Indica el título y el autor.", 0);
        if (title.length() > 250 || author.length() > 180)
            return result(false, "Título o autor demasiado largos.", 0);

        String status = input.optString("status", "want");
        if (!("want".equals(status) || "reading".equals(status) ||
                "finished".equals(status) || "abandoned".equals(status)))
            return result(false, "Estado de lectura no válido.", 0);

        int progress = Math.max(0, Math.min(100, input.optInt("progress", 0)));
        String finishDate = input.optString("finishDate", "").trim();
        String abandonedDate = input.optString("abandonedDate", "").trim();
        String summary = input.optString("summary", "").trim();
        if (summary.length() > 20000)
            return result(false, "El resumen supera los 20.000 caracteres.", 0);

        if ("want".equals(status)) {
            progress = 0;
            finishDate = "";
            abandonedDate = "";
            summary = "";
        } else if ("finished".equals(status) || progress == 100 && !"abandoned".equals(status)) {
            status = "finished";
            progress = 100;
            abandonedDate = "";
            if (finishDate.isEmpty()) finishDate = LocalDate.now().toString();
        } else if ("abandoned".equals(status)) {
            progress = Math.min(progress, 99);
            finishDate = "";
            if (abandonedDate.isEmpty()) abandonedDate = LocalDate.now().toString();
        } else {
            finishDate = "";
            abandonedDate = "";
        }

        if (!isValidDate(finishDate) || !isValidDate(abandonedDate))
            return result(false, "Revisa la fecha del libro.", 0);

        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("author", author);
        v.put("status", status);
        v.put("progress", progress);
        v.put("finish_date", finishDate);
        v.put("abandoned_date", abandonedDate);
        v.put("summary", summary);
        long now = System.currentTimeMillis();
        v.put("updated_at", now);
        long id = input.optLong("id", 0);
        try {
            SQLiteDatabase sql = helper.getWritableDatabase();
            if (id > 0) {
                int changed = sql.update("books", v, "id=?", new String[]{String.valueOf(id)});
                return changed > 0 ? result(true, "", id) : result(false, "Libro no encontrado.", 0);
            }
            v.put("created_at", now);
            long newId = sql.insertOrThrow("books", null, v);
            return result(newId > 0, "No se pudo guardar el libro.", newId);
        } catch (Exception e) {
            return result(false, "No se pudo guardar el libro en la base de datos.", 0);
        }
    }

    public static boolean delete(DatabaseHelper helper, long id) {
        if (id <= 0) return false;
        try {
            return helper.getWritableDatabase().delete(
                    "books", "id=?", new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
