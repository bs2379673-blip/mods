package com.tunombre.modelos.client;

import com.tunombre.modelos.Modelos;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.api.client.forgeevent.RenderEpicFightPlayerEvent;

/** solo cliente: Epic Fight dibuja SIEMPRE su propio modelo (aunque este en modo vanilla) si su opcion "modelo original" esta apagada; con un modelo de CPM puesto se le prohibe */
public final class EpicClient {
    private EpicClient() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(RenderEpicFightPlayerEvent e) {
        var pl = e.getPlayerPatch().getOriginal();
        if (pl != null && Modelos.clientModels.contains(pl.getUUID())) e.setShouldRender(false);
    }
}
