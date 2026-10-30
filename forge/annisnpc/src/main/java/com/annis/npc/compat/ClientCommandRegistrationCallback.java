package com.annis.npc.compat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

public interface ClientCommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess);

    Event<ClientCommandRegistrationCallback> EVENT = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> {
        for (ClientCommandRegistrationCallback c : ClientCommandRegistrationCallback.EVENT.listeners()) {
            c.register(e.getDispatcher(), e.getBuildContext());
        }
    }));
}
