package com.annis.npc.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public interface SimpleSynchronousResourceReloadListener extends ResourceManagerReloadListener {
    ResourceLocation getFabricId();
}
