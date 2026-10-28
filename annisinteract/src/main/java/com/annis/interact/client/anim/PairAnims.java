package com.annis.interact.client.anim;

import com.annis.interact.Layout;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.annis.interact.client.anim.Rig.*;

/**
 * Animaciones de pareja de Annis Interacciones (propias, hechas a mano).
 *
 * Cada pareja comparte el mismo reloj (t en ticks desde el inicio), asi los contactos (manos que
 * chocan, brazos que abrazan) coinciden. La colocacion de cada pareja la hace el servidor con las
 * mismas medidas que se usan aqui ({@link Layout}).
 */
public final class PairAnims {
    public interface Pose {
        void apply(Rig r, float t);
    }

    public interface RootFn {
        Root at(float t);
    }

    public record Anim(Pose pose, RootFn root) {
    }

    public static final Map<String, Anim> ALL = new LinkedHashMap<>();

    private static final float PX = 16.0f;
    private static final Root LYING = new Root(PI / 2, 0, 0, 0, -0.575f, 0);
    private static final Root SITTING = new Root(0, 0, 0, 0, -0.575f, 0);
    private static final Root CARRIED = new Root(PI / 2, -PI / 2, 0, 0, 0, 0);
    private static final Root PIGGY = new Root(-0.20f, 0, 0, 0, 0, 0);

    private PairAnims() {
    }

    static {
        add("abrazo", PairAnims::hug, null);
        add("beso_mejilla", PairAnims::kiss, null);
        add("recibir_beso", PairAnims::kissed, null);
        add("chocar_cinco", PairAnims::highFive, null);
        add("apreton_manos", PairAnims::handshake, null);
        add("palmadita", PairAnims::pat, null);
        add("recibir_palmadita", PairAnims::patted, null);
        add("de_la_mano_d", (r, t) -> holdHand(r, t, true), null);
        add("de_la_mano_i", (r, t) -> holdHand(r, t, false), null);
        add("cargar_brazos", PairAnims::carrier, null);
        add("en_brazos", PairAnims::carried, t -> CARRIED);
        add("llevar_caballito", PairAnims::piggyCarrier, null);
        add("a_caballito", PairAnims::piggyRider, t -> PIGGY);
        add("vals_guia", (r, t) -> waltz(r, t, true), PairAnims::waltzRoot);
        add("vals_pareja", (r, t) -> waltz(r, t, false), PairAnims::waltzRoot);
        add("estrellas_a", (r, t) -> stars(r, t, true), t -> LYING);
        add("estrellas_b", (r, t) -> stars(r, t, false), t -> LYING);
        add("sentarse_abrazo", PairAnims::sitHug, t -> SITTING);
        add("sentarse_apoyo", PairAnims::sitLean, t -> SITTING);
        add("caido", PairAnims::downed, PairAnims::downedRoot);
        add("ayudar_levantar", PairAnims::helper, null);
        add("levantarse", PairAnims::getUp, PairAnims::getUpRoot);
    }

    private static void add(String name, Pose pose, RootFn root) {
        ALL.put(name, new Anim(pose, root));
    }

    // ---------------------------------------------------------------- utilidades

    private static final Rig.P TMP = new Rig().head;

    /** Lleva el brazo hacia un punto con peso w (0 = como estaba). */
    static void reach(Rig.P arm, float x, float y, float z, float w) {
        if (w <= 0.0f) {
            return;
        }
        float p = arm.pitch, yw = arm.yaw, rl = arm.roll;
        arm.aim(x, y, z);
        TMP.pitch = arm.pitch;
        TMP.yaw = arm.yaw;
        TMP.roll = arm.roll;
        arm.pitch = lerp(p, TMP.pitch, w);
        arm.yaw = lerpAngle(yw, TMP.yaw, w);
        arm.roll = lerp(rl, TMP.roll, w);
    }

