package com.tunombre.obscurearcade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Un luchador del duelo. Hoy son jugadores; la interfaz esta para anadir luchadores NPC (obscurenpc) sin tocar la logica del duelo.
 */
public interface Fighter {
    UUID id();

    String name();

    LivingEntity entity();

    /** jugador real (el que juega la cancion en CubOsu), o null si es un NPC */
    ServerPlayer player();

    /** reproduce una animacion por nombre (ver Styles.find) visible para todos los que lo rodean */
    void play(String animation);

    /** true mientras siga conectado y vivo */
    boolean valid();

    static Fighter of(ServerPlayer p) {
        return new Fighter() {
            public UUID id() { return p.getUUID(); }

            public String name() { return p.getGameProfile().getName(); }

            public LivingEntity entity() { return p; }

            public ServerPlayer player() { return p; }

            public void play(String animation) { Anims.play(p, animation); }

            public boolean valid() { return !p.hasDisconnected() && p.isAlive() && !p.isRemoved(); }
        };
    }
}
