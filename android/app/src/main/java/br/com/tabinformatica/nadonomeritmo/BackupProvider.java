/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only, temporary attachment access granted to the share target by Android. */
public final class BackupProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File backupFile(Uri uri) throws FileNotFoundException {
        List<String> parts = uri.getPathSegments();
        if (getContext() == null || !(getContext().getPackageName() + ".backup").equals(uri.getAuthority()) || parts.size() != 2) throw new FileNotFoundException();
        boolean photo="photo".equals(parts.get(0));
        if ((!photo && !"backup".equals(parts.get(0))) || !parts.get(1).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."+(photo?"png":"json"))) throw new FileNotFoundException();
        File file = new File(new File(getContext().getCacheDir(), photo ? "photos" : "backups"), parts.get(1));
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(backupFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = backupFile(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = uri.getPathSegments().get(0).equals("photo") ? "nado-minha-natacao.png" : "nado-backup.json";
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values); return cursor;
        } catch (FileNotFoundException error) { return null; }
    }
    @Override public String getType(Uri uri) { return uri.getPathSegments().size()==2 && "photo".equals(uri.getPathSegments().get(0)) ? "image/png" : "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
