package com.annis.npc.compat;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerTickEvents {
    private ServerTickEvents() {
    }

    public interface Tick {
        void onTick(MinecraftServer server);
    }

    public static final Event<Tick> START_SERVER_TICK = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> {
        for (Tick t : ServerTickEvents.START_SERVER_TICK.listeners()) t.onTick(e.getServer());
    }));
    public static final Event<Tick> END_SERVER_TICK = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
        for (Tick t : ServerTickEvents.END_SERVER_TICK.listeners()) t.onTick(e.getServer());
    }));
}
