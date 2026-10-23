package com.tunombre.jefes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tunombre.jefes.JefeEntity;
import com.tunombre.jefes.Jefes;
import com.tunombre.jefes.core.Act;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Slenderman: cuerpo rigido (malla fija con su postura de reposo) elevado del suelo y sostenido por tentaculos.
 * Cuatro tentaculos hacen de patas (dan pasos solos al moverse), cuatro quedan en guardia moviendose despacio y uno se lanza hacia adelante al latiguear.
 */
public final class SlenderRender {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static float[] pos;
    private static byte[] nrm, typ;
    private static int n;
    private static boolean loaded, broken;
    private static final Map<JefeEntity, St> STATES = new WeakHashMap<>();

    private static final class St {
        final double[][] tip = new double[4][3], from = new double[4][3], to = new double[4][3];
        final float[] prog = new float[4];
        final boolean[] stepping = new boolean[4];
        boolean init;
        double last;
    }

    private SlenderRender() { }

    private static void load() {
        loaded = true;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(SlenderRender.class.getResourceAsStream("/assets/jefes/slender/mesh.bin")))) {
            n = in.readInt();
            in.readInt();
            pos = new float[n * 3];
            for (int i = 0; i < pos.length; i++) pos[i] = in.readFloat();
            nrm = new byte[n * 3];
            in.readFully(nrm);
            typ = new byte[n];
            in.readFully(typ);
        } catch (Exception e) {
            broken = true;
            Jefes.LOG.error("[jefes] no se pudo cargar la malla de Slenderman", e);
        }
    }

    private static double[] off(float yaw, double lx, double ly, double lz) {
        double c = Math.cos(Math.toRadians(yaw)), s = Math.sin(Math.toRadians(yaw));
        return new double[]{lx * c - lz * s, ly, lx * s + lz * c};
    }

    private static final double[][] REST = {{1.9, 0.6}, {-1.9, 0.6}, {1.6, -1.7}, {-1.6, -1.7}};
    private static final double[][] LEG_ANCHOR = {{0.30, 1.55, -0.35}, {-0.30, 1.55, -0.35}, {0.42, 1.25, -0.40}, {-0.42, 1.25, -0.40}};
    private static final double[][] GUARD_ANCHOR = {{0.28, 2.45, -0.38}, {-0.28, 2.45, -0.38}, {0.45, 2.05, -0.42}, {-0.45, 2.05, -0.42}};
    private static final double[][] GUARD_DIR = {{0.55, 0.9, -0.35}, {-0.55, 0.9, -0.35}, {0.9, 0.55, -0.45}, {-0.9, 0.55, -0.45}};

    public static void render(JefeEntity e, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!loaded) load();
        if (broken) return;
        St st = STATES.computeIfAbsent(e, k -> new St());
        Vec3 pp = e.getPosition(pt);
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        double time = e.tickCount + pt;
        double dt = st.last == 0 ? 1 : Mth.clamp(time - st.last, 0, 3);
        st.last = time;
        double sp = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        float lift = (float) (1.62 + 0.05 * Math.sin(time * 0.045) - Math.min(sp * 2.5, 0.12));

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));

        // ---- cuerpo ----
        ps.pushPose();
        ps.translate(0, lift, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.min(sp * 70, 9) + (float) Math.sin(time * 0.03) * 0.8F));
        Matrix4f pm = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        for (int i = 0; i < n; i++) {
            float r, g, b;
            switch (typ[i]) {
                case 1 -> { r = 0.80F; g = 0.78F; b = 0.74F; }
                case 2 -> { r = 0.80F; g = 0.80F; b = 0.78F; }
                default -> { r = 0.03F; g = 0.03F; b = 0.035F; }
            }
            vc.vertex(pm, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).color(r, g, b, 1F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(nm, nrm[i * 3] / 127F, nrm[i * 3 + 1] / 127F, nrm[i * 3 + 2] / 127F).endVertex();
            if (i % 3 == 2) {   // la malla es de triangulos: se repite el ultimo vertice para formar cuadrilateros degenerados
                vc.vertex(pm, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).color(r, g, b, 1F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light).normal(nm, nrm[i * 3] / 127F, nrm[i * 3 + 1] / 127F, nrm[i * 3 + 2] / 127F).endVertex();
            }
        }
        ps.popPose();

        // ---- tentaculos (coordenadas relativas a la entidad, ejes del mundo) ----
        Matrix4f pm2 = ps.last().pose();
        Matrix3f nm2 = ps.last().normal();
        // patas
        if (!st.init) {
            for (int i = 0; i < 4; i++) {
                double[] o = off(yaw, REST[i][0], 0, REST[i][1]);
                st.tip[i][0] = pp.x + o[0]; st.tip[i][1] = groundY(e, pp.x + o[0], pp.y, pp.z + o[2]); st.tip[i][2] = pp.z + o[2];
            }
            st.init = true;
        }
        int stepping = 0;
        for (int i = 0; i < 4; i++) if (st.stepping[i]) stepping++;
        for (int i = 0; i < 4; i++) {
            double[] o = off(yaw, REST[i][0], 0, REST[i][1]);
            double dx = pp.x + o[0] - st.tip[i][0], dz = pp.z + o[2] - st.tip[i][2];
            double d2 = dx * dx + dz * dz;
            if (d2 > 60) {   // se teletransporto: las patas aparecen debajo del cuerpo
                st.tip[i][0] = pp.x + o[0]; st.tip[i][2] = pp.z + o[2]; st.tip[i][1] = groundY(e, st.tip[i][0], pp.y, st.tip[i][2]); st.stepping[i] = false;
                continue;
            }
            if (st.stepping[i]) {
                st.prog[i] += (float) (dt * 0.16);
                float k = Math.min(1F, st.prog[i]);
                float ease = k * k * (3 - 2 * k);
                for (int c = 0; c < 3; c++) st.tip[i][c] = st.from[i][c] + (st.to[i][c] - st.from[i][c]) * ease;
                st.tip[i][1] += Math.sin(Math.PI * k) * 0.85;
                if (st.prog[i] >= 1F) { st.stepping[i] = false; st.tip[i][1] = st.to[i][1]; }
            } else if (stepping < 2 && d2 > 1.2 + sp * 4 && !st.stepping[(i + 1) % 2 + (i / 2) * 2]) {
                st.stepping[i] = true; stepping++;
                st.prog[i] = 0;
                double[] f = off(yaw, 0, 0, 1);
                double tx = pp.x + o[0] + f[0] * sp * 9, tz = pp.z + o[2] + f[2] * sp * 9;
                System.arraycopy(st.tip[i], 0, st.from[i], 0, 3);
                st.to[i][0] = tx; st.to[i][2] = tz; st.to[i][1] = groundY(e, tx, pp.y, tz);
            }
        }
        for (int i = 0; i < 4; i++) {
            double[] a = off(yaw, LEG_ANCHOR[i][0], lift + LEG_ANCHOR[i][1] - 0.25, LEG_ANCHOR[i][2]);
            double ax = a[0], ay = a[1], az = a[2];
            double tx = st.tip[i][0] - pp.x, ty = st.tip[i][1] - pp.y, tz = st.tip[i][2] - pp.z;
            double side = LEG_ANCHOR[i][0] > 0 ? 1 : -1;
            double[] k = off(yaw, side * 1.0, 0, 0);
            double kx = (ax + tx) / 2 + k[0], ky = Math.max(ay, ty) + 0.7, kz = (az + tz) / 2 + k[2];
            double[][] pts = new double[16][3];
            for (int s = 0; s < 16; s++) {
                double u = s / 15.0, w = 1 - u;
                pts[s][0] = w * w * ax + 2 * w * u * kx + u * u * tx;
                pts[s][1] = w * w * ay + 2 * w * u * ky + u * u * ty;
                pts[s][2] = w * w * az + 2 * w * u * kz + u * u * tz;
            }
            tube(vc, pm2, nm2, light, pts, 0.15F, 0.045F);
        }
        // guardias
        Act act = e.getAct();
        float lash = 0F;
        int lashIdx = act == Act.ATTACK_L ? 0 : act == Act.ATTACK_R ? 1 : -1;
        if (lashIdx >= 0) {
            float t = e.actTime(pt) / Act.ATTACK_L.ticks;
            lash = t < 0.45F ? t / 0.45F : Math.max(0F, (1F - t) / 0.55F);
            lash = lash * lash * (3 - 2 * lash);
        }
        for (int i = 0; i < 4; i++) {
            double[] a = off(yaw, GUARD_ANCHOR[i][0], lift + GUARD_ANCHOR[i][1] - 0.2, GUARD_ANCHOR[i][2]);
            double[] d = off(yaw, GUARD_DIR[i][0], GUARD_DIR[i][1], GUARD_DIR[i][2]);
            double len = 3.4 + i * 0.35, ph = i * 1.7;
            double[][] pts = new double[18][3];
            double[] fw = off(yaw, 0, 0, 1);
            double[] rt = off(yaw, 1, 0, 0);
            for (int s = 0; s < 18; s++) {
                double u = s / 17.0;
                double lat = Math.sin(time * 0.06 + ph + u * 3.1) * 0.38 * u, vert = Math.cos(time * 0.045 + ph * 1.3 + u * 2.4) * 0.28 * u;
                double gx = a[0] + d[0] * len * u + rt[0] * lat, gy = a[1] + d[1] * len * u + vert + u * u * 0.9, gz = a[2] + d[2] * len * u + rt[2] * lat;
                if (i == lashIdx && lash > 0F) {
                    double wx = a[0] + fw[0] * 5.4 * u + rt[0] * (lashIdx == 0 ? 1 : -1) * 0.8 * Math.sin(u * Math.PI), wy = a[1] - 0.6 * u + Math.sin(u * Math.PI) * 0.9 - u * u * 0.9, wz = a[2] + fw[2] * 5.4 * u + rt[2] * (lashIdx == 0 ? 1 : -1) * 0.8 * Math.sin(u * Math.PI);
                    gx += (wx - gx) * lash; gy += (wy - gy) * lash; gz += (wz - gz) * lash;
                }
                pts[s][0] = gx; pts[s][1] = gy; pts[s][2] = gz;
            }
            tube(vc, pm2, nm2, light, pts, 0.12F, 0.025F);
        }
    }

    private static double groundY(JefeEntity e, double x, double y, double z) {
        var hit = e.level().clip(new ClipContext(new Vec3(x, y + 2.0, z), new Vec3(x, y - 5.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        return hit.getType() == HitResult.Type.MISS ? y : hit.getLocation().y;
    }

    /** tubo de 6 caras que sigue la lista de puntos, afinandose de r0 a r1 */
    private static void tube(VertexConsumer vc, Matrix4f pm, Matrix3f nm, int light, double[][] p, float r0, float r1) {
        int m = p.length, sides = 6;
        float[][] ring = new float[m * sides][6];
        for (int i = 0; i < m; i++) {
            double[] a = p[Math.max(0, i - 1)], b = p[Math.min(m - 1, i + 1)];
            double tx = b[0] - a[0], ty = b[1] - a[1], tz = b[2] - a[2];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-6) { tx = 0; ty = 1; tz = 0; tl = 1; }
            tx /= tl; ty /= tl; tz /= tl;
            double rx = 0, ry = 1, rz = 0;
            if (Math.abs(ty) > 0.95) { rx = 1; ry = 0; }
            double ux = ty * rz - tz * ry, uy = tz * rx - tx * rz, uz = tx * ry - ty * rx;
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul;
            double vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
            float r = r0 + (r1 - r0) * (i / (float) (m - 1));
            for (int s = 0; s < sides; s++) {
                double ph = Math.PI * 2 * s / sides, c = Math.cos(ph), sn = Math.sin(ph);
                float nx = (float) (ux * c + vx * sn), ny = (float) (uy * c + vy * sn), nz = (float) (uz * c + vz * sn);
                float[] q = ring[i * sides + s];
                q[0] = (float) p[i][0] + nx * r; q[1] = (float) p[i][1] + ny * r; q[2] = (float) p[i][2] + nz * r;
                q[3] = nx; q[4] = ny; q[5] = nz;
            }
        }
        for (int i = 0; i < m - 1; i++) {
            for (int s = 0; s < sides; s++) {
                int s2 = (s + 1) % sides;
                float[] a = ring[i * sides + s], b = ring[i * sides + s2], c = ring[(i + 1) * sides + s2], d = ring[(i + 1) * sides + s];
                for (float[] q : new float[][]{a, b, c, d})
                    vc.vertex(pm, q[0], q[1], q[2]).color(0.03F, 0.03F, 0.04F, 1F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, q[3], q[4], q[5]).endVertex();
            }
        }
    }
}
