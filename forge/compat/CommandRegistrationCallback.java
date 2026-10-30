package __PKG__;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

public interface CommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);

    Event<CommandRegistrationCallback> EVENT = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
        for (CommandRegistrationCallback c : CommandRegistrationCallback.EVENT.listeners()) {
            c.register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection());
        }
    }));
}
