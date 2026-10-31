package com.gathin.tvhome.livefeed;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Fetches fixtures, results and standings for the launcher's club themes
 * from ESPN's free public JSON API (no account, no API key) and publishes
 * one livedata.json that the launcher overlays on its baked snapshot.
 *
 * Route per league (all verified against the live API on 2026-10-31):
 * standings from /apis/v2/.../standings?season=YYYY (guarded: if ESPN has
 * not opened that season, the league's themes are skipped — Indonesian
 * Super League 2026-27 was not on ESPN yet, so Persebaya keeps its
 * snapshot for now); past results from the team schedule endpoint (league
 * matches only, full season); future fixtures from single-date scoreboard
 * calls over the league calendar (ESPN rejects date-range queries).
 *
 * Card strings are formatted exactly like the launcher's baked snapshots
 * (dates and kick-offs in WIB) so live and snapshot data look identical.
 */
final class FeedUpdater {

    static final String DATA_FILE = "livedata.json";
    static final String STATUS_FILE = "status.json";

    private static final TimeZone WIB = TimeZone.getTimeZone("Asia/Jakarta");
    private static final String API = "https://site.api.espn.com";

    private static final class Club {
        final String themeId, league, leagueName, teamId, chant;

        Club(String themeId, String league, String leagueName, String teamId, String chant) {
            this.themeId = themeId;
            this.league = league;
            this.leagueName = leagueName;
            this.teamId = teamId;
            this.chant = chant;
        }
    }

    // In the launcher's theme order.
    private static final Club[] CLUBS = {
            new Club("persebaya", "idn.1", "SUPER LEAGUE", "8016", "Wani!"),
            new Club("como", "ita.1", "SERIE A", "2572", "Forza Como!"),
            new Club("manutd", "eng.1", "PREMIER LEAGUE", "360", "Come on United!"),
            new Club("arsenal", "eng.1", "PREMIER LEAGUE", "359", "COYG!"),
            new Club("liverpool", "eng.1", "PREMIER LEAGUE", "364", "YNWA!"),
            new Club("barcelona", "esp.1", "LA LIGA", "83", "Visca Barça!"),
    };

    /** Card names: ESPN display name -> standings/hero (upper) and row forms. */
    private static final String[][] ALIASES = {
            {"Manchester United", "MAN UTD", "Man Utd"},
            {"Manchester City", "MANCHESTER CITY", "Man City"},
            {"Brighton & Hove Albion", "BRIGHTON", "Brighton"},
            {"Newcastle United", "NEWCASTLE UTD", "Newcastle"},
            {"Tottenham Hotspur", "TOTTENHAM", "Tottenham"},
            {"Wolverhampton Wanderers", "WOLVES", "Wolves"},
            {"West Ham United", "WEST HAM", "West Ham"},
            {"AFC Bournemouth", "BOURNEMOUTH", "Bournemouth"},
            {"Atlético Madrid", "ATLETICO MADRID", "Atletico Madrid"},
            {"Atletico Madrid", "ATLETICO MADRID", "Atletico Madrid"},
            {"Deportivo Alavés", "ALAVES", "Alavés"},
            {"Alavés", "ALAVES", "Alavés"},
            {"Internazionale", "INTER", "Inter"},
            {"AC Milan", "MILAN", "Milan"},
            {"Persebaya Surabaya", "PERSEBAYA", "Persebaya"},
    };

    private static String upperName(String espnName) {
        for (String[] a : ALIASES) if (a[0].equals(espnName)) return a[1];
        return espnName.replace(" FC", "").replace(" AFC", "").toUpperCase(Locale.US);
    }

    private static String rowName(String espnName) {
        for (String[] a : ALIASES) if (a[0].equals(espnName)) return a[2];
        return espnName;
    }

    private static final class Ev {
        String id;
        long epoch;
        String state = "";
        String venue = "";
        String homeId = "", awayId = "", homeName = "", awayName = "";
        String homeScore = "", awayScore = "";
    }

    private static final class Row {
        String id, name, played, points;
        int rank;
    }

