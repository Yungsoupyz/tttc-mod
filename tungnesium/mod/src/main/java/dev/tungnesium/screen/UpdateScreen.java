package dev.tungnesium.screen;

import dev.tungnesium.Theme;
import dev.tungnesium.TungnesiumClient;
import dev.tungnesium.Updater;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Asks the player to update, downloads the new jar, then asks them to restart the game. */
public class UpdateScreen extends Screen {
    private enum State { PROMPT, DOWNLOADING, DONE, ERROR }

    private final String version;
    private final String url;
    private final String sha256;
    private volatile State state = State.PROMPT;
    private volatile String status = "";

    public UpdateScreen(String version, String url, String sha256) {
        super(Text.literal("Update"));
        this.version = version;
        this.url = url;
        this.sha256 = sha256;
    }

    @Override public boolean shouldCloseOnEsc() { return state != State.DOWNLOADING; }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 + 30;
        switch (state) {
            case PROMPT -> {
                addDrawableChild(ButtonWidget.builder(Text.literal("Update now"), b -> startDownload())
                        .dimensions(cx - 102, y, 100, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Later"), b -> close())
                        .dimensions(cx + 2, y, 100, 20).build());
            }
            case DONE -> addDrawableChild(ButtonWidget.builder(Text.literal("Quit game (then reopen)"),
                    b -> client.scheduleStop()).dimensions(cx - 80, y, 160, 20).build());
            case ERROR -> addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                    .dimensions(cx - 50, y, 100, 20).build());
            default -> { }
        }
    }

    private void startDownload() {
        state = State.DOWNLOADING;
        status = "Starting...";
        clearAndInit();
        Thread t = new Thread(() -> {
            try {
                Updater.download(url, sha256, version, s -> status = s);
                status = "Tungnesium " + version + " is installed. Close and reopen Minecraft to finish.";
                state = State.DONE;
            } catch (Exception e) {
                TungnesiumClient.LOG.warn("Update failed", e);
                status = "Update failed: " + e.getMessage() + ". Download it from the event Discord instead.";
                state = State.ERROR;
            }
            client.execute(this::clearAndInit);
        }, "Tungnesium-Updater");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        int pw = Math.min(width - 20, 320);
        int px = (width - pw) / 2;
        int py = height / 2 - 50;
        Theme.panel(c, px, py, pw, 92);
        c.fill(px, py, px + pw, py + 16, Theme.HEADER);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("Tungnesium update"), width / 2, py + 4, Theme.GOLD);
        String line1 = switch (state) {
            case PROMPT -> "Version " + version + " is available (you have " + TungnesiumClient.version + ").";
            case DOWNLOADING -> "Updating...";
            default -> "";
        };
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(line1), width / 2, py + 26, Theme.TEXT);
        String s = status;
        if (!s.isEmpty()) {
            int yy = py + 42;
            for (var line : textRenderer.wrapLines(Text.literal(s), pw - 20)) {
                c.drawCenteredTextWithShadow(textRenderer, line, width / 2, yy, Theme.MUTED);
                yy += 10;
            }
        }
    }
}
