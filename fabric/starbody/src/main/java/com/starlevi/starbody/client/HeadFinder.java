package com.starlevi.starbody.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starbody.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca la cabeza que de verdad se dibuja: la caja de 8x8x8 con la textura de la cara (no la del gorro),
 * visible, en cualquier parte del modelo. Asi, si un paquete de animaciones (EMF / Fresh Animations)
 * reemplaza o mueve la cabeza con sus propias piezas, el pelo se pega a esa y la sigue siempre.
 */
final class HeadFinder {
    /** El camino desde la raiz hasta la parte de la cabeza, y la esquina de su caja. */
    record Head(List<ModelPart> chain, float minX, float minY, float minZ) {
        /** Pone ps en el espacio de la cabeza de un jugador (cuello en 0,0,0, en pixeles). */
        void apply(PoseStack ps) {
            for (ModelPart p : this.chain) {
                p.translateAndRotate(ps);
            }
            ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // la caja de la cabeza de Minecraft va de (-4,-8,-4) a (4,0,4)
            ps.translate(this.minX + 4, this.minY + 8, this.minZ + 4);
        }
    }

    private HeadFinder() {
    }

    static Head find(ModelPart root) {
        List<ModelPart> path = new ArrayList<>();
        Head[] best = new Head[1];
        walk(root, path, best, 0);
        return best[0];
    }

    private static int bestDepth;

    private static void walk(ModelPart p, List<ModelPart> path, Head[] best, int depth) {
        if (!p.visible) {
            return;
        }
        path.add(p);
        if (!p.skipDraw) {
            for (ModelPart.Cube c : ((ModelPartAccessor) (Object) p).starbody$cubes()) {
                if (isHead(c) && (best[0] == null || depth > bestDepth)) {
                    best[0] = new Head(new ArrayList<>(path), c.minX, c.minY, c.minZ);
                    bestDepth = depth;
                }
            }
        }
        for (ModelPart ch : ((ModelPartAccessor) (Object) p).starbody$children().values()) {
            walk(ch, path, best, depth + 1);
        }
        path.remove(path.size() - 1);
        if (depth == 0) {
            bestDepth = 0;
        }
    }

    /** Una caja de 8x8x8 con la textura de la cara (la mitad izquierda de arriba de la skin). */
    private static boolean isHead(ModelPart.Cube c) {
        if (Math.abs(c.maxX - c.minX - 8) > 0.05f || Math.abs(c.maxY - c.minY - 8) > 0.05f || Math.abs(c.maxZ - c.minZ - 8) > 0.05f) {
            return false;
        }
        if (c.polygons.length == 0 || c.polygons[0].vertices().length == 0) {
            return false;
        }
        float u = 0, v = 0;
        int n = 0;
        for (ModelPart.Polygon pg : c.polygons) {
            for (ModelPart.Vertex vx : pg.vertices()) {
                u += vx.u();
                v += vx.v();
                n++;
            }
        }
        u /= n;
        v /= n;
        return u < 0.5f && v < 0.25f;
    }
}
