package com.annis.npc.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ServerPlayConnectionEvents {
    private ServerPlayConnectionEvents() {
    }

    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, Object sender, MinecraftServer server);
    }

    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final Event<Join> JOIN = new Event<>(() -> NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
        if (e.getEntity() instanceof ServerPlayer p && p.connection != null) {
            for (Join j : ServerPlayConnectionEvents.JOIN.listeners()) {
                j.onPlayReady(p.connection, null, p.server);
            }
        }
    }));

    public static final Event<Disconnect> DISCONNECT = new Event<>(() -> NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
        if (e.getEntity() instanceof ServerPlayer p && p.connection != null) {
            for (Disconnect d : ServerPlayConnectionEvents.DISCONNECT.listeners()) {
                d.onPlayDisconnect(p.connection, p.server);
            }
        }
    }));
}
