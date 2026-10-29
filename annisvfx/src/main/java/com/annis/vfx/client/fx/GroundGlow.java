package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import com.annis.vfx.client.VfxTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * El piso se enciende: dos anillos de luz que se abren desde el centro, un resplandor suave que late,
 * y manchas de luz que se van prendiendo cuando el anillo pasa por encima (verdes, turquesa y algo
 * dorado: no un verde plano).
 */
public final class GroundGlow implements Fx {
    private final double cx, cy, cz;
    private final float start, radius, hold;
    private final float[] spotX, spotZ, spotR, spotS;
    private final int[] spotC;
    private static final int[] PALETTE = {0x6CFF9E, 0x8CFFC8, 0x4FE3B0, 0xC8FF9A, 0xFFF0A8, 0x9AF0FF};

    /** hold = segundos que se queda encendido antes de apagarse. */
    public GroundGlow(double cx, double cy, double cz, int seed, float start, float radius, float hold) {
        this.cx = cx;
        this.cy = cy + 0.03;
        this.cz = cz;
        this.start = start;
        this.radius = radius;
        this.hold = hold;
        Random r = new Random(seed * 17L + 3);
        int n = (int) (radius * radius * 1.4f) + 12;
        this.spotX = new float[n];
        this.spotZ = new float[n];
        this.spotR = new float[n];
        this.spotS = new float[n];
        this.spotC = new int[n];
        for (int i = 0; i < n; i++) {
            float a = r.nextFloat() * 6.2832f;
            float dist = (float) Math.sqrt(r.nextFloat()) * radius;
            this.spotX[i] = MathHelper.cos(a) * dist;
            this.spotZ[i] = MathHelper.sin(a) * dist;
            this.spotR[i] = dist;
            this.spotS[i] = 0.25f + r.nextFloat() * 0.45f;
            this.spotC[i] = PALETTE[r.nextInt(PALETTE.length)];
        }
    }

    private static float easeOut(float x) {
        x = Draw.clamp01(x);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    @Override
    public void render(Draw d, ClientWorld world, float t) {
        float lt = t - this.start;
        if (lt < 0) {
            return;
        }
        float life = Draw.env(lt, 0, 0.6f, this.hold, this.hold + 3.0f);
        // anillos
        for (int i = 0; i < 2; i++) {
            float rt = (lt - i * 0.7f) / 2.8f;
            if (rt <= 0 || rt >= 1) {
                continue;
            }
            float rr = this.radius * easeOut(rt) + 0.2f;
            float a = (float) Math.pow(1 - rt, 1.3) * (i == 0 ? 0.95f : 0.6f);
            d.flat(d.glow(VfxTextures.RING), this.cx, this.cy + 0.01 * i, this.cz, rr, 0,
                    Draw.lerpColor(0xFFF4C4, 0x7CFFB0, rt * 2), a);
        }
        // resplandor del centro, que late despacio
        float pulse = 0.9f + 0.1f * MathHelper.sin(lt * 2.2f);
        d.flat(d.glow(VfxTextures.GLOW), this.cx, this.cy, this.cz, 2.6f * pulse, 0, 0x6CFFA8, 0.50f * life);
        d.flat(d.glow(VfxTextures.GLOW), this.cx, this.cy + 0.005, this.cz, 1.0f * pulse, 0, 0xE6FFD8,
                0.55f * life + 0.35f * Draw.env(lt, 0, 0.1f, 0.3f, 1.2f));
        // manchas que se prenden cuando pasa el anillo
        float front = this.radius * easeOut(lt / 2.8f);
        for (int i = 0; i < this.spotX.length; i++) {
            if (this.spotR[i] > front) {
                continue;
            }
            float lit = Draw.smooth((front - this.spotR[i]) / 0.8f);
            float tw = 0.75f + 0.25f * MathHelper.sin(lt * (1.3f + (i % 5) * 0.27f) + i);
            d.flat(d.glow(VfxTextures.GLOW), this.cx + this.spotX[i], this.cy + 0.002 * (i % 3),
                    this.cz + this.spotZ[i], this.spotS[i], i, this.spotC[i], 0.42f * lit * tw * life);
        }
    }

    @Override
    public boolean done(float t) {
        return t - this.start > this.hold + 3.2f;
    }
}
