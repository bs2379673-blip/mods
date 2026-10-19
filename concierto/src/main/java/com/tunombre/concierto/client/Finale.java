package com.tunombre.concierto.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.tunombre.dancers.DancerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Final del concierto (todo con shaders, sin imagenes):
 *  1) las bailarinas se contraen y estallan, cada una con una explosion de su color;
 *  2) sus tres luces suben y recorren el cielo como reflectores (turquesa, rojo, dorado);
 *  3) se juntan arriba del escenario y hacen una explosion gigante; despues todo vuelve a la normalidad.
 * Lleva su propio reloj: sigue aunque cambie la cancion.
 */
public final class Finale {
    private Finale() { }

    private record Pt(double x, double y, double z, float scale, int kind) { }   // kind 0 = Miku, 1 = Teto, 2 = Neru

    private static final float[][] COL = {{0.22F, 0.92F, 0.86F}, {1F, 0.2F, 0.3F}, {1F, 0.84F, 0.15F}};
    private static final float[] WHITE = {1F, 1F, 1F};

    // tiempos (segundos desde que arranca el final)
    private static final float B0 = 1.9F;      // estallido de las bailarinas (justo cuando termina de contraerse la figura)
    private static final float RISE = 2.0F;    // la luz sube desde la bailarina
    private static final float C0 = 15.5F;     // empiezan a juntarse
    private static final float X = 19.5F;      // explosion gigante
    private static final float END = X + 6.5F;
    private static final double R = 300.0;     // radio de la boveda de luces

    private static long t0 = -1L;
    private static final List<Pt> pts = new ArrayList<>();
    private static boolean snd1, snd2, flash2;

    public static boolean active() { return t0 >= 0L; }

    public static void clear() { t0 = -1L; pts.clear(); }

    private static float time() { return (System.nanoTime() - t0) / 1e9F; }

    /** ConciertoClient pide un destello blanco una sola vez en la explosion gigante */
    public static boolean takeFlash() {
        if (t0 < 0L || flash2 || time() < X) return false;
        flash2 = true;
        return true;
    }

