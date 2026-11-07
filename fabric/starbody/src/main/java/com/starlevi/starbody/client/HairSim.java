package com.starlevi.starbody.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * Las fisicas del pelo de un personaje: para cada cara (derecha, frente, izquierda, atras) de la
 * cabeza y del torso, cuanto se abre hacia afuera. Un resorte suave con un poco de rebote, movido
 * por caminar, girar, caer, agacharse y mover la cabeza. Barato: 8 valores por personaje.
 */
public final class HairSim {
    private static final float[] STIFF = {16, 10, 6.5f}, GAIN = {0.6f, 1f, 1.45f};

    /** [parte][cara]: angulo, velocidad y la punta (que llega un poco despues). */
    final float[][] angle = new float[2][4], vel = new float[2][4], tip = new float[2][4];
    private double lastX = Double.NaN, lastY, lastZ;
    private float lastYaw;
    private long lastNanos;
    float time;

    public void update(LivingEntity e, float partial, int motion) {
        long now = System.nanoTime();
        float dt = this.lastNanos == 0 ? 0 : Math.min(0.1f, (now - this.lastNanos) / 1e9f);
        this.lastNanos = now;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        float yaw = e.yBodyRot;
        if (Double.isNaN(this.lastX) || dt <= 0) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastYaw = yaw;
            return;
        }
        // velocidad en bloques por segundo, en el espacio del cuerpo
        double dx = (x - this.lastX) / dt, dy = (y - this.lastY) / dt, dz = (z - this.lastZ) / dt;
        if (dx * dx + dz * dz > 400) {
            dx = dz = dy = 0; // teletransporte
        }
        double r = Math.toRadians(yaw);
        float fwd = (float) (-Math.sin(r) * dx + Math.cos(r) * dz);
        float left = (float) (Math.cos(r) * dx + Math.sin(r) * dz);
        float turn = wrap(yaw - this.lastYaw) / dt; // grados por segundo
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.time += dt;

        float g = GAIN[motion], k = STIFF[motion], c = 2 * (float) Math.sqrt(k) * 0.55f;
        float pitch = (float) Math.toRadians(e.getXRot());
        boolean crouch = e.hasPose(Pose.CROUCHING);
        float fall = (float) Math.max(0, -dy) * 0.12f, rise = (float) Math.max(0, dy) * 0.05f;
        for (int part = 0; part < 2; part++) {
            for (int side = 0; side < 4; side++) {
                float t = 0;
                switch (side) {
                    case 0 -> t = left * 0.09f - turn * 0.0016f;   // derecha
                    case 1 -> t = -fwd * 0.09f;                     // frente
                    case 2 -> t = -left * 0.09f + turn * 0.0016f;  // izquierda
                    default -> t = fwd * 0.09f;                     // atras
                }
                t += fall - rise;
                if (part == 0) {
                    // la cabeza se inclina y el pelo sigue colgando hacia abajo
                    if (side == 1) {
                        t += pitch * 0.85f;
                    } else if (side == 3) {
                        t -= pitch * 0.85f;
                    }
                } else if (crouch) {
                    t += side == 3 ? 0.35f : side == 1 ? -0.35f : 0;
                }
                t = t * g + 0.025f * (float) Math.sin(this.time * 1.6f + side * 1.3f + part);
                t = Math.max(-0.06f, Math.min(1.25f, t));
                // resorte con un poco de rebote (en pasitos para que sea estable)
                float rest = dt;
                while (rest > 0) {
                    float h = Math.min(rest, 1 / 120f);
                    float a = k * (t - this.angle[part][side]) - c * this.vel[part][side];
                    this.vel[part][side] += a * h;
                    this.angle[part][side] += this.vel[part][side] * h;
                    rest -= h;
                }
                this.angle[part][side] = Math.max(-0.08f, Math.min(1.4f, this.angle[part][side]));
                this.tip[part][side] += (this.angle[part][side] - this.tip[part][side]) * Math.min(1, dt * 7);
            }
        }
    }

    private static float wrap(float d) {
        d %= 360;
        if (d > 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }

    /** Angulo del segmento k de n en una tira (la raiz casi quieta, la punta con atraso). */
    public float segment(int part, int side, int k, int n, float phase) {
        float f = n <= 1 ? 1 : k / (float) (n - 1);
        float a = this.angle[part][side] + (this.tip[part][side] - this.angle[part][side]) * f;
        float w = 0.25f + 0.75f * f;
        return a * w + 0.015f * f * (float) Math.sin(this.time * 2.3f + phase);
    }
}
