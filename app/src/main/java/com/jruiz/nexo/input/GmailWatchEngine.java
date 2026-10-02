package com.jruiz.nexo.input;

import android.content.Context;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.jruiz.nexo.communications.NotificationCandidateFilter;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GmailWatchEngine {
    public static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";
    public static final Scope GMAIL_SCOPE = new Scope(GMAIL_READONLY);
    private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";

    // No se limita a INBOX: un filtro/regla de Gmail puede archivar automáticamente
    // un correo relevante. Se excluyen enviados y borradores, no el correo recibido.
    private static final String QUERY = "newer_than:30d -in:sent -in:drafts";
    private static final String FILTER_REVISION = "gmail-filter-v7:";
    private static final long DEDUPE_WINDOW_MS = 45L * 60L * 1000L;
    private static final int MAX_TEXT_ATTACHMENT_BYTES = 256 * 1024;

    private GmailWatchEngine() {}

    public static GoogleSignInOptions signInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(GMAIL_SCOPE)
                .build();
    }

    public static boolean hasAuthorizedAccount(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && GoogleSignIn.hasPermissions(account, GMAIL_SCOPE);
    }

    public static String authorizedEmail(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account == null || account.getEmail() == null ? "" : account.getEmail();
    }

    public static void initializeBaseline(Context context) {
        if (!InputHubPrefs.gmailEnabled(context)) return;
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null || !GoogleSignIn.hasPermissions(account, GMAIL_SCOPE)) {
            InputHubPrefs.setGmailError(context, "Autoriza Gmail desde Ajustes.");
            return;
        }

        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            String token = GoogleAuthUtil.getToken(context, account.getAccount(), "oauth2:" + GMAIL_READONLY);
            for (String id : listMessageIds(token, QUERY)) {
                if (!db.hasExternalInput("gmail", id)) {
                    db.rememberExternalInput("gmail", id, "gmail-baseline:" + id, 0L);
                }
            }
            InputHubPrefs.setGmailEmail(context, account.getEmail());
            InputHubPrefs.setGmailBaselined(context, true);
            InputHubPrefs.setGmailLastSync(context, System.currentTimeMillis());
            InputHubPrefs.setGmailError(context, "");
            InputHubPrefs.setGmailErrorDetail(context, "");
            GmailWatchScheduler.schedule(context);
        } catch (Exception e) {
            InputHubPrefs.setGmailError(context, readableError(e));
            InputHubPrefs.setGmailErrorDetail(context, "Gmail API: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " · " + e.getMessage()));
        } finally {
            db.close();
        }
    }

    public static void scan(Context context) {
        if (!InputHubPrefs.gmailEnabled(context)) return;
        if (!InputHubPrefs.gmailBaselined(context)) {
            initializeBaseline(context);
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null || !GoogleSignIn.hasPermissions(account, GMAIL_SCOPE)) {
            InputHubPrefs.setGmailError(context, "La autorización de Gmail ya no está disponible.");
            return;
        }

        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            String token = GoogleAuthUtil.getToken(context, account.getAccount(), "oauth2:" + GMAIL_READONLY);
            boolean hadProcessingError = false;

            for (String id : listMessageIds(token, QUERY)) {
                JSONObject previous = db.getExternalInputState("gmail", id);
                if (previous.length() > 0) {
                    long captureId = previous.optLong("captureId", 0L);
                    String fingerprint = previous.optString("fingerprint", "");
                    if (captureId > 0 || db.inboxCaptureExists("gmail:" + id) ||
                            fingerprint.startsWith(FILTER_REVISION)) {
                        continue;
                    }
                    // Los errores de red/parsing se reintentan. Los baselines y filtros anteriores
                    // nunca se reevalúan automáticamente: el histórico requiere una acción explícita.
                    if (fingerprint.startsWith("gmail-error:")) {
                        if (!processMessage(token, db, context, id)) hadProcessingError = true;
                        continue;
                    }
                    continue;
                }
                if (!processMessage(token, db, context, id)) hadProcessingError = true;
            }

            InputHubPrefs.setGmailEmail(context, account.getEmail());
            InputHubPrefs.setGmailLastSync(context, System.currentTimeMillis());
            if (!hadProcessingError) {
                InputHubPrefs.setGmailError(context, "");
                InputHubPrefs.setGmailErrorDetail(context, "");
            }
        } catch (Exception e) {
            InputHubPrefs.setGmailError(context, readableError(e));
            InputHubPrefs.setGmailErrorDetail(context, "Gmail API: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " · " + e.getMessage()));
        } finally {
            db.close();
        }
    }

    private static List<String> listMessageIds(String token, String query) throws Exception {
        ArrayList<String> ids = new ArrayList<>();
        String page = "";
        int pages = 0;
        do {
            String url = API + "/messages?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8.name()) +
                    "&maxResults=500&fields=messages/id,nextPageToken";
            if (!page.isBlank()) {
                url += "&pageToken=" + URLEncoder.encode(page, StandardCharsets.UTF_8.name());
            }
            JSONObject json = getJson(url, token);
            JSONArray messages = json.optJSONArray("messages");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject m = messages.optJSONObject(i);
                    String id = m == null ? "" : m.optString("id", "");
                    if (!id.isBlank()) ids.add(id);
                }
            }
            page = json.optString("nextPageToken", "");
            pages++;
        } while (!page.isBlank() && pages < 4);
        return ids;
    }

    private static boolean processMessage(
            String token,
            DatabaseHelper db,
            Context context,
            String id
    ) {
        try {
            GmailMessage message = fetchMessage(token, id);
            long captureId = 0L;
            boolean candidate = false;

            if (message != null) {
                String text = message.analysisText();
                JSONObject classification = RuleClassifier.classify(text).toJson();
                candidate = NotificationCandidateFilter.shouldCaptureGmail(text, classification);

                if (candidate) {
                    // Gmail puede haber llegado primero por NotificationListener.
                    captureId = db.findRecentEquivalentInboxCapture(
                            "gmail", message.from, text, DEDUPE_WINDOW_MS
                    );
                    if (captureId <= 0L) {
                        JSONObject in = new JSONObject();
                        in.put("source", "gmail-api");
                        in.put("mime", "message/rfc822");
                        in.put("subject", message.subject);
                        in.put("text", text);
                        in.put("sourcePackage", "gmail-api");
                        in.put("sender", message.from);
                        in.put("sourceKey", "gmail:" + id);
                        captureId = db.insertInboxCapture(in, classification);

                        if (captureId > 0) {
                            String preview = message.subject;
                            if (!message.snippet.isBlank()) preview += " · " + message.snippet;
                            if (preview.length() > 180) preview = preview.substring(0, 177) + "…";
                            InboxCandidateNotifier.show(
                                    context, captureId, "Nexo · correo para revisar", message.from, preview
                            );
                        }
                    }
                }
            }

            if (candidate && captureId <= 0L) {
                throw new IllegalStateException("No se pudo guardar la captura de Gmail.");
            }

            String decision = captureId > 0L ? "captured:" : "rejected:";
            db.rememberExternalInput("gmail", id, FILTER_REVISION + decision + id, captureId);
            return true;
        } catch (Exception e) {
            // No consolidamos un fallo de red/parsing como descarte. La siguiente
            // consulta podrá reintentar el mismo mensaje.
            db.rememberExternalInput("gmail", id, "gmail-error:" + id, 0L);
            InputHubPrefs.setGmailError(context, readableError(e));
            return false;
        }
    }

    private static GmailMessage fetchMessage(String token, String id) throws Exception {
        String url = API + "/messages/" + URLEncoder.encode(id, StandardCharsets.UTF_8.name()) +
                "?format=full&fields=id,internalDate,snippet,payload";
        JSONObject json = getJson(url, token);
        String subject = "", from = "";
        JSONObject payload = json.optJSONObject("payload");
        JSONArray headers = payload == null ? null : payload.optJSONArray("headers");
        if (headers != null) {
            for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.optJSONObject(i);
                if (h == null) continue;
                String name = h.optString("name", "");
                if ("Subject".equalsIgnoreCase(name)) subject = h.optString("value", "");
                else if ("From".equalsIgnoreCase(name)) from = h.optString("value", "");
            }
        }

        String snippet = json.optString("snippet", "");
        String bodyText = extractReadableBody(payload, token, id);
        long internalDate = 0L;
        try { internalDate = Long.parseLong(json.optString("internalDate", "0")); }
        catch (Exception ignored) {}

        return new GmailMessage(id, from, subject, snippet, bodyText, internalDate);
    }

    private static String extractReadableBody(JSONObject payload, String token, String messageId) {
        StringBuilder out = new StringBuilder();
        collectPartText(payload, out, 0, token, messageId);
        String text = out.toString()
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        if (text.length() > 12000) text = text.substring(0, 12000);
        return text;
    }

    private static void collectPartText(
            JSONObject part,
            StringBuilder out,
            int depth,
            String token,
            String messageId
    ) {
        if (part == null || depth > 8 || out.length() > 14000) return;
        String mime = part.optString("mimeType", "").toLowerCase(java.util.Locale.ROOT);
        boolean readable = mime.startsWith("text/plain") || mime.startsWith("text/html") ||
                mime.startsWith("text/calendar") || mime.isBlank();

        JSONObject body = part.optJSONObject("body");
        String data = body == null ? "" : body.optString("data", "");
        String attachmentId = body == null ? "" : body.optString("attachmentId", "");
        int size = body == null ? 0 : body.optInt("size", 0);

        if (readable && data.isBlank() && !attachmentId.isBlank() &&
                (size <= 0 || size <= MAX_TEXT_ATTACHMENT_BYTES)) {
            try {
                String url = API + "/messages/" +
                        URLEncoder.encode(messageId, StandardCharsets.UTF_8.name()) +
                        "/attachments/" +
                        URLEncoder.encode(attachmentId, StandardCharsets.UTF_8.name()) +
                        "?fields=data,size";
                JSONObject attachment = getJson(url, token);
                data = attachment.optString("data", "");
            } catch (Exception ignored) {
                // El asunto/snippet y otras partes siguen disponibles aunque falle una parte.
            }
        }

        if (readable && !data.isBlank()) {
            try {
                byte[] decoded = android.util.Base64.decode(
                        data,
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP
                );
                if (decoded.length <= MAX_TEXT_ATTACHMENT_BYTES) {
                    String text = new String(decoded, StandardCharsets.UTF_8);
                    if (!text.isBlank()) {
                        if (out.length() > 0) out.append("\n");
                        out.append(text);
                    }
                }
            } catch (Exception ignored) {}
        }

        JSONArray parts = part.optJSONArray("parts");
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                collectPartText(parts.optJSONObject(i), out, depth + 1, token, messageId);
                if (out.length() > 14000) break;
            }
        }
    }

    private static JSONObject getJson(String endpoint, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(18000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) body.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("Gmail API HTTP " + code);
        return new JSONObject(body.toString());
    }

    private static String readableError(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage());
        if (m.contains("403")) return "Gmail API no está habilitada o la cuenta no tiene autorización.";
        if (m.contains("401")) return "La autorización de Gmail ha caducado. Vuelve a conectar la cuenta.";
        return m.isBlank() ? "No se pudo consultar Gmail." : m;
    }

    private static final class GmailMessage {
        final String id, from, subject, snippet, bodyText;
        final long internalDate;

        GmailMessage(String id, String from, String subject, String snippet, String bodyText, long internalDate) {
            this.id = id;
            this.from = from == null ? "" : from;
            this.subject = subject == null ? "" : subject;
            this.snippet = snippet == null ? "" : snippet;
            this.bodyText = bodyText == null ? "" : bodyText;
            this.internalDate = internalDate;
        }

        String analysisText() {
            StringBuilder b = new StringBuilder();
            if (!subject.isBlank()) b.append(subject);
            if (!snippet.isBlank()) {
                if (b.length() > 0) b.append("\n");
                b.append(snippet);
            }
            if (!bodyText.isBlank()) {
                if (b.length() > 0) b.append("\n");
                b.append(bodyText);
            }
            String text = b.toString();
            return text.length() > 14000 ? text.substring(0, 14000) : text;
        }
    }
}
