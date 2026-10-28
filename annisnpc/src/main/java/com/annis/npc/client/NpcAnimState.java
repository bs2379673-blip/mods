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
    private static final Map<net.minecraft.entity.LivingEntity, NpcAnimState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Estado de cualquier entidad animada desde el servidor (aldeanos de MCA, etc.). */
    public static NpcAnimState ofGeneric(net.minecraft.entity.LivingEntity e, int serial, String name) {
        return STATES.computeIfAbsent(e, n -> {
            NpcAnimState state = new NpcAnimState(serial);
            if (isProcedural(name)) {
                state.activity = name;
                state.activityTarget = true;
                state.activityWeight = 1.0f;
            }
            return state;
        });
    }

    /** Estado si ya existe (no lo crea). */
    public static NpcAnimState peek(net.minecraft.entity.LivingEntity e) {
        return STATES.get(e);
    }

    public static NpcAnimState of(AnnisNpcEntity npc) {
        return STATES.computeIfAbsent(npc, e -> {
            AnnisNpcEntity n = (AnnisNpcEntity) e;
            NpcAnimState state = new NpcAnimState(n.getEmoteSerial());
            String name = n.getEmote();
            if (isProcedural(name)) {
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
        tick(npc.getEmote(), npc.getEmoteSerial(), npc.getEmoteStart());
    }

    /** Version general: nombre de la animacion, contador de cambios y tick de inicio (reloj comun). */
    public void tick(String name, int serial, int start) {
        if (serial != this.lastSerial) {
            this.lastSerial = serial;
            KeyframeAnimation animation = name.isEmpty() ? null : EmoteLibrary.get(name);
            String act = animation == null && isProcedural(name) ? name : "";
            if (!act.isEmpty() && !this.activity.isEmpty() && !act.equals(this.activity)) {
                this.activityWeight = 0.0f;
            }
            if (!act.isEmpty()) {
                this.activity = act;
            }
            this.activityTarget = !act.isEmpty();
            AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(6, Ease.INOUTSINE);
            if (animation != null) {
                // empezar en el punto del reloj comun: los que llegan tarde quedan en sincronia
                int elapsed = 0;
                if (net.minecraft.client.MinecraftClient.getInstance().world != null) {
                    elapsed = (int) net.minecraft.client.MinecraftClient.getInstance().world.getTime() - start;
                }
                elapsed = Math.max(0, Math.min(elapsed, 20 * 60 * 30));
                this.layer.replaceAnimationWithFade(fade, new KeyframeAnimationPlayer(animation, elapsed));
            } else {
                this.layer.replaceAnimationWithFade(fade, null);
            }
        }
        this.stack.tick();
    }

    /**
     * Pone encima de una pose ya calculada (por ejemplo la de MCA) el emote o la actividad actual,
     * con la misma mezcla suave. shared = tiempo del reloj comun en ticks.
     */
    public void applyOverlay(NpcModel m, float shared) {
        if (this.applier.isActive()) {
            this.applier.updatePart("head", m.head);
            this.applier.updatePart("leftArm", m.leftArm);
            this.applier.updatePart("rightArm", m.rightArm);
            this.applier.updatePart("leftLeg", m.leftLeg);
            this.applier.updatePart("rightLeg", m.rightLeg);
            this.applier.updatePart("torso", m.body);
        } else {
            applyActivity(m, shared);
        }
    }

    /** Actividad integrada o movimiento grabado (no un emote JSON de Emotecraft). */
    public static boolean isProcedural(String name) {
        if (name == null || name.isEmpty() || EmoteLibrary.get(name) != null) {
            return false;
        }
        return MoveLibrary.has(name) || Activities.has(name);
    }

    /** Pose de cuerpo entero (tumbado, en brazos...) de la actividad actual, ya mezclada. */
    public Activities.RootPose rootPose(AnnisNpcEntity npc, float tickDelta) {
        return rootPose(npc.getEmoteStart(), tickDelta);
    }

    /** Igual, para cualquier entidad: start = tick de inicio del reloj comun. */
    public Activities.RootPose rootPose(int start, float tickDelta) {
        if (this.activity.isEmpty() || this.activityWeight <= 0.0f) {
            return null;
        }
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.world == null) {
            return null;
        }
        long elapsed = mc.world.getTime() - start;
        if (elapsed < 0 || elapsed > 20L * 60 * 60 * 24) {
            return null;
        }
        float t = elapsed + tickDelta;
        Activities.RootPose rp = Activities.root(this.activity, t);
        if (rp == null) {
            return null;
        }
        rp = rp.scaled(this.activityWeight);
        if (!Activities.springy(this.activity)) {
            return rp;
        }
        if (this.rootSprings == null) {
            this.rootSprings = new Spring[6];
            for (int c = 0; c < 6; c++) {
                this.rootSprings[c] = new Spring(1.9f, 0.7f, 0.6f, c < 3);
            }
        }
        float dt = this.rootT < 0 || t < this.rootT ? 0 : (t - this.rootT) / 20.0f;
        this.rootT = t;
        float[] v = {rp.pitch(), rp.yaw(), rp.roll(), rp.forward(), rp.up(), rp.left()};
        for (int c = 0; c < 6; c++) {
            v[c] = this.rootSprings[c].update(v[c], dt);
        }
        return new Activities.RootPose(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    /** Giro del cuerpo entero del movimiento actual (bailes importados que dan vueltas), ya mezclado. */
    public float rootYaw(AnnisNpcEntity npc, float tickDelta) {
        if (this.activity.isEmpty() || this.activityWeight <= 0.0f) {
            return 0.0f;
        }
        MoveLibrary.Move move = MoveLibrary.get(this.activity);
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (move == null || move.yaw() == null || mc.world == null) {
            return 0.0f;
        }
        long elapsed = mc.world.getTime() - npc.getEmoteStart();
        if (elapsed < 0 || elapsed > 20L * 60 * 60 * 24) {
            return 0.0f;
        }
        return MoveLibrary.yawAt(move, elapsed + tickDelta) * this.activityWeight;
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
            this.springs = null;
            this.rootSprings = null;
        }
        if (this.activity.isEmpty() || this.activityWeight <= 0.0f) {
            return;
        }
        net.minecraft.client.model.ModelPart[] parts = m.animParts();
        float[][] before = new float[parts.length][6];
        for (int i = 0; i < parts.length; i++) {
            net.minecraft.client.model.ModelPart p = parts[i];
            before[i] = new float[]{p.pitch, p.yaw, p.roll, p.pivotX, p.pivotY, p.pivotZ};
        }
        Activities.apply(this.activity, m, t);
        float w = this.activityWeight;
        boolean spring = Activities.springy(this.activity);
        if (spring && (this.springs == null || this.springs.length != parts.length)) {
            this.springs = new Spring[parts.length][6];
            for (int i = 0; i < parts.length; i++) {
                // cabeza y torso mas lentos, manos y piernas mas rapidas, codos y rodillas aun mas
                float f = i == 0 ? 2.2f : i == 1 ? 1.7f : i < 4 ? 2.5f : i < 6 ? 2.8f : 3.0f;
                for (int c = 0; c < 6; c++) {
                    this.springs[i][c] = new Spring(f, 0.62f, 0.9f, c < 3);
                }
            }
        }
        float sdt = this.springT < 0 || t < this.springT ? 0 : (t - this.springT) / 20.0f;
        this.springT = t;
        for (int i = 0; i < parts.length; i++) {
            net.minecraft.client.model.ModelPart p = parts[i];
            float[] b = before[i];
            float[] v = {
                    MathHelper.lerp(w, b[0], p.pitch), MathHelper.lerp(w, b[1], p.yaw), MathHelper.lerp(w, b[2], p.roll),
                    MathHelper.lerp(w, b[3], p.pivotX), MathHelper.lerp(w, b[4], p.pivotY), MathHelper.lerp(w, b[5], p.pivotZ)};
            if (spring) {
                Spring[] sp = this.springs[i];
                if (!sp[0].ready() || sdt <= 0) {
                    for (int c = 0; c < 6; c++) {
                        if (!sp[c].ready()) {
                            sp[c].reset(v[c]);
                        }
                    }
                }
                for (int c = 0; c < 6; c++) {
                    v[c] = sp[c].update(v[c], sdt);
                }
            }
            p.pitch = v[0];
            p.yaw = v[1];
            p.roll = v[2];
            p.pivotX = v[3];
            p.pivotY = v[4];
            p.pivotZ = v[5];
        }
    }

    private Spring[][] springs;
    private float springT = -1;
    private Spring[] rootSprings;
    private float rootT = -1;

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