    /** Runs one full refresh; writes livedata.json + status.json. Blocking. */
    static JSONObject update(Context ctx) {
        JSONObject status = new JSONObject();
        JSONObject themes = new JSONObject();
        try {
            status.put("updatedAt", System.currentTimeMillis());
            int seasonYear = seasonYear();
            Map<String, List<Club>> byLeague = new LinkedHashMap<>();
            for (Club c : CLUBS) {
                byLeague.computeIfAbsent(c.league, k -> new ArrayList<>()).add(c);
            }
            for (Map.Entry<String, List<Club>> e : byLeague.entrySet()) {
                updateLeague(e.getKey(), e.getValue(), seasonYear, themes, status);
            }
            JSONObject root = new JSONObject();
            root.put("generatedAt", System.currentTimeMillis());
            root.put("themes", themes);
            writeAtomic(new File(ctx.getFilesDir(), DATA_FILE), root.toString());
        } catch (Exception ex) {
            putStatus(status, "_all", "error: " + ex);
        }
        try {
            writeAtomic(new File(ctx.getFilesDir(), STATUS_FILE), status.toString());
        } catch (Exception ignored) {
        }
        return status;
    }

    private static void updateLeague(String league, List<Club> clubs, int seasonYear,
                                     JSONObject themes, JSONObject status) {
        // Standings first: the season guard lives here (ESPN may not have
        // opened this season for the league yet).
        List<Row> table;
        try {
            JSONObject st = getJson(API + "/apis/v2/sports/soccer/" + league
                    + "/standings?season=" + seasonYear);
            JSONObject season = st.optJSONObject("season");
            int apiYear = season == null ? -1 : season.optInt("year", -1);
            JSONArray children = st.optJSONArray("children");
            JSONArray entries = (children != null && children.length() > 0)
                    ? children.getJSONObject(0).getJSONObject("standings").getJSONArray("entries")
                    : null;
            if (apiYear != seasonYear || entries == null || entries.length() == 0) {
                for (Club c : clubs) {
                    putStatus(status, c.themeId, "skipped: ESPN has no "
                            + seasonYear + "-" + (seasonYear + 1) + " data for this league yet");
                }
                return;
            }
            table = parseTable(entries);
        } catch (Exception ex) {
            for (Club c : clubs) putStatus(status, c.themeId, "error: standings " + ex);
            return;
        }

        // Future fixtures for the whole league in one sweep of date calls.
        Map<String, Ev> future = new HashMap<>();
        try {
            collectFuture(league, clubs, future);
        } catch (Exception ignored) {
            // Hero fields fall back to the snapshot; the rest still updates.
        }

        for (Club club : clubs) {
            try {
                JSONObject t = buildTheme(club, table, future);
                if (t != null) {
                    themes.put(club.themeId, t);
                    putStatus(status, club.themeId, "ok");
                } else {
                    putStatus(status, club.themeId, "skipped: no data");
                }
            } catch (Exception ex) {
                putStatus(status, club.themeId, "error: " + ex);
            }
        }
    }

