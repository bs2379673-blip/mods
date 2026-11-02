package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un modelo de espiritu listo para animar. Las piezas se reconocen por el nombre, asi tambien se
 * animan los modelos de Blockbench que suba el servidor:
 * cuerpo/body, cabeza/head, pierna_izq/left_leg, pierna_der/right_leg, brazo_izq/left_arm,
 * brazo_der/right_arm, parpados/eyelids (se muestran al parpadear) y cualquier pieza que empiece con
 * copa, petalo, hoja, flor, oreja, ear, leaf, cap o petal (se mecen).
 */
public final class SpiritRig {
    /** Parametros de animacion de un cuadro. */
    public static final class Pose {
        public float walk;        // limbSwing
        public float walkAmount;  // 0..1
        public float age;         // ticks
        public float headYaw;     // grados, relativo al cuerpo
        public float headPitch;
        public float happy = -1;  // 0..1 durante un saltito
        public boolean sleeping;
        public int seed;
    }

    public final ModelPart root;
    final Map<String, ModelPart> byName = new HashMap<>();
    private ModelPart body, head, legL, legR, armL, armR, lids, spin;
    private final List<ModelPart> wobble = new ArrayList<>();
    private final List<ModelPart> petals = new ArrayList<>();
    /** Alto del modelo en pixeles (para poner la estrellita encima). */
    public float topY = 9;

    /** paths: nombre de cada pieza -> camino de hijos desde la raiz. */
    public SpiritRig(ModelPart root, Map<String, List<String>> paths) {
        this.root = root;
        for (Map.Entry<String, List<String>> e : paths.entrySet()) {
            ModelPart p = root;
            try {
                for (String step : e.getValue()) {
                    p = p.getChild(step);
                }
                this.byName.put(e.getKey().toLowerCase(Locale.ROOT), p);
            } catch (Exception ignored) {
                // camino roto
            }
        }
        for (Map.Entry<String, ModelPart> e : this.byName.entrySet()) {
            String n = e.getKey();
            ModelPart p = e.getValue();
            switch (n) {
                case "cuerpo", "body", "torso" -> this.body = p;
                case "cabeza", "head" -> this.head = p;
                case "pierna_izq", "left_leg", "leftleg", "pierna_izquierda" -> this.legL = p;
                case "pierna_der", "right_leg", "rightleg", "pierna_derecha" -> this.legR = p;
                case "brazo_izq", "left_arm", "leftarm", "brazo_izquierdo" -> this.armL = p;
                case "brazo_der", "right_arm", "rightarm", "brazo_derecho" -> this.armR = p;
                case "parpados", "eyelids", "blink" -> this.lids = p;
                case "petalos" -> this.spin = p;
                default -> {
                    if (n.startsWith("petalo_")) {
                        this.petals.add(p);
                    } else if (n.startsWith("copa") || n.startsWith("petal") || n.startsWith("hoja") || n.startsWith("flor")
                            || n.startsWith("oreja") || n.startsWith("ear") || n.startsWith("leaf") || n.startsWith("cap")
                            || n.startsWith("campana") || n.startsWith("rosa") || n.startsWith("pelo") || n.startsWith("cola")
                            || n.startsWith("tail") || n.startsWith("hair")) {
                        this.wobble.add(p);
                    }
                }
            }
        }
    }

    public void animate(Pose p) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        float age = p.age + p.seed * 13;
        float amt = Mth.clamp(p.walkAmount, 0, 1);
        float step = p.walk * 1.3f;
        // patitas
        if (this.legR != null) {
            this.legR.xRot += Mth.cos(step) * 1.3f * amt;
        }
        if (this.legL != null) {
            this.legL.xRot += Mth.cos(step + Mth.PI) * 1.3f * amt;
        }
        // brazitos: aletean al correr, saludan suave quietos
        float flap = Mth.cos(step) * 0.9f * amt;
        if (this.armR != null) {
            this.armR.xRot += flap;
            this.armR.zRot += 0.25f + Mth.sin(age * 0.09f) * 0.08f * (1 - amt) + amt * 0.35f;
        }
        if (this.armL != null) {
            this.armL.xRot -= flap;
            this.armL.zRot -= 0.25f + Mth.sin(age * 0.09f + 1) * 0.08f * (1 - amt) + amt * 0.35f;
        }
        // cuerpo: rebote al correr, respiracion y vaiven
        if (this.body != null) {
            float bounce = Math.abs(Mth.sin(step)) * 1.4f * amt;
            this.body.y -= bounce;
            // las patitas acompanan el saltito (sin separarse del cuerpo)
            if (this.legR != null) {
                this.legR.y -= bounce;
            }
            if (this.legL != null) {
                this.legL.y -= bounce;
            }
            this.body.xRot += amt * 0.22f;
            this.body.zRot += Mth.sin(age * 0.07f) * 0.05f * (1 - amt) + Mth.cos(step) * 0.08f * amt;
            float breath = Mth.sin(age * (p.sleeping ? 0.06f : 0.14f)) * (p.sleeping ? 0.05f : 0.035f);
            // aplastar y estirar: al tocar el piso se aplasta un poco
            float squash = amt > 0.05f ? (1 - Math.abs(Mth.sin(step))) * 0.12f * amt : 0;
            this.body.yScale = 1 + breath - squash;
            this.body.xScale = 1 - breath * 0.6f + squash * 0.7f;
            this.body.zScale = this.body.xScale;
            ModelPart look = this.head != null ? this.head : this.body;
            look.yRot += Mth.clamp(p.headYaw, -60, 60) * Mth.DEG_TO_RAD * (this.head != null ? 1f : 0.6f);
            look.xRot += Mth.clamp(p.headPitch, -30, 30) * Mth.DEG_TO_RAD * 0.5f;
            if (p.sleeping) {
                this.body.xRot += 0.18f;
            }
        }
        // saltito de alegria
        if (p.happy >= 0 && this.body != null) {
            float h = Mth.sin(p.happy * Mth.PI);
            this.body.y -= h * 4.5f;
            this.body.yRot += p.happy * Mth.TWO_PI;
            if (this.armR != null) {
                this.armR.zRot += h * 1.6f;
            }
            if (this.armL != null) {
                this.armL.zRot -= h * 1.6f;
            }
            if (this.legR != null) {
                this.legR.y -= h * 4.5f;
            }
            if (this.legL != null) {
                this.legL.y -= h * 4.5f;
            }
        }
        // parpadeo (o dormido)
        if (this.lids != null) {
            float cycle = (age + p.seed * 29) % 90;
            this.lids.visible = p.sleeping || cycle < 3.5f || (cycle > 9 && cycle < 11.5f && p.seed % 3 == 0);
        }
        // adornos que se mecen
        for (int i = 0; i < this.wobble.size(); i++) {
            ModelPart w = this.wobble.get(i);
            w.zRot += Mth.sin(age * 0.11f + i) * 0.07f + Mth.cos(step) * 0.12f * amt;
            w.xRot += Mth.cos(age * 0.09f + i * 2) * 0.05f - amt * 0.12f;
        }
        if (this.spin != null) {
            this.spin.yRot += age * 0.025f;
        }
        for (int i = 0; i < this.petals.size(); i++) {
            ModelPart l = this.petals.get(i);
            l.xRot += Mth.sin(age * 0.08f + i * 1.3f) * 0.12f - (p.sleeping ? -0.4f : 0);
        }
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        this.root.render(pose, vc, light, overlay, color);
    }
}
