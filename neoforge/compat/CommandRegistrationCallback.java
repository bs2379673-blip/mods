package __PKG__;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public interface CommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);

    Event<CommandRegistrationCallback> EVENT = new Event<>(() -> NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
        for (CommandRegistrationCallback c : CommandRegistrationCallback.EVENT.listeners()) {
            c.register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection());
        }
    }));
}
