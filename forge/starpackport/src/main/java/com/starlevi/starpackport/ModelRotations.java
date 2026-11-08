package com.starlevi.starpackport;

import com.google.common.collect.MapMaker;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Giros de cubos como en 1.21.11: cualquier angulo, y varios ejes a la vez (x, y, z).
 * La matriz y el reescalado son los mismos que usa 1.21.11.
 */
public final class ModelRotations {
    private static final Map<BlockElementRotation, Matrix4f> XYZ = new MapMaker().weakKeys().concurrencyLevel(4).makeMap();
    private static final float DEG = (float) (Math.PI / 180.0);

    private ModelRotations() {
    }

    public static void registerXyz(BlockElementRotation rot, float x, float y, float z) {
        XYZ.put(rot, new Matrix4f().rotationZYX(z * DEG, y * DEG, x * DEG));
    }

    public static void apply(Vector3f pos, BlockElementRotation rot) {
        Matrix4f known = XYZ.get(rot);
        Matrix4f m;
        if (known != null) {
            m = new Matrix4f(known);
        } else {
            Vector3f axis = switch (rot.axis()) {
                case X -> new Vector3f(1, 0, 0);
                case Y -> new Vector3f(0, 1, 0);
                case Z -> new Vector3f(0, 0, 1);
            };
            m = new Matrix4f().rotation(rot.angle() * DEG, axis);
        }
        if (rot.rescale()) {
            m.scale(factor(m, 1, 0, 0), factor(m, 0, 1, 0), factor(m, 0, 0, 1));
        }
        Vector3f o = rot.origin();
        Vector3f v = new Vector3f(pos).sub(o);
        m.transformPosition(v);
        pos.set(v.add(o));
    }

    private static float factor(Matrix4f m, float x, float y, float z) {
        Vector3f d = m.transformDirection(new Vector3f(x, y, z));
        float max = Math.max(Math.abs(d.x), Math.max(Math.abs(d.y), Math.abs(d.z)));
        return max < 1e-6f ? 1f : 1f / max;
    }
}
