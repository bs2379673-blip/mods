package com.starlevi.staravatars.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.starlevi.staravatars.net.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Arranque del cliente: recibe los personajes, agrega la capa que los dibuja y la tecla del menu. */
public final class StarAvatarsClient {
    public static final KeyMapping MENU = new KeyMapping("key.staravatars.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.staravatars");
    private static final Set<AbstractClientPlayer> HIDDEN = Collections.newSetFromMap(new WeakHashMap<>());

    private StarAvatarsClient() {
    }

    public static void init(IEventBus modBus) {
        Net.client = new Net.ClientSide() {
            @Override
            public void index(Net.Index i) {
                ClientAvatars.receive(i);
                AvatarScreen.refresh();
            }

            @Override
            public void chunk(Net.Chunk c) {
                ClientAvatars.chunk(c);
            }

            @Override
            public void outfit(Net.Outfit o) {
                ClientAvatars.outfit(o);
                AvatarScreen.refresh();
            }
        };
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(MENU));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
            for (PlayerSkin.Model skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) {
                    r.addLayer(new AvatarLayer(r));
                }
            }
        });
        // con personaje: el jugador de Minecraft no se dibuja (la capa dibuja el personaje en su lugar)
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) -> {
            AbstractClientPlayer p = (AbstractClientPlayer) e.getEntity();
            if (ClientAvatars.of(p.getUUID()) != null) {
                setVisible(e.getRenderer().getModel(), false);
                HIDDEN.add(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> {
            if (HIDDEN.remove((AbstractClientPlayer) e.getEntity())) {
                setVisible(e.getRenderer().getModel(), true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (MENU.consumeClick()) {
                if (Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().setScreen(new AvatarScreen());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientAvatars.disconnect());
    }

    static void setVisible(PlayerModel<?> m, boolean v) {
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg,
                m.leftLeg, m.rightPants, m.leftPants}) {
            p.visible = v;
        }
    }
}
