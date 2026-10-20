package com.tunombre.obscureui.client;

import com.mojang.brigadier.Command;
import com.tunombre.obscureui.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ObscureClient {
    private ObscureClient() { }

    static final KeyMapping OPEN = new KeyMapping("key.obscureui.open", 79, "key.categories.obscureui"); // O

    public static void init(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.register(ObscureClient.class);
    }

    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasPermissions(2)) {
            mc.player.displayClientMessage(Component.literal("Obscure UI: hace falta ser operador"), true);
            return;
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("cotiiui")) {
            try {
                openCotii();
                return;
            } catch (Throwable t) {
                // si falla CotiiUI se usa el panel propio
            }
        }
        mc.setScreen(new ObscureScreen());
    }

    /** panel hecho con CotiiUI (clase aparte: solo se carga si el mod esta instalado) */
    private static void openCotii() {
        com.cotii.ui.CotiiUI.openPanel(Component.literal("Obscure"), new ObscurePanel());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().screen == null) open();
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("obscure").executes(c -> {
            Minecraft.getInstance().tell(ObscureClient::open);
            return Command.SINGLE_SUCCESS;
        }));
    }

    public static void onData(Net.Data d) {
        var scr = Minecraft.getInstance().screen;
        if (scr instanceof ObscureScreen s) { s.onData(d.key(), d.json()); return; }
        if (net.minecraftforge.fml.ModList.get().isLoaded("cotiiui") && scr instanceof com.cotii.ui.PanelScreen ps && ps.panel() instanceof ObscurePanel p) p.onData(d.key(), d.json());
    }
}
