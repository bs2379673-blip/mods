package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.util.math.MathHelper;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Estado de animacion de un NPC (solo cliente): la pila de playerAnimator para los emotes
 * y los valores suavizados de la capa procedural (respiracion, inclinacion, cabeza...).
 */
public final class NpcAnimState {
    private static final Map<AnnisNpcEntity, NpcAnimState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static NpcAnimState of(AnnisNpcEntity npc) {
        return STATES.computeIfAbsent(npc, n -> {
            NpcAnimState state = new NpcAnimState(n.getEmoteSerial());
            String name = n.getEmote();
            if (Activities.has(name) && EmoteLibrary.get(name) == null) {
                state.activity = name;
                state.activityTarget = true;
                state.activityWeight = 1.0f;
            }
            return state;
        });
    }

    public final AnimationStack stack = new AnimationStack();
    public final AnimationApplier applier;
    private final ModifierLayer<IAnimation> layer = new ModifierLayer<>();
    private int lastSerial;
    private boolean activityTarget;

    // suavizado procedural
    private boolean init;
    private float lastT;
    private float smoothSpeed;
    private float smoothHeadYaw;
    private float smoothHeadPitch;

    // actividades integradas (mezcla suave con la capa procedural)
    private String activity = "";
    private float activityWeight;
    private float lastActT;
    private boolean actInit;

    private NpcAnimState(int serial) {
        this.lastSerial = serial;
        this.stack.addAnimLayer(1000, this.layer);
        this.applier = new AnimationApplier(this.stack);
    }

    /** Se llama una vez por tick de cliente. */
    public void tick(AnnisNpcEntity npc) {
        int serial = npc.getEmoteSerial();
        if (serial != this.lastSerial) {
            this.lastSerial = serial;
            String name = npc.getEmote();
            KeyframeAnimation animation = name.isEmpty() ? null : EmoteLibrary.get(name);
            String act = animation == null && Activities.has(name) ? name : "";
            if (!act.isEmpty() && !this.activity.isEmpty() && !act.equals(this.activity)) {
                this.activityWeight = 0.0f;
            }
            if (!act.isEmpty()) {
                this.activity = act;
            }
            this.activityTarget = !act.isEmpty();
            AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(6, Ease.INOUTSINE);
            if (animation != null) {
                this.layer.replaceAnimationWithFade(fade, new KeyframeAnimationPlayer(animation));
            } else {
                this.layer.replaceAnimationWithFade(fade, null);
            }
        }
        this.stack.tick();
    }

    /** true mientras un emote o una actividad controlan el cuerpo (incluyendo el fundido de salida). */
    public boolean isOverriding() {
        return this.applier.isActive() || !this.activity.isEmpty();
    }

    /** Mezcla la actividad integrada (si hay) sobre la pose actual del modelo. */
    public void applyActivity(NpcModel m, float t) {
        float dt;
        if (!this.actInit || t < this.lastActT) {
            dt = 1.0f;
            this.actInit = true;
        } else {
            dt = Math.min(4.0f, t - this.lastActT);
        }
        this.lastActT = t;
        float target = this.activityTarget ? 1.0f : 0.0f;
        this.activityWeight += (target - this.activityWeight) * rate(0.22f, dt);
        if (!this.activityTarget && this.activityWeight < 0.01f) {
            this.activityWeight = 0.0f;
            this.activity = "";
        }
        if (this.activity.isEmpty() || this.activityWeight <= 0.0f) {
            return;
        }
        net.minecraft.client.model.ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float[][] before = new float[parts.length][6];
        for (int i = 0; i < parts.length; i++) {
            net.minecraft.client.model.ModelPart p = parts[i];
            before[i] = new float[]{p.pitch, p.yaw, p.roll, p.pivotX, p.pivotY, p.pivotZ};
        }
        Activities.apply(this.activity, m, t);
        float w = this.activityWeight;
        for (int i = 0; i < parts.length; i++) {
            net.minecraft.client.model.ModelPart p = parts[i];
            float[] b = before[i];
            p.pitch = MathHelper.lerp(w, b[0], p.pitch);
            p.yaw = MathHelper.lerp(w, b[1], p.yaw);
            p.roll = MathHelper.lerp(w, b[2], p.roll);
            p.pivotX = MathHelper.lerp(w, b[3], p.pivotX);
            p.pivotY = MathHelper.lerp(w, b[4], p.pivotY);
            p.pivotZ = MathHelper.lerp(w, b[5], p.pivotZ);
        }
    }

