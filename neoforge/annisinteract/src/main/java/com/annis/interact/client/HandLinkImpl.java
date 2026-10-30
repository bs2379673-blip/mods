package com.annis.interact.client;

import com.annis.interact.client.anim.PairAnims;
import com.annis.npc.client.Activities;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Calcula donde se encuentran las manos usando la posicion y el giro reales de los dos. */
final class HandLinkImpl implements PairAnims.HandLink {
    @Override
    public float[] meet(boolean leftPerson, float t) {
        LivingEntity me = Activities.current();
        Minecraft mc = Minecraft.getInstance();
        if (me == null || mc.level == null) {
            return null;
        }
        String want = leftPerson ? "de_la_mano_i" : "de_la_mano_d";
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 myPos = me.getPosition(pt);
        Vec3 realPos = myPos;
        LivingEntity mate = null;
        double best = 3.5 * 3.5;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == me || !(e instanceof LivingEntity l) || !want.equals(AnnisInteractClient.animOf(e))) {
                continue;
            }
            double d = e.getPosition(pt).distanceToSqr(myPos);
            if (d < best) {
                best = d;
                mate = l;
            }
        }
        if (mate == null) {
            return null;
        }
        float myYaw = Mth.rotLerp(pt, me.yBodyRotO, me.yBodyRot);
        float mateYaw = Mth.rotLerp(pt, mate.yBodyRotO, mate.yBodyRot);
        Vec3 matePos = mate.getPosition(pt);
        // si alguno se dibuja pegado al jugador, se usa esa posicion (la que se ve)
        float[] gy = new float[1];
        Vec3 g = CarryRender.gluedPos(me, pt, gy);
        if (g != null) {
            myPos = g;
            myYaw = gy[0];
        }
        g = CarryRender.gluedPos(mate, pt, gy);
        if (g != null) {
            matePos = g;
            mateYaw = gy[0];
        }
        // hombros en el mundo: el mio (derecho si voy a la izquierda) y el suyo (el contrario)
        Vec3 mine = toWorld(myPos, myYaw, leftPerson ? -5 : 5, 2, 0);
        Vec3 his = toWorld(matePos, mateYaw, leftPerson ? 5 : -5, 2, 0);
        Vec3 mid = mine.add(his).scale(0.5);
        // las manos cuelgan un poco por debajo, adelantadas y balanceandose juntas
        float swing = 1.2f * Mth.sin(t * 0.33f);
        Vec3 fwd = Vec3.directionFromRotation(0, myYaw);
        Vec3 hand = mid.add(0, -8.6 / 16.0, 0).add(fwd.scale((1.4 + swing) / 16.0));
        return toModel(myPos, myYaw, hand);
    }

    /** Punto del modelo (px) de una entidad -> mundo. */
    static Vec3 toWorld(Vec3 pos, float bodyYaw, float x, float y, float z) {
        double rx = -x / 16.0, ry = 1.501 - y / 16.0, rz = z / 16.0;
        double a = Math.toRadians(180 - bodyYaw);
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(pos.x + rx * c + rz * s, pos.y + ry, pos.z - rx * s + rz * c);
    }

    /** Mundo -> modelo (px) de una entidad. */
    static float[] toModel(Vec3 pos, float bodyYaw, Vec3 w) {
        double dx = w.x - pos.x, dy = w.y - pos.y, dz = w.z - pos.z;
        double a = Math.toRadians(180 - bodyYaw);
        double c = Math.cos(a), s = Math.sin(a);
        // inversa de la rotacion (transpuesta)
        double rx = dx * c - dz * s, rz = dx * s + dz * c;
        return new float[]{(float) (-rx * 16), (float) ((1.501 - dy) * 16), (float) (rz * 16)};
    }
}
