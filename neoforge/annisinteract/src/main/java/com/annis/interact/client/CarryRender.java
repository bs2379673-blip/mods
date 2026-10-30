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
            handGlue(entity, tickDelta, matrices);
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

    /**
     * De la mano: el NPC se dibuja exactamente al costado del jugador que lo lleva, con la posicion
     * y el giro de ESTE fotograma. Asi no queda atrasado (el servidor lo mueve y el cliente lo
     * dibuja con unos ticks de retraso) y las manos van siempre sincronizadas.
     */
    /** Posicion y giro con que se dibuja (pegado al jugador de la mano), o null si no aplica. */
    static net.minecraft.world.phys.Vec3 gluedPos(LivingEntity entity, float tickDelta, float[] yawOut) {
        if (entity instanceof net.minecraft.world.entity.player.Player || entity.getVehicle() != null) {
            return null;
        }
        String mine = AnnisInteractClient.animOf(entity);
        boolean right = "de_la_mano_d".equals(mine);
        if (!right && !"de_la_mano_i".equals(mine)) {
            return null;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        String want = right ? "de_la_mano_i" : "de_la_mano_d";
        net.minecraft.world.phys.Vec3 me = entity.getPosition(tickDelta);
        net.minecraft.world.entity.player.Player lead = null;
        double best = 3.0 * 3.0;
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (!want.equals(AnnisInteractClient.animOf(p))) {
                continue;
            }
            double d = p.getPosition(tickDelta).distanceToSqr(me);
            if (d < best) {
                best = d;
                lead = p;
            }
        }
        if (lead == null) {
            return null;
        }
        float yaw = Mth.rotLerp(tickDelta, lead.yBodyRotO, lead.yBodyRot);
        // a su derecha (x negativa en el modelo) o a su izquierda
        float side = (right ? -1 : 1) * com.annis.interact.Layout.HAND_SIDE * 16.0f;
        net.minecraft.world.phys.Vec3 at = HandLinkImpl.toWorld(lead.getPosition(tickDelta), yaw, side, 1.501f * 16.0f, 0);
        double dx = at.x - me.x, dz = at.z - me.z;
        if (dx * dx + dz * dz > 1.5 * 1.5) {
            return null; // muy lejos (se trabo con algo): se deja donde esta
        }
        if (yawOut != null) {
            yawOut[0] = yaw;
        }
        return new net.minecraft.world.phys.Vec3(at.x, me.y, at.z);
    }

    /**
     * De la mano: el NPC se dibuja exactamente al costado del jugador que lo lleva, con la posicion
     * y el giro de ESTE fotograma. Asi no queda atrasado (el servidor lo mueve y el cliente lo
     * dibuja con unos ticks de retraso) y las manos van siempre sincronizadas.
     */
    private static void handGlue(LivingEntity entity, float tickDelta, PoseStack matrices) {
        float[] yaw = new float[1];
        net.minecraft.world.phys.Vec3 at = gluedPos(entity, tickDelta, yaw);
        if (at == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 me = entity.getPosition(tickDelta);
        matrices.translate(at.x - me.x, 0, at.z - me.z);
        entity.yBodyRot = yaw[0];
        entity.yBodyRotO = yaw[0];
    }
}
