package __PKG__;

import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientReceiveMessageEvents {
    private ClientReceiveMessageEvents() {
    }

    public interface Game {
        void onReceiveGameMessage(Component message, boolean overlay);
    }

    public static final Event<Game> GAME = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System e) -> {
        for (Game g : ClientReceiveMessageEvents.GAME.listeners()) g.onReceiveGameMessage(e.getMessage(), e.isOverlay());
    }));
}
