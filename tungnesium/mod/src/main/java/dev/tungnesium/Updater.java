package dev.tungnesium;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * Downloads a new copy of this mod into the mods folder.
 * Safety rules: https only, host must be on the allow-list below, and the file must match the SHA-256 the
 * server announced. The old jar is removed when the game closes (it is locked while running).
 */
public final class Updater {
    private Updater() {}

    private static final List<String> HOSTS = List.of(
            "github.com", "githubusercontent.com", "discordapp.com", "discordapp.net", "discord.com");
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private static String cachedHash;

    public static boolean hostAllowed(URI u) {
        if (u == null || !"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) return false;
        String h = u.getHost().toLowerCase(Locale.ROOT);
        for (String a : HOSTS) if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }

    /** Positive if a is newer than b. Compares dotted numbers only. */
    public static int compare(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static Optional<Path> ownJar() {
        try {
            ModContainer c = FabricLoader.getInstance().getModContainer(TungnesiumClient.ID).orElse(null);
            if (c == null || c.getOrigin().getKind() != ModOrigin.Kind.PATH) return Optional.empty();
            for (Path p : c.getOrigin().getPaths()) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) return Optional.of(p);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public static synchronized String ownJarHash() {
        if (cachedHash != null) return cachedHash;
        try {
            Optional<Path> p = ownJar();
            if (p.isPresent()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(p.get())) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
                cachedHash = HexFormat.of().formatHex(md.digest());
                return cachedHash;
            }
        } catch (Exception ignored) {
        }
        cachedHash = "";
        return cachedHash;
    }

    /** Blocking. Call from a worker thread. Throws on any problem. */
    public static void download(String url, String sha256, String version, Consumer<String> progress) throws Exception {
        if (sha256 == null || sha256.isBlank()) throw new IllegalStateException("Server did not send a checksum");
        if (!version.matches("[0-9A-Za-z._\\-]+")) throw new IllegalStateException("Bad version string");
        URI uri = URI.create(url);
        if (!hostAllowed(uri)) throw new IllegalStateException("Download host is not allowed: " + uri.getHost());

        HttpClient hc = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<InputStream> r = hc.send(
                HttpRequest.newBuilder(uri).header("User-Agent", "Tungnesium-Updater").build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (!hostAllowed(r.uri())) throw new IllegalStateException("Redirected to a host that is not allowed");
        if (r.statusCode() != 200) throw new IllegalStateException("Download failed (HTTP " + r.statusCode() + ")");

        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path part = mods.resolve("tungnesium-" + version + ".jar.part");
        Path dest = mods.resolve("tungnesium-" + version + ".jar");

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalStateException("File too large");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
                progress.accept("Downloading... " + (total / 1024) + " KB");
            }
        } catch (Exception e) {
            Files.deleteIfExists(part);
            throw e;
        }

        String got = HexFormat.of().formatHex(md.digest());
        if (!got.equalsIgnoreCase(sha256.trim())) {
            Files.deleteIfExists(part);
            throw new IllegalStateException("Checksum mismatch, file discarded");
        }
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
        scheduleDeleteOld(dest);
    }

    private static void scheduleDeleteOld(Path newJar) {
        Optional<Path> old = ownJar();
        if (old.isEmpty()) return;
        Path o = old.get().toAbsolutePath();
        if (o.equals(newJar.toAbsolutePath())) return;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                    new ProcessBuilder("cmd", "/c", "ping 127.0.0.1 -n 6 >nul & del /f /q \"" + o + "\"").start();
                } else {
                    new ProcessBuilder("sh", "-c", "sleep 4; rm -f \"$0\"", o.toString()).start();
                }
            } catch (Exception ignored) {
            }
        }));
    }
}
