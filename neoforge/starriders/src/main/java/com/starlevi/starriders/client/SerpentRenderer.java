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
    public static final float SCALE = 1.1f;
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
        float move, run, last = Float.NaN, slither, rise = -1, riseW, glide, bank, lastYaw = Float.NaN;
        /** Por asiento: cuanto hay que correr al pasajero (bloques, ejes de la serpiente) y girarlo (grados). */
        final float[][] seats = new float[SerpentEntity.SEATS][4];
    }

    /** Altura a la que flota (bloques), sin contar el vaiven. */
    public static final float HOVER = 0.5f;

    /** Correccion del asiento n (x costado, y, z adelante, giro en grados), o null si no se dibujo todavia. */
    public static float[] seatOffset(SerpentEntity e, int n) {
        State st = STATES.get(e);
        return st == null || n < 0 || n >= SerpentEntity.SEATS ? null : st.seats[n];
    }

    public SerpentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.6f;
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
        return e.distanceToSqr(x, y, z) < 96 * 96;
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
        double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
        float hs = (float) Math.sqrt(vx * vx + vz * vz);
        float k = 1 - (float) Math.exp(-dt * 0.12f);
        boolean air = !e.onGround();
        boolean rising = air && vy > 0.03;
        st.move += ((hs > 0.01f ? 1 : 0) - st.move) * k;
        st.run += ((hs > 0.38f ? 1 : 0) - st.run) * k * 0.6f;
        // levitar: se enrosca girando y se levanta; despues planea estirada
        if (rising) {
            st.rise = st.rise < 0 ? 0 : Math.min(1, st.rise + dt / 55f);
        } else if (!air) {
            st.rise = -1;
        }
        st.riseW += ((rising ? 1 : 0) - st.riseW) * k * 0.8f;
        st.glide += ((air && !rising ? 1 : 0) - st.glide) * k * 0.6f;
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        // se ladea en las curvas al planear
        float dYaw = Float.isNaN(st.lastYaw) ? 0 : Mth.wrapDegrees(bodyYaw - st.lastYaw);
        st.lastYaw = bodyYaw;
        float bankT = air ? Mth.clamp(-dYaw / Math.max(0.05f, dt) * 1.6f, -25, 25) : 0;
        st.bank += (bankT - st.bank) * k * 0.6f;
        st.slither += hs * dt;
        if (st.slither > 5f && !air) {
            st.slither = 0;
            Minecraft.getInstance().level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                    0.25f, 0.5f + e.getRandom().nextFloat() * 0.2f, false);
        }
        SerpentAnim.Params p = this.params;
        p.age = now;
        p.move = st.move;
        p.run = st.run;
        p.rise = st.rise;
        p.riseWeight = st.riseW;
        p.glide = st.glide;
        p.hiss = e.roarProgress(partial);
        p.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
        p.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        anim.apply(p);
        float hover = HOVER + Mth.sin(now * 0.08f) * 0.08f + st.glide * 0.1f;
        // pasajeros: siguen a su hueso del enroscado
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (int n = 0; n < SerpentEntity.SEATS; n++) {
            anim.seat(n, v);
            float[] st0 = SerpentEntity.SEAT_POS[n];
            float[] o = st.seats[n];
            // modelo (x adelante, z costado) -> serpiente (z adelante, x costado)
            o[0] = -v.z * SCALE - (float) st0[0];
            o[1] = v.y * SCALE + hover - (float) st0[1];
            o[2] = v.x * SCALE - (float) st0[2];
            o[3] = 0;
        }

        ps.pushPose();
        ps.translate(0, hover, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw - 90));
        ps.mulPose(Axis.XP.rotationDegrees(st.bank));
        ps.scale(SCALE, SCALE, SCALE);
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
