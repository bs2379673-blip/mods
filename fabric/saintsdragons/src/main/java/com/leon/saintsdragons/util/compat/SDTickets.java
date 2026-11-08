package com.leon.saintsdragons.util.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.constant.dataticket.DataTicket;

/** Port 1.21.11 (GeckoLib 5): datos que GeckoLib 4 daba y GeckoLib 5 no; los llenan los modelos del mod. */
public final class SDTickets {
    public static final DataTicket<Entity> ENTITY = DataTicket.create("saintsdragons_entity", Entity.class);
    public static final DataTicket<ItemStack> ITEMSTACK = DataTicket.create("saintsdragons_itemstack", ItemStack.class);
    public static final DataTicket<EntityModelData> ENTITY_MODEL_DATA = DataTicket.create("saintsdragons_entity_model_data", EntityModelData.class);

    private SDTickets() {
    }
}