    static float lerpAngle(float a, float b, float w) {
        float d = b - a;
        while (d > PI) {
            d -= 2 * PI;
        }
        while (d < -PI) {
            d += 2 * PI;
        }
        return a + d * w;
    }

    /** Punto del companero de enfrente (a D bloques, desplazado lat bloques a mi izquierda). */
    static float[] face(float d, float lat, float bx, float by, float bz) {
        return new float[]{-bx + lat * PX, by, -d * PX - bz};
    }

    /** Punto del companero a mi derecha (mismo sentido), a lat bloques. */
    static float[] right(float lat, float bx, float by, float bz) {
        return new float[]{bx - lat * PX, by, bz};
    }

    /** Punto de un cuerpo con raiz, en coordenadas de modelo "sin raiz" del mismo cuerpo. */
    static float[] unroot(Root root, float x, float y, float z) {
        float[] r = modelToRender(root, x, y, z);
        return renderToModel(null, r[0], r[1], r[2]);
    }

    /** Punto en coordenadas sin raiz -> coordenadas propias del cuerpo con raiz (para apuntar). */
    static float[] inRoot(Root root, float[] p) {
        float[] r = modelToRender(null, p[0], p[1], p[2]);
        return renderToModel(root, r[0], r[1], r[2]);
    }

    /** Punto de quien va encima (pies desplazados fwd/up/left, misma orientacion) en mi modelo. */
    static float[] rider(Root riderRoot, float fwd, float up, float left, float bx, float by, float bz) {
        float[] r = modelToRender(riderRoot, bx, by, bz);
        return renderToModel(null, r[0] - left, r[1] + up, r[2] - fwd);
    }

    /** Punto del que carga, visto desde el que es llevado (inversa de rider). */
    static float[] carrierInRider(Root riderRoot, float fwd, float up, float left, float ax, float ay, float az) {
        float[] r = modelToRender(null, ax, ay, az);
        return renderToModel(riderRoot, r[0] + left, r[1] - up, r[2] + fwd);
    }

    static void aimAt(Rig.P arm, float[] p, float w) {
        reach(arm, p[0], p[1], p[2], w);
    }

    static void breathe(Rig r, float t, float amount) {
        float b = sin(t * 0.07f) * amount;
        r.body.pitch += b * 0.012f;
        r.head.pitch += b * 0.01f;
    }

    // ---------------------------------------------------------------- abrazo (los dos igual)

    /** Abrazo cruzado: cada uno pasa el brazo derecho por encima del hombro y el izquierdo por la cintura. */
    static void hug(Rig r, float t) {
        float e = env(t, 0, 12, 66, 80);
        float sq = env(t, 16, 28, 48, 60);
        r.restUpper();
        r.torso(0.04f * e, 0, 0);
        float d = Layout.HUG;
        // derecho por encima del hombro izquierdo del otro, hacia su espalda
        float[] hi = face(d, 0, 6.5f - 0.8f * sq, -3.4f, 1.0f);
        aimAt(r.rightArm, hi, e);
        // izquierdo rodeando la cintura (su lado derecho)
        float[] lo = face(d, 0, -5.0f + 0.7f * sq, 8.5f, 0.6f + 0.4f * sq);
        aimAt(r.leftArm, lo, e);
        // palmaditas en la espalda
        float pat = env(t, 30, 34, 50, 54) * Math.max(0, sin(t * 0.9f));
        r.leftArm.pitch -= 0.10f * pat;
        // cabeza hacia la derecha, por encima de su hombro
        r.head.set(0.10f * e, -0.25f * e, -0.10f * e);
        breathe(r, t, 1 + sq);
    }

    // ---------------------------------------------------------------- beso en la mejilla

    static void kiss(Rig r, float t) {
        float e = env(t, 4, 18, 36, 50);
        r.restUpper();
        r.torso(0.10f * e, -0.10f * e, 0);
        // manos atras, timido
        float hb = env(t, 0, 10, 48, 60);
        r.rightArm.set(0.38f * hb, 0, 0.18f * hb);
        r.leftArm.set(0.38f * hb, 0, -0.18f * hb);
        // la cabeza gira hacia la mejilla izquierda del otro (a mi izquierda)
        float turn = env(t, 2, 14, 44, 56);
        r.head.set(0.12f * e, -0.95f * turn, 0.08f * e);
        r.head.z -= 0.6f * e;
        breathe(r, t, 1);
    }

