package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Club fixture card v3 (2026-10-31, following his reference
 * screenshot): league kicker + home/away tag, the next fixture in large type,
 * a live kick-off countdown (recomputed every 30 s — pure arithmetic on a
 * constant, no network), kick-off line, FORM badges for the last five league
 * matches, the last result, and the fixtures after that. All content and the
 * accent colours come from the active {@link ClubTheme} (Persebaya or
 * Como 1907 — his switchable-themes decision, 2026-10-31); the data is a
 * dated snapshot per theme, refreshed by a separate add-on later (PLAN.md).
 */
public final class PersebayaCardView extends LinearLayout {

    private final ClubTheme theme;

    private static final int RED = 0xFFD71921;
    private static final int RED_SOFT = 0xFFFF5960;
    private static final int GREY = 0xFF9A9A9A;
    private static final int FAINT = 0xFFBFBFBF;
    private static final int GREEN = 0xFF1E9E5A;
    private static final int DRAW = 0xFF5A5A5A;

    private TextView countdown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshCountdown();
            handler.postDelayed(this, 30_000L);
        }
    };

    public PersebayaCardView(Context context) {
        super(context);
        theme = ClubTheme.current(context);
        build(context);
    }

    public PersebayaCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        theme = ClubTheme.current(context);
        build(context);
    }

    private TextView text(float sp, int color, Typeface font, String value, float spacing) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (font != null) t.setTypeface(font);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        if (spacing != 0f) t.setLetterSpacing(spacing);
        return t;
    }

    /** A vertical stack of fixture lines for one column of the fixtures table. */
    private LinearLayout fixtureColumn() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private LinearLayout.LayoutParams columnLineLp(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    /** The grey vertical rule between fixture columns — same colour/weight as the horizontal divider. */
    private View columnDivider() {
        View v = new View(getContext());
        v.setBackgroundColor(0xFF262626);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT);
        lp.setMarginStart(dp(7));
        lp.setMarginEnd(dp(7));
        v.setLayoutParams(lp);
        return v;
    }

    private void build(Context c) {
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.nothing_card_bg);
        setPadding(dp(22), dp(16), dp(22), dp(16));
        setFocusable(false);

        Typeface geist = getResources().getFont(R.font.geist_light);

        // Kicker row: red bar + league (left), venue tag (right).
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(c);
        bar.setBackgroundColor(theme.accent);
        head.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(15)));
        TextView kicker = text(13, Color.WHITE, geist, "  " + theme.kicker, 0.18f);
        head.addView(kicker);
        View headSpacer = new View(c);
        head.addView(headSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(text(13, theme.accentText, geist, theme.venueTag, 0.18f));
        addView(head);

        // Fixture block: title + countdown + kick-off on the left, the club
        // crest filling the space under the venue tag on the right (the user,
        // 2026-10-31 — the reference card carries the Roma crest the same way).
        LinearLayout titleBlock = new LinearLayout(c);
        titleBlock.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tbLp.topMargin = dp(10);
        titleBlock.setLayoutParams(tbLp);

        LinearLayout fixtureCol = new LinearLayout(c);
        fixtureCol.setOrientation(VERTICAL);
        fixtureCol.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView next = text(25, Color.WHITE, geist, theme.nextMatch, 0.01f);
        fixtureCol.addView(next);
        countdown = text(15, theme.accentText, geist, "", 0.04f);
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cdLp.topMargin = dp(7);
        countdown.setLayoutParams(cdLp);
        fixtureCol.addView(countdown);
        TextView when = text(14, FAINT, geist, theme.nextWhen, 0.05f);
        LinearLayout.LayoutParams whenLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        whenLp.topMargin = dp(5);
        when.setLayoutParams(whenLp);
        fixtureCol.addView(when);
        titleBlock.addView(fixtureCol);

        ImageView crest = new ImageView(c);
        crest.setImageResource(theme.crestRes);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams crestLp = new LinearLayout.LayoutParams(dp(68), dp(80));
        crestLp.setMarginStart(dp(12));
        crestLp.gravity = Gravity.TOP;
        crest.setLayoutParams(crestLp);
        titleBlock.addView(crest);
        addView(titleBlock);

        // FORM badges.
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(HORIZONTAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        formLp.topMargin = dp(10);
        form.setLayoutParams(formLp);
        form.addView(text(12, GREY, geist, "LAST MATCH", 0.18f));
        for (char r : theme.form) {
            // FotMob-style result dots (2026-10-31): green check,
            // grey dash, red cross — no letters.
            TextView badge = new TextView(c);
            badge.setText(r == 'W' ? "✓" : r == 'L' ? "✕" : "–");
            badge.setTextSize(14);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(r == 'W' ? GREEN : r == 'L' ? RED : DRAW);
            badge.setBackground(bg);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(26), dp(26));
            bLp.setMarginStart(dp(8));
            badge.setLayoutParams(bLp);
            form.addView(badge);
        }
        addView(form);

        // Divider.
        View divider = new View(c);
        divider.setBackgroundColor(0xFF262626);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
        divLp.topMargin = dp(12);
        divider.setLayoutParams(divLp);
        addView(divider);

        // Last result + the two fixtures after this one.
        TextView last = text(14, Color.WHITE, geist, theme.lastResult, 0.03f);
        LinearLayout.LayoutParams lastLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lastLp.topMargin = dp(7);
        last.setLayoutParams(lastLp);
        addView(last);
        // Upcoming fixtures as three columns (match / stadium / date) split by grey
        // vertical rules in the same style as the divider above (2026-10-31).
        LinearLayout table = new LinearLayout(c);
        table.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tableLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tableLp.topMargin = dp(4);
        table.setLayoutParams(tableLp);

        LinearLayout colMatch = fixtureColumn();
        colMatch.setLayoutParams(new LinearLayout.LayoutParams(dp(178), LayoutParams.WRAP_CONTENT));
        LinearLayout colStadium = fixtureColumn();
        colStadium.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LinearLayout colDate = fixtureColumn();
        colDate.setLayoutParams(new LinearLayout.LayoutParams(dp(78), LayoutParams.MATCH_PARENT));
        for (int i = 0; i < theme.upNext.length; i++) {
            int m = i == 0 ? 0 : 3;
            TextView mt = text(13, GREY, geist, theme.upNext[i][0], 0.03f);
            mt.setLayoutParams(columnLineLp(m));
            colMatch.addView(mt);
            TextView st = text(13, GREY, geist, theme.upNext[i][1], 0.03f);
            st.setGravity(android.view.Gravity.CENTER);
            st.setLayoutParams(columnLineLp(m));
            colStadium.addView(st);
            TextView dt = text(13, GREY, geist, theme.upNext[i][2], 0.03f);
            dt.setGravity(android.view.Gravity.END);
            dt.setLayoutParams(columnLineLp(m));
            colDate.addView(dt);
        }
        table.addView(colMatch);
        table.addView(columnDivider());
        table.addView(colStadium);
        table.addView(columnDivider());
        table.addView(colDate);
        addView(table);

        refreshCountdown();
    }

    /** Countdown to the active theme's kick-off instant. */
    private void refreshCountdown() {
        long diff = theme.kickoffMillis - System.currentTimeMillis();
        String label;
        if (diff <= 0) {
            label = theme.kickoffDone;
        } else {
            long days = diff / 86_400_000L;
            long hours = (diff % 86_400_000L) / 3_600_000L;
            long minutes = (diff % 3_600_000L) / 60_000L;
            if (days > 0) label = "Kick-off in " + days + (days == 1 ? " day " : " days ") + hours + "h";
            else if (hours > 0) label = "Kick-off in " + hours + "h " + minutes + "m";
            else label = "Kick-off in " + minutes + " min";
        }
        if (countdown != null) countdown.setText(label);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
