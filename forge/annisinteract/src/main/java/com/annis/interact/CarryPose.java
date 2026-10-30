package com.annis.interact;

import com.annis.npc.link.EntityLinks;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Donde va el que es llevado en brazos o a caballito (mismo calculo en servidor y cliente). */
public final class CarryPose {
    /** En el cliente se cambia por la animacion que ve el cliente. */
    public static Function<Entity, String> clientAnim = e -> "";
    /** En el cliente: true si la entidad esta fija por una interaccion (no se empuja). */
    public static java.util.function.Predicate<Entity> clientAnchored = e -> false;

    /** Interacciones por archivo con alguien encima: animacion de quien va encima -> {adelante, arriba, izquierda}. */
    public static final java.util.Map<String, float[]> CUSTOM = new java.util.concurrent.ConcurrentHashMap<>();

    private CarryPose() {
    }

    /** {adelante, arriba, izquierda} para la animacion de quien va encima, o null. */
    public static float[] offsets(String anim) {
        if ("en_brazos".equals(anim)) {
            return new float[]{Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT};
        }
        if ("a_caballito".equals(anim)) {
            return new float[]{Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT};
        }
        return anim == null ? null : CUSTOM.get(anim);
    }

    private static String anim(Entity passenger) {
        if (passenger.level().isClientSide) {
            return clientAnim.apply(passenger);
        }
        return passenger instanceof LivingEntity l ? EntityLinks.currentAnim(l) : "";
    }

    /** Posicion de montura (la que devuelve getPassengerRidingPos) o null si no es nuestra. */
    public static Vec3 ridingPos(Entity vehicle, Entity passenger) {
        float[] off = offsets(anim(passenger));
        if (off == null) {
            return null;
        }
        float fwd = off[0], up = off[1], left = off[2];
        float yaw = vehicle instanceof LivingEntity l ? l.yBodyRot : vehicle.getYRot();
        float r = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r), fz = Mth.cos(r);
        double lx = Mth.cos(r), lz = Mth.sin(r);
        float scale = vehicle instanceof LivingEntity l ? l.getScale() : 1.0f;
        // posicion de los pies del pasajero (en 1.20.1 se le pone directo en positionRider)
        return vehicle.position().add((fx * fwd + lx * left) * scale, up * scale, (fz * fwd + lz * left) * scale);
    }
}
