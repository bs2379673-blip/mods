package com.tunombre.jefes.core;

/**
 * Cerebro de animacion: mezcla las animaciones PROPIAS del modelo (estar, caminar, correr, agachado, columna...) segun lo que
 * hace el jefe y les suma encima las acciones que el modelo no trae (golpes, rugido, salto, agarre) calculadas a mano
 * sobre sus huesos. Todo con la misma formula en cliente y servidor (el servidor solo la usa para saber donde esta la mano).
 */
public final class Rig {
    private Rig() { }

    /** estado de suavizado de UNA entidad (solo cliente) */
    public static final class State {
        float wIdle = 1, wWalk, wRun, wSneak, wSneakWalk, air, sw, lastT, lastStruggle;
        Act lastAct = Act.HOLD;
        double phIdle, phWalk, phRun, phSneak, phSneakWalk, last = -1;
    }

    /** lo que se sabe de la entidad en este fotograma */
    public static final class In {
        public double timeMs;
        public float moveSpeed;        // bloques/tick
        public boolean sprint, crouch, onGround = true;
        public float headYaw;          // grados, cabeza respecto al cuerpo
        public Act act = Act.NONE;
        public float actT;             // ticks desde que empezo la accion (con parcial)
        public float struggle;         // 0..1 forcejeo de la victima (agarre)
        public float turn;             // giro del cuerpo, grados/tick (inclinacion)
    }

    static float ss(float x) { x = Math.max(0F, Math.min(1F, x)); return x * x * (3F - 2F * x); }

    static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    /** 0 antes de a, 1 despues de b, suave entre medias */
    static float rng(float t, float a, float b) { return ss((t - a) / (b - a)); }

    /** postura de agarre completa (la que usa el servidor para la mano): animacion de reposo del modelo + la postura encima */
    public static void handPose(Model m, Spec s, Pose p, Act act, float t, float struggle) {
        p.reset();
        double ph = 3000.0 + t * 50.0;
        clip(m, p, s.anim("idle", "STANDING"), ph, 1F);
        for (Model.Clip c : m.clipsNamed(s.anim("always", "GLOBAL"))) p.addClip(c, ph, 1F, null);
        stance(m, s, p, act, t, struggle, ph);
    }

    public static void pose(Model m, Spec s, Pose p, State st, In in) {
        float dt = st.last < 0 ? 0 : (float) Math.max(0, Math.min(100, in.timeMs - st.last));
        boolean on = in.act.stance();
        st.sw += ((on ? 1F : 0F) - st.sw) * (1F - (float) Math.exp(-dt / 120.0));
        normal(m, s, p, st, in, dt);
        if (on) { st.lastAct = in.act; st.lastT = in.actT; st.lastStruggle = in.struggle; }
        else if (st.sw > 0.004F) st.lastT += dt / 50F;
        if (on || st.sw > 0.004F) {
            float[] ra = p.rot.clone(), pa = p.pos.clone();
            handPose(m, s, p, st.lastAct, st.lastT, st.lastStruggle);
            float w = st.sw;
            for (int i = 0; i < ra.length; i++) { p.rot[i] = ra[i] + (p.rot[i] - ra[i]) * w; p.pos[i] = pa[i] + (p.pos[i] - pa[i]) * w; }
        }
    }

