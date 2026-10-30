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
        /** Doblez del codo / rodilla (giro de la mitad de abajo, como el pitch de la parte baja). */
        public float bend;
        /** Si se uso IK: punto al que apuntar recto cuando el modelo no se puede doblar. */
        public float[] rigid;
        public boolean touched;
        final float rx, ry, rz;
        /** Pose que traia la parte (la de caminar de vanilla o de EMF) antes de la animacion. */
        float inPitch, inYaw, inRoll, inX, inY, inZ;

        P(float rx, float ry, float rz) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.inX = rx;
            this.inY = ry;
            this.inZ = rz;
            this.x = rx;
            this.y = ry;
            this.z = rz;
        }

        /**
         * Conserva la pose que traia la parte (por ejemplo el paso al caminar) y la marca como
         * controlada; los ajustes que se hagan despues se suman a ella.
         */
        public P keep() {
            this.bend = 0;
            this.rigid = null;
            this.pitch = this.inPitch;
            this.yaw = this.inYaw;
            this.roll = this.inRoll;
            this.x = this.inX;
            this.y = this.inY;
            this.z = this.inZ;
            this.touched = true;
            return this;
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
            this.bend = 0;
            this.rigid = null;
            return at(this.rx, this.ry, this.rz).set(0, 0, 0);
        }

        public void load(float pitch, float yaw, float roll, float x, float y, float z) {
            this.inPitch = pitch;
            this.inYaw = yaw;
            this.inRoll = roll;
            this.inX = x;
            this.inY = y;
            this.inZ = z;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bend = 0;
            this.rigid = null;
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

        /**
         * Dos huesos (brazo o pierna): la punta llega a (tx,ty,tz) doblando el codo o la rodilla.
         * pole = hacia donde apunta el codo/rodilla; foldBack = la parte de abajo se dobla hacia
         * atras (rodillas) en vez de hacia delante (codos). rigidTarget = a donde apuntar recto si
         * el modelo no tiene articulaciones (null = al mismo punto).
         */
        public P ik(float tx, float ty, float tz, float px, float py, float pz, boolean foldBack,
                    float l1, float l2, float[] rigidTarget) {
            float dx = tx - this.x, dy = ty - this.y, dz = tz - this.z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.0e-3f) {
                return this;
            }
            float ux = dx / d, uy = dy / d, uz = dz / d;
            float dc = Math.max(Math.abs(l1 - l2) + 0.3f, Math.min(l1 + l2 - 0.02f, d));
            float cosF = (dc * dc - l1 * l1 - l2 * l2) / (2 * l1 * l2);
            float fold = (float) Math.acos(Math.max(-1f, Math.min(1f, cosF)));
            float cosA = (l1 * l1 + dc * dc - l2 * l2) / (2 * l1 * dc);
            float alpha = (float) Math.acos(Math.max(-1f, Math.min(1f, cosA)));
            // polo perpendicular al objetivo
            float pd = px * ux + py * uy + pz * uz;
            float qx = px - pd * ux, qy = py - pd * uy, qz = pz - pd * uz;
            float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (ql < 1.0e-3f) {
                // cualquiera perpendicular
                qx = uy;
                qy = -ux;
                qz = 0;
                ql = (float) Math.sqrt(qx * qx + qy * qy);
                if (ql < 1.0e-3f) {
                    qx = 0;
                    qy = uz;
                    qz = -uy;
                    ql = (float) Math.sqrt(qy * qy + qz * qz);
                }
            }
            qx /= ql;
            qy /= ql;
            qz /= ql;
            float ca = (float) Math.cos(alpha), sa = (float) Math.sin(alpha);
            // eje del hueso de arriba (Y local)
            float yx = ca * ux + sa * qx, yy = ca * uy + sa * qy, yz = ca * uz + sa * qz;
            // hacia donde se dobla la parte de abajo: hacia el objetivo, lejos del polo
            // w = componente del objetivo perpendicular a Y
            float td = ux * yx + uy * yy + uz * yz;
            float wx = ux - td * yx, wy, wz;
            wy = uy - td * yy;
            wz = uz - td * yz;
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1.0e-3f) {
                wx = -qx;
                wy = -qy;
                wz = -qz;
            } else {
                wx /= wl;
                wy /= wl;
                wz /= wl;
            }
            // Z local: codos se doblan hacia -Z (delante), rodillas hacia +Z (atras)
            float zx = foldBack ? wx : -wx, zy = foldBack ? wy : -wy, zz = foldBack ? wz : -wz;
            // X = Y x Z
            float xx = yy * zz - yz * zy, xy = yz * zx - yx * zz, xz = yx * zy - yy * zx;
            float yaw = (float) Math.asin(Math.max(-1f, Math.min(1f, -xz)));
            float pitch = (float) Math.atan2(yz, zz);
            float roll = (float) Math.atan2(xy, xx);
            set(pitch, yaw, roll);
            this.bend = foldBack ? fold : -fold;
            this.rigid = rigidTarget != null ? rigidTarget : new float[]{tx, ty, tz};
            return this;
        }

        /** Mezcla hacia otra pose (w = 0 se queda como esta, 1 = la otra). */
        public void blend(P o, float w) {
            this.pitch += (o.pitch - this.pitch) * w;
            this.yaw += (o.yaw - this.yaw) * w;
            this.roll += (o.roll - this.roll) * w;
            this.x += (o.x - this.x) * w;
            this.y += (o.y - this.y) * w;
            this.z += (o.z - this.z) * w;
            this.bend += (o.bend - this.bend) * w;
            this.touched |= o.touched;
        }

        void copy(P o) {
            this.pitch = o.pitch;
            this.yaw = o.yaw;
            this.roll = o.roll;
            this.x = o.x;
            this.y = o.y;
            this.z = o.z;
            this.bend = o.bend;
            this.rigid = o.rigid;
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

    /** true si las piernas conservan el paso de caminar (no se suavizan con inercia). */
    public boolean walkLegs;

    public final P head = new P(0, 0, 0);
    public final P body = new P(0, 0, 0);
    public final P rightArm = new P(-5, 2, 0);
    public final P leftArm = new P(5, 2, 0);
    public final P rightLeg = new P(-1.9f, 12, 0);
    public final P leftLeg = new P(1.9f, 12, 0);

    public P[] parts() {
        return new P[]{head, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    // largos: hombro->codo 4, codo->palma 5.5; cadera->rodilla 6, rodilla->planta 6
    public static final float ARM1 = 4.0f, ARM2 = 5.5f, LEG1 = 6.0f, LEG2 = 5.8f;

    /** Mano a un punto con el codo hacia "pole" (direccion en el modelo). */
    public static void hand(P arm, float[] t, float px, float py, float pz, float[] rigidTarget) {
        arm.ik(t[0], t[1], t[2], px, py, pz, false, ARM1, ARM2, rigidTarget);
    }

    /** Pie a un punto con la rodilla hacia "pole". */
    public static void foot(P leg, float[] t, float px, float py, float pz) {
        leg.ik(t[0], t[1], t[2], px, py, pz, true, LEG1, LEG2, null);
    }

    /** Punta (palma o planta) de un miembro con su doblez. */
    public static float[] tip(P limb, boolean arm) {
        float l1 = arm ? ARM1 : LEG1, l2 = arm ? ARM2 : LEG2;
        float[] m = rot(limb.pitch, limb.yaw, limb.roll);
        float c = (float) Math.cos(limb.bend), s = (float) Math.sin(limb.bend);
        // codo en (0,l1,0); antebrazo (0,l2,0) girado por Rx(bend)
        float[] v = mul(m, 0, l1 + l2 * c, l2 * s);
        return new float[]{limb.x + v[0], limb.y + v[1], limb.z + v[2]};
    }

    /**
     * Pieza rigida (sin codo ni rodilla): apunta recta al punto. Si el punto queda mas cerca que
     * el largo del brazo, el hombro se corre un poco hacia atras (como hace Fresh Animations).
     */
    public static void straighten(P p) {
        if (p.rigid != null) {
            float[] t = p.rigid;
            float dx = t[0] - p.x, dy = t[1] - p.y, dz = t[2] - p.z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float slack = 10.0f - d;
            if (d > 1.0e-3f && slack > 0) {
                float slide = Math.min(slack, 1.5f) / d;
                p.x -= dx * slide;
                p.y -= dy * slide;
                p.z -= dz * slide;
            }
            p.aim(t[0], t[1], t[2]);
        }
        p.bend = 0;
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
