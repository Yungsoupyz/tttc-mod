package dev.tungnesium.screen;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.tungnesium.Teams;
import dev.tungnesium.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Full-screen loading screen shown between games. The server opens and closes it.
 * Put art at assets/tungnesium/textures/loading/&lt;gameId&gt;.png (any size) to show a picture; without it a
 * styled title card is drawn.
 */
public class LoadingScreen extends Screen {
    private static final Map<String, int[]> SIZE_CACHE = new HashMap<>();

    private final String title;
    private final String subtitle;
    private final long startMs = System.currentTimeMillis();
    private final long durationMs;
    private final Identifier art;
    private final int[] artSize;

    public LoadingScreen(String gameId, String title, String subtitle, int seconds) {
        super(Text.literal("Loading"));
        this.title = title;
        this.subtitle = subtitle;
        this.durationMs = seconds * 1000L;
        String safe = gameId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        this.art = Identifier.of("tungnesium", "textures/loading/" + safe + ".png");
        this.artSize = sizeOf(art);
    }

    private static int[] sizeOf(Identifier id) {
        return SIZE_CACHE.computeIfAbsent(id.toString(), k -> {
            try {
                Resource r = MinecraftClient.getInstance().getResourceManager().getResource(id).orElse(null);
                if (r == null) return null;
                try (var in = r.getInputStream(); NativeImage img = NativeImage.read(in)) {
                    return new int[] {img.getWidth(), img.getHeight()};
                }
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }

    // no vanilla blur / dirt background
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {}

    @Override
    public void tick() {
        if (durationMs > 0 && System.currentTimeMillis() - startMs >= durationMs) close();
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float t = (now - startMs) / 1000f;

        c.fillGradient(0, 0, width, height, Theme.BG_TOP, Theme.BG_BOTTOM);
        // slow-moving warm bands
        for (int i = 0; i < 6; i++) {
            int y = (int) ((t * 18 + i * (height / 6f)) % height);
            c.fill(0, y, width, y + 1, 0x18FFC53D);
        }
        c.fill(0, 0, width, 22, 0xAA1E1008);
        c.fill(0, 21, width, 22, Theme.ACCENT);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("T U N G N E S I U M").formatted(Formatting.BOLD), width / 2, 7, Theme.GOLD);

        int centerY = height / 2 - 10;
        if (artSize != null) {
            int maxW = (int) (width * 0.6f);
            int maxH = (int) (height * 0.55f);
            float s = Math.min((float) maxW / artSize[0], (float) maxH / artSize[1]);
            int w = Math.max(1, (int) (artSize[0] * s));
            int h = Math.max(1, (int) (artSize[1] * s));
            int x = (width - w) / 2;
            int y = 34;
            c.fill(x - 3, y - 3, x + w + 3, y + h + 3, Theme.ACCENT);
            Theme.texture(c, art, x, y, w, h, artSize[0], artSize[1]);
            centerY = y + h + 14;
        } else {
            // title card
            int cw = Math.min(width - 40, 300);
            int cx = (width - cw) / 2;
            Theme.panel(c, cx, centerY - 30, cw, 60);
            c.fill(cx, centerY - 30, cx + 4, centerY + 30, Theme.ACCENT);
        }

        Text big = Text.literal(title).formatted(Formatting.BOLD);
        c.drawCenteredTextWithShadow(textRenderer, big, width / 2, centerY - (artSize == null ? 14 : 0), Theme.TEXT);
        if (!subtitle.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(subtitle), width / 2,
                    centerY + (artSize == null ? 0 : 12), Theme.MUTED);
        }

        // your team
        String own = Teams.ownTeamName();
        if (own != null) {
            Teams.Info info = Teams.info(own);
            String line = (info.icon().isEmpty() ? "" : info.icon() + " ") + info.name();
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("You are on  " + line), width / 2, height - 46, info.color());
        }

        // progress bar
        int bw = Math.min(width - 80, 260);
        int bx = (width - bw) / 2;
        int by = height - 28;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, Theme.LINE);
        c.fill(bx, by, bx + bw, by + 6, 0xFF2A1408);
        if (durationMs > 0) {
            float p = Math.min(1f, (now - startMs) / (float) durationMs);
            c.fill(bx, by, bx + (int) (bw * p), by + 6, Theme.GOLD);
        } else {
            int seg = bw / 4;
            int off = (int) ((Math.sin(t * 2.2) * 0.5 + 0.5) * (bw - seg));
            c.fill(bx + off, by, bx + off + seg, by + 6, Theme.GOLD);
        }
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("Get ready..."), width / 2, by + 11, Theme.MUTED);
    }
}
