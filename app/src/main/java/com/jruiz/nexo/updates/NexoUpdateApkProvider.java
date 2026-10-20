package com.jruiz.nexo.updates;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Únicamente entrega al instalador una APK previamente validada en caché privada. */
public final class NexoUpdateApkProvider extends ContentProvider {
    private static final String FILE_NAME = "nexo-update.apk";
    private File getAuthorizedFile(Uri uri) throws FileNotFoundException {
        if (getContext() == null || !("/" + FILE_NAME).equals(uri.getPath()))
            throw new FileNotFoundException("URI no autorizada");
        File f = new File(new File(getContext().getCacheDir(), "updates"), FILE_NAME);
        if (!f.isFile()) throw new FileNotFoundException("APK no disponible");
        return f;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return ("/" + FILE_NAME).equals(uri.getPath()) ? "application/vnd.android.package-archive" : null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Solo lectura");
        return ParcelFileDescriptor.open(getAuthorizedFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                   String[] selectionArgs, String sortOrder) {
        try {
            File f = getAuthorizedFile(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Nexo-update.apk";
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = f.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
