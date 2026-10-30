package com.annis.npc;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.item.NpcWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/** En Forge las cosas se crean y registran dentro del evento de registro. */
public final class ModEntities {
    public static EntityType<AnnisNpcEntity> NPC;

    public static Item NPC_SPAWN_EGG;
    public static Item NPC_WAND;
    /** Accesorios de ejemplo (modelos 3D propios) para pegar a los NPC. */
    public static final String[] ACCESSORY_IDS = {"accesorio_tambor", "accesorio_baqueta", "accesorio_sombrero", "accesorio_trompeta"};
    public static final java.util.List<Item> ACCESSORIES = new java.util.ArrayList<>();

    private ModEntities() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AnnisNpc.MOD_ID, path);
    }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterEvent e) -> {
            e.register(Registries.ENTITY_TYPE, h -> {
                NPC = EntityType.Builder.of(AnnisNpcEntity::new, MobCategory.MISC)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(10)
                        .build("npc");
                h.register(id("npc"), NPC);
            });
            e.register(Registries.ITEM, h -> {
                NPC_SPAWN_EGG = new ForgeSpawnEggItem(() -> NPC, 0x6B4A9E, 0xF2C94C, new Item.Properties());
                h.register(id("npc_spawn_egg"), NPC_SPAWN_EGG);
                NPC_WAND = new NpcWandItem(new Item.Properties().stacksTo(1));
                h.register(id("npc_wand"), NPC_WAND);
                ACCESSORIES.clear();
                for (String a : ACCESSORY_IDS) {
                    Item item = new Item(new Item.Properties().stacksTo(16));
                    ACCESSORIES.add(item);
                    h.register(id(a), item);
                }
            });
        });
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(NPC, AnnisNpcEntity.createNpcAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                e.accept(NPC_SPAWN_EGG);
            } else if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(NPC_WAND);
                ACCESSORIES.forEach(e::accept);
            }
        });
    }

    /** Ya no hace nada: todo se registra en init (lo llama la clase @Mod). */
    public static void register() {
    }
}
