package com.starlevi.starspirits.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las formas del mod con mallas redondeadas: cuerpito de gotita con carita, patitas y bracitos, y
 * arriba el sombrero de hongo, la corona de margarita, el loto, el arbustito, el bonsai, la
 * campanita o la rosa. Textura de 256x256 pintada (zonas en {@link Mesh.Uv}).
 */
public final class SoftForms {
    /** Ojos en la textura del cuerpo (u, v dentro de la zona BODY): los usa tambien tools/texturas.py. */
    public static final float EYE_U = 0.062f, EYE_V = 0.53f;
    static final float BX = 3.7f, BY = 3.9f, BZ = 3.5f, BCY = -3.6f;

    private SoftForms() {
    }

    /** Hueso: nombre, malla, pose y sus hijos. */
    public static final class Node {
        final String name;
        final Mesh mesh;
        final PartPose pose;
        final List<Node> kids = new ArrayList<>();

        public Node(String name, Mesh mesh, PartPose pose) {
            this.name = name;
            this.mesh = mesh;
            this.pose = pose;
        }

        public Node add(Node k) {
            this.kids.add(k);
            return k;
        }

        public ModelPart bake(List<String> path, Map<String, List<String>> paths) {
            Map<String, ModelPart> children = new LinkedHashMap<>();
            for (Node k : this.kids) {
                List<String> p = new ArrayList<>(path);
                p.add(k.name);
                paths.put(k.name, p);
                children.put(k.name, k.bake(p, paths));
            }
            ModelPart part = new ModelPart(this.mesh == null ? List.of() : List.of(this.mesh.cube()), children);
            part.setInitialPose(this.pose);
            part.loadPose(this.pose);
            return part;
        }
    }

