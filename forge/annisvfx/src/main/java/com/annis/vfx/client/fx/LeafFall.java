package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import com.annis.vfx.client.VfxTextures;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Hojas que caen despacio, meciendose como un pendulo y girando. La hoja principal (si hay) cae
 * justo en el centro en "landAt" segundos y brilla; las demas caen alrededor y se apagan en el piso.
 */
public final class LeafFall implements Fx {
    private static final class Leaf {
        double x0, z0, y0, ground;
        float speed, amp, freq, phase, dirAng, spin, size, delay;
        boolean main;
        boolean groundReady;
    }

    private final List<Leaf> leaves = new ArrayList<>();
    private final double cx, cy, cz;
    private float end;

    /**
     * count = hojas de alrededor; spread = radio (bloques); main = hoja principal que cae en el centro
     * a los landAt segundos.
     */
    public LeafFall(double cx, double cy, double cz, int seed, int count, float spread, boolean main, float landAt) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        Random r = new Random(seed * 31L + 7);
        if (main) {
            Leaf l = new Leaf();
            l.main = true;
            l.amp = 0.7f;
            l.freq = 1.15f;
            l.phase = r.nextFloat() * 6.28f;
            l.dirAng = r.nextFloat() * 6.28f;
            l.spin = 0.35f;
            l.size = 0.30f;
            l.delay = 0;
            l.speed = 1.5f;
            l.y0 = cy + l.speed * landAt;
            // que al tocar el piso el vaiven lo deje justo en el centro
            float s = l.amp * Mth.sin(l.freq * landAt + l.phase);
            l.x0 = cx - s * Mth.cos(l.dirAng);
            l.z0 = cz - s * Mth.sin(l.dirAng);
            l.ground = cy;
            l.groundReady = true;
            this.leaves.add(l);
            this.end = Math.max(this.end, landAt + 4);
        }
        for (int i = 0; i < count; i++) {
            Leaf l = new Leaf();
            float ang = r.nextFloat() * 6.28f;
            float dist = (float) Math.sqrt(r.nextFloat()) * spread;
            l.x0 = cx + Math.cos(ang) * dist;
            l.z0 = cz + Math.sin(ang) * dist;
            l.speed = 1.0f + r.nextFloat() * 0.8f;
            l.y0 = cy + 6 + r.nextFloat() * 6;
            l.amp = 0.35f + r.nextFloat() * 0.6f;
            l.freq = 0.9f + r.nextFloat() * 0.6f;
            l.phase = r.nextFloat() * 6.28f;
            l.dirAng = r.nextFloat() * 6.28f;
            l.spin = (r.nextFloat() - 0.5f) * 1.6f;
            l.size = 0.16f + r.nextFloat() * 0.10f;
            l.delay = r.nextFloat() * Math.max(0.5f, landAt * 0.6f);
            l.ground = cy;
            this.leaves.add(l);
            this.end = Math.max(this.end, l.delay + (float) ((l.y0 - cy + 3) / l.speed) + 4);
        }
    }

    private void ground(Leaf l, ClientLevel w) {
        if (l.groundReady) {
            return;
        }
        l.groundReady = true;
        int top = w.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(l.x0), Mth.floor(l.z0));
        l.ground = Math.abs(top - this.cy) <= 3 ? top : this.cy;
    }

    @Override
    public void render(Draw d, ClientLevel world, float t) {
        for (Leaf l : this.leaves) {
            float lt = t - l.delay;
            if (lt < 0) {
                continue;
            }
            ground(l, world);
            float fallT = (float) ((l.y0 - l.ground) / l.speed);
            float tt = Math.min(lt, fallT);
            double y = l.y0 - l.speed * tt + 0.02;
            float sway = l.amp * Mth.sin(l.freq * tt + l.phase);
            double x = l.x0 + sway * Mth.cos(l.dirAng);
            double z = l.z0 + sway * Mth.sin(l.dirAng);
            boolean landed = lt >= fallT;
            float settle = landed ? Draw.smooth((lt - fallT) / 0.6f) : 0;
            // se mece: la inclinacion sigue a la velocidad del vaiven
            float tilt = 0.85f * Mth.cos(l.freq * tt + l.phase) * (1 - settle);
            float yaw = l.dirAng + l.spin * tt;
            float alpha;
            if (l.main) {
                alpha = 1 - Draw.smooth((lt - fallT - 1.2f) / 1.2f);
            } else {
                alpha = Math.min(1, lt / 0.6f) * (1 - Draw.smooth((lt - fallT - 1.5f) / 2.0f));
            }
            if (alpha <= 0.01f) {
                continue;
            }
            Quaternionf q = new Quaternionf().rotateY(yaw).rotateX(tilt).rotateZ(0.3f * tilt);
            Vector3f ax = q.transform(new Vector3f(l.size * 0.62f, 0, 0));
            Vector3f ay = q.transform(new Vector3f(0, 0, l.size));
            // (se pide el buffer en cada uso: cambiar de capa puede cerrar el anterior)
            d.quad(d.solid(VfxTextures.LEAF), x, y, z, ax, ay, 0xFFFFFF, alpha);
            if (l.main) {
                // halo magico alrededor de la hoja principal
                float pulse = 0.85f + 0.15f * Mth.sin(t * 5);
                float land = landed ? Draw.env(lt - fallT, 0, 0.15f, 0.5f, 1.6f) : 0;
                d.billboard(d.glow(VfxTextures.GLOW), x, y + 0.02, z, 0.55f * pulse + 1.2f * land, 0,
                        Draw.lerpColor(0x8CFFB4, 0xFFF4C0, land), 0.55f * alpha + 0.4f * land);
            }
        }
    }

    @Override
    public boolean done(float t) {
        return t > this.end;
    }
}
