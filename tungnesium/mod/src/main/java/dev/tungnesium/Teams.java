package dev.tungnesium;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.AbstractTeam;

/**
 * Team look-up. The server (plugin config.yml) sends the real names, colors and icons, so teams can be
 * renamed without touching the mod. Before the server has sent anything we fall back to guessing the color
 * from words in the scoreboard team name (red, orange, yellow, ...).
 */
public final class Teams {
    private Teams() {}

    public record Info(String key, String name, int color, String icon, int order) {}

    private static final String[] WORDS = {"red", "orange", "yellow", "lime", "green", "cyan", "aqua", "blue", "purple", "pink"};
    private static final int[] COLORS = {
            0xFFE5484D, 0xFFF08A24, 0xFFF5D90A, 0xFFA3E635, 0xFF2FA84F,
            0xFF22C5D8, 0xFF5EEAD4, 0xFF3B82F6, 0xFFA855F7, 0xFFF472B6};
    private static final int NO_TEAM_COLOR = 0xFF9A8A78;

    private static volatile Map<String, Info> configured = Map.of();

    public static void configure(List<Info> list) {
        Map<String, Info> m = new HashMap<>();
        for (Info i : list) m.put(i.key().toLowerCase(Locale.ROOT), i);
        configured = m;
    }

    private static int wordIndex(String raw) {
        for (String tok : raw.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            for (int i = 0; i < WORDS.length; i++) if (WORDS[i].equals(tok)) return i;
        }
        return -1;
    }

    /** Never null. Empty/null raw means "no team". */
    public static Info info(String raw) {
        if (raw == null || raw.isEmpty()) return new Info("", "No Team", NO_TEAM_COLOR, "", 9999);
        Info i = configured.get(raw.toLowerCase(Locale.ROOT));
        if (i != null) return i;
        int w = wordIndex(raw);
        return new Info(raw, pretty(raw), w < 0 ? NO_TEAM_COLOR : COLORS[w], "", w < 0 ? 5000 : 100 + w);
    }

    public static String pretty(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split("[_\\s-]+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String ownTeamName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        AbstractTeam t = mc.player.getScoreboardTeam();
        return t == null ? null : t.getName();
    }

    public static boolean same(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