    public static SpiritRig build(String form) {
        Node root = new Node("raiz", null, PartPose.ZERO);
        Node body = root.add(new Node("cuerpo", new Mesh().ellipsoid(0, BCY, 0, BX, BY, BZ, 0, Mth.PI, 24, 14, Mesh.Uv.BODY),
                PartPose.offset(0, 21, 0)));
        root.add(new Node("pierna_der", new Mesh().ellipsoid(0, 1.6f, -0.3f, 1.25f, 1.5f, 1.45f, 0, Mth.PI, 10, 6, Mesh.Uv.LIMB),
                PartPose.offset(-1.6f, 21, 0)));
        root.add(new Node("pierna_izq", new Mesh().ellipsoid(0, 1.6f, -0.3f, 1.25f, 1.5f, 1.45f, 0, Mth.PI, 10, 6, Mesh.Uv.LIMB),
                PartPose.offset(1.6f, 21, 0)));
        body.add(new Node("brazo_der", new Mesh().ellipsoid(-0.3f, 1.3f, 0, 0.8f, 1.5f, 0.85f, 0, Mth.PI, 8, 6, Mesh.Uv.LIMB),
                PartPose.offset(-3.4f, -3.2f, 0)));
        body.add(new Node("brazo_izq", new Mesh().ellipsoid(0.3f, 1.3f, 0, 0.8f, 1.5f, 0.85f, 0, Mth.PI, 8, 6, Mesh.Uv.LIMB),
                PartPose.offset(3.4f, -3.2f, 0)));
        Mesh lids = new Mesh();
        for (int side = -1; side <= 1; side += 2) {
            lids.patch(Mesh.onEllipsoid(0, BCY, 0, BX, BY, BZ, 0.5f + side * EYE_U, EYE_V, 0, Mth.PI), 0.75f, Mesh.Uv.LID);
        }
        body.add(new Node("parpados", lids, PartPose.ZERO));
        Node top = body.add(new Node("adorno", null, PartPose.offset(0, -7.1f, 0)));
        float topY; // altura del modelo (pixeles sobre el piso)
        switch (form) {
            case "flor" -> {
                Mesh one = new Mesh().petal(1.3f, 5.4f, 2.4f, 0.1f, 0.3f, 0.35f, 6, Mesh.Uv.PETAL);
                Mesh ring = new Mesh();
                for (int i = 0; i < 11; i++) {
                    ring.addRotatedY(one, i * Mth.TWO_PI / 11, 0, 0, 0);
                }
                top.add(new Node("petalos", ring, PartPose.offset(0, -0.1f, 0)));
                top.add(new Node("flor_centro", new Mesh().ellipsoid(0, -0.5f, 0, 2.3f, 1.2f, 2.3f, 0, Mth.PI, 16, 8, Mesh.Uv.CENTER), PartPose.ZERO));
                topY = 3 + 7.1f + 1.7f;
            }
            case "loto" -> {
                Mesh outer = new Mesh().petal(0.8f, 5.6f, 3.3f, 0.75f, 0.55f, 0.9f, 7, Mesh.Uv.PETAL);
                for (int i = 0; i < 6; i++) {
                    top.add(new Node("petalo_" + i, outer, PartPose.offsetAndRotation(0, 0.2f, 0, 0, i * Mth.TWO_PI / 6 + 0.3f, 0)));
                }
                Mesh inner = new Mesh();
                Mesh small = new Mesh().petal(0.4f, 3.8f, 2.6f, 1.1f, 0.4f, 0.7f, 6, Mesh.Uv.PETAL);
                for (int i = 0; i < 5; i++) {
                    inner.addRotatedY(small, i * Mth.TWO_PI / 5, 0, 0, 0);
                }
                top.add(new Node("flor_interior", inner, PartPose.ZERO));
                top.add(new Node("centro", new Mesh().ellipsoid(0, -0.6f, 0, 1.4f, 1.0f, 1.4f, 0, Mth.PI, 12, 8, Mesh.Uv.CENTER), PartPose.ZERO));
                topY = 3 + 7.1f + 5.5f;
            }
            case "arbusto" -> {
                top.add(new Node("tallo", new Mesh().lathe(0, 0, 0, new float[][]{{0.4f, -2.6f}, {0.45f, -1.2f}, {0.55f, 0.6f}}, 8, Mesh.Uv.STEM), PartPose.ZERO));
                Mesh bush = new Mesh()
                        .ellipsoid(0, -4.0f, 0, 2.4f, 2.1f, 2.3f, 0, Mth.PI, 16, 10, Mesh.Uv.CAP)
                        .ellipsoid(-1.7f, -3.0f, 0.5f, 1.6f, 1.4f, 1.6f, 0, Mth.PI, 12, 8, Mesh.Uv.CAP)
                        .ellipsoid(1.6f, -3.2f, -0.4f, 1.7f, 1.5f, 1.6f, 0, Mth.PI, 12, 8, Mesh.Uv.CAP);
                top.add(new Node("hoja_copa", bush, PartPose.ZERO));
                Mesh leaf = new Mesh().petal(0.2f, 4.4f, 2.4f, 0.5f, 0.2f, 0.35f, 6, Mesh.Uv.LEAF);
                top.add(new Node("hoja_a", leaf, PartPose.offsetAndRotation(0.3f, -0.6f, 0, 0, -Mth.HALF_PI, 0)));
                top.add(new Node("hoja_b", leaf, PartPose.offsetAndRotation(-0.3f, -1.1f, 0.2f, 0, Mth.HALF_PI + 0.3f, 0)));
                topY = 3 + 7.1f + 6.1f;
            }
            case "bonsai" -> {
                top.add(new Node("tronco", new Mesh().lathe(0, 0, 0, new float[][]{{0.45f, -4.8f}, {0.55f, -3.0f}, {0.7f, -1.0f}, {1.0f, 0.6f}}, 10, Mesh.Uv.STEM), PartPose.ZERO));
                Mesh crown = new Mesh()
                        .ellipsoid(0, -6.4f, 0, 3.3f, 2.2f, 3.0f, 0, Mth.PI, 18, 10, Mesh.Uv.CAP)
                        .ellipsoid(-2.7f, -5.5f, 0.4f, 2.0f, 1.6f, 1.9f, 0, Mth.PI, 12, 8, Mesh.Uv.CAP)
                        .ellipsoid(2.6f, -5.7f, -0.3f, 2.1f, 1.7f, 2.0f, 0, Mth.PI, 12, 8, Mesh.Uv.CAP)
                        .ellipsoid(0.3f, -7.9f, 0.2f, 2.0f, 1.5f, 1.9f, 0, Mth.PI, 12, 8, Mesh.Uv.CAP);
                top.add(new Node("copa_arbol", crown, PartPose.ZERO));
                top.add(new Node("hoja_copa", new Mesh().ellipsoid(0, 0, 0, 1.5f, 1.2f, 1.4f, 0, Mth.PI, 10, 6, Mesh.Uv.EXTRA),
                        PartPose.offset(3.0f, -3.4f, 0.3f)));
                topY = 3 + 7.1f + 9.4f;
            }
            case "campana" -> {
                top.add(new Node("tallito", new Mesh().lathe(0, 0, 0, new float[][]{{0.25f, -8.4f}, {0.3f, -7.4f}, {0.35f, -6.4f}}, 6, Mesh.Uv.STEM), PartPose.ZERO));
                Mesh bell = new Mesh().lathe(0, 0, 0, new float[][]{{0.2f, -6.7f}, {1.6f, -6.4f}, {2.7f, -5.4f}, {3.2f, -3.6f},
                        {3.6f, -1.7f}, {4.4f, -0.2f}, {5.3f, 0.6f}, {5.6f, 0.9f}}, 24, Mesh.Uv.CAP);
                top.add(new Node("campana", bell, PartPose.ZERO));
                topY = 3 + 7.1f + 8.4f;
            }
            case "rosa" -> {
                top.add(new Node("rosa", new Mesh().ellipsoid(0, -2.4f, 0, 2.4f, 2.7f, 2.4f, 0, Mth.PI, 16, 10, Mesh.Uv.CAP), PartPose.ZERO));
                Mesh one = new Mesh().petal(1.6f, 3.6f, 3.4f, 0.85f, 0.7f, 0.9f, 6, Mesh.Uv.PETAL);
                Mesh ring = new Mesh();
                for (int i = 0; i < 5; i++) {
                    ring.addRotatedY(one, i * Mth.TWO_PI / 5 + 0.2f, 0, 0.3f, 0);
                }
                top.add(new Node("rosa_petalos", ring, PartPose.ZERO));
                Mesh leaf = new Mesh().petal(0.2f, 3.4f, 1.9f, 0.25f, 0.35f, 0.3f, 6, Mesh.Uv.LEAF);
                top.add(new Node("hoja_a", leaf, PartPose.offsetAndRotation(2.3f, -0.5f, 0, 0, -Mth.HALF_PI, 0)));
                top.add(new Node("hoja_b", leaf, PartPose.offsetAndRotation(-2.3f, -0.6f, 0, 0, Mth.HALF_PI, 0)));
                topY = 3 + 7.1f + 5.2f;
            }
            default -> { // hongo
                Mesh cap = new Mesh().ellipsoid(0, 0.6f, 0, 6.2f, 4.8f, 6.2f, 0, Mth.HALF_PI, 26, 9, Mesh.Uv.CAP)
                        .disc(0, 0.6f, 0, 6.2f, 26, Mesh.Uv.UNDER);
                top.add(new Node("copa", cap, PartPose.ZERO));
                topY = 3 + 7.1f + 4.2f;
            }
        }
        Map<String, List<String>> paths = new HashMap<>();
        ModelPart part = root.bake(new ArrayList<>(), paths);
        SpiritRig rig = new SpiritRig(part, paths);
        rig.topY = topY;
        return rig;
    }
}
