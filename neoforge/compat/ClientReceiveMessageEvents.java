package __PKG__;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientReceiveMessageEvents {
    private ClientReceiveMessageEvents() {
    }

    public interface Game {
        void onReceiveGameMessage(Component message, boolean overlay);
    }

    public static final Event<Game> GAME = new Event<>(() -> NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System e) -> {
        for (Game g : ClientReceiveMessageEvents.GAME.listeners()) g.onReceiveGameMessage(e.getMessage(), e.isOverlay());
    }));
}
