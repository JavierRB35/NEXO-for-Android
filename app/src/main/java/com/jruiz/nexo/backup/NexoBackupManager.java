package com.jruiz.nexo.backup;

import com.jruiz.nexo.BuildConfig;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Copia de seguridad portable de Nexo.
 * Un archivo .nexo contiene la base SQLite, los adjuntos y un manifiesto con hashes SHA-256.
 */
public final class NexoBackupManager {
    private NexoBackupManager() {}

    public static final int FORMAT_VERSION = 1;
    private static final String FORMAT = "nexo-backup";
    private static final String DB_ENTRY = "database/nexo.db";
    private static final String MANIFEST_ENTRY = "manifest.json";

    public static String suggestedFileName() {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(new Date());
        return "Nexo_Backup_" + stamp + ".nexo";
    }

    /** Crea y escribe una copia en el URI elegido mediante el selector de archivos de Android. */
    public static JSONObject exportToUri(Context context, DatabaseHelper db, Uri destination) throws Exception {
        File temp = new File(context.getCacheDir(), "nexo_export_" + System.currentTimeMillis() + ".nexo");
        try {
            JSONObject info = createBackupFile(context, db, temp);
            try (InputStream in = new BufferedInputStream(new FileInputStream(temp));
                 OutputStream out = new BufferedOutputStream(requireOutput(context, destination))) {
                copy(in, out);
            }
            info.put("ok", true);
            return info;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** Crea una copia interna; se usa también como red de seguridad antes de restaurar. */
    public static JSONObject createBackupFile(Context context, DatabaseHelper db, File output) throws Exception {
        File work = new File(context.getCacheDir(), "nexo_backup_work_" + System.currentTimeMillis());
        if (!work.mkdirs()) throw new IOException("No se pudo preparar la copia");
        File dbSnapshot = new File(work, "nexo.db");
        try {
            // No hay escrituras automáticas en segundo plano. Cerramos SQLite unos milisegundos para
            // copiar un único fichero consistente y evitar depender de WAL/-shm en la copia.
            synchronized (db) {
                try {
                    SQLiteDatabase sql = db.getWritableDatabase();
                    try (Cursor c = sql.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) { if (c.moveToFirst()) { /* checkpoint */ } }
                } catch (Exception ignored) {}
                db.close();
                File live = context.getDatabasePath(DatabaseHelper.databaseName());
                if (!live.exists()) {
                    // Forzamos creación si todavía no existía.
                    db.getWritableDatabase();
                    db.close();
                }
                copyFile(context.getDatabasePath(DatabaseHelper.databaseName()), dbSnapshot);
            }

            ArrayList<EntrySource> sources = new ArrayList<>();
            sources.add(new EntrySource(DB_ENTRY, dbSnapshot));
            File attachments = new File(context.getFilesDir(), "attachments");
            collectFiles(attachments, attachments, "attachments/", sources);

            JSONArray files = new JSONArray();
            for (EntrySource s : sources) {
                JSONObject f = new JSONObject();
                f.put("path", s.entryName);
                f.put("size", s.file.length());
                f.put("sha256", sha256(s.file));
                files.put(f);
            }

            JSONObject manifest = new JSONObject();
            long createdAt = System.currentTimeMillis();
            manifest.put("format", FORMAT);
            manifest.put("formatVersion", FORMAT_VERSION);
            manifest.put("appVersion", BuildConfig.VERSION_NAME);
            manifest.put("dbSchemaVersion", DatabaseHelper.schemaVersion());
            manifest.put("createdAt", createdAt);
            manifest.put("stats", db.getBackupStats());
            manifest.put("files", files);

            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("No se pudo crear la carpeta de copia");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
                byte[] manifestBytes = manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ZipEntry me = new ZipEntry(MANIFEST_ENTRY); me.setTime(createdAt); zos.putNextEntry(me); zos.write(manifestBytes); zos.closeEntry();
                for (EntrySource s : sources) addZipEntry(zos, s.entryName, s.file);
            }

            JSONObject result = new JSONObject();
            result.put("createdAt", createdAt);
            result.put("stats", manifest.getJSONObject("stats"));
            result.put("size", output.length());
            result.put("fileName", output.getName());
            return result;
        } finally {
            deleteTree(work);
            // Reabrir es perezoso; esta llamada valida que la base sigue disponible tras la copia.
            try { db.getReadableDatabase(); } catch (Exception ignored) {}
        }
    }

    /** Valida una copia seleccionada y devuelve información segura para mostrar antes de restaurar. */
    public static JSONObject inspect(Context context, Uri source) throws Exception {
        File temp = copyUriToTemp(context, source, "nexo_restore_inspect_");
        try { return inspectFile(temp, true); }
        finally { //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** Restaura exactamente el contenido de la copia validada. */
    public static JSONObject restoreFromUri(Context context, DatabaseHelper db, Uri source) throws Exception {
        File archive = copyUriToTemp(context, source, "nexo_restore_");
        File extracted = new File(context.getCacheDir(), "nexo_restore_dir_" + System.currentTimeMillis());
        if (!extracted.mkdirs()) throw new IOException("No se pudo preparar la restauración");
        try {
            JSONObject manifest = inspectFile(archive, true);
            extractSafe(archive, extracted);
            File incomingDb = new File(extracted, DB_ENTRY);
            if (!incomingDb.exists()) throw new IOException("La copia no contiene la base de datos");
            verifySqlite(incomingDb);

            // Red de seguridad local: si la restauración falla o el usuario se equivoca, conservamos
            // automáticamente el estado inmediatamente anterior. Se mantiene solo la última.
            File safetyDir = new File(context.getFilesDir(), "restore_safety");
            if (!safetyDir.exists() && !safetyDir.mkdirs()) throw new IOException("No se pudo crear la copia de seguridad previa");
            for (File f : safeListFiles(safetyDir)) if (f.isFile()) { //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            File safety = new File(safetyDir, "Antes_de_restaurar_" + System.currentTimeMillis() + ".nexo");
            createBackupFile(context, db, safety);

            synchronized (db) {
                db.close();
                File liveDb = context.getDatabasePath(DatabaseHelper.databaseName());
                File dbDir = liveDb.getParentFile();
                if (dbDir != null && !dbDir.exists() && !dbDir.mkdirs()) throw new IOException("No se pudo preparar la base de datos");

                File stagedDb = new File(dbDir, ".nexo_restore_" + System.currentTimeMillis() + ".db");
                copyFile(incomingDb, stagedDb);
                verifySqlite(stagedDb);

                File liveAttachments = new File(context.getFilesDir(), "attachments");
                File stagedAttachments = new File(context.getFilesDir(), ".attachments_restore_" + System.currentTimeMillis());
                File incomingAttachments = new File(extracted, "attachments");
                if (incomingAttachments.exists()) copyTree(incomingAttachments, stagedAttachments);
                else if (!stagedAttachments.mkdirs()) throw new IOException("No se pudo preparar los adjuntos");

                File oldDb = new File(dbDir, ".nexo_before_restore.db");
                File oldAttachments = new File(context.getFilesDir(), ".attachments_before_restore");
                deleteIfExists(oldDb); deleteTree(oldAttachments);
                deleteIfExists(new File(liveDb.getAbsolutePath() + "-wal"));
                deleteIfExists(new File(liveDb.getAbsolutePath() + "-shm"));
                deleteIfExists(new File(liveDb.getAbsolutePath() + "-journal"));

                boolean oldDbMoved = !liveDb.exists() || liveDb.renameTo(oldDb);
                if (!oldDbMoved) { deleteIfExists(stagedDb); deleteTree(stagedAttachments); throw new IOException("No se pudo preparar la base actual para restaurar"); }
                boolean oldAttachmentsMoved = !liveAttachments.exists() || liveAttachments.renameTo(oldAttachments);
                if (!oldAttachmentsMoved) {
                    if (oldDb.exists()) { //noinspection ResultOfMethodCallIgnored
                        oldDb.renameTo(liveDb);
                    }
                    deleteIfExists(stagedDb); deleteTree(stagedAttachments);
                    throw new IOException("No se pudo preparar los adjuntos actuales para restaurar");
                }

                boolean dbInstalled = stagedDb.renameTo(liveDb);
                boolean attachmentsInstalled = dbInstalled && stagedAttachments.renameTo(liveAttachments);
                if (!dbInstalled || !attachmentsInstalled) {
                    deleteIfExists(liveDb); deleteTree(liveAttachments);
                    if (oldDb.exists()) { //noinspection ResultOfMethodCallIgnored
                        oldDb.renameTo(liveDb);
                    }
                    if (oldAttachments.exists()) { //noinspection ResultOfMethodCallIgnored
                        oldAttachments.renameTo(liveAttachments);
                    }
                    deleteIfExists(stagedDb); deleteTree(stagedAttachments);
                    throw new IOException("No se pudo completar la restauración; se ha recuperado el estado anterior");
                }

                try {
                    // Al abrir, SQLiteOpenHelper migra automáticamente copias antiguas al esquema actual.
                    db.getWritableDatabase();
                    db.repairAttachmentPaths(liveAttachments);
                    deleteIfExists(oldDb); deleteTree(oldAttachments);
                } catch (Exception openError) {
                    db.close();
                    deleteIfExists(liveDb); deleteTree(liveAttachments);
                    if (oldDb.exists()) { //noinspection ResultOfMethodCallIgnored
                        oldDb.renameTo(liveDb);
                    }
                    if (oldAttachments.exists()) { //noinspection ResultOfMethodCallIgnored
                        oldAttachments.renameTo(liveAttachments);
                    }
                    try { db.getWritableDatabase(); } catch(Exception ignored){}
                    throw new IOException("La copia no se pudo abrir después de restaurar; se ha recuperado el estado anterior", openError);
                }
            }

            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("createdAt", manifest.optLong("createdAt", 0));
            result.put("stats", manifest.optJSONObject("stats") == null ? new JSONObject() : manifest.optJSONObject("stats"));
            result.put("safetyBackup", safety.getAbsolutePath());
            return result;
        } catch (Exception restoreError) {
            throw restoreError;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
            deleteTree(extracted);
        }
    }

    private static JSONObject inspectFile(File archive, boolean verifyHashes) throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
            if (manifestEntry == null) throw new IOException("No es una copia de Nexo válida");
            JSONObject manifest;
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                manifest = new JSONObject(readUtf8(in));
            }
            if (!FORMAT.equals(manifest.optString("format"))) throw new IOException("Formato de copia no reconocido");
            int formatVersion = manifest.optInt("formatVersion", 0);
            if (formatVersion < 1 || formatVersion > FORMAT_VERSION) throw new IOException("La copia usa un formato más nuevo que esta versión de Nexo");
            int dbVersion = manifest.optInt("dbSchemaVersion", 0);
            if (dbVersion > DatabaseHelper.schemaVersion()) throw new IOException("La copia fue creada por una versión de Nexo más nueva");
            if (zip.getEntry(DB_ENTRY) == null) throw new IOException("La copia no contiene la base de datos");

            if (verifyHashes) {
                JSONArray files = manifest.optJSONArray("files");
                if (files == null || files.length() == 0) throw new IOException("La copia no contiene información de integridad");
                byte[] buf = new byte[8192];
                for (int i=0;i<files.length();i++) {
                    JSONObject f = files.optJSONObject(i); if (f == null) continue;
                    String path = f.optString("path", "");
                    if (!safeEntryName(path)) throw new IOException("Ruta no válida dentro de la copia");
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null) throw new IOException("Falta un archivo de la copia: " + path);
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    long size = 0;
                    try (InputStream in = zip.getInputStream(entry)) {
                        int n; while ((n=in.read(buf))>0) { md.update(buf,0,n); size += n; }
                    }
                    String got = hex(md.digest());
                    if (!got.equalsIgnoreCase(f.optString("sha256")) || size != f.optLong("size", -1)) {
                        throw new IOException("La copia está dañada o incompleta");
                    }
                }
            }
            JSONObject preview = new JSONObject(manifest.toString());
            preview.put("ok", true);
            return preview;
        }
    }