    private static void normal(Model m, Spec s, Pose p, State st, In in, float dt) {
        p.reset();
        st.last = in.timeMs;
        boolean moving = in.moveSpeed > 0.012F;
        float tI = !moving && !in.crouch ? 1 : 0, tS = !moving && in.crouch ? 1 : 0;
        float tW = moving && !in.crouch && !in.sprint ? 1 : 0, tR = moving && !in.crouch && in.sprint ? 1 : 0, tSW = moving && in.crouch ? 1 : 0;
        float k = 1F - (float) Math.exp(-dt / 140.0);
        float tAir = in.onGround ? 0 : 1;
        st.air += (tAir - st.air) * (1F - (float) Math.exp(-dt / 90.0));
        float ground = 1F - st.air;
        st.wIdle += (tI - st.wIdle) * k; st.wSneak += (tS - st.wSneak) * k;
        st.wWalk += (tW - st.wWalk) * k; st.wRun += (tR - st.wRun) * k; st.wSneakWalk += (tSW - st.wSneakWalk) * k;
        st.phIdle += dt; st.phSneak += dt;
        st.phWalk += dt * rate(in.moveSpeed, s.walk); st.phRun += dt * rate(in.moveSpeed, s.sprint); st.phSneakWalk += dt * rate(in.moveSpeed, s.sneak);
        clip(m, p, s.anim("idle", "STANDING"), st.phIdle, st.wIdle * ground);
        clip(m, p, s.anim("sneakIdle", "SNEAKING"), st.phSneak, st.wSneak * ground);
        clip(m, p, s.anim("walk", "WALKING"), st.phWalk, st.wWalk * ground);
        clip(m, p, s.anim("run", "RUNNING"), st.phRun, st.wRun * ground);
        clip(m, p, s.anim("sneakWalk", "SNEAK_WALK"), st.phSneakWalk, st.wSneakWalk * ground);
        if (st.air > 0.01F) clip(m, p, s.anim("idle", "STANDING"), st.phIdle, st.air * 0.6F);
        for (Model.Clip c : m.clipsNamed(s.anim("always", "GLOBAL"))) p.addClip(c, in.timeMs, 1F, null);
        Model.Clip hy = m.clip(s.anim("headYaw", "HEAD_ROTATION_YAW"));
        if (hy != null) p.addClipStep(hy, Math.max(0F, Math.min(1F, (in.headYaw + 90F) / 180F)) * hy.frames, 1F, null);
        if (st.air > 0.01F) airborne(s, p, st.air);
        if (in.act == Act.SNIFF) {
            Model.Clip sn = m.clip(s.anim("sniff", "Up looking"));
            if (sn != null) {
                float w = Math.min(rng(in.actT, 0, 10), 1F - rng(in.actT, Act.SNIFF.ticks - 10, Act.SNIFF.ticks));
                p.addClipStep(sn, Math.min(in.actT / 20F * 1000F / sn.dur * sn.frames, sn.frames - 1F), w, null);
            }
        }
        actions(m, s, p, in);
        // inclinacion al girar
        float lean = Math.max(-1F, Math.min(1F, in.turn / 6F));
        if (Math.abs(lean) > 0.01F) p.addRot(s.bone("chest"), 0, 0, -0.10F * lean);
    }

    private static float rate(float speed, float ref) { return Math.max(0.35F, Math.min(2.2F, ref <= 0 ? 1F : speed / ref)); }

    private static void clip(Model m, Pose p, String name, double ph, float w) {
        if (w <= 0.001F) return;
        for (Model.Clip c : m.clipsNamed(name)) p.addClip(c, ph, w, null);
    }

    private static void rot(Pose p, int id, float x, float y, float z) { p.addRot(id, x, y, z); }

    /** salto: brazos arriba y atras, piernas recogidas */
    private static void airborne(Spec s, Pose p, float w) {
        for (int a : new int[]{s.bone("armFL"), s.bone("armFR")}) rot(p, a, -1.2F * w, 0, 0);
        for (int a : new int[]{s.bone("armBL"), s.bone("armBR")}) rot(p, a, -0.7F * w, 0, 0);
        rot(p, s.bone("hipL"), -0.5F * w, 0, 0); rot(p, s.bone("hipR"), -0.5F * w, 0, 0);
        rot(p, s.bone("kneeL"), 0.8F * w, 0, 0); rot(p, s.bone("kneeR"), 0.8F * w, 0, 0);
    }

