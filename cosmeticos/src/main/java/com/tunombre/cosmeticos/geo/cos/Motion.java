package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.Ctx;
import com.tunombre.cosmeticos.geo.Noise;

/** estado de aleteo compartido por todas las alas: apertura suavizada, fase y amplitud segun lo que haga el jugador */
final class Motion {
    float open = 0.2F, phase, amp, bias, bank, landed;
    boolean wasAir;

    /** @param idleOpen apertura en reposo; @param flapRate velocidad angular del aleteo en el aire */
    void update(Ctx c, float idleOpen, float flapRate) {
        float target, w, a, b;
        if (c.crouch) { target = 0F; w = 0.05F; a = 0.02F; b = 0F; }
        else if (c.glide) { target = 1F; w = 0.05F; a = 0.05F; b = 0.12F; }
        else if (c.air) {
            float fall = Noise.clamp(-c.vy * 2.2F, 0F, 1F);
            target = 0.82F + 0.18F * fall;
            w = flapRate * (1F + 0.8F * fall + (c.vy > 0.1F ? 0.5F : 0F));
            a = 0.62F - 0.2F * fall; b = 0.18F;
        } else if (c.swim) { target = 0.4F; w = 0.12F; a = 0.18F; b = 0.05F; }
        else if (c.speed > 0.12F) { target = idleOpen + 0.25F; w = 0.14F + c.speed * 0.8F; a = 0.1F; b = 0.03F; }
        else if (c.speed > 0.02F) { target = idleOpen + 0.12F; w = 0.1F; a = 0.07F; b = 0.02F; }
        else { target = idleOpen; w = 0.06F; a = 0.045F; b = 0F; }
        if (wasAir && !c.air) landed = 1F;       // al aterrizar se encogen de golpe
        wasAir = c.air;
        landed = Math.max(0F, landed - c.dt * 0.08F);
        target = Math.max(0F, target - landed * 0.55F);
        float k = 1F - (float) Math.exp(-c.dt * (c.glide ? 0.12F : target > open ? 0.22F : 0.14F));
        open += (target - open) * k;
        phase += w * c.dt;
        amp += (a - amp) * (1F - (float) Math.exp(-c.dt * 0.2F));
        bias += (b - bias) * (1F - (float) Math.exp(-c.dt * 0.2F));
        bank += (Noise.clamp(c.turnAcc * 0.05F, -0.35F, 0.35F) - bank) * (1F - (float) Math.exp(-c.dt * 0.25F));
    }

    /** angulo de elevacion de un ala; delay retrasa la fase hacia la punta (s en 0..1) */
    float flap(int side, float s, float delay) {
        float base = amp * (float) Math.sin(phase - s * delay) + bias;
        return side * (base + side * bank * 0.8F);
    }
}
