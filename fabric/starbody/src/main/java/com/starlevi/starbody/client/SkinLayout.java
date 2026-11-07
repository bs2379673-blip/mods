package com.starlevi.starbody.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Donde cae cada pixel de una skin de 64x64 en el cuerpo: para la cabeza y el torso (y sus capas de
 * afuera, gorro y chaqueta) dice que cara es, en que parte del modelo y su posicion en 3D (en
 * pixeles del modelo, con la y para abajo como Minecraft).
 */
public final class SkinLayout {
    public enum Part {
        HEAD, BODY
    }

    /** Una cara lateral de una caja: derecha, frente, izquierda o atras (del personaje). */
    public record Face(Part part, boolean outer, int side, int u0, int v0, int width, int height,
                       float ax, float az, float bx, float bz, float yTop, float yBottom, float nx, float nz, String label) {
        /** Punto horizontal en la cara para una posicion a lo ancho (0..width). */
        public float x(float a) {
            return this.ax + (this.bx - this.ax) * a / this.width;
        }

        public float z(float a) {
            return this.az + (this.bz - this.az) * a / this.width;
        }

        public float y(float b) {
            return this.yTop + (this.yBottom - this.yTop) * b / this.height;
        }

        public boolean contains(int u, int v) {
            return u >= this.u0 && u < this.u0 + this.width && v >= this.v0 && v < this.v0 + this.height;
        }
    }

    private static final String[] SIDES = {"derecha", "frente", "izquierda", "atrás"};
    public static final List<Face> FACES = new ArrayList<>();

    static {
        box(Part.HEAD, false, 0, 0, -4, -8, -4, 8, 8, 8, 0, "Cabeza");
        box(Part.HEAD, true, 32, 0, -4, -8, -4, 8, 8, 8, 0.5f, "Gorro (capa de afuera)");
        box(Part.BODY, false, 16, 16, -4, 0, -2, 8, 12, 4, 0, "Torso");
        box(Part.BODY, true, 16, 32, -4, 0, -2, 8, 12, 4, 0.25f, "Chaqueta (capa de afuera)");
    }

    private static void box(Part part, boolean outer, int U, int V, float ox, float oy, float oz, int w, int h, int d, float g, String label) {
        float minX = ox - g, maxX = ox + w + g, minZ = oz - g, maxZ = oz + d + g, minY = oy - g, maxY = oy + h + g;
        int v0 = V + d;
        // en la textura, en fila: derecha | frente | izquierda | atras (dando la vuelta a la caja)
        FACES.add(new Face(part, outer, 0, U, v0, d, h, minX, maxZ, minX, minZ, minY, maxY, -1, 0, label + " · " + SIDES[0]));
        FACES.add(new Face(part, outer, 1, U + d, v0, w, h, minX, minZ, maxX, minZ, minY, maxY, 0, -1, label + " · " + SIDES[1]));
        FACES.add(new Face(part, outer, 2, U + d + w, v0, d, h, maxX, minZ, maxX, maxZ, minY, maxY, 1, 0, label + " · " + SIDES[2]));
        FACES.add(new Face(part, outer, 3, U + 2 * d + w, v0, w, h, maxX, maxZ, minX, maxZ, minY, maxY, 0, 1, label + " · " + SIDES[3]));
    }

    /** La cara donde cae el pixel (solo caras laterales de cabeza y torso), o null. */
    public static Face at(int u, int v) {
        for (Face f : FACES) {
            if (f.contains(u, v)) {
                return f;
            }
        }
        return null;
    }

    private SkinLayout() {
    }
}
