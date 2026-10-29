package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import com.annis.vfx.client.VfxTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Flores que brotan alrededor del centro, de adentro hacia afuera: crece el tallo, salen dos hojitas
 * y el capullo se abre con un pequeno rebote. Se quedan meciendose con el viento.
 */
public final class Flowers implements Fx {
    private static final int[] PETALS = {0xFFB7D5, 0xFFFFFF, 0xD7B8FF, 0xA8E6FF, 0xFFE9A8, 0xFFC8E6};

    private final double cx, cy, cz;
    private final float start, persist;
    private final float[] fx, fz, delay, height, petalLen, openMax, yaw, sway;
    private final int[] petals, color;
    private final double[] ground;
    private final boolean[] groundReady;

    public Flowers(double cx, double cy, double cz, int seed, float start, float radius, int count, float persist) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.start = start;
        this.persist = persist;
        Random r = new Random(seed * 7L + 5);
        this.fx = new float[count];
        this.fz = new float[count];
        this.delay = new float[count];
        this.height = new float[count];
        this.petalLen = new float[count];
        this.openMax = new float[count];
        this.yaw = new float[count];
        this.sway = new float[count];
        this.petals = new int[count];
        this.color = new int[count];
        this.ground = new double[count];
        this.groundReady = new boolean[count];
        for (int i = 0; i < count; i++) {
            float a = (i + r.nextFloat() * 0.8f) / count * 6.2832f;
            float dist = 0.8f + (float) Math.sqrt(r.nextFloat()) * Math.max(0.2f, radius - 0.8f);
            this.fx[i] = MathHelper.cos(a) * dist;
            this.fz[i] = MathHelper.sin(a) * dist;
            // brotan de adentro hacia afuera, siguiendo la luz
            this.delay[i] = dist / Math.max(1, radius) * 2.4f + r.nextFloat() * 0.6f;
            this.height[i] = 0.16f + r.nextFloat() * 0.22f;
            this.petalLen[i] = 0.10f + r.nextFloat() * 0.07f;
            this.openMax[i] = 1.05f + r.nextFloat() * 0.35f;
            this.yaw[i] = r.nextFloat() * 6.2832f;
            this.sway[i] = r.nextFloat() * 6.2832f;
            this.petals[i] = r.nextBoolean() ? 5 : 6;
            this.color[i] = PETALS[r.nextInt(PETALS.length)];
        }
    }

    private static float backOut(float x) {
        x = Draw.clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
    }

    @Override
    public void render(Draw d, ClientWorld world, float t) {
        float lt = t - this.start;
        if (lt < 0) {
            return;
        }
        float fade = 1 - Draw.smooth((lt - this.persist) / 2.0f);
        if (fade <= 0) {
            return;
        }
        for (int i = 0; i < this.fx.length; i++) {
            float a = lt - this.delay[i];
            if (a < 0) {
                continue;
            }
            if (!this.groundReady[i]) {
                this.groundReady[i] = true;
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(this.cx + this.fx[i]),
                        MathHelper.floor(this.cz + this.fz[i]));
                this.ground[i] = Math.abs(top - this.cy) <= 2 ? top : this.cy;
            }
            double bx = this.cx + this.fx[i], by = this.ground[i], bz = this.cz + this.fz[i];
            float grow = Draw.smooth(a / 0.8f) * fade;
            float h = this.height[i] * grow;
            // el viento lo mece un poco
            float wind = 0.08f * MathHelper.sin(t * 1.3f + this.sway[i]) * grow;
            Vector3f up = new Vector3f(wind, 1, wind * 0.5f).normalize();
            // tallo: dos planos cruzados
            for (int k = 0; k < 2; k++) {
                Vector3f ax = new Vector3f(0.015f, 0, 0).rotateY(this.yaw[i] + k * 1.5708f);
                Vector3f ay = new Vector3f(up).mul(h / 2);
                d.quad(d.solid(VfxTextures.WHITE), bx + ay.x, by + ay.y, bz + ay.z, ax, ay, 0x3E9E4E, 1);
            }
            // dos hojitas en la base del tallo
            float leafGrow = Draw.smooth((a - 0.2f) / 0.6f) * fade;
            for (int k = 0; k < 2; k++) {
                float ly = this.yaw[i] + k * 3.1416f;
                Vector3f dir = new Vector3f(MathHelper.cos(ly), 0.55f, MathHelper.sin(ly)).normalize();
                Vector3f ay = new Vector3f(dir).mul(0.07f * leafGrow);
                Vector3f ax = new Vector3f(-MathHelper.sin(ly), 0, MathHelper.cos(ly)).mul(0.04f * leafGrow);
                d.quad(d.solid(VfxTextures.LEAF), bx + ay.x, by + 0.03 + ay.y, bz + ay.z, ax, ay, 0xFFFFFF, leafGrow);
            }
            // capullo que se abre
            float bloom = Draw.smooth((a - 0.5f) / 0.9f);
            if (bloom <= 0) {
                continue;
            }
            float open = 0.15f + (this.openMax[i] - 0.15f) * backOut((a - 0.7f) / 1.3f);
            float len = this.petalLen[i] * (0.4f + 0.6f * bloom) * fade;
            double tx = bx + up.x * h, ty = by + up.y * h, tz = bz + up.z * h;
            int n = this.petals[i];
            for (int p = 0; p < n; p++) {
                float phi = this.yaw[i] + p * 6.2832f / n;
                Vector3f radial = new Vector3f(MathHelper.cos(phi), 0, MathHelper.sin(phi));
                Vector3f lenDir = new Vector3f(up).mul(MathHelper.cos(open)).add(new Vector3f(radial).mul(MathHelper.sin(open))).normalize();
                Vector3f wDir = new Vector3f(-MathHelper.sin(phi), 0, MathHelper.cos(phi));
                Vector3f ay = new Vector3f(lenDir).mul(len / 2);
                Vector3f ax = new Vector3f(wDir).mul(len * 0.32f);
                d.quad(d.solid(VfxTextures.PETAL), tx + ay.x, ty + ay.y, tz + ay.z, ax, ay, this.color[i], bloom);
            }
            // centro y un brillito al abrirse
            d.billboard(d.glow(VfxTextures.GLOW), tx, ty + 0.01, tz, 0.05f + 0.03f * bloom, 0, 0xFFE27A, 0.9f * bloom);
            float pop = Draw.env(a, 0.6f, 0.9f, 1.1f, 2.2f);
            d.billboard(d.glow(VfxTextures.GLOW), tx, ty, tz, 0.35f, 0, this.color[i], 0.45f * pop + 0.12f * bloom * fade);
        }
    }

    @Override
    public boolean done(float t) {
        return t - this.start > this.persist + 2.2f;
    }
}
