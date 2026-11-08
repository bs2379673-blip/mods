package com.leon.saintsdragons.client.compat.geo;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.Map;

/** Port 1.21.11: el estado de dibujo de las entidades del mod que usan GeckoLib. */
public class SDRenderState extends LivingEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> data = new Reference2ObjectOpenHashMap<>();

    // GeckoLib mete por mixin su propio mapa en EntityRenderState y redefine add/has;
    // hay que redefinirlos aca tambien para que todo use el mismo mapa.
    @Override
    public <D> void addGeckolibData(DataTicket<D> dataTicket, D value) {
        this.data.put(dataTicket, value);
    }

    @Override
    public boolean hasGeckolibData(DataTicket<?> dataTicket) {
        return this.data.containsKey(dataTicket);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return this.data;
    }
}
