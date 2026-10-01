package com.jruiz.nexo;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.provider.AlarmClock;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.*;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.webkit.*;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import com.jruiz.nexo.core.NexoService;
import com.jruiz.nexo.core.PlaceEntry;
import com.jruiz.nexo.calendar.CalendarExporter;
import com.jruiz.nexo.calendar.CalendarImporter;
import com.jruiz.nexo.calendar.CalendarCapturePrefs;
import com.jruiz.nexo.calendar.CalendarWatchEngine;
import com.jruiz.nexo.calendar.CalendarWatchScheduler;
import com.jruiz.nexo.calendar.HolidayCalendarService;
import com.jruiz.nexo.exchange.NexoExchangeManager;
import com.jruiz.nexo.backup.NexoBackupManager;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;
import com.jruiz.nexo.medications.MedicationScheduler;
import com.jruiz.nexo.medications.MedicationVoiceSpeaker;
import com.jruiz.nexo.medications.MedicationMessageConfig;
import com.jruiz.nexo.core.MedicationParser;
import com.jruiz.nexo.core.ListVoiceParser;
import com.jruiz.nexo.core.AlarmIntentParser;
import com.jruiz.nexo.core.UserMessageInterpreter;
import com.jruiz.nexo.core.NoteTitleEngine;
import com.jruiz.nexo.recordings.RecordingService;
import com.jruiz.nexo.recordings.RecordingStore;
import com.jruiz.nexo.recordings.RecordingProvider;
import com.jruiz.nexo.recordings.RecordingNaming;
import com.jruiz.nexo.transcription.TranscriptionModelManager;
import com.jruiz.nexo.transcription.TranscriptionStore;
import com.jruiz.nexo.transcription.TranscriptionService;
import com.jruiz.nexo.communications.CommunicationCapturePrefs;
import com.jruiz.nexo.communications.NexoNotificationListener;
import com.jruiz.nexo.input.InputHubPrefs;
import com.jruiz.nexo.input.SmsWatchEngine;
import com.jruiz.nexo.input.SmsWatchScheduler;
import com.jruiz.nexo.input.GmailWatchEngine;
import com.jruiz.nexo.input.GmailWatchScheduler;
import com.jruiz.nexo.migration.NexoFamiliaImporter;
import com.jruiz.nexo.migration.NexoFamiliaCloudSync;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.util.ArrayList;
import java.util.Locale;
import com.jruiz.nexo.documents.DocumentFiles;
import com.jruiz.nexo.documents.DocumentProvider;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 101;
    private static final int REQ_FILE = 102;
    private static final int REQ_NOTIFICATIONS = 103;
    private static final int REQ_SPEECH = 104;
    private static final int REQ_BACKUP_EXPORT = 105;
    private static final int REQ_BACKUP_RESTORE = 106;
    private static final int REQ_AUDIO = 107;
    private static final int REQ_RECORDING_AUDIO = 108;
    private static final int REQ_RECORDING_EXPORT = 109;
    private static final int REQ_DOCUMENT_FILE = 110;
    private static final int REQ_DOCUMENT_EXPORT = 111;
    private static final int REQ_CALENDAR = 112;
    private static final int REQ_GMAIL_SIGN_IN = 113;
    private static final int REQ_SMS = 114;
    private static final int REQ_NEXO_FAMILIA_IMPORT = 115;
    private String pendingDocumentRequest = "";
    private String pendingDocumentExportPath = "";

    private WebView webView;
    private DatabaseHelper db;
    private NexoService service;
    private boolean pageReady = false;
    private JSONObject pendingIncoming;
    private JSONObject pendingSharedImport;
    private JSONObject pendingSharedPreview;
    private long pendingInboxCaptureId = 0L;
    private String pendingParkingNote = "";
    private LocationManager parkingLocationManager;
    private LocationListener parkingGpsListener;
    private LocationListener parkingNetworkListener;
    private Location bestParkingLocation;
    private boolean parkingCaptureActive = false;
    private final Handler parkingHandler = new Handler(Looper.getMainLooper());
    private static final long PARKING_TIMEOUT_MS = 9000L;
    private static final float PARKING_GOOD_ACCURACY_M = 20f;
    private static final float PARKING_ACCEPTABLE_ACCURACY_M = 50f;
    private Uri pendingRestoreUri;
    private SpeechRecognizer managedRecognizer;
    private Intent managedSpeechIntent;
    private boolean managedDictationActive = false;
    private boolean managedDictationStopRequested = false;
    private int managedVoiceRetries = 0;
    private final Handler managedVoiceHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer recordingPlayer;
    private String playingRecordingId = "";
    private float recordingPlaybackSpeed = 1.0f;
    private String pendingRecordingExportId = "";
    private volatile boolean transcriptionModelDownloadActive = false;
    private NexoFamiliaCloudSync familiaCloudSync;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(getApplicationContext());
        service = new NexoService(this, db);
        familiaCloudSync = new NexoFamiliaCloudSync(getApplicationContext(), db);
        // La antigua instantánea incluida queda solo como compatibilidad manual.
        // La agenda de Nexo Familia se sincroniza ahora directamente con Supabase.
        setupWebView();
        handleIncomingIntent(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) {
            try {
                db.reconcileTemporalStatuses();
                JSONArray meds=db.getMedications("","active");
                for(int i=0;i<meds.length();i++){JSONObject m=meds.optJSONObject(i);if(m!=null)MedicationScheduler.scheduleNext(this,db,m.optLong("id"),System.currentTimeMillis()-1);}
            } catch(Exception ignored) {}
        }
        if (InputHubPrefs.autoScanEnabled(this)) {
            if (CalendarCapturePrefs.isEnabled(this) &&
                    checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                new Thread(() -> {
                    Context app = getApplicationContext();
                    if (!CalendarCapturePrefs.isBaselined(app)) {
                        CalendarWatchEngine.initializeBaseline(app);
                    } else {
                        // Recupera cambios que Android/Calendar pudiera no haber entregado
                        // mientras Nexo estaba en segundo plano y vuelve a armar el watcher.
                        CalendarWatchEngine.scan(app);
                        CalendarWatchScheduler.schedule(app);
                    }
                    jsCallback("onInputHubChanged", "calendar");
                }, "nexo-calendar-resume").start();
            }
            if (InputHubPrefs.smsEnabled(this) &&
                    checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                new Thread(() -> {
                    if (!InputHubPrefs.smsBaselined(getApplicationContext())) SmsWatchEngine.initializeBaseline(getApplicationContext());
                    else SmsWatchEngine.scan(getApplicationContext());
                    SmsWatchScheduler.schedule(getApplicationContext());
                }, "nexo-sms-resume").start();
            }
            if (InputHubPrefs.gmailEnabled(this)) {
                if (GmailWatchEngine.hasAuthorizedAccount(this)) {
                    new Thread(() -> {
                        if (!InputHubPrefs.gmailBaselined(getApplicationContext())) GmailWatchEngine.initializeBaseline(getApplicationContext());
                        else GmailWatchEngine.scan(getApplicationContext());
                        GmailWatchScheduler.schedule(getApplicationContext());
                        jsCallback("onInputHubChanged", "gmail");
                    }, "nexo-gmail-resume").start();
                } else {
                    InputHubPrefs.setGmailError(this, "Conecta una cuenta de Gmail desde Ajustes.");
                }
            }
            }
        if (pageReady) {
            jsCallback("onNotificationAccessChanged", isNotificationAccessEnabled() ? "1" : "0");
            jsCallback("onCalendarAccessChanged",
                    checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED ? "1" : "0");
            jsCallback("onInputHubChanged", "resume");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override public void onBackPressed() {
        if (webView == null || !pageReady) { super.onBackPressed(); return; }
        webView.evaluateJavascript("window.NexoUI&&window.NexoUI.handleBack?window.NexoUI.handleBack():false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override protected void onDestroy() {
        stopParkingLocationSearch();
        stopManagedVoiceInternal(false);
        if (managedRecognizer != null) {
            try { managedRecognizer.destroy(); } catch (Exception ignored) {}
            managedRecognizer = null;
        }
        stopRecordingPlaybackInternal();
        if (webView != null) {
            webView.removeJavascriptInterface("NexoNative");
            webView.destroy();
        }
        if (db != null) db.close();
        super.onDestroy();
    }

    private void setupWebView() {
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        float systemScale = getResources().getConfiguration().fontScale;
        s.setTextZoom(Math.max(90, Math.min(170, Math.round(100f * systemScale))));
        s.setDomStorageEnabled(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        String uiAssetVersion = BuildConfig.VERSION_NAME;
        android.content.SharedPreferences uiPrefs = getSharedPreferences("nexo-ui-assets", MODE_PRIVATE);
        if (!uiAssetVersion.equals(uiPrefs.getString("version", ""))) {
            webView.clearCache(true);
            uiPrefs.edit().putString("version", uiAssetVersion).apply();
        }
        // La WebView no realiza cargas de red: la lógica de Nexo permanece local.
        s.setBlockNetworkLoads(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverIncomingIfReady();
                deliverInboxReviewIfReady();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ("http".equals(u.getScheme()) || "https".equals(u.getScheme()))) {
                    openExternal(u.toString());
                    return true;
                }
                return false;
            }
        });
        webView.addJavascriptInterface(new Bridge(), "NexoNative");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (NexoNotificationListener.ACTION_REVIEW.equals(action)) {
            pendingInboxCaptureId = intent.getLongExtra("captureId", 0L);
            deliverInboxReviewIfReady();
            return;
        }
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_VIEW.equals(action)) return;
        try {
            String mime = intent.getType() == null ? "" : intent.getType();
            Uri stream = null;
            if (Intent.ACTION_VIEW.equals(action)) {
                stream = intent.getData();
            } else if (Build.VERSION.SDK_INT >= 33) {
                stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                //noinspection deprecation
                stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }

            if (stream != null) {
                String name = queryDisplayName(stream);
                if (isExchangeFile(mime, name)) {
                    inspectSharedFile(stream, mime, name);
                    return;
                }
            }
            if (!Intent.ACTION_SEND.equals(action)) return;

            JSONObject p = new JSONObject();
            p.put("source", "android-share");
            p.put("mime", mime);
            CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (subject != null) p.put("subject", subject.toString());
            if (text != null) p.put("text", text.toString());
            if (stream != null) {
                JSONObject f = copyUriToInternal(stream, mime);
                p.put("attachmentPath", f.optString("path"));
                p.put("attachmentName", f.optString("name"));
                p.put("attachmentMime", f.optString("mime"));
                if (p.optString("text").isBlank()) p.put("text", f.optString("name", "Archivo compartido"));
            }
            // Persistir antes de abrir la interfaz: si Android mata la actividad o el usuario sale,
            // el contenido recibido seguirá disponible en Nexo Inbox.
            JSONObject classification = new JSONObject();
            String sharedText = p.optString("text", "");
            if (!sharedText.isBlank()) {
                try { classification = service.classify(sharedText); }
                catch (Exception ignored) { classification = new JSONObject(); }
            }
            long captureId = db.insertInboxCapture(p, classification);
            if (captureId > 0) p.put("captureId", captureId);
            if (classification.length() > 0) p.put("classification", classification);

            pendingIncoming = p;
            deliverIncomingIfReady();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo leer el contenido compartido", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isExchangeFile(String mime, String name) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return NexoExchangeManager.MIME.equals(m) || "application/octet-stream".equals(m)
                || "text/calendar".equals(m) || "application/ics".equals(m)
                || n.endsWith(".nexoshare") || n.endsWith(".ics");
    }

    private void inspectSharedFile(Uri uri, String mime, String name) {
        new Thread(() -> {
            try {
                String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
                String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
                JSONObject payload, preview;
                if (NexoExchangeManager.MIME.equals(m) || "application/octet-stream".equals(m) || n.endsWith(".nexoshare")) {
                    payload = NexoExchangeManager.read(this, uri);
                    preview = NexoExchangeManager.preview(payload);
                } else {
                    payload = CalendarImporter.read(this, uri);
                    preview = CalendarImporter.preview(payload);
                }
                pendingSharedImport = payload;
                pendingSharedPreview = preview;
                runOnUiThread(this::deliverIncomingIfReady);
            } catch (Exception e) {
                pendingSharedImport = null;
                pendingSharedPreview = null;
                jsCallback("onSharedImportError", e.getMessage() == null ? "El archivo recibido no es compatible." : e.getMessage());
            }
        }, "nexo-shared-import").start();
    }

    private void deliverIncomingIfReady() {
        if (!pageReady) return;
        if (pendingSharedPreview != null) {
            final String js = "window.NexoUI && window.NexoUI.receiveImportPreview(" + JSONObject.quote(pendingSharedPreview.toString()) + ");";
            pendingSharedPreview = null;
            webView.evaluateJavascript(js, null);
            return;
        }
        if (pendingIncoming == null) return;
        final String js = "window.NexoUI && window.NexoUI.receiveShare(" + JSONObject.quote(pendingIncoming.toString()) + ");";
        pendingIncoming = null;
        webView.evaluateJavascript(js, null);
    }

    private void deliverInboxReviewIfReady() {
        if (!pageReady || pendingInboxCaptureId <= 0 || webView == null) return;
        long id = pendingInboxCaptureId;
        pendingInboxCaptureId = 0L;
        webView.evaluateJavascript(
                "window.NexoUI && window.NexoUI.reviewInboxCaptureFromNotification(" + id + ");",
                null
        );
    }

    private boolean isNotificationAccessEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (enabled == null || enabled.isBlank()) return false;
            ComponentName target = new ComponentName(this, NexoNotificationListener.class);
            String full = target.flattenToString();
            String shortName = target.flattenToShortString();
            for (String part : enabled.split(":")) {
                if (full.equals(part) || shortName.equals(part)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void openNotificationAccessSettings() {
        runOnUiThread(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir el acceso a notificaciones", Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void setCalendarCaptureEnabled(boolean enabled) {
        runOnUiThread(() -> {
            if (!enabled) {
                CalendarCapturePrefs.setEnabled(this, false);
                CalendarWatchScheduler.cancel(this);
                jsCallback("onCalendarAccessChanged", "0");
                return;
            }

            CalendarCapturePrefs.setEnabled(this, true);
            if (!hasCalendarPermission()) {
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR);
                return;
            }

            ensureNotificationPermission();
            if (InputHubPrefs.autoScanEnabled(this)) {
                new Thread(() -> {
                    CalendarWatchEngine.initializeBaseline(getApplicationContext());
                    jsCallback("onCalendarAccessChanged", "1");
                }, "nexo-calendar-baseline").start();
            } else {
                jsCallback("onCalendarAccessChanged", "1");
                jsCallback("onInputHubChanged", "calendar");
            }
        });
    }

    private boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void setSmsCaptureEnabled(boolean enabled) {
        runOnUiThread(() -> {
            if (!enabled) {
                InputHubPrefs.setSmsEnabled(this, false);
                InputHubPrefs.setSmsBaselined(this, false);
                SmsWatchScheduler.cancel(this);
                CommunicationCapturePrefs.setAppEnabled(this, "sms", true);
                jsCallback("onInputHubChanged", "sms");
                return;
            }
            InputHubPrefs.setSmsEnabled(this, true);
            CommunicationCapturePrefs.setAppEnabled(this, "sms", true);
            if (!hasSmsPermission()) {
                requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
                return;
            }
            ensureNotificationPermission();
            if (InputHubPrefs.autoScanEnabled(this)) {
                new Thread(() -> {
                    SmsWatchEngine.initializeBaseline(getApplicationContext());
                    jsCallback("onInputHubChanged", "sms");
                }, "nexo-sms-baseline").start();
            } else {
                jsCallback("onInputHubChanged", "sms");
            }
        });
    }

    private void clearGmailConnectionError() {
        InputHubPrefs.setGmailError(this, "");
        InputHubPrefs.setGmailErrorDetail(this, "");
    }

    private void setGmailConnectionError(Exception error) {
        int code = -1;
        String status = "";
        if (error instanceof ApiException) {
            code = ((ApiException) error).getStatusCode();
            status = GoogleSignInStatusCodes.getStatusCodeString(code);
        }
        String friendly;
        if (code == 10) {
            friendly = "Google todavía no reconoce esta versión de Nexo.";
        } else if (code == 12501) {
            friendly = "Conexión con Gmail cancelada.";
        } else if (code == 7) {
            friendly = "No se pudo contactar con Google. Comprueba tu conexión a Internet.";
        } else if (error instanceof IllegalStateException && error.getMessage() != null && error.getMessage().contains("permiso")) {
            friendly = "La cuenta no concedió permiso para leer Gmail.";
        } else {
            friendly = "No se pudo conectar Gmail.";
        }
        StringBuilder detail = new StringBuilder();
        if (!status.isBlank()) detail.append("Google Sign-In: ").append(status);
        if (code >= 0) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append("código ").append(code);
        }
        if (detail.length() == 0 && error.getMessage() != null && !error.getMessage().isBlank()) {
            detail.append(error.getMessage());
        }
        InputHubPrefs.setGmailError(this, friendly);
        InputHubPrefs.setGmailErrorDetail(this, detail.toString());
    }

    private void connectGmail() {
        runOnUiThread(() -> {
            try {
                clearGmailConnectionError();
                InputHubPrefs.setGmailEnabled(this, true);
                GoogleSignInClient client = GoogleSignIn.getClient(this, GmailWatchEngine.signInOptions());
                startActivityForResult(client.getSignInIntent(), REQ_GMAIL_SIGN_IN);
                jsCallback("onInputHubChanged", "gmail");
            } catch (Exception e) {
                InputHubPrefs.setGmailEnabled(this, false);
                setGmailConnectionError(e);
                jsCallback("onInputHubChanged", "gmail");
            }
        });
    }

    private void disconnectGmail() {
        runOnUiThread(() -> {
            GmailWatchScheduler.cancel(this);
            InputHubPrefs.setGmailEnabled(this, false);
            InputHubPrefs.setGmailBaselined(this, false);
            InputHubPrefs.setGmailEmail(this, "");
            clearGmailConnectionError();
            try {
                GoogleSignInClient client = GoogleSignIn.getClient(this, GmailWatchEngine.signInOptions());
                client.signOut().addOnCompleteListener(task -> jsCallback("onInputHubChanged", "gmail"));
            } catch (Exception ignored) {
                jsCallback("onInputHubChanged", "gmail");
            }
        });
    }

    private void scanGmailNow() {
        if (!GmailWatchEngine.hasAuthorizedAccount(this)) {
            InputHubPrefs.setGmailEnabled(this, false);
            InputHubPrefs.setGmailError(this, "Conecta Gmail antes de escanear.");
            InputHubPrefs.setGmailErrorDetail(this, "");
            jsCallback("onInputHubChanged", "gmail");
            return;
        }
        InputHubPrefs.setGmailEnabled(this, true);
        clearGmailConnectionError();
        new Thread(() -> {
            if (!InputHubPrefs.gmailBaselined(getApplicationContext())) {
                GmailWatchEngine.initializeBaseline(getApplicationContext());
            } else {
                GmailWatchEngine.scan(getApplicationContext());
            }
            if (InputHubPrefs.autoScanEnabled(getApplicationContext())) {
                GmailWatchScheduler.schedule(getApplicationContext());
            }
            jsCallback("onInputHubChanged", "gmail");
        }, "nexo-gmail-manual-scan").start();
    }

    private void setGmailCaptureEnabled(boolean enabled) {
        if (!enabled) {
            InputHubPrefs.setGmailEnabled(this, false);
            InputHubPrefs.setGmailBaselined(this, false);
            GmailWatchScheduler.cancel(this);
            jsCallback("onInputHubChanged", "gmail");
            return;
        }
        if (!GmailWatchEngine.hasAuthorizedAccount(this)) {
            connectGmail();
            return;
        }
        InputHubPrefs.setGmailEnabled(this, true);
        clearGmailConnectionError();
        if (InputHubPrefs.autoScanEnabled(this)) {
            new Thread(() -> {
                if (!InputHubPrefs.gmailBaselined(getApplicationContext())) GmailWatchEngine.initializeBaseline(getApplicationContext());
                else GmailWatchEngine.scan(getApplicationContext());
                GmailWatchScheduler.schedule(getApplicationContext());
                jsCallback("onInputHubChanged", "gmail");
            }, "nexo-gmail-enable").start();
        } else {
            jsCallback("onInputHubChanged", "gmail");
        }
    }

    private void setInputHubAutoScanEnabled(boolean enabled) {
        InputHubPrefs.setAutoScanEnabled(getApplicationContext(), enabled);
        Context app = getApplicationContext();
        if (!enabled) {
            CalendarWatchScheduler.cancel(app);
            SmsWatchScheduler.cancel(app);
            GmailWatchScheduler.cancel(app);
            return;
        }
        if (CalendarCapturePrefs.isEnabled(app) && hasCalendarPermission()) CalendarWatchScheduler.schedule(app);
        if (InputHubPrefs.smsEnabled(app) && hasSmsPermission()) SmsWatchScheduler.schedule(app);
        if (InputHubPrefs.gmailEnabled(app) && GmailWatchEngine.hasAuthorizedAccount(app)) GmailWatchScheduler.schedule(app);
    }

    private void scanInputHubNow() {
        new Thread(() -> {
            if (InputHubPrefs.smsEnabled(getApplicationContext()) && hasSmsPermission()) {
                if (!InputHubPrefs.smsBaselined(getApplicationContext())) SmsWatchEngine.initializeBaseline(getApplicationContext());
                else SmsWatchEngine.scan(getApplicationContext());
            }
            if (InputHubPrefs.gmailEnabled(getApplicationContext()) && GmailWatchEngine.hasAuthorizedAccount(getApplicationContext())) {
                if (!InputHubPrefs.gmailBaselined(getApplicationContext())) GmailWatchEngine.initializeBaseline(getApplicationContext());
                else GmailWatchEngine.scan(getApplicationContext());
            }
            if (CalendarCapturePrefs.isEnabled(getApplicationContext()) && hasCalendarPermission()) {
                CalendarWatchEngine.scan(getApplicationContext());
            }
            jsCallback("onInputHubChanged", "scan");
        }, "nexo-input-hub-scan").start();
    }

    private JSONObject copyUriToInternal(Uri uri, String mimeHint) throws Exception {
        String name = queryDisplayName(uri);
        if (name == null || name.isBlank()) name = "archivo_" + System.currentTimeMillis();
        name = DocumentFiles.safeName(name);
        File dir = new File(getFilesDir(), "attachments");
        String mime = mimeHint;
        if (mime == null || mime.isBlank()) mime = getContentResolver().getType(uri);
        File out = DocumentFiles.copy(dir,name,getContentResolver().openInputStream(uri));
        JSONObject o = new JSONObject();
        o.put("path", out.getAbsolutePath()); o.put("name", name); o.put("mime", DocumentProvider.mimeType(name,mime));
        return o;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_FILE);
    }

    private void chooseDocumentFile(String request) {
        if(!pendingDocumentRequest.isEmpty())return;
        pendingDocumentRequest=request;
        try {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
            startActivityForResult(intent,REQ_DOCUMENT_FILE);
        } catch(Exception e) {
            pendingDocumentRequest="";
            documentFileResult(request,null,"No se pudo abrir el selector de archivos.");
        }
    }

    private void documentFileResult(String request,JSONObject file,String error) {
        try {
            JSONObject result=new JSONObject().put("request",request);
            if(file!=null)result.put("file",file);
            if(error!=null)result.put("error",error);
            jsCallback("onDocumentFileChosen",result.toString());
        } catch(Exception ignored) {}
    }

    private void openAttachment(String path,String name,String mime,boolean share) {
        runOnUiThread(()->{
            try {
                Uri uri=DocumentProvider.uriFor(this,path,name,mime);
                Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);
                if(share) {
                    intent.setType(DocumentProvider.mimeType(name,mime));
                    intent.putExtra(Intent.EXTRA_STREAM,uri);
                } else intent.setDataAndType(uri,DocumentProvider.mimeType(name,mime));
                intent.setClipData(android.content.ClipData.newRawUri(name,uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent,share?"Compartir documento":"Abrir documento"));
            } catch(android.content.ActivityNotFoundException e) {
                Toast.makeText(this,"No hay una aplicación compatible para este archivo. Puedes guardar una copia.",Toast.LENGTH_LONG).show();
            } catch(Exception e) {
                Toast.makeText(this,"El archivo no está disponible. Vuelve a adjuntarlo en el documento.",Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exportDocument(long id) {
        runOnUiThread(()->{
            if(!pendingDocumentExportPath.isEmpty())return;
            try {
                JSONObject item=db.getItem(id);
                File file=DocumentFiles.requireStored(new File(getFilesDir(),"attachments"),item.optString("attachmentPath"));
                String name=item.optString("attachmentName",file.getName());
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(DocumentProvider.mimeType(name,item.optString("attachmentMime")));
                intent.putExtra(Intent.EXTRA_TITLE,name);
                pendingDocumentExportPath=file.getPath();
                startActivityForResult(intent,REQ_DOCUMENT_EXPORT);
            } catch(Exception e) {
                pendingDocumentExportPath="";
                jsCallback("onDocumentActionResult","No se pudo preparar la copia. Comprueba que el archivo siga disponible.");
            }
        });
    }

    private void copyDocumentToUri(String path,Uri target) {
        new Thread(()->{
            try {
                File file=DocumentFiles.requireStored(new File(getFilesDir(),"attachments"),path);
                try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(target,"w")) {
                    if(out==null)throw new IOException("Destino no disponible");
                    byte[] buffer=new byte[16384];int size;
                    while((size=in.read(buffer))!=-1)if(size>0)out.write(buffer,0,size);
                }
                jsCallback("onDocumentActionResult","Copia del documento guardada.");
            } catch(Exception e) {
                jsCallback("onDocumentActionResult","No se pudo guardar la copia del documento. Comprueba el destino y el espacio disponible.");
            }
        },"nexo-document-export").start();
    }

    private void applyBundledNexoFamiliaMigration() {
        applyBundledNexoFamiliaMigration(false);
    }

    private void applyBundledNexoFamiliaMigration(boolean force) {
        final String assetName = "nexo_familia_seed_v1.json";
        try {
            String raw;
            try (InputStream in = getAssets().open(assetName);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) if (n > 0) out.write(buffer, 0, n);
                raw = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            }
            JSONObject payload = new JSONObject(raw);
            String snapshot = payload.optString("snapshotVersion", "v1");
            SharedPreferences prefs = getSharedPreferences("nexo-familia-migration", MODE_PRIVATE);
            JSONObject beforeAudit = auditBundledNexoFamiliaPayload(payload);
            boolean cleanRebuildDone = snapshot.equals(prefs.getString("cleanRebuildVersion", ""));
            if (!force && cleanRebuildDone &&
                    snapshot.equals(prefs.getString("snapshotVersion", "")) &&
                    beforeAudit.optBoolean("complete", false)) {
                // Aunque los 33 UUID ya estén presentes, una versión anterior pudo
                // dejar estados temporales incorrectos. Se concilian antes de abrir UI.
                db.reconcileTemporalStatuses();
                return;
            }

            // Beta6: no intentamos reparar sobre una migración dudosa. Reconstruimos
            // únicamente las copias procedentes de Nexo Familia y mantenemos intactos
            // todos los datos manuales y de otras fuentes de Nexo Android.
            JSONArray oldReminderIds = db.resetNexoFamiliaImportedData();
            for (int i=0;i<oldReminderIds.length();i++) {
                ReminderScheduler.cancel(getApplicationContext(), oldReminderIds.optLong(i));
            }

            JSONObject result = NexoFamiliaImporter.importPayload(getApplicationContext(), db, payload);
            JSONObject afterAudit = auditBundledNexoFamiliaPayload(payload);
            int expected = payload.optJSONArray("events") == null ? 0 : payload.optJSONArray("events").length();
            JSONArray expectedRulesArray = payload.optJSONArray("reminderRules");
            if (expectedRulesArray == null) expectedRulesArray = payload.optJSONArray("reminder_rules");
            int expectedRules = expectedRulesArray == null ? 0 : expectedRulesArray.length();

            int preserved = result.optInt("preserved", 0);
            int verifiedEvents = result.optInt("verifiedEvents", 0);
            int verifiedRules = result.optInt("verifiedReminderRules", 0);
            int skipped = result.optInt("skipped", 0);
            boolean complete = result.optBoolean("ok", false) &&
                    expected > 0 &&
                    preserved == expected &&
                    verifiedEvents == expected &&
                    verifiedRules == expectedRules &&
                    skipped == 0 &&
                    afterAudit.optBoolean("complete", false);

            prefs.edit()
                    .putInt("expectedEvents", expected)
                    .putInt("expectedReminderRules", expectedRules)
                    .putInt("lastPreserved", preserved)
                    .putInt("lastVerifiedEvents", verifiedEvents)
                    .putInt("lastVerifiedReminderRules", verifiedRules)
                    .putInt("lastSkipped", skipped)
                    .putBoolean("lastComplete", complete)
                    .putLong("lastAttemptAt", System.currentTimeMillis())
                    .apply();

            if (complete) {
                prefs.edit()
                        .putString("snapshotVersion", snapshot)
                        .putString("cleanRebuildVersion", snapshot)
                        .putInt("events", verifiedEvents)
                        .putInt("reminderRules", verifiedRules)
                        .putLong("importedAt", System.currentTimeMillis())
                        .apply();
                db.reconcileTemporalStatuses();
            } else {
                prefs.edit()
                        .remove("snapshotVersion")
                        .remove("cleanRebuildVersion")
                        .apply();
            }
        } catch (Exception ignored) {
            // La app debe seguir abriendo aunque el snapshot opcional no pueda migrarse.
            // Si falla no se marca como completado y se volverá a intentar en el siguiente arranque.
        }
    }

    private JSONObject auditBundledNexoFamiliaPayload(JSONObject payload) {
        JSONObject out = new JSONObject();
        JSONArray missingEvents = new JSONArray();
        JSONArray missingRules = new JSONArray();
        try {
            JSONArray events = payload == null ? null : payload.optJSONArray("events");
            JSONArray rules = payload == null ? null : payload.optJSONArray("reminderRules");
            if (rules == null && payload != null) rules = payload.optJSONArray("reminder_rules");
            if (events == null) events = new JSONArray();
            if (rules == null) rules = new JSONArray();

            int verifiedEvents = 0;
            int expectedFamily = 0, verifiedFamily = 0, familyPending = 0, familyDone = 0;
            int expectedSelected = 0, verifiedSelected = 0, selectedPending = 0, selectedDone = 0;

            for (int i=0;i<events.length();i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) continue;
                String sourceId = event.optString("id", "").trim();
                String visibility = event.optString("visibility", "").trim();
                if ("family".equals(visibility)) expectedFamily++;
                if ("selected".equals(visibility)) expectedSelected++;

                long localId = sourceId.isEmpty() ? 0L : db.getNexoFamiliaLocalItemId(sourceId);
                JSONObject local = localId > 0L ? db.getItem(localId) : new JSONObject();
                long expectedStart = NexoFamiliaImporter.sourceStartMillis(event);
                boolean valid = local.length() > 0 &&
                        "nexo-familia".equals(local.optString("source", "")) &&
                        "event".equals(local.optString("type", "")) &&
                        event.optString("title", "").equals(local.optString("title", "")) &&
                        expectedStart > 0L &&
                        Math.abs(local.optLong("dueAt", 0L) - expectedStart) < 1000L;

                if (valid) {
                    verifiedEvents++;
                    String localStatus = local.optString("status", "");
                    if ("family".equals(visibility)) {
                        verifiedFamily++;
                        if ("done".equals(localStatus)) familyDone++; else if ("pending".equals(localStatus) || "active".equals(localStatus) || "postponed".equals(localStatus)) familyPending++;
                    }
                    if ("selected".equals(visibility)) {
                        verifiedSelected++;
                        if ("done".equals(localStatus)) selectedDone++; else if ("pending".equals(localStatus) || "active".equals(localStatus) || "postponed".equals(localStatus)) selectedPending++;
                    }
                } else {
                    JSONObject miss = new JSONObject();
                    miss.put("id", sourceId);
                    miss.put("title", event.optString("title", "Evento sin título"));
                    miss.put("visibility", visibility);
                    miss.put("expectedStart", expectedStart);
                    miss.put("localItemId", localId);
                    miss.put("localStart", local.optLong("dueAt", 0L));
                    missingEvents.put(miss);
                }
            }

            int verifiedRules = 0;
            for (int i=0;i<rules.length();i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) continue;
                String ruleId = rule.optString("id", "").trim();
                if (!ruleId.isEmpty() && db.hasNexoFamiliaReminderMapping(ruleId)) verifiedRules++;
                else missingRules.put(ruleId);
            }

            JSONObject stats = db.getNexoFamiliaImportStats();
            boolean complete = events.length() > 0 &&
                    verifiedEvents == events.length() &&
                    verifiedFamily == expectedFamily &&
                    verifiedSelected == expectedSelected &&
                    verifiedRules == rules.length() &&
                    stats.optLong("brokenMappings", 0L) == 0L;

            out.put("expectedEvents", events.length());
            out.put("verifiedEvents", verifiedEvents);
            out.put("expectedFamily", expectedFamily);
            out.put("verifiedFamily", verifiedFamily);
            out.put("expectedSelected", expectedSelected);
            out.put("verifiedSelected", verifiedSelected);
            out.put("familyPending", familyPending);
            out.put("familyDone", familyDone);
            out.put("selectedPending", selectedPending);
            out.put("selectedDone", selectedDone);
            out.put("expectedReminderRules", rules.length());
            out.put("verifiedReminderRules", verifiedRules);
            out.put("missingEvents", missingEvents);
            out.put("missingReminderRules", missingRules);
            out.put("complete", complete);
        } catch (Exception ignored) {}
        return out;
    }

    private JSONObject readBundledNexoFamiliaPayload() {
        try (InputStream in = getAssets().open("nexo_familia_seed_v1.json");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) if (n > 0) out.write(buffer, 0, n);
            return new JSONObject(out.toString(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private void chooseNexoFamiliaImport() {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(i, REQ_NEXO_FAMILIA_IMPORT);
            } catch (Exception e) {
                jsCallback("onNexoFamiliaImportError", "No se pudo abrir el selector de archivos.");
            }
        });
    }

    private void importNexoFamiliaFrom(Uri uri) {
        new Thread(() -> {
            try {
                String raw = readUriUtf8(uri, 8 * 1024 * 1024);
                JSONObject payload = new JSONObject(raw);
                JSONObject result = NexoFamiliaImporter.importPayload(getApplicationContext(), db, payload);
                if (result.optBoolean("ok", false)) {
                    jsCallbackJson("onNexoFamiliaImportDone", result);
                } else {
                    String message = result.optString("error", "");
                    if (message.isBlank()) {
                        message = "Migración incompleta: " +
                                result.optInt("verifiedEvents", 0) + "/" + result.optInt("expectedEvents", 0) +
                                " eventos y " +
                                result.optInt("verifiedReminderRules", 0) + "/" + result.optInt("expectedReminderRules", 0) +
                                " reglas verificadas.";
                    }
                    jsCallback("onNexoFamiliaImportError", message);
                }
            } catch (Exception e) {
                jsCallback("onNexoFamiliaImportError", "El archivo de Nexo Familia no es válido o está dañado.");
            }
        }, "nexo-familia-import").start();
    }

    private String readUriUtf8(Uri uri, int maxBytes) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Archivo no disponible");
            byte[] buffer = new byte[16384];
            int total = 0, n;
            while ((n = in.read(buffer)) != -1) {
                if (n <= 0) continue;
                total += n;
                if (total > maxBytes) throw new IOException("Archivo demasiado grande");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void chooseBackupExport() {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                i.putExtra(Intent.EXTRA_TITLE, NexoBackupManager.suggestedFileName());
                i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(i, REQ_BACKUP_EXPORT);
            } catch (Exception e) {
                jsCallback("onBackupError", "No se pudo abrir el selector para guardar la copia.");
            }
        });
    }

    private void chooseBackupRestore() {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, REQ_BACKUP_RESTORE);
            } catch (Exception e) {
                jsCallback("onBackupError", "No se pudo abrir el selector de copias.");
            }
        });
    }

    private void exportBackupTo(Uri uri) {
        new Thread(() -> {
            try {
                JSONObject info = NexoBackupManager.exportToUri(this, db, uri);
                getSharedPreferences("nexo_backup", MODE_PRIVATE).edit()
                        .putLong("lastExportAt", info.optLong("createdAt", System.currentTimeMillis())).apply();
                jsCallbackJson("onBackupExported", info);
            } catch (Exception e) {
                jsCallback("onBackupError", cleanBackupError(e, "No se pudo crear la copia de seguridad."));
            }
        }, "nexo-backup-export").start();
    }

    private void inspectBackup(Uri uri) {
        new Thread(() -> {
            try {
                JSONObject preview = NexoBackupManager.inspect(this, uri);
                pendingRestoreUri = uri;
                jsCallbackJson("onBackupRestorePreview", preview);
            } catch (Exception e) {
                pendingRestoreUri = null;
                jsCallback("onBackupError", cleanBackupError(e, "La copia seleccionada no es válida."));
            }
        }, "nexo-backup-inspect").start();
    }

    private void confirmRestoreBackup() {
        final Uri uri = pendingRestoreUri;
        if (uri == null) { jsCallback("onBackupError", "Selecciona primero una copia de Nexo."); return; }
        pendingRestoreUri = null;
        new Thread(() -> {
            try {
                // Cancelamos avisos del estado actual antes de reemplazarlo.
                JSONArray old = db.getFutureReminders();
                for (int i=0;i<old.length();i++) {
                    JSONObject r=old.optJSONObject(i); if(r!=null) ReminderScheduler.cancel(this,r.optLong("id"));
                }
                JSONObject result = NexoBackupManager.restoreFromUri(this, db, uri);
                JSONArray restored = db.getFutureReminders();
                for (int i=0;i<restored.length();i++) {
                    JSONObject r=restored.optJSONObject(i); if(r!=null) ReminderScheduler.schedule(this,r.optLong("id"),r.optString("title"),r.optLong("triggerAt"));
                }
                JSONArray meds=db.getMedications("","active");
                for(int i=0;i<meds.length();i++){JSONObject m=meds.optJSONObject(i);if(m!=null)MedicationScheduler.scheduleNext(this,db,m.optLong("id"),System.currentTimeMillis()-1);}
                MedicationScheduler.restorePendingRepeats(this,db);
                getSharedPreferences("nexo_backup", MODE_PRIVATE).edit()
                        .putLong("lastRestoreAt", System.currentTimeMillis())
                        .putLong("lastExportAt", result.optLong("createdAt",0))
                        .putLong("restoredBackupCreatedAt", result.optLong("createdAt",0)).apply();
                jsCallbackJson("onBackupRestored", result);
            } catch (Exception e) {
                // Si algo falla, la restauración es transaccional; volvemos a programar los avisos del estado vigente.
                try {
                    JSONArray current=db.getFutureReminders();
                    for(int i=0;i<current.length();i++){JSONObject r=current.optJSONObject(i);if(r!=null)ReminderScheduler.schedule(this,r.optLong("id"),r.optString("title"),r.optLong("triggerAt"));}
                    JSONArray meds=db.getMedications("","active");for(int i=0;i<meds.length();i++){JSONObject m=meds.optJSONObject(i);if(m!=null)MedicationScheduler.scheduleNext(this,db,m.optLong("id"),System.currentTimeMillis()-1);}
                    MedicationScheduler.restorePendingRepeats(this,db);
                } catch(Exception ignored){}
                jsCallback("onBackupError", cleanBackupError(e, "No se pudo restaurar la copia."));
            }
        }, "nexo-backup-restore").start();
    }

    private JSONObject backupStatus() {
        JSONObject o=new JSONObject();
        try {
            android.content.SharedPreferences p=getSharedPreferences("nexo_backup",MODE_PRIVATE);
            o.put("lastExportAt",p.getLong("lastExportAt",0));
            o.put("lastRestoreAt",p.getLong("lastRestoreAt",0));
            o.put("restoredBackupCreatedAt",p.getLong("restoredBackupCreatedAt",0));
            o.put("stats",db.getBackupStats());
        } catch(Exception ignored){}
        return o;
    }

    private static String cleanBackupError(Exception e,String fallback) {
        String m=e.getMessage();
        if(m==null||m.isBlank()) return fallback;
        if(m.length()>180)m=m.substring(0,180);
        return m;
    }

    private ArrayList<String> speechBiasingStrings() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray people = db.getPeople();
            for (int i=0;i<people.length() && out.size()<24;i++) {
                JSONObject person=people.optJSONObject(i);
                if(person==null)continue;
                String name=person.optString("name","").trim();
                if(!name.isBlank()&&!out.contains(name))out.add(name);
            }
        } catch(Exception ignored) {}
        for(String value:new String[]{"Nexo","Google Meet","Microsoft Teams","Zoom"}) {
            if(!out.contains(value))out.add(value);
        }
        return out;
    }

    private JSONObject speechRecognitionPayload(ArrayList<String> values, float[] confidence) {
        JSONObject out=new JSONObject();
        JSONArray hypotheses=new JSONArray();
        try {
            ArrayList<String> unique=new ArrayList<>();
            if(values!=null)for(String raw:values){
                String value=raw==null?"":raw.trim();
                if(value.isBlank())continue;
                boolean exists=false;
                for(String seen:unique)if(seen.equalsIgnoreCase(value)){exists=true;break;}
                if(!exists)unique.add(value);
                if(unique.size()>=3)break;
            }
            if(!unique.isEmpty())out.put("primary",unique.get(0));
            for(int i=0;i<unique.size();i++){
                JSONObject h=new JSONObject();
                h.put("text",unique.get(i));
                if(confidence!=null&&i<confidence.length&&confidence[i]>=0f)h.put("confidence",confidence[i]);
                hypotheses.put(h);
            }
            out.put("hypotheses",hypotheses);
        } catch(Exception ignored) {}
        return out;
    }

    private void startVoiceInput() {
        runOnUiThread(() -> {
            if (RecordingService.isRecordingActive()) {
                jsCallback("onVoiceError", "Termina la grabación antes de usar el dictado de voz.");
                return;
            }
            try {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Nexo · puedes hacer pausas cortas");
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                // Pedimos al reconocedor del sistema que tolere mejor las pausas naturales.
                // Algunos proveedores pueden ignorar estos valores, por eso Nexo también permite
                // continuar el dictado pulsando Hablar de nuevo sin sustituir el texto anterior.
                i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
                i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
                if (Build.VERSION.SDK_INT >= 33) {
                    i.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
                    ArrayList<String> bias = speechBiasingStrings();
                    if (!bias.isEmpty()) i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, bias);
                }
                startActivityForResult(i, REQ_SPEECH);
            } catch (ActivityNotFoundException e) {
                jsCallback("onVoiceError", "No encuentro un servicio de reconocimiento de voz en el dispositivo.");
            } catch (Exception e) {
                jsCallback("onVoiceError", "No se pudo iniciar el reconocimiento de voz.");
            }
        });
    }

    private void startManagedVoice() {
        runOnUiThread(() -> {
            if (RecordingService.isRecordingActive()) {
                jsCallback("onManagedVoiceError", "Termina la grabación antes de usar el dictado de voz.");
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                return;
            }
            startManagedVoiceInternal();
        });
    }

    private void startManagedVoiceInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            jsCallback("onManagedVoiceFallback", "Usando el dictado estándar de Android.");
            startVoiceInput();
            return;
        }
        try {
            if (managedRecognizer == null) {
                managedRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                managedRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { managedVoiceRetries=0; jsCallback("onManagedVoiceState", "listening"); }
                    @Override public void onBeginningOfSpeech() { jsCallback("onManagedVoiceState", "speaking"); }
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { jsCallback("onManagedVoiceState", "processing"); }
                    @Override public void onError(int error) {
                        if (!managedDictationActive) return;
                        if (managedDictationStopRequested) {
                            finishManagedVoice();
                            return;
                        }
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == SpeechRecognizer.ERROR_CLIENT) {
                            managedVoiceRetries++;
                            if (managedVoiceRetries <= 3) managedVoiceHandler.postDelayed(MainActivity.this::listenManagedSegment, 350);
                            else {
                                managedDictationActive = false;
                                jsCallback("onManagedVoiceFallback", "El dictado continuo no está respondiendo. Usando el dictado estándar de Android.");
                                startVoiceInput();
                            }
                        } else {
                            managedDictationActive = false;
                            jsCallback("onManagedVoiceError", voiceErrorMessage(error));
                        }
                    }
                    @Override public void onResults(Bundle results) {
                        ArrayList<String> values = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                            jsCallback("onManagedVoiceSegment", values.get(0).trim());
                        }
                        managedVoiceRetries=0;
                        if (managedDictationStopRequested) finishManagedVoice();
                        else if (managedDictationActive) managedVoiceHandler.postDelayed(MainActivity.this::listenManagedSegment, 260);
                    }
                    @Override public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> values = partialResults == null ? null : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (values != null && !values.isEmpty() && values.get(0) != null) {
                            jsCallback("onManagedVoicePartial", values.get(0).trim());
                        }
                    }
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            }
            managedSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
            if (Build.VERSION.SDK_INT >= 33) {
                managedSpeechIntent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
                ArrayList<String> bias = speechBiasingStrings();
                if (!bias.isEmpty()) managedSpeechIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, bias);
            }
            managedDictationActive = true;
            managedDictationStopRequested = false;
            managedVoiceRetries = 0;
            jsCallback("onManagedVoiceState", "starting");
            listenManagedSegment();
        } catch (Exception e) {
            managedDictationActive = false;
            jsCallback("onManagedVoiceFallback", "No se pudo abrir el dictado continuo. Usando el dictado estándar.");
            startVoiceInput();
        }
    }

    private void listenManagedSegment() {
        if (!managedDictationActive || managedDictationStopRequested || managedRecognizer == null || managedSpeechIntent == null) return;
        try {
            managedRecognizer.startListening(managedSpeechIntent);
        } catch (Exception e) {
            managedVoiceHandler.postDelayed(() -> {
                if (managedDictationActive && !managedDictationStopRequested) {
                    try { managedRecognizer.startListening(managedSpeechIntent); }
                    catch (Exception ex) {
                        managedDictationActive = false;
                        jsCallback("onManagedVoiceError", "No se pudo continuar el dictado.");
                    }
                }
            }, 450);
        }
    }

    private void stopManagedVoice() {
        runOnUiThread(() -> stopManagedVoiceInternal(true));
    }

    private void stopManagedVoiceInternal(boolean notify) {
        managedDictationStopRequested = true;
        managedVoiceHandler.removeCallbacksAndMessages(null);
        if (managedRecognizer != null) {
            try { managedRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (notify && managedDictationActive) {
            managedVoiceHandler.postDelayed(this::finishManagedVoice, 1100);
        } else if (!notify) {
            managedDictationActive = false;
        }
    }

    private void finishManagedVoice() {
        if (!managedDictationActive && !managedDictationStopRequested) return;
        managedVoiceHandler.removeCallbacksAndMessages(null);
        managedDictationActive = false;
        managedDictationStopRequested = false;
        if (managedRecognizer != null) {
            try { managedRecognizer.cancel(); } catch (Exception ignored) {}
        }
        jsCallback("onManagedVoiceStopped", "stopped");
    }

    private static String voiceErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "No se pudo acceder al audio.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Nexo necesita permiso de micrófono para escuchar.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "El reconocimiento de voz no está disponible en este momento.";
            case SpeechRecognizer.ERROR_SERVER: return "El servicio de voz de Android no respondió.";
            default: return "No se pudo continuar el dictado. Inténtalo de nuevo.";
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GMAIL_SIGN_IN) {
            try {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account == null || !GoogleSignIn.hasPermissions(account, GmailWatchEngine.GMAIL_SCOPE)) {
                    throw new IllegalStateException("Falta permiso de lectura de Gmail");
                }
                InputHubPrefs.setGmailEnabled(this, true);
                InputHubPrefs.setGmailBaselined(this, false);
                InputHubPrefs.setGmailEmail(this, account.getEmail());
                clearGmailConnectionError();
                CommunicationCapturePrefs.setAppEnabled(this, "gmail", true);
                if (InputHubPrefs.autoScanEnabled(this)) {
                    new Thread(() -> {
                        GmailWatchEngine.initializeBaseline(getApplicationContext());
                        GmailWatchScheduler.schedule(getApplicationContext());
                        jsCallback("onInputHubChanged", "gmail");
                    }, "nexo-gmail-baseline").start();
                } else {
                    jsCallback("onInputHubChanged", "gmail");
                }
            } catch (Exception e) {
                InputHubPrefs.setGmailEnabled(this, false);
                setGmailConnectionError(e);
                jsCallback("onInputHubChanged", "gmail");
            }
            return;
        }
        if (requestCode == REQ_NEXO_FAMILIA_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) importNexoFamiliaFrom(data.getData());
            return;
        }
        if (requestCode == REQ_BACKUP_EXPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) exportBackupTo(data.getData());
            return;
        }
        if (requestCode == REQ_BACKUP_RESTORE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri=data.getData();
                try {
                    int flags=data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri,flags);
                } catch(Exception ignored){}
                inspectBackup(uri);
            }
            return;
        }
        if (requestCode == REQ_RECORDING_EXPORT) {
            String id = pendingRecordingExportId;
            pendingRecordingExportId = "";
            if (resultCode == RESULT_OK && data != null && data.getData() != null && id != null && !id.isEmpty()) {
                exportRecordingToUri(id, data.getData());
            }
            return;
        }
        if (requestCode == REQ_DOCUMENT_FILE) {
            String request=pendingDocumentRequest;pendingDocumentRequest="";
            if(resultCode!=RESULT_OK || data==null || data.getData()==null) {
                documentFileResult(request,null,null);return;
            }
            Uri uri=data.getData();
            new Thread(()->{
                try {documentFileResult(request,copyUriToInternal(uri,getContentResolver().getType(uri)),null);}
                catch(Exception e) {documentFileResult(request,null,"No se pudo cargar el archivo. Comprueba que esté disponible y que haya espacio libre.");}
            },"nexo-document-import").start();
            return;
        }
        if (requestCode == REQ_DOCUMENT_EXPORT) {
            String path=pendingDocumentExportPath;pendingDocumentExportPath="";
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && !path.isEmpty())copyDocumentToUri(path,data.getData());
            return;
        }
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                JSONObject f = copyUriToInternal(data.getData(), getContentResolver().getType(data.getData()));
                String js = "window.NexoUI && window.NexoUI.onFileChosen(" + JSONObject.quote(f.toString()) + ");";
                webView.evaluateJavascript(js,null);
            } catch (Exception e) {
                Toast.makeText(this,"No se pudo guardar el archivo",Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                float[] confidence = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
                if (results != null && !results.isEmpty() && results.get(0) != null && !results.get(0).trim().isEmpty()) {
                    jsCallbackJson("onVoiceRecognition", speechRecognitionPayload(results, confidence));
                } else {
                    jsCallback("onVoiceError", "No he podido entender el audio. Prueba de nuevo.");
                }
            } else if (resultCode != RESULT_CANCELED) {
                jsCallback("onVoiceError", "No se pudo transcribir la nota de voz.");
            }
        }
    }

    private void requestStartRecording() {
        runOnUiThread(() -> {
            if (RecordingService.isRecordingActive()) {
                jsCallbackJson("onRecordingStateChanged", RecordingService.status());
                return;
            }
            if (managedDictationActive) {
                jsCallback("onRecordingError", "Finaliza primero el dictado de voz.");
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORDING_AUDIO);
                return;
            }
            startRecordingService();
        });
    }

    private void startRecordingService() {
        try {
            Intent i = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
            new Handler(Looper.getMainLooper()).postDelayed(() -> jsCallbackJson("onRecordingStateChanged", RecordingService.status()), 450L);
        } catch (Exception e) {
            jsCallback("onRecordingError", "No se pudo iniciar la grabación.");
        }
    }

    private void sendRecordingAction(String action) {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(this, RecordingService.class).setAction(action);
                startService(i);
                new Handler(Looper.getMainLooper()).postDelayed(() -> jsCallbackJson("onRecordingStateChanged", RecordingService.status()), 250L);
            } catch (Exception e) { jsCallback("onRecordingError", "No se pudo actualizar la grabación."); }
        });
    }

    private void playRecording(String id) {
        runOnUiThread(() -> {
            if (RecordingService.isRecordingActive()) {
                jsCallback("onRecordingError", "Termina la grabación antes de reproducir otra.");
                return;
            }
            try {
                stopRecordingPlaybackInternal();
                File f = RecordingStore.audioFile(this, id);
                if (!f.isFile() || f.length() <= 0) throw new FileNotFoundException();
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(f.getAbsolutePath());
                mp.setOnCompletionListener(done -> {
                    String completed = playingRecordingId;
                    stopRecordingPlaybackInternal();
                    jsCallback("onRecordingPlaybackStopped", completed);
                });
                mp.setOnErrorListener((bad, what, extra) -> {
                    String failed = playingRecordingId;
                    stopRecordingPlaybackInternal();
                    jsCallback("onRecordingPlaybackStopped", failed);
                    return true;
                });
                mp.prepare();
                mp.start();
                try { mp.setPlaybackParams(new PlaybackParams().setSpeed(recordingPlaybackSpeed)); } catch(Exception ignored) {}
                recordingPlayer = mp;
                playingRecordingId = id;
                jsCallback("onRecordingPlaybackStarted", id);
            } catch (Exception e) {
                stopRecordingPlaybackInternal();
                jsCallback("onRecordingError", "No se pudo reproducir la grabación.");
            }
        });
    }

    private void stopRecordingPlaybackInternal() {
        MediaPlayer mp = recordingPlayer;
        recordingPlayer = null;
        playingRecordingId = "";
        if (mp != null) {
            try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) {}
            try { mp.reset(); } catch (Exception ignored) {}
            try { mp.release(); } catch (Exception ignored) {}
        }
    }

    private void stopRecordingPlayback() {
        runOnUiThread(() -> {
            String stopped = playingRecordingId;
            stopRecordingPlaybackInternal();
            jsCallback("onRecordingPlaybackStopped", stopped);
        });
    }

    private JSONObject recordingPlaybackState() {
        JSONObject o=new JSONObject();
        try {
            MediaPlayer mp=recordingPlayer;
            o.put("id",playingRecordingId);
            o.put("active",mp!=null&&!playingRecordingId.isEmpty());
            o.put("playing",mp!=null&&mp.isPlaying());
            o.put("positionMs",mp==null?0:Math.max(0,mp.getCurrentPosition()));
            o.put("durationMs",mp==null?0:Math.max(0,mp.getDuration()));
            o.put("speed",recordingPlaybackSpeed);
        }catch(Exception ignored){}
        return o;
    }

    private void seekRecordingPlayback(String id,long positionMs){
        runOnUiThread(()->{
            try{if(recordingPlayer!=null&&id!=null&&id.equals(playingRecordingId)){int max=Math.max(0,recordingPlayer.getDuration());recordingPlayer.seekTo((int)Math.max(0,Math.min(positionMs,max)));}}catch(Exception ignored){}
        });
    }

    private void setRecordingPlaybackSpeed(float speed){
        float safe=(Math.abs(speed-1.5f)<0.1f)?1.5f:(speed>=1.9f?2.0f:1.0f);
        recordingPlaybackSpeed=safe;
        runOnUiThread(()->{try{if(recordingPlayer!=null)recordingPlayer.setPlaybackParams(new PlaybackParams().setSpeed(safe));}catch(Exception ignored){}});
    }

    private void shareRecording(String id) {
        runOnUiThread(() -> {
            try {
                File f = RecordingStore.audioFile(this, id);
                if (!f.isFile() || f.length() <= 0) throw new FileNotFoundException();
                Uri uri = RecordingProvider.uriFor(this, id);
                String title = RecordingStore.title(this, id);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("audio/mp4");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.putExtra(Intent.EXTRA_SUBJECT, title);
                share.setClipData(ClipData.newRawUri(title, uri));
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Compartir grabación"));
            } catch (Exception e) { Toast.makeText(this, "No se pudo compartir la grabación", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void exportRecording(String id) {
        runOnUiThread(() -> {
            try {
                File source = RecordingStore.audioFile(this, id);
                if (!source.isFile() || source.length() <= 0) throw new FileNotFoundException();
                String fileName = RecordingNaming.exportFileName(RecordingStore.title(this, id), RecordingStore.getMetadata(this, id).optLong("startedAt", System.currentTimeMillis()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nexo");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IOException("No se pudo crear el archivo en Descargas");
                    boolean completed = false;
                    try {
                        copyRecordingToUri(source, uri);
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(uri, ready, null, null);
                        completed = true;
                    } finally {
                        if (!completed) { try { resolver.delete(uri, null, null); } catch (Exception ignored) {} }
                    }
                    Toast.makeText(this, "Guardada en Descargas/Nexo: " + fileName, Toast.LENGTH_LONG).show();
                } else {
                    pendingRecordingExportId = id;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("audio/mp4");
                    i.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(i, REQ_RECORDING_EXPORT);
                }
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo exportar la grabación", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exportRecordingToUri(String id, Uri uri) {
        try {
            File source = RecordingStore.audioFile(this, id);
            if (!source.isFile() || source.length() <= 0) throw new FileNotFoundException();
            copyRecordingToUri(source, uri);
            Toast.makeText(this, "Grabación exportada correctamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo exportar la grabación", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyRecordingToUri(File source, Uri uri) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("Destino no disponible");
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    private void downloadTranscriptionModel() {
        if(transcriptionModelDownloadActive){jsCallback("onTranscriptionModelError","La descarga ya está en curso.");return;}
        transcriptionModelDownloadActive=true;
        new Thread(()->{
            try{
                TranscriptionModelManager.download(getApplicationContext(),(pct,msg)->{
                    JSONObject o=new JSONObject();try{o.put("progress",pct);o.put("message",msg);}catch(Exception ignored){}
                    jsCallbackJson("onTranscriptionModelProgress",o);
                });
                jsCallbackJson("onTranscriptionModelReady",TranscriptionModelManager.status(getApplicationContext()));
            }catch(Exception e){jsCallback("onTranscriptionModelError",e.getMessage()==null?"No se pudo descargar el modelo.":e.getMessage());}
            finally{transcriptionModelDownloadActive=false;}
        },"NexoModelDownload").start();
    }

    private void startTranscription(String id){
        try{
            if(!TranscriptionModelManager.installed(this)){jsCallback("onTranscriptionError","Primero descarga el modelo de transcripción.");return;}
            File audio=RecordingStore.audioFile(this,id);if(!audio.isFile()||audio.length()==0){jsCallback("onTranscriptionError","No se encuentra el audio.");return;}
            TranscriptionStore.writeStatus(this,id,"starting",1,"Preparando el archivo para transcripción…","starting");
            Intent i=new Intent(this,TranscriptionService.class).setAction(TranscriptionService.ACTION_START).putExtra(TranscriptionService.EXTRA_ID,id);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            jsCallback("onTranscriptionStarted",id);
        }catch(Exception e){jsCallback("onTranscriptionError","No se pudo iniciar la transcripción: "+e.getMessage());}
    }
    private void cancelTranscription(String id){try{Intent i=new Intent(this,TranscriptionService.class).setAction(TranscriptionService.ACTION_CANCEL).putExtra(TranscriptionService.EXTRA_ID,id);startService(i);}catch(Exception ignored){}}
    private void shareTranscription(String id){runOnUiThread(()->{try{String text=TranscriptionStore.read(this,id);if(text.isBlank())throw new IllegalStateException();Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);i.putExtra(Intent.EXTRA_SUBJECT,RecordingStore.title(this,id)+" · Transcripción");startActivity(Intent.createChooser(i,"Compartir transcripción"));}catch(Exception e){Toast.makeText(this,"No hay transcripción para compartir",Toast.LENGTH_SHORT).show();}});}
    private void copyTranscription(String id){String text=TranscriptionStore.read(this,id);if(text.isBlank())return;ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Transcripción Nexo",text));}
    private void exportTranscription(String id){runOnUiThread(()->{try{String text=TranscriptionStore.read(this,id);if(text.isBlank())throw new IllegalStateException();String base=RecordingStore.title(this,id).replaceAll("[^\\p{L}\\p{N}._ -]+","_").trim();if(base.isEmpty())base="Transcripcion_Nexo";String fileName=base+".txt";if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);v.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Nexo");Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException();try(OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException();out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}Toast.makeText(this,"Transcripción guardada en Descargas/Nexo",Toast.LENGTH_LONG).show();}else{Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(share,"Guardar transcripción"));}}catch(Exception e){Toast.makeText(this,"No se pudo guardar la transcripción",Toast.LENGTH_SHORT).show();}});}

    private void requestParking(String note) {
        pendingParkingNote = note == null ? "" : note.trim();
        runOnUiThread(() -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            } else captureParkingLocation();
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false; for (int r:grantResults) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            if (ok) captureParkingLocation(); else jsCallback("onParkingError", "Necesito permiso de ubicación para guardar dónde has aparcado.");
        } else if (requestCode == REQ_RECORDING_AUDIO) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) startRecordingService(); else jsCallback("onRecordingError", "Nexo necesita permiso de micrófono para grabar.");
        } else if (requestCode == REQ_AUDIO) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) startManagedVoiceInternal();
            else jsCallback("onManagedVoiceError", "Necesito permiso de micrófono para poder escucharte.");
        } else if (requestCode == REQ_CALENDAR) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) {
                CalendarCapturePrefs.setEnabled(this, true);
                ensureNotificationPermission();
                if (InputHubPrefs.autoScanEnabled(this)) {
                    new Thread(() -> {
                        CalendarWatchEngine.initializeBaseline(getApplicationContext());
                        jsCallback("onCalendarAccessChanged", "1");
                        jsCallback("onInputHubChanged", "calendar");
                    }, "nexo-calendar-baseline").start();
                } else {
                    jsCallback("onCalendarAccessChanged", "1");
                    jsCallback("onInputHubChanged", "calendar");
                }
            } else {
                CalendarCapturePrefs.setEnabled(this, false);
                CalendarWatchScheduler.cancel(this);
                jsCallback("onCalendarAccessChanged", "0");
                jsCallback("onInputHubChanged", "calendar");
                Toast.makeText(this, "Nexo necesita permiso de calendario para detectar eventos nuevos.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_SMS) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) {
                InputHubPrefs.setSmsEnabled(this, true);
                CommunicationCapturePrefs.setAppEnabled(this, "sms", true);
                ensureNotificationPermission();
                if (InputHubPrefs.autoScanEnabled(this)) {
                    new Thread(() -> {
                        SmsWatchEngine.initializeBaseline(getApplicationContext());
                        jsCallback("onInputHubChanged", "sms");
                    }, "nexo-sms-baseline").start();
                } else {
                    jsCallback("onInputHubChanged", "sms");
                }
            } else {
                InputHubPrefs.setSmsEnabled(this, false);
                SmsWatchScheduler.cancel(this);
                CommunicationCapturePrefs.setAppEnabled(this, "sms", true);
                jsCallback("onInputHubChanged", "sms");
                Toast.makeText(this, "Nexo necesita permiso de SMS para leer directamente los mensajes recibidos.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void captureParkingLocation() {
        stopParkingLocationSearch();
        bestParkingLocation = null;
        parkingCaptureActive = true;
        parkingLocationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (parkingLocationManager == null) { parkingCaptureActive = false; jsCallback("onParkingError","Ubicación no disponible."); return; }

        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean gps = parkingLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = parkingLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gps && !network) { parkingCaptureActive = false; jsCallback("onParkingError","Activa la ubicación para guardar el aparcamiento."); return; }

        parkingGpsListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { considerParkingLocation(location); }
            @Override public void onStatusChanged(String p,int status,Bundle extras) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        parkingNetworkListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { considerParkingLocation(location); }
            @Override public void onStatusChanged(String p,int status,Bundle extras) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        boolean requested = false;
        try {
            if (fine && gps) {
                Location last = parkingLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                parkingLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 0f, parkingGpsListener, getMainLooper());
                requested = true;
                if (isFreshParkingLocation(last, 15_000L)) considerParkingLocation(last);
                if (!parkingCaptureActive) return;
            }
            if (coarse && network) {
                Location last = parkingLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                parkingLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 700L, 0f, parkingNetworkListener, getMainLooper());
                requested = true;
                if (isFreshParkingLocation(last, 15_000L)) considerParkingLocation(last);
                if (!parkingCaptureActive) return;
            }
        } catch (SecurityException ignored) {}

        if (!requested) { parkingCaptureActive = false; stopParkingLocationSearch(); jsCallback("onParkingError","No puedo acceder a una fuente de ubicación válida."); return; }
        parkingHandler.postDelayed(this::finishParkingLocationSearch, PARKING_TIMEOUT_MS);
    }

    private boolean isFreshParkingLocation(Location location, long maxAgeMs) {
        if (location == null) return false;
        long age = Math.abs(System.currentTimeMillis() - location.getTime());
        return age <= maxAgeMs;
    }

    private float parkingAccuracy(Location location) {
        if (location == null || !location.hasAccuracy()) return 10_000f;
        return Math.max(0f, location.getAccuracy());
    }

    private void considerParkingLocation(Location location) {
        if (!parkingCaptureActive || location == null || !isFreshParkingLocation(location, 30_000L)) return;
        if (bestParkingLocation == null || parkingAccuracy(location) < parkingAccuracy(bestParkingLocation)) {
            bestParkingLocation = new Location(location);
        }
        if (parkingAccuracy(location) <= PARKING_GOOD_ACCURACY_M) {
            Location chosen = new Location(location);
            parkingCaptureActive = false;
            stopParkingLocationSearch();
            saveParkingLocation(chosen);
        }
    }

    private void finishParkingLocationSearch() {
        if (!parkingCaptureActive) return;
        parkingCaptureActive = false;
        parkingHandler.removeCallbacksAndMessages(null);
        stopParkingLocationListenersOnly();
        if (bestParkingLocation == null) {
            jsCallback("onParkingError","No he podido obtener una ubicación reciente. Sal a una zona con mejor señal e inténtalo de nuevo.");
            return;
        }
        float accuracy = parkingAccuracy(bestParkingLocation);
        if (accuracy <= PARKING_ACCEPTABLE_ACCURACY_M) {
            saveParkingLocation(new Location(bestParkingLocation));
            return;
        }
        try {
            JSONObject info = new JSONObject();
            info.put("accuracy", Math.round(accuracy));
            info.put("provider", bestParkingLocation.getProvider() == null ? "" : bestParkingLocation.getProvider());
            jsCallbackJson("onParkingNeedsConfirmation", info);
        } catch (Exception e) {
            jsCallback("onParkingError","La ubicación obtenida es poco precisa. Inténtalo de nuevo.");
        }
    }

    private void stopParkingLocationListenersOnly() {
        if (parkingLocationManager == null) return;
        try { if (parkingGpsListener != null) parkingLocationManager.removeUpdates(parkingGpsListener); } catch (Exception ignored) {}
        try { if (parkingNetworkListener != null) parkingLocationManager.removeUpdates(parkingNetworkListener); } catch (Exception ignored) {}
        parkingGpsListener = null;
        parkingNetworkListener = null;
    }

    private void stopParkingLocationSearch() {
        parkingHandler.removeCallbacksAndMessages(null);
        stopParkingLocationListenersOnly();
    }

    private void confirmParkingLocation() {
        if (bestParkingLocation == null) { jsCallback("onParkingError","No hay una ubicación pendiente para guardar."); return; }
        saveParkingLocation(new Location(bestParkingLocation));
    }

    private void saveParkingLocation(Location location) {
        parkingCaptureActive = false;
        stopParkingLocationSearch();
        if (location == null) { jsCallback("onParkingError","No he podido obtener la ubicación. Inténtalo de nuevo."); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("type","parking"); o.put("title","Coche aparcado"); o.put("body",pendingParkingNote); o.put("category","Coche");
            o.put("lat",location.getLatitude()); o.put("lon",location.getLongitude()); o.put("locationText",pendingParkingNote);
            o.put("source", location.getProvider() == null ? "location" : location.getProvider()); o.put("needsReview",false);
            long id = db.insertItem(o);
            db.retirePreviousParking(id);
            bestParkingLocation = null;
            jsCallbackJson("onParkingSaved", db.getItem(id));
        } catch (Exception e) { jsCallback("onParkingError","No se pudo guardar el aparcamiento."); }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS));
        }
    }

    private void openExternal(String url) {
        runOnUiThread(() -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this,"No hay una aplicación disponible",Toast.LENGTH_SHORT).show(); }
        });
    }

    private void openLocationDirections(String locationText) {
        final String place = locationText == null ? "" : locationText.trim();
        if (place.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "Este elemento no tiene ubicación", Toast.LENGTH_SHORT).show());
            return;
        }
        runOnUiThread(() -> {
            // Primera opción: Google Maps con el destino ya preparado para navegar.
            try {
                Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)));
                maps.setPackage("com.google.android.apps.maps");
                startActivity(maps);
                return;
            } catch (Exception ignored) {}

            // Respaldo: cualquier aplicación de mapas instalada.
            try {
                Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(place));
                startActivity(new Intent(Intent.ACTION_VIEW, geo));
                return;
            } catch (Exception ignored) {}

            // Último respaldo: Google Maps web, sin API key.
            try {
                Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(place));
                startActivity(new Intent(Intent.ACTION_VIEW, web));
            } catch (Exception e) {
                Toast.makeText(this, "No hay una aplicación de mapas disponible", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openWeather() {
        runOnUiThread(() -> {
            try {
                Intent i;
                if (Build.VERSION.SDK_INT >= 33) i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_WEATHER);
                else i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=tiempo"));
                startActivity(i);
            } catch (Exception e) { Toast.makeText(this,"No encuentro una app meteorológica",Toast.LENGTH_SHORT).show(); }
        });
    }

    private void shareCalendarEvent(long itemId, long occurrenceAt, boolean wholeSeries) {
        try {
            JSONObject item = wholeSeries ? db.getItem(itemId) :
                    (occurrenceAt > 0 ? db.getOccurrence(itemId, occurrenceAt) : db.getItem(itemId));
            if (item.length() == 0 || item.optLong("dueAt", 0) <= 0) {
                runOnUiThread(() -> Toast.makeText(this, "Este elemento no tiene fecha para compartir", Toast.LENGTH_SHORT).show());
                return;
            }
            JSONArray reminders = db.getReminderRulesForItem(itemId);
            JSONArray leadMinutes = new JSONArray();
            for (int i=0;i<reminders.length();i++) {
                JSONObject r=reminders.optJSONObject(i); if(r==null)continue;
                int minutes=r.has("leadMinutes")?r.optInt("leadMinutes",0):Math.max(0,r.optInt("leadDays",0))*1440;
                leadMinutes.put(minutes);
            }
            boolean includeRecurrence = wholeSeries && !"none".equals(item.optString("recurrence", "none"));
            Uri uri = CalendarExporter.export(this, item, leadMinutes, includeRecurrence);
            runOnUiThread(() -> {
                try {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/calendar");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.putExtra(Intent.EXTRA_SUBJECT, item.optString("title", "Evento de Nexo"));
                    share.putExtra(Intent.EXTRA_TEXT, "Evento compartido desde Nexo: " + item.optString("title", "Evento"));
                    share.setClipData(ClipData.newRawUri("Evento de Nexo", uri));
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "Compartir evento de calendario"));
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo abrir el menú para compartir", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "No se pudo crear el archivo .ics", Toast.LENGTH_SHORT).show());
        }
    }

    private void shareNexoItem(long itemId, long occurrenceAt, boolean wholeSeries) {
        try {
            JSONObject item = wholeSeries ? db.getItem(itemId) : (occurrenceAt > 0 ? db.getOccurrence(itemId, occurrenceAt) : db.getItem(itemId));
            if (item.length() == 0) { Toast.makeText(this, "Elemento no encontrado", Toast.LENGTH_SHORT).show(); return; }
            if (!wholeSeries && item.optLong("seriesId",0) > 0) { item.put("recurrence","none"); item.put("recurrenceUntil",0); }
            Uri uri = NexoExchangeManager.exportItem(this, item, db.getReminderRulesForItem(itemId));
            shareExchangeUri(uri, item.optString("title","Elemento de Nexo"));
        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "No se pudo crear el archivo Nexo", Toast.LENGTH_SHORT).show()); }
    }

    private void shareNexoList(long listId) {
        try {
            JSONObject list=db.getList(listId); if(list.length()==0){Toast.makeText(this,"Lista no encontrada",Toast.LENGTH_SHORT).show();return;}
            Uri uri=NexoExchangeManager.exportList(this,list,db.getListEntries(listId));
            shareExchangeUri(uri,list.optString("title","Lista de Nexo"));
        } catch(Exception e){runOnUiThread(() -> Toast.makeText(this,"No se pudo compartir la lista",Toast.LENGTH_SHORT).show());}
    }

    private void shareNexoJournal(long journalId) {
        try {
            JSONObject journal=db.getJournalEntry(journalId); if(journal.length()==0){Toast.makeText(this,"Entrada no encontrada",Toast.LENGTH_SHORT).show();return;}
            Uri uri=NexoExchangeManager.exportJournal(this,journal);
            shareExchangeUri(uri,journal.optString("title","Cuaderno de Nexo"));
        } catch(Exception e){runOnUiThread(() -> Toast.makeText(this,"No se pudo compartir la entrada",Toast.LENGTH_SHORT).show());}
    }

    private void sharePlainText(String title, String text) {
        final String safeTitle = title == null || title.isBlank() ? "Nexo" : title;
        final String safeText = text == null ? "" : text;
        runOnUiThread(() -> {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, safeTitle);
                share.putExtra(Intent.EXTRA_TEXT, safeText);
                startActivity(Intent.createChooser(share, "Compartir"));
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir el menú para compartir", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareExchangeUri(Uri uri, String title) {
        runOnUiThread(() -> {
            try {
                Intent share=new Intent(Intent.ACTION_SEND);share.setType(NexoExchangeManager.MIME);share.putExtra(Intent.EXTRA_STREAM,uri);
                share.putExtra(Intent.EXTRA_SUBJECT,title);share.putExtra(Intent.EXTRA_TEXT,"Contenido compartido desde Nexo: "+title);
                share.setClipData(ClipData.newRawUri("Contenido de Nexo",uri));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share,"Compartir con Nexo"));
            } catch(Exception e){Toast.makeText(this,"No se pudo abrir el menú para compartir",Toast.LENGTH_SHORT).show();}
        });
    }

    private void confirmSharedImport() {
        JSONObject payload=pendingSharedImport; pendingSharedImport=null;
        if(payload==null){jsCallback("onSharedImportError","No hay ningún contenido pendiente de importar.");return;}
        try {
            String entity=payload.optString("entity",""); JSONObject data=payload.optJSONObject("data"); if(data==null)throw new IllegalArgumentException("Contenido incompleto");
            long id=0; JSONObject result=new JSONObject();
            if("item".equals(entity)) {
                JSONObject clean=new JSONObject(data.toString()); clean.put("source", "ics-import".equals(payload.optString("format")) ? "ics-import" : "nexo-import"); clean.put("needsReview",false);
                JSONObject saved=service.saveCapture(clean); if(!saved.optBoolean("ok",false))throw new IllegalStateException(saved.optString("error","No se pudo guardar"));
                id=saved.optLong("id",0); if(saved.optJSONArray("reminderIds")!=null&&saved.optJSONArray("reminderIds").length()>0)ensureNotificationPermission();
            } else if("list".equals(entity)) {
                id=db.importSharedList(data); if(id<=0)throw new IllegalStateException("No se pudo importar la lista");
            } else if("journal".equals(entity)) {
                JSONObject clean=new JSONObject(data.toString());clean.put("source","nexo-import");id=db.insertJournalEntry(clean);if(id<=0)throw new IllegalStateException("No se pudo importar el Cuaderno");
            } else throw new IllegalArgumentException("Tipo de contenido no compatible");
            result.put("ok",true);result.put("entity",entity);result.put("id",id);result.put("title",data.optString("title","Contenido recibido"));jsCallbackJson("onSharedImportDone",result);
        } catch(Exception e){jsCallback("onSharedImportError",e.getMessage()==null?"No se pudo importar el contenido.":e.getMessage());}
    }

    private void cancelSharedImport() { pendingSharedImport=null; pendingSharedPreview=null; }

    private void openParkingDirections() {
        JSONObject p = db.getLastParking();
        if (!p.has("lat") || p.isNull("lat") || !p.has("lon") || p.isNull("lon")) { Toast.makeText(this,"No hay un aparcamiento guardado",Toast.LENGTH_SHORT).show(); return; }
        double lat=p.optDouble("lat"),lon=p.optDouble("lon");
        String label = Uri.encode("Coche aparcado");
        Uri uri = Uri.parse(String.format(Locale.US,"geo:0,0?q=%f,%f(%s)",lat,lon,label));
        runOnUiThread(() -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
            catch (Exception e) { Toast.makeText(this,"No hay una aplicación de mapas disponible",Toast.LENGTH_SHORT).show(); }
        });
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        return am!=null && am.canScheduleExactAlarms();
    }

    private void requestExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName()));
            startActivity(i);
        } catch(Exception e) { Toast.makeText(this,"No se pudo abrir el permiso de alarmas exactas",Toast.LENGTH_SHORT).show(); }
    }

    private void testMedicationVoice(String name) {
        String safe=(name==null||name.trim().isEmpty())?"el medicamento":name.trim();
        MedicationVoiceSpeaker.speak(this,"Atención. Debes tomarte "+safe+" a la hora señalada.",null);
    }

    /** Delega la alarma en la aplicación Reloj del teléfono. */
    private void createSystemAlarm(String json) {
        runOnUiThread(() -> {
            try {
                JSONObject a=new JSONObject(json==null?"{}":json);
                int hour=a.optInt("hour",-1),minute=a.optInt("minute",-1);
                if(hour<0||hour>23||minute<0||minute>59){jsCallback("onSystemAlarmError","No he podido determinar una hora válida.");return;}
                boolean safe=a.optBoolean("systemDateSafe",true);
                Intent i=new Intent(AlarmClock.ACTION_SET_ALARM);
                i.putExtra(AlarmClock.EXTRA_HOUR,hour);i.putExtra(AlarmClock.EXTRA_MINUTES,minute);
                i.putExtra(AlarmClock.EXTRA_MESSAGE,a.optString("message","Nexo"));
                JSONArray days=a.optJSONArray("days");
                if(days!=null&&days.length()>0){ArrayList<Integer> values=new ArrayList<>();for(int x=0;x<days.length();x++)values.add(days.optInt(x));i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS,values);}
                // Si la fecha puede representarse de forma inequívoca, creación directa; si no, se abre Reloj para revisión.
                i.putExtra(AlarmClock.EXTRA_SKIP_UI,safe);
                if(i.resolveActivity(getPackageManager())==null){jsCallback("onSystemAlarmError","No encuentro una aplicación de Reloj compatible con alarmas.");return;}
                startActivity(i);
                jsCallback("onSystemAlarmCreated",safe?"Alarma enviada al Reloj del teléfono.":"He abierto el Reloj con la hora preparada. Revisa el día antes de guardarla.");
            } catch(Exception e){jsCallback("onSystemAlarmError","No se pudo crear la alarma en el Reloj del teléfono.");}
        });
    }

    private void jsCallback(String fn,String msg) {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript("window.NexoUI && window.NexoUI."+fn+"("+JSONObject.quote(msg)+");",null));
    }
    private void requestHolidayCalendar(int year) {
        if (year < 2000 || year > 2100) { JSONObject o=new JSONObject(); try{o.put("year",year);o.put("status","unavailable");o.put("holidays",new JSONArray());}catch(Exception ignored){} jsCallbackJson("onHolidayCalendarLoaded",o); return; }
        new Thread(() -> jsCallbackJson("onHolidayCalendarLoaded", HolidayCalendarService.load(this, year)), "nexo-holidays-" + year).start();
    }

    private void jsCallbackJson(String fn,JSONObject data) {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript("window.NexoUI && window.NexoUI."+fn+"("+JSONObject.quote(data.toString())+");",null));
    }
    public final class Bridge {
        @JavascriptInterface public String classify(String text) { return service.classify(text).toString(); }
        @JavascriptInterface public String classifyAt(String text, long capturedAt) { return service.classify(text, capturedAt).toString(); }
        @JavascriptInterface public void requestHolidayCalendar(int year) { MainActivity.this.requestHolidayCalendar(year); }
        @JavascriptInterface public String classifyAppointment(String text) { return service.classifyAppointment(text).toString(); }
        @JavascriptInterface public String classifyAppointmentAt(String text, long capturedAt) { return service.classifyAppointment(text, capturedAt).toString(); }
        @JavascriptInterface public String classifyAppointmentProposals(String text) { return service.classifyAppointmentProposals(text).toString(); }
        @JavascriptInterface public String classifyAppointmentProposalsAt(String text, long capturedAt) { return service.classifyAppointmentProposals(text, capturedAt).toString(); }
        @JavascriptInterface public String getAppointments() { return db.getAppointments().toString(); }
        @JavascriptInterface public void startVoiceInput() { MainActivity.this.startVoiceInput(); }
        @JavascriptInterface public void startManagedVoice() { MainActivity.this.startManagedVoice(); }
        @JavascriptInterface public void stopManagedVoice() { MainActivity.this.stopManagedVoice(); }
        @JavascriptInterface public String parseListVoice(String text) { return ListVoiceParser.parse(text).toString(); }
        @JavascriptInterface public String interpretIntent(String text) { return UserMessageInterpreter.route(text).toString(); }
        @JavascriptInterface public String parseAlarm(String text) { return AlarmIntentParser.parse(text).toString(); }
        @JavascriptInterface public void createSystemAlarm(String json) { MainActivity.this.createSystemAlarm(json); }
        @JavascriptInterface public String getDisplayMode() { return getSharedPreferences("nexo_settings", MODE_PRIVATE).getString("display_mode", "comfortable"); }
        @JavascriptInterface public void setDisplayMode(String mode) {
            String m = ("normal".equals(mode) || "large".equals(mode)) ? mode : "comfortable";
            getSharedPreferences("nexo_settings", MODE_PRIVATE).edit().putString("display_mode", m).apply();
        }
        @JavascriptInterface public String getAppVersion() { return BuildConfig.VERSION_NAME; }
        @JavascriptInterface public String getStartScreen() {
            return getSharedPreferences("nexo_settings", MODE_PRIVATE).getString("start_screen", "today");
        }
        @JavascriptInterface public void setStartScreen(String screen) {
            getSharedPreferences("nexo_settings", MODE_PRIVATE).edit().putString("start_screen", "calendar".equals(screen) ? "calendar" : "today").apply();
        }
        @JavascriptInterface public long getPreferredShoppingList() {
            return getSharedPreferences("nexo_settings", MODE_PRIVATE).getLong("shopping_list_id", 0L);
        }
        @JavascriptInterface public void setPreferredShoppingList(long id) {
            getSharedPreferences("nexo_settings", MODE_PRIVATE).edit().putLong("shopping_list_id", Math.max(0L,id)).apply();
        }
        @JavascriptInterface public boolean hasNotificationAccess() { return MainActivity.this.isNotificationAccessEnabled(); }
        @JavascriptInterface public void openNotificationAccessSettings() { MainActivity.this.openNotificationAccessSettings(); }
        @JavascriptInterface public String getCommunicationCaptureSettings() {
            JSONObject o = CommunicationCapturePrefs.toJson(MainActivity.this);
            try {
                o.put("autoScanEnabled", InputHubPrefs.autoScanEnabled(MainActivity.this));
                o.put("calendarEnabled", CalendarCapturePrefs.isEnabled(MainActivity.this));
                o.put("calendarPermission", MainActivity.this.hasCalendarPermission());
                o.put("calendarBaselined", CalendarCapturePrefs.isBaselined(MainActivity.this));
                o.put("smsDirectEnabled", InputHubPrefs.smsEnabled(MainActivity.this));
                o.put("smsPermission", MainActivity.this.hasSmsPermission());
                o.put("smsBaselined", InputHubPrefs.smsBaselined(MainActivity.this));
                o.put("smsLastSync", InputHubPrefs.smsLastSync(MainActivity.this));
                o.put("smsError", InputHubPrefs.smsError(MainActivity.this));
                JSONObject watch = db.getExternalWatchDiagnostics();
                o.put("smsSeen", watch.optLong("smsSeen", 0));
                o.put("smsCaptured", watch.optLong("smsCaptured", 0));
                o.put("smsRejected", watch.optLong("smsRejected", 0));
                o.put("smsErrors", watch.optLong("smsErrors", 0));
                o.put("gmailSeen", watch.optLong("gmailSeen", 0));
                o.put("gmailCaptured", watch.optLong("gmailCaptured", 0));
                o.put("gmailRejected", watch.optLong("gmailRejected", 0));
                o.put("gmailErrors", watch.optLong("gmailErrors", 0));
                o.put("gmailDirectEnabled", InputHubPrefs.gmailEnabled(MainActivity.this));
                o.put("gmailAuthorized", GmailWatchEngine.hasAuthorizedAccount(MainActivity.this));
                o.put("gmailEmail", InputHubPrefs.gmailEmail(MainActivity.this));
                o.put("gmailBaselined", InputHubPrefs.gmailBaselined(MainActivity.this));
                o.put("gmailLastSync", InputHubPrefs.gmailLastSync(MainActivity.this));
                o.put("gmailError", InputHubPrefs.gmailError(MainActivity.this));
                o.put("gmailErrorDetail", InputHubPrefs.gmailErrorDetail(MainActivity.this));
            } catch (Exception ignored) {}
            return o.toString();
        }
        @JavascriptInterface public void setCommunicationCaptureEnabled(boolean enabled) {
            CommunicationCapturePrefs.setEnabled(MainActivity.this, enabled);
        }
        @JavascriptInterface public void setInputHubAutoScanEnabled(boolean enabled) {
            MainActivity.this.setInputHubAutoScanEnabled(enabled);
        }
        @JavascriptInterface public void setCommunicationAppEnabled(String key, boolean enabled) {
            CommunicationCapturePrefs.setAppEnabled(MainActivity.this, key, enabled);
        }
        @JavascriptInterface public void setCalendarCaptureEnabled(boolean enabled) {
            MainActivity.this.setCalendarCaptureEnabled(enabled);
        }
        @JavascriptInterface public void setSmsCaptureEnabled(boolean enabled) {
            MainActivity.this.setSmsCaptureEnabled(enabled);
        }
        @JavascriptInterface public void connectGmail() {
            MainActivity.this.connectGmail();
        }
        @JavascriptInterface public void disconnectGmail() {
            MainActivity.this.disconnectGmail();
        }
        @JavascriptInterface public void scanGmailNow() {
            MainActivity.this.scanGmailNow();
        }
        @JavascriptInterface public void setGmailCaptureEnabled(boolean enabled) {
            MainActivity.this.setGmailCaptureEnabled(enabled);
        }
        @JavascriptInterface public void scanInputHubNow() {
            MainActivity.this.scanInputHubNow();
        }
        @JavascriptInterface public String getMedicationMessages() {
            JSONArray a=new JSONArray();
            for(int offset:MedicationMessageConfig.OFFSETS){
                JSONObject o=new JSONObject();
                try{o.put("offset",offset);o.put("text",MedicationMessageConfig.get(MainActivity.this,offset));}catch(Exception ignored){}
                a.put(o);
            }
            return a.toString();
        }
        @JavascriptInterface public void setMedicationMessage(int offset,String text) { MedicationMessageConfig.set(MainActivity.this,offset,text); }
        @JavascriptInterface public void resetMedicationMessages() { MedicationMessageConfig.reset(MainActivity.this); }
        @JavascriptInterface public String getCollection(String collection) { return db.getCollection(collection).toString(); }
        @JavascriptInterface public String saveDocument(String json) {
            try {return service.saveDocument(new JSONObject(json)).toString();}
            catch(Exception e) {return "{\"ok\":false,\"error\":\"Datos del documento no válidos\"}";}
        }
        @JavascriptInterface public void chooseDocumentFile(String request) { runOnUiThread(()->MainActivity.this.chooseDocumentFile(request)); }
        @JavascriptInterface public void discardDocumentFile(String path) { service.discardUnusedAttachment(path); }
        @JavascriptInterface public void openDocumentFile(String path,String name,String mime) { MainActivity.this.openAttachment(path,name,mime,false); }
        @JavascriptInterface public void downloadDocument(long id) { MainActivity.this.exportDocument(id); }
        @JavascriptInterface public void shareDocument(long id) {
            JSONObject item=db.getItem(id);
            MainActivity.this.openAttachment(item.optString("attachmentPath"),item.optString("attachmentName"),item.optString("attachmentMime"),true);
        }
        @JavascriptInterface public String savePlace(String json) {
            try { return service.savePlace(new JSONObject(json)).toString(); }
            catch(Exception e) { return "{\"ok\":false,\"error\":\"Datos del lugar no válidos\"}"; }
        }
        @JavascriptInterface public void openPlaceMap(String location) {
            try { MainActivity.this.openExternal(PlaceEntry.mapsUrl(location)); }
            catch(IllegalArgumentException e) { runOnUiThread(()->Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_LONG).show()); }
        }
        @JavascriptInterface public String saveCapture(String json) {
            try {
                JSONObject input = new JSONObject(json);
                long updateItemId = input.optLong("updateItemId", 0L);
                JSONObject result;
                if (updateItemId > 0L) {
                    input.put("id", updateItemId);
                    result = service.updateItem(input);
                    if (result.optBoolean("ok", false)) result.put("id", updateItemId);
                } else {
                    result = service.saveCapture(input);
                }
                if (result.optBoolean("ok", false)) {
                    long captureId = input.optLong("inboxCaptureId", 0);
                    long createdId = result.optLong("id", 0);
                    if (captureId > 0 && createdId > 0) db.markInboxCaptureConverted(captureId, "item", createdId);
                }
                if (result.has("reminderId") ||
                        (result.optJSONArray("reminderIds") != null && result.optJSONArray("reminderIds").length()>0) ||
                        (result.optJSONArray("rescheduled") != null && result.optJSONArray("rescheduled").length()>0)) {
                    ensureNotificationPermission();
                }
                return result.toString();
            } catch (Exception e) { return "{\"ok\":false,\"error\":\"Datos no válidos\"}"; }
        }
        @JavascriptInterface public String updateItem(String json) {
            try {
                JSONObject r = service.updateItem(new JSONObject(json));
                if (r.optJSONArray("rescheduled") != null && r.optJSONArray("rescheduled").length() > 0) ensureNotificationPermission();
                return r.toString();
            } catch(Exception e){return "{\"ok\":false}";}
        }
        @JavascriptInterface public String deleteItem(long id) { return service.deleteItem(id).toString(); }
        @JavascriptInterface public String restoreItem(String token) { return service.restoreItem(token).toString(); }
        @JavascriptInterface public String finalizeDelete(String token) { return service.finalizeDelete(token).toString(); }
        @JavascriptInterface public String getItem(long id) { return db.getItem(id).toString(); }
        @JavascriptInterface public String getNotes() { return db.getNotes().toString(); }
        @JavascriptInterface public long saveNote(String json) {
            try { return db.insertNote(new JSONObject(json)); } catch(Exception e) { return 0; }
        }
        @JavascriptInterface public boolean updateNote(String json) {
            try { return db.updateNote(new JSONObject(json)); } catch(Exception e) { return false; }
        }
        @JavascriptInterface public boolean autosaveNote(String json) {
            try { return db.autosaveNote(new JSONObject(json)); } catch(Exception e) { return false; }
        }
        @JavascriptInterface public String suggestNoteTitle(String body) {
            return NoteTitleEngine.derive(body);
        }
        @JavascriptInterface public String getItemReminders(long id) { return db.getReminderRulesForItem(id).toString(); }
        @JavascriptInterface public String replaceItemReminders(long id,String json) {
            try {
                JSONArray leads=new JSONArray(json); JSONObject r=service.replaceReminders(id,leads);
                if (r.optJSONArray("reminderIds") != null && r.optJSONArray("reminderIds").length()>0) ensureNotificationPermission();
                return r.toString();
            } catch(Exception e){ return "{\"ok\":false,\"error\":\"Avisos no válidos\"}"; }
        }
        @JavascriptInterface public String getCalendar(long start,long end) { return service.getCalendar(start,end).toString(); }
        @JavascriptInterface public String getOccurrence(long seriesId,long occurrenceAt) { return db.getOccurrence(seriesId,occurrenceAt).toString(); }
        @JavascriptInterface public long getNextOccurrence(long seriesId,long afterMillis) { return db.findNextOccurrence(seriesId,afterMillis); }
        @JavascriptInterface public String updateOccurrence(String json,String scope) {
            try { return service.updateOccurrence(new JSONObject(json),scope).toString(); } catch(Exception e){ return "{\"ok\":false}"; }
        }
        @JavascriptInterface public String deleteOccurrence(long seriesId,long occurrenceAt,String scope) { return service.deleteOccurrence(seriesId,occurrenceAt,scope).toString(); }
        @JavascriptInterface public String setOccurrenceStatus(long seriesId,long occurrenceAt,String status) { return service.setOccurrenceStatus(seriesId,occurrenceAt,status).toString(); }
        @JavascriptInterface public String getToday() { return db.getTodayData().toString(); }
        @JavascriptInterface public String getMoreSummary() { return db.getMoreSummary().toString(); }
        @JavascriptInterface public String getPeople() { return db.getPeople().toString(); }
        @JavascriptInterface public String getPerson(long id) { return db.getPerson(id).toString(); }
        @JavascriptInterface public String savePerson(String json) {
            try { return db.savePerson(new JSONObject(json)).toString(); }
            catch(Exception e) { return new JSONObject().toString(); }
        }
        @JavascriptInterface public boolean archivePerson(long id) { return db.archivePerson(id); }
        @JavascriptInterface public String getNexoFamiliaCloudStatus() {
            return familiaCloudSync == null ? "{}" : familiaCloudSync.getStatus().toString();
        }
        @JavascriptInterface public void linkNexoFamilia(String email, String password) {
            new Thread(() -> {
                JSONObject result = familiaCloudSync.link(email, password);
                if (result.optBoolean("ok", false)) jsCallbackJson("onNexoFamiliaLinked", result);
                else jsCallback("onNexoFamiliaCloudError", result.optString("error", "No se pudo vincular Nexo Familia."));
            }, "nexo-familia-link").start();
        }
        @JavascriptInterface public void syncNexoFamiliaEvents() {
            new Thread(() -> {
                JSONObject result = familiaCloudSync.syncNewEvents();
                if (result.optBoolean("ok", false)) jsCallbackJson("onNexoFamiliaSyncDone", result);
                else {
                    if (result.optBoolean("sessionExpired", false)) jsCallbackJson("onNexoFamiliaSessionExpired", result);
                    else jsCallback("onNexoFamiliaCloudError", result.optString("error", "No se pudo importar Nexo Familia."));
                }
            }, "nexo-familia-cloud-sync").start();
        }
        @JavascriptInterface public void disconnectNexoFamilia() {
            if (familiaCloudSync != null) familiaCloudSync.disconnect();
            jsCallbackJson("onNexoFamiliaDisconnected", familiaCloudSync == null ? new JSONObject() : familiaCloudSync.getStatus());
        }
        @JavascriptInterface public void chooseNexoFamiliaImport() { MainActivity.this.chooseNexoFamiliaImport(); }
        @JavascriptInterface public void repairBundledNexoFamiliaMigration() {
            new Thread(() -> {
                MainActivity.this.applyBundledNexoFamiliaMigration(true);
                JSONObject audit = MainActivity.this.auditBundledNexoFamiliaPayload(MainActivity.this.readBundledNexoFamiliaPayload());
                jsCallbackJson("onNexoFamiliaRepairDone", audit);
            }, "nexo-familia-repair").start();
        }
        @JavascriptInterface public String getNexoFamiliaImportStats() {
            JSONObject stats = db.getNexoFamiliaImportStats();
            SharedPreferences p = getSharedPreferences("nexo-familia-migration", MODE_PRIVATE);
            try {
                JSONObject audit = MainActivity.this.auditBundledNexoFamiliaPayload(MainActivity.this.readBundledNexoFamiliaPayload());
                stats.put("expectedEvents", audit.optInt("expectedEvents", p.getInt("expectedEvents", 0)));
                stats.put("verifiedEvents", audit.optInt("verifiedEvents", 0));
                stats.put("expectedFamily", audit.optInt("expectedFamily", 0));
                stats.put("verifiedFamily", audit.optInt("verifiedFamily", 0));
                stats.put("expectedSelected", audit.optInt("expectedSelected", 0));
                stats.put("verifiedSelected", audit.optInt("verifiedSelected", 0));
                stats.put("familyPending", audit.optInt("familyPending", 0));
                stats.put("familyDone", audit.optInt("familyDone", 0));
                stats.put("selectedPending", audit.optInt("selectedPending", 0));
                stats.put("selectedDone", audit.optInt("selectedDone", 0));
                stats.put("expectedReminderRules", audit.optInt("expectedReminderRules", p.getInt("expectedReminderRules", 0)));
                stats.put("verifiedReminderRules", audit.optInt("verifiedReminderRules", 0));
                stats.put("missingEvents", audit.optJSONArray("missingEvents"));
                stats.put("missingReminderRules", audit.optJSONArray("missingReminderRules"));
                stats.put("complete", audit.optBoolean("complete", false));
                stats.put("lastPreserved", p.getInt("lastPreserved", 0));
                stats.put("lastSkipped", p.getInt("lastSkipped", 0));
                stats.put("lastAttemptAt", p.getLong("lastAttemptAt", 0L));
                stats.put("snapshotVersion", p.getString("snapshotVersion", ""));
            } catch (Exception ignored) {}
            return stats.toString();
        }
        @JavascriptInterface public String getFamilyStyleSearchIndex() {
            JSONObject out=db.getFamilyStyleSearchIndex();
            try { out.put("recordings", RecordingStore.list(MainActivity.this)); }
            catch(Exception ignored) {}
            return out.toString();
        }
        @JavascriptInterface public String getNexoFamiliaMembers() {
            try {
                if (familiaCloudSync != null) {
                    JSONArray cloudMembers = familiaCloudSync.getCachedMembers();
                    if (cloudMembers.length() > 0) return cloudMembers.toString();
                }
                JSONArray members=MainActivity.this.readBundledNexoFamiliaPayload().optJSONArray("members");
                return members==null?"[]":members.toString();
            } catch(Exception ignored) { return "[]"; }
        }
        @JavascriptInterface public String search(String q) { return db.search(q).toString(); }
        @JavascriptInterface public String synthesizeJournal(String text) { return db.synthesizeJournal(text).toString(); }
        @JavascriptInterface public long saveJournal(String json) { try { return db.insertJournalEntry(new JSONObject(json)); } catch(Exception e) { return 0; } }
        @JavascriptInterface public String getJournal(long id) { return db.getJournalEntry(id).toString(); }
        @JavascriptInterface public String getJournalEntries(String q,String kind,boolean favoritesOnly) { return db.getJournalEntries(q,kind,favoritesOnly).toString(); }
        @JavascriptInterface public boolean updateJournal(String json) { try { return db.updateJournalEntry(new JSONObject(json)); } catch(Exception e) { return false; } }
        @JavascriptInterface public boolean deleteJournal(long id) { return db.deleteJournalEntry(id); }
        @JavascriptInterface public String getJournalDailyPick(String dayKey,boolean forceNew) { return db.getJournalDailyPick(dayKey,forceNew).toString(); }
        @JavascriptInterface public String parseMedication(String text) { return MedicationParser.parse(text).toString(); }
        @JavascriptInterface public long saveMedication(String json) {
            try { JSONObject in=new JSONObject(json);long id=db.insertMedication(in);MedicationScheduler.scheduleNext(MainActivity.this,db,id,System.currentTimeMillis()-1);ensureNotificationPermission();return id; } catch(Exception e){ return 0; }
        }
        @JavascriptInterface public boolean updateMedication(String json) {
            try { JSONObject in=new JSONObject(json);long id=in.optLong("id",0);boolean ok=db.updateMedication(in);if(ok){MedicationScheduler.cancel(MainActivity.this,id);MedicationScheduler.scheduleNext(MainActivity.this,db,id,System.currentTimeMillis()-1);ensureNotificationPermission();}return ok; } catch(Exception e){ return false; }
        }
        @JavascriptInterface public boolean deleteMedication(long id) { MedicationScheduler.cancel(MainActivity.this,id);return db.deleteMedication(id); }
        @JavascriptInterface public String getMedication(long id) { return db.getMedication(id).toString(); }
        @JavascriptInterface public String getMedications(String q,String mode) { return db.getMedications(q,mode).toString(); }
        @JavascriptInterface public String getMedicationTodayDoses() { return db.getMedicationTodayDoses().toString(); }
        @JavascriptInterface public String getMedicationDoses(long id,long from,long to) { return db.getMedicationDoses(id,from,to).toString(); }
        @JavascriptInterface public boolean setMedicationDoseStatus(long id,long scheduledAt,String status) {
            boolean ok=db.setMedicationDoseStatus(id,scheduledAt,status);if(ok){MedicationScheduler.cancelDoseAlerts(MainActivity.this,id,scheduledAt);MedicationScheduler.cancel(MainActivity.this,id);MedicationScheduler.scheduleNext(MainActivity.this,db,id,scheduledAt);try{android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(com.jruiz.nexo.medications.MedicationAlarmReceiver.notificationId(id,scheduledAt));}catch(Exception ignored){}}return ok;
        }
        @JavascriptInterface public boolean canScheduleExactAlarms() { return MainActivity.this.canScheduleExactAlarms(); }
        @JavascriptInterface public void requestExactAlarms() { runOnUiThread(MainActivity.this::requestExactAlarms); }
        @JavascriptInterface public void testMedicationVoice(String name) { runOnUiThread(() -> MainActivity.this.testMedicationVoice(name)); }
        @JavascriptInterface public String getTasks(String mode) { return db.getTasks(mode).toString(); }
        @JavascriptInterface public boolean setTaskStatus(long id,String status) { return db.setTaskStatus(id,status); }
        @JavascriptInterface public String getInbox() { return db.getInbox().toString(); }
        @JavascriptInterface public String getInboxCaptures() { return db.getInboxCaptures().toString(); }
        @JavascriptInterface public String getInboxCapture(long id) { return db.getInboxCapture(id).toString(); }
        @JavascriptInterface public long getInboxCaptureCount() { return db.getPendingInboxCaptureCount(); }
        @JavascriptInterface public boolean updateInboxCaptureClassification(long id,String json) {
            try { return db.updateInboxCaptureClassification(id,new JSONObject(json)); }
            catch(Exception e) { return false; }
        }
        @JavascriptInterface public boolean markInboxCaptureConverted(long id,String entity,long convertedId) {
            return db.markInboxCaptureConverted(id,entity,convertedId);
        }
        @JavascriptInterface public boolean discardInboxCapture(long id) {
            JSONObject capture=db.getInboxCapture(id);
            String path=capture.optString("attachmentPath","");
            boolean ok=db.discardInboxCapture(id);
            if(ok && !path.isBlank()) service.discardUnusedAttachment(path);
            return ok;
        }
        @JavascriptInterface public String getFavorites() { return db.getFavorites().toString(); }
        @JavascriptInterface public String getHistory() { return db.getRecentHistory().toString(); }
        @JavascriptInterface public String getReminders() { return db.getFutureReminders().toString(); }
        @JavascriptInterface public String getLists() { return db.getLists().toString(); }
        @JavascriptInterface public String getList(long id) { return db.getList(id).toString(); }
        @JavascriptInterface public String getListEntries(long id) { return db.getListEntries(id).toString(); }
        @JavascriptInterface public long createList(String title,String kind,boolean reusable) { return db.createList(title,kind,reusable); }
        @JavascriptInterface public boolean renameList(long id,String title) { return db.renameList(id,title); }
        @JavascriptInterface public int clearList(long id) { return db.clearList(id); }
        @JavascriptInterface public int resetList(long id) { return db.resetList(id); }
        @JavascriptInterface public boolean deleteList(long id) { return db.deleteList(id); }
        @JavascriptInterface public long duplicateList(long id) { return db.duplicateList(id); }
        @JavascriptInterface public int addListItems(long listId,String raw,boolean shopping) { return db.addListItems(listId,raw,shopping); }
        @JavascriptInterface public boolean toggleListEntry(long id,boolean checked) { return db.toggleListEntry(id,checked); }
        @JavascriptInterface public boolean updateListEntry(long id,String text,boolean shopping) { return db.updateListEntry(id,text,shopping); }
        @JavascriptInterface public boolean deleteListEntry(long id) { return db.deleteListEntry(id); }
        @JavascriptInterface public int deleteCheckedListEntries(long listId) { return db.deleteCheckedListEntries(listId); }
        @JavascriptInterface public String getLastParking() { return db.getLastParking().toString(); }
        @JavascriptInterface public void saveParking(String note) { requestParking(note); }
        @JavascriptInterface public void confirmParkingLocation() { runOnUiThread(MainActivity.this::confirmParkingLocation); }
        @JavascriptInterface public void retryParkingLocation() { runOnUiThread(MainActivity.this::captureParkingLocation); }
        @JavascriptInterface public void openParkingDirections() { MainActivity.this.openParkingDirections(); }
        @JavascriptInterface public void openLocationDirections(String locationText) { MainActivity.this.openLocationDirections(locationText); }
        @JavascriptInterface public boolean deleteParking() { return db.deleteActiveParking(); }
        @JavascriptInterface public void shareCalendarEvent(long itemId,long occurrenceAt,boolean wholeSeries) { MainActivity.this.shareCalendarEvent(itemId,occurrenceAt,wholeSeries); }
        @JavascriptInterface public void shareNexoItem(long itemId,long occurrenceAt,boolean wholeSeries) { MainActivity.this.shareNexoItem(itemId,occurrenceAt,wholeSeries); }
        @JavascriptInterface public void shareNexoList(long listId) { MainActivity.this.shareNexoList(listId); }
        @JavascriptInterface public void shareNexoJournal(long journalId) { MainActivity.this.shareNexoJournal(journalId); }
        @JavascriptInterface public void sharePlainText(String title,String text) { MainActivity.this.sharePlainText(title,text); }
        @JavascriptInterface public void confirmSharedImport() { MainActivity.this.confirmSharedImport(); }
        @JavascriptInterface public void cancelSharedImport() { MainActivity.this.cancelSharedImport(); }
        @JavascriptInterface public String getBackupStatus() { return backupStatus().toString(); }
        @JavascriptInterface public void exportBackup() { chooseBackupExport(); }
        @JavascriptInterface public void chooseBackupRestore() { MainActivity.this.chooseBackupRestore(); }
        @JavascriptInterface public void confirmRestoreBackup() { MainActivity.this.confirmRestoreBackup(); }
        @JavascriptInterface public String getRecordingStatus() { return RecordingService.status().toString(); }
        @JavascriptInterface public String getRecordings() { return RecordingStore.list(MainActivity.this).toString(); }
        @JavascriptInterface public void startRecording() { MainActivity.this.requestStartRecording(); }
        @JavascriptInterface public void pauseRecording() { MainActivity.this.sendRecordingAction(RecordingService.ACTION_PAUSE); }
        @JavascriptInterface public void resumeRecording() { MainActivity.this.sendRecordingAction(RecordingService.ACTION_RESUME); }
        @JavascriptInterface public void stopRecording() { MainActivity.this.sendRecordingAction(RecordingService.ACTION_STOP); }
        @JavascriptInterface public boolean renameRecording(String id,String title) { return RecordingStore.rename(MainActivity.this,id,title); }
        @JavascriptInterface public boolean deleteRecording(String id) { if(RecordingService.isRecordingActive()&&id.equals(RecordingService.status().optString("id")))return false;boolean ok=RecordingStore.delete(MainActivity.this,id);if(ok)TranscriptionStore.deleteForRecording(MainActivity.this,id);return ok; }
        @JavascriptInterface public void playRecording(String id) { MainActivity.this.playRecording(id); }
        @JavascriptInterface public void stopRecordingPlayback() { MainActivity.this.stopRecordingPlayback(); }
        @JavascriptInterface public String getRecordingPlaybackState() { return MainActivity.this.recordingPlaybackState().toString(); }
        @JavascriptInterface public void seekRecordingPlayback(String id,long positionMs) { MainActivity.this.seekRecordingPlayback(id,positionMs); }
        @JavascriptInterface public void setRecordingPlaybackSpeed(double speed) { MainActivity.this.setRecordingPlaybackSpeed((float)speed); }
        @JavascriptInterface public void shareRecording(String id) { MainActivity.this.shareRecording(id); }
        @JavascriptInterface public void exportRecording(String id) { MainActivity.this.exportRecording(id); }
        @JavascriptInterface public String getTranscriptionModelStatus() { return TranscriptionModelManager.status(MainActivity.this).toString(); }
        @JavascriptInterface public void downloadTranscriptionModel() { MainActivity.this.downloadTranscriptionModel(); }
        @JavascriptInterface public String getTranscriptionStatus(String id) { return TranscriptionStore.status(MainActivity.this,id).toString(); }
        @JavascriptInterface public void transcribeRecording(String id) { MainActivity.this.startTranscription(id); }
        @JavascriptInterface public void cancelTranscription(String id) { MainActivity.this.cancelTranscription(id); }
        @JavascriptInterface public boolean hasTranscription(String id) { return TranscriptionStore.exists(MainActivity.this,id); }
        @JavascriptInterface public String getTranscription(String id) { return TranscriptionStore.read(MainActivity.this,id); }
        @JavascriptInterface public String getTranscriptionDiagnostic(String id) { return TranscriptionStore.diagnostic(MainActivity.this,id); }
        @JavascriptInterface public boolean deleteTranscription(String id) { return TranscriptionStore.delete(MainActivity.this,id); }
        @JavascriptInterface public void shareTranscription(String id) { MainActivity.this.shareTranscription(id); }
        @JavascriptInterface public void copyTranscription(String id) { MainActivity.this.copyTranscription(id); }
        @JavascriptInterface public void exportTranscription(String id) { MainActivity.this.exportTranscription(id); }
        @JavascriptInterface public void chooseFile() { runOnUiThread(MainActivity.this::chooseFile); }
        @JavascriptInterface public void openWeather() { MainActivity.this.openWeather(); }
        @JavascriptInterface public void openExternal(String url) { if (url != null && (url.startsWith("https://")||url.startsWith("http://"))) MainActivity.this.openExternal(url); }
    }
}
