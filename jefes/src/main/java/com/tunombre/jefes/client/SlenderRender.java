package com.tunombre.jefes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.jefes.JefeEntity;
import com.tunombre.jefes.Jefes;
import com.tunombre.jefes.core.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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

import java.util.Map;
import java.util.WeakHashMap;

/** Dibuja a Slenderman con la geometria de {@link SlenderGeo}, mas el glitch de cuando se teletransporta (cortes, doble imagen y un fantasma donde estaba). */
public final class SlenderRender {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation TEX = new ResourceLocation(Jefes.MODID, "textures/entity/slender.png");
    private static boolean tried, ok;
    private static final Map<JefeEntity, St> STATES = new WeakHashMap<>();

    private static final class St {
        final SlenderGeo.State geo = new SlenderGeo.State();
        Vec3 last;
        double glitchUntil = -1, ghostUntil = -1, ghostYaw;
        Vec3 ghost;
        float[] mesh;
    }

    private SlenderRender() { }

    public static void render(JefeEntity e, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!tried) {
            tried = true;
            ok = SlenderGeo.load(SlenderRender.class.getResourceAsStream("/assets/jefes/slender/mesh.bin"));
            if (!ok) Jefes.LOG.error("[jefes] no se pudo cargar la malla de Slenderman");
        }
        if (!ok) return;
        St st = STATES.computeIfAbsent(e, k -> new St());
        Vec3 pp = e.getPosition(pt);
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        double time = e.tickCount + pt;
        SlenderGeo.In in = new SlenderGeo.In();
        in.px = pp.x; in.py = pp.y; in.pz = pp.z; in.yaw = yaw; in.time = time;
        in.dt = st.geo.lastTime < 0 ? 1 : Mth.clamp(time - st.geo.lastTime, 0, 3);
        st.geo.lastTime = time;
        in.speed = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        in.camX = cam.x; in.camY = cam.y; in.camZ = cam.z;
        in.ground = (x, y, z) -> {
            var hit = e.level().clip(new ClipContext(new Vec3(x, y + 2.0, z), new Vec3(x, y - 5.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            return hit.getType() == HitResult.Type.MISS ? y : hit.getLocation().y;
        };
        Act act = e.getAct();
        if (act == Act.ATTACK_L || act == Act.ATTACK_R) {
            float t = e.actTime(pt) / Act.ATTACK_L.ticks;
            float l = t < 0.45F ? t / 0.45F : Math.max(0F, (1F - t) / 0.55F);
            in.lash = l * l * (3 - 2 * l);
            in.lashIdx = act == Act.ATTACK_L ? 0 : 1;
        }
        // teletransporte: salto de posicion de un cuadro al otro
        if (st.last != null && st.last.distanceToSqr(pp) > 16) {
            in.reset = true;
            st.glitchUntil = time + 18;
            st.ghostUntil = time + 14;
            st.ghost = st.last;
            st.ghostYaw = yaw;
        }
        st.last = pp;
        double glitch = time < st.glitchUntil ? 0.45 * (st.glitchUntil - time) / 18.0 : 0;
        boolean hide = glitch > 0 && ((int) Math.floor(time * 3) * 2654435761L >>> 5 & 7) == 0;

        SlenderGeo.Out out = new SlenderGeo.Out();
        SlenderGeo.tentacles(in, st.geo, out);
        st.mesh = SlenderGeo.mesh(in, st.geo, glitch, st.mesh);
        int lm = LightTexture.pack(Math.max(LightTexture.block(light), 3), Math.max(LightTexture.sky(light), 3));
        Matrix4f pm = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        double rx = Math.cos(Math.toRadians(yaw)), rz = Math.sin(Math.toRadians(yaw));
        // vertices relativos a la entidad
        float ox = 0, oy = 0, oz = 0;

        if (!hide) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
            mesh(vc, pm, nm, st.mesh, ox, oy, oz, 0, 0, 1F, 1F, 1F, 1F, lm);
            VertexConsumer tv = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            float[] a = out.tube.a;
            int cnt = out.tube.n / 9;
            for (int i = 0; i < cnt; i++) {
                int o = i * 9;
                float x = a[o], y = a[o + 1], z = a[o + 2];
                if (glitch > 0) { double d = SlenderGeo.shift(y, time, glitch); x += (float) (d * rx); z += (float) (d * rz); }
                tv.vertex(pm, x, y, z).color(a[o + 6], a[o + 7], a[o + 8], 1F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lm).normal(nm, a[o + 3], a[o + 4], a[o + 5]).endVertex();
            }
        }
        if (glitch > 0) {
            // doble imagen de colores desplazada
            VertexConsumer tr = buf.getBuffer(RenderType.entityTranslucent(TEX));
            float k = (float) (glitch * 0.22);
            mesh(tr, pm, nm, st.mesh, ox, oy, oz, (float) rx * k, (float) rz * k, 0.2F, 1F, 1F, 0.5F, LightTexture.FULL_BRIGHT);
            mesh(tr, pm, nm, st.mesh, ox, oy, oz, -(float) rx * k, -(float) rz * k, 1F, 0.15F, 0.25F, 0.5F, LightTexture.FULL_BRIGHT);
        }
        if (st.ghost != null && time < st.ghostUntil) {
            // fantasma donde estaba, deshaciendose
            SlenderGeo.In g = new SlenderGeo.In();
            g.px = st.ghost.x; g.py = st.ghost.y; g.pz = st.ghost.z; g.yaw = st.ghostYaw; g.time = time; g.speed = 0;
            float f = (float) ((st.ghostUntil - time) / 14.0);
            float[] gm = SlenderGeo.mesh(g, st.geo, 0.7 * f + 0.1, null);
            VertexConsumer tr = buf.getBuffer(RenderType.entityTranslucent(TEX));
            mesh(tr, pm, nm, gm, (float) (pp.x - st.ghost.x), (float) (pp.y - st.ghost.y), (float) (pp.z - st.ghost.z), 0, 0, 0.8F, 0.9F, 1F, 0.55F * f, LightTexture.FULL_BRIGHT);
        }
    }

    /** triangulos -> cuadrilateros degenerados (el tipo de render dibuja cuadrilateros) */
    private static void mesh(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float[] m, float ox, float oy, float oz, float dx, float dz, float r, float g, float b, float al, int lm) {
        int n = SlenderGeo.n;
        for (int i = 0; i < n; i++) {
            int o = i * 8;
            for (int rep = 0; rep < (i % 3 == 2 ? 2 : 1); rep++)
                vc.vertex(pm, m[o] - ox + dx, m[o + 1] - oy, m[o + 2] - oz + dz).color(r, g, b, al).uv(m[o + 3], m[o + 4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lm).normal(nm, m[o + 5], m[o + 6], m[o + 7]).endVertex();
        }
    }
}
