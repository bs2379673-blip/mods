package __PKG__;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class EntityTrackingEvents {
    private EntityTrackingEvents() {
    }

    public interface StartTracking {
        void onStartTracking(Entity trackedEntity, ServerPlayer player);
    }

    public interface StopTracking {
        void onStopTracking(Entity trackedEntity, ServerPlayer player);
    }

    public static final Event<StartTracking> START_TRACKING = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
        if (e.getEntity() instanceof ServerPlayer p) {
            for (StartTracking s : EntityTrackingEvents.START_TRACKING.listeners()) {
                s.onStartTracking(e.getTarget(), p);
            }
        }
    }));

    public static final Event<StopTracking> STOP_TRACKING = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StopTracking e) -> {
        if (e.getEntity() instanceof ServerPlayer p) {
            for (StopTracking s : EntityTrackingEvents.STOP_TRACKING.listeners()) {
                s.onStopTracking(e.getTarget(), p);
            }
        }
    }));
}
