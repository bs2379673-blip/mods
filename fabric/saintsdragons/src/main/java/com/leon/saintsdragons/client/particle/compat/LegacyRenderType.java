package com.leon.saintsdragons.client.particle.compat;

/** Port 1.21.11: como se dibujaba cada particula en 1.20.1 (el juego ya no tiene estos tipos). */
public enum LegacyRenderType {
    PARTICLE_SHEET_OPAQUE,
    PARTICLE_SHEET_TRANSLUCENT,
    /** Mezcla normal, sin escribir profundidad (los brillos que se superponen). */
    TRANSLUCENT_NO_DEPTH_WRITE,
    /** La particula se dibuja sola (no pasa por el lote). */
    CUSTOM
}
