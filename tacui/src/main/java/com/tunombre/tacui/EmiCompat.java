package com.tunombre.tacui;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Puente con EMI por reflexion: abre la pantalla de recetas (o usos) de un objeto. */
public final class EmiCompat {
    private EmiCompat() {}

    public static boolean loaded() {
        try { return ModList.get().isLoaded("emi"); } catch (Throwable t) { return false; }
    }

    public static void show(ItemStack st, boolean uses) {
        try {
            Object es = Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("of", ItemStack.class).invoke(null, st);
            Class<?> ing = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Class.forName("dev.emi.emi.api.EmiApi").getMethod(uses ? "displayUses" : "displayRecipes", ing).invoke(null, es);
        } catch (Throwable t) {
            TacUI.LOGGER.warn("EMI: no se pudo abrir las recetas: {}", t.toString());
        }
    }
}
