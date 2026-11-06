package dev.tungnesium.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.tungnesium.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;

/** Replaces the vanilla right-hand sidebar with a warm themed panel. Respects blank number formats. */
public final class SidebarRenderer {
    private SidebarRenderer() {}

    private record Line(Text name, Text score) {}

    public static void render(DrawContext ctx, ScoreboardObjective objective) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        Scoreboard sb = objective.getScoreboard();

        Comparator<ScoreboardEntry> cmp = Comparator.comparingInt(ScoreboardEntry::value).reversed()
                .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
        List<ScoreboardEntry> entries = sb.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(cmp)
                .limit(15)
                .toList();

        List<Line> lines = new ArrayList<>();
        for (ScoreboardEntry e : entries) {
            Team team = sb.getScoreHolderTeam(e.owner());
            Text name = Team.decorateName(team, e.name());
            Text score = e.formatted(objective.getNumberFormatOr(StyledNumberFormat.YELLOW));
            lines.add(new Line(name, score));
        }

        Text title = objective.getDisplayName();
        int width = tr.getWidth(title);
        for (Line l : lines) {
            int w = tr.getWidth(l.name());
            int sw = tr.getWidth(l.score());
            if (sw > 0) w += 10 + sw;
            width = Math.max(width, w);
        }
        width += 12;
        int rowH = 10;
        int height = 14 + lines.size() * rowH + 4;
        int x = ctx.getScaledWindowWidth() - width - 4;
        int y = (ctx.getScaledWindowHeight() - height) / 2;

        Theme.panel(ctx, x, y, width, height);
        ctx.fill(x, y, x + width, y + 13, Theme.HEADER);
        ctx.fill(x, y + 13, x + width, y + 14, Theme.ACCENT);
        ctx.drawCenteredTextWithShadow(tr, title, x + width / 2, y + 3, Theme.GOLD);

        int ry = y + 17;
        for (Line l : lines) {
            ctx.drawTextWithShadow(tr, l.name(), x + 6, ry, Theme.TEXT);
            int sw = tr.getWidth(l.score());
            if (sw > 0) ctx.drawTextWithShadow(tr, l.score(), x + width - 6 - sw, ry, Theme.GOLD);
            ry += rowH;
        }
    }
}
