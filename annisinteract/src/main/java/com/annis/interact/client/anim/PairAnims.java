package com.annis.interact.client.anim;

import com.annis.interact.Layout;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.annis.interact.client.anim.Rig.*;

/**
 * Animaciones de pareja de Annis Interacciones (propias, hechas a mano).
 *
 * Estilo "suelto": codos y rodillas con IK, respiracion y balanceo, anticipacion y rebote.
 * Annis NPC ademas pasa todo por resortes (inercia), asi nada se mueve rigido.
 * Cada pareja comparte el mismo reloj (t en ticks desde el inicio), asi los contactos coinciden.
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
    /** Altura del suelo en el modelo (pies en reposo). */
    private static final float GROUND = 24.0f;
    private static final Root LYING = new Root(PI / 2, 0, 0, 0, -0.575f, 0);
    private static final Root SITTING = new Root(0, 0, 0, 0, -0.575f, 0);
    private static final Root CARRIED = new Root(PI / 2, -PI / 2, 0, 0, 0, 0);
    private static final Root PIGGY = new Root(-0.20f, 0, 0, 0, 0, 0);

    private PairAnims() {
    }

    static {
        add("abrazo", PairAnims::hug, null);
        add("abrazo_cuello", PairAnims::hugNeck, null);
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
        add("lento_a", (r, t) -> slow(r, t, true), PairAnims::slowRoot);
        add("lento_b", (r, t) -> slow(r, t, false), PairAnims::slowRoot);
        add("estrellas_a", (r, t) -> stars(r, t, true), t -> LYING);
        add("estrellas_b", (r, t) -> stars(r, t, false), t -> LYING);
        add("sentarse_abrazo", PairAnims::sitHug, t -> SITTING);
        add("sentarse_apoyo", PairAnims::sitLean, t -> SITTING);
        add("caido", (r, t) -> downed(r, t, true), t -> downedRoot(t, true));
        add("caido_suelo", (r, t) -> downed(r, t, false), t -> downedRoot(t, false));
        add("ayudar_levantar", PairAnims::helper, null);
        add("levantarse", PairAnims::getUp, PairAnims::getUpRoot);
        add("propuesta", PairAnims::propose, null);
        add("recibir_propuesta", PairAnims::proposed, null);
        add("mimos_toca", PairAnims::toucher, null);
        add("mimos_recibe", PairAnims::touched, null);
        add("hombro_rodea", PairAnims::shoulderHold, null);
        add("hombro_apoya", PairAnims::shoulderLean, null);
        add("besar_mano", PairAnims::handKiss, null);
        add("swing_guia", (r, t) -> swing(r, t, true), t -> swingRoot(t, true));
        add("swing_sigue", (r, t) -> swing(r, t, false), t -> swingRoot(t, false));
        add("bachata_guia", (r, t) -> bachata(r, t, true), t -> bachataRoot(t, true));
        add("bachata_sigue", (r, t) -> bachata(r, t, false), t -> bachataRoot(t, false));
        add("tango_guia", (r, t) -> tango(r, t, true), t -> tangoRoot(t, true));
        add("tango_sigue", (r, t) -> tango(r, t, false), t -> tangoRoot(t, false));
        add("recibir_beso_mano", PairAnims::handKissed, null);
    }

    private static void add(String name, Pose pose, RootFn root) {
        ALL.put(name, new Anim(pose, root));
    }

    // ================================================================= utilidades

    private static final Rig.P TMP = new Rig().head;

    /** Brazo recto hacia un punto con peso w (para modelos sin codo y gestos simples). */
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

    static float[] lerp3(float[] a, float[] b, float w) {
        return new float[]{lerp(a[0], b[0], w), lerp(a[1], b[1], w), lerp(a[2], b[2], w)};
    }

    /** Sube con un pequeno rebote al final (se pasa y vuelve). */
    static float backOut(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
    }

    static float easeIn(float x) {
        x = clamp01(x);
        return x * x;
    }

    /** Sube con rebote entre a y b, baja suave entre c y d. */
    static float envB(float t, float a, float b, float c, float d) {
        return Math.min(backOut((t - a) / (b - a)), 1 - ramp(t, c, d));
    }

    /**
     * Mano a un punto doblando el codo, mezclado con la pose actual (w). La punta actual sirve
     * de inicio, asi el brazo "viaja" en vez de saltar. rigid = destino si el modelo no tiene codos.
     */
    static void hand(Rig.P arm, float[] target, float px, float py, float pz, float[] rigid, float w) {
        if (w <= 0.001f) {
            return;
        }
        float[] from = Rig.tip(arm, true);
        float[] t = w >= 0.999f ? target : lerp3(from, target, w);
        float[] rt = rigid == null ? t : (w >= 0.999f ? rigid : lerp3(from, rigid, w));
        Rig.hand(arm, t, px, py, pz, rt);
    }

    static void handR(Rig r, float[] target, float[] rigid, float w) {
        hand(r.rightArm, target, -0.45f, 0.8f, 0.35f, rigid, w);
    }

    static void handL(Rig r, float[] target, float[] rigid, float w) {
        hand(r.leftArm, target, 0.45f, 0.8f, 0.35f, rigid, w);
    }

    /** Pie a un punto (rodilla hacia delante por defecto). */
    static void foot(Rig.P leg, float x, float y, float z, float px, float py, float pz) {
        Rig.foot(leg, new float[]{x, y, z}, px, py, pz);
    }

    /** Punto del companero de enfrente (a d bloques, desplazado lat bloques a mi izquierda). */
    static float[] face(float d, float lat, float bx, float by, float bz) {
        return new float[]{-bx + lat * PX, by, -d * PX - bz};
    }

    /** Punto del companero a mi derecha (mismo sentido), a lat bloques. */
    static float[] right(float lat, float bx, float by, float bz) {
        return new float[]{bx - lat * PX, by, bz};
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

    /** Respiracion: el pecho sube, los hombros acompanan con un pequeno retraso. */
    static void breathe(Rig r, float t, float amount) {
        float b = sin(t * 0.075f) * amount;
        float b2 = sin(t * 0.075f - 0.7f) * amount;
        r.body.pitch += b * 0.014f;
        r.head.pitch -= b * 0.012f;
        r.body.y -= 0.10f * b;
        r.head.y -= 0.12f * b;
        r.rightArm.y -= 0.12f * b2;
        r.leftArm.y -= 0.12f * b2;
        r.rightArm.roll += 0.018f * b2;
        r.leftArm.roll -= 0.018f * b2;
    }

    /** Cambio de peso lento de un pie al otro. */
    static void sway(Rig r, float t, float amount) {
        float s = sin(t * 0.034f) * amount;
        r.body.roll += 0.025f * s;
        r.head.roll -= 0.03f * s;
        r.body.x += 0.25f * s;
        r.head.x += 0.30f * s;
    }

    /**
     * Piernas relajadas de pie: se suman a lo que traen (el paso de caminar de vanilla o de EMF),
     * asi que si el personaje camina sus piernas siguen moviendose y quietas quedan sueltas.
     */
    static void relaxedLegs(Rig r, float t, float amount) {
        float s = sin(t * 0.034f);
        r.walkLegs = true;
        r.rightLeg.keep();
        r.leftLeg.keep();
        // la pierna que descansa se adelanta un poco y se abre (Fresh: piezas rigidas sueltas)
        r.rightLeg.pitch += (-0.03f - 0.07f * Math.max(0, s)) * amount;
        r.leftLeg.pitch += (-0.03f - 0.07f * Math.max(0, -s)) * amount;
        r.rightLeg.roll += (0.035f + 0.02f * Math.max(0, -s)) * amount;
        r.leftLeg.roll += (-0.035f - 0.02f * Math.max(0, s)) * amount;
        r.rightLeg.z -= 0.3f * Math.max(0, s) * amount;
        r.leftLeg.z -= 0.3f * Math.max(0, -s) * amount;
    }

    /** Arrodillarse: pie derecho adelante (rodilla arriba), rodilla izquierda al suelo. */
    static void kneel(Rig r, float k) {
        // piernas rigidas: una adelante, otra atras; el cuerpo baja lo que bajan las caderas
        float a = 0.95f * k;
        float drop = 12.0f * (1 - cos(a));
        r.body.y += drop;
        r.head.y += drop;
        r.rightArm.y += drop;
        r.leftArm.y += drop;
        r.rightLeg.at(-1.9f, 12 + drop, 0);
        r.leftLeg.at(1.9f, 12 + drop, 0);
        r.rightLeg.set(-a, 0.12f * k, 0.05f * k);
        r.leftLeg.set(a * 0.85f, -0.08f * k, -0.05f * k);
    }

    // ================================================================= abrazo

    /** Abrazo: el que abraza rodea la espalda; el otro le echa los brazos al cuello. Cabezas al lado. */
    static void hug(Rig r, float t) {
        hugBody(r, t, true);
    }

    static void hugNeck(Rig r, float t) {
        hugBody(r, t, false);
    }

    /** Punto del otro en el abrazo, contando que su torso tambien se inclina hacia su izquierda. */
    static float[] hf(float d, float lean, float bx, float by, float bz) {
        return face(d, 0, bx + HUG_SLIDE * lean / HUG_LEAN + (12 - by) * sin(lean), by, bz);
    }

    static final float HUG_LEAN = 0.18f, HUG_SLIDE = 1.0f;

    static void hugBody(Rig r, float t, boolean waist) {
        float e = envB(t, 0, 14, 64, 80);
        float sq = env(t, 16, 30, 46, 60);
        float d = Layout.HUG;
        r.restUpper();
        relaxedLegs(r, t, 1);
        // el torso se inclina hacia su izquierda: las cabezas quedan una al lado de la otra
        // sin despegarlas del cuello
        float lean = HUG_LEAN * e;
        r.torso(0.06f * e + 0.03f * sq, 0.05f * sin(t * 0.09f) * sq, lean);
        // y la cadera se corre un poquito (como en Fresh Animations al cambiar el peso)
        float slide = HUG_SLIDE * e;
        r.head.x += slide;
        r.body.x += slide;
        r.rightArm.x += slide;
        r.leftArm.x += slide;
        r.drop(-0.5f * sq);
        float pat = env(t, 30, 34, 48, 52) * Math.max(0, sin(t * 0.9f));
        if (waist) {
            // manos en su espalda, a media altura; palmaditas
            // brazos por los costados del otro, cerrandose en su espalda
            hand(r.rightArm, hf(d, lean, 2.4f, 6.0f - 1.2f * pat, 3.2f), -0.9f, 0.3f, 0.2f,
                    hf(d, lean, 7.0f - 0.6f * sq, 8.5f - 1.0f * pat, 3.4f), e);
            hand(r.leftArm, hf(d, lean, -2.4f, 6.0f, 3.2f), 0.9f, 0.3f, 0.2f,
                    hf(d, lean, -4.6f + 0.6f * sq, 7.2f, 3.4f), e);
        } else {
            // brazos por encima de sus hombros, hasta la nuca
            hand(r.rightArm, hf(d, lean, 2.0f, 0.6f, 3.0f), -0.6f, -0.7f, 0.2f,
                    hf(d, lean, 6.0f, -1.6f, 0.0f), e);
            hand(r.leftArm, hf(d, lean, -2.0f, 0.6f, 3.0f), 0.6f, -0.7f, 0.2f,
                    hf(d, lean, -6.0f, -1.6f, 0.0f), e);
        }
        // la cabeza sigue pegada al cuello: gira la cara al costado y se apoya un poco mas
        r.head.set(0.10f * e, (0.32f + 0.06f * sin(t * 0.11f) * sq) * e, lean + 0.12f * e);
        breathe(r, t, 1 + sq);
    }

    // ================================================================= beso en la mejilla

    static void kiss(Rig r, float t) {
        float e = envB(t, 4, 18, 36, 50);
        float pop = env(t, 14, 20, 34, 42);
        r.restUpper();
        relaxedLegs(r, t, 1 - pop);
        r.torso(0.10f * e, -0.10f * e, 0);
        r.drop(-0.6f * pop);
        // manos atras, timido
        float hb = env(t, 0, 10, 48, 60);
        handR(r, new float[]{-1.6f, 9.6f, 3.6f}, null, hb);
        handL(r, new float[]{1.6f, 9.6f, 3.6f}, null, hb);
        // un pie se levanta hacia atras en el momento del beso
        r.leftLeg.keep();
        r.leftLeg.pitch += 0.55f * pop;
        r.leftLeg.roll += -0.06f * pop;
        // la cabeza gira hacia la mejilla izquierda del otro
        float turn = env(t, 2, 14, 44, 56);
        r.head.set(0.12f * e, -0.95f * turn, 0.10f * e);
        r.head.z -= 0.6f * e;
        breathe(r, t, 1);
    }

    static void kissed(Rig r, float t) {
        float e = env(t, 4, 18, 36, 50);
        float after = env(t, 40, 46, 54, 60);
        float hands = env(t, 0, 10, 40, 48);
        r.restUpper();
        relaxedLegs(r, t, 1);
        // ofrece la mejilla, luego baja la mirada contento
        r.head.set(-0.05f * e + 0.30f * after, 0.22f * e - 0.15f * after, -0.10f * e + 0.12f * after);
        // manos juntas delante del pecho
        handR(r, new float[]{-0.7f, 7.6f, -4.4f}, null, hands);
        handL(r, new float[]{0.7f, 7.6f, -4.4f}, null, hands);
        // despues: hombros arriba de alegria
        r.rightArm.y -= 0.8f * after;
        r.leftArm.y -= 0.8f * after;
        float bounce = after * Math.abs(sin(t * 0.45f));
        r.drop(-0.5f * bounce);
        r.torso(-0.03f * after, 0.04f * sin(t * 0.5f) * after, 0.04f * sin(t * 0.5f) * after);
        breathe(r, t, 1);
    }

    // ================================================================= chocar los cinco

    static void highFive(Rig r, float t) {
        float d = Layout.HIGH_FIVE;
        r.restUpper();
        relaxedLegs(r, t, 1);
        float lean = env(t, 7, 13, 17, 28);
        r.torso(0.10f * lean, -0.10f * lean, 0);
        float[] hit = {0, -6.0f, -d * PX / 2};
        float[] wind = {-6.5f, -6.5f, 2.2f};
        float windW = env(t, 0, 8, 9, 13);
        float strike = easeIn((t - 9) / 5.0f);
        float back = ramp(t, 17, 30);
        float out = 1 - ramp(t, 26, 40);
        // camino de la mano: atras -> golpe -> sigue hacia abajo cruzando
        float[] follow = {-2.0f, 6.0f, -5.0f};
        float[] target;
        if (t < 9) {
            target = wind;
        } else if (t < 17) {
            target = lerp3(wind, hit, Math.min(1, strike));
        } else {
            target = lerp3(hit, follow, back);
        }
        float w = t < 9 ? windW : out;
        // rebote al chocar
        float bump = env(t, 14, 15, 16, 19);
        target = new float[]{target[0], target[1] - 0.8f * bump, target[2] + 1.5f * bump};
        hand(r.rightArm, target, -0.5f, 0.6f, 0.4f, null, w);
        // el otro brazo festeja: puno arriba con el codo doblado
        float cheer = envB(t, 22, 28, 36, 44);
        handL(r, new float[]{6.5f, -4.5f, -2.5f}, null, cheer);
        r.look(hit[0], hit[1], hit[2], 0.85f * env(t, 4, 10, 18, 26));
        // anticipacion (se agacha) y saltito
        float crouch = env(t, 20, 23, 23, 25);
        float hop = env(t, 25, 27, 28, 32);
        r.drop(0.8f * crouch - 1.2f * hop);
        r.rightLeg.bend += 0.35f * crouch;
        r.leftLeg.bend += 0.35f * crouch;
        breathe(r, t, 1);
    }

    // ================================================================= apreton de manos

    static void handshake(Rig r, float t) {
        float d = Layout.SHAKE;
        float e = envB(t, 0, 12, 38, 48);
        float shake = env(t, 12, 16, 30, 36) * sin((t - 12) * 0.95f) * 1.4f;
        r.restUpper();
        relaxedLegs(r, t, 1);
        r.torso(0.05f * e, -0.06f * e, 0);
        float[] grip = {0, 8.4f + shake, -d * PX / 2};
        hand(r.rightArm, grip, -0.35f, 1.0f, 0.3f, null, e);
        // la otra mano suelta, codo un poco doblado
        r.leftArm.set(-0.05f, 0, -0.06f);
        r.leftArm.bend = -0.25f;
        float nod = env(t, 14, 18, 28, 34) * sin(t * 0.47f);
        r.look(0, -4.0f, -d * PX, 0.9f * e);
        r.head.pitch += 0.10f * nod;
        breathe(r, t, 1);
    }

    // ================================================================= palmadita

    static void pat(Rig r, float t) {
        float d = Layout.PAT;
        float e = envB(t, 0, 10, 34, 44);
        r.restUpper();
        relaxedLegs(r, t, 1);
        // cada palmadita: la mano sube y cae (el codo hace el trabajo)
        float k = (t - 10) * 0.8f;
        float pats = env(t, 10, 12, 30, 33) * (0.5f + 0.5f * sin(k));
        float[] shoulder = face(d, 0, 6.0f, -1.6f - 2.2f * pats, 0.6f);
        hand(r.rightArm, shoulder, -0.6f, 0.9f, 0.1f, null, e);
        r.look(0, -4.0f, -d * PX, 0.8f * e);
        r.head.roll += 0.10f * e;
        r.torso(0.04f * e, -0.05f * e, 0);
        breathe(r, t, 1);
        sway(r, t, 1);
    }

    static void patted(Rig r, float t) {
        float e = env(t, 0, 10, 34, 44);
        r.restUpper();
        relaxedLegs(r, t, 1);
        float k = (t - 10) * 0.8f;
        float hit = env(t, 10, 12, 30, 33) * Math.max(0, -sin(k));
        r.look(0, -4.0f, -Layout.PAT * PX, 0.8f * e);
        r.head.pitch += 0.16f * hit;
        r.head.roll += 0.12f * e;
        // el hombro que recibe baja un poco con cada palmada, sonrisa con la cabeza
        r.leftArm.y += 0.5f * hit;
        r.leftArm.roll -= 0.05f * hit;
        r.rightArm.bend = -0.15f;
        breathe(r, t, 1);
        sway(r, t, 1);
    }

    // ================================================================= de la mano (solo brazos)

    /**
     * En el juego: punto de encuentro de las manos calculado con las posiciones REALES de los dos
     * (en mi modelo), o null si no se encuentra al companero. Asi nunca quedan desfasados.
     */
    public interface HandLink {
        float[] meet(boolean leftPerson, float t);
    }

    public static HandLink handLink;

    static void holdHand(Rig r, float t, boolean leftPerson) {
        float l = Layout.HAND_SIDE;
        float swing = 1.2f * sin(t * 0.33f);
        float midX = -l * PX / 2;
        float[] meet = handLink == null ? null : handLink.meet(leftPerson, t);
        Rig.P arm = leftPerson ? r.rightArm : r.leftArm;
        float side = leftPerson ? -1 : 1;
        arm.at(5 * side, 2, 0);
        arm.set(0, 0, 0);
        float[] target = meet != null ? meet : new float[]{-side * midX, 10.6f, -1.4f + swing};
        hand(arm, target, 0.6f * side, 0.2f, 0.6f, null, 1);
        // de vez en cuando se miran
        float glance = env(t % 220, 120, 132, 160, 172);
        if (glance > 0) {
            r.head.set(r.head.pitch, r.head.yaw + (leftPerson ? -0.7f : 0.7f) * glance, r.head.roll);
        }
    }

    // ================================================================= en brazos

    static final float CARRIED_THIGH = -0.35f, CARRIED_KNEE = 1.35f;

    static float[] carriedPoint(float bx, float by, float bz) {
        return rider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, bx, by, bz);
    }

    static void carrier(Rig r, float t) {
        r.restUpper();
        relaxedLegs(r, t, 0.6f);
        r.torso(-0.07f, 0, 0);
        // hombros un poco adelante y abajo, como sosteniendo peso
        r.rightArm.z -= 1.4f;
        r.leftArm.z -= 1.4f;
        r.rightArm.y += 0.8f;
        r.leftArm.y += 0.8f;
        // brazo derecho bajo las rodillas, izquierdo bajo la espalda (los codos abajo)
        float[] kn = mul(rot(CARRIED_THIGH, 0, 0), 0, 5.0f, 4.2f);
        float[] knees = carriedPoint(-1.9f + kn[0], 12 + kn[1], kn[2]);
        float[] back = carriedPoint(0, 6.5f, 4.2f);
        hand(r.rightArm, knees, -0.4f, 1.0f, 0.2f, null, 1);
        hand(r.leftArm, back, 0.4f, 1.0f, 0.2f, null, 1);
        float[] face = carriedPoint(0, -4, 0);
        r.look(face[0], face[1], face[2], 0.6f);
        // lo mece un poquito
        r.body.roll += 0.03f * sin(t * 0.06f);
        breathe(r, t, 1.3f);
    }

    static void carried(Rig r, float t) {
        r.restAll();
        // muslos sobre el brazo, piernas colgando desde la rodilla
        float kick = sin(t * 0.11f);
        // piernas colgando sobre el brazo, pataleando un poquito
        r.rightLeg.set(0.30f + 0.06f * kick, 0, 0.05f);
        r.leftLeg.set(0.36f - 0.06f * kick, 0, -0.05f);
        r.rightLeg.bend = CARRIED_KNEE + 0.12f * kick;
        r.leftLeg.bend = CARRIED_KNEE - 0.12f * kick;
        // brazo derecho (pegado al pecho del que carga) alrededor de su cuello
        float[] neck = carrierInRider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, 2.2f, -1.4f, 3.2f);
        hand(r.rightArm, neck, 0, 0.0f, -1.0f, null, 1);
        // el otro descansa sobre la panza
        r.leftArm.set(-0.30f + 0.04f * kick, 0.45f, 0);
        float[] face = carrierInRider(CARRIED, Layout.CARRY_FWD, Layout.CARRY_UP, Layout.CARRY_LEFT, 0, -4, -3);
        r.look(face[0], face[1], face[2], 0.7f);
        breathe(r, t, 1.3f);
    }

    // ================================================================= a caballito

    static final float PIG_LEG_PITCH = -1.15f, PIG_LEG_YAW = 0.75f, PIG_KNEE = 1.25f;

    static float[] riderPoint(float bx, float by, float bz) {
        return rider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, bx, by, bz);
    }

    /** Debajo del muslo del jinete, cerca de la rodilla, en su modelo. */
    static float[] thigh(boolean rightSide) {
        float sign = rightSide ? 1 : -1;
        float[] m = rot(PIG_LEG_PITCH, sign * PIG_LEG_YAW, 0);
        float[] v = mul(m, 0, 4.5f, 3.8f);
        return new float[]{(rightSide ? -1.9f : 1.9f) + v[0], 12 + v[1], v[2]};
    }

    static void piggyCarrier(Rig r, float t) {
        r.restUpper();
        relaxedLegs(r, t, 1.4f);
        r.torso(0.50f, 0, 0.03f * sin(t * 0.07f));
        float[] rt = thigh(true), lt = thigh(false);
        hand(r.rightArm, riderPoint(rt[0], rt[1], rt[2]), -0.8f, 0.2f, 0.6f, null, 1);
        hand(r.leftArm, riderPoint(lt[0], lt[1], lt[2]), 0.8f, 0.2f, 0.6f, null, 1);
        r.head.set(-0.20f, 0.10f * sin(t * 0.03f), 0);
        breathe(r, t, 1.5f);
    }

    static void piggyRider(Rig r, float t) {
        r.restAll();
        float swing = 0.06f * sin(t * 0.12f);
        r.rightLeg.set(PIG_LEG_PITCH + swing, PIG_LEG_YAW, 0);
        r.leftLeg.set(PIG_LEG_PITCH - swing, -PIG_LEG_YAW, 0);
        r.rightLeg.bend = PIG_KNEE + 2 * swing;
        r.leftLeg.bend = PIG_KNEE - 2 * swing;
        // brazos por encima de los hombros, las manos se cruzan en su pecho
        float[] rs = carrierInRider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, 2.0f, 0.8f, -5.6f);
        float[] ls = carrierInRider(PIGGY, Layout.BACK_FWD, Layout.BACK_UP, Layout.BACK_LEFT, -2.0f, 1.4f, -5.6f);
        hand(r.rightArm, rs, -0.6f, -0.6f, 0.2f, null, 1);
        hand(r.leftArm, ls, 0.6f, -0.6f, 0.2f, null, 1);
        r.head.set(0.10f, 0.25f + 0.20f * sin(t * 0.03f), -0.15f);
        breathe(r, t, 1);
    }

    // ================================================================= vals

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
        float e = envB(t, 0, 14, 1e9f, 1e9f);
        r.restAll();
        float beat = t / 10.0f;
        float bar = sin(beat * PI * 2.0f / 3.0f);
        r.torso(lead ? 0.0f : -0.06f * e, 0.0f, 0.03f * bar * e);
        if (lead) {
            // derecha en su omoplato izquierdo, izquierda sosteniendo su mano derecha en alto
            hand(r.rightArm, face(d, 0, 2.6f, 4.0f, 3.2f), -0.7f, 0.6f, 0.2f, face(d, 0, 4.6f, 6.4f, 0.4f), e);
            hand(r.leftArm, new float[]{9.5f, -1.0f, -d * PX / 2}, 0.2f, 1.0f, 0.3f, null, e);
        } else {
            // izquierda sobre su hombro derecho, derecha en su mano
            hand(r.leftArm, face(d, 0, -5.8f, -0.6f, 0.2f), 0.8f, 0.6f, 0.2f, null, e);
            hand(r.rightArm, new float[]{-9.5f, -1.0f, -d * PX / 2}, -0.2f, 1.0f, 0.3f, null, e);
        }
        // pasos en 3/4: un pie adelante y atras con flexion en el primer tiempo
        float down = 0.5f + 0.5f * cos(2 * PI * t / 30.0f);
        r.rightLeg.set(-0.30f * bar * e, 0, 0.05f * e);
        r.leftLeg.set(0.30f * bar * e, 0, -0.05f * e);
        r.rightLeg.bend = (0.12f + 0.25f * down * Math.max(0, bar)) * e;
        r.leftLeg.bend = (0.12f + 0.25f * down * Math.max(0, -bar)) * e;
        r.drop(0.6f * down * e);
        r.look(lead ? -3.0f : 0, -4.5f, -d * PX, 0.6f * e);
        r.head.roll += (lead ? 0.06f : -0.12f) * e;
        breathe(r, t, 1);
    }

    // ================================================================= baile lento abrazados

    static Root slowRoot(float t) {
        float in = ramp(t, 0, 30);
        float phi = (2 * PI / 300.0f) * Math.max(0, t - 15) * in;
        float hd = Layout.SLOW / 2;
        return new Root(0, phi, 0, hd * (1 - cos(phi)), 0, -hd * sin(phi));
    }

    static void slow(Rig r, float t, boolean lead) {
        float d = Layout.SLOW;
        float e = envB(t, 0, 18, 1e9f, 1e9f);
        float s = sin(t * 0.09f);
        r.restAll();
        // se mecen de un pie al otro, juntos (cada uno hacia su lado opuesto en el mundo)
        float roll = 0.05f * s * (lead ? 1 : -1) * e;
        r.torso(0.04f * e, 0, roll);
        r.rightLeg.set(0, 0, 0.04f);
        r.leftLeg.set(0, 0, -0.04f);
        r.rightLeg.bend = (0.08f + 0.14f * Math.max(0, s * (lead ? 1 : -1))) * e;
        r.leftLeg.bend = (0.08f + 0.14f * Math.max(0, -s * (lead ? 1 : -1))) * e;
        if (lead) {
            // manos en su cintura
            hand(r.rightArm, face(d, 0, 4.4f, 8.6f, 0.6f), -0.8f, 0.6f, 0.3f, null, e);
            hand(r.leftArm, face(d, 0, -4.4f, 8.6f, 0.6f), 0.8f, 0.6f, 0.3f, null, e);
            r.head.set(0.12f * e, -0.20f * e, -0.10f * e);
        } else {
            // brazos alrededor de su cuello
            hand(r.rightArm, face(d, 0, 2.2f, 0.4f, 3.0f), -0.7f, -0.6f, 0.2f, face(d, 0, 5.5f, -1.6f, 0.0f), e);
            hand(r.leftArm, face(d, 0, -2.2f, 0.4f, 3.0f), 0.7f, -0.6f, 0.2f, face(d, 0, -5.5f, -1.6f, 0.0f), e);
            r.head.set(-0.08f * e, 0.22f * e, 0.12f * e);
        }
        breathe(r, t, 1.3f);
    }

    // ================================================================= mirar las estrellas

    static void stars(Rig r, float t, boolean leftPerson) {
        r.restAll();
        float l = Layout.STARS_SIDE;
        float midX = -l * PX / 2;
        if (leftPerson) {
            hand(r.rightArm, new float[]{midX, 11.6f, 0.6f}, -0.4f, 0, 1, null, 1);
            // a veces senala una estrella, el resto del tiempo la mano en el pecho
            float point = envB(t % 300, 90, 112, 170, 190);
            float trace = 2.5f * sin(t * 0.05f);
            r.leftArm.set(-0.35f * (1 - point), 0.40f * (1 - point), 0);
            hand(r.leftArm, new float[]{4.0f + trace, -4.0f, -9.0f}, 1, 0.2f, 0.4f, null, point);
            // una rodilla levantada
            r.rightLeg.set(-0.30f, 0, 0.06f);
            r.leftLeg.set(0, 0, -0.04f);
        } else {
            hand(r.leftArm, new float[]{-midX, 11.6f, 0.6f}, 0.4f, 0, 1, null, 1);
            r.rightArm.set(-0.35f, -0.40f, 0);
            // tobillos cruzados
            r.rightLeg.set(0, 0.06f, 0.10f);
            r.leftLeg.set(0, -0.04f, -0.06f);
            r.leftLeg.bend = 0.12f;
        }
        float lookAt = env(t % 260, 60, 75, 120, 135);
        r.head.set(-0.15f, (leftPerson ? -0.55f : 0.55f) * lookAt, 0.05f * sin(t * 0.02f));
        breathe(r, t, 1.6f);
    }

    // ================================================================= sentarse juntos

    /** Sentado con las rodillas arriba (los pies en el suelo, delante). */
    static void sitKnees(Rig r, float spread, float t) {
                float s = 0.3f * sin(t * 0.03f);
        // piernas estiradas hacia delante, un poco abiertas, moviendo los pies
        r.rightLeg.set(-PI / 2 + 0.12f + 0.03f * s, 0.14f + spread * 0.2f, 0);
        r.leftLeg.set(-PI / 2 + 0.12f - 0.03f * s, -0.14f - spread * 0.2f, 0);
    }

    static void sitHug(Rig r, float t) {
        r.restAll();
        sitKnees(r, 0.4f, t);
        float l = Layout.SIT_SIDE;
        r.torso(0.10f, 0, -0.04f);
        // brazo por detras de la espalda del otro
        float[] back = right(l, 2.0f, 6.0f, 4.4f);
        hand(r.rightArm, back, -0.3f, -0.4f, 1.0f, right(l, 1.0f, 10.0f, 5.6f), 1);
        // la otra mano sobre la rodilla
        handL(r, new float[]{2.8f, 6.5f, -7.0f}, null, 1);
        r.head.set(0.12f, -0.30f, -0.12f);
        breathe(r, t, 1.4f);
    }

    static void sitLean(Rig r, float t) {
        r.restAll();
        sitKnees(r, 0.0f, t);
        // apoya la cabeza en el hombro del otro (a su izquierda)
        r.torso(0.12f, 0.0f, 0.06f);
        r.head.set(0.10f, 0.15f, 0.42f);
        // abrazada a sus rodillas
        handR(r, new float[]{-1.4f, 8.5f, -9.0f}, null, 1);
        handL(r, new float[]{1.4f, 8.5f, -9.0f}, null, 1);
        breathe(r, t, 1.4f);
    }

    // ================================================================= caido

    static final float FALL = 20.0f;

    /** Caida: se doblan las rodillas, cae de espaldas con un rebote, y queda tendido. */
    static Root downedRoot(float t, boolean fall) {
        Root lying = new Root(PI / 2, 0, 0.12f + 0.03f * sin(t * 0.02f), 0, -0.56f, 0);
        if (!fall || t >= FALL + 6) {
            return lying;
        }
        float buckle = ramp(t, 0, 5);
        float f = easeIn((t - 4) / 10.0f);
        float bounce = t > 14 ? 0.10f * sin(PI * clamp01((t - 14) / 6.0f)) : 0;
        float pitch = (PI / 2) * f - bounce;
        float up = lerp(-0.10f * buckle, -0.56f, f) + 0.03f * bounce;
        float fwd = -0.18f * sin(PI * f);
        Root r = new Root(pitch, 0, 0.12f * f, fwd, up, 0);
        return r.lerp(lying, ramp(t, FALL, FALL + 6));
    }

    static void downed(Rig r, float t, boolean fall) {
        r.restAll();
        // tendido: una rodilla arriba, un brazo que a veces pide ayuda
        float ask = envB(t % 160, 40, 60, 90, 115);
        hand(r.rightArm, new float[]{-6.0f, 4.0f - 5.0f * ask, -4.0f - 5.0f * ask}, -1, 0.3f, 0.5f, null, 0.4f + 0.6f * ask);
        r.leftArm.set(0.05f, 0, -0.30f);
        r.leftArm.bend = -0.35f;
        r.rightLeg.set(-0.25f, 0, 0.10f);
        r.leftLeg.set(0.05f, 0, -0.08f);
        r.head.set(-0.10f, 0.45f * sin(t * 0.015f), 0.05f);
        breathe(r, t, 2.5f);
        if (!fall || t >= FALL + 6) {
            return;
        }
        // durante la caida
        Rig fallPose = new Rig().restAll();
        float buckle = env(t, 0, 4, 8, 14);
        float fling = env(t, 4, 9, 13, 18);
        fallPose.torso(0.30f * buckle - 0.20f * fling, 0.10f * buckle, 0);
        fallPose.rightLeg.bend = 0.9f * buckle;
        fallPose.leftLeg.bend = 0.6f * buckle;
        fallPose.rightLeg.pitch = -0.35f * buckle;
        fallPose.leftLeg.pitch = -0.15f * buckle;
        fallPose.rightArm.set(-1.6f * fling, 0, 0.5f * buckle + 0.3f * fling);
        fallPose.leftArm.set(-1.3f * fling, 0, -0.5f * buckle - 0.4f * fling);
        fallPose.rightArm.bend = -0.6f * fling;
        fallPose.leftArm.bend = -0.4f * fling;
        fallPose.head.set(0.35f * buckle - 0.4f * fling, 0, 0);
        float w = 1 - ramp(t, FALL - 4, FALL + 6);
        Rig.P[] a = r.parts(), b = fallPose.parts();
        for (int i = 0; i < a.length; i++) {
            float keepBend = a[i].bend;
            float[] rigid = a[i].rigid;
            a[i].blend(b[i], w);
            a[i].rigid = w > 0.5f ? null : rigid;
            if (w > 0.5f) {
                a[i].bend = lerp(keepBend, b[i].bend, w);
            }
        }
    }

    // ================================================================= ayudar a levantarse

    /** Punto de agarre (mano con mano) en el modelo del que ayuda, segun el tiempo. */
    static float[] helpGrip(float t) {
        float sit = ramp(t, 16, 27);
        float stand = ramp(t, 27, 37);
        float db = lerp(lerp(0.5f, 6.0f, sit), 8.8f, stand);
        float h = lerp(lerp(5.0f, 13.0f, sit), 15.4f, stand);
        return new float[]{0, GROUND - h, -(Layout.HELP * PX - db)};
    }

    static void helper(Rig r, float t) {
        float e = envB(t, 0, 10, 42, 50);
        float k = lerp(lerp(1.0f, 0.35f, ramp(t, 16, 27)), 0.0f, ramp(t, 27, 37)) * e;
        r.restAll();
        r.torso(0.75f * k + 0.08f * e, 0, 0);
        kneel(r, k);
        float[] g = helpGrip(t);
        hand(r.rightArm, g, -0.4f, 0.9f, 0.4f, null, e);
        // la otra mano en la rodilla, luego atras para hacer fuerza
        handL(r, new float[]{2.4f, 14.0f + 3.0f * k, -6.0f}, null, k);
        r.look(g[0], g[1] - 6.0f, g[2] - 4.0f, 0.85f * e);
        breathe(r, t, 1.5f);
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
        // piernas: hacia delante mientras se sienta (rodillas que se doblan), luego de pie
        float legs = -(PI / 2 - root.pitch()) * (1 - stand);
        r.rightLeg.set(legs, 0, 0.05f);
        r.leftLeg.set(legs + 0.10f * sit * (1 - stand), 0, -0.05f);
        float knees = 1.1f * sit * (1 - stand);
        r.rightLeg.bend = knees;
        r.leftLeg.bend = knees * 0.7f;
        r.rightLeg.pitch -= 0.6f * knees;
        r.leftLeg.pitch -= 0.4f * knees;
        r.torso(0.20f * sit * (1 - stand) + 0.10f * stand * (1 - ramp(t, 37, 46)), 0, 0);
        // la mano derecha agarra la del que ayuda
        float[] g = helpGrip(t);
        float[] mine = face(Layout.HELP, 0, g[0], g[1], g[2]);
        float[] local = inRoot(root, mine);
        float grab = env(t, 4, 14, 36, 44);
        hand(r.rightArm, local, -0.6f, 0.4f, 0.6f, null, grab);
        // al final se sacude la ropa
        float dust = env(t, 38, 41, 47, 50) * sin(t * 1.6f);
        handL(r, new float[]{3.0f + dust, 9.0f, -3.4f}, null, env(t, 37, 40, 47, 50));
        r.look(0, -4, -Layout.HELP * PX, 0.5f * grab);
        r.head.pitch += 0.25f * env(t, 40, 43, 46, 50);
        breathe(r, t, 1.5f);
    }

    // ================================================================= propuesta de matrimonio

    /** Punto donde se ofrece la mano (anillo), en el modelo del que se arrodilla. */
    static float[] offer(float t) {
        float up = ramp(t, 18, 30);
        return new float[]{-1.0f, lerp(10.0f, 6.2f, up), -Layout.PROPOSE * PX * 0.5f + 0.5f};
    }

    static void propose(Rig r, float t) {
        float k = envB(t, 2, 18, 84, 100);
        r.restAll();
        r.torso(0.06f * k, 0, 0);
        kneel(r, k);
        // mano adelante ofreciendo el anillo; la otra en el corazon
        float[] o = offer(t);
        float nervous = 0.25f * sin(t * 1.3f) * env(t, 30, 34, 60, 66);
        hand(r.rightArm, new float[]{o[0], o[1] + nervous, o[2]}, -0.4f, 1.0f, 0.3f, null, env(t, 14, 26, 82, 94));
        float heart = env(t, 18, 26, 80, 90);
        r.leftArm.set(-0.75f * heart, 0.55f * heart, 0.05f);
        r.look(0, -6.0f, -Layout.PROPOSE * PX, 0.9f * k);
        breathe(r, t, 1.8f);
    }

    static void proposed(Rig r, float t) {
        r.restUpper();
        relaxedLegs(r, t, 1);
        float wow = envB(t, 10, 22, 52, 62);
        float yes = env(t, 50, 56, 72, 78);
        float give = env(t, 58, 66, 84, 94);
        float clap = env(t, 82, 86, 96, 100);
        // sorpresa: manos a la boca y un pasito atras
        r.torso(-0.08f * wow, 0, 0);
        handR(r, new float[]{-1.5f, 1.0f, -9.0f}, null, wow);
        handL(r, new float[]{1.5f, 1.0f, -9.0f}, null, wow * (1 - give));
        // asiente
        r.look(0, 6.0f, -Layout.PROPOSE * PX, 0.8f);
        r.head.pitch += 0.22f * yes * sin(t * 0.9f);
        // da la mano izquierda para el anillo
        float[] o = offer(t);
        float[] mine = face(Layout.PROPOSE, 0, o[0], o[1], o[2]);
        handL(r, mine, null, give);
        // aplaude de alegria
        float c = 1.2f * Math.abs(sin(t * 1.4f));
        handR(r, new float[]{-0.4f - c, 5.0f, -5.0f}, null, clap);
        handL(r, new float[]{0.4f + c, 5.0f, -5.0f}, null, clap);
        r.drop(-0.8f * clap * Math.abs(sin(t * 0.7f)));
        breathe(r, t, 1.5f);
    }

    // ================================================================= mimos (tocar con el raton)

    /** Contacto actual del modo mimos (lo da el cliente con las posiciones reales). */
    public record Contact(int zone, float[] point, float[] start, float w) {
    }

    public interface TouchLink {
        /** Para quien toca: punto donde va su mano (en SU modelo), o null. */
        Contact asToucher();

        /** Para quien recibe: punto de contacto (en SU modelo), o null. */
        Contact asReceiver();
    }

    public static TouchLink touchLink;

    static void toucher(Rig r, float t) {
        float d = Layout.TOUCH;
        r.restUpper();
        relaxedLegs(r, t, 1);
        breathe(r, t, 1.2f);
        sway(r, t, 0.6f);
        Contact c = touchLink == null ? null : touchLink.asToucher();
        float w = c == null ? 0 : c.w();
        // sin tocar: lo mira, manos sueltas
        r.look(0, -4.0f, -d * PX, 0.8f * (1 - w));
        if (c == null || w <= 0.001f) {
            return;
        }
        float[] p = c.point().clone();
        int z = c.zone();
        // pequeno vaiven propio para que la caricia no se vea quieta
        if (z == com.annis.interact.Touch.NARIZ) {
            // toquecito: la mano va y viene
            p[2] -= 1.2f * Math.max(0, sin(t * 0.7f));
        } else if (z == com.annis.interact.Touch.ESPALDA) {
            // la mano frota en circulos
            p[0] += 1.0f * sin(t * 0.35f);
            p[1] += 0.8f * cos(t * 0.35f);
        } else if (z == com.annis.interact.Touch.COSQUILLAS) {
            p[0] += 0.8f * sin(t * 2.2f);
            p[1] += 0.5f * sin(t * 1.7f);
        } else if (com.annis.interact.Touch.isHead(z)) {
            p[0] += 0.5f * sin(t * 0.45f);
            p[1] += 0.3f * sin(t * 0.9f);
        } else if (z == com.annis.interact.Touch.HOMBRO_D || z == com.annis.interact.Touch.HOMBRO_I) {
            p[1] -= 1.6f * Math.max(0, sin(t * 0.8f));
        }
        if (com.annis.interact.Touch.isBack(z)) {
            // la espalda se alcanza rodeandole el costado (como en un abrazo)
            float side = p[0] <= 1.0f ? -1 : 1;
            p[0] = side * Math.max(Math.abs(p[0]), 5.0f);
            p[2] = Math.max(p[2], -d * PX - 1.0f);
        }
        if (com.annis.interact.Touch.isHead(z)) {
            // la mano se apoya encima, no dentro: un poco hacia fuera desde el centro de su cabeza
            float nx = p[0], ny = p[1] + 4.0f, nz = p[2] + d * PX;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 0.01f) {
                p[0] += 1.7f * nx / nl;
                p[1] += 1.7f * ny / nl;
                p[2] += 1.7f * nz / nl;
            }
        }
        // se inclina hacia lo que toca, y gira un poco hacia ese lado
        float reach = Math.max(0, -p[2] - 4.0f) / 8.0f + Math.max(0, -p[1]) / 14.0f
                + (com.annis.interact.Touch.isBack(z) ? 0.6f : 0);
        r.torso((0.04f + 0.12f * Math.min(1, reach)) * w, -0.12f * clampS(p[0] / 6.0f) * w, 0);
        boolean useRight = p[0] <= 1.0f;
        Rig.P arm = useRight ? r.rightArm : r.leftArm;
        float side = useRight ? -1 : 1;
        hand(arm, p, 0.6f * side, 0.6f, 0.4f, null, w);
        r.look(p[0], p[1], p[2], 0.9f * w);
    }

    static float clampS(float v) {
        return Math.max(-1, Math.min(1, v));
    }

    static void touched(Rig r, float t) {
        float d = Layout.TOUCH;
        r.restUpper();
        relaxedLegs(r, t, 1);
        breathe(r, t, 1.2f);
        sway(r, t, 0.6f);
        Contact c = touchLink == null ? null : touchLink.asReceiver();
        r.look(0, -4.0f, -d * PX, 0.7f);
        if (c == null || c.w() <= 0.001f) {
            return;
        }
        float w = c.w();
        float[] p = c.point();
        int z = c.zone();
        switch (z) {
            case com.annis.interact.Touch.PELO -> {
                // se acurruca contra la mano
                r.head.pitch += 0.14f * w;
                r.head.roll += 0.10f * clampS(p[0] / 4.0f) * w;
                r.torso(0.04f * w, 0, 0);
                r.drop(0.3f * w);
            }
            case com.annis.interact.Touch.MEJILLA -> {
                // apoya la cara en la mano; arrastrando se le guia la cabeza
                float[] s = c.start();
                float gx = s == null ? 0 : p[0] - s[0], gy = s == null ? 0 : p[1] - s[1];
                r.head.roll += 0.20f * clampS(p[0] / 4.0f) * w;
                r.head.yaw += Math.max(-0.6f, Math.min(0.6f, gx * 0.09f)) * w;
                r.head.pitch += Math.max(-0.4f, Math.min(0.4f, gy * 0.09f)) * w;
            }
            case com.annis.interact.Touch.NARIZ -> {
                // boop: echa la cabeza atras y sube los hombros
                float boop = Math.max(0, sin(t * 0.7f));
                r.head.pitch -= (0.10f + 0.10f * boop) * w;
                r.rightArm.y -= 0.6f * w;
                r.leftArm.y -= 0.6f * w;
            }
            case com.annis.interact.Touch.FRENTE -> {
                r.head.pitch -= 0.08f * w;
                r.head.roll += 0.06f * sin(t * 0.08f) * w;
            }
            case com.annis.interact.Touch.BARBILLA -> {
                // le levantan la barbilla: mira arriba; arrastrando se le guia la cabeza
                float[] s = c.start();
                float gx = s == null ? 0 : p[0] - s[0];
                r.head.pitch -= 0.30f * w;
                r.head.yaw += Math.max(-0.5f, Math.min(0.5f, gx * 0.12f)) * w;
            }
            case com.annis.interact.Touch.OREJA_D, com.annis.interact.Touch.OREJA_I -> {
                // se inclina hacia la mano y sube ese hombro (da cosquillas)
                boolean right = z == com.annis.interact.Touch.OREJA_D;
                r.head.roll += (right ? -0.24f : 0.24f) * w;
                Rig.P arm = right ? r.rightArm : r.leftArm;
                arm.y -= 0.9f * w;
            }
            case com.annis.interact.Touch.ESPALDA -> {
                // se relaja: la espalda se arquea un poco hacia la mano y los hombros bajan
                r.torso(-0.05f * w, 0, 0);
                r.head.pitch -= 0.06f * w;
                r.rightArm.y += 0.4f * w;
                r.leftArm.y += 0.4f * w;
            }
            case com.annis.interact.Touch.COSQUILLAS -> {
                // se retuerce de risa
                float wig = sin(t * 1.6f);
                r.torso(0.06f * w, 0.10f * sin(t * 1.1f) * w, 0.10f * wig * w);
                r.drop((0.6f + 0.5f * Math.abs(wig)) * w);
                r.rightArm.roll -= 0.25f * w;
                r.leftArm.roll += 0.25f * w;
                r.rightArm.pitch -= 0.4f * w;
                r.leftArm.pitch -= 0.4f * w;
                r.head.yaw += 0.18f * sin(t * 1.3f) * w;
            }
            case com.annis.interact.Touch.HOMBRO_D, com.annis.interact.Touch.HOMBRO_I -> {
                boolean right = z == com.annis.interact.Touch.HOMBRO_D;
                float hit = Math.max(0, -sin(t * 0.8f));
                Rig.P arm = right ? r.rightArm : r.leftArm;
                arm.y += 0.5f * hit * w;
                r.head.roll += (right ? -0.10f : 0.10f) * w;
            }
            default -> {
                if (com.annis.interact.Touch.isHand(z)) {
                    // el brazo va adonde lo lleva la mano del otro
                    boolean right = com.annis.interact.Touch.rightSide(z);
                    Rig.P arm = right ? r.rightArm : r.leftArm;
                    hand(arm, p, right ? -0.5f : 0.5f, 0.7f, 0.4f, null, w);
                    r.look(p[0], p[1], p[2], 0.5f * w);
                }
            }
        }
    }

    // ================================================================= apoyarse en el hombro

    /** El de la izquierda le rodea la cintura al otro (que esta a su derecha) y se inclina hacia el. */
    static void shoulderHold(Rig r, float t) {
        float l = Layout.LEAN_SIDE;
        float e = envB(t, 0, 16, 1.0e9f, 1.0e9f);
        r.restUpper();
        relaxedLegs(r, t, 1);
        r.torso(0.03f * e, 0, -SL_LEAN_A * e);
        // brazo derecho por detras, la mano en su cintura
        float[] waist = right(l, SL_HX, SL_HY, SL_HZ);
        hand(r.rightArm, waist, -0.3f, 0.4f, 1.0f, null, e);
        r.leftArm.set(0.04f * sin(t * 0.05f), 0, 0.06f);
        // cabeza hacia la suya
        r.head.set(0.06f * e, -0.20f * e, -(SL_LEAN_A + 0.16f) * e);
        breathe(r, t, 1.2f);
    }

    static final float SL_LEAN_A = 0.10f, SL_LEAN_B = 0.14f, SL_SLIDE = 0.8f;
    static final float SL_HX = 1.5f, SL_HY = 6.0f, SL_HZ = 6.0f;

    /** El de la derecha apoya la cabeza hacia el hombro del otro; su brazo cae por delante. */
    static void shoulderLean(Rig r, float t) {
        float e = envB(t, 4, 22, 1.0e9f, 1.0e9f);
        r.restUpper();
        relaxedLegs(r, t, 1);
        r.torso(0.04f * e, 0.04f * e, SL_LEAN_B * e);
        float slide = SL_SLIDE * e;
        r.head.x += slide;
        r.body.x += slide;
        r.rightArm.x += slide;
        r.leftArm.x += slide;
        r.head.set(0.10f * e, 0.16f * e, (SL_LEAN_B + 0.28f) * e + 0.02f * sin(t * 0.04f));
        // el brazo del lado del otro, un poco adelante (el suyo pasa por detras)
        r.leftArm.set(-0.22f * e, 0, (-0.04f - SL_LEAN_B) * e);
        r.rightArm.set(-0.05f * e, 0.10f * e, 0.04f);
        breathe(r, t, 1.3f);
    }

    // ================================================================= besar la mano

    /** Donde se encuentran la mano del que recibe y la boca del que besa (en el modelo del que besa). */
    static float[] kissPoint(float t) {
        float lift = ramp(t, 14, 26);
        return new float[]{1.2f, lerp(6.0f, 1.0f, lift), -Layout.HAND_KISS * PX + 5.6f};
    }

    static void handKiss(Rig r, float t) {
        float e = envB(t, 2, 12, 56, 68);
        float bow = env(t, 22, 32, 42, 52);
        r.restUpper();
        relaxedLegs(r, t, 1);
        r.torso(0.30f * bow + 0.05f * e, -0.05f * e, 0);
        // toma su mano desde abajo y la levanta un poco
        float[] k = kissPoint(t);
        hand(r.rightArm, new float[]{k[0] - 0.4f, k[1] + 1.4f, k[2] + 0.4f}, -0.3f, 1.0f, 0.3f, null, env(t, 6, 14, 54, 64));
        // la otra mano a la espalda, como un caballero
        handL(r, new float[]{1.4f, 9.0f, 3.6f}, null, e);
        r.look(k[0], k[1], k[2], 0.9f * e);
        r.head.pitch += 0.35f * bow;
        breathe(r, t, 1);
    }

    static void handKissed(Rig r, float t) {
        float e = envB(t, 2, 14, 56, 68);
        float shy = env(t, 34, 40, 56, 64);
        r.restUpper();
        relaxedLegs(r, t, 1);
        // ofrece la mano derecha
        float[] k = kissPoint(t);
        float[] mine = face(Layout.HAND_KISS, 0, k[0], k[1], k[2]);
        hand(r.rightArm, mine, -0.4f, 0.8f, 0.4f, null, env(t, 4, 14, 54, 64));
        // timidez: la otra mano al pecho y la cabeza ladeada
        handL(r, new float[]{1.0f, 4.0f, -4.4f}, null, shy);
        r.look(0, -2.0f, -Layout.HAND_KISS * PX, 0.8f * e);
        r.head.roll += 0.16f * shy;
        r.head.pitch += 0.08f * shy;
        r.drop(-0.6f * shy * Math.abs(sin(t * 0.5f)));
        breathe(r, t, 1.2f);
    }

    // ================================================================= swing (con vuelta)

    static final float SWING_CYCLE = 64.0f;

    /** Cuanto lleva girado quien sigue (0..1 por vuelta). */
    static float swingSpin(float t) {
        if (t < 24) {
            return 0;
        }
        float k = (t - 24) % SWING_CYCLE;
        return smooth(clamp01((k - 40) / 16.0f));
    }

    static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    static Root swingRoot(float t, boolean lead) {
        float bounce = 0.035f * Math.abs(sin(t * PI / 10.0f));
        float spin = lead ? 0 : swingSpin(t) * 2 * PI;
        return new Root(0, spin, 0, 0, bounce, 0);
    }

    static void swing(Rig r, float t, boolean lead) {
        float d = Layout.SWING;
        float e = envB(t, 0, 14, 1e9f, 1e9f);
        r.restAll();
        float beat = t * PI / 10.0f;
        // paso de rock: el cuerpo va y viene, una pierna patea en cada tiempo fuerte
        float rock = sin(beat * 0.5f);
        r.torso((0.05f + 0.06f * rock) * e, 0.10f * sin(beat * 0.5f) * e, 0);
        float kick = Math.max(0, sin(beat * 0.5f));
        float kick2 = Math.max(0, -sin(beat * 0.5f));
        r.rightLeg.set((-0.45f * kick + 0.10f * kick2) * e, 0, 0.05f);
        r.leftLeg.set((-0.45f * kick2 + 0.10f * kick) * e, 0, -0.05f);
        r.drop(0.6f * Math.abs(sin(beat)) * e);
        // manos unidas: la izquierda del que guia con la derecha del que sigue
        float spin = swingSpin(t);
        float raise = env(spin, 0, 0.15f, 0.85f, 1.0f) + (spin > 0 && spin < 1 ? 1 : 0) * 0;
        float lift = t < 24 ? 0 : env((t - 24) % SWING_CYCLE, 34, 40, 56, 60);
        float[] joined = {3.0f, lerp(9.0f, -9.5f, lift), -d * PX / 2};
        if (lead) {
            hand(r.leftArm, joined, 0.5f, 0.6f, 0.3f, null, e);
            // la otra mano suelta, marcando el ritmo
            hand(r.rightArm, new float[]{-6.5f, 9.0f - 1.0f * Math.abs(sin(beat)), -2.5f}, -0.5f, 0.6f, 0.3f, null, e);
            r.look(0, -4, -d * PX, 0.7f * e);
        } else {
            Root root = swingRoot(t, false);
            float[] mine = inRoot(root, face(d, 0, joined[0], joined[1], joined[2]));
            hand(r.rightArm, mine, -0.5f, 0.6f, 0.3f, null, e);
            // brazo libre abierto con estilo
            r.leftArm.set(-0.4f * e, 0, (-0.9f - 0.2f * lift) * e);
            r.head.roll -= 0.08f * e;
        }
        breathe(r, t, 1);
        raise *= 0;
    }

    // ================================================================= bachata

    /** 8 tiempos de 10 ticks: 1-2-3-toque a un lado, 5-6-7-toque al otro. */
    static float bachataSide(float t) {
        float k = (t % 80.0f) / 10.0f;
        float step = 0.11f;
        if (k < 3) {
            return step * smooth(k / 3.0f) * 2 - step;
        }
        if (k < 4) {
            return step;
        }
        if (k < 7) {
            return step - step * smooth((k - 4) / 3.0f) * 2;
        }
        return -step;
    }

    static Root bachataRoot(float t, boolean lead) {
        float in = ramp(t, 0, 20);
        float side = bachataSide(t) * in;
        return new Root(0, 0, 0, 0, 0, lead ? side : -side);
    }

    static void bachata(Rig r, float t, boolean lead) {
        float d = Layout.BACHATA;
        float e = envB(t, 0, 16, 1e9f, 1e9f);
        r.restAll();
        float k = (t % 80.0f) / 10.0f;
        // el "toque" (tiempos 4 y 8): la cadera salta y la pierna se apoya de puntillas
        float tap = env(k, 3, 3.4f, 3.7f, 4) + env(k, 7, 7.4f, 7.7f, 8);
        boolean tapRight = k >= 3 && k < 4;
        float hip = 0.07f * sin(t * PI / 10.0f) * e;
        r.torso(0.04f * e, 0, hip);
        float slide = 0.8f * sin(t * PI / 10.0f) * e;
        r.body.x += slide;
        r.head.x += slide;
        r.rightArm.x += slide;
        r.leftArm.x += slide;
        r.rightLeg.set(0, 0, 0.05f + (tapRight ? 0.12f * tap : 0));
        r.leftLeg.set(0, 0, -0.05f - (tapRight ? 0 : 0.12f * tap));
        r.drop(0.4f * Math.abs(sin(t * PI / 10.0f)) * e);
        if (lead) {
            // derecha en su espalda, izquierda con su mano derecha a un lado
            hand(r.rightArm, face(d, 0, 2.6f, 5.0f, 3.0f), -0.7f, 0.6f, 0.2f, face(d, 0, 4.6f, 6.0f, 0.6f), e);
            hand(r.leftArm, new float[]{8.5f, 3.0f, -d * PX / 2}, 0.2f, 1.0f, 0.3f, null, e);
            r.look(-3.0f, -4.5f, -d * PX, 0.6f * e);
        } else {
            hand(r.leftArm, face(d, 0, -5.8f, -0.4f, 0.2f), 0.8f, 0.6f, 0.2f, null, e);
            hand(r.rightArm, new float[]{-8.5f, 3.0f, -d * PX / 2}, -0.2f, 1.0f, 0.3f, null, e);
            r.look(0, -4.5f, -d * PX, 0.5f * e);
            r.head.roll -= 0.10f * e;
        }
        breathe(r, t, 1);
    }

    // ================================================================= tango

    static final float TANGO_CYCLE = 120.0f;

    /** Avance del que guia (bloques): lento-lento-rapido-rapido-lento, y vuelve. */
    static float tangoWalk(float t) {
        float k = t % TANGO_CYCLE;
        if (k < 40) {
            return 0.18f * smooth(k / 40.0f);
        }
        if (k < 60) {
            return 0.18f + 0.10f * smooth((k - 40) / 20.0f);
        }
        if (k < 84) {
            return 0.28f;
        }
        return 0.28f * (1 - smooth((k - 84) / 36.0f));
    }

    /** La caida hacia atras de quien sigue (0..1). */
    static float tangoDip(float t) {
        return env(t % TANGO_CYCLE, 64, 74, 92, 104);
    }

    static Root tangoRoot(float t, boolean lead) {
        float in = ramp(t, 0, 20);
        float f = tangoWalk(t) * in;
        float dip = tangoDip(t) * in;
        if (lead) {
            return new Root(0, 0, 0, f, -0.06f * dip, 0);
        }
        return new Root(0.45f * dip, 0, 0, -f, -0.03f * dip, 0);
    }

    static void tango(Rig r, float t, boolean lead) {
        float d = Layout.TANGO;
        float e = envB(t, 0, 16, 1e9f, 1e9f);
        r.restAll();
        float k = t % TANGO_CYCLE;
        float dip = tangoDip(t) * e;
        // pasos: piernas largas, como caminando
        float stepPhase = k < 40 ? k / 20.0f : k < 60 ? 2 + (k - 40) / 10.0f : 4;
        float walking = k < 60 ? 1 : 0;
        float sw = sin(stepPhase * PI) * walking;
        float dir = lead ? 1 : -1;
        r.rightLeg.set(-0.35f * sw * dir * e, 0, 0.04f);
        r.leftLeg.set(0.35f * sw * dir * e, 0, -0.04f);
        // cabeza que "corta" de golpe a un lado en cada acento
        float snap = (k < 40 || (k >= 84)) ? 1 : -1;
        if (lead) {
            r.torso((0.06f + 0.30f * dip) * e, 0, 0);
            // en la caida: se agacha con una pierna adelante
            r.rightLeg.pitch += -0.55f * dip;
            r.leftLeg.pitch += 0.30f * dip;
            r.drop(1.2f * dip);
            float back = 3.5f * dip;
            hand(r.rightArm, face(d, 0, 2.6f, 5.0f + 0.8f * dip, 3.0f + back), -0.7f, 0.6f, 0.2f,
                    face(d, 0, 4.6f, 6.0f, 0.6f + back), e);
            hand(r.leftArm, new float[]{8.0f, 0.5f + 2.0f * dip, -d * PX / 2 - 1.0f}, 0.2f, 1.0f, 0.3f, null, e);
            r.look(0, -4.0f + 3.0f * dip, -d * PX, 0.5f * e);
            r.head.yaw += 0.45f * snap * (1 - dip) * e;
        } else {
            r.torso(-0.04f * e, 0, 0);
            // en la caida: una pierna se estira hacia el
            r.leftLeg.pitch += -0.70f * dip;
            hand(r.leftArm, face(d, 0, -5.8f, -0.4f, 0.2f), 0.8f, 0.6f, 0.2f, null, e * (1 - 0.3f * dip));
            hand(r.rightArm, new float[]{-8.0f, 0.5f, -d * PX / 2 - 1.0f}, -0.2f, 1.0f, 0.3f, null, e);
            r.head.yaw -= 0.45f * snap * (1 - dip) * e;
            r.head.pitch -= 0.25f * dip;
        }
        breathe(r, t, 1);
    }
}
