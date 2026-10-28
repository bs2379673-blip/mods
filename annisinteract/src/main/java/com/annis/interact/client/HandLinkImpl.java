package com.annis.interact.client;

import com.annis.interact.client.anim.PairAnims;
import com.annis.npc.client.Activities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Calcula donde se encuentran las manos usando la posicion y el giro reales de los dos. */
final class HandLinkImpl implements PairAnims.HandLink {
    @Override
    public float[] meet(boolean leftPerson, float t) {
        LivingEntity me = Activities.current();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (me == null || mc.world == null) {
            return null;
        }
        String want = leftPerson ? "de_la_mano_i" : "de_la_mano_d";
        float pt = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d myPos = me.getLerpedPos(pt);
        LivingEntity mate = null;
        double best = 3.5 * 3.5;
        for (Entity e : mc.world.getEntities()) {
            if (e == me || !(e instanceof LivingEntity l) || !want.equals(AnnisInteractClient.animOf(e))) {
                continue;
            }
            double d = e.getLerpedPos(pt).squaredDistanceTo(myPos);
            if (d < best) {
                best = d;
                mate = l;
            }
        }
        if (mate == null) {
            return null;
        }
        float myYaw = MathHelper.lerpAngleDegrees(pt, me.prevBodyYaw, me.bodyYaw);
        float mateYaw = MathHelper.lerpAngleDegrees(pt, mate.prevBodyYaw, mate.bodyYaw);
        // hombros en el mundo: el mio (derecho si voy a la izquierda) y el suyo (el contrario)
        Vec3d mine = toWorld(myPos, myYaw, leftPerson ? -5 : 5, 2, 0);
        Vec3d his = toWorld(mate.getLerpedPos(pt), mateYaw, leftPerson ? 5 : -5, 2, 0);
        Vec3d mid = mine.add(his).multiply(0.5);
        // las manos cuelgan un poco por debajo, adelantadas y balanceandose juntas
        float swing = 1.2f * MathHelper.sin(t * 0.33f);
        Vec3d fwd = Vec3d.fromPolar(0, myYaw);
        Vec3d hand = mid.add(0, -8.6 / 16.0, 0).add(fwd.multiply((1.4 + swing) / 16.0));
        return toModel(myPos, myYaw, hand);
    }

    /** Punto del modelo (px) de una entidad -> mundo. */
    static Vec3d toWorld(Vec3d pos, float bodyYaw, float x, float y, float z) {
        double rx = -x / 16.0, ry = 1.501 - y / 16.0, rz = z / 16.0;
        double a = Math.toRadians(180 - bodyYaw);
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(pos.x + rx * c + rz * s, pos.y + ry, pos.z - rx * s + rz * c);
    }

    /** Mundo -> modelo (px) de una entidad. */
    static float[] toModel(Vec3d pos, float bodyYaw, Vec3d w) {
        double dx = w.x - pos.x, dy = w.y - pos.y, dz = w.z - pos.z;
        double a = Math.toRadians(180 - bodyYaw);
        double c = Math.cos(a), s = Math.sin(a);
        // inversa de la rotacion (transpuesta)
        double rx = dx * c - dz * s, rz = dx * s + dz * c;
        return new float[]{(float) (-rx * 16), (float) ((1.501 - dy) * 16), (float) (rz * 16)};
    }
}
