package com.starlevi.starspirits.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las formas que trae el mod. Todas comparten el cuerpito (bulbo con carita, patitas y bracitos) y
 * cambia lo que llevan arriba: sombrero de hongo, corona de margarita, loto, arbustito, bonsai,
 * campanita o rosa. Textura de 64x80 (ver ejemplo_textura.png en la carpeta del servidor).
 */
public final class Forms {
    public static final int TEX_W = 64, TEX_H = 80;
    private static final Map<String, SpiritRig> CACHE = new HashMap<>();

    private Forms() {
    }

    /** Arma modelos y anota el camino de cada pieza (para encontrarlas por nombre al animar). */
    public static final class Builder {
        final MeshDefinition mesh = new MeshDefinition();
        final Map<String, List<String>> paths = new HashMap<>();
        final Map<PartDefinition, List<String>> where = new HashMap<>();

        public Builder() {
            this.where.put(this.mesh.getRoot(), List.of());
        }

        public PartDefinition root() {
            return this.mesh.getRoot();
        }

        public PartDefinition add(PartDefinition parent, String name, CubeListBuilder cubes, PartPose pose) {
            PartDefinition d = parent.addOrReplaceChild(name, cubes, pose);
            List<String> path = new ArrayList<>(this.where.getOrDefault(parent, List.of()));
            path.add(name);
            this.where.put(d, path);
            this.paths.put(name, path);
            return d;
        }

        public SpiritRig bake(int w, int h) {
            return new SpiritRig(LayerDefinition.create(this.mesh, w, h).bakeRoot(), this.paths);
        }
    }

    public static synchronized SpiritRig get(String form) {
        return CACHE.computeIfAbsent(form, SoftForms::build);
    }

    private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }

    private static SpiritRig build(String form) {
        Builder b = new Builder();
        PartDefinition root = b.root();
        PartDefinition body = b.add(root, "cuerpo", box(0, 0, -3, -6, -3, 6, 6, 6), PartPose.offset(0, 21, 0));
        b.add(root, "pierna_der", box(24, 0, -1, 0, -1, 2, 3, 2), PartPose.offset(-1.5f, 21, 0));
        b.add(root, "pierna_izq", box(32, 0, -1, 0, -1, 2, 3, 2), PartPose.offset(1.5f, 21, 0));
        b.add(body, "brazo_der", box(40, 0, -1, 0, -0.5f, 1, 3, 1), PartPose.offset(-3, -3.5f, 0));
        b.add(body, "brazo_izq", box(44, 0, 0, 0, -0.5f, 1, 3, 1), PartPose.offset(3, -3.5f, 0));
        b.add(body, "parpados", box(48, 0, -2.5f, -4.2f, -3.3f, 5, 2, 1), PartPose.ZERO);
        PartDefinition top = b.add(body, "adorno", CubeListBuilder.create(), PartPose.offset(0, -6, 0));
        float topY;
        switch (form) {
            case "flor" -> {
                b.add(top, "petalos", box(0, 25, -6, 0, -6, 12, 0, 12), PartPose.offset(0, -0.6f, 0));
                b.add(top, "flor_centro", box(48, 25, -2, -1.6f, -2, 4, 2, 4), PartPose.ZERO);
                topY = 21 - 6 - 2;
            }
            case "loto" -> {
                for (int i = 0; i < 6; i++) {
                    b.add(top, "petalo_" + i, box(44, 37, -1.5f, 0, -6, 3, 0, 6),
                            PartPose.offsetAndRotation(0, -0.2f, 0, -0.95f, i * Mth.TWO_PI / 6, 0));
                }
                b.add(top, "flor_centro", box(48, 25, -2, -1.2f, -2, 4, 2, 4), PartPose.ZERO);
                topY = 21 - 6 - 4;
            }
            case "arbusto" -> {
                b.add(top, "tallo", box(60, 0, -0.5f, -3, -0.5f, 1, 3, 1), PartPose.ZERO);
                b.add(top, "hoja_copa", box(28, 37, -2, -6, -2, 4, 3, 4), PartPose.ZERO);
                b.add(top, "hoja_a", box(44, 60, 0, 0, -1, 3, 0, 2), PartPose.offsetAndRotation(0.3f, -2.2f, 0, 0, 0, -0.5f));
                b.add(top, "hoja_b", box(44, 60, -3, 0, -1, 3, 0, 2), PartPose.offsetAndRotation(-0.3f, -1.4f, 0, 0, 0.3f, 0.5f));
                topY = 21 - 6 - 6;
            }
            case "bonsai" -> {
                b.add(top, "tronco", box(60, 0, -0.5f, -4, -0.5f, 1, 4, 1), PartPose.ZERO);
                b.add(top, "copa_arbol", box(0, 37, -3.5f, -8.5f, -3.5f, 7, 5, 7), PartPose.ZERO);
                b.add(top, "hoja_copa", box(28, 37, -2, -2, -2, 4, 3, 4), PartPose.offset(1.5f, -9.5f, 0.5f));
                topY = 21 - 6 - 11.5f;
            }
            case "campana" -> {
                b.add(top, "tallito", box(60, 0, -0.5f, -6.5f, -0.5f, 1, 2, 1), PartPose.ZERO);
                b.add(top, "campana", box(0, 49, -3, -5, -3, 6, 5, 6), PartPose.ZERO);
                b.add(top, "campana_borde", box(24, 49, -3.5f, -1, -3.5f, 7, 1, 7), PartPose.ZERO);
                topY = 21 - 6 - 6.5f;
            }
            case "rosa" -> {
                b.add(top, "rosa_base", box(20, 60, -3, -2, -3, 6, 2, 6), PartPose.ZERO);
                b.add(top, "rosa", box(0, 60, -2.5f, -5.5f, -2.5f, 5, 4, 5), PartPose.ZERO);
                b.add(top, "hoja_a", box(44, 60, 0, 0, -1, 3, 0, 2), PartPose.offsetAndRotation(2.5f, -1.2f, 0, 0, 0, -0.35f));
                b.add(top, "hoja_b", box(44, 60, -3, 0, -1, 3, 0, 2), PartPose.offsetAndRotation(-2.5f, -1.2f, 0, 0, 0, 0.35f));
                topY = 21 - 6 - 5.5f;
            }
            default -> { // hongo
                b.add(top, "copa", box(0, 12, -5, -3.5f, -5, 10, 3, 10), PartPose.ZERO);
                b.add(top, "copa_top", box(40, 12, -3, -5.5f, -3, 6, 2, 6), PartPose.ZERO);
                topY = 21 - 6 - 5.5f;
            }
        }
        SpiritRig rig = b.bake(TEX_W, TEX_H);
        rig.topY = 24 - topY;
        return rig;
    }
}
