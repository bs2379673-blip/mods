package __PKG__;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientPlayConnectionEvents {
    private ClientPlayConnectionEvents() {
    }

    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }

    public interface Join {
        void onPlayReady(ClientPacketListener handler, Object sender, Minecraft client);
    }

    public static final Event<Disconnect> DISCONNECT = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
        for (Disconnect d : ClientPlayConnectionEvents.DISCONNECT.listeners()) d.onPlayDisconnect(null, Minecraft.getInstance());
    }));

    public static final Event<Join> JOIN = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
        for (Join j : ClientPlayConnectionEvents.JOIN.listeners()) j.onPlayReady(Minecraft.getInstance().getConnection(), null, Minecraft.getInstance());
    }));
}
