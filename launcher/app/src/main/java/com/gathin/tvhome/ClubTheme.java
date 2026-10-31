package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * The club themes Rheza switches between (his decision, 2026-10-31: ONE app
 * with switchable themes — "lebih simple" — instead of a separate APK per
 * theme; the switch lives in the Settings long-press menu, Home setup and the
 * Home key helper are untouched). Each theme carries its football card data
 * (a dated snapshot — the launcher never goes online, so a separate add-on
 * will refresh it later, see PLAN.md), the accent colours sampled from the
 * club crest, the crest drawable, and the bundled ghost wallpaper.
 *
 * Switching persists the choice and copies the theme's wallpaper over the
 * theme folder's wallpaper.jpg; MainActivity then recreates so the cards and
 * the wallpaper rebuild around the new theme.
 */
final class ClubTheme {

    private static final String PREFS = "club_theme";
    private static final String KEY = "current";

    /** Shared position labels for the themes whose club sits in the top eight. */
    private static final String[] POS_1_TO_8 = {"1", "2", "3", "4", "5", "6", "7", "8"};

    final String id;
    final String displayName;
    // Fixture card.
    final String kicker;
    final String venueTag;
    final String nextMatch;
    final String nextWhen;
    final String lastResult;
    final String[][] upNext;
    final char[] form;
    final int crestRes;
    final long kickoffMillis;
    final String kickoffDone;
    // Standings card.
    final String standingsTitle;
    final String[] teams;
    final String[] played;
    final String[] points;
    final int highlightRow;
    // Position labels for the standings rows, one per row (normally 1-8;
    // Manchester United's card shows 1-7 then 16 — Rheza's pick, 2026-10-31).
    final String[] positions;
    // Accents: band/bar colour and its brighter text variant, both sampled
    // from the crest (Persebaya #0C8544/#10B45C; Como #13446B/#2481CB).
    final int accent;
    final int accentText;
    final String wallpaperAsset;

    private ClubTheme(String id, String displayName,
                      String kicker, String venueTag, String nextMatch, String nextWhen,
                      String lastResult, String[][] upNext, char[] form, int crestRes,
                      long kickoffMillis, String kickoffDone,
                      String standingsTitle, String[] teams, String[] played, String[] points,
                      int highlightRow, String[] positions, int accent, int accentText, String wallpaperAsset) {
        this.id = id;
        this.displayName = displayName;
        this.kicker = kicker;
        this.venueTag = venueTag;
        this.nextMatch = nextMatch;
        this.nextWhen = nextWhen;
        this.lastResult = lastResult;
        this.upNext = upNext;
        this.form = form;
        this.crestRes = crestRes;
        this.kickoffMillis = kickoffMillis;
        this.kickoffDone = kickoffDone;
        this.standingsTitle = standingsTitle;
        this.teams = teams;
        this.played = played;
        this.points = points;
        this.highlightRow = highlightRow;
        this.positions = positions;
        this.accent = accent;
        this.accentText = accentText;
        this.wallpaperAsset = wallpaperAsset;
    }

    /** Kick-off as an absolute instant; the card times are WIB, so the zone is fixed. */
    private static long kickoff(int year, int month0, int day, int hour, int minute) {
        Calendar ko = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        ko.set(year, month0, day, hour, minute, 0);
        ko.set(Calendar.MILLISECOND, 0);
        return ko.getTimeInMillis();
    }

