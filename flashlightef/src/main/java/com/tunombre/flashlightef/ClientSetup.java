package com.tunombre.flashlightef;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

/** reemplaza el modelo de la linterna por la version corregida para Epic Fight */
public final class ClientSetup {
    private ClientSetup() { }

    @SubscribeEvent
    public static void onBake(ModelEvent.ModifyBakingResult e) {
        for (ResourceLocation k : new ArrayList<>(e.getModels().keySet())) {
            if (!(k instanceof ModelResourceLocation mrl) || !mrl.getVariant().equals("inventory")) continue;
            if (!k.getNamespace().equals("flashlightmod") || !k.getPath().equals("flashlight")) continue;
            BakedModel m = e.getModels().get(k);
            if (m != null && !(m instanceof FlipModel)) e.getModels().put(k, new FlipModel(m));
        }
    }
}
