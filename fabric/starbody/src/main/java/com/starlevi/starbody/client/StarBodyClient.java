package com.starlevi.starbody.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.starlevi.starbody.StarBody;
import com.starlevi.starbody.Traits;
import com.starlevi.starbody.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

/** Arranque del cliente: la capa del pelo, la tecla del editor (H) y los rasgos que manda el servidor. */
public final class StarBodyClient implements ClientModInitializer {
    private static final Map<Entity, HairSim> SIMS = new WeakHashMap<>();
    private static KeyMapping editorKey;
    private static int joinTicks = -1;
    static boolean openEditor;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.SkinTraits.TYPE, (p, ctx) -> ClientTraits.put(p.skin(), Traits.decode(p.data())));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> ar) {
                helper.register(new HairLayer((RenderLayerParent) ar));
            }
        });
        KeyMapping.Category cat = KeyMapping.Category.register(Net.id("starbody"));
        editorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.starbody.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommandManager.literal("starbody").executes(c -> {
            openEditor = true;
            return 1;
        })));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (editorKey.consumeClick()) {
                openEditor = true;
            }
            if (openEditor && mc.player != null) {
                openEditor = false;
                mc.setScreen(new EditorScreen());
            }
            // al entrar: si el servidor no tiene mis rasgos y yo los tengo guardados, se los mando
            if (joinTicks >= 0 && --joinTicks == 0 && mc.player != null) {
                String skin = mySkin().toString();
                if (ClientTraits.get(skin) == null) {
                    byte[] data = loadLocal(skin);
                    if (data != null) {
                        ClientTraits.put(skin, Traits.decode(data));
                        ClientPlayNetworking.send(new Net.SetTraits(skin, data));
                    }
                }
            }
        });
        ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> joinTicks = 60);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
            ClientTraits.clear();
            SIMS.clear();
        }));
    }

    public static Identifier mySkin() {
        return Minecraft.getInstance().player.getSkin().body().texturePath();
    }

    /** Por cada jugador que se dibuja: actualiza sus fisicas y deja el pelo listo para la capa. */
    public static void extract(Avatar e, AvatarRenderState s, float partial) {
        FabricRenderState fs = (FabricRenderState) (Object) s;
        if (s.skin == null) {
            fs.setData(HairRender.KEY, null);
            return;
        }
        Identifier skinId = s.skin.body().texturePath();
        String skin = skinId.toString();
        Traits t = ClientTraits.get(skin);
        HairMesh mesh = t == null || t.empty() ? null : ClientTraits.mesh(skin);
        if (mesh == null) {
            fs.setData(HairRender.KEY, null);
            return;
        }
        HairSim sim = SIMS.computeIfAbsent(e, k -> new HairSim());
        sim.update(e, partial, t.motion);
        fs.setData(HairRender.KEY, new HairRender.Frame(skinId, mesh, sim, s.showHat, s.showJacket));
    }

    /** La skin a usar para el cuerpo (sin el pelo que cuelga), o null para dejar la normal. */
    public static Identifier texture(AvatarRenderState s) {
        HairRender.Frame f = ((FabricRenderState) (Object) s).getData(HairRender.KEY);
        return f == null ? null : ClientTraits.hidden(f.skin());
    }

    // ---------------------------------------------------------------- copia local de mis rasgos

    private static Path localFile(String skin) {
        return FabricLoader.getInstance().getConfigDir().resolve(StarBody.MOD_ID).resolve(skin.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".rasgos");
    }

    static void saveLocal(String skin, byte[] data) {
        try {
            Path f = localFile(skin);
            Files.createDirectories(f.getParent());
            if (data.length == 0) {
                Files.deleteIfExists(f);
            } else {
                Files.write(f, data);
            }
        } catch (Exception e) {
            StarBody.LOGGER.warn("StarBody: no pude guardar la copia local: {}", e.toString());
        }
    }

    static byte[] loadLocal(String skin) {
        try {
            Path f = localFile(skin);
            return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
