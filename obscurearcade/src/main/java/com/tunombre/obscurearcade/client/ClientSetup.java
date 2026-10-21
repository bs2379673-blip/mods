package com.tunombre.obscurearcade.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

public final class ClientSetup {
    private ClientSetup() { }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientSetup.class);
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) { FnfCommand.register(e.getDispatcher()); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { ClientState.dueling = false; }
}
