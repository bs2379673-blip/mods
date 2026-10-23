package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.cosmeticos.Cosmetics;
import com.tunombre.cosmeticos.geo.Cosm;
import com.tunombre.cosmeticos.geo.Ctx;
import com.tunombre.cosmeticos.geo.Mesh;
import com.tunombre.cosmeticos.geo.Reg;
import com.tunombre.cosmeticos.geo.V3;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** calcula el movimiento del jugador, construye las mallas de cada cosmetico en el espacio de su hueso y las dibuja */
public final class CosRender {
    private CosRender() { }

    /** coloca la pila en el hueso pedido (hace pushPose; quien llama lo cierra) */
    public interface Rig { boolean push(PoseStack ps, Cosm.Bone bone); }

    private static final class PState {
        final Ctx ctx = new Ctx();
        float lastT = -1000F, turnSm;
        float lastYaw;
        float lastTurnT;
        float fade = 0F;
    }

    private static final Map<UUID, PState> STATES = new HashMap<>();
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    private static final Cosm[] COSM = new Cosm[64];
    private static boolean loaded;

    public static void clear() { STATES.clear(); }

    private static ResourceLocation tex(String n) {
        return TEX.computeIfAbsent(n, k -> new ResourceLocation("cosmeticos", "textures/cos/" + k + ".png"));
    }

    public static void draw(AbstractClientPlayer p, long mask, float pt, int light, MultiBufferSource buf, PoseStack ps, Rig rig) {
        if (!loaded) {
            for (Cosmetics.Def d : Cosmetics.ALL) COSM[d.index()] = Reg.get(d.id());
            loaded = true;
        }
        if (STATES.size() > 256) STATES.clear();
        PState st = STATES.computeIfAbsent(p.getUUID(), k -> new PState());
        Ctx c = st.ctx;
        if (c.mem == null) { c.mem = new HashMap<>(); c.seed = (p.getUUID().hashCode() & 0xFFFF) / 65535F; }
        update(p, st, c, pt);
        for (Cosm.Bone bone : Cosm.Bone.values()) {
            boolean any = false;
            for (int i = 0; i < Cosmetics.ALL.size(); i++) if ((mask & (1L << i)) != 0L && COSM[i] != null && COSM[i].bone() == bone) { any = true; break; }
            if (!any) continue;
            if (!rig.push(ps, bone)) continue;
            try {
                camera(ps, c);
                for (int i = 0; i < Cosmetics.ALL.size(); i++) {
                    if ((mask & (1L << i)) == 0L || COSM[i] == null || COSM[i].bone() != bone) continue;
                    c.sc.clear();
                    try { COSM[i].build(c); } catch (RuntimeException ex) { continue; }
                    flush(c, ps, buf, light);
                }
            } finally {
                ps.popPose();
            }
        }
    }

    private static void update(AbstractClientPlayer p, PState st, Ctx c, float pt) {
        float now = p.tickCount + pt;
        float dt = now - st.lastT;
        if (dt < 0F || dt > 40F) dt = 0F;
        st.lastT = now;
        c.t = now;
        c.dt = Math.min(dt, 2F);
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, dy = p.getY() - p.yo;
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        double a = Math.toRadians(yaw);
        c.vf = (float) (dx * -Math.sin(a) + dz * Math.cos(a));
        c.vl = (float) (dx * Math.cos(a) + dz * Math.sin(a));
        c.vy = (float) dy;
        c.speed = (float) Math.sqrt(c.vf * c.vf + c.vl * c.vl);
        float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
        st.turnSm += (turn - st.turnSm) * (1F - (float) Math.exp(-c.dt * 0.3F));
        c.turn = turn;
        c.turnAcc = st.turnSm;
        c.air = !p.onGround() && !p.isInWater() && !p.isPassenger();
        c.glide = p.isFallFlying();
        c.crouch = p.isCrouching();
        c.swim = p.isSwimming() || p.isInWater();
        c.sprint = p.isSprinting();
        c.fly = p.getAbilities().flying;
        c.carrying = com.tunombre.cosmeticos.Hook.isCarrying(p);
    }

    private static void camera(PoseStack ps, Ctx c) {
        Matrix4f inv = new Matrix4f(ps.last().pose()).invert();
        Vector3f r = inv.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f u = inv.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f f = inv.transformDirection(new Vector3f(0, 0, -1)).normalize();
        c.camR = new V3(r.x, r.y, r.z); c.camU = new V3(u.x, u.y, u.z); c.camF = new V3(f.x, f.y, f.z);
        // arriba del mundo: la vista aplica la rotación inversa de la cámara; en pantallas (inventario) no hay cámara útil y queda la del hueso
        try {
            org.joml.Quaternionf q = new org.joml.Quaternionf(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation()).conjugate();
            Vector3f wu = q.transform(new Vector3f(0, 1, 0));
            Vector3f bu = inv.transformDirection(wu).normalize();
            c.up = Float.isFinite(bu.x) ? new V3(bu.x, bu.y, bu.z) : V3.Y;
        } catch (RuntimeException e) {
            c.up = V3.Y;
        }
    }

    private static void flush(Ctx c, PoseStack ps, MultiBufferSource buf, int light) {
        Matrix4f pose = ps.last().pose();
        Matrix3f nrm = ps.last().normal();
        for (Mesh m : c.sc.meshes.values()) {
            if (m.n == 0) continue;
            ResourceLocation t = tex(m.tex);
            VertexConsumer vc = buf.getBuffer(m.glow ? CosTypes.glow(t) : RenderType.entityTranslucent(t));
            int lm = m.glow ? LightTexture.FULL_BRIGHT : light;
            float[] d = m.d;
            for (int i = 0; i < m.n; i++) {
                int o = i * Mesh.STRIDE;
                vc.vertex(pose, d[o], d[o + 1], d[o + 2]).color(d[o + 8], d[o + 9], d[o + 10], d[o + 11]).uv(d[o + 3], d[o + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lm).normal(nrm, d[o + 5], d[o + 6], d[o + 7]).endVertex();
            }
        }
    }
}