    private static void actions(Model m, Spec s, Pose p, In in) {
        float t = in.actT;
        int chest = s.bone("chest"), head = s.bone("head"), body = s.bone("body");
        int fl = s.bone("armFL"), fr = s.bone("armFR"), bl = s.bone("armBL"), br = s.bone("armBR");
        int[] jaws = s.boneList("jaws");
        switch (in.act) {
            case ATTACK_L, ATTACK_R -> {
                float sg = in.act == Act.ATTACK_L ? 1F : -1F;
                int front = in.act == Act.ATTACK_L ? fl : fr, back = in.act == Act.ATTACK_L ? bl : br;
                float wind = rng(t, 0, 7), strike = rng(t, 7, 10.5F), rec = rng(t, 11, 18);
                float x = lerp(lerp(0, -2.45F, wind), -0.45F, strike);
                x = lerp(x, 0, rec);
                float z = lerp(lerp(0, -0.55F * sg, wind), 0.75F * sg, strike);
                z = lerp(z, 0, rec);
                rot(p, front, x, 0, z);
                rot(p, back, x * 0.55F, 0, z * 0.6F);
                float tw = lerp(lerp(0, -0.45F * sg, wind), 0.55F * sg, strike);
                tw = lerp(tw, 0, rec);
                rot(p, chest, lerp(0, 0.18F, wind) - 0.3F * strike * (1 - rec), tw, 0);
                rot(p, head, 0.1F * strike * (1 - rec), -tw * 0.5F, 0);
            }
            case SMASH -> {
                float wind = rng(t, 0, 16), strike = rng(t, 16, 20), rec = rng(t, 27, 36);
                float x = lerp(lerp(0, -3.0F, wind), -0.2F, strike);
                x = lerp(x, 0, rec);
                for (int a : new int[]{fl, fr}) rot(p, a, x, 0, lerp(lerp(0, 0.2F, wind), -0.15F, strike) * (a == fl ? 1 : -1) * (1 - rec));
                for (int a : new int[]{bl, br}) rot(p, a, x * 0.7F, 0, 0);
                float lean = lerp(lerp(0, 0.35F, wind), -0.55F, strike) * (1 - rec);
                rot(p, chest, lean, 0, 0);
                rot(p, head, -lean * 0.5F, 0, 0);
                p.addPos(body, 0, 6F * strike * (1 - rec), 0);
            }
            case ROAR -> {
                float up = rng(t, 0, 10), down = 1F - rng(t, 44, 56);
                float w = Math.min(up, down);
                float shake = (float) Math.sin(t * 1.9F) * 0.06F * w;
                rot(p, head, -0.55F * w + shake, shake * 0.6F, 0);
                rot(p, chest, 0.32F * w, 0, 0);
                for (int j : jaws) rot(p, j, 0.85F * w, 0, 0);
                rot(p, fl, -0.5F * w, 0, -0.9F * w); rot(p, fr, -0.5F * w, 0, 0.9F * w);
                rot(p, bl, -0.3F * w, 0, -0.7F * w); rot(p, br, -0.3F * w, 0, 0.7F * w);
                p.addPos(body, 0, -2F * w, 0);
            }
            case STAGGER -> {
                float w = Math.min(rng(t, 0, 6), 1F - rng(t, 40, 60));
                float sw = (float) Math.sin(t * 0.5F);
                rot(p, chest, 0.32F * w, 0, 0.12F * sw * w);
                rot(p, head, 0.2F * w, 0.2F * sw * w, 0);
                for (int a : new int[]{fl, fr, bl, br}) rot(p, a, -1.1F * w + 0.2F * sw, 0, (a == fl || a == bl ? -0.5F : 0.5F) * w);
            }
            case LEAP -> {
                float crouch = 1F - rng(t, 10, 14);
                float w = Math.min(rng(t, 0, 10), 1F);
                float c = w * crouch;
                p.addPos(body, 0, 9F * c, 0);
                rot(p, s.bone("hipL"), -0.7F * c, 0, 0); rot(p, s.bone("hipR"), -0.7F * c, 0, 0);
                rot(p, s.bone("kneeL"), 1.0F * c, 0, 0); rot(p, s.bone("kneeR"), 1.0F * c, 0, 0);
                rot(p, chest, 0.3F * c, 0, 0);
                for (int a : new int[]{fl, fr, bl, br}) rot(p, a, 0.6F * c, 0, 0);
            }
            case LAND -> {
                float w = 1F - rng(t, 0, 14);
                p.addPos(body, 0, 8F * w, 0);
                rot(p, chest, 0.25F * w, 0, 0);
                rot(p, s.bone("hipL"), -0.6F * w, 0, 0); rot(p, s.bone("hipR"), -0.6F * w, 0, 0);
                rot(p, s.bone("kneeL"), 0.9F * w, 0, 0); rot(p, s.bone("kneeR"), 0.9F * w, 0, 0);
            }
            default -> { }
        }
    }

