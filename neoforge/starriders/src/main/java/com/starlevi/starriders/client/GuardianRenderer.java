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
    public static final float SCALE = 2.3f;
    /** Cuadros de la caminata donde apoya cada pata (para el sonido de pisada). */
    private static final float[] STEPS = {30f, 120f};

    private static SkinnedModel model;
    private static GuardianAnim anim;
    private static boolean failed;
    private final GuardianAnim.Params params = new GuardianAnim.Params();
    private static final Map<GuardianEntity, State> STATES = new WeakHashMap<>();

    static final class State {
        float frame = GuardianAnim.IDLE_FRAME, walk, run, air, ground = Float.NaN, last = Float.NaN;
    }

    public GuardianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.2f;
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
        super.render(e, yaw, partial, ps, buffers, light);
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
