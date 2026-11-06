package dev.tungnesium;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.tungnesium.screen.EventsScreen;
import dev.tungnesium.screen.LoadingScreen;
import dev.tungnesium.screen.UpdateScreen;
import dev.tungnesium.ui.PauseMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class TungnesiumClient implements ClientModInitializer {
    public static final String ID = "tungnesium";
    public static final Logger LOG = LoggerFactory.getLogger("Tungnesium");
    public static String version = "0.0.0";

    private static KeyBinding openKey;
    private static KeyBinding tabKey;
    private static KeyBinding sidebarKey;

    @Override
    public void onInitializeClient() {
        version = FabricLoader.getInstance().getModContainer(ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
        TungConfig.load();
        Net.registerPayloads();

        ClientPlayNetworking.registerGlobalReceiver(Net.RequestModList.ID,
                (p, ctx) -> ClientPlayNetworking.send(ModReport.build(p.nonce())));
        ClientPlayNetworking.registerGlobalReceiver(Net.Loading.ID,
                (p, ctx) -> ctx.client().setScreen(new LoadingScreen(p.gameId(), p.title(), p.subtitle(), p.seconds())));
        ClientPlayNetworking.registerGlobalReceiver(Net.ClearLoading.ID, (p, ctx) -> {
            if (ctx.client().currentScreen instanceof LoadingScreen) ctx.client().setScreen(null);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.Standings.ID, (p, ctx) -> StandingsData.parse(p.json()));
        ClientPlayNetworking.registerGlobalReceiver(Net.OpenEvents.ID, (p, ctx) -> openEvents(ctx.client()));
        ClientPlayNetworking.registerGlobalReceiver(Net.UpdateAvailable.ID,
                (p, ctx) -> ctx.client().setScreen(new UpdateScreen(p.version(), p.url(), p.sha256())));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tungnesium.open_events", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.tungnesium"));
        tabKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tungnesium.toggle_tab", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.tungnesium"));
        sidebarKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tungnesium.toggle_sidebar", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.tungnesium"));

        ClientTickEvents.END_CLIENT_TICK.register(TungnesiumClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StandingsData.clear());
        PauseMenu.register();
    }

    private static void tick(MinecraftClient mc) {
        while (tabKey.wasPressed()) {
            TungConfig.tab = !TungConfig.tab;
            TungConfig.save();
            say(mc, "Event Tab list: " + (TungConfig.tab ? "ON" : "OFF"));
        }
        while (sidebarKey.wasPressed()) {
            TungConfig.scoreboard = !TungConfig.scoreboard;
            TungConfig.save();
            say(mc, "Event scoreboard: " + (TungConfig.scoreboard ? "ON" : "OFF"));
        }
        while (openKey.wasPressed()) {
            if (mc.player != null && mc.currentScreen == null) openEvents(mc);
        }
    }

    private static void say(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    public static void openEvents(MinecraftClient mc) {
        ClientPlayNetworking.send(Net.RequestStandings.INSTANCE);
        mc.setScreen(new EventsScreen());
    }
}
