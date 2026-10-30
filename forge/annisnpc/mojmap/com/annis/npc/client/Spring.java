package com.annis.npc.client;

/**
 * Dinamica de segundo orden (seguimiento con inercia): la pose no salta, acelera, se pasa un
 * poquito y se asienta. f = frecuencia (Hz), z = amortiguacion, r = respuesta inicial.
 */
public final class Spring {
    private final float k1, k2, k3;
    private float y, yd, xp;
    private boolean init;
    private final boolean angle;

    public Spring(float f, float z, float r, boolean angle) {
        float w = (float) (2 * Math.PI * f);
        this.k1 = z / (float) (Math.PI * f);
        this.k2 = 1.0f / (w * w);
        this.k3 = r * z / w;
        this.angle = angle;
    }

    private static float wrap(float a) {
        while (a > Math.PI) {
            a -= (float) (2 * Math.PI);
        }
        while (a < -Math.PI) {
            a += (float) (2 * Math.PI);
        }
        return a;
    }

    public void reset(float x) {
        this.y = x;
        this.xp = x;
        this.yd = 0;
        this.init = true;
    }

    public boolean ready() {
        return this.init;
    }

    /** dt en segundos. */
    public float update(float x, float dt) {
        if (!this.init) {
            reset(x);
            return x;
        }
        if (this.angle) {
            x = this.y + wrap(x - this.y);
            this.xp = this.y + wrap(this.xp - this.y);
        }
        if (dt <= 0) {
            return this.y;
        }
        dt = Math.min(dt, 0.1f);
        float xd = (x - this.xp) / dt;
        this.xp = x;
        float k2s = Math.max(this.k2, Math.max(dt * dt / 2 + dt * this.k1 / 2, dt * this.k1));
        this.y += dt * this.yd;
        this.yd += dt * (x + this.k3 * xd - this.y - this.k1 * this.yd) / k2s;
        return this.y;
    }
}
