package com.jruiz.nexo.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Vinculación y sincronización unidireccional Nexo Familia -> Nexo Android.
 *
 * La contraseña nunca se persiste. Se usa únicamente para obtener una sesión de
 * Supabase Auth. Access/refresh tokens se guardan cifrados con Android Keystore.
 * Tras la migración inicial, los UUID ya conocidos no se vuelven a tocar.
 */
public final class NexoFamiliaCloudSync {
    private static final String BASE_URL = "https://gimzsrreghmuuwmizojc.supabase.co";
    private static final String API_KEY = "sb_publishable_0a0J8fcCZH-07wWeGY4Qbg_ZKZCKbgv";

    private static final String PREFS = "nexo-familia-cloud";
    private static final String KEYSTORE_ALIAS = "nexo_familia_sync_session_v1";
    private static final String PREF_SESSION_IV = "session_iv";
    private static final String PREF_SESSION_BLOB = "session_blob";
    private static final String PREF_BASELINE = "online_baseline_completed";
    private static final String PREF_LAST_SYNC = "last_sync_at";
    private static final String PREF_MEMBERS = "cached_members";

    private static final int PAGE_SIZE = 1000;

    private final Context context;
    private final DatabaseHelper db;
    private final SharedPreferences prefs;

    public NexoFamiliaCloudSync(Context context, DatabaseHelper db) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONObject getStatus() {
        JSONObject out = new JSONObject();
        try {
            JSONObject session = readSession();
            boolean connected = session != null
                    && !session.optString("refreshToken", "").isBlank()
                    && !session.optString("email", "").isBlank();
            JSONObject stats = db.getNexoFamiliaImportStats();
            out.put("connected", connected);
            out.put("email", connected ? session.optString("email", "") : "");
            out.put("baselineCompleted", prefs.getBoolean(PREF_BASELINE, false));
            out.put("lastSyncAt", prefs.getLong(PREF_LAST_SYNC, 0L));
            out.put("importedEvents", stats.optLong("events", 0L));
            out.put("knownEventIds", stats.optLong("mappings", 0L));
            out.put("localAppointments", db.countLocalAppointments());
        } catch (Exception e) {
            try {
                out.put("connected", false);
                out.put("error", safeMessage(e));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject link(String email, String password) {
        JSONObject out = new JSONObject();
        try {
            String cleanEmail = email == null ? "" : email.trim();
            if (cleanEmail.isBlank()) return out.put("ok", false).put("error", "Escribe el correo de Nexo Familia.");
            if (password == null || password.isBlank()) return out.put("ok", false).put("error", "Escribe la contraseña de Nexo Familia.");

            JSONObject body = new JSONObject();
            body.put("email", cleanEmail);
            body.put("password", password);
            JSONObject response = requestObject(
                    "POST",
                    BASE_URL + "/auth/v1/token?grant_type=password",
                    body,
                    null,
                    false
            );

            JSONObject user = response.optJSONObject("user");
            String access = response.optString("access_token", "");
            String refresh = response.optString("refresh_token", "");
            long expiresIn = Math.max(60L, response.optLong("expires_in", 3600L));
            if (access.isBlank() || refresh.isBlank() || user == null) {
                return out.put("ok", false).put("error", "Supabase no devolvió una sesión válida.");
            }

            JSONObject session = new JSONObject();
            session.put("email", cleanEmail);
            session.put("userId", user.optString("id", ""));
            session.put("accessToken", access);
            session.put("refreshToken", refresh);
            session.put("expiresAt", System.currentTimeMillis() + expiresIn * 1000L);
            saveSession(session);

            out.put("ok", true);
            out.put("email", cleanEmail);
            out.put("baselineCompleted", prefs.getBoolean(PREF_BASELINE, false));
        } catch (HttpFailure e) {
            try {
                out.put("ok", false).put("error", authError(e));
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                out.put("ok", false).put("error", safeMessage(e));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject syncNewEvents() {
        JSONObject out = new JSONObject();
        try {
            String token = validAccessToken();
            JSONObject payload = fetchPayload(token);

            JSONObject validation = NexoFamiliaImporter.validatePayloadIntegrity(payload);
            if (!validation.optBoolean("ok", false)) {
                return out.put("ok", false)
                        .put("error", "Los datos recibidos de Nexo Familia no superan la validación.")
                        .put("errors", validation.optJSONArray("errors"));
            }

            boolean firstBaseline = !prefs.getBoolean(PREF_BASELINE, false);
            JSONArray sourceEvents = payload.optJSONArray("events");
            if (firstBaseline && (sourceEvents == null || sourceEvents.length() == 0)) {
                return out.put("ok", false)
                        .put("error", "No se ha recibido ningún evento de Nexo Familia. La agenda local no se ha modificado.");
            }

            int removedLocalAppointments = 0;
            JSONObject result;

            if (firstBaseline) {
                removedLocalAppointments = db.countLocalAppointments();
                JSONArray reminderIds = new JSONArray();
                SQLiteDatabase sql = db.getWritableDatabase();
                sql.beginTransaction();
                try {
                    reminderIds = db.resetAllAppointmentsForNexoFamiliaMigration();
                    result = NexoFamiliaImporter.importPayload(context, db, payload);
                    if (result.optBoolean("ok", false)) sql.setTransactionSuccessful();
                } finally {
                    sql.endTransaction();
                }

                if (!result.optBoolean("ok", false)) return result;

                // Solo después de confirmar la transacción se modifican las alarmas del sistema.
                for (int i = 0; i < reminderIds.length(); i++) {
                    ReminderScheduler.cancel(context, reminderIds.optLong(i));
                }
                scheduleCommittedFamiliaReminders();
                prefs.edit().putBoolean(PREF_BASELINE, true).apply();
            } else {
                result = NexoFamiliaImporter.importNewOnly(context, db, payload);
            }

            if (!result.optBoolean("ok", false)) return result;

            JSONArray members = payload.optJSONArray("members");
            if (members != null) prefs.edit().putString(PREF_MEMBERS, members.toString()).apply();
            long now = System.currentTimeMillis();
            prefs.edit().putLong(PREF_LAST_SYNC, now).apply();

            db.reconcileTemporalStatuses();

            result.put("firstBaseline", firstBaseline);
            result.put("removedLocalAppointments", removedLocalAppointments);
            result.put("sourceEvents", payload.optJSONArray("events") == null ? 0 : payload.optJSONArray("events").length());
            result.put("lastSyncAt", now);
            result.put("baselineCompleted", prefs.getBoolean(PREF_BASELINE, false));
            return result;
        } catch (HttpFailure e) {
            try {
                if (e.status == 401 || e.status == 403) {
                    clearSession();
                    out.put("sessionExpired", true);
                }
                out.put("ok", false).put("error", apiError(e));
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                out.put("ok", false).put("error", safeMessage(e));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private void scheduleCommittedFamiliaReminders() {
        JSONArray rows = db.getNexoFamiliaPendingReminderSchedules();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            long id = row.optLong("id", 0L);
            long triggerAt = row.optLong("triggerAt", 0L);
            if (id <= 0L || triggerAt <= System.currentTimeMillis()) continue;
            ReminderScheduler.schedule(context, id, row.optString("title", "Recordatorio"), triggerAt);
        }
    }

    public void disconnect() {
        clearSession();
    }

    public JSONArray getCachedMembers() {
        try {
            String raw = prefs.getString(PREF_MEMBERS, "[]");
            return new JSONArray(raw == null || raw.isBlank() ? "[]" : raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONObject fetchPayload(String accessToken) throws Exception {
        // Nexo Android sincroniza la agenda familiar. Las reservas de vehículo
        // comparten tabla en Nexo Familia desde 2026-10-03, pero no son eventos
        // de agenda y no deben convertirse en citas Android.
        JSONArray events = restArray("/rest/v1/events?select=*&event_kind=eq.event&order=created_at.asc", accessToken);
        JSONArray allRules = restArray("/rest/v1/event_reminder_rules?select=*&order=created_at.asc", accessToken);
        JSONArray memberships = restArray("/rest/v1/family_members?select=family_id,user_id,role,status&status=eq.active", accessToken);
        JSONArray profiles = restArray("/rest/v1/profiles?select=id,full_name,color", accessToken);

        Map<String, JSONObject> profileById = new HashMap<>();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile == null) continue;
            String id = profile.optString("id", "").trim();
            if (!id.isBlank()) profileById.put(id, profile);
        }

        JSONArray members = new JSONArray();
        Set<String> seenMembers = new HashSet<>();
        for (int i = 0; i < memberships.length(); i++) {
            JSONObject membership = memberships.optJSONObject(i);
            if (membership == null) continue;
            String userId = membership.optString("user_id", "").trim();
            if (userId.isBlank() || !seenMembers.add(userId)) continue;
            JSONObject profile = profileById.get(userId);
            JSONObject member = new JSONObject();
            member.put("user_id", userId);
            member.put("family_id", membership.optString("family_id", ""));
            member.put("role", membership.optString("role", "member"));
            member.put("full_name", profile == null ? "Miembro" : profile.optString("full_name", "Miembro"));
            member.put("color", profile == null ? "#3158D4" : profile.optString("color", "#3158D4"));
            members.put(member);
        }

        // Conserva únicamente avisos cuyo evento forma parte de la agenda
        // sincronizable. Esto protege futuras extensiones de la tabla events.
        Set<String> eventIds = new HashSet<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null) {
                String id = event.optString("id", "").trim();
                if (!id.isBlank()) eventIds.add(id);
            }
        }
        JSONArray rules = new JSONArray();
        for (int i = 0; i < allRules.length(); i++) {
            JSONObject rule = allRules.optJSONObject(i);
            if (rule == null) continue;
            if (eventIds.contains(rule.optString("event_id", "").trim())) rules.put(rule);
        }

        JSONObject audit = new JSONObject();
        audit.put("familyEventCount", events.length());
        audit.put("reminderRuleCount", rules.length());

        JSONObject payload = new JSONObject();
        payload.put("format", NexoFamiliaImporter.FORMAT);
        payload.put("snapshotVersion", "supabase-" + System.currentTimeMillis());
        payload.put("events", events);
        payload.put("reminderRules", rules);
        payload.put("members", members);
        payload.put("sourceAudit", audit);
        return payload;
    }

    private JSONArray restArray(String path, String initialToken) throws Exception {
        JSONArray all = new JSONArray();
        int from = 0;
        String token = initialToken;

        while (true) {
            try {
                JSONArray page = requestArray("GET", BASE_URL + path, null, token, true, from, from + PAGE_SIZE - 1);
                for (int i = 0; i < page.length(); i++) all.put(page.get(i));
                if (page.length() < PAGE_SIZE) break;
                from += PAGE_SIZE;
            } catch (HttpFailure e) {
                if ((e.status == 401 || e.status == 403) && from == 0) {
                    token = refreshSession();
                    continue;
                }
                throw e;
            }
        }
        return all;
    }

    private String validAccessToken() throws Exception {
        JSONObject session = readSession();
        if (session == null || session.optString("refreshToken", "").isBlank()) {
            throw new IllegalStateException("Vincula primero tu usuario de Nexo Familia.");
        }
        long expiresAt = session.optLong("expiresAt", 0L);
        String access = session.optString("accessToken", "");
        if (!access.isBlank() && expiresAt > System.currentTimeMillis() + 90_000L) return access;
        return refreshSession();
    }

    private String refreshSession() throws Exception {
        JSONObject session = readSession();
        if (session == null) throw new IllegalStateException("Vuelve a vincular Nexo Familia.");
        String refresh = session.optString("refreshToken", "");
        if (refresh.isBlank()) throw new IllegalStateException("Vuelve a vincular Nexo Familia.");

        JSONObject body = new JSONObject().put("refresh_token", refresh);
        JSONObject response = requestObject(
                "POST",
                BASE_URL + "/auth/v1/token?grant_type=refresh_token",
                body,
                null,
                false
        );

        String access = response.optString("access_token", "");
        String nextRefresh = response.optString("refresh_token", refresh);
        long expiresIn = Math.max(60L, response.optLong("expires_in", 3600L));
        if (access.isBlank()) throw new IllegalStateException("La sesión de Nexo Familia ha caducado.");

        session.put("accessToken", access);
        session.put("refreshToken", nextRefresh);
        session.put("expiresAt", System.currentTimeMillis() + expiresIn * 1000L);
        saveSession(session);
        return access;
    }

    private JSONObject requestObject(String method, String url, JSONObject body, String bearer, boolean rest) throws Exception {
        String raw = request(method, url, body, bearer, rest, -1, -1);
        return new JSONObject(raw == null || raw.isBlank() ? "{}" : raw);
    }

    private JSONArray requestArray(String method, String url, JSONObject body, String bearer, boolean rest,
                                   int rangeStart, int rangeEnd) throws Exception {
        String raw = request(method, url, body, bearer, rest, rangeStart, rangeEnd);
        return new JSONArray(raw == null || raw.isBlank() ? "[]" : raw);
    }

    private String request(String method, String urlValue, JSONObject body, String bearer, boolean rest,
                           int rangeStart, int rangeEnd) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("apikey", API_KEY);
        connection.setRequestProperty("User-Agent", "Nexo-Android/NexoFamiliaSync");
        if (bearer != null && !bearer.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        if (rest && rangeStart >= 0 && rangeEnd >= rangeStart) {
            connection.setRequestProperty("Range", rangeStart + "-" + rangeEnd);
            connection.setRequestProperty("Range-Unit", "items");
        }

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = readText(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) throw new HttpFailure(status, response);
        return response;
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private void saveSession(JSONObject session) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(PREF_SESSION_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(PREF_SESSION_BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    private JSONObject readSession() {
        try {
            String ivRaw = prefs.getString(PREF_SESSION_IV, "");
            String blobRaw = prefs.getString(PREF_SESSION_BLOB, "");
            if (ivRaw == null || blobRaw == null || ivRaw.isBlank() || blobRaw.isBlank()) return null;

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
            if (key == null) return null;

            byte[] iv = Base64.decode(ivRaw, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(blobRaw, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] clear = cipher.doFinal(encrypted);
            return new JSONObject(new String(clear, StandardCharsets.UTF_8));
        } catch (Exception e) {
            clearSession();
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private void clearSession() {
        prefs.edit().remove(PREF_SESSION_IV).remove(PREF_SESSION_BLOB).apply();
    }

    private static String authError(HttpFailure failure) {
        String detail = parseErrorMessage(failure.body);
        if (failure.status == 400 || failure.status == 401) {
            if (detail.toLowerCase().contains("invalid login")) {
                return "Correo o contraseña de Nexo Familia incorrectos.";
            }
        }
        return detail.isBlank() ? "No se pudo iniciar sesión en Nexo Familia." : detail;
    }

    private static String apiError(HttpFailure failure) {
        String detail = parseErrorMessage(failure.body);
        if (failure.status == 401 || failure.status == 403) {
            return "La sesión de Nexo Familia ya no es válida. Vuelve a vincularla.";
        }
        return detail.isBlank() ? "No se pudo consultar Nexo Familia." : detail;
    }

    private static String parseErrorMessage(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            JSONObject o = new JSONObject(raw);
            String[] keys = {"msg", "message", "error_description", "error", "details", "hint"};
            for (String key : keys) {
                String value = o.optString(key, "").trim();
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) {}
        return raw.length() > 220 ? raw.substring(0, 220) : raw;
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        return message == null || message.isBlank() ? "No se pudo completar la sincronización." : message;
    }

    private static final class HttpFailure extends Exception {
        final int status;
        final String body;

        HttpFailure(int status, String body) {
            super("HTTP " + status);
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }
}
