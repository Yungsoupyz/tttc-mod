package dev.tungnesium.screen;

import java.util.ArrayList;
import java.util.List;

import dev.tungnesium.StandingsData;
import dev.tungnesium.Teams;
import dev.tungnesium.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Standings browser: Teams / Players / Games tabs. Data comes from the server. */
public class EventsScreen extends Screen {
    private static final int ROW_H = 16;

    private record Row(int rank, String name, int color, String right, boolean mine) {}

    private int tab = 0;
    private int gameIdx = 0;
    private double scroll = 0;
    private int px, py, pw, ph;

    public EventsScreen() {
        super(Text.literal("Standings"));
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 20);
        ph = height - 30;
        px = (width - pw) / 2;
        py = 15;
        String[] names = {"Teams", "Players", "Games"};
        int bw = (pw - 20) / 3;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(names[i]), b -> { tab = idx; scroll = 0; })
                    .dimensions(px + 10 + i * bw, py + 24, bw - 4, 18).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { gameIdx--; scroll = 0; })
                .dimensions(px + 10, py + 46, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { gameIdx++; scroll = 0; })
                .dimensions(px + pw - 30, py + 46, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(px + pw / 2 - 40, py + ph - 22, 80, 18).build());
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        super.renderBackground(c, mx, my, delta);
        Theme.panel(c, px, py, pw, ph);
        c.fill(px, py, px + pw, py + 18, Theme.HEADER);
        c.fill(px, py + 18, px + pw, py + 19, Theme.ACCENT);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        String own = Teams.ownTeamName();
        if (tab == 0) {
            int rank = 1;
            for (StandingsData.TeamRow r : StandingsData.teams) {
                Teams.Info i = Teams.info(r.team());
                out.add(new Row(rank++, (i.icon().isEmpty() ? "" : i.icon() + " ") + i.name(), i.color(),
                        String.format("%,d", r.points()), Teams.same(own, r.team())));
            }
        } else if (tab == 1) {
            int rank = 1;
            for (StandingsData.PlayerRow r : StandingsData.players) {
                out.add(new Row(rank++, r.name(), Teams.info(r.team()).color(), String.format("%,d", r.points()),
                        Teams.same(own, r.team())));
            }
        } else if (!StandingsData.games.isEmpty()) {
            int n = StandingsData.games.size();
            gameIdx = ((gameIdx % n) + n) % n;
            int rank = 1;
            for (StandingsData.TeamRow r : StandingsData.games.get(gameIdx).teams()) {
                Teams.Info i = Teams.info(r.team());
                out.add(new Row(rank++, (i.icon().isEmpty() ? "" : i.icon() + " ") + i.name(), i.color(),
                        String.format("%,d", r.points()), Teams.same(own, r.team())));
            }
        }
        return out;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(StandingsData.eventName).formatted(Formatting.BOLD),
                width / 2, py + 5, Theme.GOLD);

        if (tab == 2) {
            String g = StandingsData.games.isEmpty() ? "No games yet"
                    : StandingsData.games.get(((gameIdx % StandingsData.games.size()) + StandingsData.games.size()) % StandingsData.games.size()).name();
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(g), width / 2, py + 50, Theme.TEXT);
        }

        int top = py + 68;
        int bottom = py + ph - 28;
        int x1 = px + 10;
        int x2 = px + pw - 10;

        if (!StandingsData.loaded) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("Waiting for the server..."), width / 2, top + 20, Theme.MUTED);
            return;
        }

        List<Row> rows = rows();
        int maxScroll = Math.max(0, rows.size() * ROW_H - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        c.enableScissor(x1, top, x2, bottom);
        int y = top - (int) scroll;
        for (Row r : rows) {
            if (y + ROW_H >= top && y <= bottom) {
                c.fill(x1, y, x2, y + ROW_H - 1, r.mine() ? 0xC0805010 : Theme.PANEL_LIGHT);
                c.fill(x1, y, x1 + 3, y + ROW_H - 1, r.color());
                String rk = "#" + r.rank();
                int rankColor = r.rank() == 1 ? Theme.GOLD : Theme.MUTED;
                c.drawTextWithShadow(textRenderer, rk, x1 + 8, y + 4, rankColor);
                c.drawTextWithShadow(textRenderer, r.name(), x1 + 36, y + 4, r.mine() ? Theme.GOLD : Theme.TEXT);
                c.drawTextWithShadow(textRenderer, r.right(), x2 - 6 - textRenderer.getWidth(r.right()), y + 4, Theme.GOLD);
            }
            y += ROW_H;
        }
        c.disableScissor();
        if (rows.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("Nothing here yet"), width / 2, top + 20, Theme.MUTED);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * ROW_H;
        return true;
    }
}