    // Persebaya snapshot of 31 Oct 2026 (Wikipedia Super League template,
    // matches played to 30 Oct 2026). FORM, oldest first: MD4 W 1-0 at
    // Isenmulang (9 Oct), MD5 D 2-2 vs PSS (18 Oct), MD6 L 0-1 at PSM
    // (22 Oct), MD7 W 3-1 vs Madura United (26 Oct), MD8 D 1-1 vs Persijap
    // (30 Oct).
    static final ClubTheme PERSEBAYA = new ClubTheme(
            "persebaya", "Persebaya",
            "SUPER LEAGUE · MATCHDAY 9", "AWAY",
            "PERSIJA vs PERSEBAYA ",
            "Sat 7 Nov · 7:00 PM WIB · Gelora Bung Karno ",
            "Last: Persebaya 1–1 Persijap Jepara",
            new String[][]{
                    {"Persebaya vs Java United", "Gelora Bung Tomo", "Sat 21 Nov"},
                    {"Persebaya vs Arema", "Kanjuruhan", "Sun 29 Nov"},
                    {"Persebaya vs Persib", "Gelora Bung Tomo", "Sun 6 Dec"},
                    {"Persebaya vs Dewa United", "Gelora Bung Tomo", "Wed 9 Dec"},
            },
            new char[]{'W', 'D', 'L', 'W', 'D'},
            R.drawable.persebaya_logo,
            kickoff(2026, Calendar.NOVEMBER, 7, 19, 0),
            "Already kicked off · Wani!",
            "STANDINGS · SUPER LEAGUE",
            new String[]{"PERSIJA", "DEWA UNITED", "BALI UNITED", "PERSIB", "PERSEBAYA",
                    "MADURA UNITED", "PERSITA", "BHAYANGKARA PRESISI"},
            new String[]{"7", "7", "7", "7", "8", "7", "6", "8"},
            new String[]{"18", "14", "13", "13", "13", "13", "12", "11"},
            4, POS_1_TO_8, 0xFF0C8544, 0xFF10B45C,
            "wallpaper_persebaya.jpg");

    // Como 1907 snapshot of 31 Oct 2026 (Serie A after matchday 9: TheStatsAPI
    // table updated 31 Oct 2026; fixtures/results cross-checked on the club
    // site and BetExplorer). Kick-offs are shown in WIB (Italian local times
    // converted, so Inter away reads Mon 9 Nov 00:00 WIB and Juventus home
    // Mon 30 Nov 02:45 WIB). FORM, oldest first (league only): MD5 L 0-2 at
    // Frosinone (20 Sep), MD6 D 0-0 vs Roma (11 Oct), MD7 W 3-1 at Fiorentina
    // (18 Oct), MD8 W 2-0 vs Sassuolo (24 Oct), MD9 W 3-1 at Torino (27 Oct).
    static final ClubTheme COMO = new ClubTheme(
            "como", "Como 1907",
            "SERIE A · MATCHDAY 10", "HOME",
            "COMO vs VENEZIA ",
            "Sun 1 Nov · 6:30 PM WIB · Giuseppe Sinigaglia ",
            "Last: Como 3–1 Torino",
            new String[][]{
                    {"Como vs Inter", "Giuseppe Meazza", "Mon 9 Nov"},
                    {"Como vs Cagliari", "Giuseppe Sinigaglia", "Sat 21 Nov"},
                    {"Como vs Juventus", "Giuseppe Sinigaglia", "Mon 30 Nov"},
                    {"Como vs Monza", "Stadio Brianteo", "Sat 5 Dec"},
            },
            new char[]{'L', 'D', 'W', 'W', 'W'},
            R.drawable.como_logo,
            kickoff(2026, Calendar.NOVEMBER, 1, 18, 30),
            "Already kicked off · Forza Como!",
            "STANDINGS · SERIE A",
            new String[]{"INTER", "LAZIO", "ROMA", "COMO", "MILAN",
                    "FROSINONE", "NAPOLI", "JUVENTUS"},
            new String[]{"9", "9", "9", "9", "9", "9", "9", "9"},
            new String[]{"25", "25", "21", "20", "18", "17", "14", "14"},
            3, POS_1_TO_8, 0xFF13446B, 0xFF2481CB,
            "wallpaper_como.jpg");

