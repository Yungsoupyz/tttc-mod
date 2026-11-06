package dev.tungnesium;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

/**
 * Builds the list of installed mods and enabled resource packs that the server asks for.
 * Format of each entry: id|version|name. Client-to-server packets are limited to ~32 KB, so we cap the size.
 */
public final class ModReport {
    private ModReport() {}

    private static String clip(String s, int n) {
        if (s == null) return "";
        s = s.replace('|', '/');
        return s.length() <= n ? s : s.substring(0, n);
    }

    public static Net.ModList build(String nonce) {
        List<String> mods = new ArrayList<>();
        int budget = 22000;
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            ModMetadata md = m.getMetadata();
            String e = clip(md.getId(), 64) + "|" + clip(md.getVersion().getFriendlyString(), 40) + "|" + clip(md.getName(), 40);
            budget -= e.length() + 3;
            if (budget < 0) break;
            mods.add(e);
        }

        List<String> packs = new ArrayList<>();
        int packBudget = 4000;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            for (ResourcePackProfile p : mc.getResourcePackManager().getEnabledProfiles()) {
                String e = clip(p.getId(), 80) + "|" + clip(p.getDisplayName().getString(), 60);
                packBudget -= e.length() + 3;
                if (packBudget < 0) break;
                packs.add(e);
            }
        } catch (Exception ex) {
            TungnesiumClient.LOG.warn("Could not list resource packs", ex);
        }

        return new Net.ModList(nonce, TungnesiumClient.version, Updater.ownJarHash(), mods, packs);
    }
}