    private static void verifySqlite(File file) throws Exception {
        SQLiteDatabase check = null;
        try {
            check = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor c = check.rawQuery("PRAGMA quick_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) throw new IOException("La base de datos de la copia está dañada");
            }
            try (Cursor c = check.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='items'", null)) {
                if (!c.moveToFirst()) throw new IOException("La copia no contiene datos de Nexo");
            }
        } finally { if (check != null) check.close(); }
    }

    private static File copyUriToTemp(Context context, Uri uri, String prefix) throws Exception {
        File temp = File.createTempFile(prefix, ".nexo", context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
            if (in == null) throw new IOException("No se pudo abrir la copia seleccionada");
            copy(in, out);
        }
        return temp;
    }

    private static OutputStream requireOutput(Context context, Uri uri) throws Exception {
        OutputStream out = context.getContentResolver().openOutputStream(uri, "w");
        if (out == null) throw new IOException("No se pudo escribir la copia");
        return out;
    }

    private static void extractSafe(File archive, File destination) throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            String root = destination.getCanonicalPath() + File.separator;
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!safeEntryName(name)) throw new IOException("Ruta no válida dentro de la copia");
                if (MANIFEST_ENTRY.equals(name)) continue;
                File out = new File(destination, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(root)) throw new IOException("Ruta no válida dentro de la copia");
                if (e.isDirectory()) { if (!out.exists() && !out.mkdirs()) throw new IOException("No se pudo extraer la copia"); continue; }
                File parent = out.getParentFile(); if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("No se pudo extraer la copia");
                try (InputStream in = zip.getInputStream(e); OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n; while ((n=in.read(buf))>0) os.write(buf,0,n);
                }
            }
        }
    }

    private static boolean safeEntryName(String name) {
        if (name == null || name.isBlank()) return false;
        String n=name.replace('\\','/');
        if (n.startsWith("/") || n.contains(":")) return false;
        for (String part : n.split("/")) if ("..".equals(part)) return false;
        return true;
    }

    private static void collectFiles(File root, File current, String prefix, List<EntrySource> out) throws IOException {
        if (!current.exists()) return;
        File[] files = current.listFiles(); if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isDirectory()) collectFiles(root, f, prefix, out);
            else {
                String rel = root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/');
                out.add(new EntrySource(prefix + rel, f));
            }
        }
    }

    private static void addZipEntry(ZipOutputStream zos, String name, File file) throws Exception {
        ZipEntry e = new ZipEntry(name); e.setTime(file.lastModified()); zos.putNextEntry(e);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) zos.write(buf,0,n);
        }
        zos.closeEntry();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) md.update(buf,0,n);
        }
        return hex(md.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(); for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b)); return s.toString(); }
    private static String readUtf8(InputStream in) throws Exception { ByteArrayOutputStream b=new ByteArrayOutputStream(); copy(in,b); return b.toString(java.nio.charset.StandardCharsets.UTF_8.name()); }
    private static void copy(InputStream in, OutputStream out) throws IOException { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0)out.write(buf,0,n); out.flush(); }
    private static void copyFile(File from, File to) throws IOException { File p=to.getParentFile(); if(p!=null&&!p.exists()&&!p.mkdirs())throw new IOException("No se pudo crear carpeta"); try(InputStream in=new BufferedInputStream(new FileInputStream(from));OutputStream out=new BufferedOutputStream(new FileOutputStream(to))){copy(in,out);} }
    private static void copyTree(File from, File to) throws IOException { if(from.isDirectory()){if(!to.exists()&&!to.mkdirs())throw new IOException("No se pudo restaurar adjuntos");for(File f:safeListFiles(from))copyTree(f,new File(to,f.getName()));}else copyFile(from,to); }
    private static File[] safeListFiles(File dir) { File[] f=dir.listFiles(); return f==null?new File[0]:f; }
    private static void deleteTree(File f) { if(f==null||!f.exists())return;if(f.isDirectory())for(File c:safeListFiles(f))deleteTree(c); //noinspection ResultOfMethodCallIgnored
        f.delete(); }
    private static void deleteIfExists(File f) { if(f.exists()) { //noinspection ResultOfMethodCallIgnored
        f.delete(); } }

    private static final class EntrySource {
        final String entryName; final File file;
        EntrySource(String entryName, File file) { this.entryName=entryName; this.file=file; }
    }
}
