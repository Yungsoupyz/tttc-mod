package dev.tungnesium;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/** Per-player toggles, saved to config/tungnesium.json. */
public final class TungConfig {
    private TungConfig() {}

    public static boolean tab = true;
    public static boolean scoreboard = true;
    public static boolean pauseTheme = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tungnesium.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("tab")) tab = o.get("tab").getAsBoolean();
            if (o.has("scoreboard")) scoreboard = o.get("scoreboard").getAsBoolean();
            if (o.has("pauseTheme")) pauseTheme = o.get("pauseTheme").getAsBoolean();
        } catch (Exception e) {
            TungnesiumClient.LOG.warn("Could not read config", e);
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("tab", tab);
            o.addProperty("scoreboard", scoreboard);
            o.addProperty("pauseTheme", pauseTheme);
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file(), g.toJson(o));
        } catch (Exception e) {
            TungnesiumClient.LOG.warn("Could not save config", e);
        }
    }
}