    private static float rate(float perTick, float dt) {
        return 1.0f - (float) Math.exp(-perTick * dt);
    }

    /**
     * Capa procedural estilo "fresh animations": movimiento suave de cabeza, torso y brazos,
     * respiracion en reposo e inclinacion al caminar. Se suma a la pose normal del modelo.
     */
    public void applyProcedural(NpcModel m, float limbAngle, float limbDistance, float t,
                                float headYaw, float headPitch) {
        float yawIn = MathHelper.wrapDegrees(headYaw);
        float dt;
        if (!this.init || t < this.lastT) {
            dt = 1.0f;
            this.smoothHeadYaw = yawIn;
            this.smoothHeadPitch = headPitch;
            this.smoothSpeed = MathHelper.clamp(limbDistance, 0.0f, 1.0f);
            this.init = true;
        } else {
            dt = Math.min(4.0f, t - this.lastT);
            this.smoothHeadYaw += (yawIn - this.smoothHeadYaw) * rate(0.30f, dt);
            this.smoothHeadPitch += (headPitch - this.smoothHeadPitch) * rate(0.30f, dt);
            this.smoothSpeed += (MathHelper.clamp(limbDistance, 0.0f, 1.0f) - this.smoothSpeed) * rate(0.20f, dt);
        }
        this.lastT = t;

        float speed = this.smoothSpeed;
        float idle = 1.0f - MathHelper.clamp(speed * 6.0f, 0.0f, 1.0f);
        float swing = limbAngle * 0.6662f;

        // cabeza suavizada
        m.head.yaw = this.smoothHeadYaw * MathHelper.RADIANS_PER_DEGREE;
        m.head.pitch = this.smoothHeadPitch * MathHelper.RADIANS_PER_DEGREE;

        // al caminar: inclinacion del torso, torsion contraria a los brazos y balanceo de la cabeza
        float lean = speed * 0.10f;
        m.body.pitch += lean;
        m.head.pitch -= lean * 0.7f;
        m.body.roll += MathHelper.sin(swing) * 0.035f * speed;
        m.body.yaw += MathHelper.cos(swing) * 0.08f * speed;
        m.head.yaw -= MathHelper.cos(swing) * 0.05f * speed;
        m.head.roll -= MathHelper.sin(swing) * 0.02f * speed;

        // rebote de cadera: sube y baja la parte de arriba con cada paso
        float bob = Math.abs(MathHelper.cos(swing)) * 0.8f * speed;
        m.body.pivotY -= bob;
        m.head.pivotY -= bob;
        m.leftArm.pivotY -= bob;
        m.rightArm.pivotY -= bob;

        // hombros mas relajados, se abren un poco al caminar
        float relax = 0.05f + 0.06f * speed;
        m.leftArm.roll -= relax;
        m.rightArm.roll += relax;

        // reposo: respiracion, cambio de peso y mirada que se mueve sola
        float breath = MathHelper.sin(t * 0.07f);
        float shift = MathHelper.sin(t * 0.031f);
        m.body.pitch += breath * 0.012f * idle;
        m.body.pivotY += breath * 0.12f * idle;
        m.head.pitch += breath * 0.010f * idle;
        m.leftArm.roll -= breath * 0.02f * idle;
        m.rightArm.roll += breath * 0.02f * idle;
        m.body.roll += shift * 0.02f * idle;
        m.body.yaw += MathHelper.sin(t * 0.023f + 1.3f) * 0.025f * idle;
        m.head.yaw += MathHelper.sin(t * 0.037f + 0.6f) * 0.04f * idle;
        m.head.roll -= shift * 0.02f * idle;
        m.leftLeg.roll -= shift * 0.012f * idle;
        m.rightLeg.roll -= shift * 0.012f * idle;
    }
}
