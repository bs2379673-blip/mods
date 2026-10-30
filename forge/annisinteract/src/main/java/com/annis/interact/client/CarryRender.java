package com.annis.interact.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Al dibujar a quien va en brazos o a caballito, lo pega al modelo del que lo lleva usando la
 * posicion y el giro de ESE fotograma: no queda atrasado al girar, y no importa lo que otros mods
 * (por ejemplo MCA, que corre a los aldeanos a un hombro) hagan con su posicion real.
 */
public final class CarryRender {
    private CarryRender() {
    }

    /** Llamar al principio de LivingEntityRenderer.render (las matrices ya estan en su posicion). */
    public static void apply(LivingEntity entity, float tickDelta, PoseStack matrices) {
        Entity v = entity.getVehicle();
        if (!(v instanceof LivingEntity carrier)) {
            return;
        }
        float[] off = com.annis.interact.CarryPose.offsets(AnnisInteractClient.animOf(entity));
        if (off == null) {
            return;
        }
        float fwd = off[0], up = off[1], left = off[2];
        float yaw = Mth.rotLerp(tickDelta, carrier.yBodyRotO, carrier.yBodyRot);
        float r = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r), fz = Mth.cos(r);
        double lx = Mth.cos(r), lz = Mth.sin(r);
        float scale = carrier.getScale();
        double cx = Mth.lerp(tickDelta, carrier.xOld, carrier.getX());
        double cy = Mth.lerp(tickDelta, carrier.yOld, carrier.getY());
        double cz = Mth.lerp(tickDelta, carrier.zOld, carrier.getZ());
        double wx = cx + (fx * fwd + lx * left) * scale;
        double wy = cy + up * scale;
        double wz = cz + (fz * fwd + lz * left) * scale;
        // donde lo iba a dibujar el juego
        double ex = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double ey = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double ez = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        matrices.translate(wx - ex, wy - ey, wz - ez);
        // y mirando hacia donde mira el que lo lleva (las animaciones cuentan con eso)
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
    }
}
