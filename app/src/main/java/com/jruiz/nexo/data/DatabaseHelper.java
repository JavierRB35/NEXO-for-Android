package com.jruiz.nexo.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.jruiz.nexo.core.ProductCategorizer;
import com.jruiz.nexo.core.QueryInterpreter;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.core.RecurrenceEngine;
import com.jruiz.nexo.core.JournalSynthesizer;
import com.jruiz.nexo.core.PlaceEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.util.*;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "nexo.db";
    private static final int DB_VERSION = 20;

    public static String databaseName() { return DB_NAME; }
    public static int schemaVersion() { return DB_VERSION; }

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL DEFAULT 'note'," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL DEFAULT ''," +
                "original_text TEXT NOT NULL DEFAULT ''," +
                "category TEXT NOT NULL DEFAULT 'Otros'," +
                "subcategory TEXT NOT NULL DEFAULT ''," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "due_at INTEGER NOT NULL DEFAULT 0," +
                "end_at INTEGER NOT NULL DEFAULT 0," +
                "has_time INTEGER NOT NULL DEFAULT 0," +
                "all_day INTEGER NOT NULL DEFAULT 0," +
                "time_zone TEXT NOT NULL DEFAULT 'Europe/Madrid'," +
                "maps_url TEXT NOT NULL DEFAULT ''," +
                "recurrence_rule TEXT NOT NULL DEFAULT ''," +
                "participants_text TEXT NOT NULL DEFAULT ''," +
                "deleted_at INTEGER NOT NULL DEFAULT 0," +
                "lat REAL," +
                "lon REAL," +
                "location_text TEXT NOT NULL DEFAULT ''," +
                "priority INTEGER NOT NULL DEFAULT 1," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "source TEXT NOT NULL DEFAULT 'manual'," +
                "attachment_path TEXT NOT NULL DEFAULT ''," +
                "attachment_name TEXT NOT NULL DEFAULT ''," +
                "attachment_mime TEXT NOT NULL DEFAULT ''," +
                "status TEXT NOT NULL DEFAULT 'active'," +
                "needs_review INTEGER NOT NULL DEFAULT 0," +
                "recurrence TEXT NOT NULL DEFAULT 'none'," +
                "recurrence_until INTEGER NOT NULL DEFAULT 0," +
                "duration_minutes INTEGER NOT NULL DEFAULT 0," +
                "voice_enabled INTEGER NOT NULL DEFAULT 0," +
                "note_title_manual INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_items_due ON items(due_at)");
        db.execSQL("CREATE INDEX idx_items_type_status ON items(type,status)");
        db.execSQL("CREATE INDEX idx_items_category ON items(category)");

        db.execSQL("CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item_id INTEGER NOT NULL DEFAULT 0," +
                "title TEXT NOT NULL," +
                "trigger_at INTEGER NOT NULL," +
                "occurrence_at INTEGER NOT NULL DEFAULT 0," +
                "lead_days INTEGER NOT NULL DEFAULT 0," +
                "lead_minutes INTEGER NOT NULL DEFAULT 0," +
                "enabled INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_reminders_trigger ON reminders(trigger_at)");

        db.execSQL("CREATE TABLE recurrence_exceptions (" +
                "series_id INTEGER NOT NULL," +
                "occurrence_at INTEGER NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'active'," +
                "override_json TEXT NOT NULL DEFAULT ''," +
                "override_due_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(series_id,occurrence_at)" +
                ")");
        db.execSQL("CREATE INDEX idx_recurrence_override_due ON recurrence_exceptions(override_due_at)");

        db.execSQL("CREATE TABLE lists (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "kind TEXT NOT NULL DEFAULT 'General'," +
                "reusable INTEGER NOT NULL DEFAULT 0," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE list_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "list_id INTEGER NOT NULL," +
                "text TEXT NOT NULL," +
                "checked INTEGER NOT NULL DEFAULT 0," +
                "sort_order INTEGER NOT NULL DEFAULT 0," +
                "category TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_list_entries_list ON list_entries(list_id)");

        db.execSQL("CREATE TABLE history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "action TEXT NOT NULL," +
                "entity_type TEXT NOT NULL," +
                "entity_id INTEGER NOT NULL DEFAULT 0," +
                "label TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL" +
                ")");

        db.execSQL("CREATE TABLE learned_terms (" +
                "term TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "weight INTEGER NOT NULL DEFAULT 1," +
                "PRIMARY KEY(term,category)" +
                ")");

        createInboxTables(db);
        createCalendarWatchTables(db);
        createExternalWatchTables(db);
        createNexoFamiliaCompatibilityTables(db);
        createJournalTables(db);
        seedJournalBiblicalLibrary(db);
        createMedicationTables(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE items ADD COLUMN has_time INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE items ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
            // Los datos antiguos usaban datetime-local, por lo que una fecha existente tenía hora explícita.
            db.execSQL("UPDATE items SET has_time=1 WHERE due_at>0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'none'");
            db.execSQL("ALTER TABLE items ADD COLUMN recurrence_until INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE reminders ADD COLUMN occurrence_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS recurrence_exceptions (series_id INTEGER NOT NULL,occurrence_at INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'active',override_json TEXT NOT NULL DEFAULT '',override_due_at INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,PRIMARY KEY(series_id,occurrence_at))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_recurrence_override_due ON recurrence_exceptions(override_due_at)");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE items ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN lead_minutes INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE reminders SET lead_minutes=lead_days*1440 WHERE lead_minutes=0 AND lead_days>0");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE items ADD COLUMN original_text TEXT NOT NULL DEFAULT ''");
            // Los registros anteriores no tenían transcripción separada; no se inventa una.
        }
        if (oldVersion < 7) {
            createJournalTables(db);
        }
        if (oldVersion < 8) {
            createMedicationTables(db);
        }
        if (oldVersion < 10) {
            cleanupSeedDuplicates(db);
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE items ADD COLUMN voice_enabled INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 12) {
            createJournalTables(db);
        }
        if (oldVersion < 13) {
            // Sustituye únicamente las bibliotecas incluidas por defecto; conserva intactas las entradas del usuario.
            replaceDefaultJournalLibrary(db);
        }
        if (oldVersion < 14) {
            // Conserva los títulos de notas existentes como manuales para que una actualización no los reescriba.
            db.execSQL("ALTER TABLE items ADD COLUMN note_title_manual INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE items SET note_title_manual=1 WHERE type='note'");
        }
        if (oldVersion < 15) {
            createInboxTables(db);
        }
        if (oldVersion < 16) {
            db.execSQL("ALTER TABLE inbox_captures ADD COLUMN source_package TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE inbox_captures ADD COLUMN sender TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE inbox_captures ADD COLUMN source_key TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_source_key ON inbox_captures(source_key)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_source_package ON inbox_captures(source_package,status)");
        }
        if (oldVersion < 17) {
            createCalendarWatchTables(db);
        }
        if (oldVersion < 18) {
            createExternalWatchTables(db);
        }
        if (oldVersion < 19) {
            createNexoFamiliaCompatibilityTables(db);
        }
        if (oldVersion < 20) {
            db.execSQL("ALTER TABLE items ADD COLUMN end_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE items ADD COLUMN all_day INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE items ADD COLUMN time_zone TEXT NOT NULL DEFAULT 'Europe/Madrid'");
            db.execSQL("ALTER TABLE items ADD COLUMN maps_url TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE items ADD COLUMN recurrence_rule TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE items ADD COLUMN participants_text TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE items SET end_at=CASE WHEN duration_minutes>0 THEN due_at+(duration_minutes*60000) ELSE due_at END WHERE due_at>0 AND end_at=0");
            db.execSQL("UPDATE items SET all_day=1 WHERE due_at>0 AND has_time=0 AND type IN ('event','task','reminder','expiry')");
        }
    }


    private static void createInboxTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS inbox_captures (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source TEXT NOT NULL DEFAULT 'android-share'," +
                "mime TEXT NOT NULL DEFAULT ''," +
                "subject TEXT NOT NULL DEFAULT ''," +
                "raw_text TEXT NOT NULL DEFAULT ''," +
                "attachment_path TEXT NOT NULL DEFAULT ''," +
                "attachment_name TEXT NOT NULL DEFAULT ''," +
                "attachment_mime TEXT NOT NULL DEFAULT ''," +
                "detected_type TEXT NOT NULL DEFAULT ''," +
                "detected_title TEXT NOT NULL DEFAULT ''," +
                "classification_json TEXT NOT NULL DEFAULT '{}'," +
                "status TEXT NOT NULL DEFAULT 'new'," +
                "converted_entity TEXT NOT NULL DEFAULT ''," +
                "converted_id INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "source_package TEXT NOT NULL DEFAULT ''," +
                "sender TEXT NOT NULL DEFAULT ''," +
                "source_key TEXT NOT NULL DEFAULT ''" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_status_created ON inbox_captures(status,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_converted ON inbox_captures(converted_entity,converted_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_source_key ON inbox_captures(source_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_captures_source_package ON inbox_captures(source_package,status)");
    }


    private static void createCalendarWatchTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar_watch_events (" +
                "event_id INTEGER PRIMARY KEY," +
                "fingerprint TEXT NOT NULL DEFAULT ''," +
                "capture_id INTEGER NOT NULL DEFAULT 0," +
                "linked_item_id INTEGER NOT NULL DEFAULT 0," +
                "last_seen_at INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_watch_capture ON calendar_watch_events(capture_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_watch_linked_item ON calendar_watch_events(linked_item_id)");
    }


    private static void createExternalWatchTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS external_watch_items (" +
                "source TEXT NOT NULL," +
                "external_id TEXT NOT NULL," +
                "fingerprint TEXT NOT NULL DEFAULT ''," +
                "capture_id INTEGER NOT NULL DEFAULT 0," +
                "last_seen_at INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(source,external_id)" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_external_watch_capture ON external_watch_items(capture_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_external_watch_seen ON external_watch_items(source,last_seen_at)");
    }


    private static void createNexoFamiliaCompatibilityTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS nexo_familia_events (" +
                "source_event_id TEXT PRIMARY KEY," +
                "local_item_id INTEGER NOT NULL DEFAULT 0," +
                "family_id TEXT NOT NULL DEFAULT ''," +
                "created_by TEXT NOT NULL DEFAULT ''," +
                "participant_ids_json TEXT NOT NULL DEFAULT '[]'," +
                "visibility TEXT NOT NULL DEFAULT ''," +
                "shared_with_json TEXT NOT NULL DEFAULT '[]'," +
                "event_origin TEXT NOT NULL DEFAULT ''," +
                "social_type TEXT NOT NULL DEFAULT ''," +
                "venue_type TEXT NOT NULL DEFAULT ''," +
                "maps_url TEXT NOT NULL DEFAULT ''," +
                "guest_count INTEGER NOT NULL DEFAULT 0," +
                "guest_details_json TEXT NOT NULL DEFAULT '[]'," +
                "external_uid TEXT NOT NULL DEFAULT ''," +
                "external_source TEXT NOT NULL DEFAULT ''," +
                "source_status TEXT NOT NULL DEFAULT ''," +
                "time_zone TEXT NOT NULL DEFAULT 'Europe/Madrid'," +
                "recurrence_rule TEXT NOT NULL DEFAULT ''," +
                "start_raw TEXT NOT NULL DEFAULT ''," +
                "end_raw TEXT NOT NULL DEFAULT ''," +
                "raw_json TEXT NOT NULL DEFAULT '{}'," +
                "imported_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_nexo_familia_local_item ON nexo_familia_events(local_item_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nexo_familia_reminders (" +
                "source_rule_id TEXT PRIMARY KEY," +
                "source_event_id TEXT NOT NULL DEFAULT ''," +
                "local_item_id INTEGER NOT NULL DEFAULT 0," +
                "local_reminder_id INTEGER NOT NULL DEFAULT 0," +
                "minutes_before INTEGER NOT NULL DEFAULT 0," +
                "source_status TEXT NOT NULL DEFAULT ''," +
                "raw_json TEXT NOT NULL DEFAULT '{}'," +
                "imported_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nexo_familia_reminder_event ON nexo_familia_reminders(source_event_id)");
    }

    public long getNexoFamiliaLocalItemId(String sourceEventId) {
        if (sourceEventId == null || sourceEventId.isBlank()) return 0L;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT local_item_id FROM nexo_familia_events WHERE source_event_id=?",
                new String[]{sourceEventId})) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } catch (Exception ignored) { return 0L; }
    }

    public void rememberNexoFamiliaEvent(String sourceEventId, long localItemId, JSONObject source) {
        if (sourceEventId == null || sourceEventId.isBlank() || localItemId <= 0 || source == null) return;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("source_event_id", sourceEventId);
        v.put("local_item_id", localItemId);
        v.put("family_id", cleanJsonString(source, "family_id"));
        v.put("created_by", cleanJsonString(source, "created_by"));
        v.put("participant_ids_json", String.valueOf(source.optJSONArray("participant_ids") == null ? new JSONArray() : source.optJSONArray("participant_ids")));
        v.put("visibility", cleanJsonString(source, "visibility"));
        v.put("shared_with_json", String.valueOf(source.optJSONArray("shared_with") == null ? new JSONArray() : source.optJSONArray("shared_with")));
        v.put("event_origin", cleanJsonString(source, "event_origin"));
        v.put("social_type", cleanJsonString(source, "social_type"));
        v.put("venue_type", cleanJsonString(source, "venue_type"));
        v.put("maps_url", cleanJsonString(source, "maps_url"));
        v.put("guest_count", Math.max(0, source.optInt("guest_count", 0)));
        Object guestDetails = source.opt("guest_details");
        v.put("guest_details_json", guestDetails == null || guestDetails == JSONObject.NULL ? "[]" : String.valueOf(guestDetails));
        v.put("external_uid", cleanJsonString(source, "external_uid"));
        v.put("external_source", cleanJsonString(source, "external_source"));
        v.put("source_status", cleanJsonString(source, "status"));
        v.put("time_zone", nonBlank(source.optString("time_zone"), "Europe/Madrid"));
        v.put("recurrence_rule", cleanJsonString(source, "recurrence_rule"));
        v.put("start_raw", source.optBoolean("all_day", false) ? cleanJsonString(source, "start_date") : cleanJsonString(source, "start_at"));
        v.put("end_raw", source.optBoolean("all_day", false) ? cleanJsonString(source, "end_date") : cleanJsonString(source, "end_at"));
        v.put("raw_json", source.toString());
        v.put("updated_at", now);
        long importedAt = now;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT imported_at FROM nexo_familia_events WHERE source_event_id=?",
                new String[]{sourceEventId})) {
            if (c.moveToFirst()) importedAt = c.getLong(0);
        } catch (Exception ignored) {}
        v.put("imported_at", importedAt);
        getWritableDatabase().insertWithOnConflict("nexo_familia_events", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public JSONArray deleteNexoFamiliaImportedReminders(String sourceEventId) {
        JSONArray ids = new JSONArray();
        if (sourceEventId == null || sourceEventId.isBlank()) return ids;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ArrayList<Long> values = new ArrayList<>();
            try (Cursor c = db.rawQuery(
                    "SELECT local_reminder_id FROM nexo_familia_reminders WHERE source_event_id=? AND local_reminder_id>0",
                    new String[]{sourceEventId})) {
                while (c.moveToNext()) values.add(c.getLong(0));
            }
            for (Long id : values) {
                ids.put(id);
                db.delete("reminders", "id=?", new String[]{String.valueOf(id)});
            }
            db.delete("nexo_familia_reminders", "source_event_id=?", new String[]{sourceEventId});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return ids;
    }

    public void rememberNexoFamiliaReminder(String sourceRuleId, String sourceEventId, long localItemId,
                                            long localReminderId, int minutesBefore, String sourceStatus,
                                            JSONObject raw) {
        if (sourceRuleId == null || sourceRuleId.isBlank()) return;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("source_rule_id", sourceRuleId);
        v.put("source_event_id", sourceEventId == null ? "" : sourceEventId);
        v.put("local_item_id", Math.max(0, localItemId));
        v.put("local_reminder_id", Math.max(0, localReminderId));
        v.put("minutes_before", Math.max(0, minutesBefore));
        v.put("source_status", sourceStatus == null ? "" : sourceStatus);
        v.put("raw_json", raw == null ? "{}" : raw.toString());
        v.put("updated_at", now);
        long importedAt = now;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT imported_at FROM nexo_familia_reminders WHERE source_rule_id=?",
                new String[]{sourceRuleId})) {
            if (c.moveToFirst()) importedAt = c.getLong(0);
        } catch (Exception ignored) {}
        v.put("imported_at", importedAt);
        getWritableDatabase().insertWithOnConflict("nexo_familia_reminders", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Elimina únicamente los datos locales creados por la migración de Nexo Familia.
     * No toca eventos manuales ni otras fuentes. Devuelve los IDs de avisos eliminados
     * para cancelar sus alarmas del sistema.
     */
    public JSONArray resetNexoFamiliaImportedData() {
        JSONArray reminderIds = new JSONArray();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String targetItems = "item_id IN (SELECT local_item_id FROM nexo_familia_events) " +
                    "OR item_id IN (SELECT id FROM items WHERE source='nexo-familia')";
            try (Cursor c = db.rawQuery(
                    "SELECT id FROM reminders WHERE " + targetItems,
                    null)) {
                while (c.moveToNext()) reminderIds.put(c.getLong(0));
            }

            db.delete("reminders", targetItems, null);
            db.execSQL("DELETE FROM recurrence_exceptions WHERE series_id IN (SELECT local_item_id FROM nexo_familia_events) " +
                    "OR series_id IN (SELECT id FROM items WHERE source='nexo-familia')");
            db.execSQL("DELETE FROM items WHERE id IN (SELECT local_item_id FROM nexo_familia_events) OR source='nexo-familia'");
            db.delete("nexo_familia_reminders", null, null);
            db.delete("nexo_familia_events", null, null);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return reminderIds;
    }

    public boolean hasNexoFamiliaReminderMapping(String sourceRuleId) {
        if (sourceRuleId == null || sourceRuleId.isBlank()) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM nexo_familia_reminders WHERE source_rule_id=? LIMIT 1",
                new String[]{sourceRuleId})) {
            return c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Devuelve el registro original completo de Nexo Familia asociado al evento local.
     * El modelo Android conserva además los campos de uso diario normalizados; aquí se
     * mantienen sin pérdida los campos sociales, de visibilidad, UUID, invitados,
     * origen externo y cualquier otro valor presente en Supabase.
     */
    public JSONObject getNexoFamiliaMetadataForLocalItem(long localItemId) {
        JSONObject out = new JSONObject();
        if (localItemId <= 0) return out;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT source_event_id,raw_json FROM nexo_familia_events WHERE local_item_id=? LIMIT 1",
                new String[]{String.valueOf(localItemId)})) {
            if (!c.moveToFirst()) return out;
            String sourceEventId = c.getString(0);
            String raw = c.getString(1);
            JSONObject source = new JSONObject(raw == null || raw.isBlank() ? "{}" : raw);
            out.put("sourceEventId", sourceEventId);
            out.put("source", source);

            JSONArray rules = new JSONArray();
            try (Cursor r = getReadableDatabase().rawQuery(
                    "SELECT raw_json FROM nexo_familia_reminders WHERE source_event_id=? ORDER BY minutes_before DESC,source_rule_id ASC",
                    new String[]{sourceEventId})) {
                while (r.moveToNext()) {
                    String ruleRaw = r.getString(0);
                    try { rules.put(new JSONObject(ruleRaw == null || ruleRaw.isBlank() ? "{}" : ruleRaw)); }
                    catch (Exception ignored) {}
                }
            }
            out.put("reminderRules", rules);
        } catch (Exception ignored) {}
        return out;
    }

    public JSONObject getNexoFamiliaImportStats() {
        JSONObject out = new JSONObject();
        try {
            out.put("events", scalar("SELECT COUNT(*) FROM nexo_familia_events nf JOIN items i ON i.id=nf.local_item_id WHERE i.deleted_at=0"));
            out.put("mappings", scalar("SELECT COUNT(*) FROM nexo_familia_events"));
            out.put("reminders", scalar("SELECT COUNT(*) FROM nexo_familia_reminders"));
            out.put("metadataComplete", scalar("SELECT COUNT(*) FROM nexo_familia_events WHERE raw_json<>'' AND raw_json<>'{}'"));
            out.put("brokenMappings", scalar("SELECT COUNT(*) FROM nexo_familia_events nf LEFT JOIN items i ON i.id=nf.local_item_id WHERE i.id IS NULL OR i.deleted_at<>0"));
            out.put("emptySourceIds", scalar("SELECT COUNT(*) FROM nexo_familia_events WHERE source_event_id=''"));
        } catch (Exception ignored) {}
        return out;
    }


    private static void createJournalTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS journal_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT NOT NULL DEFAULT 'thought'," +
                "title TEXT NOT NULL," +
                "summary TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "source TEXT NOT NULL DEFAULT 'manual'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_created ON journal_entries(created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_kind ON journal_entries(kind)");
        db.execSQL("CREATE TABLE IF NOT EXISTS journal_daily_pick (" +
                "day_key TEXT PRIMARY KEY," +
                "entry_id INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ")");
    }


    private static void createMedicationTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS medications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "dose TEXT NOT NULL DEFAULT ''," +
                "reason TEXT NOT NULL DEFAULT ''," +
                "instructions TEXT NOT NULL DEFAULT ''," +
                "schedule_mode TEXT NOT NULL DEFAULT 'once'," +
                "interval_minutes INTEGER NOT NULL DEFAULT 0," +
                "times_json TEXT NOT NULL DEFAULT '[]'," +
                "start_at INTEGER NOT NULL," +
                "end_at INTEGER NOT NULL DEFAULT 0," +
                "lead_minutes INTEGER NOT NULL DEFAULT 5," +
                "voice_enabled INTEGER NOT NULL DEFAULT 1," +
                "stock_qty REAL," +
                "stock_unit TEXT NOT NULL DEFAULT ''," +
                "low_stock_threshold REAL," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "source_text TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_medications_active ON medications(active,start_at,end_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS medication_doses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "medication_id INTEGER NOT NULL," +
                "scheduled_at INTEGER NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'pending'," +
                "action_at INTEGER NOT NULL DEFAULT 0," +
                "note TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(medication_id,scheduled_at)" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_medication_doses_med_time ON medication_doses(medication_id,scheduled_at)");
    }

    /** Evita duplicar la cita de ejemplo al actualizar desde versiones anteriores. */
    private static void cleanupSeedDuplicates(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM journal_entries WHERE source='seed-2026-08-21'", null)) {
            if (c.moveToFirst() && c.getLong(0) > 0) {
                db.delete("journal_entries", "source=? AND body=?", new String[]{"seed-journal-v24-0", "No puedes añadir días a tu vida, pero sí vida a tus días."});
            }
        } catch (Exception ignored) {}
    }

    /**
     * Sustituye las bibliotecas de frases incluidas por defecto en versiones anteriores.
     * Solo elimina registros identificados inequívocamente como semillas de Nexo; nunca toca entradas manuales.
     */
    private static void replaceDefaultJournalLibrary(SQLiteDatabase db) {
        try {
            db.delete("journal_daily_pick", null, null);
            db.delete("journal_entries", "source LIKE ? OR source LIKE ? OR source=? OR source LIKE ?",
                    new String[]{"seed-journal-v24-%", "seed-journal-v272-%", "seed-2026-08-21", "seed-journal-v294-%"});
            seedJournalBiblicalLibrary(db);
        } catch (Exception ignored) {}
    }

    /** Biblioteca de 40 frases de esperanza cristiana incluida por defecto desde Nexo 2.9.4. */
    private static void seedJournalBiblicalLibrary(SQLiteDatabase db) {
        try {
            String[][] seeds = new String[][]{
                    {"Luz para el siguiente paso", "No necesitas ver todo el camino; basta con tener luz para dar el siguiente paso.", "Inspirada en: Salmo 119,105 — «Lámpara es tu palabra para mis pasos, luz en mi sendero».", "Fe · Esperanza · Camino"},
                    {"Fuerza para atravesar", "Hay momentos en los que Dios no cambia las circunstancias, sino que te da fuerzas para atravesarlas.", "Inspirada en: Isaías 41,10 — Dios promete sostener y fortalecer a quien confía en Él.", "Fe · Fortaleza · Confianza"},
                    {"La historia conserva sentido", "Que hoy no entiendas lo que ocurre no significa que tu historia haya perdido su sentido.", "Inspirada en: Romanos 8,28 — «Todas las cosas cooperan para el bien de los que aman a Dios».", "Esperanza · Sentido · Confianza"},
                    {"Después de la noche", "Después de la noche siempre vuelve la mañana; ninguna oscuridad es definitiva.", "Inspirada en: Salmo 30,6 — «Al atardecer llega el llanto, y por la mañana, la alegría».", "Esperanza · Luz · Consuelo"},
                    {"Confiar sin saber", "La fe no consiste en saber qué sucederá, sino en confiar incluso cuando no lo sabes.", "Inspirada en: Proverbios 3,5-6 — «Confía en el Señor de todo corazón y no te apoyes en tu propia inteligencia».", "Fe · Confianza · Camino"},
                    {"Una etapa no es el destino", "No confundas una etapa difícil con un destino definitivo.", "Inspirada en: 2 Corintios 4,17-18 — Pablo contrapone la tribulación presente con aquello que permanece eternamente.", "Esperanza · Perspectiva · Fortaleza"},
                    {"También en el silencio", "Dios puede estar obrando también en aquello que ahora solo percibes como silencio.", "Inspirada en: Isaías 55,8-9 — Los caminos y pensamientos de Dios trascienden los nuestros.", "Fe · Silencio · Confianza"},
                    {"El pasado no decide el futuro", "Tu pasado explica parte de tu historia, pero no determina necesariamente tu futuro.", "Inspirada en: Isaías 43,18-19 — «No recordéis las cosas pasadas... Mirad que voy a hacer algo nuevo».", "Esperanza · Futuro · Renovación"},
                    {"No rendirse hoy", "A veces avanzar significa simplemente no rendirse hoy.", "Inspirada en: Gálatas 6,9 — «No nos cansemos de hacer el bien, porque a su tiempo cosecharemos si no desfallecemos».", "Constancia · Esperanza · Hoy"},
                    {"Una pequeña luz", "Incluso una pequeña luz puede romper una gran oscuridad.", "Inspirada en: Juan 1,5 — «La luz brilla en las tinieblas, y las tinieblas no la recibieron».", "Luz · Esperanza · Fe"},
                    {"Cada cosa a su tiempo", "No todo lo que tarda está perdido; algunas cosas necesitan tiempo para madurar.", "Inspirada en: Eclesiastés 3,1 — «Todo tiene su momento y cada cosa su tiempo bajo el cielo».", "Tiempo · Paciencia · Esperanza"},
                    {"Cuando terminan las fuerzas", "Cuando tus fuerzas terminan, todavía puedes elegir confiar.", "Inspirada en: 2 Corintios 12,9 — «Te basta mi gracia, porque la fuerza se perfecciona en la debilidad».", "Fe · Debilidad · Fortaleza"},
                    {"El sufrimiento no vence", "La esperanza cristiana no dice que todo será fácil, sino que el sufrimiento no tendrá la última palabra.", "Inspirada en: Romanos 8,18 — «Los sufrimientos del tiempo presente no son comparables con la gloria que se manifestará en nosotros».", "Esperanza · Sufrimiento · Fe"},
                    {"Comenzar de nuevo", "No tengas miedo de comenzar de nuevo: también los nuevos comienzos forman parte del camino.", "Inspirada en: Lamentaciones 3,22-23 — «Las misericordias del Señor no se han agotado... se renuevan cada mañana».", "Renovación · Esperanza · Camino"},
                    {"Puertas que se cierran", "Hay puertas que se cierran no porque todo termine, sino porque debes seguir caminando.", "Inspirada en: Hechos 16,6-10 — Pablo ve frustrados algunos de sus planes y acaba comprendiendo que debe dirigirse hacia Macedonia.", "Camino · Cambio · Confianza"},
                    {"La herida puede enseñar", "Lo que hoy parece una herida puede convertirse mañana en una fuente de sabiduría y compasión.", "Inspirada en: 2 Corintios 1,3-4 — Dios nos consuela en nuestras tribulaciones para que podamos consolar a otros.", "Consuelo · Sabiduría · Compasión"},
                    {"Pedir apoyo también es fortaleza", "No necesitas ser fuerte todos los días; también es fortaleza reconocer cuándo necesitas apoyo.", "Inspirada en: Eclesiastés 4,9-10 — «Más valen dos que uno... porque si uno cae, el otro levanta a su compañero».", "Apoyo · Fortaleza · Comunidad"},
                    {"No atravesar la tormenta solo", "Dios no promete una vida sin tormentas, pero la Biblia insiste en que no tenemos que atravesarlas solos.", "Inspirada en: Salmo 23,4 — «Aunque camine por valle de sombra de muerte, no temeré ningún mal, porque tú estás conmigo».", "Fe · Compañía · Fortaleza"},
                    {"Todavía hay camino", "Mientras puedas dar un paso más, todavía hay camino.", "Inspirada en: Filipenses 3,13-14 — Pablo habla de olvidar lo que queda atrás y lanzarse hacia lo que está por delante.", "Camino · Esperanza · Avance"},
                    {"La historia no ha terminado", "La esperanza comienza muchas veces con una decisión sencilla: creer que la historia todavía no ha terminado.", "Inspirada en: Romanos 5,3-5 — La tribulación produce paciencia, la paciencia virtud probada y esta esperanza; «y la esperanza no defrauda».", "Esperanza · Paciencia · Confianza"},
                    {"Afrontar el día sin miedo", "No tengas miedo de lo que viene; afronta cada día confiando en que no estás solo.", "Inspirada en: Mateo 28,20 — Jesús promete permanecer con sus discípulos «todos los días hasta el fin del mundo».", "Fe · Compañía · Confianza"},
                    {"Solo el desafío de hoy", "No cargues hoy con los problemas de mañana; cada día tiene ya su propio desafío.", "Inspirada en: Mateo 6,34 — Jesús invita a no vivir dominados por la preocupación por el futuro.", "Presente · Confianza · Serenidad"},
                    {"También puedes descansar", "Cuando sientas que ya no puedes más, recuerda que también puedes descansar.", "Inspirada en: Mateo 11,28 — «Venid a mí todos los fatigados y agobiados, y yo os aliviaré».", "Descanso · Consuelo · Fe"},
                    {"Paz en medio de los problemas", "La paz verdadera no depende siempre de que desaparezcan los problemas.", "Inspirada en: Juan 14,27 — Jesús ofrece a sus discípulos una paz distinta de la que puede ofrecer el mundo.", "Paz · Fe · Confianza"},
                    {"No tengas miedo", "Incluso en medio de la tormenta puede haber una voz que te diga: “No tengas miedo”.", "Inspirada en: Marcos 6,47-50 — Jesús se acerca a los discípulos en medio del mar y les dice que tengan confianza y no teman.", "Fe · Tormenta · Confianza"},
                    {"Nada es imposible para Dios", "Lo que hoy parece imposible puede no serlo para Dios.", "Inspirada en: Lucas 1,37 — «Porque para Dios nada hay imposible».", "Fe · Esperanza · Posibilidad"},
                    {"Mirado con misericordia", "Una vida puede cambiar profundamente cuando alguien se sabe mirado con misericordia.", "Inspirada en: Lucas 19,1-10 — El encuentro de Jesús con Zaqueo transforma su manera de vivir.", "Misericordia · Cambio · Vida"},
                    {"Siempre hay un camino de regreso", "Siempre existe un camino de regreso para quien decide levantarse y volver.", "Inspirada en: Lucas 15,11-32 — La parábola del hijo pródigo presenta a un padre que recibe al hijo que regresa.", "Regreso · Misericordia · Esperanza"},
                    {"Las caídas no son el final", "Tus caídas no tienen por qué ser el final de tu historia.", "Inspirada en: Juan 21,15-19 — Después de haber negado a Jesús, Pedro es restaurado y vuelve a recibir una misión.", "Esperanza · Restauración · Misión"},
                    {"Atreverse a pedir ayuda", "A veces la fe comienza simplemente atreviéndose a pedir ayuda.", "Inspirada en: Marcos 9,24 — «Creo; ayuda mi incredulidad».", "Fe · Ayuda · Confianza"},
                    {"Una fe pequeña puede caminar", "No necesitas tener una fe perfecta; incluso una fe pequeña puede ponerse en camino.", "Inspirada en: Mateo 17,20 — Jesús utiliza la imagen de la fe «como un grano de mostaza».", "Fe · Camino · Confianza"},
                    {"Dar espacio a la paz", "No permitas que tus preocupaciones ocupen todo el espacio de tu vida.", "Inspirada en: Filipenses 4,6-7 — Pablo invita a presentar las preocupaciones ante Dios y habla de una paz que supera toda comprensión.", "Paz · Preocupación · Confianza"},
                    {"Aprender a vivir en cada circunstancia", "Aprender a estar bien no significa tenerlo todo, sino aprender a vivir también cuando las circunstancias cambian.", "Inspirada en: Filipenses 4,11-13 — Pablo afirma haber aprendido a vivir tanto en la abundancia como en la necesidad.", "Serenidad · Cambio · Fortaleza"},
                    {"La misión continúa", "Cuando una puerta se cierre, tu misión puede continuar por otro camino.", "Inspirada en: Hechos 16,6-10 — Los planes de Pablo cambian varias veces hasta que dirige su misión hacia Macedonia.", "Misión · Camino · Cambio"},
                    {"Golpeado, no destruido", "El sufrimiento puede golpearte, pero no necesariamente destruirte.", "Inspirada en: 2 Corintios 4,8-9 — Pablo habla de estar atribulados, perplejos y perseguidos, pero no vencidos.", "Fortaleza · Sufrimiento · Esperanza"},
                    {"Fuerza en la debilidad", "Tu debilidad no te hace inútil; también desde ella puede manifestarse una fuerza que no es solo tuya.", "Inspirada en: 2 Corintios 12,9-10 — «Mi poder se manifiesta plenamente en la debilidad».", "Debilidad · Fortaleza · Gracia"},
                    {"Que el mal no te defina", "No dejes que el mal determine en qué clase de persona te conviertes.", "Inspirada en: Romanos 12,21 — «No te dejes vencer por el mal; al contrario, vence el mal con el bien».", "Bien · Carácter · Esperanza"},
                    {"El dolor no tiene la última palabra", "La esperanza cristiana no consiste en negar el dolor, sino en creer que el dolor no posee la última palabra.", "Inspirada en: Apocalipsis 21,4 — Se anuncia un futuro en el que «ya no habrá muerte, ni duelo, ni llanto, ni dolor».", "Esperanza · Dolor · Consuelo"},
                    {"Caer y seguir teniendo misión", "Puedes caer muchas veces y seguir teniendo una misión.", "Inspirada en: Lucas 22,31-32 — Antes incluso de la negación de Pedro, Jesús le anuncia su caída y, al mismo tiempo, le confía la tarea de fortalecer a sus hermanos.", "Misión · Restauración · Esperanza"},
                    {"El amor permanece", "El amor es una de las pocas cosas que nunca pierde verdaderamente su valor.", "Inspirada en: 1 Corintios 13,7-8.13 — Pablo describe un amor que persevera y concluye que permanecen la fe, la esperanza y el amor, siendo el amor el mayor de ellos.", "Amor · Fe · Esperanza"}
            };
            long base = System.currentTimeMillis();
            for (int i = 0; i < seeds.length; i++) {
                String source = "seed-journal-v294-" + i;
                try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM journal_entries WHERE source=?", new String[]{source})) {
                    if (c.moveToFirst() && c.getLong(0) > 0) continue;
                }
                ContentValues v = new ContentValues();
                v.put("kind", "quote");
                v.put("title", seeds[i][0]);
                v.put("summary", seeds[i][2]);
                v.put("body", seeds[i][1]);
                v.put("tags", seeds[i][3]);
                v.put("favorite", 0);
                v.put("source", source);
                long created = base + (i * 1000L);
                v.put("created_at", created);
                v.put("updated_at", created);
                db.insert("journal_entries", null, v);
            }
        } catch (Exception ignored) {}
    }

    public long insertItem(JSONObject in) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        String itemType = in.optString("type", "note");
        v.put("type", itemType);
        v.put("title", nonBlank(in.optString("title"), "Nota rápida"));
        v.put("body", in.optString("body", ""));
        v.put("original_text", in.optString("originalText", ""));
        v.put("category", nonBlank(in.optString("category"), "Otros"));
        v.put("subcategory", in.optString("subcategory", ""));
        v.put("tags", in.optString("tags", ""));
        long dueAt = Math.max(0, in.optLong("dueAt", 0));
        boolean hasTime = in.optBoolean("hasTime", false);
        int durationMinutes = Math.max(0, in.optInt("durationMinutes", 0));
        long endAt = Math.max(0, in.optLong("endAt", 0));
        if (endAt <= 0 && dueAt > 0) endAt = durationMinutes > 0 ? dueAt + durationMinutes * 60000L : dueAt;
        boolean allDay = in.has("allDay") ? in.optBoolean("allDay") :
                (dueAt > 0 && !hasTime && isAppointmentTypeForStatus(itemType));
        v.put("due_at", dueAt);
        v.put("end_at", endAt);
        v.put("has_time", hasTime ? 1 : 0);
        v.put("all_day", allDay ? 1 : 0);
        v.put("time_zone", nonBlank(in.optString("timeZone"), "Europe/Madrid"));
        v.put("maps_url", in.optString("mapsUrl", ""));
        v.put("recurrence_rule", in.optString("recurrenceRule", ""));
        v.put("participants_text", in.optString("participantsText", ""));
        v.put("deleted_at", 0);
        if (in.has("lat") && !in.isNull("lat")) v.put("lat", in.optDouble("lat"));
        if (in.has("lon") && !in.isNull("lon")) v.put("lon", in.optDouble("lon"));
        v.put("location_text", in.optString("locationText", ""));
        v.put("priority", clamp(in.optInt("priority", 1), 1, 3));
        v.put("favorite", in.optBoolean("favorite", false) ? 1 : 0);
        v.put("source", in.optString("source", "manual"));
        v.put("attachment_path", in.optString("attachmentPath", ""));
        v.put("attachment_name", in.optString("attachmentName", ""));
        v.put("attachment_mime", in.optString("attachmentMime", ""));
        String status = in.optString("status", in.optString("type", "note").equals("task") ? "pending" : "active");
        v.put("status", status);
        v.put("needs_review", in.optBoolean("needsReview", false) ? 1 : 0);
        v.put("recurrence", RecurrenceEngine.normalize(in.optString("recurrence", RecurrenceEngine.NONE)));
        v.put("recurrence_until", Math.max(0, in.optLong("recurrenceUntil", 0)));
        v.put("duration_minutes", durationMinutes);
        v.put("voice_enabled", in.optBoolean("voiceEnabled", false) ? 1 : 0);
        v.put("note_title_manual", in.has("noteTitleManual") ? (in.optBoolean("noteTitleManual") ? 1 : 0) : ("note".equals(itemType) ? 1 : 0));
        v.put("created_at", now);
        v.put("updated_at", now);
        long id = getWritableDatabase().insertOrThrow("items", null, v);
        addHistory("guardado", in.optString("type", "note"), id, in.optString("title", "Nota rápida"));
        return id;
    }

    public boolean updateItem(JSONObject in) {
        long id = in.optLong("id", 0);
        if (id <= 0) return false;
        JSONObject old = getItem(id);
        ContentValues v = new ContentValues();
        if (in.has("title")) v.put("title", nonBlank(in.optString("title"), "Sin título"));
        if (in.has("body")) v.put("body", in.optString("body", ""));
        if (in.has("originalText")) v.put("original_text", in.optString("originalText", ""));
        if (in.has("category")) v.put("category", nonBlank(in.optString("category"), "Otros"));
        if (in.has("subcategory")) v.put("subcategory", in.optString("subcategory", ""));
        if (in.has("attachmentPath")) v.put("attachment_path", in.optString("attachmentPath", ""));
        if (in.has("attachmentName")) v.put("attachment_name", in.optString("attachmentName", ""));
        if (in.has("attachmentMime")) v.put("attachment_mime", in.optString("attachmentMime", ""));
        if (in.has("type")) {
            String newType = in.optString("type", "note");
            v.put("type", newType);
            String oldType = old.optString("type", "note");
            if (!in.has("status") && !oldType.equals(newType)) {
                v.put("status", "task".equals(newType) ? "pending" : "active");
            }
        }
        if (in.has("dueAt")) {
            long newDue=Math.max(0,in.optLong("dueAt",0));
            v.put("due_at",newDue);
            if(!in.has("endAt")) {
                long oldDue=old.optLong("dueAt",0),oldEnd=old.optLong("endAt",0);
                long span=Math.max(0L,oldEnd-oldDue);
                if(span<=0&&old.optInt("durationMinutes",0)>0)span=old.optInt("durationMinutes",0)*60000L;
                v.put("end_at",newDue>0?newDue+span:0L);
            }
        }
        if (in.has("endAt")) v.put("end_at", Math.max(0, in.optLong("endAt", 0)));
        if (in.has("hasTime")) v.put("has_time", in.optBoolean("hasTime") ? 1 : 0);
        if (in.has("allDay")) v.put("all_day", in.optBoolean("allDay") ? 1 : 0);
        if (in.has("timeZone")) v.put("time_zone", nonBlank(in.optString("timeZone"), "Europe/Madrid"));
        if (in.has("mapsUrl")) v.put("maps_url", in.optString("mapsUrl", ""));
        if (in.has("recurrenceRule")) v.put("recurrence_rule", in.optString("recurrenceRule", ""));
        if (in.has("participantsText")) v.put("participants_text", in.optString("participantsText", ""));
        if (in.has("locationText")) v.put("location_text", in.optString("locationText", ""));
        if (in.has("priority")) v.put("priority", clamp(in.optInt("priority", 1), 1, 3));
        if (in.has("favorite")) v.put("favorite", in.optBoolean("favorite") ? 1 : 0);
        if (in.has("status")) v.put("status", in.optString("status", "active"));
        if (in.has("needsReview")) v.put("needs_review", in.optBoolean("needsReview") ? 1 : 0);
        if (in.has("recurrence")) v.put("recurrence", RecurrenceEngine.normalize(in.optString("recurrence", RecurrenceEngine.NONE)));
        if (in.has("recurrenceUntil")) v.put("recurrence_until", Math.max(0, in.optLong("recurrenceUntil", 0)));
        if (in.has("durationMinutes")) v.put("duration_minutes", Math.max(0, in.optInt("durationMinutes", 0)));
        if (in.has("voiceEnabled")) v.put("voice_enabled", in.optBoolean("voiceEnabled") ? 1 : 0);
        if (in.has("noteTitleManual")) v.put("note_title_manual", in.optBoolean("noteTitleManual") ? 1 : 0);
        // Reprogramar una cita/tarea decide de nuevo su estado temporal: hoy/futuro = pendiente, pasado = finalizada.
        if (in.has("dueAt") && !in.has("status")) {
            String effectiveType=in.optString("type",old.optString("type","note"));
            long newDue=Math.max(0,in.optLong("dueAt",0));
            long oldDue=Math.max(0,old.optLong("dueAt",0));
            if(newDue>0 && newDue!=oldDue && isAppointmentTypeForStatus(effectiveType) && !RecurrenceEngine.isRecurring(in.optString("recurrence",old.optString("recurrence",RecurrenceEngine.NONE)))) {
                v.put("status", isBeforeToday(newDue) ? "done" : "pending");
            }
        }
        v.put("updated_at", System.currentTimeMillis());
        int rows = getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(id)});
        if (rows > 0) {
            String oldCat = old.optString("category");
            String newCat = in.optString("category", oldCat);
            if (!newCat.equals(oldCat)) learnFromCorrection(old.optString("title") + " " + old.optString("body"), newCat);
            addHistory("actualizado", in.optString("type", old.optString("type", "item")), id, in.optString("title", old.optString("title")));
        }
        return rows > 0;
    }

    public JSONObject getItem(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=? AND deleted_at=0", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return new JSONObject();
            JSONObject item = itemJson(c);
            if ("nexo-familia".equals(item.optString("source", ""))) {
                JSONObject metadata = getNexoFamiliaMetadataForLocalItem(id);
                if (metadata.length() > 0) item.put("nexoFamilia", metadata);
            }
            return item;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    /** Concilia estados dependientes de la fecha sin tocar cancelados/omitidos. */
    public synchronized void reconcileTemporalStatuses() {
        SQLiteDatabase db=getWritableDatabase();
        long now=System.currentTimeMillis();
        long start=LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Los elementos locales normales conservan la regla histórica: un evento se
        // completa al quedar su día anterior en el pasado. Los eventos importados
        // desde Nexo Familia se excluyen de esta actualización genérica porque su
        // estado se reconstruye a partir del estado fuente y del epoch real.
        ContentValues done=new ContentValues();done.put("status","done");done.put("updated_at",now);
        db.update("items",done,
                "deleted_at=0 AND source<>'nexo-familia' AND due_at>0 " +
                "AND (CASE WHEN end_at>0 THEN end_at ELSE due_at END)<? " +
                "AND recurrence='none' AND type IN ('event','task','reminder','expiry') " +
                "AND status IN ('active','pending','postponed')",
                new String[]{String.valueOf(start)});

        // Nexo Familia: nunca se deduce el estado por visibility. Se respeta
        // cancelled/completed de Supabase; los demás quedan pending mientras su
        // final real no haya pasado. Esto evita que los eventos selected futuros
        // desaparezcan de Agenda al ser marcados como done por una conciliación local.
        try {
            db.execSQL(
                    "UPDATE items SET status=CASE " +
                    "WHEN lower(COALESCE((SELECT nf.source_status FROM nexo_familia_events nf WHERE nf.local_item_id=items.id LIMIT 1),''))='cancelled' THEN 'cancelled' " +
                    "WHEN lower(COALESCE((SELECT nf.source_status FROM nexo_familia_events nf WHERE nf.local_item_id=items.id LIMIT 1),''))='completed' THEN 'done' " +
                    "WHEN recurrence='none' AND due_at>0 AND (CASE WHEN end_at>0 THEN end_at ELSE due_at END)<? THEN 'done' " +
                    "ELSE 'pending' END, updated_at=? " +
                    "WHERE deleted_at=0 AND source='nexo-familia'",
                    new Object[]{now,now});
        } catch (Exception ignored) {}

        // Los cumpleaños locales (no procedentes de Nexo Familia) se evalúan contra
        // la aparición del año actual. Hoy todavía queda pendiente.
        try(Cursor c=db.rawQuery(
                "SELECT id,due_at,status,recurrence,recurrence_until FROM items " +
                "WHERE deleted_at=0 AND source<>'nexo-familia' AND due_at>0 " +
                "AND status<>'cancelled' AND lower(category) IN ('cumpleanos','cumpleaños','cumple')",
                null)){
            ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now(zone);
            long yearStart=LocalDate.of(today.getYear(),1,1).atStartOfDay(zone).toInstant().toEpochMilli();
            long yearEnd=LocalDate.of(today.getYear()+1,1,1).atStartOfDay(zone).toInstant().toEpochMilli()-1;
            while(c.moveToNext()){
                long id=c.getLong(0),at=c.getLong(1),until=c.getLong(4);String status=c.getString(2),recurrence=c.getString(3);
                if(RecurrenceEngine.isRecurring(recurrence)){
                    if("done".equals(status)){
                        ContentValues baseOpen=new ContentValues();baseOpen.put("status","pending");baseOpen.put("updated_at",System.currentTimeMillis());
                        db.update("items",baseOpen,"id=?",new String[]{String.valueOf(id)});
                    }
                    List<Long> occs=RecurrenceEngine.occurrencesBetween(at,recurrence,until,yearStart,yearEnd,zone,20);
                    if(!occs.isEmpty()){
                        long key=occs.get(0);LocalDate d=Instant.ofEpochMilli(key).atZone(zone).toLocalDate();String wanted=d.isBefore(today)?"done":"active";
                        JSONObject ex=getRecurrenceException(id,key);String current=ex.length()==0?"active":ex.optString("state","active");
                        if(!wanted.equals(current))setOccurrenceStatus(id,key,wanted);
                    }
                }else{
                    LocalDate stored=Instant.ofEpochMilli(at).atZone(zone).toLocalDate();LocalDate thisYear;
                    try{thisYear=LocalDate.of(today.getYear(),stored.getMonthValue(),stored.getDayOfMonth());}catch(DateTimeException e){thisYear=LocalDate.of(today.getYear(),2,28);}
                    String wanted=thisYear.isBefore(today)?"done":"pending";
                    if(!wanted.equals(status)){ContentValues v=new ContentValues();v.put("status",wanted);v.put("updated_at",System.currentTimeMillis());db.update("items",v,"id=?",new String[]{String.valueOf(id)});}
                }
            }
        }catch(Exception ignored){}
    }

    private static boolean isAppointmentTypeForStatus(String type){return "event".equals(type)||"task".equals(type)||"reminder".equals(type)||"expiry".equals(type);}
    private static boolean isBeforeToday(long at){if(at<=0)return false;ZoneId z=ZoneId.systemDefault();return Instant.ofEpochMilli(at).atZone(z).toLocalDate().isBefore(LocalDate.now(z));}

    public JSONObject getTodayData() {
        reconcileTemporalStatuses();
        JSONObject out = new JSONObject();
        try {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            long start = today.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
            long next14 = today.plusDays(14).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
            long next7 = today.plusDays(7).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
            long past30 = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli();

            JSONArray todayAll = getCalendar(start, end);
            JSONArray todayTasks = new JSONArray(), todayItems = new JSONArray();
            for (int i=0;i<todayAll.length();i++) {
                JSONObject o=todayAll.optJSONObject(i); if(o==null)continue;
                String status=o.optString("status","active");
                if ("task".equals(o.optString("type"))) {
                    if (!"done".equals(status) && !"deleted".equals(o.optString("occurrenceStatus"))) todayTasks.put(o);
                } else if ("active".equals(status)||"pending".equals(status)||"postponed".equals(status)) todayItems.put(o);
            }
            out.put("todayTasks", todayTasks);
            out.put("todayItems", todayItems);

            JSONArray past = getCalendar(past30, start-1), overdue = new JSONArray();
            for (int i=0;i<past.length() && overdue.length()<20;i++) {
                JSONObject o=past.optJSONObject(i); if(o==null)continue;
                String type = o.optString("type");
                if (("event".equals(type) || "task".equals(type) || "reminder".equals(type) || "expiry".equals(type))
                        && !"done".equals(o.optString("status")) && !"cancelled".equals(o.optString("status")) && !"deleted".equals(o.optString("occurrenceStatus"))) overdue.put(o);
            }
            out.put("overdueTasks", overdue);

            JSONArray future = getCalendar(end+1, next14), upcoming = new JSONArray();
            for (int i=0;i<future.length() && upcoming.length()<20;i++) {
                JSONObject o=future.optJSONObject(i); if(o==null)continue;
                String status=o.optString("status","active");
                if (!"done".equals(status) && !"cancelled".equals(status) && !"deleted".equals(o.optString("occurrenceStatus"))) upcoming.put(o);
            }
            out.put("upcoming", upcoming);
            out.put("important", queryItems("priority=3 AND (status='active' OR status='pending')", null, "due_at ASC, updated_at DESC", 8));
            out.put("recent", queryItems("type<>'parking'", null, "created_at DESC", 6));
            out.put("reminders", queryReminders(System.currentTimeMillis(), next7, 12));
            out.put("pendingTasks", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND type IN ('event','task','reminder','expiry') AND status NOT IN ('done','cancelled')"));
            out.put("inboxCount", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND needs_review=1"));
            out.put("lastParking", getLastParking());
        } catch (Exception ignored) {}
        return out;
    }

    /** Bloc de notas: notas locales sin fecha, ordenadas de más nuevas a más antiguas. */
    public JSONArray getNotes() {
        return queryItems("type='note' AND status='active'", null, "created_at DESC", 500);
    }

    /** Guarda una nota del bloc sin convertirla en evento ni añadirla al calendario. */
    public long insertNote(JSONObject in) {
        try {
            JSONObject note = new JSONObject(in.toString());
            note.put("type", "note");
            note.put("category", nonBlank(note.optString("category"), "Notas"));
            note.put("dueAt", 0);
            note.put("hasTime", false);
            note.put("recurrence", RecurrenceEngine.NONE);
            note.put("recurrenceUntil", 0);
            note.put("status", "active");
            note.put("needsReview", false);
            note.put("source", nonBlank(note.optString("source"), "notepad"));
            if (!note.has("noteTitleManual")) note.put("noteTitleManual", false);
            return insertItem(note);
        } catch (Exception e) { return 0; }
    }

    /** Edita únicamente contenido del bloc; conserva la fecha original de creación. */
    public boolean updateNote(JSONObject in) {
        long id = in.optLong("id", 0);
        if (id <= 0) return false;
        JSONObject current = getItem(id);
        if (current.length() == 0 || !"note".equals(current.optString("type"))) return false;
        try {
            JSONObject note = new JSONObject(in.toString());
            note.put("type", "note");
            note.put("category", nonBlank(note.optString("category", current.optString("category")), "Notas"));
            note.put("dueAt", 0);
            note.put("hasTime", false);
            note.put("recurrence", RecurrenceEngine.NONE);
            note.put("recurrenceUntil", 0);
            note.put("status", "active");
            note.put("needsReview", false);
            return updateItem(note);
        } catch (Exception e) { return false; }
    }

    /**
     * Autoguardado silencioso del Bloc de notas. Evita llenar el historial con una
     * entrada por cada pausa al escribir. Solo toca título, cuerpo, origen manual
     * del título y updated_at.
     */
    public boolean autosaveNote(JSONObject in) {
        long id = in.optLong("id", 0);
        if (id <= 0) return false;
        JSONObject current = getItem(id);
        if (current.length() == 0 || !"note".equals(current.optString("type"))) return false;
        ContentValues v = new ContentValues();
        if (in.has("title")) v.put("title", nonBlank(in.optString("title"), current.optString("title", "Nota rápida")));
        if (in.has("body")) v.put("body", in.optString("body", ""));
        if (in.has("noteTitleManual")) v.put("note_title_manual", in.optBoolean("noteTitleManual") ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("items", v, "id=? AND type='note' AND deleted_at=0", new String[]{String.valueOf(id)}) > 0;
    }

    public JSONArray getTasks(String mode) {
        reconcileTemporalStatuses();
        String where = "type='task'";
        if ("pending".equals(mode)) where += " AND status='pending'";
        else if ("done".equals(mode)) where += " AND status='done'";
        return queryItems(where, null, "CASE WHEN status='pending' THEN 0 ELSE 1 END, due_at ASC, created_at DESC", 200);
    }

    /** One row per appointment. Existing task/reminder/expiry rows keep their ids and alert rules. */
    public JSONArray getAppointments() {
        reconcileTemporalStatuses();
        return queryItems("type IN ('event','task','reminder','expiry')", null,
                "CASE WHEN status IN ('done','cancelled') THEN 1 ELSE 0 END, CASE WHEN due_at=0 THEN 1 ELSE 0 END, due_at ASC, created_at DESC", -1);
    }

    public boolean setTaskStatus(long id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("updated_at", System.currentTimeMillis());
        int rows = getWritableDatabase().update("items", v, "id=? AND type='task' AND deleted_at=0", new String[]{String.valueOf(id)});
        if (rows > 0) addHistory(status.equals("done") ? "tarea realizada" : "tarea actualizada", "task", id, getItem(id).optString("title"));
        return rows > 0;
    }

    /** Compatibilidad con versiones anteriores que expresaban la anticipación en días. */
    public long insertReminder(long itemId, String title, long triggerAt, int leadDays) {
        return insertReminderMinutes(itemId, title, triggerAt, Math.max(0, leadDays) * 1440, 0L);
    }

    /** Compatibilidad con llamadas antiguas de series recurrentes. */
    public long insertReminder(long itemId, String title, long triggerAt, int leadDays, long occurrenceAt) {
        return insertReminderMinutes(itemId, title, triggerAt, Math.max(0, leadDays) * 1440, occurrenceAt);
    }

    /** Regla de aviso en minutos. Permite 15 min, horas, días, semanas, etc. */
    public long insertReminderMinutes(long itemId, String title, long triggerAt, int leadMinutes, long occurrenceAt) {
        int safeMinutes = Math.max(0, leadMinutes);
        ContentValues v = new ContentValues();
        v.put("item_id", itemId);
        v.put("title", title);
        v.put("trigger_at", triggerAt);
        v.put("occurrence_at", Math.max(0, occurrenceAt));
        v.put("lead_days", safeMinutes / 1440); // columna histórica, conservada por compatibilidad
        v.put("lead_minutes", safeMinutes);
        v.put("enabled", 1);
        v.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertOrThrow("reminders", null, v);
        addHistory("recordatorio creado", "reminder", id, title);
        return id;
    }

    /** Devuelve las reglas de aviso de un elemento, estén o no activas en este instante. */
    public JSONArray getReminderRulesForItem(long itemId) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,item_id,title,trigger_at,occurrence_at,lead_days,lead_minutes,enabled,created_at FROM reminders WHERE item_id=? ORDER BY lead_minutes ASC,id ASC",
                new String[]{String.valueOf(itemId)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id",c.getLong(0)); o.put("itemId",c.getLong(1)); o.put("title",c.getString(2));
                o.put("triggerAt",c.getLong(3)); o.put("occurrenceAt",c.getLong(4)); o.put("leadDays",c.getInt(5));
                o.put("leadMinutes",c.getInt(6)); o.put("enabled",c.getInt(7)==1); o.put("createdAt",c.getLong(8));
                a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public boolean deleteRemindersForItem(long itemId) {
        return getWritableDatabase().delete("reminders", "item_id=?", new String[]{String.valueOf(itemId)}) >= 0;
    }

    /** Recalcula todos los avisos de un elemento desde su próxima aparición vigente. */
    public JSONArray rescheduleRemindersForItem(long itemId) {
        JSONArray out = new JSONArray();
        JSONObject item = getItem(itemId);
        SQLiteDatabase db = getWritableDatabase();
        if (item.length() == 0 || item.optLong("dueAt", 0) <= 0) {
            ContentValues off = new ContentValues(); off.put("enabled", 0);
            db.update("reminders", off, "item_id=?", new String[]{String.valueOf(itemId)});
            return out;
        }
        try (Cursor c = db.rawQuery("SELECT id,title,lead_minutes FROM reminders WHERE item_id=?", new String[]{String.valueOf(itemId)})) {
            while (c.moveToNext()) {
                long id = c.getLong(0); String title = item.optString("title", c.getString(1)); int leadMinutes = Math.max(0,c.getInt(2));
                long occurrence = findNextOccurrence(itemId, reminderSearchAfter(item));
                if (occurrence <= 0) {
                    ContentValues v = new ContentValues(); v.put("enabled", 0); db.update("reminders", v, "id=?", new String[]{String.valueOf(id)});
                    continue;
                }
                JSONObject occ = getOccurrence(itemId, occurrence);
                title = occ.optString("title", title);
                long actualDue = occ.optLong("dueAt", occurrence);
                boolean hasTime = occ.optBoolean("hasTime", item.optBoolean("hasTime", false));
                long trigger = reminderTriggerMinutes(actualDue, leadMinutes, hasTime);
                ContentValues v = new ContentValues();
                v.put("title", title); v.put("occurrence_at", occurrence); v.put("trigger_at", trigger); v.put("enabled", trigger > System.currentTimeMillis() ? 1 : 0);
                db.update("reminders", v, "id=?", new String[]{String.valueOf(id)});
                if (trigger > System.currentTimeMillis()) {
                    JSONObject o = new JSONObject(); o.put("id", id); o.put("title", title); o.put("triggerAt", trigger); o.put("occurrenceAt", occurrence); o.put("leadMinutes",leadMinutes); out.put(o);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Compatibilidad con llamadas de la versión 1.1. */
    public JSONArray rescheduleRemindersForItem(long itemId, long dueAt, boolean hasTime) {
        return rescheduleRemindersForItem(itemId);
    }

    /** Anticipaciones exactas en minutos. Para fechas sin hora conserva el comportamiento seguro de las 09:00. */
    private static long reminderTriggerMinutes(long dueAt, int leadMinutes, boolean hasTime) {
        if (dueAt <= 0) return 0;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime due = Instant.ofEpochMilli(dueAt).atZone(zone);
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime trigger = due.minusMinutes(Math.max(0, leadMinutes));
        if (trigger.isAfter(now)) return trigger.toInstant().toEpochMilli();
        if (due.isAfter(now)) return due.toInstant().toEpochMilli();
        if (!hasTime && due.toLocalDate().equals(now.toLocalDate())) return System.currentTimeMillis() + 60_000L;
        return trigger.toInstant().toEpochMilli();
    }

    private static long reminderSearchAfter(JSONObject item) {
        if (item != null && !item.optBoolean("hasTime", false)) {
            ZoneId zone=ZoneId.systemDefault();
            return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()-1;
        }
        return System.currentTimeMillis()-1;
    }

    public JSONArray getActiveRemindersForItem(long itemId) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,item_id,title,trigger_at,occurrence_at,lead_days,lead_minutes,enabled,created_at FROM reminders WHERE item_id=? AND enabled=1 ORDER BY trigger_at ASC",
                new String[]{String.valueOf(itemId)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id",c.getLong(0)); o.put("itemId",c.getLong(1)); o.put("title",c.getString(2));
                o.put("triggerAt",c.getLong(3)); o.put("occurrenceAt",c.getLong(4)); o.put("leadDays",c.getInt(5)); o.put("leadMinutes",c.getInt(6)); o.put("enabled",true); o.put("createdAt",c.getLong(8));
                a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    /** Datos necesarios para un aviso hablado. La voz pertenece al elemento, no a la alarma física. */
    public JSONObject getReminderVoiceData(long reminderId) {
        JSONObject out = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT item_id,title,occurrence_at,lead_minutes FROM reminders WHERE id=?", new String[]{String.valueOf(reminderId)})) {
            if (!c.moveToFirst()) return out;
            long itemId=c.getLong(0), occurrenceAt=c.getLong(2);
            JSONObject item=getItem(itemId);
            if (item.length()==0) return out;
            JSONObject occurrence=(occurrenceAt>0)?getOccurrence(itemId,occurrenceAt):item;
            if (occurrence.length()==0) occurrence=item;
            out.put("itemId",itemId);
            out.put("title",occurrence.optString("title",c.getString(1)));
            out.put("type",occurrence.optString("type",item.optString("type","reminder")));
            out.put("dueAt",occurrence.optLong("dueAt",item.optLong("dueAt",0)));
            out.put("hasTime",occurrence.optBoolean("hasTime",item.optBoolean("hasTime",false)));
            out.put("leadMinutes",c.getInt(3));
            out.put("voiceEnabled",item.optBoolean("voiceEnabled",false));
        } catch(Exception ignored) {}
        return out;
    }

    public boolean setReminderEnabled(long reminderId, boolean enabled) {
        ContentValues v = new ContentValues(); v.put("enabled", enabled ? 1 : 0);
        return getWritableDatabase().update("reminders", v, "id=?", new String[]{String.valueOf(reminderId)}) > 0;
    }

    /** Tras disparar un aviso recurrente, avanza la misma alarma a la siguiente aparición. */
    public JSONObject advanceRecurringReminder(long reminderId) {
        JSONObject out = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT item_id,title,lead_minutes,occurrence_at FROM reminders WHERE id=?", new String[]{String.valueOf(reminderId)})) {
            if (!c.moveToFirst()) return out;
            long itemId = c.getLong(0); String title = c.getString(1); int leadMinutes = Math.max(0,c.getInt(2)); long currentOccurrence = c.getLong(3);
            JSONObject item = getItem(itemId);
            if (!RecurrenceEngine.isRecurring(item.optString("recurrence", RecurrenceEngine.NONE))) {
                ContentValues off = new ContentValues(); off.put("enabled", 0); getWritableDatabase().update("reminders", off, "id=?", new String[]{String.valueOf(reminderId)});
                return out;
            }
            long next = findNextOccurrence(itemId, Math.max(currentOccurrence, System.currentTimeMillis() - 1));
            if (next <= 0) {
                ContentValues off = new ContentValues(); off.put("enabled", 0); getWritableDatabase().update("reminders", off, "id=?", new String[]{String.valueOf(reminderId)});
                return out;
            }
            JSONObject occ = getOccurrence(itemId, next);
            long due = occ.optLong("dueAt", next);
            long trigger = reminderTriggerMinutes(due, leadMinutes, occ.optBoolean("hasTime", item.optBoolean("hasTime", false)));
            if (trigger <= System.currentTimeMillis()) return out;
            ContentValues v = new ContentValues(); v.put("occurrence_at", next); v.put("trigger_at", trigger); v.put("enabled", 1);
            getWritableDatabase().update("reminders", v, "id=?", new String[]{String.valueOf(reminderId)});
            out.put("id", reminderId); out.put("title", title); out.put("triggerAt", trigger); out.put("occurrenceAt", next); out.put("leadMinutes",leadMinutes);
        } catch (Exception ignored) {}
        return out;
    }

    public boolean setItemDeleted(long id, boolean deleted) {
        JSONObject old = deleted ? getItem(id) : getItemIncludingDeleted(id);
        if (old.length() == 0) return false;
        ContentValues v = new ContentValues();
        v.put("deleted_at", deleted ? System.currentTimeMillis() : 0);
        v.put("updated_at", System.currentTimeMillis());
        int rows = getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(id)});
        if (rows > 0) addHistory(deleted ? "eliminado" : "restaurado", old.optString("type","item"), id, old.optString("title","Elemento"));
        return rows > 0;
    }

    public JSONArray getDeletedItems() {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE deleted_at>0 ORDER BY deleted_at ASC", null)) {
            while (c.moveToNext()) a.put(itemJson(c));
        } catch (Exception ignored) {}
        return a;
    }

    public JSONObject getItemIncludingDeleted(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? itemJson(c) : new JSONObject();
        }
    }

    public boolean purgeDeletedItem(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("reminders", "item_id=?", new String[]{String.valueOf(id)});
            db.delete("recurrence_exceptions", "series_id=?", new String[]{String.valueOf(id)});
            int rows = db.delete("items", "id=? AND deleted_at>0", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return rows > 0;
        } finally { db.endTransaction(); }
    }


    /**
     * Devuelve todas las apariciones fechadas de Nexo en un rango. Las series se expanden
     * en memoria; SQLite conserva solo la regla y las excepciones.
     */
    public JSONArray getCalendar(long start, long end) {
        ArrayList<JSONObject> rows = new ArrayList<>();
        if (start <= 0 || end < start) return new JSONArray();
        SQLiteDatabase db = getReadableDatabase();

        // Elementos no recurrentes.
        try (Cursor c = db.rawQuery("SELECT * FROM items WHERE deleted_at=0 AND type NOT IN ('place','restaurant','document') AND due_at>0 AND due_at<=? AND (CASE WHEN end_at>0 THEN end_at ELSE due_at END)>=? AND recurrence='none' ORDER BY due_at ASC",
                new String[]{String.valueOf(end), String.valueOf(start)})) {
            while (c.moveToNext()) {
                JSONObject o = itemJson(c);
                o.put("recurring", false); o.put("seriesId", o.optLong("id")); o.put("occurrenceAt", o.optLong("dueAt")); o.put("occurrenceStatus", o.optString("status", "active"));
                rows.add(o);
            }
        } catch (Exception ignored) {}

        // Series recurrentes que pueden cruzar el rango.
        try (Cursor c = db.rawQuery("SELECT * FROM items WHERE deleted_at=0 AND type NOT IN ('place','restaurant','document') AND due_at>0 AND recurrence<>'none' AND due_at<=? AND (recurrence_until=0 OR recurrence_until>=?)",
                new String[]{String.valueOf(end), String.valueOf(start)})) {
            while (c.moveToNext()) {
                JSONObject base = itemJson(c);
                long seriesId = base.optLong("id");
                HashMap<Long, JSONObject> exceptions = recurrenceExceptions(seriesId, start, end);
                HashSet<Long> emitted = new HashSet<>();
                long spanMillis = Math.max(0L, base.optLong("endAt",0) - base.optLong("dueAt",0));
                if (spanMillis <= 0 && base.optInt("durationMinutes",0) > 0) spanMillis = base.optInt("durationMinutes",0) * 60000L;
                long expansionStart = Math.max(0L, start - spanMillis);
                List<Long> occurrences = RecurrenceEngine.occurrencesBetween(base.optLong("dueAt"), base.optString("recurrence"), base.optLong("recurrenceUntil"), expansionStart, end, ZoneId.systemDefault(), 5000);
                for (long occurrenceAt : occurrences) {
                    JSONObject ex = exceptions.get(occurrenceAt);
                    JSONObject occ = buildOccurrence(base, occurrenceAt, ex);
                    if (occ == null) continue;
                    long actual = occ.optLong("dueAt", occurrenceAt);
                    long actualEnd = occ.optLong("endAt", actual);
                    if (actual > end || actualEnd < start) continue;
                    rows.add(occ); emitted.add(occurrenceAt);
                }
                // Excepciones movidas desde otra fecha hacia el rango actual.
                for (Map.Entry<Long,JSONObject> entry : exceptions.entrySet()) {
                    long original = entry.getKey(); JSONObject ex = entry.getValue();
                    long overrideDue = ex.optLong("overrideDueAt", 0);
                    if (emitted.contains(original) || overrideDue < start || overrideDue > end) continue;
                    JSONObject occ = buildOccurrence(base, original, ex);
                    if (occ != null) rows.add(occ);
                }
            }
        } catch (Exception ignored) {}

        rows.sort(Comparator.comparingLong(o -> o.optLong("dueAt", Long.MAX_VALUE)));
        JSONArray out = new JSONArray(); for (JSONObject o : rows) out.put(o); return out;
    }

    /** Obtiene una aparición concreta de una serie (la clave occurrenceAt siempre es la fecha original de la regla). */
    public JSONObject getOccurrence(long seriesId, long occurrenceAt) {
        JSONObject base = getItem(seriesId);
        if (base.length() == 0) return new JSONObject();
        if (!RecurrenceEngine.isRecurring(base.optString("recurrence", RecurrenceEngine.NONE))) {
            try {
                base.put("recurring", false); base.put("seriesId", seriesId); base.put("occurrenceAt", base.optLong("dueAt")); base.put("occurrenceStatus", base.optString("status","active"));
            } catch (Exception ignored) {}
            return base;
        }
        JSONObject occurrence = buildOccurrence(base, occurrenceAt, getRecurrenceException(seriesId, occurrenceAt));
        return occurrence == null ? new JSONObject() : occurrence;
    }

    /** Guarda estado/override de una sola aparición sin duplicar la serie completa. */
    public boolean saveOccurrenceException(long seriesId, long occurrenceAt, String state, JSONObject override) {
        if (seriesId <= 0 || occurrenceAt <= 0) return false;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("series_id", seriesId); v.put("occurrence_at", occurrenceAt); v.put("state", nonBlank(state, "active"));
        String json = override == null ? "" : override.toString(); v.put("override_json", json);
        v.put("override_due_at", override == null ? 0 : Math.max(0, override.optLong("dueAt", 0)));
        v.put("updated_at", now);
        JSONObject old = getRecurrenceException(seriesId, occurrenceAt);
        v.put("created_at", old.length() == 0 ? now : old.optLong("createdAt", now));
        long row = getWritableDatabase().insertWithOnConflict("recurrence_exceptions", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        if (row != -1) addHistory("aparición actualizada", "recurrence", seriesId, getItem(seriesId).optString("title"));
        return row != -1;
    }

    public boolean deleteOccurrenceException(long seriesId, long occurrenceAt) {
        return getWritableDatabase().delete("recurrence_exceptions", "series_id=? AND occurrence_at=?", new String[]{String.valueOf(seriesId), String.valueOf(occurrenceAt)}) > 0;
    }

    public boolean setOccurrenceStatus(long seriesId, long occurrenceAt, String status) {
        JSONObject ex = getRecurrenceException(seriesId, occurrenceAt);
        JSONObject override = new JSONObject();
        try {
            String raw = ex.optString("overrideJson", "");
            if (!raw.isBlank()) override = new JSONObject(raw);
        } catch (Exception ignored) {}
        String state = status == null || status.isBlank() ? "active" : status;
        return saveOccurrenceException(seriesId, occurrenceAt, state, override);
    }

    /** Recorta una serie justo antes de la aparición indicada. */
    public boolean truncateSeriesBefore(long seriesId, long occurrenceAt) {
        JSONObject base = getItem(seriesId); if (base.length() == 0) return false;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate d = Instant.ofEpochMilli(occurrenceAt).atZone(zone).toLocalDate();
        long until = d.minusDays(1).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
        ContentValues v = new ContentValues(); v.put("recurrence_until", until); v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(seriesId)}) > 0;
    }

    public void deleteExceptionsFrom(long seriesId, long occurrenceAt) {
        getWritableDatabase().delete("recurrence_exceptions", "series_id=? AND occurrence_at>=?", new String[]{String.valueOf(seriesId), String.valueOf(occurrenceAt)});
    }

    /** Devuelve la clave original de la próxima aparición efectiva posterior a afterMillis. */
    public long findNextOccurrence(long seriesId, long afterMillis) {
        JSONObject base = getItem(seriesId); if (base.length() == 0 || base.optLong("dueAt",0)<=0) return 0;
        if ("done".equals(base.optString("status")) || "cancelled".equals(base.optString("status")) || PlaceEntry.isPlace(base.optString("type")) || "document".equals(base.optString("type"))) return 0;
        String recurrence = base.optString("recurrence", RecurrenceEngine.NONE);
        if (!RecurrenceEngine.isRecurring(recurrence)) return base.optLong("dueAt") > afterMillis ? base.optLong("dueAt") : 0;

        long bestKey = 0, bestActual = Long.MAX_VALUE;
        // Apariciones movidas hacia el futuro pueden proceder de una clave original ya pasada.
        try (Cursor c = getReadableDatabase().rawQuery("SELECT occurrence_at,override_due_at,state FROM recurrence_exceptions WHERE series_id=? AND override_due_at>? AND state NOT IN ('deleted','done') ORDER BY override_due_at ASC LIMIT 1",
                new String[]{String.valueOf(seriesId), String.valueOf(afterMillis)})) {
            if (c.moveToFirst()) { bestKey=c.getLong(0); bestActual=c.getLong(1); }
        } catch (Exception ignored) {}

        long cursor = afterMillis;
        for (int guard=0; guard<5000; guard++) {
            long key = RecurrenceEngine.nextOccurrence(base.optLong("dueAt"), recurrence, base.optLong("recurrenceUntil"), cursor, ZoneId.systemDefault());
            if (key <= 0) break;
            JSONObject occ = getOccurrence(seriesId, key);
            if (occ.length() > 0) {
                String state = occ.optString("occurrenceStatus", "active");
                long actual = occ.optLong("dueAt", key);
                if (!"deleted".equals(state) && !"done".equals(state) && actual > afterMillis && actual < bestActual) { bestKey=key; bestActual=actual; }
                if (actual > afterMillis && actual >= bestActual) break;
            }
            cursor = key;
        }
        return bestKey;
    }

    public JSONObject getRecurrenceException(long seriesId, long occurrenceAt) {
        JSONObject o = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT state,override_json,override_due_at,created_at,updated_at FROM recurrence_exceptions WHERE series_id=? AND occurrence_at=?",
                new String[]{String.valueOf(seriesId), String.valueOf(occurrenceAt)})) {
            if (c.moveToFirst()) {
                o.put("state",c.getString(0)); o.put("overrideJson",c.getString(1)); o.put("overrideDueAt",c.getLong(2)); o.put("createdAt",c.getLong(3)); o.put("updatedAt",c.getLong(4));
            }
        } catch (Exception ignored) {}
        return o;
    }

    private HashMap<Long,JSONObject> recurrenceExceptions(long seriesId, long start, long end) {
        HashMap<Long,JSONObject> map = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT occurrence_at,state,override_json,override_due_at,created_at,updated_at FROM recurrence_exceptions WHERE series_id=? AND ((occurrence_at BETWEEN ? AND ?) OR (override_due_at BETWEEN ? AND ?))",
                new String[]{String.valueOf(seriesId),String.valueOf(start),String.valueOf(end),String.valueOf(start),String.valueOf(end)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject(); o.put("state",c.getString(1)); o.put("overrideJson",c.getString(2)); o.put("overrideDueAt",c.getLong(3)); o.put("createdAt",c.getLong(4)); o.put("updatedAt",c.getLong(5)); map.put(c.getLong(0),o);
            }
        } catch (Exception ignored) {}
        return map;
    }

    private JSONObject buildOccurrence(JSONObject base, long occurrenceAt, JSONObject ex) {
        try {
            String state = ex == null ? "active" : ex.optString("state", "active");
            if ("deleted".equals(state)) return null;
            JSONObject o = new JSONObject(base.toString());
            long baseStart=base.optLong("dueAt",0),baseEnd=base.optLong("endAt",0);
            long span=Math.max(0L,baseEnd-baseStart);
            if(span<=0&&base.optInt("durationMinutes",0)>0)span=base.optInt("durationMinutes",0)*60000L;
            o.put("recurring", true); o.put("seriesId", base.optLong("id")); o.put("occurrenceAt", occurrenceAt); o.put("dueAt", occurrenceAt);
            o.put("endAt", occurrenceAt + span);
            o.put("occurrenceStatus", state);
            if ("done".equals(state) || "postponed".equals(state)) o.put("status", state);
            if (ex != null) {
                String raw = ex.optString("overrideJson", "");
                if (!raw.isBlank()) {
                    JSONObject ov = new JSONObject(raw);
                    String[] keys = {"title","body","category","type","dueAt","endAt","hasTime","allDay","timeZone","mapsUrl","recurrenceRule","participantsText","durationMinutes","locationText","priority","favorite","status","needsReview"};
                    for (String key : keys) if (ov.has(key)) o.put(key, ov.get(key));
                }
                long moved = ex.optLong("overrideDueAt",0); if (moved>0) {
                    long overriddenEnd = o.optLong("endAt",0);
                    boolean hasExplicitEnd = false;
                    try {
                        String rawEnd = ex.optString("overrideJson","");
                        hasExplicitEnd = !rawEnd.isBlank() && new JSONObject(rawEnd).has("endAt");
                    } catch(Exception ignored) {}
                    o.put("dueAt",moved);
                    if (!hasExplicitEnd) o.put("endAt",moved+span);
                    else if (overriddenEnd < moved) o.put("endAt",moved);
                }
            }
            return o;
        } catch (Exception e) { return null; }
    }

    public JSONArray getFutureReminders() {
        return queryReminders(System.currentTimeMillis(), Long.MAX_VALUE, 1000);
    }

    public boolean isAttachmentReferenced(String path) {
        // Include soft-deleted items because the user may still undo their deletion.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM items WHERE attachment_path=? LIMIT 1",new String[]{path})) {
            if(c.moveToFirst()) return true;
        }
        // Una captura pendiente también es propietaria del adjunto hasta convertirse o descartarse.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM inbox_captures WHERE attachment_path=? AND status IN ('new','reviewing') LIMIT 1",new String[]{path})) {
            return c.moveToFirst();
        }
    }

    public JSONArray getActiveArchiveReminders() {
        JSONArray out=new JSONArray();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT r.id FROM reminders r JOIN items i ON i.id=r.item_id WHERE r.enabled=1 AND i.type IN ('place','restaurant','document')",null)) {
            while(c.moveToNext())out.put(new JSONObject().put("id",c.getLong(0)));
        } catch(Exception ignored) {}
        return out;
    }

    /**
     * Buscador local 2.0.
     * Normaliza tanto la consulta como los datos (acentos, ñ y mayúsculas dejan de importar),
     * busca en todos los campos útiles y ordena por relevancia.
     */
    // Read-only collections use existing item fields. No schema migration.
    public JSONArray getCollection(String collection) {
        if ("documents".equals(collection)) {
            return queryItems("(type='document' OR attachment_path<>'')", null, "updated_at DESC", 3000);
        }
        if ("places".equals(collection)) {
            return queryItems("type IN ('place','restaurant')", null, "updated_at DESC", 3000);
        }
        return new JSONArray();
    }

    public JSONArray search(String raw) {
        String q = RuleClassifier.norm(raw == null ? "" : raw.trim()).replaceAll("\\s+", " ").trim();
        JSONArray out = new JSONArray();
        if (q.isBlank()) return out;

        if (q.contains("aparqu") || q.contains("coche aparcado")) {
            JSONObject p = getLastParking();
            if (p.length() > 0) out.put(withResultKind(p, "item"));
            return out;
        }

        if (((q.contains("producto") || q.contains("cosas")) && (q.contains("comprar") || q.contains("compra"))) ||
                q.contains("queria comprar") || q.contains("quiero comprar") || q.contains("por comprar")) {
            JSONArray shopping = searchListsNormalized(new String[]{"compra"}, true);
            if (shopping.length() > 0) return shopping;
        }

        boolean pending = q.contains("pendiente");
        boolean week = q.contains("semana");
        boolean domainPending = containsAnySearch(q,"lugar","sitio","restaurante","producto","documento","coche","viaje","cumpleanos");
        boolean genericPending = q.equals("pendiente") || q.equals("pendientes") || q.contains("que tengo pendiente") || q.contains("tengo pendiente") || q.contains("pendiente esta semana") || q.contains("pendientes esta semana");
        if (pending && (q.contains("tarea") || (!domainPending && genericPending))) {
            long limit = week ? endOfCurrentWeekMillis() : Long.MAX_VALUE;
            JSONArray a = queryItems("type IN ('event','task','reminder','expiry') AND status NOT IN ('done','cancelled') AND (due_at=0 OR due_at<=?)",
                    new String[]{String.valueOf(limit)}, "priority DESC, due_at ASC", 200);
            for (int i=0;i<a.length();i++) out.put(withResultKind(a.optJSONObject(i), "item"));
            return out;
        }

        SearchIntent intent = SearchIntent.from(q);
        String[] rawTerms = QueryInterpreter.importantTerms(q);
        LinkedHashSet<String> termSet = new LinkedHashSet<>();
        for (String t : rawTerms) {
            String n = RuleClassifier.norm(t);
            if (!n.isBlank() && !intent.consumedTerms.contains(n)) termSet.add(n);
        }
        for (String t : intent.extraTerms) termSet.add(t);
        String[] terms = termSet.toArray(new String[0]);

        ArrayList<ScoredResult> ranked = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM items WHERE deleted_at=0 ORDER BY favorite DESC,updated_at DESC LIMIT 3000", null)) {
            while (c.moveToNext()) {
                JSONObject item = itemJson(c);
                if (!intent.accepts(item)) continue;
                int score = scoreItem(item, q, terms, intent);
                if (score > 0) ranked.add(new ScoredResult(score, item.optLong("updatedAt", 0), searchSortAt(item), withResultKind(item, "item")));
            }
        } catch (Exception ignored) {}

        // Listas: se evalúan con la misma normalización, incluida la ñ/tildes.
        JSONArray lists = searchListsNormalized(terms.length == 0 ? new String[]{q} : terms, false);
        for (int i=0;i<lists.length();i++) {
            JSONObject l = lists.optJSONObject(i); if (l == null) continue;
            int score = 35 + (l.optBoolean("favorite", false) ? 5 : 0);
            ranked.add(new ScoredResult(score, l.optLong("updatedAt", 0), 0L, l));
        }

        final long now = System.currentTimeMillis();
        ranked.sort((a,b) -> {
            boolean aDated = a.sortAt > 0, bDated = b.sortAt > 0;
            if (aDated != bDated) return aDated ? -1 : 1; // los elementos con fecha siempre primero
            if (aDated) {
                boolean aFuture = a.sortAt >= now, bFuture = b.sortAt >= now;
                if (aFuture != bFuture) return aFuture ? -1 : 1; // primero lo próximo, después lo pasado
                int c = aFuture ? Long.compare(a.sortAt, b.sortAt) : Long.compare(b.sortAt, a.sortAt);
                if (c != 0) return c; // futuro: más cercano primero; pasado: más reciente primero
            }
            int c = Integer.compare(b.score, a.score);
            if (c != 0) return c;
            return Long.compare(b.updatedAt, a.updatedAt);
        });
        HashSet<String> seen = new HashSet<>();
        for (ScoredResult r : ranked) {
            String key = r.data.optString("resultKind","item") + ":" + r.data.optLong("id",0);
            if (seen.add(key)) out.put(r.data);
            if (out.length() >= 100) break;
        }
        return out;
    }

    private static boolean containsAnySearch(String q,String... values){ for(String v:values) if(q.contains(v)) return true; return false; }

    private int scoreItem(JSONObject item, String fullQuery, String[] terms, SearchIntent intent) {
        String title = normField(item.optString("title"));
        String body = normField(item.optString("body"));
        String original = normField(item.optString("originalText"));
        String category = normField(item.optString("category"));
        String subcategory = normField(item.optString("subcategory"));
        String tags = normField(item.optString("tags"));
        String location = normField(item.optString("locationText"));
        String type = normField(item.optString("type"));
        String recurrence = normField(item.optString("recurrence"));
        String attachment = normField(item.optString("attachmentName"));
        String all = String.join(" ", title, body, original, category, subcategory, tags, location, type, recurrence, attachment);

        if (terms.length > 0) {
            for (String term : terms) {
                if (!matchesTerm(all, term)) return 0;
            }
        } else if (intent.category == null && intent.type == null && !all.contains(fullQuery)) return 0;

        int score = 1;
        if (title.equals(fullQuery)) score += 160;
        else if (title.contains(fullQuery)) score += 120;
        if (category.equals(fullQuery)) score += 130;
        else if (category.contains(fullQuery)) score += 95;
        if (type.equals(fullQuery)) score += 90;
        if (location.contains(fullQuery)) score += 80;
        if (tags.contains(fullQuery)) score += 65;
        if (subcategory.contains(fullQuery)) score += 55;
        if (body.contains(fullQuery)) score += 45;
        if (original.contains(fullQuery)) score += 30;
        if (recurrence.contains(fullQuery)) score += 20;
        if (attachment.contains(fullQuery)) score += 25;

        for (String term : terms) {
            if (title.contains(term)) score += 35;
            if (category.contains(term)) score += 30;
            if (type.contains(term)) score += 22;
            if (location.contains(term)) score += 20;
            if (tags.contains(term)) score += 15;
            if (body.contains(term)) score += 10;
            if (original.contains(term)) score += 7;
        }
        if (item.optBoolean("favorite", false)) score += 5;
        return score;
    }

    private boolean matchesTerm(String all, String term) {
        if (all.contains(term)) return true;
        for (String alt : SearchIntent.alternatives(term)) if (all.contains(alt)) return true;
        return false;
    }

    private JSONArray searchListsNormalized(String[] terms, boolean shoppingOnly) {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT l.id,l.title,l.kind,l.favorite,l.updated_at,COALESCE(GROUP_CONCAT(e.text||' '||e.category,' '),'') " +
                        "FROM lists l LEFT JOIN list_entries e ON e.list_id=l.id GROUP BY l.id ORDER BY l.favorite DESC,l.updated_at DESC LIMIT 500", null)) {
            while (c.moveToNext()) {
                String title = normField(c.getString(1)), kind = normField(c.getString(2)), entries = normField(c.getString(5));
                if (shoppingOnly && !(kind.contains("compra") || title.contains("compra"))) continue;
                String all = title + " " + kind + " " + entries;
                boolean ok = terms.length == 0;
                if (terms.length > 0) {
                    ok = true;
                    for (String t : terms) if (!matchesTerm(all, t)) { ok=false; break; }
                }
                if (!ok) continue;
                JSONObject o = new JSONObject();
                o.put("resultKind","list"); o.put("id",c.getLong(0)); o.put("title",c.getString(1));
                o.put("category",c.getString(2)); o.put("favorite",c.getInt(3)==1); o.put("updatedAt",c.getLong(4));
                o.put("body", shoppingOnly ? "Lista de compra guardada" : "Lista guardada");
                out.put(o);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String normField(String s) {
        return RuleClassifier.norm(s == null ? "" : s).replaceAll("\\s+", " ").trim();
    }

    private static long endOfCurrentWeekMillis() {
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone);
        int remaining=7-today.getDayOfWeek().getValue();
        return today.plusDays(remaining).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
    }

    /** Fecha efectiva usada para ordenar resultados de búsqueda.
     *  En series recurrentes se usa la próxima aparición desde el inicio de hoy,
     *  no la fecha histórica con la que se creó la serie.
     */
    private long searchSortAt(JSONObject item) {
        long due = item.optLong("dueAt", 0);
        if (due <= 0) return 0;
        String recurrence = item.optString("recurrence", RecurrenceEngine.NONE);
        if (!RecurrenceEngine.isRecurring(recurrence)) return due;
        ZoneId zone = ZoneId.systemDefault();
        long todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli();
        long next = RecurrenceEngine.nextOccurrence(due, recurrence, item.optLong("recurrenceUntil", 0), todayStart - 1, zone);
        return next > 0 ? next : due;
    }

    private static final class ScoredResult {
        final int score; final long updatedAt; final long sortAt; final JSONObject data;
        ScoredResult(int score,long updatedAt,long sortAt,JSONObject data){this.score=score;this.updatedAt=updatedAt;this.sortAt=sortAt;this.data=data;}
    }

    private static final class SearchIntent {
        String category; String type; long start=0,end=0;
        final Set<String> consumedTerms=new HashSet<>();
        final Set<String> extraTerms=new LinkedHashSet<>();

        static SearchIntent from(String q) {
            SearchIntent x=new SearchIntent();
            if (containsAny(q,"cumpleanos","cumple","cumples")) { x.category="cumpleanos"; x.consumedTerms.addAll(Arrays.asList("cumpleanos","cumple","cumples")); }
            else if (containsAny(q,"salud","medico","medica","analitica","hospital","clinica")) { x.category="salud"; }
            else if (containsAny(q,"coche","itv","vehiculo","neumaticos")) { x.category="coche"; }
            else if (containsAny(q,"viaje","viajes")) x.category="viajes";
            else if (containsAny(q,"restaurante","restaurantes","cenar","cenando","comer")) x.category="restaurantes";
            else if (containsAny(q,"lugar","lugares","sitio","sitios")) x.category="lugares";
            else if (containsAny(q,"compra","compras")) x.category="compras";
            else if (containsAny(q,"documento","documentos","manual","factura")) x.category="documentos";
            else if (containsAny(q,"finanza","finanzas")) x.category="finanzas";
            else if (containsAny(q,"casa","hogar")) x.category="casa";
            else if (containsAny(q,"ocio")) x.category="ocio";
            else if (containsAny(q,"idea","ideas")) x.category="ideas";

            if (containsAny(q,"tarea","tareas","recordatorio","recordatorios","cita","citas","evento","eventos","vencimiento","vencimientos")) {
                x.type="appointment";
                x.consumedTerms.addAll(Arrays.asList("tarea","tareas","recordatorio","recordatorios","cita","citas","evento","eventos","vencimiento","vencimientos"));
            }

            ZoneId z=ZoneId.systemDefault(); LocalDate today=LocalDate.now(z);
            if (q.contains("hoy")) { x.start=dayStart(today,z); x.end=dayEnd(today,z); x.consumedTerms.add("hoy"); }
            else if (q.contains("manana")) { LocalDate d=today.plusDays(1); x.start=dayStart(d,z); x.end=dayEnd(d,z); x.consumedTerms.add("manana"); }
            else if (q.contains("esta semana")) { x.start=dayStart(today,z); x.end=dayEnd(today.plusDays(7-today.getDayOfWeek().getValue()),z); x.consumedTerms.add("semana"); }
            else if (q.contains("este mes")) { LocalDate ss=today.withDayOfMonth(1),ee=ss.plusMonths(1).minusDays(1); x.start=dayStart(ss,z);x.end=dayEnd(ee,z);x.consumedTerms.add("mes"); }

            String[] months={"enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"};
            for(int i=0;i<months.length;i++) if(q.contains(months[i])) {
                int year=today.getYear(); LocalDate ss=LocalDate.of(year,i+1,1);
                x.start=dayStart(ss,z);x.end=dayEnd(ss.plusMonths(1).minusDays(1),z);x.consumedTerms.add(months[i]);break;
            }
            java.util.regex.Matcher ym=java.util.regex.Pattern.compile("\\b(20\\d{2})\\b").matcher(q);
            if(ym.find() && x.start>0) {
                int year=Integer.parseInt(ym.group(1)); LocalDate d=java.time.Instant.ofEpochMilli(x.start).atZone(z).toLocalDate();
                LocalDate ss=LocalDate.of(year,d.getMonthValue(),1);x.start=dayStart(ss,z);x.end=dayEnd(ss.plusMonths(1).minusDays(1),z);
                x.consumedTerms.add(ym.group(1));
            }
            return x;
        }

        boolean accepts(JSONObject item) {
            String cat=normField(item.optString("category")), typ=normField(item.optString("type"));
            if(category!=null && !cat.equals(category) && !PlaceEntry.matchesSearchCategory(item,category)) return false;
            if ("appointment".equals(type)) {
                if (!Arrays.asList("event","task","reminder","expiry").contains(typ)) return false;
            } else if(type!=null && !typ.equals(type)) return false;
            if(start>0) {
                long due=item.optLong("dueAt",0);
                String recurrence=item.optString("recurrence",RecurrenceEngine.NONE);
                if(RecurrenceEngine.isRecurring(recurrence)) {
                    java.util.List<Long> xs=RecurrenceEngine.occurrencesBetween(due,recurrence,item.optLong("recurrenceUntil",0),start,end,ZoneId.systemDefault(),1);
                    if(xs.isEmpty()) return false;
                } else if(due<start||due>end) return false;
            }
            return true;
        }

        static String[] alternatives(String term) {
            if("cumple".equals(term)||"cumples".equals(term)||"cumpleanos".equals(term)) return new String[]{"cumpleanos","cumple"};
            if("medico".equals(term)||"medica".equals(term)) return new String[]{"salud","medico","hospital","clinica"};
            if("analitica".equals(term)) return new String[]{"analitica","salud"};
            if("cita".equals(term)||"citas".equals(term)) return new String[]{"event","cita","evento"};
            if("evento".equals(term)||"eventos".equals(term)) return new String[]{"event","evento","cita","expiry","reminder"};
            if("tarea".equals(term)||"tareas".equals(term)) return new String[]{"task","tarea"};
            if("recordatorio".equals(term)||"recordatorios".equals(term)) return new String[]{"reminder","recordatorio"};
            if("sitio".equals(term)||"sitios".equals(term)) return new String[]{"lugares","lugar","place","sitio"};
            if("lugar".equals(term)||"lugares".equals(term)) return new String[]{"lugares","lugar","place","restaurant"};
            if("restaurante".equals(term)||"restaurantes".equals(term)) return new String[]{"restaurantes","restaurante","restaurant"};
            if("cenar".equals(term)||"cenando".equals(term)||"comer".equals(term)) return new String[]{"restaurantes","restaurante","restaurant"};
            if("diaria".equals(term)||"diario".equals(term)) return new String[]{"daily","diaria"};
            if("semanal".equals(term)) return new String[]{"weekly","semanal"};
            if("mensual".equals(term)) return new String[]{"monthly","mensual"};
            if("bimestral".equals(term)) return new String[]{"bimonthly","bimestral"};
            if("trimestral".equals(term)) return new String[]{"quarterly","trimestral"};
            if("semestral".equals(term)) return new String[]{"semiannual","semestral"};
            if("anual".equals(term)) return new String[]{"annual","anual"};
            if("vencimiento".equals(term)||"vence".equals(term)) return new String[]{"expiry","vencimiento","vence"};
            return new String[]{term};
        }
        private static boolean containsAny(String q,String...v){for(String s:v)if(q.contains(s))return true;return false;}
        private static long dayStart(LocalDate d,ZoneId z){return d.atStartOfDay(z).toInstant().toEpochMilli();}
        private static long dayEnd(LocalDate d,ZoneId z){return d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()-1;}
    }


    // ---------------- CUADERNO ----------------
    public long insertJournalEntry(JSONObject in) {
        String body = in.optString("body", "").trim();
        if (body.isBlank()) return 0;
        JournalSynthesizer.Result auto = JournalSynthesizer.synthesize(body);
        long now = in.optLong("createdAt", System.currentTimeMillis());
        ContentValues v = new ContentValues();
        v.put("kind", safeJournalKind(in.optString("kind", auto.kind)));
        v.put("title", nonBlank(in.optString("title", auto.title), auto.title));
        v.put("summary", in.has("summary") ? in.optString("summary", "") : auto.summary);
        v.put("body", body);
        v.put("tags", in.has("tags") ? in.optString("tags", "") : auto.tags);
        v.put("favorite", in.optBoolean("favorite", false) ? 1 : 0);
        v.put("source", in.optString("source", "manual"));
        v.put("created_at", now);
        v.put("updated_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertOrThrow("journal_entries", null, v);
        addHistory("cuaderno guardado", "journal", id, v.getAsString("title"));
        return id;
    }

    public JSONObject synthesizeJournal(String raw) {
        JSONObject o = new JSONObject();
        try {
            JournalSynthesizer.Result r = JournalSynthesizer.synthesize(raw == null ? "" : raw);
            o.put("kind", r.kind); o.put("title", r.title); o.put("summary", r.summary); o.put("tags", r.tags); o.put("body", r.body);
        } catch (Exception ignored) {}
        return o;
    }

    public boolean updateJournalEntry(JSONObject in) {
        long id = in.optLong("id", 0); if (id <= 0) return false;
        ContentValues v = new ContentValues();
        if (in.has("kind")) v.put("kind", safeJournalKind(in.optString("kind")));
        if (in.has("title")) v.put("title", nonBlank(in.optString("title"), "Sin título"));
        if (in.has("summary")) v.put("summary", in.optString("summary", ""));
        if (in.has("body")) v.put("body", in.optString("body", ""));
        if (in.has("tags")) v.put("tags", in.optString("tags", ""));
        if (in.has("favorite")) v.put("favorite", in.optBoolean("favorite", false) ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        boolean ok = getWritableDatabase().update("journal_entries", v, "id=?", new String[]{String.valueOf(id)}) > 0;
        if (ok) addHistory("cuaderno editado", "journal", id, in.optString("title", ""));
        return ok;
    }

    public boolean deleteJournalEntry(long id) {
        if (id <= 0) return false;
        SQLiteDatabase sql = getWritableDatabase();
        sql.delete("journal_daily_pick", "entry_id=?", new String[]{String.valueOf(id)});
        boolean ok = sql.delete("journal_entries", "id=?", new String[]{String.valueOf(id)}) > 0;
        if (ok) addHistory("cuaderno eliminado", "journal", id, "");
        return ok;
    }

    public JSONObject getJournalEntry(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,kind,title,summary,body,tags,favorite,source,created_at,updated_at FROM journal_entries WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? journalJson(c) : new JSONObject();
        } catch (Exception e) { return new JSONObject(); }
    }

    public JSONArray getJournalEntries(String rawQuery, String kind, boolean favoritesOnly) {
        String q = normField(rawQuery == null ? "" : rawQuery);
        String k = safeJournalFilter(kind);
        if (k.isBlank() && (q.contains("cita") || q.contains("frase"))) k = "quote";
        if (k.isBlank() && (q.contains("pensamiento") || q.contains("reflexion"))) k = "thought";
        ArrayList<JSONObject> matches = new ArrayList<>();
        ArrayList<Integer> scores = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,kind,title,summary,body,tags,favorite,source,created_at,updated_at FROM journal_entries ORDER BY created_at DESC LIMIT 3000", null)) {
            while (c.moveToNext()) {
                JSONObject o = journalJson(c);
                if (!k.isBlank() && !k.equals(o.optString("kind"))) continue;
                if (favoritesOnly && !o.optBoolean("favorite", false)) continue;
                String title = normField(o.optString("title")), summary = normField(o.optString("summary")), body = normField(o.optString("body")), tags = normField(o.optString("tags"));
                String all = title + " " + summary + " " + body + " " + tags + " " + normField(o.optString("kind"));
                int score = 1;
                if (!q.isBlank()) {
                    String[] terms = QueryInterpreter.importantTerms(q);
                    if (terms.length == 0) terms = q.split("\\s+");
                    boolean ok = true;
                    for (String t : terms) {
                        String nt = normField(t); if (nt.isBlank()) continue;
                        if (nt.equals("pensamiento") || nt.equals("pensamientos") || nt.equals("reflexion") || nt.equals("reflexiones") || nt.equals("cita") || nt.equals("citas") || nt.equals("frase") || nt.equals("frases") || nt.equals("cuaderno") || nt.equals("sobre")) continue;
                        if (!all.contains(nt)) { ok = false; break; }
                        if (title.contains(nt)) score += 40;
                        else if (tags.contains(nt)) score += 25;
                        else if (summary.contains(nt)) score += 15;
                        else score += 8;
                    }
                    if (!ok) continue;
                }
                if (o.optBoolean("favorite", false)) score += 4;
                matches.add(o); scores.add(score);
            }
        } catch (Exception ignored) {}
        ArrayList<Integer> idx = new ArrayList<>(); for (int i=0;i<matches.size();i++) idx.add(i);
        idx.sort((a,b)-> { int sc=Integer.compare(scores.get(b),scores.get(a)); if(sc!=0)return sc; return Long.compare(matches.get(b).optLong("createdAt"),matches.get(a).optLong("createdAt")); });
        JSONArray out = new JSONArray(); for (int i : idx) { out.put(matches.get(i)); if (out.length()>=200) break; }
        return out;
    }

    public JSONObject getJournalDailyPick(String dayKey, boolean forceNew) {
        String day = dayKey == null ? "" : dayKey.trim();
        if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) day = LocalDate.now().toString();
        if (!forceNew) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT j.id,j.kind,j.title,j.summary,j.body,j.tags,j.favorite,j.source,j.created_at,j.updated_at FROM journal_daily_pick p JOIN journal_entries j ON j.id=p.entry_id WHERE p.day_key=?", new String[]{day})) {
                if (c.moveToFirst()) return journalJson(c);
            } catch (Exception ignored) {}
        }
        ArrayList<JSONObject> all = new ArrayList<>();
        HashSet<Long> recent = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT entry_id FROM journal_daily_pick ORDER BY created_at DESC LIMIT 7", null)) { while(c.moveToNext()) recent.add(c.getLong(0)); } catch(Exception ignored){}
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,kind,title,summary,body,tags,favorite,source,created_at,updated_at FROM journal_entries ORDER BY created_at DESC", null)) { while(c.moveToNext()) all.add(journalJson(c)); } catch(Exception ignored){}
        if (all.isEmpty()) return new JSONObject();
        ArrayList<JSONObject> pool = new ArrayList<>(); for(JSONObject o:all) if(!recent.contains(o.optLong("id"))) pool.add(o); if(pool.isEmpty()) pool.addAll(all);
        ArrayList<JSONObject> weighted = new ArrayList<>(); for(JSONObject o:pool){weighted.add(o);if(o.optBoolean("favorite",false))weighted.add(o);} 
        long seed = forceNew ? System.nanoTime() : day.hashCode();
        JSONObject pick = weighted.get(new Random(seed).nextInt(weighted.size()));
        ContentValues v = new ContentValues(); v.put("day_key", day); v.put("entry_id", pick.optLong("id")); v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("journal_daily_pick", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return pick;
    }

    private JSONObject journalJson(Cursor c) {
        JSONObject o = new JSONObject();
        try {
            o.put("resultKind","journal"); o.put("id",c.getLong(0)); o.put("kind",c.getString(1)); o.put("title",c.getString(2));
            o.put("summary",c.getString(3)); o.put("body",c.getString(4)); o.put("tags",c.getString(5)); o.put("favorite",c.getInt(6)==1);
            o.put("source",c.getString(7)); o.put("createdAt",c.getLong(8)); o.put("updatedAt",c.getLong(9));
        } catch(Exception ignored){}
        return o;
    }
    private static String safeJournalKind(String s){String k=s==null?"":s.trim().toLowerCase(Locale.ROOT);return "quote".equals(k)?"quote":"thought";}
    private static String safeJournalFilter(String s){String k=s==null?"":s.trim().toLowerCase(Locale.ROOT);return ("quote".equals(k)||"thought".equals(k))?k:"";}


    // -------------------- Medicamentos --------------------
    public long insertMedication(JSONObject in) {
        String name=in.optString("name","").trim();
        if(name.isBlank()) throw new IllegalArgumentException("Indica el nombre del medicamento");
        String mode=safeMedicationMode(in.optString("scheduleMode","once"));
        int interval=Math.max(0,in.optInt("intervalMinutes",0));
        JSONArray times=normalizeMedicationTimes(in.optJSONArray("times"));
        if("interval".equals(mode) && interval<=0) throw new IllegalArgumentException("Indica cada cuántas horas debe repetirse la toma");
        if("times".equals(mode) && times.length()==0) throw new IllegalArgumentException("Indica las horas concretas de las tomas");
        long start=in.optLong("startAt",0),end=Math.max(0,in.optLong("endAt",0));
        if(start<=0) throw new IllegalArgumentException("Indica cuándo empieza el tratamiento");
        if(end>0&&end<start) throw new IllegalArgumentException("La fecha final debe ser posterior al inicio");
        long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("name",name);v.put("dose",in.optString("dose","").trim());v.put("reason",in.optString("reason","").trim());v.put("instructions",in.optString("instructions","").trim());
        v.put("schedule_mode",mode);v.put("interval_minutes",interval);v.put("times_json",times.toString());v.put("start_at",start);v.put("end_at",end);
        v.put("lead_minutes",5);v.put("voice_enabled",in.optBoolean("voiceEnabled",true)?1:0);v.put("active",in.optBoolean("active",true)?1:0);v.put("source_text",in.optString("sourceText","").trim());
        if(in.has("stockQty")&&!in.isNull("stockQty"))v.put("stock_qty",Math.max(0,in.optDouble("stockQty",0)));else v.putNull("stock_qty");
        v.put("stock_unit",in.optString("stockUnit","").trim());if(in.has("lowStockThreshold")&&!in.isNull("lowStockThreshold"))v.put("low_stock_threshold",Math.max(0,in.optDouble("lowStockThreshold",0)));else v.putNull("low_stock_threshold");
        v.put("created_at",now);v.put("updated_at",now);long id=getWritableDatabase().insertOrThrow("medications",null,v);addHistory("tratamiento creado","medication",id,name);return id;
    }

    public boolean updateMedication(JSONObject in){
        long id=in.optLong("id",0);JSONObject old=getMedication(id);if(old.length()==0)return false;ContentValues v=new ContentValues();
        if(in.has("name")){String n=in.optString("name","").trim();if(n.isBlank())return false;v.put("name",n);}if(in.has("dose"))v.put("dose",in.optString("dose","").trim());if(in.has("reason"))v.put("reason",in.optString("reason","").trim());if(in.has("instructions"))v.put("instructions",in.optString("instructions","").trim());
        String mode=in.has("scheduleMode")?safeMedicationMode(in.optString("scheduleMode")):old.optString("scheduleMode","once");int interval=in.has("intervalMinutes")?Math.max(0,in.optInt("intervalMinutes",0)):old.optInt("intervalMinutes",0);JSONArray times=in.has("times")?normalizeMedicationTimes(in.optJSONArray("times")):old.optJSONArray("times");if(times==null)times=new JSONArray();
        if("interval".equals(mode)&&interval<=0)return false;if("times".equals(mode)&&times.length()==0)return false;v.put("schedule_mode",mode);v.put("interval_minutes",interval);v.put("times_json",times.toString());
        long start=in.has("startAt")?in.optLong("startAt",0):old.optLong("startAt",0),end=in.has("endAt")?Math.max(0,in.optLong("endAt",0)):old.optLong("endAt",0);if(start<=0||end>0&&end<start)return false;v.put("start_at",start);v.put("end_at",end);
        if(in.has("leadMinutes"))v.put("lead_minutes",5);if(in.has("voiceEnabled"))v.put("voice_enabled",in.optBoolean("voiceEnabled")?1:0);if(in.has("active"))v.put("active",in.optBoolean("active")?1:0);if(in.has("sourceText"))v.put("source_text",in.optString("sourceText","").trim());
        if(in.has("stockQty")){if(in.isNull("stockQty"))v.putNull("stock_qty");else v.put("stock_qty",Math.max(0,in.optDouble("stockQty",0)));}if(in.has("stockUnit"))v.put("stock_unit",in.optString("stockUnit","").trim());if(in.has("lowStockThreshold")){if(in.isNull("lowStockThreshold"))v.putNull("low_stock_threshold");else v.put("low_stock_threshold",Math.max(0,in.optDouble("lowStockThreshold",0)));}
        v.put("updated_at",System.currentTimeMillis());boolean ok=getWritableDatabase().update("medications",v,"id=?",new String[]{String.valueOf(id)})>0;if(ok)addHistory("tratamiento actualizado","medication",id,getMedication(id).optString("name"));return ok;
    }

    public boolean deleteMedication(long id){SQLiteDatabase sql=getWritableDatabase();JSONObject m=getMedication(id);sql.delete("medication_doses","medication_id=?",new String[]{String.valueOf(id)});boolean ok=sql.delete("medications","id=?",new String[]{String.valueOf(id)})>0;if(ok)addHistory("tratamiento eliminado","medication",id,m.optString("name"));return ok;}

    public JSONObject getMedication(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,dose,reason,instructions,schedule_mode,interval_minutes,times_json,start_at,end_at,lead_minutes,voice_enabled,stock_qty,stock_unit,low_stock_threshold,active,source_text,created_at,updated_at FROM medications WHERE id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst())return medicationJson(c);}catch(Exception ignored){}return new JSONObject();
    }

    public JSONArray getMedications(String rawQuery,String mode){
        String q=RuleClassifier.norm(rawQuery==null?"":rawQuery.trim());
        if(q.equals("medicamento")||q.equals("medicamentos")||q.equals("medicacion")||q.equals("tratamiento")||q.equals("tratamientos"))q="";
        ArrayList<JSONObject> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,dose,reason,instructions,schedule_mode,interval_minutes,times_json,start_at,end_at,lead_minutes,voice_enabled,stock_qty,stock_unit,low_stock_threshold,active,source_text,created_at,updated_at FROM medications ORDER BY active DESC,updated_at DESC",null)){
            while(c.moveToNext()){JSONObject m=medicationJson(c);boolean active=m.optBoolean("active",false)&&!(m.optLong("endAt",0)>0&&m.optLong("endAt")<System.currentTimeMillis());if("active".equals(mode)&&!active)continue;if("history".equals(mode)&&active)continue;if(!q.isBlank()){String all=RuleClassifier.norm(String.join(" ",m.optString("name"),m.optString("dose"),m.optString("reason"),m.optString("instructions"),m.optString("sourceText")));boolean ok=true;for(String t:q.split(" "))if(!t.isBlank()&&!all.contains(t)){ok=false;break;}if(!ok)continue;}m.put("active",active);m.put("nextDoseAt",getNextMedicationDose(m.optLong("id"),System.currentTimeMillis()-1));rows.add(m);}
        }catch(Exception ignored){}
        rows.sort((a,b)->{boolean aa=a.optBoolean("active"),ba=b.optBoolean("active");if(aa!=ba)return aa?-1:1;long an=a.optLong("nextDoseAt",0),bn=b.optLong("nextDoseAt",0);if(an>0&&bn>0)return Long.compare(an,bn);if(an>0)return -1;if(bn>0)return 1;return a.optString("name").compareToIgnoreCase(b.optString("name"));});JSONArray out=new JSONArray();for(JSONObject x:rows)out.put(x);return out;
    }

    private JSONObject medicationJson(Cursor c){JSONObject o=new JSONObject();try{o.put("resultKind","medication");o.put("id",c.getLong(0));o.put("name",c.getString(1));o.put("title",c.getString(1));o.put("dose",c.getString(2));o.put("reason",c.getString(3));o.put("instructions",c.getString(4));o.put("scheduleMode",c.getString(5));o.put("intervalMinutes",c.getInt(6));o.put("times",new JSONArray(c.getString(7)==null?"[]":c.getString(7)));o.put("startAt",c.getLong(8));o.put("endAt",c.getLong(9));o.put("leadMinutes",c.getInt(10));o.put("voiceEnabled",c.getInt(11)==1);if(!c.isNull(12))o.put("stockQty",c.getDouble(12));o.put("stockUnit",c.getString(13));if(!c.isNull(14))o.put("lowStockThreshold",c.getDouble(14));o.put("active",c.getInt(15)==1);o.put("sourceText",c.getString(16));o.put("createdAt",c.getLong(17));o.put("updatedAt",c.getLong(18));}catch(Exception ignored){}return o;}

    private static String safeMedicationMode(String s){String m=s==null?"":s.trim().toLowerCase(Locale.ROOT);return "interval".equals(m)||"times".equals(m)?m:"once";}
    private static JSONArray normalizeMedicationTimes(JSONArray in){TreeSet<String> set=new TreeSet<>();if(in!=null)for(int i=0;i<in.length();i++){String s=in.optString(i,"").trim();try{LocalTime t=LocalTime.parse(s);set.add(t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));}catch(Exception ignored){}}JSONArray a=new JSONArray();for(String s:set)a.put(s);return a;}

    public long getNextMedicationDose(long medicationId,long afterMillis){JSONObject m=getMedication(medicationId);if(m.length()==0||!m.optBoolean("active",false))return 0;long start=m.optLong("startAt",0),end=m.optLong("endAt",0);if(start<=0||end>0&&afterMillis>=end)return 0;String mode=m.optString("scheduleMode","once");long candidate=0;
        if("once".equals(mode)){candidate=start>afterMillis?start:0;}
        else if("interval".equals(mode)){long step=Math.max(1,m.optInt("intervalMinutes",0))*60000L;if(afterMillis<start)candidate=start;else {long k=((afterMillis-start)/step)+1;candidate=start+k*step;}}
        else {JSONArray times=m.optJSONArray("times");if(times==null||times.length()==0)return 0;ZoneId z=ZoneId.systemDefault();LocalDate first=Instant.ofEpochMilli(start).atZone(z).toLocalDate();LocalDate d=Instant.ofEpochMilli(Math.max(start,afterMillis+1)).atZone(z).toLocalDate();for(int day=0;day<3700;day++,d=d.plusDays(1)){if(d.isBefore(first))continue;for(int i=0;i<times.length();i++){try{LocalTime t=LocalTime.parse(times.optString(i));long at=d.atTime(t).atZone(z).toInstant().toEpochMilli();if(at<start||at<=afterMillis)continue;if(end>0&&at>end)return 0;candidate=at;break;}catch(Exception ignored){}}if(candidate>0)break;}}
        if(candidate>0&&end>0&&candidate>end)return 0;return candidate;
    }

    public JSONObject getNextMedicationDoseInfo(long medicationId,long afterMillis){JSONObject m=getMedication(medicationId);if(m.length()==0)return new JSONObject();long at=getNextMedicationDose(medicationId,afterMillis);if(at<=0)return new JSONObject();try{m.put("scheduledAt",at);m.put("alertAt",at-Math.max(0,5)*60000L);m.put("doseStatus",getMedicationDoseStatus(medicationId,at));m.put("doseRecorded",medicationDoseExists(medicationId,at));}catch(Exception ignored){}return m;}
    private boolean medicationDoseExists(long medicationId,long scheduledAt){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM medication_doses WHERE medication_id=? AND scheduled_at=? LIMIT 1",new String[]{String.valueOf(medicationId),String.valueOf(scheduledAt)})){return c.moveToFirst();}catch(Exception ignored){}return false;}

    public long ensureMedicationDose(long medicationId,long scheduledAt){ContentValues v=new ContentValues();v.put("medication_id",medicationId);v.put("scheduled_at",scheduledAt);v.put("status","pending");v.put("action_at",0);v.put("note","");v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insertWithOnConflict("medication_doses",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public boolean setMedicationDoseStatus(long medicationId,long scheduledAt,String status){
        String st=("taken".equals(status)||"skipped".equals(status)||"snoozed".equals(status))?status:"pending";
        ensureMedicationDose(medicationId,scheduledAt);
        ContentValues v=new ContentValues();v.put("status",st);v.put("action_at",System.currentTimeMillis());
        boolean ok=getWritableDatabase().update("medication_doses",v,"medication_id=? AND scheduled_at=?",new String[]{String.valueOf(medicationId),String.valueOf(scheduledAt)})>0;
        if(ok){
            JSONObject med=getMedication(medicationId);
            if(("taken".equals(st)||"skipped".equals(st))&&"once".equals(med.optString("scheduleMode"))){
                ContentValues done=new ContentValues();done.put("active",0);done.put("updated_at",System.currentTimeMillis());
                getWritableDatabase().update("medications",done,"id=?",new String[]{String.valueOf(medicationId)});
            }
            addHistory("taken".equals(st)?"medicación tomada":"skipped".equals(st)?"medicación omitida":"medicación pospuesta","medication",medicationId,med.optString("name"));
        }
        return ok;
    }
    private String getMedicationDoseStatus(long medicationId,long scheduledAt){try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM medication_doses WHERE medication_id=? AND scheduled_at=?",new String[]{String.valueOf(medicationId),String.valueOf(scheduledAt)})){if(c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "pending";}
    public String getMedicationDoseStatusPublic(long medicationId,long scheduledAt){return getMedicationDoseStatus(medicationId,scheduledAt);}

    /** Dosis ya anunciadas que siguen pendientes/pospuestas. Se usa para reconstruir avisos tras reiniciar. */
    public JSONArray getUnresolvedMedicationDoses(){
        JSONArray a=new JSONArray();long now=System.currentTimeMillis();
        String sql="SELECT d.medication_id,d.scheduled_at,d.status FROM medication_doses d JOIN medications m ON m.id=d.medication_id WHERE m.active=1 AND d.status IN ('pending','snoozed') AND d.scheduled_at<=? ORDER BY d.scheduled_at ASC";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(now)})){
            while(c.moveToNext()){JSONObject o=new JSONObject();try{o.put("medicationId",c.getLong(0));o.put("scheduledAt",c.getLong(1));o.put("status",c.getString(2));a.put(o);}catch(Exception ignored){}}
        }catch(Exception ignored){}
        return a;
    }

    public JSONArray getMedicationDoses(long medicationId,long from,long to){JSONArray a=new JSONArray();JSONObject med=getMedication(medicationId);if(med.length()==0)return a;long cursor=from-1;int count=0;while(count<500){long at=getNextMedicationDose(medicationId,cursor);if(at<=0||at>to)break;JSONObject o=new JSONObject();try{o.put("medicationId",medicationId);o.put("name",med.optString("name"));o.put("dose",med.optString("dose"));o.put("scheduledAt",at);o.put("status",getMedicationDoseStatus(medicationId,at));}catch(Exception ignored){}a.put(o);cursor=at;count++;}return a;}

    public JSONArray getMedicationTodayDoses(){ZoneId z=ZoneId.systemDefault();LocalDate d=LocalDate.now();long from=d.atStartOfDay(z).toInstant().toEpochMilli(),to=d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()-1;JSONArray out=new JSONArray(),meds=getMedications("","active");for(int i=0;i<meds.length();i++){JSONObject m=meds.optJSONObject(i);if(m==null)continue;JSONArray ds=getMedicationDoses(m.optLong("id"),from,to);for(int j=0;j<ds.length();j++){JSONObject x=ds.optJSONObject(j);if(x!=null){try{x.put("leadMinutes",m.optInt("leadMinutes",5));x.put("voiceEnabled",m.optBoolean("voiceEnabled",true));}catch(Exception ignored){}out.put(x);}}}ArrayList<JSONObject> list=new ArrayList<>();for(int i=0;i<out.length();i++)if(out.optJSONObject(i)!=null)list.add(out.optJSONObject(i));list.sort(Comparator.comparingLong(x->x.optLong("scheduledAt")));JSONArray sorted=new JSONArray();for(JSONObject x:list)sorted.put(x);return sorted;}

    public JSONObject getMoreSummary() {
        JSONObject o = new JSONObject();
        try {
            o.put("tasks", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND type='task' AND status='pending'"));
            o.put("lists", scalar("SELECT COUNT(*) FROM lists"));
            o.put("inbox", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND needs_review=1") + getPendingInboxCaptureCount());
            o.put("favorites", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND favorite=1") + scalar("SELECT COUNT(*) FROM lists WHERE favorite=1"));
            o.put("reminders", scalar("SELECT COUNT(*) FROM reminders WHERE enabled=1 AND trigger_at>" + System.currentTimeMillis()));
            o.put("journal", scalar("SELECT COUNT(*) FROM journal_entries"));
            o.put("medications", scalar("SELECT COUNT(*) FROM medications WHERE active=1"));
        } catch (Exception ignored) {}
        return o;
    }

    public JSONObject getBackupStats() {
        JSONObject o=new JSONObject();
        try {
            o.put("items", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0"));
            o.put("tasks", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND type='task'"));
            o.put("lists", scalar("SELECT COUNT(*) FROM lists"));
            o.put("documents", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND (category='Documentos' OR attachment_path<>'')"));
            o.put("reminders", scalar("SELECT COUNT(*) FROM reminders"));
            o.put("attachments", scalar("SELECT COUNT(*) FROM items WHERE deleted_at=0 AND attachment_path<>''"));
            o.put("journal", scalar("SELECT COUNT(*) FROM journal_entries"));
            o.put("medications", scalar("SELECT COUNT(*) FROM medications"));
            o.put("inboxCaptures", scalar("SELECT COUNT(*) FROM inbox_captures WHERE status IN ('new','reviewing')"));
        } catch(Exception ignored){}
        return o;
    }

    /** Reescribe rutas absolutas de adjuntos al restaurar en otro dispositivo. */
    public void repairAttachmentPaths(java.io.File attachmentsDir) {
        if (attachmentsDir == null) return;
        SQLiteDatabase sql=getWritableDatabase();
        try (Cursor c=sql.rawQuery("SELECT id,attachment_path,attachment_name FROM items WHERE attachment_path<>''",null)) {
            while(c.moveToNext()) {
                long id=c.getLong(0); String old=c.getString(1), name=c.getString(2);
                String base="";
                if(old!=null&&!old.isBlank()) base=new java.io.File(old).getName();
                if(base.isBlank()&&name!=null) base=name;
                if(base.isBlank()) continue;
                java.io.File candidate=new java.io.File(attachmentsDir,base);
                if(candidate.exists()) {
                    ContentValues v=new ContentValues();v.put("attachment_path",candidate.getAbsolutePath());
                    sql.update("items",v,"id=?",new String[]{String.valueOf(id)});
                }
            }
        } catch(Exception ignored){}
        try (Cursor c=sql.rawQuery("SELECT id,attachment_path,attachment_name FROM inbox_captures WHERE attachment_path<>''",null)) {
            while(c.moveToNext()) {
                long id=c.getLong(0); String old=c.getString(1), name=c.getString(2);
                String base="";
                if(old!=null&&!old.isBlank()) base=new java.io.File(old).getName();
                if(base.isBlank()&&name!=null) base=name;
                if(base.isBlank()) continue;
                java.io.File candidate=new java.io.File(attachmentsDir,base);
                if(candidate.exists()) {
                    ContentValues v=new ContentValues();v.put("attachment_path",candidate.getAbsolutePath());
                    sql.update("inbox_captures",v,"id=?",new String[]{String.valueOf(id)});
                }
            }
        } catch(Exception ignored){}
    }

    /** Guarda primero el contenido recibido para que no dependa de completar el formulario en la misma sesión. */
    public long insertInboxCapture(JSONObject in, JSONObject classification) {
        if (in == null) return 0;
        long now = System.currentTimeMillis();
        JSONObject detected = classification == null ? new JSONObject() : classification;
        ContentValues v = new ContentValues();
        v.put("source", nonBlank(in.optString("source"), "android-share"));
        v.put("mime", in.optString("mime", ""));
        v.put("subject", in.optString("subject", ""));
        String raw = in.optString("text", "");
        if (raw.isBlank()) raw = in.optString("attachmentName", "");
        v.put("raw_text", raw);
        v.put("attachment_path", in.optString("attachmentPath", ""));
        v.put("attachment_name", in.optString("attachmentName", ""));
        v.put("attachment_mime", in.optString("attachmentMime", ""));
        v.put("detected_type", detected.optString("type", ""));
        v.put("detected_title", detected.optString("title", ""));
        v.put("classification_json", detected.toString());
        v.put("status", "new");
        v.put("converted_entity", "");
        v.put("converted_id", 0);
        v.put("created_at", now);
        v.put("updated_at", now);
        v.put("source_package", in.optString("sourcePackage", ""));
        v.put("sender", in.optString("sender", ""));
        v.put("source_key", in.optString("sourceKey", ""));
        return getWritableDatabase().insert("inbox_captures", null, v);
    }

    public boolean replaceInboxCapture(long id, JSONObject in, JSONObject classification) {
        if (id <= 0 || in == null) return false;
        JSONObject detected = classification == null ? new JSONObject() : classification;
        ContentValues v = new ContentValues();
        v.put("source", nonBlank(in.optString("source"), "calendar"));
        v.put("mime", in.optString("mime", ""));
        v.put("subject", in.optString("subject", ""));
        v.put("raw_text", in.optString("text", ""));
        v.put("detected_type", detected.optString("type", ""));
        v.put("detected_title", detected.optString("title", ""));
        v.put("classification_json", detected.toString());
        v.put("source_package", in.optString("sourcePackage", ""));
        v.put("sender", in.optString("sender", ""));
        v.put("source_key", in.optString("sourceKey", ""));
        v.put("status", "new");
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update(
                "inbox_captures",
                v,
                "id=? AND status IN ('new','reviewing')",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    public boolean isInboxCapturePending(long id) {
        if (id <= 0) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM inbox_captures WHERE id=? AND status IN ('new','reviewing') LIMIT 1",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    public JSONObject getCalendarWatchState(long eventId) {
        JSONObject out = new JSONObject();
        if (eventId <= 0) return out;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT event_id,fingerprint,capture_id,linked_item_id,last_seen_at FROM calendar_watch_events WHERE event_id=? LIMIT 1",
                new String[]{String.valueOf(eventId)})) {
            if (c.moveToFirst()) {
                out.put("eventId", c.getLong(0));
                out.put("fingerprint", c.getString(1));
                out.put("captureId", c.getLong(2));
                out.put("linkedItemId", c.getLong(3));
                out.put("lastSeenAt", c.getLong(4));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void rememberCalendarEvent(long eventId, String fingerprint, long captureId, long linkedItemId) {
        if (eventId <= 0) return;
        SQLiteDatabase db = getWritableDatabase();
        JSONObject existing = getCalendarWatchState(eventId);
        long keepLinked = linkedItemId >= 0 ? linkedItemId : existing.optLong("linkedItemId", 0L);
        long keepCapture = captureId >= 0 ? captureId : existing.optLong("captureId", 0L);
        ContentValues v = new ContentValues();
        v.put("event_id", eventId);
        v.put("fingerprint", fingerprint == null ? "" : fingerprint);
        v.put("capture_id", keepCapture);
        v.put("linked_item_id", keepLinked);
        v.put("last_seen_at", System.currentTimeMillis());
        db.insertWithOnConflict("calendar_watch_events", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void touchCalendarEvent(long eventId) {
        if (eventId <= 0) return;
        ContentValues v = new ContentValues();
        v.put("last_seen_at", System.currentTimeMillis());
        getWritableDatabase().update("calendar_watch_events", v, "event_id=?", new String[]{String.valueOf(eventId)});
    }

    private void linkCalendarCaptureToItem(long captureId, long itemId) {
        if (captureId <= 0 || itemId <= 0) return;
        ContentValues v = new ContentValues();
        v.put("linked_item_id", itemId);
        v.put("capture_id", captureId);
        v.put("last_seen_at", System.currentTimeMillis());
        getWritableDatabase().update("calendar_watch_events", v, "capture_id=?", new String[]{String.valueOf(captureId)});
    }

    public boolean hasExternalInput(String source, String externalId) {
        if (source == null || source.isBlank() || externalId == null || externalId.isBlank()) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM external_watch_items WHERE source=? AND external_id=? LIMIT 1",
                new String[]{source, externalId})) {
            return c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    public JSONObject getExternalInputState(String source, String externalId) {
        JSONObject out = new JSONObject();
        if (source == null || source.isBlank() || externalId == null || externalId.isBlank()) return out;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT source,external_id,fingerprint,capture_id,last_seen_at FROM external_watch_items WHERE source=? AND external_id=? LIMIT 1",
                new String[]{source, externalId})) {
            if (c.moveToFirst()) {
                out.put("source", c.getString(0));
                out.put("externalId", c.getString(1));
                out.put("fingerprint", c.getString(2));
                out.put("captureId", c.getLong(3));
                out.put("lastSeenAt", c.getLong(4));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void rememberExternalInput(String source, String externalId, String fingerprint, long captureId) {
        if (source == null || source.isBlank() || externalId == null || externalId.isBlank()) return;
        JSONObject existing = getExternalInputState(source, externalId);
        ContentValues v = new ContentValues();
        v.put("source", source);
        v.put("external_id", externalId);
        v.put("fingerprint", fingerprint == null ? "" : fingerprint);
        v.put("capture_id", captureId >= 0 ? captureId : existing.optLong("captureId", 0L));
        v.put("last_seen_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "external_watch_items", null, v, SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public JSONObject getExternalWatchDiagnostics() {
        JSONObject out = new JSONObject();
        try {
            out.put("smsSeen", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='sms'"));
            out.put("smsCaptured", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='sms' AND capture_id>0"));
            out.put("smsRejected", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='sms' AND fingerprint LIKE 'sms-filter-v6:rejected:%'"));
            out.put("smsErrors", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='sms' AND fingerprint LIKE 'sms-error:%'"));
            out.put("gmailSeen", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='gmail'"));
            out.put("gmailCaptured", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='gmail' AND capture_id>0"));
            out.put("gmailRejected", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='gmail' AND fingerprint LIKE 'gmail-filter-v6:rejected:%'"));
            out.put("gmailErrors", scalar("SELECT COUNT(*) FROM external_watch_items WHERE source='gmail' AND fingerprint LIKE 'gmail-error:%'"));
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Evita duplicar una misma comunicación cuando llega primero por notificación
     * y después por el proveedor directo (o al revés).
     */
    public long findRecentEquivalentInboxCapture(String sourceFamily, String sender, String text, long windowMs) {
        String target = normalizeInboxText(text);
        if (target.isBlank()) return 0L;
        String targetSender = normalizeInboxText(sender);
        long cutoff = System.currentTimeMillis() - Math.max(60_000L, windowMs);
        String sql = "SELECT id,source,source_package,sender,raw_text FROM inbox_captures " +
                "WHERE created_at>=? AND status IN ('new','reviewing') ORDER BY created_at DESC LIMIT 120";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(cutoff)})) {
            while (c.moveToNext()) {
                String source = c.isNull(1) ? "" : c.getString(1);
                String pkg = c.isNull(2) ? "" : c.getString(2);
                if (!sameInboxSourceFamily(sourceFamily, source, pkg)) continue;

                String candidateSender = normalizeInboxText(c.isNull(3) ? "" : c.getString(3));
                String candidate = normalizeInboxText(c.isNull(4) ? "" : c.getString(4));
                if (candidate.isBlank()) continue;

                boolean textMatch = candidate.equals(target);
                int shorter = Math.min(candidate.length(), target.length());
                if (!textMatch && shorter >= 20) {
                    textMatch = candidate.contains(target) || target.contains(candidate);
                }
                if (!textMatch) continue;

                // Texto exactamente igual es suficiente: la notificación puede mostrar
                // el nombre del contacto mientras el proveedor SMS entrega el teléfono.
                if (candidate.equals(target)) return c.getLong(0);

                boolean senderMatch = targetSender.isBlank() || candidateSender.isBlank() ||
                        candidateSender.equals(targetSender) ||
                        candidateSender.contains(targetSender) || targetSender.contains(candidateSender);
                if (senderMatch) return c.getLong(0);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static boolean sameInboxSourceFamily(String wanted, String source, String pkg) {
        String family = wanted == null ? "" : wanted.trim().toLowerCase(Locale.ROOT);
        if (family.isBlank()) return true;
        String s = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        if ("gmail".equals(family)) {
            return "gmail-api".equals(s) || "com.google.android.gm".equals(p);
        }
        if ("sms".equals(family)) {
            return "sms-provider".equals(s) || p.contains("messaging") || p.contains("mms") ||
                    p.contains("message") || "android.sms".equals(p);
        }
        return true;
    }

    private static String normalizeInboxText(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public boolean inboxCaptureExists(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM inbox_captures WHERE source_key=? LIMIT 1",
                new String[]{sourceKey})) {
            return c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    public long getPendingInboxCaptureCount() {
        return scalar("SELECT COUNT(*) FROM inbox_captures WHERE status IN ('new','reviewing')");
    }

    public JSONArray getInboxCaptures() {
        JSONArray out = new JSONArray();
        String sql = "SELECT id,source,mime,subject,raw_text,attachment_path,attachment_name,attachment_mime,detected_type,detected_title,classification_json,status,converted_entity,converted_id,created_at,updated_at,source_package,sender,source_key FROM inbox_captures WHERE status IN ('new','reviewing') ORDER BY created_at DESC LIMIT 200";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.put(inboxCaptureJson(c));
        } catch (Exception ignored) {}
        return out;
    }

    public JSONObject getInboxCapture(long id) {
        String sql = "SELECT id,source,mime,subject,raw_text,attachment_path,attachment_name,attachment_mime,detected_type,detected_title,classification_json,status,converted_entity,converted_id,created_at,updated_at,source_package,sender,source_key FROM inbox_captures WHERE id=? LIMIT 1";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return inboxCaptureJson(c);
        } catch (Exception ignored) {}
        return new JSONObject();
    }

    public boolean updateInboxCaptureClassification(long id, JSONObject classification) {
        if (id <= 0 || classification == null) return false;
        ContentValues v = new ContentValues();
        v.put("detected_type", classification.optString("type", ""));
        v.put("detected_title", classification.optString("title", ""));
        v.put("classification_json", classification.toString());
        v.put("status", "reviewing");
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("inbox_captures", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean markInboxCaptureConverted(long id, String entity, long convertedId) {
        if (id <= 0 || convertedId <= 0) return false;
        ContentValues v = new ContentValues();
        v.put("status", "converted");
        v.put("converted_entity", nonBlank(entity, "item"));
        v.put("converted_id", convertedId);
        v.put("updated_at", System.currentTimeMillis());
        boolean ok = getWritableDatabase().update("inbox_captures", v, "id=?", new String[]{String.valueOf(id)}) > 0;
        if (ok) linkCalendarCaptureToItem(id, convertedId);
        return ok;
    }

    public boolean discardInboxCapture(long id) {
        if (id <= 0) return false;
        ContentValues v = new ContentValues();
        v.put("status", "discarded");
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("inbox_captures", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    private static JSONObject inboxCaptureJson(Cursor c) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", c.getLong(0));
            o.put("source", c.getString(1));
            o.put("mime", c.getString(2));
            o.put("subject", c.getString(3));
            o.put("rawText", c.getString(4));
            o.put("attachmentPath", c.getString(5));
            o.put("attachmentName", c.getString(6));
            o.put("attachmentMime", c.getString(7));
            o.put("detectedType", c.getString(8));
            o.put("detectedTitle", c.getString(9));
            String rawClassification = c.getString(10);
            try { o.put("classification", new JSONObject(rawClassification == null ? "{}" : rawClassification)); }
            catch (Exception ignored) { o.put("classification", new JSONObject()); }
            o.put("status", c.getString(11));
            o.put("convertedEntity", c.getString(12));
            o.put("convertedId", c.getLong(13));
            o.put("createdAt", c.getLong(14));
            o.put("updatedAt", c.getLong(15));
            o.put("sourcePackage", c.getString(16));
            o.put("sender", c.getString(17));
            o.put("sourceKey", c.getString(18));
        } catch (Exception ignored) {}
        return o;
    }

    public JSONArray getInbox() { return queryItems("needs_review=1", null, "created_at DESC", 200); }
    public JSONArray getFavorites() { return queryItems("favorite=1", null, "updated_at DESC", 200); }
    public JSONArray getRecentHistory() {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT action,entity_type,entity_id,label,created_at FROM history ORDER BY created_at DESC LIMIT 100", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("action",c.getString(0)); o.put("entityType",c.getString(1)); o.put("entityId",c.getLong(2)); o.put("label",c.getString(3)); o.put("createdAt",c.getLong(4)); a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public JSONObject getLastParking() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE deleted_at=0 AND type='parking' ORDER BY created_at DESC LIMIT 1", null)) {
            return c.moveToFirst() ? itemJson(c) : new JSONObject();
        }
    }

    /**
     * Conserva un único aparcamiento activo. Los anteriores quedan retirados para que
     * nunca reaparezcan al eliminar el más reciente. No cambia el esquema de la BD.
     */
    public void retirePreviousParking(long keepId) {
        if (keepId <= 0) return;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("deleted_at", now);
        v.put("updated_at", now);
        getWritableDatabase().update("items", v, "type='parking' AND deleted_at=0 AND id<>?", new String[]{String.valueOf(keepId)});
    }

    /** Elimina cualquier aparcamiento activo para que no resurja uno antiguo. */
    public boolean deleteActiveParking() {
        JSONObject current = getLastParking();
        if (current.length() == 0) return false;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("deleted_at", now);
        v.put("updated_at", now);
        int rows = getWritableDatabase().update("items", v, "type='parking' AND deleted_at=0", null);
        if (rows > 0) addHistory("aparcamiento eliminado", "parking", current.optLong("id",0), current.optString("title","Coche aparcado"));
        return rows > 0;
    }

    public long createList(String title, String kind, boolean reusable) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", nonBlank(title,"Nueva lista")); v.put("kind", nonBlank(kind,"General")); v.put("reusable", reusable?1:0); v.put("favorite",0); v.put("created_at",now); v.put("updated_at",now);
        long id = getWritableDatabase().insertOrThrow("lists", null, v);
        addHistory("lista creada","list",id,title);
        return id;
    }

    public boolean renameList(long listId, String title) {
        String value = title == null ? "" : title.trim();
        if (listId <= 0 || value.isBlank()) return false;
        JSONObject before = getList(listId);
        if (!before.has("id")) return false;
        ContentValues v = new ContentValues();
        v.put("title", value);
        v.put("updated_at", System.currentTimeMillis());
        boolean ok = getWritableDatabase().update("lists", v, "id=?", new String[]{String.valueOf(listId)}) > 0;
        if (ok) addHistory("lista renombrada", "list", listId, value);
        return ok;
    }

    public int clearList(long listId) {
        if (listId <= 0 || !getList(listId).has("id")) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int count = db.delete("list_entries", "list_id=?", new String[]{String.valueOf(listId)});
        ContentValues u = new ContentValues();
        u.put("updated_at", System.currentTimeMillis());
        db.update("lists", u, "id=?", new String[]{String.valueOf(listId)});
        if (count > 0) addHistory("lista vaciada", "list", listId, count + " elementos");
        return count;
    }

    public int resetList(long listId) {
        if (listId <= 0 || !getList(listId).has("id")) return 0;
        ContentValues v = new ContentValues();
        v.put("checked", 0);
        int count = getWritableDatabase().update("list_entries", v, "list_id=? AND checked<>0", new String[]{String.valueOf(listId)});
        if (count > 0) {
            touchList(listId);
            addHistory("lista reiniciada", "list", listId, count + " elementos");
        }
        return count;
    }

    public boolean deleteList(long listId) {
        if (listId <= 0) return false;
        JSONObject before = getList(listId);
        if (!before.has("id")) return false;
        String title = before.optString("title", "Lista");
        SQLiteDatabase db = getWritableDatabase();
        boolean ok = false;
        db.beginTransaction();
        try {
            db.delete("list_entries", "list_id=?", new String[]{String.valueOf(listId)});
            ok = db.delete("lists", "id=?", new String[]{String.valueOf(listId)}) > 0;
            if (ok) db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (ok) addHistory("lista eliminada", "list", listId, title);
        return ok;
    }

    public long duplicateList(long listId) {
        if (listId <= 0) return 0;
        JSONObject source = getList(listId);
        if (!source.has("id")) return 0;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        long newId = 0;
        db.beginTransaction();
        try {
            ContentValues list = new ContentValues();
            list.put("title", source.optString("title", "Lista") + " (copia)");
            list.put("kind", source.optString("kind", "General"));
            list.put("reusable", source.optBoolean("reusable", false) ? 1 : 0);
            list.put("favorite", 0);
            list.put("created_at", now);
            list.put("updated_at", now);
            newId = db.insertOrThrow("lists", null, list);

            try (Cursor c = db.rawQuery("SELECT text,checked,sort_order,category FROM list_entries WHERE list_id=? ORDER BY sort_order ASC,id ASC", new String[]{String.valueOf(listId)})) {
                int offset = 0;
                while (c.moveToNext()) {
                    ContentValues e = new ContentValues();
                    e.put("list_id", newId);
                    e.put("text", c.getString(0));
                    e.put("checked", c.getInt(1));
                    e.put("sort_order", c.getInt(2));
                    e.put("category", c.getString(3));
                    e.put("created_at", now + (++offset));
                    db.insertOrThrow("list_entries", null, e);
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            newId = 0;
        } finally {
            db.endTransaction();
        }
        if (newId > 0) addHistory("lista duplicada", "list", newId, source.optString("title", "Lista"));
        return newId;
    }

    private void touchList(long listId) {
        if (listId <= 0) return;
        ContentValues u = new ContentValues();
        u.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("lists", u, "id=?", new String[]{String.valueOf(listId)});
    }

    private long listIdForEntry(long entryId) {
        if (entryId <= 0) return 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT list_id FROM list_entries WHERE id=?", new String[]{String.valueOf(entryId)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Importa una lista compartida sin reutilizar IDs del dispositivo emisor. */
    public long importSharedList(JSONObject data) {
        if (data == null) return 0;
        SQLiteDatabase sql = getWritableDatabase();
        long now = System.currentTimeMillis();
        long listId = 0;
        sql.beginTransaction();
        try {
            ContentValues list = new ContentValues();
            list.put("title", nonBlank(data.optString("title"), "Lista recibida"));
            list.put("kind", nonBlank(data.optString("kind"), "General"));
            list.put("reusable", data.optBoolean("reusable", false) ? 1 : 0);
            list.put("favorite", data.optBoolean("favorite", false) ? 1 : 0);
            list.put("created_at", now); list.put("updated_at", now);
            listId = sql.insertOrThrow("lists", null, list);
            JSONArray entries = data.optJSONArray("entries");
            if (entries != null) {
                for (int i=0;i<entries.length();i++) {
                    JSONObject e=entries.optJSONObject(i); if(e==null)continue;
                    String text=e.optString("text","").trim(); if(text.isEmpty())continue;
                    ContentValues v=new ContentValues(); v.put("list_id",listId); v.put("text",text);
                    v.put("checked",e.optBoolean("checked",false)?1:0); v.put("sort_order",Math.max(0,e.optInt("sortOrder",i)));
                    v.put("category",e.optString("category","")); v.put("created_at",now+i);
                    sql.insertOrThrow("list_entries",null,v);
                }
            }
            sql.setTransactionSuccessful();
        } catch (Exception e) { listId=0; }
        finally { sql.endTransaction(); }
        if(listId>0)addHistory("lista recibida","list",listId,data.optString("title","Lista recibida"));
        return listId;
    }

    public JSONArray getLists() {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT l.id,l.title,l.kind,l.reusable,l.favorite,l.updated_at,COUNT(e.id),SUM(CASE WHEN e.checked=0 THEN 1 ELSE 0 END) FROM lists l LEFT JOIN list_entries e ON e.list_id=l.id GROUP BY l.id ORDER BY l.favorite DESC,l.updated_at DESC", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id",c.getLong(0)); o.put("title",c.getString(1)); o.put("kind",c.getString(2)); o.put("reusable",c.getInt(3)==1); o.put("favorite",c.getInt(4)==1); o.put("updatedAt",c.getLong(5)); o.put("count",c.getInt(6)); o.put("pending",c.isNull(7)?0:c.getInt(7)); a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public JSONArray getListEntries(long listId) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,text,checked,sort_order,category,created_at FROM list_entries WHERE list_id=? ORDER BY checked ASC,category ASC,sort_order ASC,id ASC", new String[]{String.valueOf(listId)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id",c.getLong(0)); o.put("text",c.getString(1)); o.put("checked",c.getInt(2)==1); o.put("sortOrder",c.getInt(3)); o.put("category",c.getString(4)); o.put("createdAt",c.getLong(5)); a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public int addListItems(long listId, String raw, boolean shopping) {
        if (listId <= 0 || raw == null) return 0;
        String[] parts = raw.split("[,;\\n]+");
        int n = 0; SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int order = (int)scalar("SELECT COALESCE(MAX(sort_order),0)+1 FROM list_entries WHERE list_id=" + listId);
            for (String p : parts) {
                String text = p.trim(); if (text.isEmpty()) continue;
                ContentValues v = new ContentValues();
                v.put("list_id",listId); v.put("text",text); v.put("checked",0); v.put("sort_order",order++); v.put("category",shopping? ProductCategorizer.category(text):""); v.put("created_at",System.currentTimeMillis());
                db.insertOrThrow("list_entries",null,v); n++;
            }
            ContentValues u = new ContentValues(); u.put("updated_at",System.currentTimeMillis()); db.update("lists",u,"id=?",new String[]{String.valueOf(listId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if (n>0) addHistory("elementos añadidos","list",listId,n+" elementos");
        return n;
    }

    public boolean toggleListEntry(long entryId, boolean checked) {
        long listId = listIdForEntry(entryId);
        ContentValues v = new ContentValues(); v.put("checked",checked?1:0);
        boolean ok = getWritableDatabase().update("list_entries",v,"id=?",new String[]{String.valueOf(entryId)})>0;
        if (ok) touchList(listId);
        return ok;
    }

    public boolean updateListEntry(long entryId, String text, boolean shopping) {
        String value = text == null ? "" : text.trim();
        if (entryId <= 0 || value.isBlank()) return false;
        ContentValues v = new ContentValues();
        v.put("text", value);
        v.put("category", shopping ? ProductCategorizer.category(value) : "");
        boolean ok = getWritableDatabase().update("list_entries",v,"id=?",new String[]{String.valueOf(entryId)})>0;
        if (ok) {
            try (Cursor c=getReadableDatabase().rawQuery("SELECT list_id FROM list_entries WHERE id=?",new String[]{String.valueOf(entryId)})) {
                if(c.moveToFirst()){ContentValues u=new ContentValues();u.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("lists",u,"id=?",new String[]{String.valueOf(c.getLong(0))});}
            } catch(Exception ignored){}
        }
        return ok;
    }

    public boolean deleteListEntry(long entryId) {
        long listId = listIdForEntry(entryId);
        boolean ok = getWritableDatabase().delete("list_entries","id=?",new String[]{String.valueOf(entryId)})>0;
        if (ok) touchList(listId);
        return ok;
    }

    public int deleteCheckedListEntries(long listId) {
        if (listId <= 0) return 0;
        int count=getWritableDatabase().delete("list_entries","list_id=? AND checked=1",new String[]{String.valueOf(listId)});
        if(count>0){ContentValues u=new ContentValues();u.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("lists",u,"id=?",new String[]{String.valueOf(listId)});addHistory("comprados eliminados","list",listId,count+" elementos");}
        return count;
    }

    public JSONObject getList(long id) {
        JSONObject o = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,kind,reusable,favorite,created_at,updated_at FROM lists WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) { o.put("id",c.getLong(0)); o.put("title",c.getString(1)); o.put("kind",c.getString(2)); o.put("reusable",c.getInt(3)==1); o.put("favorite",c.getInt(4)==1); o.put("createdAt",c.getLong(5)); o.put("updatedAt",c.getLong(6)); }
        } catch (Exception ignored) {}
        return o;
    }

    public String bestLearnedCategory(String text) {
        String[] terms = QueryInterpreter.importantTerms(text);
        if (terms.length == 0) return "";
        HashMap<String,Integer> scores = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        for (String t : terms) {
            try (Cursor c = db.rawQuery("SELECT category,weight FROM learned_terms WHERE term=?", new String[]{t})) {
                while (c.moveToNext()) scores.merge(c.getString(0),c.getInt(1),Integer::sum);
            }
        }
        String best=""; int max=0;
        for (Map.Entry<String,Integer> e:scores.entrySet()) if (e.getValue()>max){max=e.getValue();best=e.getKey();}
        return max>=2?best:"";
    }

    private void learnFromCorrection(String text, String category) {
        String[] terms = QueryInterpreter.importantTerms(text);
        SQLiteDatabase db = getWritableDatabase();
        for (String t : terms) {
            if (t.length()<4) continue;
            int current = 0;
            try (Cursor c = db.rawQuery("SELECT weight FROM learned_terms WHERE term=? AND category=?", new String[]{t, category})) {
                if (c.moveToFirst()) current = c.getInt(0);
            }
            ContentValues v = new ContentValues(); v.put("term", t); v.put("category", category); v.put("weight", current + 1);
            db.insertWithOnConflict("learned_terms", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private JSONArray queryItems(String where, String[] args, String order, int limit) {
        JSONArray a = new JSONArray();
        String sql = "SELECT * FROM items WHERE deleted_at=0 AND (" + where + ") ORDER BY " + order + " LIMIT " + limit;
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) a.put(itemJson(c));
        } catch (Exception ignored) {}
        return a;
    }

    private JSONArray queryReminders(long from, long to, int limit) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,item_id,title,trigger_at,occurrence_at,lead_days,lead_minutes,enabled,created_at FROM reminders WHERE enabled=1 AND item_id IN (SELECT id FROM items WHERE deleted_at=0 AND type NOT IN ('place','restaurant','document')) AND trigger_at>=? AND trigger_at<=? ORDER BY trigger_at ASC LIMIT " + limit, new String[]{String.valueOf(from),String.valueOf(to)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id",c.getLong(0)); o.put("itemId",c.getLong(1)); o.put("title",c.getString(2)); o.put("triggerAt",c.getLong(3)); o.put("occurrenceAt",c.getLong(4)); o.put("leadDays",c.getInt(5)); o.put("leadMinutes",c.getInt(6)); o.put("enabled",c.getInt(7)==1); o.put("createdAt",c.getLong(8)); a.put(o);
            }
        } catch (Exception ignored) {}
        return a;
    }

    private JSONObject itemJson(Cursor c) {
        JSONObject o = new JSONObject();
        try {
            o.put("id",c.getLong(c.getColumnIndexOrThrow("id")));
            o.put("type",c.getString(c.getColumnIndexOrThrow("type")));
            o.put("title",c.getString(c.getColumnIndexOrThrow("title")));
            o.put("body",c.getString(c.getColumnIndexOrThrow("body")));
            o.put("originalText",c.getString(c.getColumnIndexOrThrow("original_text")));
            o.put("category",c.getString(c.getColumnIndexOrThrow("category")));
            o.put("subcategory",c.getString(c.getColumnIndexOrThrow("subcategory")));
            o.put("tags",c.getString(c.getColumnIndexOrThrow("tags")));
            o.put("dueAt",c.getLong(c.getColumnIndexOrThrow("due_at")));
            int endIndex=c.getColumnIndex("end_at");o.put("endAt",endIndex>=0?c.getLong(endIndex):0L);
            o.put("hasTime",c.getInt(c.getColumnIndexOrThrow("has_time"))==1);
            int allDayIndex=c.getColumnIndex("all_day");o.put("allDay",allDayIndex>=0&&c.getInt(allDayIndex)==1);
            int tzIndex=c.getColumnIndex("time_zone");o.put("timeZone",tzIndex>=0?c.getString(tzIndex):"Europe/Madrid");
            int mapsIndex=c.getColumnIndex("maps_url");o.put("mapsUrl",mapsIndex>=0?c.getString(mapsIndex):"");
            int ruleIndex=c.getColumnIndex("recurrence_rule");o.put("recurrenceRule",ruleIndex>=0?c.getString(ruleIndex):"");
            int participantsIndex=c.getColumnIndex("participants_text");o.put("participantsText",participantsIndex>=0?c.getString(participantsIndex):"");
            o.put("deletedAt",c.getLong(c.getColumnIndexOrThrow("deleted_at")));
            int li=c.getColumnIndexOrThrow("lat"),lo=c.getColumnIndexOrThrow("lon");
            if (!c.isNull(li)) o.put("lat",c.getDouble(li)); else o.put("lat",JSONObject.NULL);
            if (!c.isNull(lo)) o.put("lon",c.getDouble(lo)); else o.put("lon",JSONObject.NULL);
            o.put("locationText",c.getString(c.getColumnIndexOrThrow("location_text")));
            o.put("priority",c.getInt(c.getColumnIndexOrThrow("priority")));
            o.put("favorite",c.getInt(c.getColumnIndexOrThrow("favorite"))==1);
            o.put("source",c.getString(c.getColumnIndexOrThrow("source")));
            o.put("attachmentPath",c.getString(c.getColumnIndexOrThrow("attachment_path")));
            o.put("attachmentName",c.getString(c.getColumnIndexOrThrow("attachment_name")));
            o.put("attachmentMime",c.getString(c.getColumnIndexOrThrow("attachment_mime")));
            o.put("status",c.getString(c.getColumnIndexOrThrow("status")));
            o.put("needsReview",c.getInt(c.getColumnIndexOrThrow("needs_review"))==1);
            o.put("recurrence",c.getString(c.getColumnIndexOrThrow("recurrence")));
            o.put("recurrenceUntil",c.getLong(c.getColumnIndexOrThrow("recurrence_until")));
            o.put("durationMinutes",c.getInt(c.getColumnIndexOrThrow("duration_minutes")));
            int voiceIndex=c.getColumnIndex("voice_enabled");
            o.put("voiceEnabled",voiceIndex>=0 && c.getInt(voiceIndex)==1);
            int noteTitleManualIndex=c.getColumnIndex("note_title_manual");
            o.put("noteTitleManual",noteTitleManualIndex>=0 && c.getInt(noteTitleManualIndex)==1);
            o.put("createdAt",c.getLong(c.getColumnIndexOrThrow("created_at")));
            o.put("updatedAt",c.getLong(c.getColumnIndexOrThrow("updated_at")));
        } catch (Exception ignored) {}
        return o;
    }

    private JSONObject withResultKind(JSONObject o,String kind){ try{o.put("resultKind",kind);}catch(Exception ignored){} return o; }

    private long scalar(String sql) {
        try (Cursor c = getReadableDatabase().rawQuery(sql,null)) { return c.moveToFirst()?c.getLong(0):0; }
    }

    private void addHistory(String action,String type,long id,String label) {
        ContentValues v = new ContentValues(); v.put("action",action); v.put("entity_type",type); v.put("entity_id",id); v.put("label",label==null?"":label); v.put("created_at",System.currentTimeMillis());
        getWritableDatabase().insert("history",null,v);
    }

    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String cleanJsonString(JSONObject source,String key){
        if(source==null||key==null||!source.has(key)||source.isNull(key))return "";
        Object value=source.opt(key);
        if(value==null||value==JSONObject.NULL)return "";
        String text=String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text)?"":text;
    }
    private static String nonBlank(String s,String fallback){return s==null||s.trim().isEmpty()?fallback:s.trim();}
}
