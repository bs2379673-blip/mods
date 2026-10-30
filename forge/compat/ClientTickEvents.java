package __PKG__;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class ClientTickEvents {
    private ClientTickEvents() {
    }

    public interface Tick {
        void onTick(Minecraft client);
    }

    public static final Event<Tick> START_CLIENT_TICK = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
        if (e.phase == TickEvent.Phase.START) {
            for (Tick t : ClientTickEvents.START_CLIENT_TICK.listeners()) t.onTick(Minecraft.getInstance());
        }
    }));
    public static final Event<Tick> END_CLIENT_TICK = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
        if (e.phase == TickEvent.Phase.END) {
            for (Tick t : ClientTickEvents.END_CLIENT_TICK.listeners()) t.onTick(Minecraft.getInstance());
        }
    }));
}
