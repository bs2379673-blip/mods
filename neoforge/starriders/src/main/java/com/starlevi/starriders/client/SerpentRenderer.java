package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starriders.SerpentEntity;
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
 * Dibuja la Serpiente Gigante (modelo "Snake" de Vasian-Digital3D, CC BY 4.0, reducido y animado por
 * codigo). Cada parte del cuerpo tiene su textura; los ojos y las marcas de la cabeza brillan.
 */
public class SerpentRenderer extends EntityRenderer<SerpentEntity> {
    public static final float SCALE = 1.8f;
    private static final ResourceLocation[] TEX = new ResourceLocation[8];
    private static final ResourceLocation[] GLOW = new ResourceLocation[8];

    static {
        for (int i = 0; i < 8; i++) {
            TEX[i] = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/serpiente_" + (i + 1) + ".png");
        }
        GLOW[0] = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/serpiente_1_brillo.png");
        GLOW[1] = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/serpiente_2_brillo.png");
    }

    private static SkinnedModel model;
    private static SerpentAnim anim;
    private static boolean failed;
    private final SerpentAnim.Params params = new SerpentAnim.Params();
    private static final Map<SerpentEntity, State> STATES = new WeakHashMap<>();

    static final class State {
        float phase, move, run, last = Float.NaN, slither;
        /** Por asiento: cuanto hay que correr al pasajero (bloques, ejes de la serpiente) y girarlo (grados). */
        final float[][] seats = new float[SerpentEntity.SEATS][4];
    }

    /** Correccion del asiento n (x costado, y, z adelante, giro en grados), o null si no se dibujo todavia. */
    public static float[] seatOffset(SerpentEntity e, int n) {
        State st = STATES.get(e);
        return st == null || n < 0 || n >= SerpentEntity.SEATS ? null : st.seats[n];
    }

    public SerpentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4f;
    }

    private static boolean load() {
        if (model != null || failed) {
            return model != null;
        }
        try (InputStream in = SerpentRenderer.class.getResourceAsStream("/assets/starriders/models/entity/serpiente.bin")) {
            model = SkinnedModel.read(in);
            anim = new SerpentAnim(model);
        } catch (Exception e) {
            failed = true;
            StarRiders.LOGGER.error("StarRiders: no se pudo cargar el modelo de la serpiente", e);
        }
        return model != null;
    }

    @Override
    public boolean shouldRender(SerpentEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        // es muy larga: se dibuja aunque la caja chica de colision quede fuera de la pantalla
        return e.distanceToSqr(x, y, z) < 160 * 160;
    }

    @Override
    public void render(SerpentEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!load()) {
            return;
        }
        State st = STATES.computeIfAbsent(e, k -> new State());
        float now = e.tickCount + partial;
        float dt = Float.isNaN(st.last) ? 0 : Mth.clamp(now - st.last, 0, 3);
        st.last = now;
        double vx = e.getX() - e.xo, vz = e.getZ() - e.zo;
        float hs = (float) Math.sqrt(vx * vx + vz * vz);
        float k = 1 - (float) Math.exp(-dt * 0.12f);
        st.move += ((hs > 0.01f ? 1 : 0) - st.move) * k;
        st.run += ((hs > 0.38f ? 1 : 0) - st.run) * k * 0.6f;
        // la onda avanza lo mismo que el cuerpo (en metros del modelo), asi el cuerpo no patina
        float dist = hs / SCALE;
        st.phase += dist / SerpentAnim.WAVE * Mth.TWO_PI * dt;
        st.slither += hs * dt;
        if (st.slither > 3.5f) {
            st.slither = 0;
            Minecraft.getInstance().level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.SAND_STEP, SoundSource.NEUTRAL,
                    0.5f, 0.6f, false);
        }
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        SerpentAnim.Params p = this.params;
        p.age = now;
        p.phase = st.phase;
        p.move = st.move;
        p.run = st.run;
        p.hiss = e.roarProgress(partial);
        p.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
        p.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        anim.apply(p);
        // los pasajeros de atras van sobre el lomo aunque el cuerpo ondule
        float[] base = new float[4], pt = new float[4];
        anim.point(SerpentAnim.SEAT, base);
        for (int n = 0; n < SerpentEntity.SEATS; n++) {
            float s = SerpentAnim.SEAT + n * SerpentEntity.SPACING / SCALE;
            anim.point(s, pt);
            float[] o = st.seats[n];
            // modelo (x adelante, z costado) -> serpiente (z adelante, x costado); el asiento fijo esta en -n*SPACING
            o[0] = -pt[2] * SCALE;
            o[1] = (pt[1] - base[1]) * SCALE;
            o[2] = pt[0] * SCALE + n * SerpentEntity.SPACING;
            o[3] = pt[3] * Mth.RAD_TO_DEG;
        }

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw - 90));
        ps.scale(SCALE, SCALE, SCALE);
        // el eje del cuerpo esta a 0.3 m y la panza a ~0.32 m por debajo: queda apoyada en el piso
        ps.translate(0, 0.02f, 0);
        model.renderGroups(ps, g -> g < TEX.length ? buffers.getBuffer(RenderType.entityCutoutNoCull(TEX[g])) : null, light,
                OverlayTexture.NO_OVERLAY, -1);
        model.renderGroups(ps, g -> g < GLOW.length && GLOW[g] != null ? buffers.getBuffer(RenderType.eyes(GLOW[g])) : null, light,
                OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SerpentEntity e) {
        return TEX[0];
    }
}
