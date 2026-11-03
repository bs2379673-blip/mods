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
        /** Animaciones especiales del espiritu (de su json): vueltita, saludo, baile, volar, corazon. */
        public java.util.List<String> specials = java.util.List.of();
        /** Piezas que aletean como alas (de su json "alas"). */
        public java.util.List<String> wings = java.util.List.of();
    }

    /** Especial que esta haciendo ahora (para las particulas): nombre y avance 0..1, o null. */
    public static String[] currentSpecial(Pose p) {
        if (p.specials.isEmpty() || p.sleeping || p.walkAmount > 0.2f || p.happy >= 0) {
            return null;
        }
        float t = p.age + Math.floorMod(p.seed * 37, 160);
        float cyc = t % 160;
        String which = p.specials.get(Math.floorMod((int) (t / 160) + p.seed, p.specials.size()));
        float dur = switch (which) {
            case "vueltita" -> 22;
            case "saludo" -> 34;
            case "volar" -> 48;
            case "corazon" -> 30;
            case "voltereta" -> 30;
            case "aplausos" -> 40;
            case "estirarse" -> 56;
            case "curioso" -> 50;
            case "sentarse" -> 70;
            case "gelatina" -> 36;
            default -> 44;
        };
        return cyc < dur ? new String[]{which, Float.toString(cyc / dur)} : null;
    }

    public final ModelPart root;
    final Map<String, ModelPart> byName = new HashMap<>();
    private ModelPart body, head, legL, legR, armL, armR, lids, spin;
    private final List<ModelPart> wobble = new ArrayList<>();
    private final List<ModelPart> petals = new ArrayList<>();
    /** Alto del modelo en pixeles (para poner la estrellita encima). */
    public float topY = 9;
    /**
     * Cuerpo tipo munequito (modelos .obj): todo lo que mueve al bicho entero (saltitos, giros,
     * balanceo, aplastarse) se hace sobre el modelo completo apoyado en el piso, asi patas, brazos y
     * cabeza nunca se despegan del cuerpo.
     */
    public boolean chibi;
    // transformacion del modelo entero (solo en modo munequito), alrededor de los pies
    private float gLift, gSpin, gRock, gLean, gSquashX = 1, gSquashY = 1, gShiftZ;

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
        if (this.chibi) {
            animateChibi(p);
            return;
        }
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
        specials(p, age, amt);
        for (int i = 0; i < this.petals.size(); i++) {
            ModelPart l = this.petals.get(i);
            l.xRot += Mth.sin(age * 0.08f + i * 1.3f) * 0.12f - (p.sleeping ? -0.4f : 0);
        }
    }

    /** Alas que aletean y animaciones especiales (vueltita, saludo, baile, volar, corazon). */
    private void specials(Pose p, float age, float amt) {
        float lift = 0;
        String[] sp = currentSpecial(p);
        String which = sp == null ? "" : sp[0];
        float f = sp == null ? 0 : Float.parseFloat(sp[1]);
        float bell = Mth.sin(f * Mth.PI);
        boolean flying = which.equals("volar");
        // alas: aleteo suave quieto, rapido al correr o al volar; plegadas al dormir
        if (!p.wings.isEmpty()) {
            float speed = p.sleeping ? 0.05f : 0.35f + amt * 0.6f + (flying ? 0.7f * bell : 0);
            float amp = p.sleeping ? 0.04f : 0.22f + amt * 0.3f + (flying ? 0.35f * bell : 0);
            for (String w : p.wings) {
                ModelPart part = this.byName.get(w);
                if (part == null) {
                    continue;
                }
                float side = Math.signum(part.x == 0 ? 1 : part.x);
                part.zRot += side * (0.15f + Mth.sin(age * speed * 1.6f) * amp) * (p.sleeping ? 0.2f : 1);
                part.yRot += side * Mth.cos(age * speed * 1.6f) * amp * 0.25f;
            }
            // con alas va flotando un poquito al correr
            lift += amt * (1.6f + Mth.sin(age * 0.5f) * 0.6f);
        }
        if (this.body == null) {
            return;
        }
        switch (which) {
            case "vueltita" -> { // salta y da una vuelta entera
                this.body.yRot += (1 - (1 - f) * (1 - f)) * Mth.TWO_PI;
                lift += bell * 4.5f;
                if (this.armR != null) {
                    this.armR.zRot += bell * 1.4f;
                }
                if (this.armL != null) {
                    this.armL.zRot -= bell * 1.4f;
                }
            }
            case "saludo" -> { // levanta el bracito y saluda moviendo la mano
                ModelPart arm = this.armR != null ? this.armR : this.armL;
                if (arm != null) {
                    float side = arm == this.armR ? 1 : -1;
                    arm.xRot -= bell * 2.4f;
                    arm.zRot += side * bell * (0.35f + Mth.sin(age * 0.9f) * 0.35f);
                }
                this.body.zRot += bell * 0.12f * (this.armR != null ? -1 : 1);
                ModelPart look = this.head != null ? this.head : this.body;
                look.zRot += bell * 0.15f;
            }
            case "volar" -> { // sube flotando, se balancea y baja
                lift += bell * 6f + Mth.sin(age * 0.6f) * bell * 0.8f;
                this.body.xRot += Mth.sin(age * 0.3f) * 0.08f * bell;
                if (this.legR != null) {
                    this.legR.xRot += 0.4f * bell;
                }
                if (this.legL != null) {
                    this.legL.xRot += 0.4f * bell;
                }
            }
            case "corazon" -> { // abraza el aire y se balancea feliz
                if (this.armR != null) {
                    this.armR.zRot += bell * 0.6f;
                    this.armR.yRot -= bell * 0.9f;
                }
                if (this.armL != null) {
                    this.armL.zRot -= bell * 0.6f;
                    this.armL.yRot += bell * 0.9f;
                }
                this.body.zRot += Mth.sin(f * Mth.TWO_PI * 2) * 0.15f * bell;
                ModelPart look = this.head != null ? this.head : this.body;
                look.xRot -= bell * 0.25f;
            }
            case "baile" -> { // bailecito de lado a lado
                float b = Mth.sin(f * Mth.TWO_PI * 3);
                this.body.zRot += b * 0.22f * bell;
                lift += Math.abs(b) * 1.2f * bell;
                ModelPart look = this.head != null ? this.head : this.body;
                look.zRot -= b * 0.18f * bell;
                if (this.armR != null) {
                    this.armR.zRot += (1.2f + b * 0.5f) * bell;
                }
                if (this.armL != null) {
                    this.armL.zRot -= (1.2f - b * 0.5f) * bell;
                }
            }
            default -> {
            }
        }
        this.body.y -= lift;
        if (this.legR != null && !flying) {
            this.legR.y -= lift;
        }
        if (this.legL != null && !flying) {
            this.legL.y -= lift;
        }
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        if (!this.chibi) {
            this.root.render(pose, vc, light, overlay, color);
            return;
        }
        // el modelo entero: gira, se balancea y se aplasta apoyado en sus pies (y = 24 px)
        pose.pushPose();
        pose.translate(0, (24 - this.gLift) / 16f, this.gShiftZ / 16f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(this.gSpin));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(this.gRock));
        pose.mulPose(com.mojang.math.Axis.XP.rotation(this.gLean));
        pose.scale(this.gSquashX, this.gSquashY, this.gSquashX);
        pose.translate(0, -24 / 16f, 0);
        this.root.render(pose, vc, light, overlay, color);
        pose.popPose();
    }

    /** Animacion para cuerpos tipo munequito (cabeza grande, patitas y bracitos cortos). */
    private void animateChibi(Pose p) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        float age = p.age + p.seed * 13;
        float amt = Mth.clamp(p.walkAmount, 0, 1), still = 1 - amt;
        float step = p.walk * 1.15f;
        float s = Mth.sin(step), c = Mth.cos(step);
        // al caminar: pasitos cortos, se bambolea de lado a lado y da saltitos
        float hop = Math.abs(s) * 1.3f * amt;
        this.gLift = hop;
        this.gSpin = 0;
        this.gRock = s * 0.13f * amt + Mth.sin(age * 0.055f) * 0.035f * still;
        this.gLean = amt * 0.1f;
        this.gShiftZ = 0;
        // respira; al apoyar el pie se aplasta un poquito
        float breath = Mth.sin(age * (p.sleeping ? 0.06f : 0.13f)) * (p.sleeping ? 0.03f : 0.02f);
        float land = amt * (1 - Math.abs(s)) * 0.07f;
        this.gSquashY = 1 + breath - land;
        this.gSquashX = 1 - breath * 0.5f + land * 0.6f;
        if (this.legR != null) {
            this.legR.xRot += c * 0.55f * amt;
        }
        if (this.legL != null) {
            this.legL.xRot -= c * 0.55f * amt;
        }
        // brazos: van al reves que las piernas; quietos se mecen apenas
        if (this.armR != null) {
            this.armR.xRot -= c * 0.5f * amt;
            this.armR.zRot += Mth.sin(age * 0.07f) * 0.04f * still;
        }
        if (this.armL != null) {
            this.armL.xRot += c * 0.5f * amt;
            this.armL.zRot -= Mth.sin(age * 0.07f + 1.3f) * 0.04f * still;
        }
        // cabeza: mira, inclina la cabecita de vez en cuando y cabecea con los pasos
        ModelPart look = this.head != null ? this.head : this.body;
        if (look != null) {
            look.yRot += Mth.clamp(p.headYaw, -55, 55) * Mth.DEG_TO_RAD * 0.8f;
            look.xRot += Mth.clamp(p.headPitch, -30, 30) * Mth.DEG_TO_RAD * 0.5f - s * 0.06f * amt;
            float tilt = Mth.sin(age * 0.021f) * Mth.sin(age * 0.047f);
            look.zRot += tilt * 0.14f * still;
        }
        if (p.sleeping) {
            this.gSquashY -= 0.06f;
            this.gLean += 0.12f;
            if (look != null) {
                look.xRot += 0.35f;
            }
        }
        // orejas, pelo y cola: se mecen solos y rebotan con los pasos
        for (int i = 0; i < this.wobble.size(); i++) {
            ModelPart w = this.wobble.get(i);
            float side = Math.signum(w.x == 0 ? 1 : w.x);
            w.zRot += side * (Mth.sin(age * 0.1f + i) * 0.06f + Math.abs(s) * 0.1f * amt);
            w.xRot += Mth.cos(age * 0.08f + i * 2) * 0.04f - amt * 0.06f;
            w.yRot += Mth.sin(age * 0.09f + i) * 0.05f;
        }
        // saltito de alegria (al recibir algo)
        if (p.happy >= 0) {
            float h = Mth.sin(p.happy * Mth.PI);
            this.gLift += h * 5f;
            this.gSpin += p.happy * Mth.TWO_PI;
            this.gSquashY += h * 0.06f;
            if (this.armR != null) {
                this.armR.zRot += h * 1.4f;
            }
            if (this.armL != null) {
                this.armL.zRot -= h * 1.4f;
            }
        }
        chibiSpecials(p, age, amt);
    }

    /** Especiales del munequito: siempre moviendo el modelo entero, nunca piezas sueltas. */
    private void chibiSpecials(Pose p, float age, float amt) {
        String[] sp = currentSpecial(p);
        String which = sp == null ? "" : sp[0];
        float f = sp == null ? 0 : Float.parseFloat(sp[1]);
        float bell = Mth.sin(f * Mth.PI);
        boolean flying = which.equals("volar");
        if (!p.wings.isEmpty()) {
            float speed = p.sleeping ? 0.05f : 0.35f + amt * 0.6f + (flying ? 0.7f * bell : 0);
            float amp = p.sleeping ? 0.04f : 0.2f + amt * 0.25f + (flying ? 0.35f * bell : 0);
            for (String w : p.wings) {
                ModelPart part = this.byName.get(w);
                if (part != null) {
                    float side = Math.signum(part.x == 0 ? 1 : part.x);
                    part.zRot += side * (0.12f + Mth.sin(age * speed * 1.6f) * amp) * (p.sleeping ? 0.2f : 1);
                }
            }
            this.gLift += amt * (1.4f + Mth.sin(age * 0.5f) * 0.5f);
        }
        switch (which) {
            case "vueltita" -> { // salta, gira entero y cae con un rebotito
                float e = 1 - (1 - f) * (1 - f);
                this.gSpin += e * Mth.TWO_PI;
                this.gLift += bell * 4.5f;
                this.gSquashY += bell * 0.05f - (f > 0.85f ? (f - 0.85f) * 0.5f : 0);
                if (this.armR != null) {
                    this.armR.zRot += bell * 1.2f;
                }
                if (this.armL != null) {
                    this.armL.zRot -= bell * 1.2f;
                }
            }
            case "saludo" -> { // levanta el bracito y saluda
                ModelPart arm = this.armR != null ? this.armR : this.armL;
                if (arm != null) {
                    float side = arm == this.armR ? 1 : -1;
                    arm.xRot -= bell * 2.5f;
                    arm.zRot += side * bell * (0.25f + Mth.sin(age * 0.9f) * 0.3f);
                }
                this.gRock += bell * 0.08f * (this.armR != null ? -1 : 1);
                if (this.head != null) {
                    this.head.zRot += bell * 0.15f;
                }
            }
            case "volar" -> { // sube flotando, patitas colgando
                this.gLift += bell * 6f + Mth.sin(age * 0.6f) * bell * 0.6f;
                this.gLean += Mth.sin(age * 0.3f) * 0.06f * bell;
                if (this.legR != null) {
                    this.legR.xRot += 0.35f * bell;
                }
                if (this.legL != null) {
                    this.legL.xRot += 0.25f * bell;
                }
            }
            case "corazon" -> { // se abraza y se balancea feliz
                if (this.armR != null) {
                    this.armR.xRot -= bell * 0.9f;
                    this.armR.yRot -= bell * 0.5f;
                }
                if (this.armL != null) {
                    this.armL.xRot -= bell * 0.9f;
                    this.armL.yRot += bell * 0.5f;
                }
                this.gRock += Mth.sin(f * Mth.TWO_PI * 2) * 0.14f * bell;
                if (this.head != null) {
                    this.head.xRot -= bell * 0.2f;
                }
            }
            case "baile" -> { // bailecito: salta de lado a lado con los brazos arriba
                float b = Mth.sin(f * Mth.TWO_PI * 3);
                this.gRock += b * 0.2f * bell;
                this.gLift += Math.abs(b) * 1.5f * bell;
                this.gSpin += Mth.sin(f * Mth.TWO_PI * 1.5f) * 0.35f * bell;
                if (this.head != null) {
                    this.head.zRot -= b * 0.15f * bell;
                }
                if (this.armR != null) {
                    this.armR.zRot += (1.1f + b * 0.4f) * bell;
                }
                if (this.armL != null) {
                    this.armL.zRot -= (1.1f - b * 0.4f) * bell;
                }
                if (this.legR != null) {
                    this.legR.xRot += b * 0.3f * bell;
                }
                if (this.legL != null) {
                    this.legL.xRot -= b * 0.3f * bell;
                }
            }
            case "voltereta" -> { // salta, da una vuelta carnero en el aire (alrededor de la panza) y cae aplastandose
                float e = f < 0.15f ? 0 : smooth(Mth.clamp((f - 0.15f) / 0.6f, 0, 1));
                float th = e * Mth.TWO_PI;
                float h = this.topY * 0.45f;
                this.gLean += th;
                this.gLift += h * (1 - Mth.cos(th)) + Mth.sin(Mth.clamp((f - 0.1f) / 0.75f, 0, 1) * Mth.PI) * 6f;
                this.gShiftZ += h * Mth.sin(th);
                // agacharse antes y aplastarse al caer
                float crouch = f < 0.15f ? Mth.sin(f / 0.15f * Mth.PI) : f > 0.8f ? Mth.sin((f - 0.8f) / 0.2f * Mth.PI) : 0;
                this.gSquashY -= crouch * 0.14f;
                this.gSquashX += crouch * 0.08f;
                if (this.armR != null) {
                    this.armR.zRot += bell * 1.0f;
                }
                if (this.armL != null) {
                    this.armL.zRot -= bell * 1.0f;
                }
                if (this.legR != null) {
                    this.legR.xRot -= bell * 0.6f;
                }
                if (this.legL != null) {
                    this.legL.xRot -= bell * 0.6f;
                }
            }
            case "aplausos" -> { // aplaude contento dando saltitos
                float clap = 0.5f + 0.5f * Mth.sin(age * 1.7f);
                if (this.armR != null) {
                    this.armR.xRot -= bell * 1.25f;
                    this.armR.yRot -= bell * (0.25f + clap * 0.55f);
                }
                if (this.armL != null) {
                    this.armL.xRot -= bell * 1.25f;
                    this.armL.yRot += bell * (0.25f + clap * 0.55f);
                }
                this.gLift += Math.abs(Mth.sin(f * Mth.PI * 4)) * 1.4f * bell;
                if (this.head != null) {
                    this.head.zRot += Mth.sin(f * Mth.TWO_PI * 2) * 0.12f * bell;
                    this.head.xRot -= bell * 0.12f;
                }
            }
            case "estirarse" -> { // se estira para arriba, bosteza tirando la cabeza atras y se sacude
                float up = f < 0.65f ? smooth(Mth.clamp(f / 0.3f, 0, 1)) : 1 - smooth((f - 0.65f) / 0.2f > 1 ? 1 : (f - 0.65f) / 0.2f);
                up = Mth.clamp(up, 0, 1);
                if (this.armR != null) {
                    this.armR.xRot -= up * 2.7f;
                    this.armR.zRot += up * 0.35f;
                }
                if (this.armL != null) {
                    this.armL.xRot -= up * 2.7f;
                    this.armL.zRot -= up * 0.35f;
                }
                this.gSquashY += up * 0.1f;
                this.gSquashX -= up * 0.05f;
                this.gLean -= up * 0.08f;
                if (this.head != null) {
                    this.head.xRot -= up * 0.45f;
                }
                // sacudon al final
                if (f > 0.82f) {
                    float sh = Mth.sin((f - 0.82f) / 0.18f * Mth.PI);
                    this.gRock += Mth.sin(age * 2.2f) * 0.1f * sh;
                }
            }
            case "rebote" -> { // tres botes de gelatina: se estira en el aire, se aplasta al tocar el piso
                float k = f * 3, u = k - (float) Math.floor(k);
                float air = Mth.sin(u * Mth.PI);
                this.gLift += air * (5f - (float) Math.floor(k) * 1.2f);
                float land = u < 0.12f ? 1 - u / 0.12f : u > 0.9f ? (u - 0.9f) / 0.1f : 0;
                this.gSquashY += air * 0.08f - land * 0.16f;
                this.gSquashX += -air * 0.04f + land * 0.1f;
                if (this.armR != null) {
                    this.armR.zRot += air * 0.9f;
                }
                if (this.armL != null) {
                    this.armL.zRot -= air * 0.9f;
                }
            }
            case "curioso" -> { // se inclina hacia adelante, ladea la cabecita y mira a un lado y al otro
                float lean = smooth(Mth.clamp(bell * 1.6f, 0, 1));
                this.gLean += lean * 0.2f;
                if (this.head != null) {
                    this.head.yRot += Mth.sin(f * Mth.TWO_PI) * 0.6f * lean;
                    this.head.zRot += lean * 0.3f;
                } else {
                    this.gSpin += Mth.sin(f * Mth.TWO_PI) * 0.4f * lean;
                }
                if (this.armR != null) {
                    this.armR.xRot -= lean * 0.5f;
                }
            }
            case "sentarse" -> { // se sienta un ratito con las patitas para adelante y se mece
                float sit = smooth(Mth.clamp(Math.min(f / 0.15f, (1 - f) / 0.15f), 0, 1));
                if (this.legR != null) {
                    this.legR.xRot -= sit * 1.45f;
                }
                if (this.legL != null) {
                    this.legL.xRot -= sit * 1.45f;
                }
                this.gLift -= sit * 2.6f;
                this.gLean -= sit * 0.1f;
                this.gRock += Mth.sin(age * 0.12f) * 0.06f * sit;
                if (this.armR != null) {
                    this.armR.xRot -= sit * 0.5f;
                }
                if (this.armL != null) {
                    this.armL.xRot -= sit * 0.5f;
                }
                if (this.head != null) {
                    this.head.zRot += Mth.sin(age * 0.05f) * 0.15f * sit;
                }
            }
            case "gelatina" -> { // tiembla entero como un flan
                float w = Mth.sin(age * 1.3f) * bell;
                this.gSquashY += w * 0.09f;
                this.gSquashX -= w * 0.06f;
                this.gRock += Mth.sin(age * 0.85f) * 0.07f * bell;
            }
            default -> {
            }
        }
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
