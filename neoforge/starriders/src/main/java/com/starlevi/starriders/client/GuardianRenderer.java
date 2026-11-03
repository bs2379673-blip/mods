package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starriders.GuardianEntity;
import com.starlevi.starriders.StarRiders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dibuja el Guardian del Bosque (modelo "Tree Heretic Keeper" de Vasian-Digital3D, CC BY 4.0,
 * reducido y con animaciones agregadas). La caminata avanza segun lo que se mueve, para que las patas
 * no patinen.
 */
public class GuardianRenderer extends EntityRenderer<GuardianEntity> {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/guardian.png");
    public static final float SCALE = 4.2f;
    /** Cuadros de la caminata donde apoya cada pata (para el sonido de pisada). */
    private static final float[] STEPS = {30f, 120f};

    private static SkinnedModel model;
    private static GuardianAnim anim;
    private static boolean failed;
    private final GuardianAnim.Params params = new GuardianAnim.Params();
    private static final Map<GuardianEntity, State> STATES = new WeakHashMap<>();

    /** Lo que necesita el jinete para moverse con el paso del guardian (null si todavia no se dibujo). */
    public static float[] riderInfo(GuardianEntity e) {
        State st = STATES.get(e);
        if (st == null || model == null) {
            return null;
        }
        return new float[]{st.frame / model.frames, st.walk, st.run, st.air};
    }

    static final class State {
        float frame = GuardianAnim.IDLE_FRAME, walk, run, air, ground = Float.NaN, last = Float.NaN, grapple;
    }

    public GuardianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.2f;
    }

    private static boolean load() {
        if (model != null || failed) {
            return model != null;
        }
        try (InputStream in = GuardianRenderer.class.getResourceAsStream("/assets/starriders/models/entity/guardian.bin")) {
            model = SkinnedModel.read(in);
            anim = new GuardianAnim(model);
        } catch (Exception e) {
            failed = true;
            StarRiders.LOGGER.error("StarRiders: no se pudo cargar el modelo del guardian", e);
        }
        return model != null;
    }

    @Override
    public void render(GuardianEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!load()) {
            return;
        }
        State st = STATES.computeIfAbsent(e, k -> new State());
        float now = e.tickCount + partial;
        float dt = Float.isNaN(st.last) ? 0 : Mth.clamp(now - st.last, 0, 3);
        st.last = now;
        double vx = e.getX() - e.xo, vz = e.getZ() - e.zo;
        float hs = (float) Math.sqrt(vx * vx + vz * vz);
        // todo se mezcla de a poco (suavizado exponencial por tiempo, no por cuadro)
        float k = 1 - (float) Math.exp(-dt * 0.16f);
        boolean moving = hs > 0.01f;
        st.walk += ((moving ? 1 : 0) - st.walk) * k;
        st.run += ((hs > 0.32f ? 1 : 0) - st.run) * k * 0.6f;
        st.air += ((!e.onGround() && Math.abs(e.getY() - e.yo) > 0.02 ? 1 : 0) - st.air) * k;
        // avanza la caminata segun la distancia recorrida (con un tope para que no se vea frenetica)
        float perFrame = GuardianAnim.STRIDE_PER_FRAME * SCALE;
        // cuadros de la animacion (60 por segundo) que avanza por tick
        float framesPerTick = Mth.clamp(hs / perFrame, 0, 14f);
        if (!moving && st.walk > 0.05f) {
            // al frenar termina el paso con calma en vez de cortarlo
            framesPerTick = 1.5f * st.walk;
        }
        float before = st.frame;
        st.frame += framesPerTick * dt * (1 - st.air * 0.85f);
        if (st.walk > 0.4f && st.air < 0.3f) {
            for (float s : STEPS) {
                if (crossed(before, st.frame, s, model.frames)) {
                    Minecraft.getInstance().level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL,
                            0.35f + st.run * 0.3f, 0.65f + e.getRandom().nextFloat() * 0.1f, false);
                }
            }
        }
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        GuardianAnim.Params p = this.params;
        p.age = now;
        p.walkFrame = st.frame;
        p.walkAmount = st.walk;
        p.run = st.run;
        p.air = st.air;
        p.roar = e.roarProgress(partial);
        p.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
        p.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        Vec3 anchor = e.rootAnchor();
        float rootAge = e.rootAge(partial);
        st.grapple += ((anchor != null ? 1 : 0) - st.grapple) * (1 - (float) Math.exp(-dt * 0.4f));
        p.grapple = st.grapple;
        anim.apply(p);
        // apoya las patas en el piso (suave, para que no tiemble); en el aire no lo baja de golpe
        float g = anim.ground();
        if (Float.isNaN(st.ground)) {
            st.ground = g;
        }
        st.ground += (g - st.ground) * (1 - (float) Math.exp(-dt * 0.6f)) * (1 - st.air);

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.scale(SCALE, SCALE, SCALE);
        ps.translate(0, -st.ground, 0);
        model.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
        if (anchor != null && rootAge >= 0) {
            // raices: salen de las manos y se clavan en el lugar alto
            Vec3 base = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
            Vec3 to = anchor.subtract(base);
            float ext = Mth.clamp(rootAge / 8f, 0, 1);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
            org.joml.Vector3f h = new org.joml.Vector3f();
            for (int side = 0; side < 2; side++) {
                anim.hand(side == 0, h);
                org.joml.Vector3f w = new org.joml.Vector3f(h.x, h.y - st.ground, h.z).mul(SCALE);
                new org.joml.Quaternionf().rotationY(-bodyYaw * Mth.DEG_TO_RAD).transform(w);
                for (int r = 0; r < 2; r++) {
                    roots(ps, vc, new Vec3(w.x, w.y, w.z), to, ext, now, side * 2 + r, light);
                }
            }
        }
        super.render(e, yaw, partial, ps, buffers, light);
    }

    /** Una raiz: de la mano al enganche, ondulando un poco, mas gruesa en la base. */
    private static void roots(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, float ext, float now, int seed, int light) {
        int n = 14;
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) {
            return;
        }
        Vec3 dir = d.scale(1 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float ph = seed * 1.7f;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n * ext;
            float wob = Mth.sin(t * 9 + ph + now * 0.15f) * 0.18f * (1 - t) + (seed % 2 == 0 ? 0.25f : -0.25f) * Mth.sin(t * Mth.PI);
            Vec3 p = a.add(d.scale(t)).add(side.scale(wob)).add(up.scale(Mth.sin(t * Mth.PI) * 0.4f * (seed < 2 ? 1 : -0.6f)));
            pts[i] = p;
        }
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float w0 = Mth.lerp(t0, 0.32f, 0.08f), w1 = Mth.lerp(t1, 0.32f, 0.08f);
            float v0 = (float) (t0 * len * ext * 0.5), v1 = (float) (t1 * len * ext * 0.5);
            for (int q = 0; q < 2; q++) {
                Vec3 o = q == 0 ? side : up;
                quad(vc, pose, pts[i].add(o.scale(-w0)), pts[i + 1].add(o.scale(-w1)), pts[i + 1].add(o.scale(w1)), pts[i].add(o.scale(w0)),
                        0.30f, 0.36f, v0, v1, light, o.cross(dir));
            }
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1,
                             int light, Vec3 n) {
        vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(-1).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(-1).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(-1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(-1).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    private static boolean crossed(float a, float b, float mark, int frames) {
        if (b <= a) {
            return false;
        }
        float n = (float) Math.floor((a - mark) / frames);
        return mark + (n + 1) * frames <= b;
    }

    @Override
    public ResourceLocation getTextureLocation(GuardianEntity e) {
        return TEX;
    }
}
