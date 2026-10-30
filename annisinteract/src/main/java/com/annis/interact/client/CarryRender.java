package com.annis.interact.client;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Al dibujar a quien va en brazos o a caballito, lo pega al modelo del que lo lleva usando la
 * posicion y el giro de ESE fotograma: no queda atrasado al girar, y no importa lo que otros mods
 * (por ejemplo MCA, que corre a los aldeanos a un hombro) hagan con su posicion real.
 */
public final class CarryRender {
    private CarryRender() {
    }

    /** Llamar al principio de LivingEntityRenderer.render (las matrices ya estan en su posicion). */
    public static void apply(LivingEntity entity, float tickDelta, MatrixStack matrices) {
        Entity v = entity.getVehicle();
        if (!(v instanceof LivingEntity carrier)) {
            return;
        }
        float[] off = com.annis.interact.CarryPose.offsets(AnnisInteractClient.animOf(entity));
        if (off == null) {
            return;
        }
        float fwd = off[0], up = off[1], left = off[2];
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, carrier.prevBodyYaw, carrier.bodyYaw);
        float r = yaw * MathHelper.RADIANS_PER_DEGREE;
        double fx = -MathHelper.sin(r), fz = MathHelper.cos(r);
        double lx = MathHelper.cos(r), lz = MathHelper.sin(r);
        float scale = carrier.getScale();
        double cx = MathHelper.lerp(tickDelta, carrier.lastRenderX, carrier.getX());
        double cy = MathHelper.lerp(tickDelta, carrier.lastRenderY, carrier.getY());
        double cz = MathHelper.lerp(tickDelta, carrier.lastRenderZ, carrier.getZ());
        double wx = cx + (fx * fwd + lx * left) * scale;
        double wy = cy + up * scale;
        double wz = cz + (fz * fwd + lz * left) * scale;
        // donde lo iba a dibujar el juego
        double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        matrices.translate(wx - ex, wy - ey, wz - ez);
        // y mirando hacia donde mira el que lo lleva (las animaciones cuentan con eso)
        entity.bodyYaw = yaw;
        entity.prevBodyYaw = yaw;
        entity.headYaw = yaw;
        entity.prevHeadYaw = yaw;
    }
}
