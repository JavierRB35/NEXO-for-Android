package com.jruiz.nexo.documents;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.Locale;

/** Native, non-exported provider. Only the chosen file is granted temporarily, read-only. */
public final class DocumentProvider extends ContentProvider {
    public static final String AUTHORITY="com.jruiz.nexo.documents";

    public static Uri uriFor(Context context,String path,String name,String mime) throws IOException {
        File file=DocumentFiles.requireStored(new File(context.getFilesDir(),"attachments"),path);
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("file")
                .appendPath(file.getName()).appendQueryParameter("name",DocumentFiles.safeName(name))
                .appendQueryParameter("mime",mimeType(name,mime)).build();
    }

    public static String mimeType(String name,String hint) {
        String mime=hint==null?"":hint.trim().toLowerCase(Locale.ROOT);
        if (!mime.equals("application/octet-stream") && mime.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) return mime;
        String filename=name==null?"":name;int dot=filename.lastIndexOf('.');
        String inferred=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(dot+1).toLowerCase(Locale.ROOT));
        return inferred==null?"application/octet-stream":inferred;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return mimeType(uri.getQueryParameter("name"),uri.getQueryParameter("mime")); }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri==null || !"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())
                || uri.getPathSegments().size()!=2 || !"file".equals(uri.getPathSegments().get(0)))
            throw new FileNotFoundException("Documento no válido");
        try {
            File root=new File(getContext().getFilesDir(),"attachments");
            return DocumentFiles.requireStored(root,new File(root,uri.getPathSegments().get(1)).getPath());
        } catch(IOException e) { throw new FileNotFoundException("Documento no disponible"); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Solo lectura");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns,1);
        try {
            File file=resolve(uri);MatrixCursor.RowBuilder row=cursor.newRow();
            for(String column:columns) {
                if(OpenableColumns.DISPLAY_NAME.equals(column))row.add(DocumentFiles.safeName(uri.getQueryParameter("name")));
                else if(OpenableColumns.SIZE.equals(column))row.add(file.length());else row.add(null);
            }
        } catch(FileNotFoundException ignored) {}
        return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
}
