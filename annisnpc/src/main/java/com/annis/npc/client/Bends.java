package com.annis.npc.client;

import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Dobla codos y rodillas de los jugadores con bendy-lib (via playerAnimator). */
final class Bends {
    private Bends() {
    }

    private static void bend(ModelPart part, ModelPart lower) {
        IBendHelper.INSTANCE.bend(part, 0.0f, lower.pitch);
    }

    static void apply(PlayerEntityModel<?> pm, NpcModel p) {
        try {
            bend(pm.rightArm, p.rightForearm);
            bend(pm.rightSleeve, p.rightForearm);
            bend(pm.leftArm, p.leftForearm);
            bend(pm.leftSleeve, p.leftForearm);
            bend(pm.rightLeg, p.rightShin);
            bend(pm.rightPants, p.rightShin);
            bend(pm.leftLeg, p.leftShin);
            bend(pm.leftPants, p.leftShin);
        } catch (Throwable ignored) {
            // otra version de playerAnimator: quedan rectos
        }
    }
}
