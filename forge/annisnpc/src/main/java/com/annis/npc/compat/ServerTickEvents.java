package com.annis.npc.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ServerTickEvents {
    private ServerTickEvents() {
    }

    public interface Tick {
        void onTick(MinecraftServer server);
    }

    public static final Event<Tick> START_SERVER_TICK = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
        if (e.phase == TickEvent.Phase.START) fire(ServerTickEvents.START_SERVER_TICK);
    }));
    public static final Event<Tick> END_SERVER_TICK = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
        if (e.phase == TickEvent.Phase.END) fire(ServerTickEvents.END_SERVER_TICK);
    }));

    private static void fire(Event<Tick> ev) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (Tick t : ev.listeners()) {
                t.onTick(server);
            }
        }
    }
}
