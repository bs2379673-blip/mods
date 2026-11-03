package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starriders.GuardianEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Jinete del Guardian: va colgado de la melena, agarrado solo con los brazos, con el cuerpo pegado al
 * lomo y las piernas sueltas. Se mueve como un muneco de trapo (sin exagerar): un resorte con algo de
 * retraso hace que el cuerpo se hamaque con cada paso y siga un poco cuando la bestia frena.
 */
public final class RiderPose {
    /** Altura de las manos sobre los pies con los brazos arriba (bloques): ahi cuelga. */
    public static final float HANDS = 2.05f;
    private static final Set<Player> PUSHED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Player, Swing> SWING = new WeakHashMap<>();

    /** Resorte del cuerpo colgado: cabeceo y balanceo de costado, con su velocidad. */
    public static final class Swing {
        public float pitch = Float.NaN, pitchVel, roll, rollVel, last = Float.NaN;

        /** Avanza el resorte hacia el angulo que pide el paso de la bestia. */
        public void step(float now, float phase, float walk, float run, float air) {
            float dt = Float.isNaN(this.last) ? 0 : Mth.clamp(now - this.last, 0, 3);
            this.last = now;
            float targetPitch = lean(walk, run, air) + Mth.sin(phase - 0.6f) * 3f * walk * (1 + run * 0.5f);
            float targetRoll = Mth.sin(phase * 0.5f - 0.4f) * 5f * walk;
            if (Float.isNaN(this.pitch)) {
                this.pitch = targetPitch;
            }
            // pasos chicos para que sea estable aunque baje el fps
            int n = Math.max(1, Mth.ceil(dt * 4));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                this.pitchVel += ((targetPitch - this.pitch) * 0.09f - this.pitchVel * 0.22f) * h;
                this.pitch += this.pitchVel * h;
                this.rollVel += ((targetRoll - this.roll) * 0.07f - this.rollVel * 0.2f) * h;
                this.roll += this.rollVel * h;
            }
        }
    }

    private RiderPose() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) -> {
            Player p = e.getEntity();
            if (p.getVehicle() instanceof com.starlevi.starriders.SerpentEntity sp) {
                // pasajeros de la serpiente: se acomodan sobre la curva del lomo
                float[] o = SerpentRenderer.seatOffset(sp, sp.seatOf(p));
                if (o != null) {
                    float yaw = Mth.rotLerp(e.getPartialTick(), sp.yBodyRotO, sp.yBodyRot);
                    net.minecraft.world.phys.Vec3 w = new net.minecraft.world.phys.Vec3(o[0], o[1], o[2]).yRot(-yaw * Mth.DEG_TO_RAD);
                    PoseStack ps = e.getPoseStack();
                    ps.pushPose();
                    PUSHED.add(p);
                    ps.translate(w.x, w.y, w.z);
                    ps.mulPose(Axis.YP.rotationDegrees(o[3]));
                }
                return;
            }
            if (!(p.getVehicle() instanceof GuardianEntity g) || g instanceof com.starlevi.starriders.SerpentEntity) {
                return;
            }
            float[] st = GuardianRenderer.riderInfo(g);
            float phase = st == null ? 0 : st[0] * Mth.TWO_PI * 2;
            float walk = st == null ? 0 : st[1], run = st == null ? 0 : st[2], air = st == null ? 0 : st[3];
            Swing sw = SWING.computeIfAbsent(p, k -> new Swing());
            sw.step(p.tickCount + e.getPartialTick(), phase, walk, run, air);
            float yaw = Mth.rotLerp(e.getPartialTick(), g.yBodyRotO, g.yBodyRot);
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            PUSHED.add(p);
            // el cuerpo del jugador sigue al guardian, no a la camara
            outer(ps, yaw, Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot), sw, phase, walk);
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> {
            if (PUSHED.remove(e.getEntity())) {
                e.getPoseStack().popPose();
            }
        });
    }

    /** Grados que se inclina el cuerpo hacia el lomo (los pies quedan atras, colgando). */
    static float lean(float walk, float run, float air) {
        return 46f + run * 4f + air * 6f;
    }

    /** Gira todo el cuerpo alrededor de las manos (que quedan fijas en la melena). */
    public static void outer(PoseStack ps, float bodyYaw, float playerYaw, Swing sw, float phase, float walk) {
        float bob = Mth.sin(phase) * 0.05f * walk;
        ps.translate(0, bob, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.translate(0, HANDS, 0);
        ps.mulPose(Axis.XP.rotationDegrees(sw.pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(sw.roll * 0.3f));
        ps.translate(0, -HANDS, 0);
        // el render del jugador gira segun su propio cuerpo (que sigue a la camara): se compensa
        ps.mulPose(Axis.YP.rotationDegrees(playerYaw));
    }

    /** Despues de setupAnim (y de Punchy): brazos arriba agarrados, piernas sueltas. */
    public static void apply(PlayerModel<?> m, Player p, float age) {
        if (!(p.getVehicle() instanceof GuardianEntity g) || g instanceof com.starlevi.starriders.SerpentEntity) {
            return;
        }
        float[] st = GuardianRenderer.riderInfo(g);
        Swing sw = SWING.get(p);
        parts(m, st == null ? 0 : st[0] * Mth.TWO_PI * 2, st == null ? 0 : st[1], st == null ? 0 : st[3],
                sw == null ? 0 : sw.pitchVel, sw == null ? 46f : sw.pitch, age);
    }

    public static void parts(PlayerModel<?> m, float phase, float walk, float air, float pitchVel, float pitch, float age) {
        float leanRad = pitch * Mth.DEG_TO_RAD;
        // brazos: estirados arriba de la cabeza, un poco abiertos, agarrados de la melena
        float pull = Mth.sin(phase + 0.4f) * 0.05f * walk;
        m.rightArm.xRot = -2.95f + pull;
        m.rightArm.yRot = 0.05f;
        m.rightArm.zRot = -0.16f;
        m.leftArm.xRot = -2.95f - pull;
        m.leftArm.yRot = -0.05f;
        m.leftArm.zRot = 0.16f;
        // piernas: sueltas, colgando casi derechas; se hamacan con retraso (con la velocidad del resorte)
        float lag = Mth.clamp(-pitchVel * 0.35f, -0.35f, 0.35f);
        float sR = Mth.sin(phase - 1.3f), sL = Mth.sin(phase - 1.8f);
        m.rightLeg.xRot = -leanRad * 0.3f + sR * 0.2f * walk + lag + air * 0.25f;
        m.rightLeg.yRot = 0.05f;
        m.rightLeg.zRot = 0.1f + sL * 0.06f * walk;
        m.leftLeg.xRot = -leanRad * 0.3f + sL * 0.2f * walk + lag * 0.9f + air * 0.3f;
        m.leftLeg.yRot = -0.05f;
        m.leftLeg.zRot = -0.1f - sR * 0.06f * walk;
        // cabeza: mira hacia arriba y adelante, y se sacude apenas con los pasos
        m.head.xRot = -0.35f + Mth.sin(phase - 0.9f) * 0.08f * walk;
        m.head.yRot = 0;
        m.head.zRot = 0;
        m.body.xRot = 0;
        m.body.yRot = 0;
        m.hat.copyFrom(m.head);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.jacket.copyFrom(m.body);
    }
}
