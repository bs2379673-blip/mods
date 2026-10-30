package com.annis.vfx.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class ServerPlayConnectionEvents {
    private ServerPlayConnectionEvents() {
    }

    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, Object sender, MinecraftServer server);
    }

    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final Event<Join> JOIN = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
        if (e.getEntity() instanceof ServerPlayer p && p.connection != null) {
            for (Join j : ServerPlayConnectionEvents.JOIN.listeners()) {
                j.onPlayReady(p.connection, null, p.server);
            }
        }
    }));

    public static final Event<Disconnect> DISCONNECT = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
        if (e.getEntity() instanceof ServerPlayer p && p.connection != null) {
            for (Disconnect d : ServerPlayConnectionEvents.DISCONNECT.listeners()) {
                d.onPlayDisconnect(p.connection, p.server);
            }
        }
    }));
}
