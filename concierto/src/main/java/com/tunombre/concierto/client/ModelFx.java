package com.tunombre.concierto.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * efectos de shader pegados a los modelos (las bailarinas del mod dancers, sin depender de el): aura, halo en los pies, columna de luz
 * y el RAYO que sale de las manos. Se encuentra a las bailarinas por su tipo de entidad (dancers:dancer) y se lee su escala y modelo por reflexion.
 */
public final class ModelFx {
    private static final ResourceLocation DANCER = new ResourceLocation("dancers", "dancer");
    private static Method mScale, mModel;
    private static boolean reflectFailed;

    private static final class D {
        double x, y, z;
        float yaw, scale;
        String model = "";
    }

    // rayo: carga (esferas en las manos) y despues el rayo
    private static float beamT0 = -1F, beamCharge, beamDur;

    public static void startBeam(float songT, float dur, float charge) {
        beamT0 = songT;
        beamDur = dur;
        beamCharge = charge;
    }

    public static void clear() { beamT0 = -1F; }

    public static boolean beamActive(float songT) { return beamT0 >= 0F && songT < beamT0 + beamCharge + beamDur + 0.6F; }

    private static List<D> find(float partial, Stage stage) {
        List<D> out = new ArrayList<>();
        var mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        for (Entity e : mc.level.entitiesForRendering()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (!DANCER.equals(id)) continue;
            if (stage.set) {
                double dx = e.getX() - stage.x, dz = e.getZ() - stage.z;
                if (dx * dx + dz * dz > 160.0 * 160.0) continue;
            }
            D d = new D();
            d.x = e.getX() + (e.getX() - e.xOld) * 0;
            d.y = e.getY();
            d.z = e.getZ();
            d.yaw = e.getYRot();
            d.scale = 1F;
            try {
                if (!reflectFailed) {
                    if (mScale == null) {
                        mScale = e.getClass().getMethod("getDancerScale");
                        mModel = e.getClass().getMethod("getModelName");
                    }
                    d.scale = ((Number) mScale.invoke(e)).floatValue();
                    d.model = String.valueOf(mModel.invoke(e));
                }
            } catch (Throwable t) {
                reflectFailed = true;
            }
            out.add(d);
        }
        return out;
    }

    private static float[] colorOf(String model) {
        String m = model.toLowerCase();
        if (m.contains("teto")) return new float[]{0.95F, 0.22F, 0.32F};
        if (m.contains("neru")) return new float[]{1F, 0.85F, 0.12F};
        return new float[]{0.22F, 0.92F, 0.86F};
    }

    private static void v(BufferBuilder bb, double x, double y, double z, float[] c, float a, float u, float w) {
        bb.vertex(x, y, z).color(c[0], c[1], c[2], a).uv(u, w).endVertex();
    }

    /** cinta que mira a la camara; coordenadas relativas a la camara. uv.y = metros / 10 */
    private static void strip(BufferBuilder bb, double ox, double oy, double oz, double dx, double dy, double dz, double len, double w0, double w1, float[] c, float a0, float a1, double v0) {
        double mx = ox + dx * len * 0.5, my = oy + dy * len * 0.5, mz = oz + dz * len * 0.5;
        double vx = -mx, vy = -my, vz = -mz;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx /= l; sy /= l; sz /= l;
        double ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
        v(bb, ox - sx * w0, oy - sy * w0, oz - sz * w0, c, a0, -1F, (float) v0);
        v(bb, ox + sx * w0, oy + sy * w0, oz + sz * w0, c, a0, 1F, (float) v0);
        v(bb, ex + sx * w1, ey + sy * w1, ez + sz * w1, c, a1, 1F, (float) (v0 + len / 10.0));
        v(bb, ex - sx * w1, ey - sy * w1, ez - sz * w1, c, a1, -1F, (float) (v0 + len / 10.0));
    }

    private static void glow(BufferBuilder bb, double hx, double hy, double hz, float[] c, double half, float a) {
        double dist = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (dist < 1e-3) return;
        double nx = -hx / dist, ny = -hy / dist, nz = -hz / dist;
        double rxv = -nz, rzv = nx;
        double rl = Math.sqrt(rxv * rxv + rzv * rzv);
        if (rl < 1e-6) return;
        rxv /= rl; rzv /= rl;
        double uxv = ny * rzv, uyv = nz * rxv - nx * rzv, uzv = -ny * rxv;
        v(bb, hx - rxv * half - uxv * half, hy - uyv * half, hz - rzv * half - uzv * half, c, a, -1F, -1F);
        v(bb, hx + rxv * half - uxv * half, hy - uyv * half, hz + rzv * half - uzv * half, c, a, 1F, -1F);
        v(bb, hx + rxv * half + uxv * half, hy + uyv * half, hz + rzv * half + uzv * half, c, a, 1F, 1F);
        v(bb, hx - rxv * half + uxv * half, hy + uyv * half, hz - rzv * half + uzv * half, c, a, -1F, 1F);
    }

