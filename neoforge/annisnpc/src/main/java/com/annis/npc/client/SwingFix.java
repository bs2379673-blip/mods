package com.annis.npc.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Golpe con la mano (pegar, romper bloques) cuando otro mod de animaciones (Punchy Moves) pisa la pose
 * del jugador: se guarda la pose justo despues del juego y, si despues de Punchy los brazos cambiaron,
 * se le vuelve a sumar el movimiento del golpe encima de su animacion (no en lugar de ella).
 */
public final class SwingFix {
    private static final float[] BEFORE = new float[9];
    private static Object beforeModel;

    private SwingFix() {
    }

    /** Despues del juego y antes de Punchy. */
    public static void capture(HumanoidModel<?> m) {
        beforeModel = m;
        put(0, m.rightArm);
        put(3, m.leftArm);
        put(6, m.body);
    }

    private static void put(int i, ModelPart p) {
        BEFORE[i] = p.xRot;
        BEFORE[i + 1] = p.yRot;
        BEFORE[i + 2] = p.zRot;
    }

    private static boolean same(int i, ModelPart p) {
        return Math.abs(BEFORE[i] - p.xRot) < 1.0e-4f && Math.abs(BEFORE[i + 1] - p.yRot) < 1.0e-4f
                && Math.abs(BEFORE[i + 2] - p.zRot) < 1.0e-4f;
    }

    /** Despues de Punchy. */
    public static void apply(HumanoidModel<?> m, LivingEntity entity, float ageInTicks) {
        if (beforeModel != m) {
            return;
        }
        beforeModel = null;
        float partial = Mth.clamp(ageInTicks - entity.tickCount, 0.0f, 1.0f);
        float f = m.attackTime > 0.0f ? m.attackTime : entity.getAttackAnim(partial);
        if (f <= 0.0f) {
            return;
        }
        HumanoidArm side = entity.swingingArm == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        ModelPart arm = side == HumanoidArm.LEFT ? m.leftArm : m.rightArm;
        ModelPart other = side == HumanoidArm.LEFT ? m.rightArm : m.leftArm;
        // si nadie toco el brazo que golpea, el golpe del juego ya esta
        if (same(side == HumanoidArm.LEFT ? 3 : 0, arm)) {
            return;
        }
        float twist = Mth.sin(Mth.sqrt(f) * ((float) Math.PI * 2.0f)) * 0.2f;
        if (side == HumanoidArm.LEFT) {
            twist *= -1.0f;
        }
        float g = 1.0f - f;
        g = 1.0f - g * g * g * g;
        float lift = Mth.sin(g * (float) Math.PI) * 1.2f;
        float aim = Mth.sin(f * (float) Math.PI) * -(m.head.xRot - 0.7f) * 0.75f;
        m.body.yRot += twist;
        arm.xRot -= lift + aim;
        arm.yRot += twist * 2.0f;
        arm.zRot += Mth.sin(f * (float) Math.PI) * -0.4f;
        other.yRot += twist;
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.jacket.copyFrom(pm.body);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftSleeve.copyFrom(pm.leftArm);
        }
    }
}
