package dev.tungnesium.ui;

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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Esc menu: warm top line, a Standings button and a small top-teams panel. */
public final class PauseMenu {
    private PauseMenu() {}

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
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.fill(0, 0, width, 2, Theme.ACCENT);
        if (!StandingsData.loaded || width < 360) return;

        int x = 6;
        int y = 32;
        int w = 118;
        int rows = Math.min(5, StandingsData.teams.size());
        Theme.panel(ctx, x, y, w, 16 + rows * 11);
        ctx.fill(x, y, x + w, y + 13, Theme.HEADER);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("Top teams"), x + 5, y + 3, Theme.GOLD);
        int ry = y + 16;
        int rank = 1;
        for (StandingsData.TeamRow r : StandingsData.teams) {
            if (rank > rows) break;
            Teams.Info i = Teams.info(r.team());
            ctx.fill(x + 3, ry, x + 5, ry + 9, i.color());
            ctx.drawTextWithShadow(mc.textRenderer, rank + ". " + mc.textRenderer.trimToWidth(i.name(), 66), x + 8, ry, Theme.TEXT);
            String p = String.valueOf(r.points());
            ctx.drawTextWithShadow(mc.textRenderer, p, x + w - 4 - mc.textRenderer.getWidth(p), ry, Theme.GOLD);
            ry += 11;
            rank++;
        }
    }
}
