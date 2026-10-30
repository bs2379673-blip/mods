package __PKG__;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

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
            NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(listener));
        }
    }
}
