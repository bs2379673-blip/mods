package com.tunombre.infectados.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.infectados.mob.Witch;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pelo de la Witch: mechones planos (2D) con fisica de muelle. Cada mechon es una cadena de puntos que cuelga de la
 * cabeza; la gravedad y la inercia los mueven cuando la cabeza se mueve (llorar, levantarse, correr).
 */
@SuppressWarnings({"rawtypes"})
public class WitchHairLayer extends PatchedLayer {
    /** anclas en el hueso de la cabeza (x, y, z) en bloques + ancho del mechon */
    static final float[][] ROOTS;
    static final int SEG = 5;
    static final float LEN = 0.12F;

    static {
        java.util.List<float[]> l = new java.util.ArrayList<>();
        // flequillo que tapa la cara
        for (float x = -0.18F; x <= 0.19F; x += 0.07F) l.add(new float[]{x, 0.40F, 0.235F, 0.07F, 1.0F});
        // lados
        for (float y = 0.42F; y >= 0.28F; y -= 0.07F) {
            l.add(new float[]{0.235F, y, 0.08F, 0.08F, 1.2F});
            l.add(new float[]{-0.235F, y, 0.08F, 0.08F, 1.2F});
            l.add(new float[]{0.235F, y, -0.1F, 0.08F, 1.3F});
            l.add(new float[]{-0.235F, y, -0.1F, 0.08F, 1.3F});
        }
        // espalda
        for (float x = -0.2F; x <= 0.21F; x += 0.08F) l.add(new float[]{x, 0.38F, -0.235F, 0.09F, 1.5F});
        ROOTS = l.toArray(new float[0][]);
    }

    private static class Sim {
        final Vector3f[][] p = new Vector3f[ROOTS.length][SEG + 1];
        final Vector3f[][] o = new Vector3f[ROOTS.length][SEG + 1];
        float lastT = -1;
    }

    private final Map<LivingEntity, Sim> sims = new WeakHashMap<>();

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity e, RenderLayer layer, PoseStack ps, MultiBufferSource buf, int light, OpenMatrix4f[] poses, float a, float b, float c, float d) {
        if (!(e instanceof Witch) || poses.length <= 9) return;
        Matrix4f head = toJoml(poses[9]);
        Matrix4f rootM = toJoml(poses[0]);
        Vector3f up = rootM.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f grav = new Vector3f(up).mul(-0.012F);
        Sim s = sims.computeIfAbsent(e, k -> new Sim());
        float t = e.tickCount + net.minecraft.client.Minecraft.getInstance().getFrameTime();
        boolean init = s.lastT < 0 || t - s.lastT > 10 || t < s.lastT;
        int steps = init ? 30 : Math.min(4, Math.max(0, (int) ((t - s.lastT) * 2)));
        if (init || steps > 0) s.lastT = t;
        for (int i = 0; i < ROOTS.length; i++) {
            float[] r = ROOTS[i];
            Vector3f anchor = head.transformPosition(new Vector3f(r[0], r[1], r[2]));
            Vector3f out = head.transformDirection(new Vector3f(r[0], 0, r[2])).normalize();
            float len = LEN * r[4];
            if (init) {
                for (int k = 0; k <= SEG; k++) {
                    s.p[i][k] = new Vector3f(anchor).add(new Vector3f(up).mul(-len * k)).add(new Vector3f(out).mul(0.02F * k));
                    s.o[i][k] = new Vector3f(s.p[i][k]);
                }
            }
            for (int st = 0; st < steps; st++) {
                s.p[i][0].set(anchor);
                for (int k = 1; k <= SEG; k++) {      // verlet con amortiguacion
                    Vector3f cur = s.p[i][k], old = s.o[i][k];
                    Vector3f vel = new Vector3f(cur).sub(old).mul(0.9F);
                    old.set(cur);
                    cur.add(vel).add(grav).add(new Vector3f(out).mul(0.0015F));
                }
                for (int it = 0; it < 3; it++) {     // distancia fija entre puntos (muelle rigido)
                    for (int k = 1; k <= SEG; k++) {
                        Vector3f a0 = s.p[i][k - 1], b0 = s.p[i][k];
                        Vector3f dlt = new Vector3f(b0).sub(a0);
                        float dl = dlt.length();
                        if (dl < 1e-5F) continue;
                        float diff = (dl - len) / dl;
                        if (k == 1) b0.sub(dlt.mul(diff));
                        else {
                            Vector3f h = dlt.mul(diff * 0.5F);
                            a0.add(h);
                            b0.sub(h);
                        }
                    }
                    s.p[i][0].set(anchor);
                }
            }
            // dibujar la cinta (doble cara con la textura del mechon)
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InfVanillaRenderer.tex("witch_pelo")));
            Matrix4f m = ps.last().pose();
            Vector3f side = new Vector3f(up).cross(out).normalize().mul(r[3] / 2F);
            float u0 = (i % 4) * 0.25F, u1 = u0 + 0.25F;
            for (int k = 0; k < SEG; k++) {
                Vector3f p0 = s.p[i][k], p1 = s.p[i][k + 1];
                float v0 = k / (float) SEG, v1 = (k + 1) / (float) SEG;
                float w0 = 1F - 0.4F * v0, w1 = 1F - 0.4F * v1;
                vert(vc, m, new Vector3f(p0).sub(new Vector3f(side).mul(w0)), u0, v0, light, out);
                vert(vc, m, new Vector3f(p0).add(new Vector3f(side).mul(w0)), u1, v0, light, out);
                vert(vc, m, new Vector3f(p1).add(new Vector3f(side).mul(w1)), u1, v1, light, out);
                vert(vc, m, new Vector3f(p1).sub(new Vector3f(side).mul(w1)), u0, v1, light, out);
            }
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vector3f p, float u, float v, int light, Vector3f n) {
        Vector4f q = m.transform(new Vector4f(p.x, p.y, p.z, 1F));
        vc.vertex(q.x, q.y, q.z, 1F, 1F, 1F, 1F, u, v, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
    }

    private static Matrix4f toJoml(OpenMatrix4f o) {
        return OpenMatrix4f.exportToMojangMatrix(o);
    }
}
