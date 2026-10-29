package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import com.annis.vfx.client.VfxTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/** Columna de luz suave que baja del cielo al centro, con un orbe que late donde aparece alguien. */
public final class LightPillar implements Fx {
    private final double cx, cy, cz;
    private final float start, end;

    public LightPillar(double cx, double cy, double cz, float start, float end) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.start = start;
        this.end = end;
    }

    @Override
    public void render(Draw d, ClientWorld world, float t) {
        float e = Draw.env(t, this.start, this.start + 1.4f, this.end - 1.6f, this.end);
        if (e <= 0) {
            return;
        }
        float lt = t - this.start;
        float breathe = 0.92f + 0.08f * MathHelper.sin(lt * 3.1f);
        d.beam(d.glow(VfxTextures.BEAM), this.cx, this.cy, this.cz, 0.9f * e * breathe, 9.0f, 0x7CFFB8, 0.45f * e);
        d.beam(d.glow(VfxTextures.BEAM), this.cx, this.cy, this.cz, 0.35f * e, 9.0f, 0xF2FFE8, 0.75f * e);
        d.billboard(d.glow(VfxTextures.GLOW), this.cx, this.cy + 1.0, this.cz, 1.6f * e * breathe, 0, 0xB8FFD0, 0.5f * e);
        // chispas girando alrededor de la columna
        for (int i = 0; i < 10; i++) {
            float a = lt * (0.9f + 0.07f * i) + i * 0.628f;
            float h = (lt * 0.5f + i * 0.37f) % 3.0f;
            float rr = 0.55f + 0.1f * MathHelper.sin(lt * 2 + i);
            d.billboard(d.glow(VfxTextures.SPARK), this.cx + MathHelper.cos(a) * rr, this.cy + 0.2 + h,
                    this.cz + MathHelper.sin(a) * rr, 0.07f, a, 0xFFFFFF, e * Draw.env(h, 0, 0.4f, 2.3f, 3.0f));
        }
    }

    @Override
    public boolean done(float t) {
        return t > this.end;
    }
}