    static void kissed(Rig r, float t) {
        float e = env(t, 4, 18, 36, 50);
        float after = env(t, 44, 50, 56, 60);
        r.restUpper();
        // ofrece la mejilla girando un poco la cabeza y luego baja la mirada contento
        r.head.set(-0.05f * e + 0.30f * after, 0.22f * e, -0.10f * e);
        // manos juntas delante
        float hands = env(t, 0, 10, 50, 60);
        r.rightArm.set(-0.38f * hands, -0.40f * hands, 0);
        r.leftArm.set(-0.38f * hands, 0.40f * hands, 0);
        // pequeno balanceo de alegria
        float sway = after * sin(t * 0.5f);
        r.torso(-0.03f * after, 0.05f * sway, 0.04f * sway);
        breathe(r, t, 1);
    }

    // ---------------------------------------------------------------- chocar los cinco (los dos igual)

    static void highFive(Rig r, float t) {
        float d = Layout.HIGH_FIVE;
        r.restUpper();
        float lean = env(t, 6, 13, 18, 28);
        r.torso(0.08f * lean, -0.08f * lean, 0);
        // punto de contacto: en el medio, por encima de la cabeza
        float[] hit = {0, -6.0f, -d * PX / 2};
        float[] up = {-6.5f, -9.5f, 2.0f};
        float wind = env(t, 0, 7, 9, 13);
        float strike = env(t, 9, 14, 18, 28);
        float[] target = up;
        float w;
        if (strike > 0) {
            target = new float[]{lerp(up[0], hit[0], strike), lerp(up[1], hit[1], strike), lerp(up[2], hit[2], strike)};
            w = 1.0f - ramp(t, 24, 38);
        } else {
            w = wind;
        }
        aimAt(r.rightArm, target, w);
        // rebote al chocar
        float bump = env(t, 14, 15, 16, 19);
        r.rightArm.pitch += 0.15f * bump;
        // el otro brazo celebra un poco despues
        float cheer = env(t, 20, 26, 34, 42);
        r.leftArm.set(-0.35f * cheer, 0, -0.25f * cheer);
        r.look(hit[0], hit[1], hit[2], 0.8f * env(t, 4, 10, 18, 26));
        r.drop(-0.6f * env(t, 22, 26, 28, 33));
    }

    // ---------------------------------------------------------------- apreton de manos (los dos igual)

    static void handshake(Rig r, float t) {
        float d = Layout.SHAKE;
        float e = env(t, 0, 10, 38, 48);
        float shake = env(t, 12, 16, 30, 36) * sin((t - 12) * 0.95f) * 1.3f;
        r.restUpper();
        r.torso(0.04f * e, 0, 0);
        float[] grip = {0, 8.6f + shake, -d * PX / 2};
        aimAt(r.rightArm, grip, e);
        r.leftArm.set(0, 0, -0.05f);
        // asiente mientras saluda
        float nod = env(t, 14, 18, 28, 34) * sin(t * 0.47f);
        r.look(0, -4.0f, -d * PX, 0.9f * e);
        r.head.pitch += 0.10f * nod;
    }

    // ---------------------------------------------------------------- palmadita en el hombro

    static void pat(Rig r, float t) {
        float d = Layout.PAT;
        float e = env(t, 0, 10, 34, 44);
        r.restUpper();
        // hombro izquierdo del otro (queda delante de mi brazo derecho)
        float[] shoulder = face(d, 0, 6.0f, -1.6f, 0.5f);
        aimAt(r.rightArm, shoulder, e);
        float pats = env(t, 10, 12, 30, 33);
        r.rightArm.pitch -= 0.16f * pats * Math.max(0, sin((t - 10) * 0.8f));
        r.look(0, -4.0f, -d * PX, 0.8f * e);
        r.head.roll += 0.10f * e;
        breathe(r, t, 1);
    }