    // Manchester United snapshot of 31 Oct 2026 (Premier League after
    // matchday 8: TheStatsAPI table updated 31 Oct 2026; results
    // cross-checked on PLStats and FootballDatabase). United sit 16th, so
    // the standings card shows positions 1-7, then 16 with United on the
    // last row (Rheza's pick, 2026-10-31).
    // FORM, oldest first (league only): MD4 L, MD5 D, MD6 D, MD7 D 1-1 at
    // Fulham (10 Oct), MD8 L 0-3 vs Bournemouth (25 Oct). The Chelsea match
    // kicked off the same evening this snapshot was taken (31 Oct 19:30 WIB).
    static final ClubTheme MANUTD = new ClubTheme(
            "manutd", "Manchester United",
            "PREMIER LEAGUE · MATCHDAY 9", "AWAY",
            "CHELSEA vs MAN UTD ",
            "Sat 31 Oct · 7:30 PM WIB · Stamford Bridge ",
            "Last: Man Utd 0–3 Bournemouth",
            new String[][]{
                    {"Man Utd vs Aston Villa", "Old Trafford", "Sun 8 Nov"},
                    {"Liverpool vs Man Utd", "Anfield", "Sun 22 Nov"},
                    {"Man Utd vs Brentford", "Old Trafford", "Sat 28 Nov"},
                    {"Newcastle vs Man Utd", "St James' Park", "Thu 3 Dec"},
            },
            new char[]{'L', 'D', 'D', 'D', 'L'},
            R.drawable.manutd_logo,
            kickoff(2026, Calendar.OCTOBER, 31, 19, 30),
            "Already kicked off · Come on United!",
            "STANDINGS · PREMIER LEAGUE",
            new String[]{"MANCHESTER CITY", "ARSENAL", "BRIGHTON", "LIVERPOOL",
                    "CHELSEA", "BRENTFORD", "LEEDS UNITED", "MAN UTD"},
            new String[]{"8", "8", "8", "8", "8", "8", "8", "8"},
            new String[]{"21", "21", "16", "15", "13", "11", "11", "7"},
            7, new String[]{"1", "2", "3", "4", "5", "6", "7", "16"}, 0xFFD2100B, 0xFFFCDF00,
            "wallpaper_manutd.jpg");

    // Arsenal snapshot of 31 Oct 2026 (Premier League after matchday 8, same
    // table as above; results cross-checked on FootballDatabase and
    // PLStats). FORM, oldest first (league only): MD4 W at Sunderland
    // (12 Sep), MD5 L 0-3 at Brighton (19 Sep), MD6 W 2-1 vs Leeds (10 Oct),
    // MD7 W 1-0 at Nottingham Forest (18 Oct), MD8 W 2-0 vs Everton (24 Oct).
    static final ClubTheme ARSENAL = new ClubTheme(
            "arsenal", "Arsenal",
            "PREMIER LEAGUE · MATCHDAY 9", "AWAY",
            "LIVERPOOL vs ARSENAL ",
            "Sun 1 Nov · 11:30 PM WIB · Anfield ",
            "Last: Arsenal 2–0 Everton",
            new String[][]{
                    {"Arsenal vs Hull City", "Emirates Stadium", "Sat 7 Nov"},
                    {"Newcastle vs Arsenal", "St James' Park", "Sun 22 Nov"},
                    {"Arsenal vs Man City", "Emirates Stadium", "Sun 29 Nov"},
                    {"Tottenham vs Arsenal", "Tottenham Hotspur Stadium", "Sun 6 Dec"},
            },
            new char[]{'W', 'L', 'W', 'W', 'W'},
            R.drawable.arsenal_logo,
            kickoff(2026, Calendar.NOVEMBER, 1, 23, 30),
            "Already kicked off · COYG!",
            "STANDINGS · PREMIER LEAGUE",
            new String[]{"MANCHESTER CITY", "ARSENAL", "BRIGHTON", "LIVERPOOL",
                    "CHELSEA", "BRENTFORD", "LEEDS UNITED", "NEWCASTLE UTD"},
            new String[]{"8", "8", "8", "8", "8", "8", "8", "8"},
            new String[]{"21", "21", "16", "15", "13", "11", "11", "11"},
            1, POS_1_TO_8, 0xFFD00006, 0xFFFF4043,
            "wallpaper_arsenal.jpg");

