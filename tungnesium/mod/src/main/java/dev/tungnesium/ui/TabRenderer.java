package dev.tungnesium.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tungnesium.StandingsData;
import dev.tungnesium.Teams;
import dev.tungnesium.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Replaces the vanilla Tab list with team cards. */
public final class TabRenderer {
    private TabRenderer() {}

    private static final int COL_W = 150;
    private static final int GAP = 4;
    private static final int ROW_H = 10;
    private static final int CARD_HEAD = 14;

    private record Card(String key, List<PlayerListEntry> players) {
        int height() { return CARD_HEAD + players.size() * ROW_H + 4; }
    }

    public static void render(DrawContext ctx, int sw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        PlayerListHud hud = mc.inGameHud.getPlayerListHud();
        int sh = ctx.getScaledWindowHeight();

        // Group by scoreboard team. Many event servers hide players from the vanilla list ("unlisted"),
        // so we also build the teams from scoreboard membership instead of trusting the listed flag.
        Map<String, List<PlayerListEntry>> groups = new HashMap<>();
        Set<PlayerListEntry> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int count = 0;
        if (mc.world != null) {
            for (Team t : mc.world.getScoreboard().getTeams()) {
                for (String member : t.getPlayerList()) {
                    PlayerListEntry e = handler.getPlayerListEntry(member);
                    if (e != null && seen.add(e)) {
                        groups.computeIfAbsent(t.getName(), k -> new ArrayList<>()).add(e);
                        count++;
                    }
                }
            }
        }
        for (PlayerListEntry e : handler.getListedPlayerListEntries()) {
            if (!seen.add(e)) continue;
            Team t = e.getScoreboardTeam();
            groups.computeIfAbsent(t == null ? "" : t.getName(), k -> new ArrayList<>()).add(e);
            count++;
        }
        List<Card> cards = new ArrayList<>();
        for (var en : groups.entrySet()) {
            List<PlayerListEntry> l = en.getValue();
            l.sort(Comparator.comparing((PlayerListEntry p) -> hud.getPlayerName(p).getString().toLowerCase()));
            cards.add(new Card(en.getKey(), l));
        }
        cards.sort(Comparator.comparingInt((Card c) -> Teams.info(c.key()).order()).thenComparing(Card::key));

        // choose number of columns so it fits the screen
        int maxCols = Math.max(1, (sw - 20) / (COL_W + GAP));
        int avail = sh - 60;
        int cols = 1;
        List<List<Card>> layout = null;
        for (cols = Math.min(2, maxCols); cols <= maxCols; cols++) {
            layout = distribute(cards, cols);
            if (tallest(layout) <= avail) break;
        }
        if (cols > maxCols) {
            cols = maxCols;
            layout = distribute(cards, cols);
        }

        int totalW = cols * (COL_W + GAP) - GAP;
        int x0 = (sw - totalW) / 2;
        int y0 = 8;

        // header
        Theme.panel(ctx, x0, y0, totalW, 20);
        ctx.fill(x0, y0, x0 + totalW, y0 + 20, Theme.HEADER);
        ctx.fill(x0, y0 + 19, x0 + totalW, y0 + 20, Theme.ACCENT);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(StandingsData.eventName.toUpperCase()).formatted(Formatting.BOLD),
                sw / 2, y0 + 6, Theme.GOLD);

        String self = mc.player.getName().getString();
        int maxY = y0 + 24;
        for (int ci = 0; ci < layout.size(); ci++) {
            int x = x0 + ci * (COL_W + GAP);
            int y = y0 + 24;
            for (Card card : layout.get(ci)) {
                drawCard(ctx, tr, hud, card, x, y, self);
                y += card.height() + GAP;
            }
            maxY = Math.max(maxY, y);
        }

        String foot = count + " online";
        Theme.panel(ctx, x0, maxY, totalW, 14);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(foot), sw / 2, maxY + 3, Theme.MUTED);
    }

    private static List<List<Card>> distribute(List<Card> cards, int cols) {
        List<List<Card>> out = new ArrayList<>();
        int[] h = new int[cols];
        for (int i = 0; i < cols; i++) out.add(new ArrayList<>());
        for (Card c : cards) {
            int best = 0;
            for (int i = 1; i < cols; i++) if (h[i] < h[best]) best = i;
            out.get(best).add(c);
            h[best] += c.height() + GAP;
        }
        return out;
    }

    private static int tallest(List<List<Card>> layout) {
        int m = 0;
        for (List<Card> col : layout) {
            int h = 0;
            for (Card c : col) h += c.height() + GAP;
            m = Math.max(m, h);
        }
        return m;
    }

    private static void drawCard(DrawContext ctx, TextRenderer tr, PlayerListHud hud, Card card, int x, int y, String self) {
        Teams.Info info = Teams.info(card.key());
        int h = card.height();
        Theme.panel(ctx, x, y, COL_W, h);
        ctx.fill(x, y, x + 3, y + h, info.color());
        ctx.fill(x + 3, y, x + COL_W, y + CARD_HEAD - 1, Theme.PANEL_LIGHT);

        String title = (info.icon().isEmpty() ? "" : info.icon() + " ") + info.name();
        ctx.drawTextWithShadow(tr, Text.literal(title).formatted(Formatting.BOLD), x + 8, y + 3, info.color());
        Integer pts = StandingsData.pointsOf(card.key());
        if (pts != null) {
            String s = String.format("%,d", pts);
            ctx.drawTextWithShadow(tr, s, x + COL_W - 5 - tr.getWidth(s), y + 3, Theme.GOLD);
        }

        int ry = y + CARD_HEAD;
        for (PlayerListEntry e : card.players()) {
            PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), x + 8, ry, 8);
            String name = hud.getPlayerName(e).getString();
            name = tr.trimToWidth(name, COL_W - 24);
            boolean me = name.equals(self);
            ctx.drawTextWithShadow(tr, name, x + 20, ry, me ? Theme.GOLD : Theme.TEXT);
            ry += ROW_H;
        }
    }
}
