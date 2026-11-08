package com.starlevi.starmodels.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

/** Arranque del cliente: recibe los personajes, agrega la capa que los dibuja y la tecla del workshop (J). */
public final class StarModelsClient implements ClientModInitializer {
    private static KeyMapping menu;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.Index.TYPE, (p, ctx) -> ClientAvatars.receive(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Chunk.TYPE, (p, ctx) -> ClientAvatars.chunk(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Outfit.TYPE, (p, ctx) -> ClientAvatars.outfit(p));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> ar) {
                helper.register(new AvatarLayer((RenderLayerParent) ar));
            }
        });
        KeyMapping.Category cat = KeyMapping.Category.register(Net.id("starmodels"));
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.starmodels.workshop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menu.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new WorkshopScreen());
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(ClientAvatars::disconnect));
    }

    /** Desde el mixin del dibujo del jugador: que personaje lleva (y si se ve de lejos). */
    public static void extract(Avatar e, AvatarRenderState s) {
        FabricRenderState fs = (FabricRenderState) (Object) s;
        ClientAvatars.Look look = ClientAvatars.look(e.getUUID());
        ClientAvatars.Ready r = look == null ? null : ClientAvatars.get(look.base());
        if (r == null) {
            fs.setData(AvatarLayer.KEY, null);
            return;
        }
        for (ClientAvatars.Added a : look.with()) {
            ClientAvatars.get(a.from()); // que se vaya bajando
        }
        Entity cam = Minecraft.getInstance().getCameraEntity();
        boolean far = cam != null && cam != e && e.distanceToSqr(cam) > AvatarLayer.FAR * AvatarLayer.FAR;
        fs.setData(AvatarLayer.KEY, new AvatarLayer.Frame(look, r, far));
        s.showCape = false;
    }
}