    private static JSONObject buildTheme(Club club, List<Row> table, Map<String, Ev> future)
            throws Exception {
        Row me = null;
        for (Row r : table) if (r.id.equals(club.teamId)) me = r;
        if (me == null) return null;

        JSONObject t = new JSONObject();

        // Standings window: top 8, or 1-7 plus the club's own row when the
        // club sits lower (user's 1-7-then-16 format for Manchester United).
        List<Row> win = new ArrayList<>();
        List<String> pos = new ArrayList<>();
        if (me.rank <= 8) {
            for (int i = 0; i < Math.min(8, table.size()); i++) {
                win.add(table.get(i));
                pos.add(String.valueOf(i + 1));
            }
        } else {
            for (int i = 0; i < Math.min(7, table.size()); i++) {
                win.add(table.get(i));
                pos.add(String.valueOf(i + 1));
            }
            win.add(me);
            pos.add(String.valueOf(me.rank));
        }
        JSONArray teams = new JSONArray(), played = new JSONArray(),
                points = new JSONArray(), positions = new JSONArray();
        int highlight = -1;
        for (int i = 0; i < win.size(); i++) {
            Row r = win.get(i);
            teams.put(upperName(r.name));
            played.put(r.played);
            points.put(r.points);
            positions.put(pos.get(i));
            if (r.id.equals(club.teamId)) highlight = i;
        }
        t.put("standingsTitle", "STANDINGS · " + club.leagueName);
        t.put("teams", teams);
        t.put("played", played);
        t.put("points", points);
        t.put("positions", positions);
        t.put("highlightRow", highlight);

        // Past results from the team schedule endpoint (league matches only).
        JSONObject sched = getJson(API + "/apis/site/v2/sports/soccer/" + club.league
                + "/teams/" + club.teamId + "/schedule?season=" + seasonYear());
        List<Ev> past = new ArrayList<>();
        JSONArray events = sched.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                Ev ev = parseEvent(events.getJSONObject(i));
                if (ev != null && "post".equals(ev.state)) past.add(ev);
            }
        }
        Collections.sort(past, Comparator.comparingLong(e -> e.epoch));
        if (!past.isEmpty()) {
            StringBuilder form = new StringBuilder();
            List<Ev> last5 = past.subList(Math.max(0, past.size() - 5), past.size());
            for (Ev ev : last5) {
                boolean home = ev.homeId.equals(club.teamId);
                int us = parseScore(home ? ev.homeScore : ev.awayScore);
                int them = parseScore(home ? ev.awayScore : ev.homeScore);
                if (us < 0 || them < 0) continue;
                form.append(us > them ? 'W' : us == them ? 'D' : 'L');
            }
            if (form.length() == 5) t.put("form", form.toString());
            Ev last = past.get(past.size() - 1);
            t.put("lastResult", "Last: " + rowName(last.homeName) + " " + last.homeScore
                    + "–" + last.awayScore + " " + rowName(last.awayName));
        }

        // Next fixture + the four after it, from the scoreboard sweep.
        List<Ev> mine = new ArrayList<>();
        for (Ev ev : future.values()) {
            if (ev.homeId.equals(club.teamId) || ev.awayId.equals(club.teamId)) mine.add(ev);
        }
        Collections.sort(mine, Comparator.comparingLong(e -> e.epoch));
        if (!mine.isEmpty()) {
            Ev hero = mine.get(0);
            boolean home = hero.homeId.equals(club.teamId);
            t.put("kicker", club.leagueName + " · MATCHDAY " + (parseScore(me.played) + 1));
            t.put("venueTag", home ? "HOME" : "AWAY");
            t.put("nextMatch", upperName(hero.homeName) + " vs " + upperName(hero.awayName));
            t.put("nextWhen", fmtDay(hero.epoch) + " · " + fmtTime(hero.epoch)
                    + " WIB · " + hero.venue);
            t.put("kickoffMillis", hero.epoch);
            t.put("kickoffDone", "Already kicked off · " + club.chant);
            JSONArray up = new JSONArray();
            for (int i = 1; i < mine.size() && i <= 4; i++) {
                Ev e = mine.get(i);
                JSONArray row = new JSONArray();
                row.put(rowName(e.homeName) + " vs " + rowName(e.awayName));
                row.put(e.venue);
                row.put(fmtDay(e.epoch));
                up.put(row);
            }
            if (up.length() == 4) t.put("upNext", up);
        }
        return t;
    }

    /** Collects this league's upcoming (and in-play) club events, ~45 days out. */
    private static void collectFuture(String league, List<Club> clubs, Map<String, Ev> out)
            throws Exception {
        List<String> ids = new ArrayList<>();
        for (Club c : clubs) ids.add(c.teamId);
        Calendar now = Calendar.getInstance(WIB);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(now.getTime());
        JSONObject first = getJson(API + "/apis/site/v2/sports/soccer/" + league
                + "/scoreboard?dates=" + today);
        addClubEvents(first, ids, out);
        JSONArray leagues = first.optJSONArray("leagues");
        if (leagues == null || leagues.length() == 0) return;
        JSONArray calendar = leagues.getJSONObject(0).optJSONArray("calendar");
        if (calendar == null) return;
        Calendar hi = (Calendar) now.clone();
        hi.add(Calendar.DAY_OF_YEAR, 45);
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        dayFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < calendar.length(); i++) {
            String iso = calendar.optString(i, "");
            if (iso.length() < 10) continue;
            String day = iso.substring(0, 10).replace("-", "");
            if (day.compareTo(today) <= 0) continue;
            Calendar d = Calendar.getInstance(WIB);
            d.setTime(dayFmt.parse(iso.substring(0, 10)));
            if (d.after(hi)) continue;
            JSONObject sb = getJson(API + "/apis/site/v2/sports/soccer/" + league
                    + "/scoreboard?dates=" + day);
            addClubEvents(sb, ids, out);
        }
    }

    private static void addClubEvents(JSONObject scoreboard, List<String> teamIds,
                                      Map<String, Ev> out) {
        JSONArray events = scoreboard.optJSONArray("events");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            try {
                Ev ev = parseEvent(events.getJSONObject(i));
                if (ev == null) continue;
                if (!teamIds.contains(ev.homeId) && !teamIds.contains(ev.awayId)) continue;
                if ("post".equals(ev.state)) continue; // schedule endpoint owns results
                out.put(ev.id, ev);
            } catch (Exception ignored) {
            }
        }
    }

    private static Ev parseEvent(JSONObject e) throws Exception {
        JSONArray comps = e.optJSONArray("competitions");
        if (comps == null || comps.length() == 0) return null;
        JSONObject c = comps.getJSONObject(0);
        JSONArray cs = c.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        Ev ev = new Ev();
        ev.id = e.optString("id");
        ev.epoch = parseIso(e.optString("date"));
        JSONObject status = c.optJSONObject("status");
        if (status != null) {
            JSONObject type = status.optJSONObject("type");
            if (type != null) ev.state = type.optString("state", "");
        }
        JSONObject venue = c.optJSONObject("venue");
        if (venue != null) ev.venue = venue.optString("fullName", "");
        for (int i = 0; i < cs.length(); i++) {
            JSONObject comp = cs.getJSONObject(i);
            JSONObject team = comp.optJSONObject("team");
            if (team == null) continue;
            String id = team.optString("id");
            String name = team.optString("displayName");
            String score = scoreOf(comp);
            if ("home".equals(comp.optString("homeAway"))) {
                ev.homeId = id;
                ev.homeName = name;
                ev.homeScore = score;
            } else {
                ev.awayId = id;
                ev.awayName = name;
                ev.awayScore = score;
            }
        }
        return ev;
    }

    /** ESPN writes scores as an object on some endpoints, a string on others. */
    private static String scoreOf(JSONObject competitor) {
        Object sc = competitor.opt("score");
        if (sc == null || sc == JSONObject.NULL) return "";
        if (sc instanceof JSONObject) return ((JSONObject) sc).optString("displayValue", "");
        return String.valueOf(sc);
    }

    private static List<Row> parseTable(JSONArray entries) throws Exception {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.getJSONObject(i);
            JSONObject team = e.getJSONObject("team");
            Row r = new Row();
            r.id = team.optString("id");
            r.name = team.optString("displayName");
            r.rank = Integer.MAX_VALUE;
            JSONArray stats = e.optJSONArray("stats");
            if (stats != null) {
                for (int j = 0; j < stats.length(); j++) {
                    JSONObject s = stats.getJSONObject(j);
                    String v = s.optString("displayValue", "");
                    switch (s.optString("name")) {
                        case "rank":
                            r.rank = parseScore(v);
                            break;
                        case "gamesPlayed":
                            r.played = v;
                            break;
                        case "points":
                            r.points = v;
                            break;
                        default:
                            break;
                    }
                }
            }
            rows.add(r);
        }
        Collections.sort(rows, Comparator.comparingInt(r -> r.rank));
        return rows;
    }

    private static int parseScore(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static long parseIso(String iso) throws Exception {
        // ESPN: 2026-11-01T16:30Z (seconds appear on some endpoints).
        String pat = iso.length() > 17 && iso.charAt(16) == ':'
                ? "yyyy-MM-dd'T'HH:mm:ss'Z'" : "yyyy-MM-dd'T'HH:mm'Z'";
        SimpleDateFormat f = new SimpleDateFormat(pat, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.parse(iso).getTime();
    }

    private static String fmtDay(long epoch) {
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM", Locale.US);
        f.setTimeZone(WIB);
        return f.format(epoch);
    }

    private static String fmtTime(long epoch) {
        SimpleDateFormat f = new SimpleDateFormat("h:mm a", Locale.US);
        f.setTimeZone(WIB);
        return f.format(epoch);
    }

    private static int seasonYear() {
        Calendar wib = Calendar.getInstance(WIB);
        int y = wib.get(Calendar.YEAR);
        if (wib.get(Calendar.MONTH) < Calendar.JULY) y--;
        return y;
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; LiveFeed/1.0)");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code + " for " + url);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = in.read(buf)) != -1) sb.append(buf, 0, n);
            }
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private static void writeAtomic(File target, String content) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(target)) throw new Exception("rename failed for " + target);
    }

    private static void putStatus(JSONObject status, String key, String value) {
        try {
            status.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
