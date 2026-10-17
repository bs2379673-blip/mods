package com.tunombre.crafteos;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/** modificador de botin global: saca de cualquier botin (cofres, drops de mobs y bloques, pesca...) los items prohibidos */
public class BanLoot extends LootModifier {
    public static final Supplier<Codec<BanLoot>> CODEC = Suppliers.memoize(() -> com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> codecStart(i).apply(i, BanLoot::new)));

    public BanLoot(LootItemCondition[] conditions) { super(conditions); }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        loot.removeIf(Bans::isBanned);
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}
