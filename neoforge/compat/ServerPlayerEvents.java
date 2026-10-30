package __PKG__;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerPlayerEvents {
    private ServerPlayerEvents() {
    }

    public interface AfterRespawn {
        void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }

    /** Clone guarda el jugador viejo; Respawn avisa cuando el nuevo ya esta en el mundo. */
    private static final Map<UUID, ServerPlayer> OLD = new ConcurrentHashMap<>();

    public static final Event<AfterRespawn> AFTER_RESPAWN = new Event<>(() -> {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (e.getOriginal() instanceof ServerPlayer old) {
                OLD.put(old.getUUID(), old);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                ServerPlayer old = OLD.remove(p.getUUID());
                for (AfterRespawn a : ServerPlayerEvents.AFTER_RESPAWN.listeners()) {
                    a.afterRespawn(old == null ? p : old, p, e.isEndConquered());
                }
            }
        });
    });
}
