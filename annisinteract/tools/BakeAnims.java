import com.annis.interact.client.anim.PairAnims;
import com.annis.interact.client.anim.Rig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte las animaciones de pareja de Annis Interacciones en un paquete de recursos con
 * animaciones Bedrock (assets/punchy_3rd_person/animations/annis_interacciones.json), para abrirlas
 * y retocarlas en Punchy Studio. Los nombres son "annis_<animacion>"; si el paquete esta activo,
 * Annis usa esa version en vez de la propia.
 *
 * Uso: java BakeAnims <carpeta de salida>
 */
public final class BakeAnims {
    /** nombre -> {inicio, largo (ticks), repite (1/0)}. */
    private static final Map<String, int[]> WINDOWS = new LinkedHashMap<>();

    static {
        scene("abrazo", 80);
        scene("abrazo_cuello", 80);
        scene("beso_mejilla", 60);
        scene("recibir_beso", 60);
        scene("chocar_cinco", 45);
        scene("apreton_manos", 50);
        scene("palmadita", 45);
        scene("recibir_palmadita", 45);
        loop("cargar_brazos", 0, 210);
        loop("en_brazos", 0, 228);
        loop("llevar_caballito", 0, 180);
        loop("a_caballito", 0, 210);
        loop("vals_guia", 20, 300);
        loop("vals_pareja", 20, 300);
        loop("lento_a", 30, 300);
        loop("lento_b", 30, 300);
        loop("estrellas_a", 0, 300);
        loop("estrellas_b", 0, 260);
        loop("sentarse_abrazo", 0, 210);
        loop("sentarse_apoyo", 0, 210);
        scene("caido", 30);
        loop("caido_suelo", 0, 160);
        scene("ayudar_levantar", 50);
        scene("levantarse", 50);
        scene("propuesta", 100);
        scene("recibir_propuesta", 100);
        loop("hombro_rodea", 30, 160);
        loop("hombro_apoya", 30, 160);
        scene("besar_mano", 70);
        scene("recibir_beso_mano", 70);
        loop("cama_abrazado", 0, 300);
        loop("cama_abraza", 0, 300);
        loop("swing_guia", 24, 320);
        loop("swing_sigue", 24, 320);
        loop("bachata_guia", 20, 160);
        loop("bachata_sigue", 20, 160);
        loop("tango_guia", 20, 240);
        loop("tango_sigue", 20, 240);
    }

    private static void scene(String n, int len) {
        WINDOWS.put(n, new int[]{0, len, 0});
    }

