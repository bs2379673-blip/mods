package com.leon.saintsdragons.client.renderer.item;

import com.leon.saintsdragons.client.model.item.MossbackItemModel;
import com.leon.saintsdragons.common.item.MossbackItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Port 1.21.11 / GeckoLib 5: el Mossback en la mano. En el inventario se ve el dibujo plano (eso lo decide la
 * definicion del item, assets/saintsdragons/items/mossback.json).
 */
public class MossbackItemRenderer extends GeoItemRenderer<MossbackItem> {
    public MossbackItemRenderer() {
        super(new MossbackItemModel());
    }
}
