package com.gathin.tvhome.livefeed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Status screen for the feed: when it last refreshed and what happened per
 * theme. The real work runs on the 6-hour job; this screen is for checking
 * and for a manual refresh.
 */
public final class MainActivity extends Activity {

    private TextView statusView;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeedScheduler.schedulePeriodic(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(dp(56), dp(40), dp(56), dp(40));

        TextView title = text("TV LIVE FEED", 30, Color.WHITE, Typeface.BOLD);
        root.addView(title);
        TextView sub = text("Refreshes the launcher's football cards every 6 hours "
                + "(fixtures, results, standings). The launcher itself stays offline; "
                + "this app is the only one that goes online.", 16, 0xFF9A9A9A, Typeface.NORMAL);
        sub.setPadding(0, dp(10), 0, dp(24));
        root.addView(sub);

        refreshButton = new Button(this);
        refreshButton.setText("REFRESH NOW");
        refreshButton.setTextSize(16);
        refreshButton.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(260), dp(56));
        root.addView(refreshButton, blp);

        statusView = text("", 15, 0xFFCCCCCC, Typeface.NORMAL);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setPadding(0, dp(28), 0, 0);
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        showStatus();
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        statusView.setText("Refreshing…");
        new Thread(() -> {
            FeedUpdater.update(getApplicationContext());
            runOnUiThread(() -> {
                refreshButton.setEnabled(true);
                showStatus();
            });
        }, "livefeed-manual").start();
    }

    private void showStatus() {
        StringBuilder sb = new StringBuilder();
        try {
            File f = new File(getFilesDir(), FeedUpdater.STATUS_FILE);
            if (!f.exists()) {
                statusView.setText("No refresh has run yet. Press REFRESH NOW.");
                return;
            }
            JSONObject s = new JSONObject(
                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            long at = s.optLong("updatedAt", 0);
            if (at > 0) {
                SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.US);
                sb.append("Last refresh: ").append(fmt.format(new Date(at))).append("\n\n");
            }
            String[] themes = {"persebaya", "como", "manutd", "arsenal", "liverpool", "barcelona"};
            for (String theme : themes) {
                String v = s.optString(theme, "");
                if (!v.isEmpty()) sb.append(theme).append(": ").append(v).append("\n");
            }
        } catch (Exception e) {
            sb.append("Status unreadable: ").append(e);
        }
        statusView.setText(sb.toString());
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
