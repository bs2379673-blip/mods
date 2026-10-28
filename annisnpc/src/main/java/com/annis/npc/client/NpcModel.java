package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import dev.kosmx.playerAnim.core.util.Pair;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/**
 * Modelo de jugador para los NPC. Aplica a mano los emotes de playerAnimator
 * (la libreria solo lo hace para jugadores) y, si no hay emote, la capa procedural suave.
 * Si se construye con {@link JointModel}, tiene codos y rodillas ("lower" de cada miembro).
 */
public class NpcModel extends PlayerEntityModel<AnnisNpcEntity> {
    public final boolean jointed;
    public final ModelPart rightForearm;
    public final ModelPart leftForearm;
    public final ModelPart rightShin;
    public final ModelPart leftShin;
    /** Cintura (mitad de abajo del torso): la espalda se dobla entre pecho y cintura. */
    public final ModelPart waist;
    private final ModelPart jacketLower;
    private final ModelPart rightSleeveLower;
    private final ModelPart leftSleeveLower;
    private final ModelPart rightPantsLower;
    private final ModelPart leftPantsLower;
    private final ModelPart[] animParts;

    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.jointed = this.rightArm.hasChild("lower");
        if (this.jointed) {
            this.rightForearm = this.rightArm.getChild("lower");
            this.leftForearm = this.leftArm.getChild("lower");
            this.rightShin = this.rightLeg.getChild("lower");
            this.leftShin = this.leftLeg.getChild("lower");
            this.rightSleeveLower = this.rightSleeve.getChild("lower");
            this.leftSleeveLower = this.leftSleeve.getChild("lower");
            this.rightPantsLower = this.rightPants.getChild("lower");
            this.leftPantsLower = this.leftPants.getChild("lower");
            this.waist = this.body.getChild("lower");
            this.jacketLower = this.jacket.getChild("lower");
            this.animParts = new ModelPart[]{this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg,
                    this.rightForearm, this.leftForearm, this.rightShin, this.leftShin, this.waist};
        } else {
            this.rightForearm = null;
            this.leftForearm = null;
            this.rightShin = null;
            this.leftShin = null;
            this.rightSleeveLower = null;
            this.leftSleeveLower = null;
            this.rightPantsLower = null;
            this.leftPantsLower = null;
            this.waist = null;
            this.jacketLower = null;
            this.animParts = new ModelPart[]{this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
        }
    }

    /** Partes que mueven las animaciones (6 en el modelo rigido, 10 con codos y rodillas). */
    public ModelPart[] animParts() {
        return this.animParts;
    }

    private static void zero(ModelPart p) {
        p.pitch = 0.0f;
        p.yaw = 0.0f;
        p.roll = 0.0f;
    }

