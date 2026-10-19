package com.tunombre.modelos;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.api.forgeevent.ChangePlayerModeEvent;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** unica clase que toca Epic Fight (solo se carga si el mod esta instalado) */
final class EpicSupport {
    private EpicSupport() { }

    /** deja al jugador en modo vanilla (true) o en modo Epic Fight (false); sincroniza con su cliente */
    static void vanilla(ServerPlayer p, boolean vanilla) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp == null) return;
        if (vanilla) { if (!pp.isVanillaMode()) pp.toVanillaMode(true); }
        else if (!pp.isEpicFightMode()) pp.toEpicFightMode(true);
    }

    /** candado: con un modelo puesto no se puede pasar a modo Epic Fight (ni con la tecla ni por ningun otro mod), en servidor y en cliente */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMode(ChangePlayerModeEvent e) {
        if (e.getPlayerMode() != PlayerPatch.PlayerMode.EPICFIGHT) return;
        net.minecraft.world.entity.player.Player pl = e.getPlayerPatch().getOriginal();
        if (pl == null) return;
        boolean locked;
        if (pl instanceof ServerPlayer sp) locked = Server.isLocked(sp);
        else locked = pl.level().isClientSide && Modelos.clientLocked && pl.getUUID().equals(Modelos.clientUuid);
        if (locked) e.setCanceled(true);
    }

    static boolean isEpic(ServerPlayer p) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        return pp != null && pp.isEpicFightMode();
    }
}
