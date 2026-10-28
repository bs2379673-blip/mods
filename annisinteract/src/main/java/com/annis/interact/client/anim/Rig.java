package com.annis.interact.client.anim;

/**
 * Pose de un cuerpo de jugador sin depender de Minecraft (se puede probar fuera del juego).
 *
 * Coordenadas del modelo, en pixeles: x = izquierda del personaje, y = abajo, z = atras
 * (el frente es -z). Pivotes de reposo: cabeza y torso (0,0,0), brazos (+-5,2,0), piernas (+-1.9,12,0).
 * Giros como ModelPart: Rz(roll) * Ry(yaw) * Rx(pitch).
 */
public final class Rig {
    public static final float PI = (float) Math.PI;

    public static final class P {
        public float pitch, yaw, roll, x, y, z;
        public boolean touched;
        final float rx, ry, rz;

        P(float rx, float ry, float rz) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        public P set(float pitch, float yaw, float roll) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.touched = true;
            return this;
        }

        public P at(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.touched = true;
            return this;
        }

        /** Vuelve al reposo (pivote y giros) y la marca como controlada por la animacion. */
        public P rest() {
            return at(this.rx, this.ry, this.rz).set(0, 0, 0);
        }

        public void load(float pitch, float yaw, float roll, float x, float y, float z) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.x = x;
            this.y = y;
            this.z = z;
            this.touched = false;
        }

        /** Apunta el eje del miembro (hacia +y local) a un punto del modelo. */
        public P aim(float tx, float ty, float tz) {
            float dx = tx - this.x, dy = ty - this.y, dz = tz - this.z;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-4f) {
                return this;
            }
            return aimDir(dx / len, dy / len, dz / len);
        }

        public P aimDir(float ux, float uy, float uz) {
            float p = (float) Math.acos(Math.max(-1.0f, Math.min(1.0f, uy)));
            float horiz = (float) Math.sqrt(ux * ux + uz * uz);
            if (horiz < 1.0e-4f) {
                return set(uy > 0 ? 0 : -PI, 0, 0);
            }
            if (uz <= 0.0f) {
                // hacia delante: pitch negativo
                return set(-p, (float) Math.atan2(-ux, -uz), 0);
            }
            return set(p, (float) Math.atan2(ux, uz), 0);
        }

        /** Mezcla hacia otra pose (w = 0 se queda como esta, 1 = la otra). */
        public void blend(P o, float w) {
            this.pitch += (o.pitch - this.pitch) * w;
            this.yaw += (o.yaw - this.yaw) * w;
            this.roll += (o.roll - this.roll) * w;
            this.x += (o.x - this.x) * w;
            this.y += (o.y - this.y) * w;
            this.z += (o.z - this.z) * w;
            this.touched |= o.touched;
        }

        void copy(P o) {
            this.pitch = o.pitch;
            this.yaw = o.yaw;
            this.roll = o.roll;
            this.x = o.x;
            this.y = o.y;
            this.z = o.z;
            this.touched = o.touched;
        }
    }

    /**
     * Pose del cuerpo entero alrededor de la cadera (0.7 bloques sobre los pies): radianes y bloques
     * (forward = hacia donde mira, up = arriba, left = su izquierda).
     */
    public record Root(float pitch, float yaw, float roll, float forward, float up, float left) {
        public static final Root NONE = new Root(0, 0, 0, 0, 0, 0);

        public Root lerp(Root o, float w) {
            return new Root(pitch + (o.pitch - pitch) * w, yaw + (o.yaw - yaw) * w, roll + (o.roll - roll) * w,
                    forward + (o.forward - forward) * w, up + (o.up - up) * w, left + (o.left - left) * w);
        }
    }

    public final P head = new P(0, 0, 0);
    public final P body = new P(0, 0, 0);
    public final P rightArm = new P(-5, 2, 0);
    public final P leftArm = new P(5, 2, 0);
    public final P rightLeg = new P(-1.9f, 12, 0);
    public final P leftLeg = new P(1.9f, 12, 0);

    public P[] parts() {
        return new P[]{head, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    /** Todo el cuerpo en reposo (de pie, brazos abajo). */
    public Rig restAll() {
        for (P p : parts()) {
            p.rest();
        }
        return this;
    }

    public Rig restUpper() {
        head.rest();
        body.rest();
        rightArm.rest();
        leftArm.rest();
        return this;
    }

    /**
     * Inclina el torso alrededor de la cadera; la cabeza y los hombros se mueven con el.
     * Los brazos conservan su giro (se apuntan despues).
     */
    public Rig torso(float pitch, float yaw, float roll) {
        float[] m = rot(pitch, yaw, roll);
        float[] neck = mul(m, 0, -12, 0);
        body.at(neck[0], 12 + neck[1], neck[2]).set(pitch, yaw, roll);
        head.at(neck[0], 12 + neck[1], neck[2]);
        float[] r = mul(m, -5, -10, 0);
        rightArm.at(r[0], 12 + r[1], r[2]);
        float[] l = mul(m, 5, -10, 0);
        leftArm.at(l[0], 12 + l[1], l[2]);
        return this;
    }

    /** Sube o baja todo menos las piernas (rodillas flexionadas, agacharse un poco). */
    public Rig drop(float dy) {
        head.y += dy;
        body.y += dy;
        rightArm.y += dy;
        leftArm.y += dy;
        rightLeg.y += dy;
        leftLeg.y += dy;
        head.touched = body.touched = rightArm.touched = leftArm.touched = true;
        return this;
    }

    /** Gira la cabeza para mirar un punto del modelo (por encima de lo que tenga). */
    public Rig look(float tx, float ty, float tz, float w) {
        float hx = head.x, hy = head.y - 4, hz = head.z;
        float dx = tx - hx, dy = ty - hy, dz = tz - hz;
        float horiz = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.atan2(-dx, -dz);
        float pitch = (float) Math.atan2(dy, Math.max(0.01f, horiz));
        head.set(head.pitch + (pitch - head.pitch) * w, head.yaw + (yaw - head.yaw) * w, head.roll);
        return this;
    }

    public void copyFrom(Rig o) {
        P[] a = parts(), b = o.parts();
        for (int i = 0; i < a.length; i++) {
            a[i].copy(b[i]);
        }
    }

    // ---- matematica ----

    /** Matriz 3x3 (fila mayor) de Rz(roll)*Ry(yaw)*Rx(pitch). */
    public static float[] rot(float pitch, float yaw, float roll) {
        float cx = (float) Math.cos(pitch), sx = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cz = (float) Math.cos(roll), sz = (float) Math.sin(roll);
        // Ry*Rx
        float[] yx = {
                cy, sy * sx, sy * cx,
                0, cx, -sx,
                -sy, cy * sx, cy * cx};
        float[] z = {cz, -sz, 0, sz, cz, 0, 0, 0, 1};
        return mm(z, yx);
    }

    public static float[] mm(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    public static float[] mul(float[] m, float x, float y, float z) {
        return new float[]{
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z};
    }

    /** Punto local de una pieza en coordenadas del modelo. */
    public static float[] point(P p, float lx, float ly, float lz) {
        float[] v = mul(rot(p.pitch, p.yaw, p.roll), lx, ly, lz);
        return new float[]{p.x + v[0], p.y + v[1], p.z + v[2]};
    }

    // ---- del marco del modelo al marco del render (bloques) y la raiz ----

    /**
     * Modelo (px) -> marco de render de la entidad (bloques, delante = -Z, izquierda = -X, arriba = +Y),
     * aplicando la raiz. Igual que LivingEntityRenderer + NpcRenderer.applyRoot.
     */
    public static float[] modelToRender(Root root, float x, float y, float z) {
        float rx = -x / 16.0f, ry = 1.501f - y / 16.0f, rz = z / 16.0f;
        if (root == null) {
            return new float[]{rx, ry, rz};
        }
        float[] m = rot(root.pitch(), root.yaw(), root.roll());
        float[] v = mul(m, rx, ry - 0.7f, rz);
        return new float[]{v[0] - root.left(), v[1] + 0.7f + root.up(), v[2] - root.forward()};
    }

    /** Inversa de modelToRender. */
    public static float[] renderToModel(Root root, float rx, float ry, float rz) {
        float x = rx, y = ry, z = rz;
        if (root != null) {
            float[] m = rot(root.pitch(), root.yaw(), root.roll());
            float ax = rx + root.left(), ay = ry - 0.7f - root.up(), az = rz + root.forward();
            // transpuesta = inversa
            x = m[0] * ax + m[3] * ay + m[6] * az;
            y = m[1] * ax + m[4] * ay + m[7] * az + 0.7f;
            z = m[2] * ax + m[5] * ay + m[8] * az;
        }
        return new float[]{-x * 16.0f, (1.501f - y) * 16.0f, z * 16.0f};
    }

    // ---- utilidades de tiempo ----

    public static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    /** 0 -> 1 entre a y b. */
    public static float ramp(float t, float a, float b) {
        return smooth((t - a) / (b - a));
    }

    /** Sube entre a y b, se mantiene, y baja entre c y d. */
    public static float env(float t, float a, float b, float c, float d) {
        return Math.min(ramp(t, a, b), 1 - ramp(t, c, d));
    }

    public static float lerp(float a, float b, float w) {
        return a + (b - a) * w;
    }

    public static float sin(float v) {
        return (float) Math.sin(v);
    }

    public static float cos(float v) {
        return (float) Math.cos(v);
    }
}
