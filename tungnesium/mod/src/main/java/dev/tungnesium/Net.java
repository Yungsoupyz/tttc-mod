package dev.tungnesium;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** All packets between the Tungnesium client and the Tungnesium Paper plugin. */
public final class Net {
    private Net() {}

    private static final String NS = "tungnesium";
    private static final int BIG = 1_000_000;

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
        return new CustomPayload.Id<>(Identifier.of(NS, path));
    }

    private static void writeList(PacketByteBuf b, List<String> l) {
        b.writeVarInt(l.size());
        for (String s : l) b.writeString(s);
    }

    private static List<String> readList(PacketByteBuf b) {
        int n = Math.min(b.readVarInt(), 5000);
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readString());
        return l;
    }

    // ---------------------------------------------------------------- server -> client

    public record RequestModList(String nonce) implements CustomPayload {
        public static final CustomPayload.Id<RequestModList> ID = id("request_modlist");
        public static final PacketCodec<PacketByteBuf, RequestModList> CODEC =
                PacketCodec.of((v, b) -> b.writeString(v.nonce()), b -> new RequestModList(b.readString()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Loading(String gameId, String title, String subtitle, int seconds) implements CustomPayload {
        public static final CustomPayload.Id<Loading> ID = id("loading");
        public static final PacketCodec<PacketByteBuf, Loading> CODEC = PacketCodec.of(
                (v, b) -> {
                    b.writeString(v.gameId());
                    b.writeString(v.title());
                    b.writeString(v.subtitle());
                    b.writeVarInt(v.seconds());
                },
                b -> new Loading(b.readString(), b.readString(), b.readString(), b.readVarInt()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ClearLoading() implements CustomPayload {
        public static final ClearLoading INSTANCE = new ClearLoading();
        public static final CustomPayload.Id<ClearLoading> ID = id("clear_loading");
        public static final PacketCodec<PacketByteBuf, ClearLoading> CODEC = PacketCodec.unit(INSTANCE);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Standings(String json) implements CustomPayload {
        public static final CustomPayload.Id<Standings> ID = id("standings");
        public static final PacketCodec<PacketByteBuf, Standings> CODEC =
                PacketCodec.of((v, b) -> b.writeString(v.json(), BIG), b -> new Standings(b.readString(BIG)));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record OpenEvents() implements CustomPayload {
        public static final OpenEvents INSTANCE = new OpenEvents();
        public static final CustomPayload.Id<OpenEvents> ID = id("open_events");
        public static final PacketCodec<PacketByteBuf, OpenEvents> CODEC = PacketCodec.unit(INSTANCE);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record UpdateAvailable(String version, String url, String sha256) implements CustomPayload {
        public static final CustomPayload.Id<UpdateAvailable> ID = id("update_available");
        public static final PacketCodec<PacketByteBuf, UpdateAvailable> CODEC = PacketCodec.of(
                (v, b) -> {
                    b.writeString(v.version());
                    b.writeString(v.url(), 2048);
                    b.writeString(v.sha256());
                },
                b -> new UpdateAvailable(b.readString(), b.readString(2048), b.readString()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------------------------------------------------------------- client -> server

    public record ModList(String nonce, String version, String jarHash, List<String> mods, List<String> packs)
            implements CustomPayload {
        public static final CustomPayload.Id<ModList> ID = id("modlist");
        public static final PacketCodec<PacketByteBuf, ModList> CODEC = PacketCodec.of(
                (v, b) -> {
                    b.writeString(v.nonce());
                    b.writeString(v.version());
                    b.writeString(v.jarHash());
                    writeList(b, v.mods());
                    writeList(b, v.packs());
                },
                b -> new ModList(b.readString(), b.readString(), b.readString(), readList(b), readList(b)));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RequestStandings() implements CustomPayload {
        public static final RequestStandings INSTANCE = new RequestStandings();
        public static final CustomPayload.Id<RequestStandings> ID = id("request_standings");
        public static final PacketCodec<PacketByteBuf, RequestStandings> CODEC = PacketCodec.unit(INSTANCE);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(RequestModList.ID, RequestModList.CODEC);
        PayloadTypeRegistry.playS2C().register(Loading.ID, Loading.CODEC);
        PayloadTypeRegistry.playS2C().register(ClearLoading.ID, ClearLoading.CODEC);
        PayloadTypeRegistry.playS2C().register(Standings.ID, Standings.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEvents.ID, OpenEvents.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateAvailable.ID, UpdateAvailable.CODEC);
        PayloadTypeRegistry.playC2S().register(ModList.ID, ModList.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestStandings.ID, RequestStandings.CODEC);
    }
}