    @Override
    public void setAngles(AnnisNpcEntity npc, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        super.setAngles(npc, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        if (this.jointed) {
            zero(this.rightForearm);
            zero(this.leftForearm);
            zero(this.rightShin);
            zero(this.leftShin);
            zero(this.waist);
        }

        NpcAnimState state = NpcAnimState.of(npc);
        boolean overriding = state.isOverriding();
        EmfCompat.setPaused(npc, overriding);
        // si EMF anima el modelo (caminar/reposo), su animacion manda cuando no hay emote ni actividad
        boolean emfDrives = !overriding && EmfCompat.emfAnimates(this);
        if (state.applier.isActive()) {
            state.applier.updatePart("head", this.head);
            state.applier.updatePart("leftArm", this.leftArm);
            state.applier.updatePart("rightArm", this.rightArm);
            state.applier.updatePart("leftLeg", this.leftLeg);
            state.applier.updatePart("rightLeg", this.rightLeg);
            state.applier.updatePart("torso", this.body);
            if (this.jointed) {
                // dobleces de los emotes de Emotecraft (mismo calculo que bendy-lib)
                bend(state, "rightArm", this.rightForearm);
                bend(state, "leftArm", this.leftForearm);
                bend(state, "rightLeg", this.rightShin);
                bend(state, "leftLeg", this.leftShin);
                torsoBend(state);
            }
        } else {
            // capa procedural solo de pie; las actividades tambien montado o agachado (en brazos...)
            if (!emfDrives && !npc.isSneaking() && !npc.hasVehicle()) {
                state.applyProcedural(this, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
                if (this.jointed) {
                    naturalBends(limbDistance);
                }
            }
            // reloj comun: todos los NPC con la misma actividad se mueven al mismo compas
            float partial = animationProgress - npc.age;
            float shared = partial;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.world != null) {
                long elapsed = mc.world.getTime() - npc.getEmoteStart();
                if (elapsed >= 0 && elapsed < 20L * 60 * 60 * 24) {
                    shared = elapsed + partial;
                }
            }
            Activities.setCurrent(npc);
            state.applyActivity(this, shared);
            Activities.setCurrent(null);
        }

        // la capa exterior de la skin copia la pose de cada parte
        this.hat.copyTransform(this.head);
        this.jacket.copyTransform(this.body);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
        if (this.jointed) {
            this.rightSleeveLower.copyTransform(this.rightForearm);
            this.leftSleeveLower.copyTransform(this.leftForearm);
            this.rightPantsLower.copyTransform(this.rightShin);
            this.leftPantsLower.copyTransform(this.leftShin);
            this.jacketLower.copyTransform(this.waist);
        }
    }

    private static void bend(NpcAnimState state, String name, ModelPart lower) {
        Pair<Float, Float> b = state.applier.getBend(name);
        if (b != null && b.getRight() != null && Math.abs(b.getRight()) > 1e-4f) {
            JointModel.setAxisRotation(lower, b.getLeft(), b.getRight());
        }
    }

    // ---- matrices 3x3 (por filas) para la espalda ----
    private static double[] rot(ModelPart p) {
        double cx = Math.cos(p.pitch), sx = Math.sin(p.pitch);
        double cy = Math.cos(p.yaw), sy = Math.sin(p.yaw);
        double cz = Math.cos(p.roll), sz = Math.sin(p.roll);
        return new double[]{
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx};
    }

    private static double[] mm(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    private static double[] tr(double[] m) {
        return new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
    }

    private static double[] mv(double[] m, double x, double y, double z) {
        return new double[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    private static void setRot(ModelPart p, double[] m) {
        JointModel.setFromMatrix(p, m[0], m[3], m[6], m[7], m[8]);
    }

    private static double[] axisRot(float axisAngle, float angle) {
        double ax = Math.cos(axisAngle), az = Math.sin(axisAngle);
        double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        return new double[]{
                c + ax * ax * t, -az * s, ax * az * t,
                az * s, c, -ax * s,
                ax * az * t, ax * s, c + az * az * t};
    }

    /**
     * Doblez de torso de Emotecraft en el modelo articulado: la cintura queda donde estaba y el pecho
     * se dobla en la mitad, llevandose la cabeza y los brazos (como bendy-lib).
     */
    private void torsoBend(NpcAnimState state) {
        Pair<Float, Float> b = state.applier.getBend("torso");
        if (b == null || b.getRight() == null || Math.abs(b.getRight()) < 1e-4f) {
            return;
        }
        double[] rb = rot(this.body);
        double[] bend = axisRot(b.getLeft(), b.getRight());
        double[] rc = mm(rb, bend);
        double[] d = mm(rc, tr(rb));
        float split = JointModel.SPLIT;
        double[] q = mv(rb, 0, 12, 0);
        double[] c6 = mv(rc, 0, split, 0);
        double[] b6 = mv(rb, 0, 12 - split, 0);
        double npx = this.body.pivotX + q[0] - c6[0] - b6[0];
        double npy = this.body.pivotY + q[1] - c6[1] - b6[1];
        double npz = this.body.pivotZ + q[2] - c6[2] - b6[2];
        for (ModelPart p : new ModelPart[]{this.head, this.rightArm, this.leftArm}) {
            double[] off = mv(d, p.pivotX - this.body.pivotX, p.pivotY - this.body.pivotY, p.pivotZ - this.body.pivotZ);
            p.pivotX = (float) (npx + off[0]);
            p.pivotY = (float) (npy + off[1]);
            p.pivotZ = (float) (npz + off[2]);
            setRot(p, mm(d, rot(p)));
        }
        this.body.pivotX = (float) npx;
        this.body.pivotY = (float) npy;
        this.body.pivotZ = (float) npz;
        setRot(this.body, rc);
        setRot(this.waist, tr(bend));
    }

    /** Dobleces naturales al caminar y en reposo: rodilla atras al levantar el pie, codos un poco flexionados. */
    private void naturalBends(float limbDistance) {
        float speed = MathHelper.clamp(limbDistance, 0.0f, 1.0f);
        // la pierna que va hacia atras dobla la rodilla
        this.rightShin.pitch = Math.max(0.0f, this.rightLeg.pitch) * 1.1f + 0.05f;
        this.leftShin.pitch = Math.max(0.0f, this.leftLeg.pitch) * 1.1f + 0.05f;
        // codos: algo flexionados siempre, mas al caminar
        float elbow = -0.12f - 0.35f * speed;
        this.rightForearm.pitch = elbow + Math.min(0.0f, this.rightArm.pitch) * 0.4f;
        this.leftForearm.pitch = elbow + Math.min(0.0f, this.leftArm.pitch) * 0.4f;
        // espalda: la cadera compensa el giro y la inclinacion del pecho (se ve el torso retorcerse)
        this.waist.yaw = -this.body.yaw;
        this.waist.roll = -this.body.roll * 0.8f;
        this.waist.pitch = -this.body.pitch * 0.5f;
    }

    @Override
    public void setArmAngle(Arm arm, MatrixStack matrices) {
        super.setArmAngle(arm, matrices);
        if (this.jointed) {
            // los objetos en la mano siguen al antebrazo
            ModelPart lower = arm == Arm.RIGHT ? this.rightForearm : this.leftForearm;
            lower.rotate(matrices);
            matrices.translate(0.0f, -lower.pivotY / 16.0f, 0.0f);
        }
    }
}
