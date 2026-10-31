package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Nothing-style date + time card (user's spec, 2026-10-31): dot-matrix day
 * number with weekday/month on the top row, a LARGE digital clock on its own
 * row below (never small, never squeezed beside the date — v1's single row
 * overflowed the card at 1080p and the clock landed on top of the weekday).
 * Self-updating once a second while attached; no network.
 */
public final class DateTimeCardView extends LinearLayout {

    private static final String[] DAYS =
            {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
    private static final String[] MONTHS =
            {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView dayNumber;
    private TextView weekday;
    private TextView monthYear;
    private TextView clockMain;
    private TextView clockSecs;
    private int lastMinute = -1;
    private int lastSecond = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 250);
        }
    };

    public DateTimeCardView(Context context) {
        super(context);
        build(context);
    }

    public DateTimeCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        build(context);
    }

    private TextView text(Context c, float sp, int color, Typeface font, String value) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (font != null) t.setTypeface(font);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void build(Context c) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.nothing_card_bg);
        setPadding(dp(30), dp(20), dp(30), dp(20));
        setFocusable(false);

        Typeface doto = getResources().getFont(R.font.doto_bold);
        Typeface geist = getResources().getFont(R.font.geist_light);

        // Top row: big dot-matrix day number + weekday / month-year column.
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        dayNumber = text(c, 60, Color.WHITE, doto, "31");
        top.addView(dayNumber);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        midLp.setMarginStart(dp(16));
        mid.setLayoutParams(midLp);
        weekday = text(c, 19, Color.WHITE, geist, "SATURDAY ");
        weekday.setLetterSpacing(0.12f);
        // Trailing space above (and in refresh): with letterSpacing, Android's
        // text layout under-measures the final glyph and clips it ("SATURDA"
        // in v1.1/v1.2 despite end padding) — a sacrificial trailing space
        // absorbs the clip instead of the Y.
        monthYear = text(c, 15, 0xFF9A9A9A, geist, "OCT / 2026");
        monthYear.setLetterSpacing(0.18f);
        monthYear.setPadding(0, 0, dp(5), 0);
        LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        myLp.topMargin = dp(7);
        monthYear.setLayoutParams(myLp);
        mid.addView(weekday);
        mid.addView(monthYear);
        top.addView(mid);
        addView(top);

        // Nothing accent: one short red bar between the date and the clock.
        View accent = new View(c);
        accent.setBackgroundColor(0xFFD71921);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(30), dp(3));
        accentLp.topMargin = dp(15);
        accent.setLayoutParams(accentLp);
        addView(accent);

        // Bottom row: the clock, large, on its own line.
        LinearLayout clockBox = new LinearLayout(c);
        clockBox.setOrientation(HORIZONTAL);
        clockBox.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        clockLp.topMargin = dp(13);
        clockBox.setLayoutParams(clockLp);
        clockMain = text(c, 44, Color.WHITE, doto, "15:17");
        clockSecs = text(c, 22, 0xFFD71921, doto, "24");
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        secLp.setMarginStart(dp(7));
        secLp.bottomMargin = dp(6);
        clockSecs.setLayoutParams(secLp);
        clockBox.addView(clockMain);
        clockBox.addView(clockSecs);
        addView(clockBox);

        refresh();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        Calendar now = Calendar.getInstance();
        int sec = now.get(Calendar.SECOND);
        int min = now.get(Calendar.MINUTE);
        if (sec != lastSecond) {
            lastSecond = sec;
            clockSecs.setText(String.format(Locale.US, "%02d", sec));
        }
        if (min == lastMinute) return;
        lastMinute = min;
        dayNumber.setText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)));
        weekday.setText(DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + " ");
        monthYear.setText(MONTHS[now.get(Calendar.MONTH)] + " / " + now.get(Calendar.YEAR));
        clockMain.setText(String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), min));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastMinute = -1;
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
