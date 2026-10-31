package com.gathin.tvhome.livefeed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Serves the add-on's livedata.json to the launcher at
 * content://com.gathin.tvhome.livefeed/livedata.json. The payload is public
 * football data, so it is readable by any app; nothing else is exposed and
 * every write operation is a no-op.
 */
public final class FeedProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    private File dataFile() {
        return new File(getContext().getFilesDir(), FeedUpdater.DATA_FILE);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = dataFile();
        if (!f.exists()) throw new FileNotFoundException("no live data yet");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File f = dataFile();
            if (!f.exists()) return null;
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            MatrixCursor cursor = new MatrixCursor(new String[]{"json"});
            cursor.addRow(new Object[]{json});
            return cursor;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
