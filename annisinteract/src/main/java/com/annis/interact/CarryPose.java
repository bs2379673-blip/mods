package com.annis.interact;

import com.annis.npc.link.EntityLinks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.function.Function;

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
        if (passenger.getWorld().isClient) {
            return clientAnim.apply(passenger);
        }
        return passenger instanceof LivingEntity l ? EntityLinks.currentAnim(l) : "";
    }

    /** Posicion de montura (la que devuelve getPassengerRidingPos) o null si no es nuestra. */
    public static Vec3d ridingPos(Entity vehicle, Entity passenger) {
        float[] off = offsets(anim(passenger));
        if (off == null) {
            return null;
        }
        float fwd = off[0], up = off[1], left = off[2];
        float yaw = vehicle instanceof LivingEntity l ? l.bodyYaw : vehicle.getYaw();
        float r = yaw * MathHelper.RADIANS_PER_DEGREE;
        double fx = -MathHelper.sin(r), fz = MathHelper.cos(r);
        double lx = MathHelper.cos(r), lz = MathHelper.sin(r);
        float scale = vehicle instanceof LivingEntity l ? l.getScale() : 1.0f;
        Vec3d feet = vehicle.getPos().add((fx * fwd + lx * left) * scale, up * scale, (fz * fwd + lz * left) * scale);
        // el juego le resta el punto de enganche del pasajero: lo sumamos para que los pies queden ahi
        return feet.add(passenger.getVehicleAttachmentPos(vehicle));
    }
}
