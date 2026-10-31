package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * League standings card v3 (2026-10-31, following his reference
 * screenshot): # / TEAM / P / PTS columns, the club's own row carried on a
 * full band in the club colour. Title, rows and band colour come from the
 * active {@link ClubTheme} (Persebaya / Super League or Como 1907 / Serie A,
 * snapshots of 31 Oct 2026); the launcher never goes online — a separate
 * add-on app will refresh this later.
 */
public final class StandingsCardView extends LinearLayout {

    private final ClubTheme theme;

    private static final int GREY = 0xFF9A9A9A;
    private static final int FAINT = 0xFFBFBFBF;

    public StandingsCardView(Context context) {
        super(context);
        theme = ClubTheme.current(context);
        build(context);
    }

    public StandingsCardView(Context context, AttributeSet attrs) {
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

    private void build(Context c) {
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.nothing_card_bg);
        setPadding(dp(26), dp(16), dp(26), dp(12));
        setFocusable(false);

        Typeface geist = getResources().getFont(R.font.geist_light);
        Typeface mono = getResources().getFont(R.font.geistmono_light);

        addView(text(16, Color.WHITE, geist, theme.standingsTitle, 0.12f));

        // Column headers.
        LinearLayout head = row();
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headLp.topMargin = dp(12);
        head.setLayoutParams(headLp);
        head.setPadding(dp(10), 0, dp(10), 0);
        head.addView(cell(text(11, GREY, mono, "#", 0.05f), 26));
        head.addView(cell(text(11, GREY, geist, "TEAM", 0.15f), 0));
        head.addView(cell(text(11, GREY, mono, "P", 0.05f), 36, Gravity.CENTER));
        head.addView(cell(text(11, GREY, mono, "PTS", 0.05f), 46, Gravity.END));
        addView(head);

        // Rows.
        for (int i = 0; i < theme.teams.length; i++) {
            boolean hl = i == theme.highlightRow;
            int fg = hl ? Color.WHITE : Color.WHITE;
            int sub = hl ? Color.WHITE : FAINT;
            int posColor = hl ? Color.WHITE : GREY;
            LinearLayout r = row();
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(26));
            rLp.topMargin = dp(2);
            r.setLayoutParams(rLp);
            r.setPadding(dp(10), 0, dp(10), 0);
            if (hl) {
                GradientDrawable band = new GradientDrawable();
                band.setColor(theme.accent); // the club colour, sampled from its crest
                band.setCornerRadius(dp(9));
                r.setBackground(band);
            }
            r.addView(cell(text(14, posColor, mono, theme.positions[i], 0f), 26));
            r.addView(cell(text(15, fg, geist, theme.teams[i], 0.05f), 0));
            r.addView(cell(text(14, sub, mono, theme.played[i], 0f), 36, Gravity.CENTER));
            r.addView(cell(text(14, sub, mono, theme.points[i], 0f), 46, Gravity.END));
            addView(r);
        }
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView cell(TextView t, int widthDp) {
        return cell(t, widthDp, Gravity.START);
    }

    private TextView cell(TextView t, int widthDp, int gravity) {
        t.setGravity(gravity);
        if (widthDp > 0) {
            t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LayoutParams.WRAP_CONTENT));
        } else {
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
