package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import com.annis.vfx.client.VfxTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/** Lucecitas que suben del piso en espiral, titilando (verdes, blancas y doradas). */
public final class Motes implements Fx {
    private final double cx, cy, cz;
    private final float start, span;
    private final float[] born, ang, dist, speed, life, size, swirl;
    private final int[] color;
    private static final int[] PALETTE = {0xA8FFC8, 0xFFFFFF, 0xFFF0B0, 0x8CFFE0, 0xD8FFB0};

    public Motes(double cx, double cy, double cz, int seed, float start, float span, float radius, int count) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.start = start;
        this.span = span;
        Random r = new Random(seed * 13L + 11);
        this.born = new float[count];
        this.ang = new float[count];
        this.dist = new float[count];
        this.speed = new float[count];
        this.life = new float[count];
        this.size = new float[count];
        this.swirl = new float[count];
        this.color = new int[count];
        for (int i = 0; i < count; i++) {
            this.born[i] = r.nextFloat() * span;
            this.ang[i] = r.nextFloat() * 6.2832f;
            this.dist[i] = 0.3f + (float) Math.sqrt(r.nextFloat()) * radius;
            this.speed[i] = 0.25f + r.nextFloat() * 0.45f;
            this.life[i] = 2.5f + r.nextFloat() * 2.5f;
            this.size[i] = 0.035f + r.nextFloat() * 0.06f;
            this.swirl[i] = (r.nextBoolean() ? 1 : -1) * (0.3f + r.nextFloat() * 0.5f);
            this.color[i] = PALETTE[r.nextInt(PALETTE.length)];
        }
    }

    @Override
    public void render(Draw d, ClientWorld world, float t) {
        float lt = t - this.start;
        if (lt < 0) {
            return;
        }
        for (int i = 0; i < this.born.length; i++) {
            float a = lt - this.born[i];
            if (a < 0 || a > this.life[i]) {
                continue;
            }
            float k = a / this.life[i];
            float an = this.ang[i] + this.swirl[i] * a;
            float rr = this.dist[i] * (1 - 0.35f * k);
            double x = this.cx + MathHelper.cos(an) * rr;
            double z = this.cz + MathHelper.sin(an) * rr;
            double y = this.cy + 0.1 + this.speed[i] * a;
            float tw = 0.6f + 0.4f * MathHelper.sin(a * 9 + i);
            float alpha = Draw.env(k, 0, 0.15f, 0.7f, 1) * tw;
            d.billboard(d.glow(VfxTextures.SPARK), x, y, z, this.size[i], a, this.color[i], alpha);
            d.billboard(d.glow(VfxTextures.GLOW), x, y, z, this.size[i] * 3.5f, 0, this.color[i], 0.25f * alpha);
        }
    }

    @Override
    public boolean done(float t) {
        return t - this.start > this.span + 6;
    }
}
