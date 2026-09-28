package com.jruiz.nexo.recordings;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Almacenamiento local de grabaciones. Los audios no forman parte del backup normal de Nexo. */
public final class RecordingStore {
    private static final String AUDIO_EXT = ".m4a";
    private static final String META_EXT = ".json";

    private RecordingStore() {}

    public static File directory(Context context) {
        File dir = new File(context.getFilesDir(), "recordings");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta de grabaciones");
        return dir;
    }

    public static String idFor(long startedAt) { return RecordingNaming.idFor(startedAt); }

    public static File audioFile(Context context, String id) {
        RecordingNaming.validateId(id);
        return new File(directory(context), id + AUDIO_EXT);
    }

    private static File metaFile(Context context, String id) {
        RecordingNaming.validateId(id);
        return new File(directory(context), id + META_EXT);
    }

    public static String defaultTitle(long startedAt) { return RecordingNaming.defaultTitle(startedAt); }

    public static JSONObject createMetadata(Context context, long startedAt) throws Exception {
        String id = idFor(startedAt);
        JSONObject meta = new JSONObject();
        meta.put("id", id);
        meta.put("title", defaultTitle(startedAt));
        meta.put("startedAt", startedAt);
        meta.put("createdAt", System.currentTimeMillis());
        writeMeta(context, id, meta);
        return meta;
    }

    public static JSONObject getMetadata(Context context, String id) {
        RecordingNaming.validateId(id);
        File f = metaFile(context, id);
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] data = in.readAllBytes();
                JSONObject meta = new JSONObject(new String(data, StandardCharsets.UTF_8));
                if (!id.equals(meta.optString("id"))) meta.put("id", id);
                if (meta.optLong("startedAt", 0) <= 0) meta.put("startedAt", parseStartedAt(id));
                if (meta.optString("title", "").isBlank()) meta.put("title", defaultTitle(meta.optLong("startedAt", parseStartedAt(id))));
                return meta;
            } catch (Exception ignored) {}
        }
        JSONObject fallback = new JSONObject();
        try {
            long startedAt = parseStartedAt(id);
            fallback.put("id", id);
            fallback.put("title", defaultTitle(startedAt));
            fallback.put("startedAt", startedAt);
        } catch (Exception ignored) {}
        return fallback;
    }

    public static boolean rename(Context context, String id, String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > 120) return false;
        File audio = audioFile(context, id);
        if (!audio.isFile()) return false;
        try {
            JSONObject meta = getMetadata(context, id);
            meta.put("title", t);
            meta.put("updatedAt", System.currentTimeMillis());
            writeMeta(context, id, meta);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean delete(Context context, String id) {
        File audio = audioFile(context, id);
        File meta = metaFile(context, id);
        boolean audioOk = !audio.exists() || audio.delete();
        boolean metaOk = !meta.exists() || meta.delete();
        return audioOk && metaOk;
    }

    public static JSONArray list(Context context) {
        JSONArray out = new JSONArray();
        File[] files = directory(context).listFiles((dir, name) -> name.startsWith("rec_") && name.endsWith(AUDIO_EXT));
        if (files == null || files.length == 0) return out;
        List<File> sorted = new ArrayList<>();
        java.util.Collections.addAll(sorted, files);
        sorted.sort(Comparator.comparingLong((File f) -> parseStartedAtSafe(idFromFile(f))).reversed());
        for (File f : sorted) {
            try {
                String id = idFromFile(f);
                JSONObject meta = getMetadata(context, id);
                long startedAt = meta.optLong("startedAt", parseStartedAt(id));
                JSONObject row = new JSONObject();
                row.put("id", id);
                row.put("title", meta.optString("title", defaultTitle(startedAt)));
                row.put("startedAt", startedAt);
                row.put("durationMs", durationMs(f));
                row.put("sizeBytes", Math.max(0, f.length()));
                out.put(row);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static String title(Context context, String id) {
        return getMetadata(context, id).optString("title", id);
    }

    private static void writeMeta(Context context, String id, JSONObject meta) throws Exception {
        File target = metaFile(context, id);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp, false)) {
            out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
            try { out.getFD().sync(); } catch (Exception ignored) {}
        }
        if (target.exists() && !target.delete()) throw new IllegalStateException("No se pudo actualizar la metadata");
        if (!tmp.renameTo(target)) throw new IllegalStateException("No se pudo guardar la metadata");
    }

    private static long durationMs(File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String raw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0 : Math.max(0, Long.parseLong(raw));
        } catch (Exception e) { return 0; }
        finally { try { mmr.release(); } catch (Exception ignored) {} }
    }

    private static String idFromFile(File f) {
        String n = f.getName();
        return n.substring(0, n.length() - AUDIO_EXT.length());
    }

    private static long parseStartedAtSafe(String id) {
        try { return RecordingNaming.parseStartedAt(id); } catch (Exception e) { return 0; }
    }

    private static long parseStartedAt(String id) { return RecordingNaming.parseStartedAt(id); }
}
