package com.annis.npc.compat;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {
    }

    public interface Server {
        void run(MinecraftServer server);
    }

    public static final Event<Server> SERVER_STOPPING = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
        for (Server s : ServerLifecycleEvents.SERVER_STOPPING.listeners()) {
            s.run(e.getServer());
        }
    }));

    public static final Event<Server> SERVER_STARTED = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
        for (Server s : ServerLifecycleEvents.SERVER_STARTED.listeners()) {
            s.run(e.getServer());
        }
    }));
}
