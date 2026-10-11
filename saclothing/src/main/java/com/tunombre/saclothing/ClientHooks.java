package com.tunombre.saclothing;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * Epic Fight reconstruye sus renderers parcheados al cargar; guardamos el del jugador y, ya dentro del mundo
 * (cuando todos los mods terminaron de registrar sus capas), envolvemos la capa de Curios existente
 * (la de Epic Fight o la de sa_epicfight_compat, lo que haya) con la nuestra.
 */
final class ClientHooks {
    private static final Logger LOG = LogUtils.getLogger();
    @SuppressWarnings("rawtypes")
    private static volatile PatchedLivingEntityRenderer pending;

    private ClientHooks() {
    }

    static void register() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(EventPriority.LOWEST, ClientHooks::onPatchedRenderers);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
    }

    @SuppressWarnings("rawtypes")
    private static void onPatchedRenderers(PatchedRenderersEvent.Modify event) {
        PatchedEntityRenderer r = event.get(EntityType.PLAYER);
        if (r instanceof PatchedLivingEntityRenderer living) {
            pending = living;
        }
    }

    @SuppressWarnings("rawtypes")
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PatchedLivingEntityRenderer r = pending;
        if (r == null || Minecraft.getInstance().level == null) {
            return;
        }
        pending = null;
        try {
            Installer.install(r);
        } catch (Throwable t) {
            LOG.error("[saclothing] no se pudo instalar la capa de prendas de Epic Fight", t);
        }
    }
}
