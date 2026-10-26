package com.annis.npc;

import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<AnnisNpcEntity> NPC = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(AnnisNpc.MOD_ID, "npc"),
            EntityType.Builder.create(AnnisNpcEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .maxTrackingRange(10)
                    .build("npc"));

    public static Item NPC_SPAWN_EGG;

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(NPC, AnnisNpcEntity.createNpcAttributes());
        NPC_SPAWN_EGG = Registry.register(
                Registries.ITEM,
                Identifier.of(AnnisNpc.MOD_ID, "npc_spawn_egg"),
                new SpawnEggItem(NPC, 0x6B4A9E, 0xF2C94C, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(NPC_SPAWN_EGG));
    }
}
