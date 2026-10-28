package com.annis.npc.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modelo articulado: brazos y piernas partidos en dos bloques (codo y rodilla en la mitad del
 * miembro, como bendy-lib/Emotecraft), manteniendo el aspecto cuadrado de Minecraft.
 * Usa la misma skin de 64x64: cada mitad toma su parte de la textura.
 */
public final class JointModel {
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    private static final Set<Direction> SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    // Ojo: en ModelPart.Cuboid la cara DOWN esta en la Y minima del modelo (el modelo tiene Y hacia abajo),
    // o sea es la tapa de ARRIBA del miembro y usa la textura "superior"; UP es la de abajo (palma / suela).
    private static final Set<Direction> SIDES_TOP = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN);
    private static final Set<Direction> BOTTOM = EnumSet.of(Direction.UP);

    /** Mitad del miembro donde va la bisagra (pixeles desde arriba del cubo). */
    public static final float SPLIT = 6.0f;

    private JointModel() {
    }

    private static ModelPart.Cuboid box(int u, int v, float x, float y, float z, float w, float h, float d,
                                        float dil, Set<Direction> faces) {
        return new ModelPart.Cuboid(u, v, x, y, z, w, h, d, dil, dil, dil, false, 64.0f, 64.0f, faces);
    }

    private static ModelPart part(List<ModelPart.Cuboid> cuboids, Map<String, ModelPart> children,
                                  float px, float py, float pz) {
        ModelPart p = new ModelPart(cuboids, children);
        p.setPivot(px, py, pz);
        p.setDefaultTransform(p.getTransform());
        return p;
    }

    /**
     * Un miembro de 12 de alto partido en dos. La mitad de abajo es el hijo "lower", con su pivote en
     * la bisagra; se solapa 1 pixel con la de arriba para que no se abra un hueco al doblar.
     */
    private static ModelPart limb(int u, int v, float x0, float yTop, float w, float dil,
                                  float px, float py, float pz) {
        float split = SPLIT;
        float rest = 12.0f - split;
        ModelPart lower = part(List.of(
                        box(u, (int) (v + split - 1), x0, -1.0f, -2.0f, w, rest + 1.0f, 4.0f, dil, SIDES),
                        box(u, v, x0, -split, -2.0f, w, 12.0f, 4.0f, dil, BOTTOM)),
                Map.of(), 0.0f, yTop + split, 0.0f);
        Map<String, ModelPart> ch = new HashMap<>();
        ch.put("lower", lower);
        return part(List.of(box(u, v, x0, yTop, -2.0f, w, split, 4.0f, dil, SIDES_TOP)), ch, px, py, pz);
    }

    /** Raiz con todas las partes que espera PlayerEntityModel. */
    public static ModelPart build(boolean slim) {
        Map<String, ModelPart> c = new HashMap<>();
        c.put("head", part(List.of(box(0, 0, -4, -8, -4, 8, 8, 8, 0.0f, ALL)), Map.of(), 0, 0, 0));
        c.put("hat", part(List.of(box(32, 0, -4, -8, -4, 8, 8, 8, 0.5f, ALL)), Map.of(), 0, 0, 0));
        // torso partido en pecho (arriba) y cintura (hijo "lower"): articulacion de la espalda
        c.put("body", limb(16, 16, -4.0f, 0.0f, 8.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        c.put("jacket", limb(16, 32, -4.0f, 0.0f, 8.0f, 0.25f, 0.0f, 0.0f, 0.0f));
        float aw = slim ? 3.0f : 4.0f;
        float ay = slim ? 2.5f : 2.0f;
        float rx0 = slim ? -2.0f : -3.0f;
        c.put("right_arm", limb(40, 16, rx0, -2.0f, aw, 0.0f, -5.0f, ay, 0.0f));
        c.put("right_sleeve", limb(40, 32, rx0, -2.0f, aw, 0.25f, -5.0f, ay, 0.0f));
        c.put("left_arm", limb(32, 48, -1.0f, -2.0f, aw, 0.0f, 5.0f, ay, 0.0f));
        c.put("left_sleeve", limb(48, 48, -1.0f, -2.0f, aw, 0.25f, 5.0f, ay, 0.0f));
        c.put("right_leg", limb(0, 16, -2.0f, 0.0f, 4.0f, 0.0f, -1.9f, 12.0f, 0.0f));
        c.put("right_pants", limb(0, 32, -2.0f, 0.0f, 4.0f, 0.25f, -1.9f, 12.0f, 0.0f));
        c.put("left_leg", limb(16, 48, -2.0f, 0.0f, 4.0f, 0.0f, 1.9f, 12.0f, 0.0f));
        c.put("left_pants", limb(0, 48, -2.0f, 0.0f, 4.0f, 0.25f, 1.9f, 12.0f, 0.0f));
        c.put("ear", part(List.of(), Map.of(), 0, 0, 0));
        c.put("cloak", part(List.of(), Map.of(), 0, 0, 0));
        return new ModelPart(List.of(), c);
    }

    /** Matriz de giro alrededor de un eje (bendy-lib: eje (cos a, 0, sin a)) pasada a los angulos de ModelPart. */
    public static void setAxisRotation(ModelPart p, float axisAngle, float angle) {
        double ax = Math.cos(axisAngle);
        double az = Math.sin(axisAngle);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1 - c;
        // Rodrigues con eje (ax, 0, az)
        double r00 = c + ax * ax * t;
        double r10 = az * s;
        double r20 = ax * az * t;
        double r21 = ax * s; // Rodrigues: s*ux + t*uz*uy (uy = 0)
        double r22 = c + az * az * t;
        setFromMatrix(p, r00, r10, r20, r21, r22);
    }

    /** Descompone R = Rz(roll) Ry(yaw) Rx(pitch) (el orden de ModelPart.rotate). */
    public static void setFromMatrix(ModelPart p, double r00, double r10, double r20, double r21, double r22) {
        p.yaw = (float) Math.asin(Math.max(-1.0, Math.min(1.0, -r20)));
        p.pitch = (float) Math.atan2(r21, r22);
        p.roll = (float) Math.atan2(r10, r00);
    }
}
