package com.leon.saintsdragons.client.compat.geo;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Port 1.21.11: lo que recibian los modelos de GeckoLib 4 en setCustomAnimations. */
public final class LegacyAnimState<T extends GeoAnimatable> {
    private final T animatable;
    private final float partialTick;
    private final GeoRenderState renderState;

    public LegacyAnimState(T animatable, float partialTick, GeoRenderState renderState) {
        this.animatable = animatable;
        this.partialTick = partialTick;
        this.renderState = renderState;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public T animatable() {
        return this.animatable;
    }

    public float getPartialTick() {
        return this.partialTick;
    }

    public GeoRenderState renderState() {
        return this.renderState;
    }

    public <D> D getData(DataTicket<D> ticket) {
        return this.renderState == null ? null : this.renderState.getGeckolibData(ticket);
    }

    public boolean isMoving() {
        Boolean m = getData(software.bernie.geckolib.constant.DataTickets.IS_MOVING);
        return m != null && m;
    }
}
