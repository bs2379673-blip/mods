package com.tunombre.tacui;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = TacUI.ID, value = Dist.CLIENT)
    public static final class Forge {
        @SubscribeEvent
        public static void onScreen(ScreenEvent.Opening e) {
            if (!Layout.get().inventory) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            Screen s = e.getNewScreen();
            try {
                if (s instanceof InventoryScreen) e.setNewScreen(new TacInventoryScreen(mc.player));
                else if (s instanceof CreativeModeInventoryScreen)
                    e.setNewScreen(new TacInventoryScreen(mc.player, mc.player.inventoryMenu, TacInventoryScreen.Mode.CREATIVE));
                else if (s instanceof AbstractContainerScreen<?> acs && CuriosCompat.isMenu(acs.getMenu()))
                    e.setNewScreen(new TacInventoryScreen(mc.player, acs.getMenu(), TacInventoryScreen.Mode.CURIOS));
            } catch (Throwable t) {
                TacUI.LOGGER.warn("No se pudo abrir la pantalla tactica, se usa la de vainilla: {}", t.toString());
            }
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (!Layout.get().hud) return;
            var id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                    || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onCommands(RegisterClientCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("tacui")
                    .then(Commands.literal("theme").then(Commands.argument("name", StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"red", "blue", "green", "orange", "blackout"}, b))
                            .executes(c -> { Layout l = Layout.get(); l.theme = Theme.of(StringArgumentType.getString(c, "name")).name(); l.save(); return 1; })))
                    .then(Commands.literal("reset").executes(c -> { Layout l = Layout.get(); l.pos.clear(); l.save(); msg("Layout reiniciado"); return 1; }))
                    .then(Commands.literal("inventory").then(Commands.argument("on", StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                            .executes(c -> { Layout l = Layout.get(); l.inventory = !StringArgumentType.getString(c, "on").equalsIgnoreCase("off"); l.save(); msg("Inventario tactico: " + (l.inventory ? "activado" : "desactivado")); return 1; })))
                    .then(Commands.literal("containers").then(Commands.argument("on", StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                            .executes(c -> { Layout l = Layout.get(); l.containers = !StringArgumentType.getString(c, "on").equalsIgnoreCase("off"); l.save(); msg("Contenedores tacticos: " + (l.containers ? "activados" : "desactivados")); return 1; })))
                    .then(Commands.literal("hud").then(Commands.argument("on", StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                            .executes(c -> { Layout l = Layout.get(); l.hud = !StringArgumentType.getString(c, "on").equalsIgnoreCase("off"); l.save(); msg("HUD tactico: " + (l.hud ? "activado" : "desactivado")); return 1; }))));
        }

        private static void msg(String s) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
        }
    }

    @Mod.EventBusSubscriber(modid = TacUI.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("hud", HudOverlay::render);
        }
    }
}
