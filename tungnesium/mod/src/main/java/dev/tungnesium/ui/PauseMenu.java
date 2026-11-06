package dev.tungnesium.ui;

import java.util.List;

import dev.tungnesium.Net;
import dev.tungnesium.StandingsData;
import dev.tungnesium.Teams;
import dev.tungnesium.Theme;
import dev.tungnesium.TungConfig;
import dev.tungnesium.TungnesiumClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Esc menu: a Standings button, and the event standings down both sides:
 * places 1-5 on the left, 6-10 on the right.
 */
public final class PauseMenu {
    private PauseMenu() {}

    private static final int ROW_H = 13;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof GameMenuScreen) || !TungConfig.pauseTheme) return;
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Standings"),
                    b -> TungnesiumClient.openEvents(client)).dimensions(6, 6, 80, 20).build());
            if (client.player != null) ClientPlayNetworking.send(Net.RequestStandings.INSTANCE);
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(ctx, s.width));
        });
    }

    private static void draw(DrawContext ctx, int width) {
        ctx.fill(0, 0, width, 2, Theme.ACCENT);
        if (!StandingsData.loaded || width < 440) return;

        List<StandingsData.TeamRow> all = StandingsData.teams;
        int n = all.size();
        if (n == 0) return;

        List<StandingsData.TeamRow> left = all.subList(0, Math.min(5, n));
        panel(ctx, 6, 32, left, 1, false, width);
        if (n > 5) {
            List<StandingsData.TeamRow> right = all.subList(5, Math.min(10, n));
            panel(ctx, width - 6, 32, right, 6, true, width);
        }
    }

    /** anchorX is the left edge for the left panel, or the right edge when alignRight is true. */
    private static void panel(DrawContext ctx, int anchorX, int y, List<StandingsData.TeamRow> rows, int firstRank,
                              boolean alignRight, int screenWidth) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int w = 130;
        for (StandingsData.TeamRow r : rows) {
            Teams.Info i = Teams.info(r.team());
            String label = (i.icon().isEmpty() ? "" : i.icon() + " ") + i.name();
            w = Math.max(w, 30 + tr.getWidth(label) + 14 + tr.getWidth(String.format("%,d", r.points())));
        }
        w = Math.min(w, 180);
        int x = alignRight ? anchorX - w : anchorX;
        int h = 16 + rows.size() * ROW_H + 3;

        Theme.panel(ctx, x, y, w, h);
        ctx.fill(x, y, x + w, y + 13, Theme.HEADER);
        String title = firstRank == 1 ? "Standings 1-" + rows.size() : "Standings " + firstRank + "-" + (firstRank + rows.size() - 1);
        ctx.drawTextWithShadow(tr, title, x + 5, y + 3, Theme.GOLD);

        String own = Teams.ownTeamName();
        int ry = y + 16;
        int rank = firstRank;
        for (StandingsData.TeamRow r : rows) {
            Teams.Info i = Teams.info(r.team());
            boolean mine = Teams.same(own, r.team());
            if (mine) ctx.fill(x + 2, ry - 1, x + w - 2, ry + ROW_H - 2, 0xC0805010);
            ctx.fill(x + 3, ry - 1, x + 5, ry + ROW_H - 2, i.color());
            ctx.drawTextWithShadow(tr, rank + ".", x + 8, ry + 1, rank == 1 ? Theme.GOLD : Theme.MUTED);
            String label = (i.icon().isEmpty() ? "" : i.icon() + " ") + i.name();
            ctx.drawTextWithShadow(tr, label, x + 26, ry + 1, mine ? Theme.GOLD : i.color());
            String p = String.format("%,d", r.points());
            ctx.drawTextWithShadow(tr, p, x + w - 5 - tr.getWidth(p), ry + 1, Theme.GOLD);
            ry += ROW_H;
            rank++;
        }
    }
}
