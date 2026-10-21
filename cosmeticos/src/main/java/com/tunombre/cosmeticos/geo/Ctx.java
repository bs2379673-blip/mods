package com.tunombre.cosmeticos.geo;

import java.util.Map;
import java.util.function.Supplier;

/** datos de movimiento del jugador, ejes de la camara en el hueso y memoria por jugador */
public final class Ctx {
    public float t;            // ticks con parte decimal
    public float dt;           // ticks desde el cuadro anterior de este jugador (0..2)
    public float vf, vl, vy;   // velocidad (bloques/tick): hacia delante, hacia la izquierda y arriba
    public float speed;        // velocidad horizontal
    public float turn;         // giro del cuerpo en grados por tick
    public float turnAcc;      // aceleracion del giro (suavizada)
    public boolean air, glide, crouch, swim, sprint;
    public float seed;         // 0..1 distinto por jugador
    public V3 camR = V3.X, camU = V3.Y, camF = V3.Z;   // ejes de la camara expresados en el espacio del hueso
    public Scene sc = new Scene();
    public Map<String, Object> mem;
    public float lift;         // 0..1 cuanto se ha despegado del suelo (suavizado)
    public float stride;       // fase de la zancada (sube con la velocidad)

    @SuppressWarnings("unchecked")
    public <T> T state(String key, Supplier<T> make) { return (T) mem.computeIfAbsent(key, k -> make.get()); }

    public float sin(float speedMul, float phase) { return (float) Math.sin(t * speedMul + phase); }
}
