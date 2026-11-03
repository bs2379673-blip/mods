package com.starlevi.starspirits;

import com.starlevi.starspirits.block.ExtractionBlock;
import com.starlevi.starspirits.block.ExtractionBlockEntity;
import com.starlevi.starspirits.block.PotBlock;
import com.starlevi.starspirits.block.PotBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Bloques, objetos, entidad, datos del jugador y pestana de creativo. */
public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StarSpirits.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarSpirits.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StarSpirits.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, StarSpirits.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StarSpirits.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StarSpirits.MOD_ID);

    /** Punto de extraccion: no se rompe en supervivencia (solo en creativo). */
    public static final DeferredBlock<ExtractionBlock> EXTRACTION = BLOCKS.register("punto_extraccion", () -> new ExtractionBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0f, 3600000.0f).sound(SoundType.STONE)
                    .noOcclusion().lightLevel(s -> 7).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<PotBlock> POT = BLOCKS.register("maceta", () -> new PotBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.0f).sound(SoundType.DECORATED_POT)
                    .noOcclusion().lightLevel(s -> 3)));

    /** Pilar Runico: piedra gigante donde se vinculan los espiritus atrapados. No se rompe en supervivencia. */
    public static final DeferredBlock<com.starlevi.starspirits.block.PillarBlock> PILLAR = BLOCKS.register("pilar_runico",
            () -> new com.starlevi.starspirits.block.PillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 9).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<com.starlevi.starspirits.block.PillarBodyBlock> PILLAR_BODY = BLOCKS.register("pilar_runico_cuerpo",
            () -> new com.starlevi.starspirits.block.PillarBodyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.STONE).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<com.starlevi.starspirits.item.SpiritItem> CAUGHT = ITEMS.register("espiritu_atrapado",
            () -> new com.starlevi.starspirits.item.SpiritItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final DeferredItem<com.starlevi.starspirits.item.WandItem> WAND = ITEMS.register("varita_espiritus",
            () -> new com.starlevi.starspirits.item.WandItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    static {
        ITEMS.register("pilar_runico", () -> new BlockItem(PILLAR.get(), new Item.Properties()));
        ITEMS.register("punto_extraccion", () -> new BlockItem(EXTRACTION.get(), new Item.Properties()));
        ITEMS.register("maceta", () -> new BlockItem(POT.get(), new Item.Properties()));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractionBlockEntity>> EXTRACTION_BE = BLOCK_ENTITIES.register(
            "punto_extraccion", () -> BlockEntityType.Builder.of(ExtractionBlockEntity::new, EXTRACTION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.starlevi.starspirits.block.PillarBlockEntity>> PILLAR_BE = BLOCK_ENTITIES.register(
            "pilar_runico", () -> BlockEntityType.Builder.of(com.starlevi.starspirits.block.PillarBlockEntity::new, PILLAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PotBlockEntity>> POT_BE = BLOCK_ENTITIES.register(
            "maceta", () -> BlockEntityType.Builder.of(PotBlockEntity::new, POT.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<SpiritEntity>> SPIRIT = ENTITIES.register("espiritu",
            () -> EntityType.Builder.<SpiritEntity>of(SpiritEntity::new, MobCategory.MISC).sized(0.45f, 0.7f)
                    .clientTrackingRange(10).updateInterval(2).build("espiritu"));

    public static final Supplier<AttachmentType<SpiritData>> DATA = ATTACHMENTS.register("espiritus",
            () -> AttachmentType.builder(SpiritData::new).serialize(SpiritData.CODEC).copyOnDeath().build());

    static {
        TABS.register("starspirits", () -> CreativeModeTab.builder()
                .title(Component.literal("StarSpirits"))
                .icon(() -> new ItemStack(POT.get()))
                .displayItems((params, out) -> {
                    out.accept(PILLAR.get());
                    out.accept(WAND.get());
                })
                .build());
    }

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
    }
}
