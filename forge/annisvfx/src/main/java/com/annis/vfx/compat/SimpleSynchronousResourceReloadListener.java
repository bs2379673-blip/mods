package com.annis.vfx.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public interface SimpleSynchronousResourceReloadListener extends ResourceManagerReloadListener {
    ResourceLocation getFabricId();
}
