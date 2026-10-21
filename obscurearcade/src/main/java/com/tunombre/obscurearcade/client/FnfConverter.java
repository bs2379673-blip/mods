package com.tunombre.obscurearcade.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Convierte una cancion de Friday Night Funkin (charts JSON clasico/Psych o V-Slice + Inst/Voices .ogg) a un .osz de osu!mania 4K
 * con dos versiones por dificultad (Jugador y Oponente), que CubOsu! importa desde su carpeta de importacion.
 */
public final class FnfConverter {
    private FnfConverter() { }

    record Note(long time, int lane, long len) { }

    /** dificultad -> {jugador, oponente} */
    record Chart(List<Note> player, List<Note> opp) { }

    public static String convert(Path folder, Path outDir) throws IOException {
        String name = folder.getFileName().toString();
        Map<String, Chart> charts = new LinkedHashMap<>();
        double bpm = 120;
        List<Path> jsons = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().forEach(jsons::add);
        }
        for (Path p : jsons) {
            JsonObject root;
            try { root = JsonParser.parseString(Files.readString(p)).getAsJsonObject(); } catch (RuntimeException e) { continue; }
            String fn = p.getFileName().toString();
            String low = fn.toLowerCase(Locale.ROOT);
            if (root.has("timeChanges")) {
                try { bpm = root.getAsJsonArray("timeChanges").get(0).getAsJsonObject().get("bpm").getAsDouble(); } catch (RuntimeException ignored) { }
            } else if (root.has("song") && root.get("song").isJsonObject() && root.getAsJsonObject("song").has("notes")) {
                JsonObject song = root.getAsJsonObject("song");
                if (song.has("bpm")) bpm = song.get("bpm").getAsDouble();
                String diff = low.substring(0, low.length() - 5);
                if (diff.startsWith(name.toLowerCase(Locale.ROOT) + "-")) diff = diff.substring(name.length() + 1);
                else if (diff.equals(name.toLowerCase(Locale.ROOT)) || diff.equals("chart")) diff = "normal";
                charts.put(diff, legacy(song));
            } else if (root.has("notes") && root.get("notes").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("notes").entrySet()) {
                    if (e.getValue().isJsonArray()) charts.put(e.getKey(), slice(e.getValue().getAsJsonArray()));
                }
            }
        }
        if (charts.isEmpty()) throw new IOException("No encontré ningún chart (.json) en la carpeta");

        Path inst = null;
        List<Path> voices = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                String f = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!f.endsWith(".ogg")) continue;
                if (f.contains("inst")) { if (inst == null) inst = p; }
                else if (f.contains("voices")) voices.add(p);
            }
        }
        if (inst == null) throw new IOException("Falta Inst.ogg");

        Files.createDirectories(outDir);
        Path out = outDir.resolve(name + ".osz");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(out))) {
            String title = name.replace('_', ' ');
            int n = 0;
            for (Map.Entry<String, Chart> e : charts.entrySet()) {
                for (int side = 0; side < 2; side++) {
                    List<Note> l = side == 0 ? e.getValue().player() : e.getValue().opp();
                    if (l.isEmpty()) continue;
                    String ver = cap(e.getKey()) + (side == 0 ? " - Jugador" : " - Oponente");
                    z.putNextEntry(new ZipEntry(title + " [" + ver + "].osu"));
                    z.write(osu(title, ver, bpm, l).getBytes(StandardCharsets.UTF_8));
                    z.closeEntry();
                    n++;
                }
            }
            if (n == 0) throw new IOException("Los charts no tienen notas");
            z.putNextEntry(new ZipEntry("audio.wav"));
            writeAudio(inst, voices, z);
            z.closeEntry();
            return out.toString() + " (" + n + " mapas)";
        }
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static Chart legacy(JsonObject song) {
        List<Note> pl = new ArrayList<>(), op = new ArrayList<>();
        for (JsonElement se : song.getAsJsonArray("notes")) {
            JsonObject sec = se.getAsJsonObject();
            boolean must = sec.has("mustHitSection") && sec.get("mustHitSection").getAsBoolean();
            if (!sec.has("sectionNotes")) continue;
            for (JsonElement ne : sec.getAsJsonArray("sectionNotes")) {
                JsonArray a = ne.getAsJsonArray();
                if (a.size() < 2) continue;
                long t = Math.round(a.get(0).getAsDouble());
                int d = a.get(1).getAsInt();
                if (d < 0) continue;
                long len = a.size() > 2 && a.get(2).isJsonPrimitive() && a.get(2).getAsJsonPrimitive().isNumber() ? Math.round(a.get(2).getAsDouble()) : 0;
                boolean mine = must ? d < 4 : d >= 4;
                (mine ? pl : op).add(new Note(t, d % 4, len));
            }
        }
        return tidy(pl, op);
    }

    private static Chart slice(JsonArray notes) {
        List<Note> pl = new ArrayList<>(), op = new ArrayList<>();
        for (JsonElement ne : notes) {
            JsonObject o = ne.getAsJsonObject();
            long t = Math.round(o.get("t").getAsDouble());
            int d = o.get("d").getAsInt();
            long len = o.has("l") ? Math.round(o.get("l").getAsDouble()) : 0;
            if (d < 0) continue;
            (d < 4 ? pl : op).add(new Note(t, d % 4, len));
        }
        return tidy(pl, op);
    }

    private static Chart tidy(List<Note> a, List<Note> b) { return new Chart(clean(a), clean(b)); }

    private static List<Note> clean(List<Note> l) {
        l.sort(Comparator.comparingLong(Note::time).thenComparingInt(Note::lane));
        List<Note> out = new ArrayList<>();
        Note prev = null;
        for (Note n : l) {
            if (prev != null && prev.time() == n.time() && prev.lane() == n.lane()) continue;
            out.add(n);
            prev = n;
        }
        return out;
    }

    private static String osu(String title, String ver, double bpm, List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("osu file format v14\n\n[General]\nAudioFilename: audio.wav\nAudioLeadIn: 0\nPreviewTime: -1\nCountdown: 0\nMode: 3\n\n");
        sb.append("[Metadata]\nTitle:").append(title).append("\nTitleUnicode:").append(title).append("\nArtist:Friday Night Funkin'\nArtistUnicode:Friday Night Funkin'\n");
        sb.append("Creator:Obscure Arcade\nVersion:").append(ver).append("\nSource:\nTags:fnf\nBeatmapID:0\nBeatmapSetID:-1\n\n");
        sb.append("[Difficulty]\nHPDrainRate:6\nCircleSize:4\nOverallDifficulty:7\nApproachRate:5\nSliderMultiplier:1.4\nSliderTickRate:1\n\n");
        sb.append("[Events]\n\n[TimingPoints]\n0,").append(String.format(Locale.ROOT, "%.4f", 60000.0 / Math.max(20, bpm))).append(",4,1,0,100,1,0\n\n[HitObjects]\n");
        for (Note n : notes) {
            int x = n.lane() * 128 + 64;
            if (n.len() > 60) sb.append(x).append(",192,").append(n.time()).append(",128,0,").append(n.time() + n.len()).append(":0:0:0:0:\n");
            else sb.append(x).append(",192,").append(n.time()).append(",1,0,0:0:0:0:\n");
        }
        return sb.toString();
    }

    // ---- audio ----

    private record Pcm(short[] data, int channels, int rate) {
        int frames() { return data.length / channels; }
    }

    private static Pcm decode(Path p) throws IOException {
        byte[] raw = Files.readAllBytes(p);
        ByteBuffer bb = ByteBuffer.allocateDirect(raw.length);
        bb.put(raw).flip();
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer ch = st.mallocInt(1), sr = st.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(bb, ch, sr);
            if (pcm == null) throw new IOException("No pude decodificar " + p.getFileName());
            try {
                short[] d = new short[pcm.remaining()];
                pcm.get(d);
                return new Pcm(d, Math.max(1, ch.get(0)), sr.get(0));
            } finally {
                LibCStdlib.free(pcm);
            }
        }
    }

    private static int sample(Pcm t, double pos, int c) {
        int f = (int) pos;
        if (f < 0 || f >= t.frames()) return 0;
        return t.data()[f * t.channels() + Math.min(c, t.channels() - 1)];
    }

    private static void writeAudio(Path inst, List<Path> voices, OutputStream out) throws IOException {
        Pcm base = decode(inst);
        List<Pcm> tracks = new ArrayList<>();
        tracks.add(base);
        for (Path v : voices) tracks.add(decode(v));
        int frames = base.frames();
        int rate = base.rate();
        int dataLen = frames * 4;
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLen).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        h.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4).putShort((short) 4).putShort((short) 16);
        h.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLen);
        out.write(h.array());
        int chunk = 16384;
        ByteBuffer buf = ByteBuffer.allocate(chunk * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int start = 0; start < frames; start += chunk) {
            int n = Math.min(chunk, frames - start);
            buf.clear();
            for (int i = 0; i < n; i++) {
                int fi = start + i;
                for (int c = 0; c < 2; c++) {
                    int sum = 0;
                    for (Pcm t : tracks) sum += sample(t, t.rate() == rate ? fi : fi * (double) t.rate() / rate, c);
                    buf.putShort((short) Math.max(-32768, Math.min(32767, sum)));
                }
            }
            out.write(buf.array(), 0, n * 4);
        }
    }
}
