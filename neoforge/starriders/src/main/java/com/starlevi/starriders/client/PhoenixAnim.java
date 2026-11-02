package com.starlevi.starriders.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Animaciones del Fenix, todas mezcladas con pesos suaves (sin cortes): reposo (respira, mira,
 * se acomoda), caminar, despegue, aleteo, planeo, picada, aterrizaje y grito.
 */
public final class PhoenixAnim {
    /** Lo que necesita un cuadro. */
    public static final class Params {
        public float age;
        public float walk, walkAmount;
        public float fly;        // 0 en el piso .. 1 volando
        public float glide;      // 0 aleteando .. 1 planeando
        public float dive;       // 0 .. 1 en picada
        public float flapPhase;  // radianes
        public float flapPower = 1;  // fuerza del aleteo (despegue/subida mas fuerte)
        public float headYaw, headPitch;
        public float roar = -1;  // 0..1 gritando
        public float tailSpeed;  // 0..1 (la cola flamea con la velocidad)
    }

    private PhoenixAnim() {
    }

    private static void rot(ModelPart p, float x, float y, float z) {
        if (p != null) {
            p.xRot += x;
            p.yRot += y;
            p.zRot += z;
        }
    }

    public static void apply(PhoenixModel m, Params a) {
        m.root.getAllParts().forEach(ModelPart::resetPose);
        float t = a.age;
        float fly = a.fly, ground = 1 - fly;
        float glide = a.glide * fly, flap = (1 - a.glide) * fly, dive = a.dive * fly;
        ModelPart body = m.part("cuerpo"), n1 = m.part("cuello_1"), n2 = m.part("cuello_2"), head = m.part("cabeza");
        // ---- cuerpo
        float breath = Mth.sin(t * 0.07f);
        body.y += breath * 0.25f * ground - Math.abs(Mth.sin(a.walk * 0.7f)) * 0.6f * a.walkAmount * ground;
        body.xRot += -0.12f * ground + Mth.sin(a.flapPhase) * 0.05f * flap;
        body.y += Mth.sin(a.flapPhase + 1.2f) * 0.9f * flap * a.flapPower;
        // ---- cuello y cabeza: en el piso en S y mirando; volando estirado hacia adelante
        float look = Mth.clamp(a.headYaw, -70, 70) * Mth.DEG_TO_RAD, pitch = Mth.clamp(a.headPitch, -40, 40) * Mth.DEG_TO_RAD;
        float preen = Math.max(0, Mth.sin(t * 0.013f) - 0.93f) / 0.07f * ground; // de vez en cuando se acomoda las plumas
        rot(n1, (-0.15f + breath * 0.04f + Mth.sin(a.walk * 0.7f) * 0.12f * a.walkAmount) * ground + (-1.25f) * fly + preen * 0.6f,
                look * 0.3f * ground, 0);
        rot(n2, 0.25f * ground + 0.15f * fly + preen * 0.9f, look * 0.35f, preen * 0.6f);
        rot(head, (0.05f + pitch * 0.6f) * ground + (1.05f + pitch * 0.3f) * fly + preen * 0.5f, look * 0.35f, preen * -0.8f);
        // ---- grito: cabeza arriba, alas abiertas
        float roar = a.roar >= 0 ? Mth.sin(a.roar * Mth.PI) : 0;
        rot(n1, -0.55f * roar, 0, 0);
        rot(head, -0.7f * roar, 0, Mth.sin(t * 1.3f) * 0.05f * roar);
        // ---- alas
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "der" : "izq";
            float d = side < 0 ? 1 : -1; // + sube el ala derecha (zRot), - la izquierda
            ModelPart arm = m.part("brazo_" + s), fore = m.part("antebrazo_" + s), hand = m.part("mano_" + s);
            // plegadas (en el piso): brazo hacia atras, antebrazo hacia adelante, mano hacia atras
            float fold = ground * (1 - roar);
            rot(arm, 0.1f * fold, d * 1.3f * fold, d * -0.35f * fold);
            rot(fore, 0, d * -2.55f * fold, d * 0.15f * fold);
            rot(hand, 0, d * 2.4f * fold, 0);
            // grito: abiertas y en alto
            rot(arm, 0, d * -0.2f * roar * ground, d * (0.7f + Mth.sin(t * 0.5f) * 0.15f) * roar * ground);
            rot(fore, 0, 0, d * 0.25f * roar * ground);
            // aleteo: el brazo baja fuerte y la punta acompaña con retraso (onda)
            float ph = a.flapPhase, pw = a.flapPower;
            rot(arm, Mth.sin(ph + 0.4f) * 0.12f * flap, d * Mth.cos(ph) * 0.18f * flap, d * Mth.sin(ph) * 0.95f * pw * flap);
            rot(fore, 0, d * -Mth.cos(ph) * 0.12f * flap, d * Mth.sin(ph - 0.7f) * 0.45f * pw * flap);
            rot(hand, Mth.sin(ph - 1.4f) * 0.15f * flap, 0, d * Mth.sin(ph - 1.3f) * 0.55f * pw * flap);
            // planeo: abiertas, con un poquito de diedro y ondulando
            float und = Mth.sin(t * 0.09f + side) * 0.05f;
            rot(arm, 0, 0, d * (0.12f + und) * glide);
            rot(hand, 0, 0, d * (0.1f - und) * glide);
            // picada: alas hacia atras
            rot(arm, 0, d * -0.95f * dive, d * 0.25f * dive);
            rot(fore, 0, d * 0.55f * dive, 0);
            rot(hand, 0, d * -0.5f * dive, 0);
        }
        // ---- patas: caminan en el piso, recogidas volando
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "der" : "izq";
            float ph = a.walk * 0.7f + (side < 0 ? 0 : Mth.PI);
            rot(m.part("pierna_" + s), Mth.cos(ph) * 0.6f * a.walkAmount * ground + 1.25f * fly, 0, 0);
            rot(m.part("canilla_" + s), Math.max(0, -Mth.sin(ph)) * 0.6f * a.walkAmount * ground + 0.9f * fly, 0, 0);
            rot(m.part("pie_" + s), -0.3f * fly, 0, 0);
        }
        // ---- cola: flamea; mas rapido y estirada con la velocidad
        float sp = Mth.clamp(a.tailSpeed, 0, 1);
        rot(m.part("cola"), (-0.2f * ground - 0.12f * fly) + Mth.sin(a.flapPhase - 1.6f) * 0.08f * flap, look * -0.2f * ground, 0);
        for (int i = 0; i < 7; i++) {
            float wv = t * (0.06f + sp * 0.25f) + i * 0.9f;
            float amp = 0.06f + sp * 0.08f;
            rot(m.part("pluma_" + i), Mth.sin(wv) * amp - 0.15f * ground - 0.05f * fly, Mth.cos(wv * 0.7f) * amp * 0.6f, 0);
            rot(m.part("pluma_" + i + "_b"), Mth.sin(wv - 1.2f) * amp * 1.6f + 0.25f * ground - 0.04f * fly, Mth.cos(wv * 0.7f - 1) * amp, 0);
        }
        rot(m.part("cresta"), Mth.sin(t * 0.11f) * 0.06f + 0.25f * fly, 0, 0);
    }
}
