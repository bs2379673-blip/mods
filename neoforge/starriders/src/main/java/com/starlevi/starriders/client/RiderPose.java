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
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Pose del jinete del Guardian: inclinado hacia adelante, piernas abiertas abrazando el lomo y las
 * manos agarradas de la melena. Se hamaca con cada paso de la bestia.
 */
public final class RiderPose {
    private static final Set<Player> PUSHED = Collections.newSetFromMap(new WeakHashMap<>());

    private RiderPose() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) -> {
            Player p = e.getEntity();
            if (!(p.getVehicle() instanceof GuardianEntity g)) {
                return;
            }
            float[] st = GuardianRenderer.riderInfo(g);
            float phase = st == null ? 0 : st[0] * Mth.TWO_PI * 2;
            float walk = st == null ? 0 : st[1], run = st == null ? 0 : st[2], air = st == null ? 0 : st[3];
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            PUSHED.add(p);
            // se hamaca con el paso: sube y baja, y se balancea un poco de costado
            float bob = (Mth.sin(phase) * 0.07f + Mth.sin(phase * 2) * 0.03f) * walk * (1 + run * 0.6f);
            float sway = Mth.cos(phase * 0.5f) * 3f * walk;
            float bodyYaw = Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot);
            ps.translate(0, bob, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            ps.translate(0, 0.62, 0);
            ps.mulPose(Axis.XP.rotationDegrees(lean(walk, run, air) + Mth.sin(phase) * 3f * walk));
            ps.mulPose(Axis.ZP.rotationDegrees(sway));
            ps.translate(0, -0.62, 0);
            ps.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> {
            if (PUSHED.remove(e.getEntity())) {
                e.getPoseStack().popPose();
            }
        });
    }

    /** Grados que se inclina hacia adelante. */
    private static float lean(float walk, float run, float air) {
        return 18f + walk * 6f + run * 10f - air * 8f;
    }

    /** Despues de setupAnim (y de Punchy): piernas abiertas, manos agarradas de la melena. */
    public static void apply(PlayerModel<?> m, Player p, float age) {
        if (!(p.getVehicle() instanceof GuardianEntity g)) {
            return;
        }
        float[] st = GuardianRenderer.riderInfo(g);
        float phase = st == null ? 0 : st[0] * Mth.TWO_PI * 2;
        float walk = st == null ? 0 : st[1], run = st == null ? 0 : st[2], air = st == null ? 0 : st[3];
        float leanRad = lean(walk, run, air) * Mth.DEG_TO_RAD;
        // piernas: sentado a horcajadas, bien abiertas, apretando con las rodillas
        float grip = Mth.sin(phase) * 0.05f * walk;
        m.rightLeg.xRot = -1.25f + leanRad * 0.5f;
        m.rightLeg.yRot = 0.35f;
        m.rightLeg.zRot = 0.55f + grip;
        m.leftLeg.xRot = -1.25f + leanRad * 0.5f;
        m.leftLeg.yRot = -0.35f;
        m.leftLeg.zRot = -0.55f - grip;
        // brazos: hacia adelante y abajo, agarrados de la melena; tironean con cada paso
        float tug = Mth.sin(phase + 0.6f) * 0.12f * walk + run * 0.1f;
        m.rightArm.xRot = -0.75f + leanRad * 0.3f - tug;
        m.rightArm.yRot = -0.15f;
        m.rightArm.zRot = 0.18f;
        m.leftArm.xRot = -0.75f + leanRad * 0.3f + tug * 0.8f;
        m.leftArm.yRot = 0.15f;
        m.leftArm.zRot = -0.18f;
        // la cabeza compensa la inclinacion para seguir mirando al frente
        m.head.xRot -= leanRad * 0.8f;
        m.hat.copyFrom(m.head);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.jacket.copyFrom(m.body);
    }
}
