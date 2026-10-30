package com.annis.npc.compat;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;

public final class ResourceManagerHelper {
    private final PackType type;

    private ResourceManagerHelper(PackType type) {
        this.type = type;
    }

    public static ResourceManagerHelper get(PackType type) {
        return new ResourceManagerHelper(type);
    }

    public void registerReloadListener(PreparableReloadListener listener) {
        if (this.type == PackType.CLIENT_RESOURCES) {
            Compat.modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(listener));
        } else {
            MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(listener));
        }
    }
}
