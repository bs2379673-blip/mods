package __PKG__;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public interface ClientCommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess);

    Event<ClientCommandRegistrationCallback> EVENT = new Event<>(() -> NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> {
        for (ClientCommandRegistrationCallback c : ClientCommandRegistrationCallback.EVENT.listeners()) {
            c.register(e.getDispatcher(), e.getBuildContext());
        }
    }));
}
