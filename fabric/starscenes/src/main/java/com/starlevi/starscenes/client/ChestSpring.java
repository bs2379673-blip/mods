package com.starlevi.starscenes.client;

import net.minecraft.world.entity.Entity;

/** Un resorte suave para el pecho: rebota al saltar, al caer y un poquito al caminar. */
final class ChestSpring {
    private float off, vel;
    private double lastY = Double.NaN;
    private float lastVy, lastT = -1;
    private float phase;

    /** Devuelve el giro extra (radianes). */
    float update(Entity e, float now, float walkSpeed) {
        float dt = this.lastT < 0 ? 0.05f : Math.min(0.1f, Math.max(0.001f, now - this.lastT));
        this.lastT = now;
        double y = e.getY();
        float vy = Double.isNaN(this.lastY) ? 0 : (float) ((y - this.lastY) / dt);
        this.lastY = y;
        float acc = (vy - this.lastVy) / dt;
        this.lastVy = vy;
        // al caminar, un rebote a cada paso
        this.phase += dt * 11f * Math.min(1, walkSpeed * 8);
        float walk = (float) Math.sin(this.phase) * Math.min(1, walkSpeed * 6) * 1.4f;
        float force = -acc * 0.012f + walk;
        this.vel += (force - this.off * 90f - this.vel * 9f) * dt;
        this.off += this.vel * dt;
        this.off = Math.max(-0.35f, Math.min(0.35f, this.off));
        return this.off;
    }
}