    private static float hash(float n) { float s = (float) Math.sin(n * 127.1F) * 43758.5453F; return s - (float) Math.floor(s); }

    private static void set(ShaderInstance sh, String n, float v) { sh.safeGetUniform(n).set(v); }

    private static void begin(BufferBuilder bb) { bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX); }

    /** dibuja todo con el shader aditivo. aura=false deja solo el rayo (por ejemplo durante un apagon) */
    public static void draw(ShaderInstance sh, double camX, double camY, double camZ, float partial, Stage stage, float songT, float level, float kick, boolean aura) {
        boolean beam = beamActive(songT);
        if (!aura && !beam) return;
        List<D> ds = find(partial, stage);
        if (ds.isEmpty() && !beam) return;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // ---- aura: halo en los pies, columna de luz suave y brillo detras del cuerpo
        if (aura && !ds.isEmpty()) {
            set(sh, "Kind", 8F);
            set(sh, "Alpha", 1F);
            begin(bb);
            for (D d : ds) {
                float[] c = colorOf(d.model);
                double R = 1.5 * d.scale * (1.0 + 0.10 * kick);
                double ox = d.x - camX, oy = d.y - camY + 0.04, oz = d.z - camZ;
                float a = 0.55F + 0.4F * level;
                v(bb, ox - R, oy, oz - R, c, a, -1F, -1F);
                v(bb, ox + R, oy, oz - R, c, a, 1F, -1F);
                v(bb, ox + R, oy, oz + R, c, a, 1F, 1F);
                v(bb, ox - R, oy, oz + R, c, a, -1F, 1F);
            }
            BufferUploader.drawWithShader(bb.end());

            set(sh, "Kind", 7F);
            begin(bb);
            for (D d : ds) {
                float[] c = colorOf(d.model);
                double ox = d.x - camX, oy = d.y - camY, oz = d.z - camZ;
                float a = 0.22F + 0.35F * level + 0.2F * kick;
                strip(bb, ox, oy, oz, 0, 1, 0, 60.0 * Math.max(1F, d.scale / 2F), 0.30 * d.scale, 0.55 * d.scale, c, a, a * 0.1F, 0);
            }
            BufferUploader.drawWithShader(bb.end());

            set(sh, "Kind", 6F);
            set(sh, "Prog", songT);
            begin(bb);
            for (D d : ds) {
                float[] c = colorOf(d.model);
                glow(bb, d.x - camX, d.y - camY + 0.95 * d.scale, d.z - camZ, c, 1.15 * d.scale * (1.0 + 0.12 * kick), 0.16F + 0.22F * level);
                // chispas que suben alrededor del cuerpo
                for (int i = 0; i < 10; i++) {
                    float ph = (songT * (0.35F + 0.1F * hash(i)) + hash(i * 3.7F)) % 1F;
                    double ang = hash(i * 5.3F) * Math.PI * 2 + songT * 0.8, rad = (0.55 + 0.35 * hash(i * 9.1F)) * d.scale;
                    glow(bb, d.x - camX + Math.cos(ang) * rad, d.y - camY + ph * 2.2 * d.scale, d.z - camZ + Math.sin(ang) * rad, c, 0.07 * d.scale + 0.03, (1F - ph) * 0.9F);
                }
            }
            BufferUploader.drawWithShader(bb.end());
        }

        if (!beam) return;

        // ---- rayo de las manos
        D who = null;
        double best = 1e18;
        for (D d : ds) {
            if (!d.model.toLowerCase().contains("miku") && !ds.isEmpty() && ds.size() > 1) continue;
            double dx = d.x - stage.x, dz = d.z - stage.z, dd = dx * dx + dz * dz;
            if (!stage.set) dd = 0;
            if (dd < best) { best = dd; who = d; }
        }
        double fxv, fzv, lx, lz, px, py, pz, sc;
        if (who != null) {
            double yaw = Math.toRadians(who.yaw);
            fxv = -Math.sin(yaw); fzv = Math.cos(yaw);
            lx = Math.cos(yaw); lz = Math.sin(yaw);
            px = who.x; py = who.y; pz = who.z; sc = who.scale;
        } else {
            fxv = stage.fx(); fzv = stage.fz(); lx = stage.rx(); lz = stage.rz();
            px = stage.x; py = stage.y; pz = stage.z; sc = stage.scale;
        }
        double hy = py + 1.05 * sc;
        double[] hl = {px + lx * 0.34 * sc + fxv * 0.32 * sc, hy, pz + lz * 0.34 * sc + fzv * 0.32 * sc};
        double[] hr = {px - lx * 0.34 * sc + fxv * 0.32 * sc, hy, pz - lz * 0.34 * sc + fzv * 0.32 * sc};
        double[] mid = {(hl[0] + hr[0]) / 2 + fxv * 0.9 * sc, hy + 0.05 * sc, (hl[2] + hr[2]) / 2 + fzv * 0.9 * sc};
        float age = songT - beamT0;
        float[] cy = {0.25F, 0.95F, 0.9F}, pk = {1F, 0.45F, 0.75F}, wh = {1F, 1F, 1F};

        set(sh, "Prog", songT);
        if (age < beamCharge) {
            // carga: dos esferas que crecen en las manos y luces que se juntan hacia ellas
            float u = Math.max(0F, age / beamCharge);
            set(sh, "Kind", 6F);
            begin(bb);
            double sz = (0.25 + 1.1 * u * u) * sc * (1.0 + 0.2 * Math.sin(age * 30.0));
            for (double[] h : new double[][]{hl, hr}) {
                glow(bb, h[0] - camX, h[1] - camY, h[2] - camZ, cy, sz, 1F);
                glow(bb, h[0] - camX, h[1] - camY, h[2] - camZ, wh, sz * 0.45, 1F);
            }
            BufferUploader.drawWithShader(bb.end());
            set(sh, "Kind", 7F);
            set(sh, "Alpha", 1F);
            begin(bb);
            for (int i = 0; i < 26; i++) {
                float ph = (age * (1.2F + hash(i)) + hash(i * 7.7F)) % 1F;
                double ang = hash(i * 2.9F) * Math.PI * 2, el = (hash(i * 4.1F) - 0.4) * 1.2;
                double dist = (1F - ph) * 7.0 * sc;
                double[] h = (i & 1) == 0 ? hl : hr;
                double sx0 = h[0] + Math.cos(ang) * Math.cos(el) * dist, sy0 = h[1] + Math.sin(el) * dist, sz0 = h[2] + Math.sin(ang) * Math.cos(el) * dist;
                double ex = h[0] - sx0, ey = h[1] - sy0, ez = h[2] - sz0, l = Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (l < 0.05) continue;
                strip(bb, sx0 - camX, sy0 - camY, sz0 - camZ, ex / l, ey / l, ez / l, Math.min(l, 2.2 * sc), 0.05 * sc + 0.02, 0.01, (i % 3 == 0) ? pk : cy, 0.9F * ph, 0.9F, 0);
            }
            BufferUploader.drawWithShader(bb.end());
        } else {
            float ba = age - beamCharge;
            float fade = Math.min(1F, ba / 0.25F) * Math.min(1F, Math.max(0F, (beamDur + 0.5F - ba) / 0.5F));
            float throb = 1F + 0.12F * kick + 0.05F * (float) Math.sin(ba * 40.0);
            double el = Math.toRadians(7.0);
            double dx = fxv * Math.cos(el), dy = Math.sin(el), dz = fzv * Math.cos(el);
            double len = 240.0;
            double ox = mid[0] - camX, oy = mid[1] - camY, oz = mid[2] - camZ;
            // las manos se unen en el rayo
            set(sh, "Kind", 9F);
            set(sh, "Alpha", fade);
            begin(bb);
            for (double[] h : new double[][]{hl, hr}) {
                double ex = mid[0] - h[0], ey = mid[1] - h[1], ez = mid[2] - h[2], l = Math.sqrt(ex * ex + ey * ey + ez * ez);
                strip(bb, h[0] - camX, h[1] - camY, h[2] - camZ, ex / l, ey / l, ez / l, l, 0.10 * sc, 0.28 * sc, cy, 0.9F, 1F, 0);
            }
            // capas del rayo: halo rosado ancho, cuerpo celeste y nucleo blanco
            strip(bb, ox, oy, oz, dx, dy, dz, len, 0.55 * sc * throb, 2.6 * sc * throb, pk, 0.55F, 0.25F, 0);
            strip(bb, ox, oy, oz, dx, dy, dz, len, 0.38 * sc * throb, 1.7 * sc * throb, cy, 0.9F, 0.55F, 0);
            strip(bb, ox, oy, oz, dx, dy, dz, len, 0.16 * sc * throb, 0.6 * sc * throb, wh, 1F, 0.8F, 0);
            BufferUploader.drawWithShader(bb.end());

            set(sh, "Kind", 6F);
            set(sh, "Alpha", fade);
            begin(bb);
            glow(bb, ox, oy, oz, cy, 2.6 * sc * throb, 1F);
            glow(bb, ox, oy, oz, wh, 1.2 * sc * throb, 1F);
            for (double[] h : new double[][]{hl, hr}) glow(bb, h[0] - camX, h[1] - camY, h[2] - camZ, wh, 0.55 * sc, 0.9F);
            // anillos de energia que viajan por el rayo
            for (int i = 0; i < 14; i++) {
                double dd = ((ba * 70.0 + i * 17.0) % 240.0);
                double gsz = (0.5 + dd * 0.012) * sc;
                glow(bb, ox + dx * dd, oy + dy * dd, oz + dz * dd, (i & 1) == 0 ? cy : pk, gsz, 0.7F * (float) (1.0 - dd / 260.0));
            }
            BufferUploader.drawWithShader(bb.end());
        }
    }
}
