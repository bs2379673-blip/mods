package com.annis.vfx.client.fx;

import com.annis.vfx.client.Draw;
import net.minecraft.client.world.ClientWorld;

/** Un efecto que se dibuja en funcion del tiempo (siempre igual para todos: sin estado al azar en vivo). */
public interface Fx {
    /** t = segundos desde que empezo la escena. */
    void render(Draw d, ClientWorld world, float t);

    /** true cuando ya no va a dibujar nada mas. */
    boolean done(float t);
}
