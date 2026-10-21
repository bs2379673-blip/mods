package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuResult;
import com.cotii.cubosu.api.ServerCubOsuListener;
import net.minecraft.server.level.ServerPlayer;

/** Resultados de CubOsu en el servidor: reparte a la maquina recreativa o al duelo que corresponda. */
public final class Results implements ServerCubOsuListener {
    @Override
    public void onFinish(ServerPlayer player, CubOsuResult result) {
        try {
            Duels.onFinish(player, result);
            Arcade.onFinish(player, result);
        } catch (RuntimeException ignored) { }
    }
}
