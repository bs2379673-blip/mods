package com.leon.saintsdragons.server.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Port 1.21.11: Path ya no se puede extender. Los caminos del buscador propio se crean como Path comunes y
 * aca se recuerda cuales terminan en el borde de la busqueda.
 */
public final class DragonGroundPath {
    private static final Map<Path, Boolean> FRONTIER = Collections.synchronizedMap(new WeakHashMap<>());

    private DragonGroundPath() {
    }

    public static Path create(List<Node> nodes, BlockPos target, boolean reached, boolean frontier) {
        Path path = new Path(nodes, target, reached);
        if (frontier) {
            FRONTIER.put(path, Boolean.TRUE);
        }
        return path;
    }

    public static boolean endsAtSearchBoundary(Path path) {
        return path != null && FRONTIER.containsKey(path);
    }
}
