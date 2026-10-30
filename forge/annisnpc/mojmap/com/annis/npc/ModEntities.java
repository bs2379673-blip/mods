package com.annis.npc;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.item.NpcWandItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModEntities {
    public static final EntityType<AnnisNpcEntity> NPC = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "npc"),
            EntityType.Builder.of(AnnisNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build("npc"));

    public static Item NPC_SPAWN_EGG;
    public static Item NPC_WAND;
    /** Accesorios de ejemplo (modelos 3D propios) para pegar a los NPC. */
    public static final String[] ACCESSORY_IDS = {"accesorio_tambor", "accesorio_baqueta", "accesorio_sombrero", "accesorio_trompeta"};
    public static final java.util.List<Item> ACCESSORIES = new java.util.ArrayList<>();

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(NPC, AnnisNpcEntity.createNpcAttributes());
        NPC_SPAWN_EGG = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "npc_spawn_egg"),
                new SpawnEggItem(NPC, 0x6B4A9E, 0xF2C94C, new Item.Properties()));
        NPC_WAND = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "npc_wand"),
                new NpcWandItem(new Item.Properties().stacksTo(1)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(NPC_SPAWN_EGG));
        for (String id : ACCESSORY_IDS) {
            ACCESSORIES.add(Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, id),
                    new Item(new Item.Properties().stacksTo(16))));
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(NPC_WAND);
            ACCESSORIES.forEach(entries::accept);
        });
    }
}
