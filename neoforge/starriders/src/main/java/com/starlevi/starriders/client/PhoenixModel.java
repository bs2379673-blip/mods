package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo del Fenix (mallas suaves, textura de 512x512). Huesos: cuerpo, cuello_1, cuello_2, cabeza
 * (pico y cresta), alas de tres partes (brazo, antebrazo, mano) con plumas, cola de plumas largas en
 * dos tramos y patas. Las animaciones estan en {@link PhoenixAnim}.
 */
public final class PhoenixModel {
    /** Zonas de la textura (pixeles). Las mismas usa tools/fenix_textura.py. */
    static final float[] BODY = {0, 0, 256, 128}, NECK = {256, 0, 384, 64}, HEAD = {384, 0, 512, 64},
            BEAK = {256, 64, 320, 96}, CREST = {320, 64, 384, 128}, LEG = {384, 64, 448, 96}, FOOT = {448, 64, 512, 96},
            WING_FEATHER = {0, 128, 128, 256}, COVERT = {128, 128, 256, 256}, ARM = {384, 96, 512, 128},
            TAIL_A = {256, 128, 320, 320}, TAIL_B = {256, 320, 320, 512}, TAIL2_A = {320, 128, 384, 320}, TAIL2_B = {320, 320, 384, 512},
            PLUME = {384, 128, 448, 256};
    /** Ojos en la textura de la cabeza (u a cada lado del frente, v). */
    static final float EYE_U = 0.17f, EYE_V = 0.42f;

    public final ModelPart root;
    final Map<String, ModelPart> parts = new HashMap<>();

    private static final class Node {
        final String name;
        final Mesh mesh;
        final PartPose pose;
        final List<Node> kids = new ArrayList<>();

        Node(String name, Mesh mesh, PartPose pose) {
            this.name = name;
            this.mesh = mesh;
            this.pose = pose;
        }

        Node add(Node n) {
            this.kids.add(n);
            return n;
        }

        ModelPart bake(Map<String, ModelPart> out) {
            Map<String, ModelPart> ch = new LinkedHashMap<>();
            for (Node k : this.kids) {
                ch.put(k.name, k.bake(out));
            }
            ModelPart p = new ModelPart(this.mesh == null ? List.of() : List.of(this.mesh.cube()), ch);
            p.setInitialPose(this.pose);
            p.loadPose(this.pose);
            out.put(this.name, p);
            return p;
        }
    }

