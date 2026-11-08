package com.leon.saintsdragons.server.ai.navigation.async;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Port 1.21.11: WalkNodeEvaluator.getBlockPathTypeStatic(BlockGetter, pos) de 1.20.1 ya no existe (ahora pide
 * un nivel con colisiones). Se arma igual a partir del bloque y el de abajo, sobre la copia de bloques.
 */
final class SnapshotPathTypes extends WalkNodeEvaluator {
    private SnapshotPathTypes() {
    }

    static PathType of(BlockGetter level, BlockPos.MutableBlockPos pos) {
        PathType type = getPathTypeFromState(level, pos);
        if (type != PathType.OPEN || pos.getY() < level.getMinY() + 1) {
            return type;
        }
        BlockPos below = pos.below();
        PathType under = getPathTypeFromState(level, below);
        type = under != PathType.WALKABLE && under != PathType.OPEN && under != PathType.WATER && under != PathType.LAVA
                ? PathType.WALKABLE : PathType.OPEN;
        if (under == PathType.DAMAGE_FIRE) {
            type = PathType.DAMAGE_FIRE;
        } else if (under == PathType.DAMAGE_OTHER) {
            type = PathType.DAMAGE_OTHER;
        } else if (under == PathType.STICKY_HONEY) {
            type = PathType.STICKY_HONEY;
        } else if (under == PathType.POWDER_SNOW) {
            type = PathType.DANGER_POWDER_SNOW;
        } else if (under == PathType.DAMAGE_CAUTIOUS) {
            type = PathType.DAMAGE_CAUTIOUS;
        }
        return type;
    }
}
