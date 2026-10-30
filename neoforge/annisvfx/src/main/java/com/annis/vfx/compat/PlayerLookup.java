package com.annis.vfx.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerLookup {
    private PlayerLookup() {
    }

    public static Collection<ServerPlayer> world(ServerLevel level) {
        return new ArrayList<>(level.players());
    }

    public static Collection<ServerPlayer> around(ServerLevel level, Vec3 pos, double radius) {
        double r2 = radius * radius;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Los jugadores que ven a la entidad (los que tienen cargado su chunk y estan dentro del alcance
     * de seguimiento de ese tipo de entidad). No incluye a la entidad misma si es jugador.
     */
    public static Collection<ServerPlayer> tracking(Entity entity) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(entity.level() instanceof ServerLevel level)) {
            return out;
        }
        double range = Math.max(entity.getType().clientTrackingRange() * 16.0, 64.0);
        double r2 = range * range;
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(entity.blockPosition()), false)) {
            if (p != entity && horizontal(p, entity) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