    public static void start(Stage stage) {
        clear();
        snd1 = snd2 = flash2 = false;
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof DancerEntity de)) continue;
                if (stage.set) {
                    double dx = e.getX() - stage.x, dz = e.getZ() - stage.z;
                    if (dx * dx + dz * dz > 200.0 * 200.0) continue;
                }
                String m = de.getModelName().toLowerCase();
                int k = m.contains("teto") ? 1 : m.contains("neru") ? 2 : 0;
                pts.add(new Pt(e.getX(), e.getY(), e.getZ(), de.getDancerScale(), k));
            }
        }
        boolean[] have = new boolean[3];
        for (Pt p : pts) if (p.scale() < 20F) have[p.kind()] = true;
        double cx = stage.set ? stage.x : (mc.player != null ? mc.player.getX() : 0), cy = stage.set ? stage.y : (mc.player != null ? mc.player.getY() : 64), cz = stage.set ? stage.z : (mc.player != null ? mc.player.getZ() : 0);
        for (int k = 0; k < 3; k++) {
            if (have[k]) continue;
            double lat = (k == 1 ? 11 : k == 2 ? -11 : 0);
            pts.add(new Pt(cx + stage.rx() * lat, cy, cz + stage.rz() * lat, 10F, k));
        }
        t0 = System.nanoTime();
    }

    private static float ease(float x) { x = Math.max(0F, Math.min(1F, x)); return 1F - (1F - x) * (1F - x) * (1F - x); }
    private static float smooth(float a, float b, float x) { float t = Math.max(0F, Math.min(1F, (x - a) / (b - a))); return t * t * (3F - 2F * t); }

    /** posicion de la luz i en el cielo (absoluta) a los F segundos */
    private static double[] light(Stage st, int i, float F, Pt from) {
        float az = (i - 1) * 0.95F + 1.15F * (float) Math.sin(0.40 * F + i * 2.1);
        float el = 0.52F + 0.30F * (float) Math.sin(0.58 * F + i * 1.7 + 1.0);
        float w = smooth(C0, X, F);
        az = az * (1F - w) + 0.03F * (i - 1) * w;
        el = el * (1F - w) + 1.50F * w;
        double ce = Math.cos(el);
        double rr = Math.sin(az) * ce, ff = Math.cos(az) * ce, uu = Math.sin(el);
        double sx = st.x + st.rx() * rr * R + st.fx() * ff * R;
        double sy = st.y + uu * R;
        double sz = st.z + st.rz() * rr * R + st.fz() * ff * R;
        float rise = ease((F - B0) / RISE);
        double k = rise;
        // sube desde el pecho de la bailarina hasta su lugar en la boveda
        return new double[]{from.x() + (sx - from.x()) * k, from.y() + 0.95 * from.scale() + (sy - from.y() - 0.95 * from.scale()) * k, from.z() + (sz - from.z()) * k};
    }

    public static void draw(ShaderInstance sh, double camX, double camY, double camZ, Stage st, float gain) {
        if (t0 < 0L || gain < 0.02F) return;
        float F = time();
        if (F > END) { clear(); return; }
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!snd1 && F >= B0 && mc.player != null) {
            snd1 = true;
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 3F, 0.7F, false);
        }
        if (!snd2 && F >= X && mc.player != null) {
            snd2 = true;
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 6F, 0.55F, false);
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 3F, 0.6F, false);
        }
        float fadeOut = F > X + 3.5F ? Math.max(0F, 1F - (F - X - 3.5F) / 3F) : 1F;
        float g = gain * fadeOut;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        StageFx.set(sh, "Alpha", 1F);
        StageFx.set(sh, "Prog", F);

        // ---------- 1) explosion de cada bailarina
        if (F >= B0 && F < B0 + 4.5F) {
            float a = F - B0;
            for (Pt p : pts) {
                float[] c = COL[p.kind()];
                double s = p.scale();
                double ox = p.x() - camX, oy = p.y() - camY, oz = p.z() - camZ;
                // columna de luz
                StageFx.set(sh, "Kind", 7F);
                StageFx.begin(bb);
                float ca = g * Math.max(0F, 1F - a / 2.4F);
                StageFx.strip(bb, ox, oy, oz, ox, oy + 6.0 * s, oz, 0.35 * s * (1F - a / 4F), 0.12 * s, c, ca, ca * 0.1F);
                BufferUploader.drawWithShader(bb.end());
                // anillos de choque en el piso (se abren y se desvanecen)
                StageFx.set(sh, "Kind", 8F);
                StageFx.begin(bb);
                for (int j = 0; j < 3; j++) {
                    float aj = a - j * 0.28F;
                    if (aj < 0F) continue;
                    double r = (1.5 + 6.5 * ease(aj / 2.6F)) * s;
                    float ra = g * Math.max(0F, 1F - aj / 2.8F) * 0.9F;
                    float[] cc = j == 1 ? WHITE : c;
                    double y = oy + 0.4 + j * 0.05;
                    StageFx.v(bb, ox - r, y, oz - r, cc, ra, -1F, -1F);
                    StageFx.v(bb, ox + r, y, oz - r, cc, ra, 1F, -1F);
                    StageFx.v(bb, ox + r, y, oz + r, cc, ra, 1F, 1F);
                    StageFx.v(bb, ox - r, y, oz + r, cc, ra, -1F, 1F);
                }
                BufferUploader.drawWithShader(bb.end());
                // esfera de luz + chispas
                StageFx.set(sh, "Kind", 6F);
                StageFx.begin(bb);
                double hy = oy + 0.95 * s;
                float ga = g * Math.max(0F, 1F - a / 1.4F);
                StageFx.glow(bb, ox, hy, oz, c, (1.0 + 4.5 * ease(a / 0.45F)) * s, ga);
                StageFx.glow(bb, ox, hy, oz, WHITE, (0.5 + 2.0 * ease(a / 0.3F)) * s, ga * 0.9F);
                for (int i = 0; i < 70; i++) {
                    float h1 = StageFx.hash(i * 3.7F + p.kind() * 11F), h2 = StageFx.hash(i * 5.3F + 1F + p.kind()), h3 = StageFx.hash(i * 9.1F + 2F);
                    double th = h1 * Math.PI * 2, ph = Math.acos(2 * h2 - 1), sp = (3.0 + 7.0 * h3) * s;
                    double dx = Math.sin(ph) * Math.cos(th), dy = Math.cos(ph), dz = Math.sin(ph) * Math.sin(th);
                    double t = a * 0.55;
                    float sa = g * Math.max(0F, 1F - a / (2.2F + 1.5F * h3));
                    StageFx.glow(bb, ox + dx * sp * t, hy + dy * sp * t - 6.0 * s * t * t, oz + dz * sp * t, i % 3 == 0 ? WHITE : c, (0.10 + 0.14 * h3) * s, sa);
                }
                BufferUploader.drawWithShader(bb.end());
            }
        }

        // ---------- 2) las tres luces
        if (F >= B0) {
            float[][] pos = new float[0][];
            for (int i = 0; i < 3; i++) {
                Pt from = null;
                for (Pt p : pts) if (p.kind() == i && (from == null || p.scale() < from.scale())) from = p;
                if (from == null) continue;
                float[] c = COL[i];
                double[] head = light(st, i, F, from);
                double hx = head[0] - camX, hy = head[1] - camY, hz = head[2] - camZ;
                double bx = from.x() - camX, by = from.y() - camY, bz = from.z() - camZ;
                float la = g * Math.min(1F, (F - B0) / 0.6F) * (F > X ? Math.max(0F, 1F - (F - X) / 0.4F) : 1F);
                // reflector: del escenario hasta la luz
                StageFx.set(sh, "Kind", 7F);
                StageFx.begin(bb);
                StageFx.strip(bb, bx, by, bz, hx, hy, hz, 0.9, 5.5, c, la * 0.42F, la * 0.30F);
                BufferUploader.drawWithShader(bb.end());
                // estela y cabeza
                StageFx.set(sh, "Kind", 6F);
                StageFx.begin(bb);
                for (int k = 1; k <= 16; k++) {
                    double[] tp = light(st, i, Math.max(B0, F - k * 0.10F), from);
                    float ta = la * (1F - k / 17F) * 0.75F;
                    StageFx.glow(bb, tp[0] - camX, tp[1] - camY, tp[2] - camZ, c, 9.0 * (1F - k / 22F), ta);
                }
                float pulse = 1F + 0.12F * (float) Math.sin(F * 9F + i);
                StageFx.glow(bb, hx, hy, hz, c, 24.0 * pulse, la);
                StageFx.glow(bb, hx, hy, hz, WHITE, 8.5 * pulse, la);
                BufferUploader.drawWithShader(bb.end());
            }
        }

        // ---------- 3) explosion gigante arriba del escenario
        if (F >= X) {
            float a = F - X;
            double zx = st.x + st.fx() * R * 0.03, zy = st.y + R * Math.sin(1.5F), zz = st.z + st.fz() * R * 0.03;
            double ox = zx - camX, oy = zy - camY, oz = zz - camZ;
            // anillos de choque enormes (siempre de frente a la camara)
            StageFx.set(sh, "Kind", 8F);
            StageFx.begin(bb);
            for (int j = 0; j < 4; j++) {
                float aj = a - j * 0.18F;
                if (aj < 0F) continue;
                double r = 20.0 + 330.0 * ease(aj / 3.2F);
                float ra = g * Math.max(0F, 1F - aj / 3.6F) * 0.8F;
                StageFx.glow(bb, ox, oy, oz, j == 3 ? WHITE : COL[j % 3], r, ra);
            }
            BufferUploader.drawWithShader(bb.end());
            // rayos que salen disparados
            StageFx.set(sh, "Kind", 7F);
            StageFx.begin(bb);
            for (int i = 0; i < 96; i++) {
                float h1 = StageFx.hash(i * 2.3F + 7F), h2 = StageFx.hash(i * 6.1F + 3F), h3 = StageFx.hash(i * 8.9F + 5F);
                double th = h1 * Math.PI * 2, ph = Math.acos(2 * h2 - 1);
                double dx = Math.sin(ph) * Math.cos(th), dy = Math.cos(ph), dz = Math.sin(ph) * Math.sin(th);
                double d0 = 30 + 20 * h3 + 210 * ease(a / 1.6F) * (0.6 + 0.4 * h3);
                double d1 = d0 + (25 + 90 * h3) * Math.max(0.2F, 1F - a / 3F);
                float sa = g * Math.max(0F, 1F - a / (2.4F + 1.2F * h3));
                StageFx.strip(bb, ox + dx * d0, oy + dy * d0, oz + dz * d0, ox + dx * d1, oy + dy * d1, oz + dz * d1, 0.8 + 1.4 * h3, 0.1, COL[i % 3], sa, sa * 0.2F);
            }
            BufferUploader.drawWithShader(bb.end());
            // nucleo blanco y lluvia de brillos
            StageFx.set(sh, "Kind", 6F);
            StageFx.begin(bb);
            float fl = g * Math.max(0F, 1F - a / 1.6F);
            StageFx.glow(bb, ox, oy, oz, WHITE, 40.0 + 190.0 * ease(a / 0.5F), fl * fl);
            for (int c = 0; c < 3; c++) StageFx.glow(bb, ox, oy, oz, COL[c], 30.0 + 100.0 * ease(a / 0.9F), fl * 0.8F);
            for (int i = 0; i < 220; i++) {
                float h1 = StageFx.hash(i * 1.9F + 13F), h2 = StageFx.hash(i * 4.7F + 17F), h3 = StageFx.hash(i * 7.3F + 19F);
                double th = h1 * Math.PI * 2, ph = Math.acos(2 * h2 - 1), sp = 60 + 140 * h3;
                double t = a * 0.6;
                double dx = Math.sin(ph) * Math.cos(th), dy = Math.cos(ph), dz = Math.sin(ph) * Math.sin(th);
                float sa = g * Math.max(0F, 1F - a / (3.2F + 1.8F * h3)) * (0.5F + 0.5F * (float) Math.sin(F * 14F + i));
                StageFx.glow(bb, ox + dx * sp * t, oy + dy * sp * t - 55.0 * t * t, oz + dz * sp * t, i % 4 == 0 ? WHITE : COL[i % 3], 1.6 + 2.2 * h3, sa);
            }
            BufferUploader.drawWithShader(bb.end());
        }
    }
}
