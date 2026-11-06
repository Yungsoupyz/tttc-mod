package dev.tungnesium;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Latest standings sent by the server (see plugin StandingsBuilder for the JSON format). */
public final class StandingsData {
    private StandingsData() {}

    public record TeamRow(String team, int points) {}
    public record PlayerRow(String name, String team, int points) {}
    public record Game(String id, String name, List<TeamRow> teams) {}

    public static volatile boolean loaded = false;
    public static volatile String eventName = "Tungnesium";
    public static volatile List<TeamRow> teams = List.of();
    public static volatile List<PlayerRow> players = List.of();
    public static volatile List<Game> games = List.of();
    private static volatile Map<String, Integer> teamPoints = Map.of();

    public static void clear() {
        loaded = false;
        teams = List.of();
        players = List.of();
        games = List.of();
        teamPoints = Map.of();
    }

    public static Integer pointsOf(String team) {
        return team == null ? null : teamPoints.get(team.toLowerCase(Locale.ROOT));
    }

    private static String str(JsonObject o, String k, String d) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? d : e.getAsString();
    }

    private static int num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    private static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFF9A8A78;
        }
    }

    private static List<TeamRow> teamRows(JsonArray a) {
        List<TeamRow> l = new ArrayList<>();
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            l.add(new TeamRow(str(o, "team", ""), num(o, "points")));
        }
        l.sort(Comparator.comparingInt(TeamRow::points).reversed());
        return l;
    }

    public static void parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            eventName = str(root, "event", "Tungnesium");

            // team look and names come from the server config
            List<Teams.Info> infos = new ArrayList<>();
            JsonArray ta = root.getAsJsonArray("teams");
            int order = 0;
            for (JsonElement e : ta) {
                JsonObject o = e.getAsJsonObject();
                infos.add(new Teams.Info(str(o, "team", ""), str(o, "name", ""), color(str(o, "color", "#999999")),
                        str(o, "icon", ""), order++));
            }
            Teams.configure(infos);

            List<TeamRow> trows = teamRows(ta);
            Map<String, Integer> tp = new HashMap<>();
            for (TeamRow r : trows) tp.put(r.team().toLowerCase(Locale.ROOT), r.points());

            List<PlayerRow> prows = new ArrayList<>();
            if (root.has("players")) {
                for (JsonElement e : root.getAsJsonArray("players")) {
                    JsonObject o = e.getAsJsonObject();
                    prows.add(new PlayerRow(str(o, "name", "?"), str(o, "team", ""), num(o, "points")));
                }
            }
            prows.sort(Comparator.comparingInt(PlayerRow::points).reversed());

            List<Game> grows = new ArrayList<>();
            if (root.has("games")) {
                for (JsonElement e : root.getAsJsonArray("games")) {
                    JsonObject o = e.getAsJsonObject();
                    grows.add(new Game(str(o, "id", ""), str(o, "name", "Game"), teamRows(o.getAsJsonArray("teams"))));
                }
            }

            teams = trows;
            players = prows;
            games = grows;
            teamPoints = tp;
            loaded = true;
        } catch (Exception e) {
            TungnesiumClient.LOG.warn("Bad standings payload", e);
        }
    }
}