    // Liverpool snapshot of 31 Oct 2026 (Premier League after matchday 8,
    // same table). FORM, oldest first (league only): MD4 D 0-0 vs Fulham
    // (12 Sep), MD5 W 1-0 at Bournemouth (20 Sep), MD6 L 0-3 vs Man City
    // (11 Oct), MD7 W 2-1 at Brentford (17 Oct), MD8 W 2-0 vs Brighton
    // (25 Oct).
    static final ClubTheme LIVERPOOL = new ClubTheme(
            "liverpool", "Liverpool",
            "PREMIER LEAGUE · MATCHDAY 9", "HOME",
            "LIVERPOOL vs ARSENAL ",
            "Sun 1 Nov · 11:30 PM WIB · Anfield ",
            "Last: Liverpool 2–0 Brighton",
            new String[][]{
                    {"Crystal Palace vs Liverpool", "Selhurst Park", "Sun 8 Nov"},
                    {"Liverpool vs Man Utd", "Anfield", "Sun 22 Nov"},
                    {"Everton vs Liverpool", "Hill Dickinson Stadium", "Sun 29 Nov"},
                    {"Liverpool vs Sunderland", "Anfield", "Wed 2 Dec"},
            },
            new char[]{'D', 'W', 'L', 'W', 'W'},
            R.drawable.liverpool_logo,
            kickoff(2026, Calendar.NOVEMBER, 1, 23, 30),
            "Already kicked off · YNWA!",
            "STANDINGS · PREMIER LEAGUE",
            new String[]{"MANCHESTER CITY", "ARSENAL", "BRIGHTON", "LIVERPOOL",
                    "CHELSEA", "BRENTFORD", "LEEDS UNITED", "NEWCASTLE UTD"},
            new String[]{"8", "8", "8", "8", "8", "8", "8", "8"},
            new String[]{"21", "21", "16", "15", "13", "11", "11", "11"},
            3, POS_1_TO_8, 0xFFB3101B, 0xFFF04438,
            "wallpaper_liverpool.jpg");

    // Barcelona snapshot of 31 Oct 2026 (La Liga after matchday 10:
    // TheStatsAPI table updated 31 Oct 2026; fixtures cross-checked on the
    // club site). FORM, oldest first (league only): four straight wins, then
    // MD10 L 1-2 vs Real Madrid (25 Oct) — the season's only defeat. The
    // Alavés match kicks off in the small hours of 1 Nov WIB (31 Oct 21:00
    // CET in Barcelona).
    static final ClubTheme BARCELONA = new ClubTheme(
            "barcelona", "Barcelona",
            "LA LIGA · MATCHDAY 11", "HOME",
            "BARCELONA vs ALAVES ",
            "Sun 1 Nov · 3:00 AM WIB · Spotify Camp Nou ",
            "Last: Barcelona 1–2 Real Madrid",
            new String[][]{
                    {"Atletico Madrid vs Barcelona", "Riyadh Air Metropolitano", "Mon 9 Nov"},
                    {"Barcelona vs Villarreal", "Spotify Camp Nou", "Sun 22 Nov"},
                    {"Deportivo vs Barcelona", "ABANCA-Riazor", "Sun 29 Nov"},
                    {"Barcelona vs Celta Vigo", "Spotify Camp Nou", "Sun 6 Dec"},
            },
            new char[]{'W', 'W', 'W', 'W', 'L'},
            R.drawable.barcelona_logo,
            kickoff(2026, Calendar.NOVEMBER, 1, 3, 0),
            "Already kicked off · Visca Barça!",
            "STANDINGS · LA LIGA",
            new String[]{"BARCELONA", "ATLETICO MADRID", "REAL MADRID", "REAL BETIS",
                    "REAL SOCIEDAD", "SEVILLA", "ALAVES", "ATHLETIC CLUB"},
            new String[]{"10", "10", "10", "10", "10", "10", "10", "9"},
            new String[]{"27", "25", "24", "20", "19", "17", "12", "12"},
            0, POS_1_TO_8, 0xFFC30038, 0xFFFF5C78,
            "wallpaper_barcelona.jpg");