    /** postura del agarre (sumada a la animacion de reposo): igual en cliente y servidor, asi la mano esta donde se ve */
    public static void stance(Model m, Spec s, Pose p, Act act, float t, float struggle, double timeMs) {
        int chest = s.bone("chest"), head = s.bone("head");
        int fl = s.bone("armFL"), fr = s.bone("armFR"), bl = s.bone("armBL"), br = s.bone("armBR");
        float armUp = s.grab.get("armUp").getAsFloat(), armMouth = s.grab.get("armMouth").getAsFloat(), armReach = s.grab.get("armReach").getAsFloat();
        float sway = (float) Math.sin(timeMs / 420.0) * 0.025F;
        float ax, lean, headX = 0, jaw = 0, fade = 1F, eng;
        switch (act) {
            case REACH -> { float k = rng(t, 0, 13); ax = lerp(0, armReach, k); lean = -0.22F * k; eng = k; }
            case HOLD -> {
                float up = rng(t, 0, 18);
                ax = lerp(armReach, armUp, up) + (float) Math.sin(t * 1.7F) * 0.05F * struggle; lean = lerp(-0.22F, 0.10F, up); eng = 1F;
                headX = 0.10F * up;
            }
            case KILL -> {
                float to = rng(t, 0, 30);
                ax = lerp(armUp, armMouth, to); lean = lerp(0.10F, 0.62F, to); headX = lerp(0.10F, 0.75F, to); eng = 1F;
                jaw = 0.95F * rng(t, 22, 38) * (1F - rng(t, 44, 49));
            }
            case RECOVER -> { ax = armMouth; lean = 0.62F; headX = 0.75F; fade = 1F - rng(t, 0, 26); eng = 1F; }
            default -> { // THROW
                float sw = rng(t, 0, 9);
                ax = lerp(armUp, -0.15F, sw); lean = lerp(0.10F, -0.35F, sw); headX = 0.10F * (1F - sw);
                fade = 1F - rng(t, 13, 32); eng = 1F;
            }
        }
        // el brazo que agarra, el tronco y la cabeza; los otros brazos solo acompanan un poco para que no quede rigido
        rot(p, fl, ax * fade, 0, 0);
        rot(p, fr, (-0.30F * eng + sway * 2) * fade, 0, 0.20F * eng * fade);
        rot(p, bl, (-0.22F * eng + sway) * fade, 0, -0.14F * eng * fade);
        rot(p, br, (-0.22F * eng - sway) * fade, 0, 0.14F * eng * fade);
        rot(p, chest, (lean + sway) * fade, 0, 0);
        rot(p, head, headX * fade, 0, 0);
        for (int j : s.boneList("jaws")) rot(p, j, jaw, 0, 0);
        float bend = Math.abs(lean) * fade;
        rot(p, s.bone("kneeL"), 0.18F * bend, 0, 0); rot(p, s.bone("kneeR"), 0.18F * bend, 0, 0);
    }

    /** matriz raiz del modelo: yaw en grados (el de la entidad), escala del jefe y suelo del modelo */
    public static Mat4 root(Model m, float scale, float yawDeg) {
        return new Mat4().rotateY((float) Math.toRadians(180F - yawDeg)).translate(0, m.ground / 16F * scale, 0).scale(-scale / 16F, -scale / 16F, scale / 16F);
    }
}
