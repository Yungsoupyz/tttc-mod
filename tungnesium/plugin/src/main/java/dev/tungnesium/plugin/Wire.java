package dev.tungnesium.plugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minecraft-style VarInt / String encoding, matching PacketByteBuf on the client. */
final class Wire {
    private Wire() {}

    static void varInt(ByteArrayOutputStream o, int v) {
        while ((v & ~0x7F) != 0) {
            o.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write(v);
    }

    static void str(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        varInt(o, b.length);
        o.write(b, 0, b.length);
    }

    static final class Reader {
        private final byte[] d;
        private int p = 0;

        Reader(byte[] d) { this.d = d; }

        int varInt() {
            int v = 0, shift = 0;
            while (true) {
                if (p >= d.length) throw new IllegalArgumentException("eof");
                byte b = d[p++];
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return v;
                shift += 7;
                if (shift > 35) throw new IllegalArgumentException("varint too long");
            }
        }

        String str() {
            int n = varInt();
            if (n < 0 || p + n > d.length) throw new IllegalArgumentException("bad string length");
            String s = new String(d, p, n, StandardCharsets.UTF_8);
            p += n;
            return s;
        }

        List<String> list() {
            int n = varInt();
            if (n < 0 || n > 5000) throw new IllegalArgumentException("bad list length");
            List<String> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(str());
            return l;
        }
    }
}
