package com.jruiz.nexo.updates;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.jruiz.nexo.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Canal de actualización opcional: no instala, ni reemplaza datos, sin consentimiento. */
public final class NexoUpdateManager {
    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/JavierRB35/nexo-android-updates/main/latest.json";
    private static final String APK_PATH_PREFIX =
            "/JavierRB35/nexo-android-updates/releases/download/";
    private static final String PINNED_CERT_SHA256 =
            "595799E351233418F05BC5053E42B3AFF439D677FFBB0F59A1D92E29D15FEC48";
    private static final String PREFS = "nexo_updates";
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final long MAX_APK_BYTES = 250L * 1024 * 1024;
    private final Activity activity;
    private final Consumer<JSONObject> listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    private volatile String status = "{\"state\":\"idle\",\"message\":\"Todavía no se han buscado actualizaciones.\"}";
    private volatile Release latest;
    private boolean waitingForInstallPermission;

    public NexoUpdateManager(Activity activity, Consumer<JSONObject> listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public JSONObject getStatus() {
        try { return new JSONObject(status); }
        catch (Exception e) { return new JSONObject(); }
    }

    private void emit(String state, String message, Release release) {
        JSONObject result = new JSONObject();
        try {
            result.put("state", state);
            result.put("message", message);
            if (release != null) {
                result.put("versionName", release.versionName);
                result.put("versionCode", release.versionCode);
                result.put("notes", release.notes);
            }
        } catch (Exception ignored) {}
        status = result.toString();
        main.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) listener.accept(getStatus());
        });
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void check(boolean manual) {
        if (downloading.get()) return;
        if (!checking.compareAndSet(false, true)) return;
        long now = System.currentTimeMillis();
        long last = prefs().getLong("last_successful_check", 0);
        if (!manual && last > 0 && now >= last && now - last < CHECK_INTERVAL_MS) {
            checking.set(false);
            return;
        }
        emit("checking", "Buscando nuevas versiones…", null);
        new Thread(() -> {
            try {
                Release remote = fetchRelease();
                latest = remote;
                prefs().edit().putLong("last_successful_check", System.currentTimeMillis()).apply();
                if (remote.versionCode <= BuildConfig.VERSION_CODE) {
                    emit("current", "La versión instalada está actualizada.", null);
                    if (manual) showMessage("Nexo actualizado", "No hay versiones publicadas más recientes.");
                } else if (!isInstalledSignatureOfficial(activity)) {
                    emit("error", "La firma de esta instalación no coincide con la firma oficial de Nexo.", remote);
                    if (manual) showMessage("Firma incompatible",
                            "Esta instalación fue firmada con otra clave. No se puede sustituir mediante una actualización. Haz una copia de seguridad antes de cambiar de instalación.");
                } else {
                    emit("available", "Nueva versión " + remote.versionName + " disponible.", remote);
                    int alerted = prefs().getInt("last_alerted_code", 0);
                    if (manual || alerted != remote.versionCode) {
                        prefs().edit().putInt("last_alerted_code", remote.versionCode).apply();
                        main.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            new AlertDialog.Builder(activity)
                                    .setTitle("Actualización de Nexo")
                                    .setMessage("Disponible Nexo " + remote.versionName + ".\n\n"
                                            + remote.notes + "\n\nLa instalación requiere tu confirmación.")
                                    .setNegativeButton("Más tarde", (dialog, which) -> {})
                                    .setPositiveButton("Actualizar", (dialog, which) -> download(remote))
                                    .show();
                        });
                    }
                }
            } catch (Exception e) {
                emit("error", "No se pudo consultar el canal de actualizaciones. Comprueba tu conexión o que el canal público esté configurado.", null);
                if (manual) showMessage("Actualizaciones no disponibles",
                        "No se pudo comprobar la última versión publicada. No se ha modificado ningún dato.");
            } finally {
                checking.set(false);
            }
        }, "nexo-update-check").start();
    }

    private void showMessage(String title, String message) {
        main.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed())
                new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                        .setPositiveButton("Aceptar", null).show();
        });
    }

    /** Descarga únicamente el manifiesto público HTTPS; jamás usa credenciales de GitHub. */
    public static Release fetchRelease() throws Exception {
        URL url = new URL(MANIFEST_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("Accept", "application/json");
            if (con.getResponseCode() != 200) throw new IllegalStateException("manifest HTTP");
            if (con.getContentLengthLong() > 32768) throw new IllegalStateException("manifest grande");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = con.getInputStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > 32768) throw new IllegalStateException("manifest grande");
                    out.write(b, 0, n);
                }
            }
            return Release.parse(new JSONObject(out.toString("UTF-8")));
        } finally {
            con.disconnect();
        }
    }

    public static final class Release {
        public final String versionName, apkUrl, sha256, notes;
        public final int versionCode;
        public final long size;

        private Release(String name, int code, String url, String hash, long bytes, String notes) {
            this.versionName = name; this.versionCode = code; this.apkUrl = url;
            this.sha256 = hash; this.size = bytes; this.notes = notes;
        }

        public static Release parse(JSONObject json) throws Exception {
            if (json.getInt("schemaVersion") != 1 ||
                    !BuildConfig.APPLICATION_ID.equals(json.getString("packageName")))
                throw new IllegalArgumentException("canal de otro paquete");
            String name = json.getString("versionName");
            int code = json.getInt("versionCode");
            String hash = json.getString("apkSha256").toUpperCase(Locale.ROOT);
            long size = json.getLong("apkSize");
            String apk = json.getString("apkUrl");
            String notes = json.optString("notes", "Mejoras y correcciones.");
            if (!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || code <= 0 ||
                    !hash.matches("[0-9A-F]{64}") || size < 1024 * 1024 || size > MAX_APK_BYTES)
                throw new IllegalArgumentException("metadatos no válidos");
            URL u = new URL(apk);
            if (!"https".equals(u.getProtocol()) || !"github.com".equalsIgnoreCase(u.getHost()) ||
                    !u.getPath().startsWith(APK_PATH_PREFIX + "v" + name + "/") ||
                    !u.getPath().endsWith("/Nexo-v" + name + "-release.apk") ||
                    u.getQuery() != null || u.getRef() != null)
                throw new IllegalArgumentException("origen APK no válido");
            return new Release(name, code, apk, hash, size, notes.length() > 800 ? notes.substring(0, 800) : notes);
        }
    }

    public static boolean isInstalledSignatureOfficial(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    BuildConfig.APPLICATION_ID, signatureFlags());
            return hasOfficialSigner(info);
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("deprecation")
    private static int signatureFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static boolean hasOfficialSigner(PackageInfo packageInfo) throws Exception {
        if (packageInfo == null) return false;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (packageInfo.signingInfo == null) return false;
            signatures = packageInfo.signingInfo.getApkContentsSigners();
        } else {
            signatures = packageInfo.signatures;
        }
        if (signatures == null || signatures.length != 1) return false;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        return PINNED_CERT_SHA256.equals(hex(digest));
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02X", b & 255));
        return s.toString();
    }

    public static boolean isNewer(Release release) {
        return release.versionCode > BuildConfig.VERSION_CODE;
    }

    private File apkFile() {
        return new File(new File(activity.getCacheDir(), "updates"), "nexo-update.apk");
    }

    public void download(Release release) {
        if (release == null || !isNewer(release) || !downloading.compareAndSet(false, true)) return;
        emit("downloading", "Descargando y verificando Nexo " + release.versionName + "…", release);
        new Thread(() -> {
            File target = apkFile();
            File partial = new File(target.getParentFile(), "nexo-update.partial.apk");
            HttpURLConnection con = null;
            try {
                if (!isInstalledSignatureOfficial(activity)) throw new IllegalStateException("La firma instalada no es oficial.");
                if (!target.getParentFile().exists() && !target.getParentFile().mkdirs())
                    throw new IllegalStateException("No se pudo preparar la descarga.");
                con = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(60000);
                con.setInstanceFollowRedirects(true);
                if (con.getResponseCode() != 200 || !"https".equals(con.getURL().getProtocol()))
                    throw new IllegalStateException("Descarga HTTP incorrecta.");
                String host = con.getURL().getHost().toLowerCase(Locale.ROOT);
                if (!("github.com".equals(host) || host.endsWith(".githubusercontent.com")))
                    throw new IllegalStateException("Redirección fuera de GitHub.");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long copied = 0;
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] block = new byte[32768];
                    int n;
                    while ((n = in.read(block)) != -1) {
                        copied += n;
                        if (copied > release.size || copied > MAX_APK_BYTES)
                            throw new IllegalStateException("La APK supera el tamaño anunciado.");
                        digest.update(block, 0, n);
                        out.write(block, 0, n);
                    }
                    out.getFD().sync();
                }
                if (copied != release.size || !release.sha256.equals(hex(digest.digest())))
                    throw new IllegalStateException("La huella o tamaño de la APK no coincide.");
                @SuppressWarnings("deprecation")
                PackageInfo pkg = activity.getPackageManager().getPackageArchiveInfo(partial.getAbsolutePath(), signatureFlags());
                if (pkg == null || !BuildConfig.APPLICATION_ID.equals(pkg.packageName) ||
                        (Build.VERSION.SDK_INT >= 28 ? pkg.getLongVersionCode() : pkg.versionCode) != release.versionCode ||
                        !hasOfficialSigner(pkg))
                    throw new IllegalStateException("APK con paquete, versión o firma incompatibles.");
                if (target.exists() && !target.delete()) throw new IllegalStateException("No se pudo limpiar la descarga anterior.");
                if (!partial.renameTo(target)) throw new IllegalStateException("No se pudo conservar la APK verificada.");
                emit("ready", "Descarga verificada. Abriendo el instalador de Android…", release);
                main.post(this::installDownloaded);
            } catch (Exception error) {
                emit("error", "Descarga o verificación fallida: " + error.getMessage(), release);
                showMessage("Actualización no instalada", "No se pudo verificar la nueva APK.\n" + error.getMessage());
            } finally {
                if (con != null) con.disconnect();
                if (partial.exists()) partial.delete();
                downloading.set(false);
            }
        }, "nexo-update-download").start();
    }

    private void installDownloaded() {
        File file = apkFile();
        if (!file.isFile() || activity.isFinishing() || activity.isDestroyed()) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            new AlertDialog.Builder(activity).setTitle("Permitir instalar Nexo")
                    .setMessage("Android debe autorizar a Nexo como origen de instalación. Después podrás confirmar la actualización en el instalador del sistema.")
                    .setNegativeButton("Cancelar", (d,w) -> waitingForInstallPermission = false)
                    .setPositiveButton("Abrir ajustes", (d,w) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    }).show();
            return;
        }
        waitingForInstallPermission = false;
        Uri uri = Uri.parse("content://" + activity.getPackageName() + ".updates/nexo-update.apk");
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.setClipData(ClipData.newRawUri("Nexo APK", uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(install);
        } catch (Exception e) {
            showMessage("No se pudo abrir el instalador", "Android no pudo iniciar la instalación: " + e.getMessage());
        }
    }

    public void onResume() {
        if (waitingForInstallPermission && Build.VERSION.SDK_INT >= 26 &&
                activity.getPackageManager().canRequestPackageInstalls()) {
            installDownloaded();
        }
    }
}
