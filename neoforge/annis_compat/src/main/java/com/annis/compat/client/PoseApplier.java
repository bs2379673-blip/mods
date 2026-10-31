package com.annis.compat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

/**
 * Pone una animacion de Punchy encima de la pose que ya tiene el modelo (la de Punchy Moves, la
 * del juego...), mezclada con un peso de 0 a 1. Los huesos que la animacion no mueve no se tocan.
 * La cabeza se suma a donde mira el jugador.
 */
public final class PoseApplier {
    public static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** Pose muestreada: rotacion en radianes y posicion en pixeles del modelo (desde la inicial). */
    public static final class Sample {
        final float[][] rot = new float[6][3];
        final float[][] pos = new float[6][3];
        final boolean[] hasRot = new boolean[6];
        final boolean[] hasPos = new boolean[6];

        public void clear() {
            for (int i = 0; i < 6; i++) {
                this.hasRot[i] = false;
                this.hasPos[i] = false;
            }
        }

        /** Lee la animacion en el tiempo t (segundos dentro del clip). */
        public void read(Clip clip, float t) {
            float[] v = new float[3];
            for (int i = 0; i < 6; i++) {
                if (clip.rotation(BONES[i], t, v)) {
                    this.rot[i][0] = (float) Math.toRadians(v[0]);
                    this.rot[i][1] = (float) Math.toRadians(v[1]);
                    this.rot[i][2] = (float) Math.toRadians(v[2]);
                    this.hasRot[i] = true;
                }
                if (clip.position(BONES[i], t, v)) {
                    // Bedrock: Y hacia arriba; el modelo del juego: Y hacia abajo
                    this.pos[i][0] = v[0];
                    this.pos[i][1] = -v[1];
                    this.pos[i][2] = v[2];
                    this.hasPos[i] = true;
                }
            }
        }

        /** Mezcla otra pose encima (w de 0 a 1); los huesos que la otra no tiene quedan igual. */
        public void blend(Sample o, float w) {
            for (int i = 0; i < 6; i++) {
                if (o.hasRot[i]) {
                    for (int c = 0; c < 3; c++) {
                        this.rot[i][c] = this.hasRot[i] ? lerpAngle(this.rot[i][c], o.rot[i][c], w) : o.rot[i][c];
                    }
                    if (!this.hasRot[i] && w < 1) {
                        // sin base: se mezcla desde cero
                        for (int c = 0; c < 3; c++) {
                            this.rot[i][c] *= w;
                        }
                    }
                    this.hasRot[i] = true;
                }
                if (o.hasPos[i]) {
                    for (int c = 0; c < 3; c++) {
                        float base = this.hasPos[i] ? this.pos[i][c] : 0;
                        this.pos[i][c] = base + (o.pos[i][c] - base) * w;
                    }
                    this.hasPos[i] = true;
                }
            }
        }
    }

    private PoseApplier() {
    }

    private static float lerpAngle(float a, float b, float w) {
        return a + (b - a) * w;
    }

    private static ModelPart part(HumanoidModel<?> m, int i) {
        return switch (i) {
            case 0 -> m.head;
            case 1 -> m.body;
            case 2 -> m.rightArm;
            case 3 -> m.leftArm;
            case 4 -> m.rightLeg;
            default -> m.leftLeg;
        };
    }

    /** Aplica la pose al modelo con peso w. */
    public static void apply(HumanoidModel<?> model, Sample s, float w) {
        if (w <= 0.0f) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            ModelPart p = part(model, i);
            if (s.hasRot[i]) {
                if (i == 0) {
                    // la cabeza sigue mirando donde mira el jugador
                    p.xRot += s.rot[i][0] * w;
                    p.yRot += s.rot[i][1] * w;
                    p.zRot += s.rot[i][2] * w;
                } else {
                    p.xRot += (s.rot[i][0] - p.xRot) * w;
                    p.yRot += (s.rot[i][1] - p.yRot) * w;
                    p.zRot += (s.rot[i][2] - p.zRot) * w;
                }
            }
            if (s.hasPos[i]) {
                PartPose init = p.getInitialPose();
                p.x += (init.x + s.pos[i][0] - p.x) * w;
                p.y += (init.y + s.pos[i][1] - p.y) * w;
                p.z += (init.z + s.pos[i][2] - p.z) * w;
            }
        }
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> pm) {
            pm.jacket.copyFrom(pm.body);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.leftPants.copyFrom(pm.leftLeg);
        }
    }
}