    static void patted(Rig r, float t) {
        float e = env(t, 0, 10, 34, 44);
        r.restUpper();
        float nods = env(t, 10, 14, 30, 34) * Math.max(0, sin((t - 10) * 0.8f));
        r.look(0, -4.0f, -Layout.PAT * PX, 0.8f * e);
        r.head.pitch += 0.18f * nods;
        r.head.roll -= 0.08f * e;
        // los hombros se encogen un poquito con cada palmada
        r.leftArm.pitch -= 0.04f * nods;
        breathe(r, t, 1);
    }

    // ---------------------------------------------------------------- de la mano (solo brazos)

    /** mine = brazo derecho (el que va a la izquierda) o izquierdo (el de la derecha). */
    static void holdHand(Rig r, float t, boolean leftPerson) {
        float l = Layout.HAND_SIDE;
        float sway = 0.07f * sin(t * 0.33f);
        if (leftPerson) {
            // el otro esta a mi derecha: mano en el medio
            float[] hand = right(l, 0, 0, 0);
            float midX = (-5.0f + (hand[0] + 5.0f)) / 2.0f;
            r.rightArm.at(-5, 2, 0);
            reach(r.rightArm, midX, 11.4f, -1.2f, 1);
            r.rightArm.pitch += sway;
        } else {
            // el otro esta a mi izquierda (+x)
            float midX = (5.0f + (l * PX - 5.0f)) / 2.0f;
            r.leftArm.at(5, 2, 0);
            reach(r.leftArm, midX, 11.4f, -1.2f, 1);
            r.leftArm.pitch += sway;
        }
        // de vez en cuando se miran
        float glance = env(t % 220, 120, 132, 160, 172);
        if (glance > 0) {
            r.head.set(r.head.pitch, r.head.yaw + (leftPerson ? -0.7f : 0.7f) * glance, r.head.roll);
        }
    }

    // ---------------------------------------------------------------- en brazos (princesa)

    static final float LEG_DROOP = 0.30f;

