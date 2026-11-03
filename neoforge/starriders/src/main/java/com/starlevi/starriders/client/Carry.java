package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Jinetes pegados al cuerpo de la montura: cada renderer guarda, por asiento, cuanto se movio y giro la
 * parte del cuerpo donde va sentado (respecto de la pose de referencia), en los ejes de la montura
 * (x costado, y arriba, z adelante, sin el giro). Al dibujar al jinete se le aplica lo mismo, asi sube,
 * baja, se inclina y se ladea junto con el lomo en vez de flotar quieto en el punto de montar.
 */
public final class Carry {
    private static final Map<Entity, Matrix4f[]> MAP = new WeakHashMap<>();

    private Carry() {
    }

    /** Matrices de los asientos de la montura (se crean la primera vez). */
    public static Matrix4f[] of(Entity mount, int seats) {
        Matrix4f[] m = MAP.get(mount);
        if (m == null || m.length != seats) {
            m = new Matrix4f[seats];
            for (int i = 0; i < seats; i++) {
                m[i] = new Matrix4f();
            }
            MAP.put(mount, m);
        }
        return m;
    }

    /** La del asiento, o null si la montura todavia no se dibujo. */
    public static Matrix4f get(Entity mount, int seat) {
        Matrix4f[] m = MAP.get(mount);
        return m == null || seat < 0 || seat >= m.length ? null : m[seat];
    }

    /** Aplica el movimiento del asiento al jinete (la pila esta en la posicion del jinete). */
    public static void apply(PoseStack ps, LivingEntity mount, Entity rider, float partial, Matrix4f local) {
        double rx = Mth.lerp(partial, rider.xo, rider.getX()) - Mth.lerp(partial, mount.xo, mount.getX());
        double ry = Mth.lerp(partial, rider.yo, rider.getY()) - Mth.lerp(partial, mount.yo, mount.getY());
        double rz = Mth.lerp(partial, rider.zo, rider.getZ()) - Mth.lerp(partial, mount.zo, mount.getZ());
        float yaw = Mth.rotLerp(partial, mount.yBodyRotO, mount.yBodyRot);
        ps.translate(-rx, -ry, -rz);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(local);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(rx, ry, rz);
    }
}
