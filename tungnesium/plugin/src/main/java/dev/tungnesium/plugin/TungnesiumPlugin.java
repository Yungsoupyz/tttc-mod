package dev.tungnesium.plugin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;

public final class TungnesiumPlugin extends JavaPlugin implements Listener, PluginMessageListener, TabExecutor {

    private static final String NS = "tungnesium:";
    // server -> client
    private static final String CH_REQ_MODS = NS + "request_modlist";
    private static final String CH_LOADING = NS + "loading";
    private static final String CH_CLEAR = NS + "clear_loading";
    private static final String CH_STANDINGS = NS + "standings";
    private static final String CH_OPEN = NS + "open_events";
    private static final String CH_UPDATE = NS + "update_available";
    // client -> server
    private static final String CH_MODLIST = NS + "modlist";
    private static final String CH_REQ_STANDINGS = NS + "request_standings";

    private static final class Pending {
        final String nonce = UUID.randomUUID().toString();
        final long since = System.currentTimeMillis();
        boolean resent = false;
    }

    private record Report(String version, String jarHash, List<String> mods, List<String> packs, long time) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Report> reports = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String c : List.of(CH_REQ_MODS, CH_LOADING, CH_CLEAR, CH_STANDINGS, CH_OPEN, CH_UPDATE)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, c);
        }
        for (String c : List.of(CH_MODLIST, CH_REQ_STANDINGS)) {
            getServer().getMessenger().registerIncomingPluginChannel(this, c, this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("tungnesium").setExecutor(this);
        getCommand("tungnesium").setTabCompleter(this);
        getCommand("events").setExecutor(this);

        // handshake watchdog, once per second
        Bukkit.getScheduler().runTaskTimer(this, this::watchdog, 20L, 20L);
        // standings push
        long every = Math.max(5, getConfig().getInt("standings.push-interval-seconds", 15)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::pushStandingsToVerified, every, every);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    // ------------------------------------------------------------------ helpers

    private String msg(String path, Map<String, String> vars) {
        String s = getConfig().getString(path, "");
        s = s.replace("{discord}", getConfig().getString("discord", ""));
        for (var e : vars.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
        return s;
    }

    private void send(Player p, String channel, byte[] data) {
        p.sendPluginMessage(this, channel, data);
    }

    private static byte[] bytes(String... strings) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (String s : strings) Wire.str(o, s);
        return o.toByteArray();
    }

    private void kick(Player p, String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) p.kick(Component.text(message));
        });
    }

    // ------------------------------------------------------------------ handshake

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("tungnesium.bypass")) return;
        Pending pd = new Pending();
        pending.put(p.getUniqueId(), pd);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && pending.get(p.getUniqueId()) == pd) send(p, CH_REQ_MODS, bytes(pd.nonce));
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        reports.remove(e.getPlayer().getUniqueId());
    }

    private void watchdog() {
        long timeout = getConfig().getLong("join.handshake-timeout-seconds", 20) * 1000L;
        long now = System.currentTimeMillis();
        for (var en : new ArrayList<>(pending.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Pending pd = en.getValue();
            if (p == null || !p.isOnline()) {
                pending.remove(en.getKey());
                continue;
            }
            long age = now - pd.since;
            if (age > timeout) {
                pending.remove(en.getKey());
                p.kick(Component.text(msg("join.not-installed-message", Map.of())));
            } else if (age > timeout / 2 && !pd.resent) {
                pd.resent = true;
                send(p, CH_REQ_MODS, bytes(pd.nonce));
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            if (channel.equals(CH_MODLIST)) handleModList(player, message);
            else if (channel.equals(CH_REQ_STANDINGS)) send(player, CH_STANDINGS, bytes(buildStandings()));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Bad packet from " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void handleModList(Player p, byte[] data) {
        Wire.Reader r = new Wire.Reader(data);
        String nonce = r.str();
        String version = r.str();
        String hash = r.str();
        List<String> mods = r.list();
        List<String> packs = r.list();

        boolean bypass = p.hasPermission("tungnesium.bypass");
        Pending pd = pending.get(p.getUniqueId());
        if (!bypass) {
            if (pd == null || !pd.nonce.equals(nonce)) return; // stale or unsolicited
            pending.remove(p.getUniqueId());
        }
        reports.put(p.getUniqueId(), new Report(version, hash, mods, packs, System.currentTimeMillis()));

        // standings straight away so Tab / scoreboard look right
        send(p, CH_STANDINGS, bytes(buildStandings()));
        if (bypass) return;

        // ---- mod check
        if (getConfig().getBoolean("modcheck.enabled", true)) {
            List<String> bad = findViolations(mods, packs);
            if (!bad.isEmpty()) {
                String reasons = String.join(", ", bad);
                String alert = p.getName() + " failed the mod check: " + reasons;
                getLogger().warning(alert);
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("tungnesium.alerts")) staff.sendMessage(Component.text("[Tungnesium] " + alert));
                }
                if (getConfig().getString("modcheck.action", "kick").equalsIgnoreCase("kick")) {
                    kick(p, msg("modcheck.kick-message", Map.of("reasons", reasons)));
                    return;
                }
            }
        }

        // ---- version check
        String need = getConfig().getString("update.latest-version", "");
        if (!need.isEmpty() && compare(version, need) < 0) {
            String url = getConfig().getString("update.url", "");
            String sha = getConfig().getString("update.sha256", "");
            if (!url.isEmpty() && !sha.isEmpty()) send(p, CH_UPDATE, bytes(need, url, sha));
            if (getConfig().getBoolean("update.enforce-latest", true)) {
                long grace = (url.isEmpty() ? 5 : getConfig().getLong("update.grace-seconds", 90)) * 20L;
                String text = msg("update.outdated-message", Map.of("have", version, "need", need));
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Report cur = reports.get(p.getUniqueId());
                    if (p.isOnline() && cur != null && compare(cur.version(), need) < 0) p.kick(Component.text(text));
                }, grace);
            }
        }
    }

    private List<String> findViolations(List<String> mods, List<String> packs) {
        List<String> bad = new ArrayList<>();
        boolean allowlist = getConfig().getString("modcheck.mode", "denylist").equalsIgnoreCase("allowlist");
        Set<String> denyIds = lower(getConfig().getStringList("modcheck.deny-ids"));
        List<String> denyContains = new ArrayList<>(lower(getConfig().getStringList("modcheck.deny-contains")));
        List<String> denyPacks = new ArrayList<>(lower(getConfig().getStringList("modcheck.deny-packs")));
        List<String> allow = new ArrayList<>(lower(getConfig().getStringList("modcheck.allow-ids")));

        for (String entry : mods) {
            String[] parts = entry.split("\\|", 3);
            String id = parts[0].toLowerCase(Locale.ROOT);
            String name = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";
            if (allowlist) {
                if (!allowed(id, allow)) bad.add("mod " + parts[0]);
                continue;
            }
            boolean hit = denyIds.contains(id);
            for (String c : denyContains) if (id.contains(c) || name.contains(c)) hit = true;
            if (hit) bad.add("mod " + parts[0]);
        }
        for (String entry : packs) {
            String low = entry.toLowerCase(Locale.ROOT);
            for (String c : denyPacks) {
                if (low.contains(c)) {
                    String[] parts = entry.split("\\|", 2);
                    bad.add("pack " + (parts.length > 1 ? parts[1] : parts[0]));
                    break;
                }
            }
        }
        return bad;
    }

    private static Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) s.add(x.toLowerCase(Locale.ROOT));
        return s;
    }

    private static boolean allowed(String id, List<String> allow) {
        for (String a : allow) {
            if (a.endsWith("*") ? id.startsWith(a.substring(0, a.length() - 1)) : id.equals(a)) return true;
        }
        return false;
    }

    private static int compare(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ standings

    private record TeamCfg(String key, String team, String holder, String name, String color, String icon) {}

    private List<TeamCfg> teams() {
        List<TeamCfg> out = new ArrayList<>();
        ConfigurationSection s = getConfig().getConfigurationSection("teams");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            String team = s.getString(k + ".scoreboard-team", k);
            out.add(new TeamCfg(k, team, s.getString(k + ".holder", team), s.getString(k + ".name", k),
                    s.getString(k + ".color", "#999999"), s.getString(k + ".icon", "")));
        }
        return out;
    }

    private static int score(Scoreboard sb, String objective, String holder) {
        if (objective == null || objective.isEmpty()) return 0;
        Objective o = sb.getObjective(objective);
        if (o == null) return 0;
        Score s = o.getScore(holder);
        return s.isScoreSet() ? s.getScore() : 0;
    }

    /** player name -> points for one objective (only real players) */
    private static Map<String, Integer> playerScores(Scoreboard sb, String objective) {
        Map<String, Integer> out = new HashMap<>();
        if (objective == null || objective.isEmpty()) return out;
        Objective o = sb.getObjective(objective);
        if (o == null) return out;
        for (String entry : sb.getEntries()) {
            Score s = o.getScore(entry);
            if (!s.isScoreSet()) continue;
            if (Bukkit.getOfflinePlayerIfCached(entry) == null) continue;
            out.put(entry, s.getScore());
        }
        return out;
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    /**
     * JSON sent to the client:
     * {"event":"..","teams":[{"team","name","color","icon","points"}],
     *  "players":[{"name","team","points"}],
     *  "games":[{"id","name","teams":[{"team","points"}]}]}
     */
    String buildStandings() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        List<TeamCfg> teams = teams();
        ConfigurationSection games = getConfig().getConfigurationSection("games");

        String teamTotal = getConfig().getString("standings.team-total-objective", "");
        String playerTotal = getConfig().getString("standings.player-total-objective", "");

        // per game results
        Map<String, Map<String, Integer>> gameTeamPoints = new LinkedHashMap<>(); // gameId -> teamKey -> pts
        Map<String, Integer> playerSum = new HashMap<>();
        Map<String, String> gameNames = new LinkedHashMap<>();
        if (games != null) {
            for (String id : games.getKeys(false)) {
                gameNames.put(id, games.getString(id + ".name", id));
                String tObj = games.getString(id + ".team-objective", "");
                String pObj = games.getString(id + ".player-objective", "");
                Map<String, Integer> ps = playerScores(sb, pObj);
                for (var e : ps.entrySet()) playerSum.merge(e.getKey(), e.getValue(), Integer::sum);

                Map<String, Integer> tp = new LinkedHashMap<>();
                for (TeamCfg t : teams) {
                    int pts;
                    if (!tObj.isEmpty()) {
                        pts = score(sb, tObj, t.holder());
                    } else {
                        pts = 0;
                        for (var e : ps.entrySet()) {
                            Team pt = sb.getEntryTeam(e.getKey());
                            if (pt != null && pt.getName().equalsIgnoreCase(t.team())) pts += e.getValue();
                        }
                    }
                    tp.put(t.key(), pts);
                }
                gameTeamPoints.put(id, tp);
            }
        }

        StringBuilder j = new StringBuilder("{");
        j.append("\"event\":").append(esc(getConfig().getString("event-name", "Tungnesium"))).append(",");

        j.append("\"teams\":[");
        boolean first = true;
        for (TeamCfg t : teams) {
            int pts;
            if (!teamTotal.isEmpty()) {
                pts = score(sb, teamTotal, t.holder());
            } else {
                pts = 0;
                for (var g : gameTeamPoints.values()) pts += g.getOrDefault(t.key(), 0);
            }
            if (!first) j.append(',');
            first = false;
            j.append("{\"team\":").append(esc(t.team()))
                    .append(",\"name\":").append(esc(t.name()))
                    .append(",\"color\":").append(esc(t.color()))
                    .append(",\"icon\":").append(esc(t.icon()))
                    .append(",\"points\":").append(pts).append('}');
        }
        j.append("],");

        Map<String, Integer> players = !playerTotal.isEmpty() ? playerScores(sb, playerTotal) : playerSum;
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(players.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        j.append("\"players\":[");
        first = true;
        int n = 0;
        for (var e : sorted) {
            if (n++ >= 200) break;
            Team pt = sb.getEntryTeam(e.getKey());
            if (!first) j.append(',');
            first = false;
            j.append("{\"name\":").append(esc(e.getKey()))
                    .append(",\"team\":").append(esc(pt == null ? "" : pt.getName()))
                    .append(",\"points\":").append(e.getValue()).append('}');
        }
        j.append("],");

        j.append("\"games\":[");
        first = true;
        for (var g : gameTeamPoints.entrySet()) {
            if (!first) j.append(',');
            first = false;
            j.append("{\"id\":").append(esc(g.getKey()))
                    .append(",\"name\":").append(esc(gameNames.get(g.getKey())))
                    .append(",\"teams\":[");
            boolean f2 = true;
            for (TeamCfg t : teams) {
                if (!f2) j.append(',');
                f2 = false;
                j.append("{\"team\":").append(esc(t.team())).append(",\"points\":")
                        .append(g.getValue().getOrDefault(t.key(), 0)).append('}');
            }
            j.append("]}");
        }
        j.append("]}");
        return j.toString();
    }

    private void pushStandingsToVerified() {
        if (reports.isEmpty()) return;
        byte[] data = bytes(buildStandings());
        for (UUID id : reports.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) send(p, CH_STANDINGS, data);
        }
    }

    // ------------------------------------------------------------------ commands

    private List<Player> targets(CommandSender sender, String selector) {
        List<Player> out = new ArrayList<>();
        try {
            for (Entity e : Bukkit.selectEntities(sender, selector)) if (e instanceof Player pl) out.add(pl);
        } catch (IllegalArgumentException ex) {
            Player pl = Bukkit.getPlayerExact(selector);
            if (pl != null) out.add(pl);
        }
        return out;
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_\\s-]+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("events")) {
            if (sender instanceof Player p) send(p, CH_OPEN, new byte[0]);
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/tungnesium <reload|loading|clearloading|events|standings|status|mods>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("Tungnesium config reloaded.");
            }
            case "loading" -> {
                // /tungnesium loading <game> <selector> [seconds] [subtitle...]
                if (args.length < 3) {
                    sender.sendMessage("Usage: /tungnesium loading <game> <@a|player> [seconds] [subtitle]");
                    return true;
                }
                String game = args[1].toLowerCase(Locale.ROOT);
                int seconds = 0;
                if (args.length > 3) {
                    try {
                        seconds = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                String subtitle = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : "";
                String title = getConfig().getString("games." + game + ".name", pretty(game));
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                Wire.str(o, game);
                Wire.str(o, title);
                Wire.str(o, subtitle);
                Wire.varInt(o, Math.max(0, seconds));
                byte[] data = o.toByteArray();
                int n = 0;
                for (Player p : targets(sender, args[2])) {
                    send(p, CH_LOADING, data);
                    n++;
                }
                sender.sendMessage("Loading screen sent to " + n + " player(s).");
            }
            case "clearloading" -> {
                String sel = args.length > 1 ? args[1] : "@a";
                int n = 0;
                for (Player p : targets(sender, sel)) {
                    send(p, CH_CLEAR, new byte[0]);
                    n++;
                }
                sender.sendMessage("Cleared loading screen for " + n + " player(s).");
            }
            case "events" -> {
                String sel = args.length > 1 ? args[1] : (sender instanceof Player p ? p.getName() : "@a");
                for (Player p : targets(sender, sel)) send(p, CH_OPEN, new byte[0]);
            }
            case "standings" -> {
                pushStandingsToVerified();
                sender.sendMessage("Standings pushed to " + reports.size() + " player(s).");
            }
            case "status" -> {
                sender.sendMessage("Verified: " + reports.size() + " | waiting for handshake: " + pending.size());
                for (var e : reports.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) sender.sendMessage(" - " + p.getName() + "  mod " + e.getValue().version());
                }
            }
            case "mods" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /tungnesium mods <player> [all]");
                    return true;
                }
                Player p = Bukkit.getPlayerExact(args[1]);
                Report r = p == null ? null : reports.get(p.getUniqueId());
                if (r == null) {
                    sender.sendMessage("No mod report for that player (offline, bypass, or not answered yet).");
                    return true;
                }
                boolean all = args.length > 2 && args[2].equalsIgnoreCase("all");
                sender.sendMessage("Tungnesium " + r.version() + " | jar sha256 " + (r.jarHash().isEmpty() ? "n/a" : r.jarHash().substring(0, 12) + "..."));
                int shown = 0;
                for (String m : r.mods()) {
                    String[] pt = m.split("\\|", 3);
                    if (!all && (pt[0].startsWith("fabric-") || pt[0].equals("java") || pt[0].equals("minecraft"))) continue;
                    if (shown++ < 60) sender.sendMessage(" mod " + pt[0] + " " + (pt.length > 1 ? pt[1] : ""));
                }
                for (String pk : r.packs()) sender.sendMessage(" pack " + pk.replace('|', ' '));
                if (shown > 60) sender.sendMessage(" ... and " + (shown - 60) + " more");
            }
            default -> sender.sendMessage("Unknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("tungnesium")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("reload", "loading", "clearloading", "events", "standings", "status", "mods"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("loading")) {
            ConfigurationSection g = getConfig().getConfigurationSection("games");
            if (g != null) out.addAll(g.getKeys(false));
        } else if (args.length == 2 || args.length == 3) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            out.add("@a");
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