    static float[] carriedPoint(float bx, float by, float bz) {
        return rider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, bx, by, bz);
    }

    static void carrier(Rig r, float t) {
        r.restUpper();
        r.torso(-0.06f, 0, 0);
        // brazo derecho debajo de las rodillas, izquierdo debajo de la espalda
        // debajo de los muslos (las piernas cuelgan LEG_DROOP)
        float ky = 6.0f * cos(LEG_DROOP) - 4.3f * sin(LEG_DROOP);
        float kz = 6.0f * sin(LEG_DROOP) + 4.3f * cos(LEG_DROOP);
        float[] knees = carriedPoint(-1.9f, 12.0f + ky, kz);
        float[] back = carriedPoint(0, 6.0f, 4.3f);
        aimAt(r.rightArm, knees, 1);
        aimAt(r.leftArm, back, 1);
        float[] face = carriedPoint(0, -4, 0);
        r.look(face[0], face[1], face[2], 0.6f);
        breathe(r, t, 1);
    }

    static void carried(Rig r, float t) {
        r.restAll();
        // piernas colgando un poco sobre el brazo
        r.rightLeg.set(LEG_DROOP, 0, 0.04f);
        r.leftLeg.set(LEG_DROOP + 0.08f, 0, -0.04f);
        // brazo derecho (pegado al pecho del que carga) alrededor de su cuello
        float[] neck = carrierInRider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, 2.5f, 1.2f, 2.6f);
        aimAt(r.rightArm, neck, 1);
        // el otro sobre la panza
        r.leftArm.set(-0.30f, 0.40f, 0);
        float[] face = carrierInRider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, 0, -4, -3);
        r.look(face[0], face[1], face[2], 0.7f);
        float kick = sin(t * 0.11f);
        r.rightLeg.pitch += 0.06f * kick;
        r.leftLeg.pitch -= 0.06f * kick;
        breathe(r, t, 1);
    }

    // ---------------------------------------------------------------- a caballito

    static float[] riderPoint(float bx, float by, float bz) {
        return rider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, bx, by, bz);
    }

    static final float PIG_LEG_PITCH = -1.20f, PIG_LEG_YAW = 0.75f;

    /** Debajo del muslo del jinete (lado derecho o izquierdo) en su modelo. */
    static float[] thigh(boolean rightSide) {
        float sign = rightSide ? 1 : -1;
        float[] m = rot(PIG_LEG_PITCH, sign * PIG_LEG_YAW, 0);
        float[] v = mul(m, 0, 5.0f, 4.0f);
        return new float[]{(rightSide ? -1.9f : 1.9f) + v[0], 12 + v[1], v[2]};
    }

    static void piggyCarrier(Rig r, float t) {
        r.restUpper();
        r.torso(0.55f, 0, 0);
        // manos debajo de los muslos del que va atras
        float[] rt = thigh(true), lt = thigh(false);
        aimAt(r.rightArm, riderPoint(rt[0], rt[1], rt[2]), 1);
        aimAt(r.leftArm, riderPoint(lt[0], lt[1], lt[2]), 1);
        r.head.set(-0.15f, 0.10f * sin(t * 0.03f), 0);
        breathe(r, t, 1.5f);
    }

    static void piggyRider(Rig r, float t) {
        r.restAll();
        // piernas abiertas rodeando la cintura
        float swing = 0.05f * sin(t * 0.12f);
        r.rightLeg.set(PIG_LEG_PITCH + swing, PIG_LEG_YAW, 0);
        r.leftLeg.set(PIG_LEG_PITCH - swing, -PIG_LEG_YAW, 0);
        // brazos por encima de los hombros, las manos se cruzan en su pecho
        float[] rs = carrierInRider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, 2.5f, 0.5f, -5.5f);
        float[] ls = carrierInRider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, -2.5f, 1.0f, -5.5f);
        aimAt(r.rightArm, rs, 1);
        aimAt(r.leftArm, ls, 1);
        r.head.set(0.10f, 0.25f + 0.20f * sin(t * 0.03f), -0.15f);
    }

    // ---------------------------------------------------------------- vals

    static final float WALTZ_TURN = 2 * PI / 100.0f;

    static Root waltzRoot(float t) {
        float in = ramp(t, 0, 20);
        float phi = WALTZ_TURN * Math.max(0, t - 10) * in;
        float hd = Layout.WALTZ / 2;
        float rise = 0.035f * (0.5f - 0.5f * cos(2 * PI * t / 30.0f)) * in;
        return new Root(0, phi, 0, hd * (1 - cos(phi)), rise, -hd * sin(phi));
    }

    static void waltz(Rig r, float t, boolean lead) {
        float d = Layout.WALTZ;
        float e = ramp(t, 0, 12);
        r.restUpper();
        if (lead) {
            // derecha en su espalda (su lado izquierdo), izquierda tomando su mano derecha a un lado
            float[] back = face(d, 0, 4.6f, 6.4f, 0.4f);
            aimAt(r.rightArm, back, e);
            float[] hold = face(d, 0, -12.0f, -1.0f, -d * PX / 2 + 0.0f);
            aimAt(r.leftArm, hold, e);
        } else {
            // izquierda sobre su hombro derecho, derecha tomando su mano izquierda
            float[] shoulder = face(d, 0, -6.5f, -2.6f, -1.2f);
            aimAt(r.leftArm, shoulder, e);
            float[] hold = face(d, 0, 12.0f, -1.0f, -d * PX / 2);
            aimAt(r.rightArm, hold, e);
        }
        // pasos en 3/4: un pie y luego el otro, con subida y bajada
        float beat = t / 10.0f;
        float step = sin(beat * PI * 2.0f / 3.0f) * e;
        r.rightLeg.rest();
        r.leftLeg.rest();
        r.rightLeg.pitch = -0.30f * step;
        r.leftLeg.pitch = 0.30f * step;
        r.rightLeg.roll = 0.05f * e;
        r.leftLeg.roll = -0.05f * e;
        r.look(0, -4.5f, -d * PX, 0.6f * e);
        r.head.roll += (lead ? 0.06f : -0.10f) * e;
        r.torso(0.0f, 0.0f, 0.03f * sin(beat * PI * 2.0f / 3.0f) * e);
    }

    // ---------------------------------------------------------------- mirar las estrellas

    static void stars(Rig r, float t, boolean leftPerson) {
        r.restAll();
        float l = Layout.STARS_SIDE;
        // manos juntas entre los dos
        if (leftPerson) {
            float midX = (-6.0f + (-l * PX + 6.0f)) / 2.0f;
            reach(r.rightArm, midX, 12.2f, -0.2f, 1);
            // a veces senala una estrella
            float point = env(t % 300, 90, 110, 170, 190);
            r.leftArm.set(lerp(0, -2.55f, point), lerp(-0.1f, 0.25f + 0.2f * sin(t * 0.05f), point), -0.1f * (1 - point));
            r.rightLeg.set(0, 0, 0.06f);
            r.leftLeg.set(0, 0, -0.04f);
        } else {
            float midX = (6.0f + (l * PX - 6.0f)) / 2.0f;
            reach(r.leftArm, midX, 12.2f, -0.2f, 1);
            r.rightArm.set(-0.62f, -0.55f, 0);
            // una rodilla un poco levantada
            r.rightLeg.set(-0.35f, 0, 0.02f);
            r.leftLeg.set(0, 0, -0.03f);
        }
        float lookAt = env(t % 260, 60, 75, 120, 135);
        r.head.set(-0.15f, (leftPerson ? -0.55f : 0.55f) * lookAt, 0);
        breathe(r, t, 1.5f);
    }

    // ---------------------------------------------------------------- sentarse juntos

    static void sitLegs(Rig r) {
        r.rightLeg.set(-PI / 2 + 0.12f, 0.10f, 0);
        r.leftLeg.set(-PI / 2 + 0.12f, -0.10f, 0);
    }

    static void sitHug(Rig r, float t) {
        r.restAll();
        sitLegs(r);
        float l = Layout.SIT_SIDE;
        r.torso(0.02f, 0, -0.03f);
        // brazo derecho por detras de la espalda del otro, a la altura de los hombros
        float[] back = right(l, 1.0f, 10.0f, 5.6f);
        reach(r.rightArm, back[0], back[1], back[2], 1);
        // la otra mano sobre la rodilla
        r.leftArm.set(-1.05f, 0.18f, 0);
        r.head.set(0.12f, -0.30f, -0.10f);
        breathe(r, t, 1.4f);
    }

    static void sitLean(Rig r, float t) {
        r.restAll();
        sitLegs(r);
        // apoya la cabeza en el hombro del otro (a su izquierda)
        r.torso(0.04f, 0.0f, 0.05f);
        r.head.set(0.10f, 0.15f, 0.42f);
        // las manos delante, sobre las rodillas
        r.rightArm.set(-1.10f, -0.20f, 0);
        r.leftArm.set(-1.10f, 0.22f, 0);
        breathe(r, t, 1.4f);
    }

    // ---------------------------------------------------------------- caido y levantarse

    static Root downedRoot(float t) {
        float roll = 0.12f + 0.03f * sin(t * 0.02f);
        return new Root(PI / 2, 0, roll, 0, -0.56f, 0);
    }

    static void downed(Rig r, float t) {
        r.restAll();
        // un brazo se levanta pidiendo ayuda cada tanto
        float ask = env(t % 160, 40, 60, 90, 115);
        r.rightArm.set(-1.25f * ask - 0.05f, 0.25f * ask, 0.15f);
        r.leftArm.set(0.05f, 0, -0.25f);
        r.rightLeg.set(-0.25f, 0, 0.10f);
        r.leftLeg.set(0.05f, 0, -0.08f);
        r.head.set(-0.10f, 0.45f * sin(t * 0.015f), 0);
        breathe(r, t, 2.5f);
    }

    /** Punto de agarre (mano con mano) en el modelo del que ayuda, segun el tiempo. */
    static float[] helpGrip(float t) {
        float sit = ramp(t, 16, 27);
        float stand = ramp(t, 27, 37);
        // distancia desde el caido hacia el que ayuda y altura sobre el suelo (px)
        float db = lerp(lerp(0.5f, 6.0f, sit), 8.8f, stand);
        float h = lerp(lerp(5.0f, 13.0f, sit), 15.4f, stand);
        return new float[]{0, 24.0f - h, -(Layout.HELP * PX - db)};
    }

    static void helper(Rig r, float t) {
        float e = env(t, 0, 10, 42, 50);
        float crouch = lerp(lerp(1.0f, 0.30f, ramp(t, 16, 27)), 0.0f, ramp(t, 27, 37)) * e;
        r.restAll();
        // en cuclillas con un pie adelante; el cuerpo baja lo que bajan las piernas
        r.rightLeg.set(-0.9f * crouch, 0.75f * crouch, 0);
        r.leftLeg.set(0.9f * crouch, 0, 0);
        float drop = 12.0f * (1 - cos(0.9f * crouch));
        r.torso(1.1f * crouch + 0.08f * e, 0, 0);
        r.head.y += drop;
        r.body.y += drop;
        r.rightArm.y += drop;
        r.leftArm.y += drop;
        r.rightLeg.y += drop;
        r.leftLeg.y += drop;
        float[] g = helpGrip(t);
        aimAt(r.rightArm, g, e);
        // el otro brazo apoyado en la rodilla y luego atras para hacer fuerza
        r.leftArm.set(-0.9f * crouch + 0.25f * e * (1 - crouch), 0, -0.10f * e);
        r.look(g[0], g[1] - 6.0f, g[2] - 4.0f, 0.85f * e);
    }

    static Root getUpRoot(float t) {
        float sit = ramp(t, 16, 27);
        float stand = ramp(t, 27, 37);
        Root lying = new Root(PI / 2, 0, 0.10f, 0, -0.56f, 0);
        Root r = lying.lerp(SITTING, sit);
        return r.lerp(Root.NONE, stand);
    }

    static void getUp(Rig r, float t) {
        r.restAll();
        Root root = getUpRoot(t);
        float sit = ramp(t, 16, 27);
        float stand = ramp(t, 27, 37);
        // piernas: hacia delante mientras se sienta, luego de pie
        float legs = -(PI / 2 - root.pitch()) * (1 - stand);
        r.rightLeg.set(legs, 0, 0.05f);
        r.leftLeg.set(legs + 0.10f * sit * (1 - stand), 0, -0.05f);
        // se inclina hacia delante al levantarse
        r.torso(0.20f * sit * (1 - stand) + 0.10f * stand * (1 - ramp(t, 37, 46)), 0, 0);
        // la mano derecha agarra la del que ayuda (mismo punto)
        float[] g = helpGrip(t);
        float[] mine = face(Layout.HELP, 0, g[0], g[1], g[2]);
        float[] local = inRoot(root, mine);
        float grab = env(t, 4, 14, 36, 44);
        aimAt(r.rightArm, local, grab);
        // al final se sacude la ropa
        float dust = env(t, 38, 41, 47, 50) * sin(t * 1.6f);
        r.leftArm.set(-0.4f - 0.25f * dust, 0.3f, 0);
        r.look(0, -4, -Layout.HELP * PX, 0.5f * grab);
        r.head.pitch += 0.25f * env(t, 40, 43, 46, 50);
    }
}