    private static void loop(String n, int start, int len) {
        WINDOWS.put(n, new int[]{start, len, 1});
    }

    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final float[][] REST = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    private static final int STEP = 2; // un fotograma clave cada 2 ticks (10 por segundo)

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "annis_animaciones");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n");
        boolean firstAnim = true;
        for (Map.Entry<String, int[]> w : WINDOWS.entrySet()) {
            PairAnims.Anim anim = PairAnims.ALL.get(w.getKey());
            if (anim == null) {
                continue;
            }
            int start = w.getValue()[0], len = w.getValue()[1];
            boolean loop = w.getValue()[2] == 1;
            int frames = len / STEP + 1;
            // [hueso][fotograma][rot xyz, pos xyz]
            float[][][] v = new float[6][frames][6];
            boolean[] used = new boolean[6];
            float[][] root = anim.root() == null ? null : new float[frames][6];
            for (int f = 0; f < frames; f++) {
                float t = start + f * STEP;
                Rig rig = new Rig().restAll();
                Rig.P[] ps = rig.parts();
                for (Rig.P p : ps) {
                    p.load(0, 0, 0, p.x, p.y, p.z);
                }
                anim.pose().apply(rig, t);
                for (int i = 0; i < 6; i++) {
                    Rig.P p = ps[i];
                    // piernas que siguen el paso de caminar: no se guardan (asi en el juego siguen caminando)
                    if (!p.touched || (i >= 4 && rig.walkLegs)) {
                        continue;
                    }
                    if (i >= 2) {
                        Rig.straighten(p);
                    }
                    used[i] = true;
                    v[i][f] = new float[]{deg(p.pitch), deg(p.yaw), deg(p.roll),
                            p.x - REST[i][0], -(p.y - REST[i][1]), p.z - REST[i][2]};
                }
                if (root != null) {
                    Rig.Root r = anim.root().at(t);
                    root[f] = r == null ? new float[6] : toPunchyRoot(r);
                }
            }
            sb.append(firstAnim ? "" : ",\n");
            firstAnim = false;
            sb.append("    \"annis_").append(w.getKey()).append("\": {\n");
            sb.append("      \"animation_length\": ").append(num(len / 20.0f)).append(",\n");
            sb.append("      \"loop\": ").append(loop).append(",\n");
            sb.append("      \"bones\": {\n");
            boolean firstBone = true;
            for (int i = 0; i < 6; i++) {
                if (!used[i]) {
                    continue;
                }
                sb.append(firstBone ? "" : ",\n");
                firstBone = false;
                sb.append("        \"").append(BONES[i]).append("\": {\n");
                sb.append("          \"rotation\": ").append(track(v[i], 0, frames)).append(",\n");
                sb.append("          \"position\": ").append(track(v[i], 3, frames)).append("\n        }");
            }
            if (root != null) {
                sb.append(firstBone ? "" : ",\n");
                firstBone = false;
                sb.append("        \"root\": {\n");
                sb.append("          \"rotation\": ").append(track(root, 0, frames)).append(",\n");
                sb.append("          \"position\": ").append(track(root, 3, frames)).append("\n        }");
            }
            sb.append("\n      }\n    }");
        }
        sb.append("\n  }\n}\n");
        Path anims = out.resolve("assets/punchy_3rd_person/animations");
        Files.createDirectories(anims);
        Files.writeString(anims.resolve("annis_interacciones.json"), sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 34,\n"
                + "    \"description\": \"Annis Animaciones: interacciones de Annis World, editables en Punchy Studio\"\n  }\n}\n",
                StandardCharsets.UTF_8);
        System.out.println("Animaciones exportadas: " + WINDOWS.size() + " -> " + anims);
    }

    /** Raiz de Annis (gira en la cadera, 0.7 bloques) -> hueso root de Punchy (gira en los pies). */
    static float[] toPunchyRoot(Rig.Root r) {
        float[] m = Rig.rot(r.pitch(), r.yaw(), r.roll());
        // mismo orden que al dibujar: Z, luego Y, luego X
        float[] mh = mulZyx(r.pitch(), r.yaw(), r.roll(), 0, 0.7f, 0);
        float t1x = -r.left(), t1y = r.up(), t1z = -r.forward();
        float ox = t1x + 0 - mh[0], oy = t1y + 0.7f - mh[1], oz = t1z + 0 - mh[2];
        return new float[]{-deg(r.pitch()), -deg(r.yaw()), deg(r.roll()), -ox * 16, oy * 16, oz * 16};
    }

    /** Rz(roll) * Ry(yaw) * Rx(pitch) * v. */
    static float[] mulZyx(float pitch, float yaw, float roll, float x, float y, float z) {
        double cx = Math.cos(pitch), sx = Math.sin(pitch);
        double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
        double x1 = x;
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x2 = x1 * cy + z1 * sy, z2 = -x1 * sy + z1 * cy;
        double cz = Math.cos(roll), sz = Math.sin(roll);
        double x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
        return new float[]{(float) x3, (float) y3, (float) z2};
    }

    private static float deg(float r) {
        return (float) Math.toDegrees(r);
    }

    private static String num(float f) {
        if (Math.abs(f) < 0.0005f) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.3f", f).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Fotogramas clave de 3 valores (desde "off"), saltando los que no cambian. */
    private static String track(float[][] vals, int off, int frames) {
        StringBuilder sb = new StringBuilder("{");
        float[] last = null;
        boolean first = true;
        for (int f = 0; f < frames; f++) {
            float[] cur = {vals[f][off], vals[f][off + 1], vals[f][off + 2]};
            boolean lastFrame = f == frames - 1;
            boolean nextSame = !lastFrame && same(cur, new float[]{vals[f + 1][off], vals[f + 1][off + 1], vals[f + 1][off + 2]});
            if (last != null && same(cur, last) && nextSame && !lastFrame) {
                continue;
            }
            sb.append(first ? "" : ", ");
            first = false;
            sb.append('"').append(num(f * STEP / 20.0f)).append("\": [").append(num(cur[0])).append(", ")
                    .append(num(cur[1])).append(", ").append(num(cur[2])).append(']');
            last = cur;
        }
        return sb.append('}').toString();
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 0.01f && Math.abs(a[1] - b[1]) < 0.01f && Math.abs(a[2] - b[2]) < 0.01f;
    }
}
