package com.jruiz.nexo.recordings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** ContentProvider nativo y de solo lectura para compartir grabaciones sin exponer file://. */
public final class RecordingProvider extends ContentProvider {
    public static final String AUTHORITY = "com.jruiz.nexo.recordings";

    public static Uri uriFor(Context context, String id) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("audio").appendPath(id).build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "audio/mp4"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Solo lectura");
        String id = extractId(uri);
        File file = RecordingStore.audioFile(providerContext(), id);
        if (!file.isFile()) throw new FileNotFoundException("Grabación no encontrada");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String id = extractId(uri);
        File file = RecordingStore.audioFile(providerContext(), id);
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String c : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(c)) row.add(RecordingStore.title(providerContext(), id) + ".m4a");
            else if (OpenableColumns.SIZE.equals(c)) row.add(file.isFile() ? file.length() : 0L);
            else row.add(null);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }

    private Context providerContext() {
        Context c = getContext();
        if (c == null) throw new IllegalStateException("Contexto no disponible");
        return c;
    }

    private static String extractId(Uri uri) {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 2 || !"audio".equals(uri.getPathSegments().get(0))) {
            throw new IllegalArgumentException("URI de grabación no válida");
        }
        return uri.getPathSegments().get(1);
    }
}
