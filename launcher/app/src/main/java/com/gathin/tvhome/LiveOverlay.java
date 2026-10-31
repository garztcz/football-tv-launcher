package com.gathin.tvhome;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Reads the TV Live Feed add-on's data and overlays it on the baked
 * {@link ClubTheme} snapshot. The add-on (a separate app, the only one with
 * an INTERNET permission — this launcher stays offline by design) publishes
 * content://com.gathin.tvhome.livefeed/livedata.json with one entry per
 * theme id. Any failure at all — add-on absent, no data yet, bad JSON —
 * quietly keeps the snapshot, so the home screen can never break over a
 * feed problem. Reads are cached for 30 s; the cards only ask when built.
 */
final class LiveOverlay {

    private static final Uri DATA_URI =
            Uri.parse("content://com.gathin.tvhome.livefeed/livedata.json");
    private static final long TTL_MS = 30_000;

    private static JSONObject cachedThemes;
    private static long cachedAt;

    private LiveOverlay() {
    }

    static ClubTheme apply(Context ctx, ClubTheme base) {
        JSONObject themes = themes(ctx);
        if (themes == null) return base;
        JSONObject live = themes.optJSONObject(base.id);
        return live == null ? base : ClubTheme.withLive(base, live);
    }

    private static synchronized JSONObject themes(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < TTL_MS) return cachedThemes;
        cachedAt = now;
        try (InputStream in = ctx.getContentResolver().openInputStream(DATA_URI)) {
            if (in == null) return cachedThemes;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            JSONObject root = new JSONObject(bos.toString("UTF-8"));
            JSONObject themes = root.optJSONObject("themes");
            if (themes != null) cachedThemes = themes;
        } catch (Exception ignored) {
            // Add-on not installed (yet) or its data unreadable: snapshot.
        }
        return cachedThemes;
    }
}
