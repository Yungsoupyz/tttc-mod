package dev.tungnesium;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Warm orange / yellow / brown palette and a few drawing helpers. */
public final class Theme {
    private Theme() {}

    public static final int BG_DARK = 0xFF1E1008;
    public static final int BG_TOP = 0xFF2A1408;
    public static final int BG_BOTTOM = 0xFFB4560F;
    public static final int PANEL = 0xD038200E;
    public static final int PANEL_LIGHT = 0xD04A2C14;
    public static final int HEADER = 0xFF7A3E0A;
    public static final int ACCENT = 0xFFF08A24;
    public static final int GOLD = 0xFFFFC53D;
    public static final int TEXT = 0xFFFFF1DC;
    public static final int MUTED = 0xFFC9A27A;
    public static final int LINE = 0xFF8A5528;

    public static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, PANEL);
        c.drawBorder(x, y, w, h, LINE);
    }

    /** Draws a whole texture stretched into w x h. tw/th are the real pixel size of the PNG. */
    public static void texture(DrawContext c, Identifier id, int x, int y, int w, int h, int tw, int th) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, tw, th, tw, th);
    }
}
