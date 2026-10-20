package com.tunombre.cosmeticos;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** efecto sin funcion de juego: solo marca que el jugador lleva ese cosmetico */
public class CosmeticEffect extends MobEffect {
    public final int index;

    public CosmeticEffect(int index, int color) {
        super(MobEffectCategory.NEUTRAL, color);
        this.index = index;
    }

    /** la leche no lo quita */
    @Override
    public List<ItemStack> getCurativeItems() { return List.of(); }
}
