package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starriders.PhoenixEntity;
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
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Dibuja el Fenix con sus animaciones; inclina el cuerpo con el vuelo y se ladea en las curvas. */
public class PhoenixRenderer extends EntityRenderer<PhoenixEntity> {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/fenix.png");
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "textures/entity/fenix_brillo.png");
    public static final float SCALE = 1.5f;
    /** Altura del centro del cuerpo (bloques, sin escalar): ahi gira al inclinarse. */
    private static final float CENTER = (24 - 10) / 16f;

    private final PhoenixModel model = new PhoenixModel();
    private final PhoenixAnim.Params params = new PhoenixAnim.Params();
    private static final Map<PhoenixEntity, State> STATES = new WeakHashMap<>();

    /** Estado suave de cada fenix (pesos de animacion, aleteo, inclinacion). */
    static final class State {
        float fly, glide, dive, phase, power = 1, bank, pitch, last = Float.NaN, lastYaw = Float.NaN;
        float glideTimer;
    }

    public PhoenixRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.1f;
    }

    static State state(PhoenixEntity e) {
        return STATES.computeIfAbsent(e, k -> new State());
    }

    @Override
    public void render(PhoenixEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        State st = state(e);
        float now = e.tickCount + partial;
        float dt = Float.isNaN(st.last) ? 0 : Mth.clamp(now - st.last, 0, 3);
        st.last = now;
        boolean flying = e.isFlyingNow();
        double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
        float hs = (float) Math.sqrt(vx * vx + vz * vz);
        float k = 1 - (float) Math.exp(-dt * 0.18);
        st.fly += ((flying ? 1 : 0) - st.fly) * k;
        // planea si va rapido y no sube; de vez en cuando vuelve a aletear un poco
        st.glideTimer += dt;
        boolean climbing = vy > 0.06;
        boolean wantsGlide = flying && !climbing && hs > 0.4 && (st.glideTimer % 120) > 30;
        st.glide += ((wantsGlide ? 1 : 0) - st.glide) * k * 0.6f;
        st.dive += ((flying && vy < -0.45 ? 1 : 0) - st.dive) * k;
        float freq = climbing || hs < 0.25 ? 0.5f : 0.36f;
        float before = st.phase;
        st.phase += dt * freq;
        st.power += ((climbing ? 1.3f : 1f) - st.power) * k;
        // sonido del aletazo (al bajar el ala)
        if (st.fly > 0.5f && (1 - st.glide) > 0.4f && Mth.floor((before - Mth.HALF_PI) / Mth.TWO_PI) != Mth.floor((st.phase - Mth.HALF_PI) / Mth.TWO_PI)) {
            Minecraft.getInstance().level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.NEUTRAL,
                    0.45f, 1.35f + e.getRandom().nextFloat() * 0.1f, false);
        }
        // inclinacion: hacia donde vuela y ladeo en las curvas (con Do a Barrel Roll, el giro del jinete)
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float dYaw = Float.isNaN(st.lastYaw) ? 0 : Mth.wrapDegrees(bodyYaw - st.lastYaw);
        st.lastYaw = bodyYaw;
        float bankTarget = flying ? Mth.clamp(-dYaw / Math.max(0.05f, dt) * 0.06f, -0.75f, 0.75f) : 0;
        Float roll = e.getControllingPassenger() instanceof Player p ? DabrCompat.roll(p, partial) : null;
        if (roll != null && flying) {
            bankTarget = roll * Mth.DEG_TO_RAD;
        }
        st.bank += (bankTarget - st.bank) * k * 0.7f;
        float pitchTarget = flying ? (float) Math.toDegrees(Math.atan2(vy, Math.max(0.05, hs))) * 0.7f : 0;
        st.pitch += (Mth.clamp(pitchTarget, -55, 45) - st.pitch) * k * 0.5f;

        PhoenixAnim.Params p = this.params;
        p.age = now;
        p.walk = e.walkAnimation.position(partial);
        p.walkAmount = flying ? 0 : Math.min(1, e.walkAnimation.speed(partial));
        p.fly = st.fly;
        p.glide = st.glide;
        p.dive = st.dive;
        p.flapPhase = st.phase;
        p.flapPower = st.power;
        p.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
        p.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        p.roar = e.roarProgress(partial);
        p.tailSpeed = Mth.clamp(hs * 1.3f, 0, 1);
        PhoenixAnim.apply(this.model, p);

        // el jinete se inclina y se ladea junto con el fenix (sus ejes estan girados media vuelta)
        Carry.of(e, 1)[0].identity().rotateY(Mth.PI).translate(0, CENTER * SCALE, 0).rotateX(st.pitch * Mth.DEG_TO_RAD).rotateZ(st.bank)
                .translate(0, -CENTER * SCALE, 0).rotateY(-Mth.PI);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        ps.translate(0, CENTER * SCALE, 0);
        ps.mulPose(Axis.XP.rotationDegrees(st.pitch));
        ps.mulPose(Axis.ZP.rotation(st.bank));
        ps.translate(0, -CENTER * SCALE, 0);
        ps.scale(-SCALE, -SCALE, SCALE);
        ps.translate(0, -1.501f, 0);
        this.model.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY);
        this.model.render(ps, buffers.getBuffer(RenderType.eyes(GLOW)), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PhoenixEntity e) {
        return TEX;
    }
}