    public PhoenixModel() {
        Node root = new Node("raiz", null, PartPose.ZERO);
        Node body = root.add(new Node("cuerpo", new Mesh()
                .ellipsoid(0, 0, 0.5f, 4.5f, 4.4f, 9.8f, 0, Mth.PI, 26, 14, BODY)
                .ellipsoid(0, 1.0f, -4.8f, 4.2f, 4.6f, 4.8f, 0, Mth.PI, 20, 12, BODY), PartPose.offset(0, 10, 0)));
        // cuello y cabeza
        Node n1 = body.add(new Node("cuello_1", new Mesh().ellipsoid(0, -3.4f, 0, 2.9f, 4.9f, 2.8f, 0, Mth.PI, 14, 8, NECK),
                PartPose.offsetAndRotation(0, -2.0f, -8.0f, -0.4f, 0, 0)));
        Node n2 = n1.add(new Node("cuello_2", new Mesh().ellipsoid(0, -2.6f, 0, 2.5f, 4.4f, 2.4f, 0, Mth.PI, 12, 8, NECK),
                PartPose.offsetAndRotation(0, -5.6f, 0, 0.5f, 0, 0)));
        Node head = n2.add(new Node("cabeza", new Mesh().ellipsoid(0, -1.5f, -1.4f, 2.2f, 2.2f, 3.2f, 0, Mth.PI, 16, 10, HEAD),
                PartPose.offsetAndRotation(0, -5.6f, 0, -0.1f, 0, 0)));
        head.add(new Node("pico", new Mesh().lathe(0, 0, 0, new float[][]{{0.1f, -3.8f}, {0.55f, -2.8f}, {1.0f, -1.3f}, {1.25f, 0.2f}}, 10, BEAK),
                PartPose.offsetAndRotation(0, -1.2f, -4.1f, Mth.HALF_PI + 0.18f, 0, 0)));
        Mesh crest = new Mesh();
        Mesh plume = new Mesh().petal(0, 6.5f, 1.6f, 0.2f, 0.5f, 0.25f, 6, CREST);
        for (int i = 0; i < 4; i++) {
            crest.addRotatedY(plume, (i - 1.5f) * 0.28f, 0, i * -0.15f, i * 0.4f);
        }
        head.add(new Node("cresta", crest, PartPose.offsetAndRotation(0, -3.6f, -1.2f, -2.15f, 0, 0)));
        // alas
        for (int side = -1; side <= 1; side += 2) {
            wing(body, side);
        }
        // cola: plumas largas en dos tramos (para que ondulen)
        Node tail = body.add(new Node("cola", new Mesh(), PartPose.offset(0, -1.5f, 9.0f)));
        Mesh fan = new Mesh();
        Mesh small = new Mesh().petal(0, 7.5f, 3.0f, 0.15f, -0.1f, 0.3f, 6, COVERT);
        for (int i = 0; i < 7; i++) {
            fan.addRotatedY(small, Mth.PI + (i - 3) * 0.22f, 0, -0.2f, 0);
        }
        tail.add(new Node("cola_abanico", fan, PartPose.ZERO));
        for (int i = 0; i < 7; i++) {
            float spread = (i - 3) * 0.13f;
            boolean alt = i % 2 == 1;
            float len = 14 - Math.abs(i - 3) * 1.6f;
            Node a = tail.add(new Node("pluma_" + i, new Mesh().petal(0, len, 2.6f, 0.05f, 0.05f, 0.25f, 8, alt ? TAIL2_A : TAIL_A),
                    PartPose.offsetAndRotation(0, i % 2 * -0.3f, 0, -0.06f, Mth.PI + spread, 0)));
            a.add(new Node("pluma_" + i + "_b", new Mesh().petal(0, len, 3.0f, 0.0f, 0.12f, 0.35f, 8, alt ? TAIL2_B : TAIL_B),
                    PartPose.offset(0, 0, -len)));
        }
        // patas
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "der" : "izq";
            Node leg = body.add(new Node("pierna_" + s, new Mesh().ellipsoid(0, 1.6f, 0, 1.7f, 2.6f, 2.0f, 0, Mth.PI, 10, 8, BODY),
                    PartPose.offset(side * 2.6f, 3.6f, 1.5f)));
            Node shin = leg.add(new Node("canilla_" + s, new Mesh().lathe(0, 0, 0, new float[][]{{0.75f, 0}, {0.6f, 2.5f}, {0.55f, 5.4f}}, 8, LEG),
                    PartPose.offset(0, 3.8f, 0.3f)));
            Mesh foot = new Mesh();
            Mesh toe = new Mesh().petal(0, 2.4f, 0.9f, -0.2f, -0.4f, 0.1f, 4, FOOT);
            for (int t = -1; t <= 1; t++) {
                foot.addRotatedY(toe, t * 0.45f, 0, 0, 0);
            }
            foot.addRotatedY(toe, Mth.PI, 0, 0, 0);
            shin.add(new Node("pie_" + s, foot, PartPose.offset(0, 5.4f, 0)));
        }
        this.root = root.bake(this.parts);
    }

    /** Ala de tres partes con plumas: side -1 derecha (hacia -x), +1 izquierda. */
    private static void wing(Node body, int side) {
        String s = side < 0 ? "der" : "izq";
        float d = side; // direccion hacia la punta en x
        Mesh arm = new Mesh().ellipsoid(d * 4.3f, 0, 0, 4.6f, 1.4f, 1.8f, 0, Mth.PI, 12, 6, ARM);
        Mesh cov = new Mesh().petal(0, 5.5f, 2.6f, 0.05f, -0.05f, 0.2f, 5, COVERT);
        for (int i = 0; i < 5; i++) {
            arm.addRotatedY(cov, Mth.PI + d * 0.06f, d * (1.2f + i * 1.7f), 0.2f, 0.4f);
        }
        Node a = body.add(new Node("brazo_" + s, arm, PartPose.offset(d * 4.4f, -2.8f, -2.6f)));
        Mesh fore = new Mesh().ellipsoid(d * 4.6f, 0, 0, 5.0f, 1.1f, 1.4f, 0, Mth.PI, 12, 6, ARM);
        Mesh sec = new Mesh().petal(0, 11f, 3.0f, 0.0f, -0.1f, 0.2f, 7, WING_FEATHER);
        Mesh cov2 = new Mesh().petal(0, 5f, 2.6f, 0.08f, -0.05f, 0.2f, 5, COVERT);
        for (int i = 0; i < 6; i++) {
            fore.addRotatedY(sec, Mth.PI + d * (0.04f * i), d * (0.8f + i * 1.65f), 0.35f, 0.3f);
            fore.addRotatedY(cov2, Mth.PI + d * (0.04f * i), d * (1.2f + i * 1.65f), 0.05f, 0.5f);
        }
        Node f = a.add(new Node("antebrazo_" + s, fore, PartPose.offset(d * 8.6f, 0, 0)));
        Mesh hand = new Mesh().ellipsoid(d * 3.6f, 0, 0, 4.0f, 0.9f, 1.1f, 0, Mth.PI, 10, 6, ARM);
        Mesh prim = new Mesh().petal(0, 13f, 3.0f, 0.0f, -0.08f, 0.2f, 8, WING_FEATHER);
        for (int i = 0; i < 6; i++) {
            float ang = Mth.PI + d * (0.15f + i * 0.24f);
            hand.addRotatedY(prim, ang, d * (0.6f + i * 1.25f), 0.3f, 0.3f);
        }
        f.add(new Node("mano_" + s, hand, PartPose.offset(d * 9.2f, 0, 0)));
    }

    public ModelPart part(String name) {
        return this.parts.get(name);
    }

    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay) {
        this.root.render(ps, vc, light, overlay, 0xFFFFFFFF);
    }
}
