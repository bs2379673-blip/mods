package __PKG__;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public interface SimpleSynchronousResourceReloadListener extends ResourceManagerReloadListener {
    ResourceLocation getFabricId();
}