    /** All themes in Rheza's order (set 2026-10-31); the Settings row cycles it. */
    static final ClubTheme[] ALL = {PERSEBAYA, COMO, MANUTD, ARSENAL, LIVERPOOL, BARCELONA};

    static ClubTheme current(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY, PERSEBAYA.id);
        ClubTheme base = PERSEBAYA;
        for (ClubTheme t : ALL) {
            if (t.id.equals(id)) {
                base = t;
                break;
            }
        }
        // Live data from the TV Live Feed add-on wins over the baked
        // snapshot when present; any problem falls back to the snapshot.
        return LiveOverlay.apply(ctx, base);
    }

    /**
     * A copy of {@code base} whose card data is replaced by the live-feed
     * add-on's values for the fields it supplies. Identity fields (id, name,
     * crest, accents, wallpaper) always stay with the baked theme.
     */
    static ClubTheme withLive(ClubTheme base, org.json.JSONObject o) {
        try {
            String[][] upNext = base.upNext;
            org.json.JSONArray up = o.optJSONArray("upNext");
            if (up != null && up.length() == base.upNext.length) {
                String[][] rows = new String[up.length()][];
                boolean ok = true;
                for (int i = 0; i < up.length(); i++) {
                    org.json.JSONArray r = up.optJSONArray(i);
                    if (r == null || r.length() < 3) {
                        ok = false;
                        break;
                    }
                    rows[i] = new String[]{r.optString(0), r.optString(1), r.optString(2)};
                }
                if (ok) upNext = rows;
            }
            char[] form = base.form;
            String f = o.optString("form", "");
            if (f.length() == base.form.length) form = f.toCharArray();
            return new ClubTheme(
                    base.id, base.displayName,
                    o.optString("kicker", base.kicker),
                    o.optString("venueTag", base.venueTag),
                    o.optString("nextMatch", base.nextMatch),
                    o.optString("nextWhen", base.nextWhen),
                    o.optString("lastResult", base.lastResult),
                    upNext, form, base.crestRes,
                    o.optLong("kickoffMillis", base.kickoffMillis),
                    o.optString("kickoffDone", base.kickoffDone),
                    o.optString("standingsTitle", base.standingsTitle),
                    strings(o, "teams", base.teams),
                    strings(o, "played", base.played),
                    strings(o, "points", base.points),
                    o.optInt("highlightRow", base.highlightRow),
                    strings(o, "positions", base.positions),
                    base.accent, base.accentText,
                    base.wallpaperAsset);
        } catch (Exception e) {
            return base;
        }
    }

    private static String[] strings(org.json.JSONObject o, String key, String[] fallback) {
        org.json.JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() != fallback.length) return fallback;
        String[] out = new String[a.length()];
        for (int i = 0; i < a.length(); i++) out[i] = a.optString(i, fallback[i]);
        return out;
    }

    /** The next theme in the user's order, wrapping back to Persebaya.
     *  Compare by id, never by reference: ClubTheme.current() may hand back
     *  a live-data copy (withLive), which is not the canonical ALL entry —
     *  a == check silently fell through to PERSEBAYA for every theme with
     *  live data, which read as a Persebaya/Como toggle (bug in v5.7). */
    ClubTheme next() {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(id)) return ALL[(i + 1) % ALL.length];
        }
        return PERSEBAYA;
    }

    /** Persist the choice and put the theme's wallpaper in place. */
    static void activate(Context ctx, ClubTheme theme) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, theme.id).apply();
        File dir = Config.dir(ctx);
        if (dir == null) return;
        try {
            File out = new File(dir, Config.WALLPAPER_NAME);
            try (InputStream in = ctx.getAssets().open(theme.wallpaperAsset);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            }
        } catch (Exception ignored) {
            // The cards and accents still switch; the old wallpaper stays.
        }
    }
}
