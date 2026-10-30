package com.annis.vfx.compat;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientTickEvents {
    private ClientTickEvents() {
    }

    public interface Tick {
        void onTick(Minecraft client);
    }

    public static final Event<Tick> START_CLIENT_TICK = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> {
        for (Tick t : ClientTickEvents.START_CLIENT_TICK.listeners()) t.onTick(Minecraft.getInstance());
    }));
    public static final Event<Tick> END_CLIENT_TICK = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
        for (Tick t : ClientTickEvents.END_CLIENT_TICK.listeners()) t.onTick(Minecraft.getInstance());
    }));
}
